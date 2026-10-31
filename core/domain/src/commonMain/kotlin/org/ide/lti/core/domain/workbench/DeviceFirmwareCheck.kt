/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workbench

import org.ide.lti.core.domain.pipeline.toolkit.SuperLayout
import org.ide.lti.core.domain.pipeline.toolkit.SuperTool
import org.ide.lti.core.model.target.TargetDevice

/** What the extracted firmware says about the device: its build properties, boot image sizes and super table. */
public data class FirmwareFacts(
    val props: Map<String, String>,
    /** Partition size each boot-chain image was signed for (its AVB footer's `Image size`). */
    val partitionSizes: Map<String, Long>,
    /** The stock super image's partition table; null when the firmware has none (an OTA payload). */
    val superLayout: SuperLayout?,
    /**
     * The dynamic partitions the stock fstab mounts (`logical`), in its order; empty when there is none. The package
     * writes only the device's dynamic partitions into super, so one missing there is gone after flashing.
     */
    val logicalPartitions: List<String> = emptyList(),
)

/** One fact: [setting] is the device setting it checks (null for facts shown for reference only). */
public data class FirmwareFact(val setting: String?, val label: String, val configured: String?, val firmware: String) {
    public val differs: Boolean
        get() = setting != null && configured != firmware
}

/**
 * Compares the workspace's device configuration with what its own firmware says: codename,
 * fastboot product, virtual A/B, the boot-chain partition sizes and the super table. A wrong size here makes the
 * build sign images for the wrong partition size.
 */
public class DeviceFirmwareCheck(private val runner: WorkbenchRunner) {

    public suspend fun read(workspaceId: String, bootPartitions: List<String>): FirmwareFacts = runner.read(
        workspaceId,
    ) {
        val props = PROP_FILES.mapNotNull { readText(it) }.flatMap { it.lines() }
            .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 && line.startsWith("ro.") } }
            .associate { it[0].trim() to it[1].trim() }
        val sizes = bootPartitions.mapNotNull { p ->
            val info = tryRun("avbtool", "info_image", "--image", abs("$EXTRACTED/$p.img"))
            IMAGE_SIZE.find(
                info.stdout,
            )?.groupValues?.get(1)?.toLongOrNull()?.takeIf { info.exitCode == 0 }?.let { p to it }
        }.toMap()
        val superLayout = SUPER_IMAGES.firstOrNull { exists(it) }
            ?.let { SuperTool.parseLpdumpOutput(run("lpdump", abs(it))) }
        val logical = sh(LOGICAL_SCRIPT, abs(EXTRACTED)).lines().map(String::trim).filter(String::isNotEmpty)
        FirmwareFacts(props, sizes, superLayout, logical)
    }

    public companion object {
        private const val EXTRACTED = "firmware/extracted"
        private val PROP_FILES = listOf("$EXTRACTED/system/system/build.prop", "$EXTRACTED/vendor/build.prop")
        private val SUPER_IMAGES = listOf("firmware/raw/super.raw.img", "firmware/raw/super.img")
        private val IMAGE_SIZE = Regex("""Image size:\s+(\d+) bytes""")

        /** `sh -c LOGICAL_SCRIPT sh <extracted>`: each partition the stock fstabs mount as `logical`, once. */
        private const val LOGICAL_SCRIPT: String = """
            cat "$1"/vendor/etc/fstab.* "$1"/odm/etc/fstab.* 2>/dev/null |
              awk '$1 !~ /^#/ && $5 ~ /(^|,)logical(,|$)/ {
                p = $2; sub(/^\//, "", p); if (!(p in s)) { s[p] = 1; print p } }'
        """

        /** The device's dynamic partitions next to the stock fstab's, compared as sets (their order means nothing). */
        private fun dynamicFact(device: TargetDevice, facts: FirmwareFacts): FirmwareFact? =
            facts.logicalPartitions.takeIf { it.isNotEmpty() }?.let { stock ->
                FirmwareFact(
                    "dynamicPartitions",
                    "dynamic partitions",
                    device.dynamicPartitions.sorted().joinToString(", "),
                    stock.sorted().joinToString(", "),
                )
            }

        private val SIZE_FIELDS = mapOf(
            "boot" to "bootPartitionBytes",
            "vendor_boot" to "vendorBootPartitionBytes",
            "init_boot" to "initBootPartitionBytes",
            "dtbo" to "dtboPartitionBytes",
        )

        /** Each fact of [facts] next to [device]'s setting; facts the firmware does not carry are left out. */
        public fun compare(device: TargetDevice, facts: FirmwareFacts): List<FirmwareFact> {
            val p = facts.props
            fun prop(vararg keys: String) = keys.firstNotNullOfOrNull { p[it]?.ifBlank { null } }
            val codename = prop("ro.product.vendor.device", "ro.product.system.device", "ro.product.device")
            val sizes = facts.partitionSizes.mapNotNull { (partition, size) ->
                SIZE_FIELDS[partition]?.let { field ->
                    FirmwareFact(field, "$partition partition size", sizeOf(device, field).toString(), size.toString())
                }
            }
            val superFacts = facts.superLayout?.let { layout ->
                val group = layout.groups.firstOrNull()
                listOfNotNull(
                    FirmwareFact(
                        "superPartitionBytes",
                        "super size",
                        "${device.superPartitionBytes}",
                        "${layout.superSize}",
                    )
                        .takeIf { layout.superSize > 0 },
                    group?.let { FirmwareFact("superGroupName", "super group", device.superGroupName, it.name) },
                    group?.let {
                        FirmwareFact(
                            "superGroupBytes",
                            "super group size",
                            "${device.superGroupBytes}",
                            "${it.maxSize}",
                        )
                    },
                )
            }.orEmpty()
            return listOfNotNull(
                codename?.let { FirmwareFact("codename", "codename", device.codename, it) },
                dynamicFact(device, facts),
                codename?.takeIf { it !in device.assertModels }?.let {
                    FirmwareFact(
                        "assertModels",
                        "devices the package installs on",
                        device.assertModels.joinToString(),
                        it,
                    )
                },
                prop(
                    "ro.board.platform",
                )?.let { FirmwareFact("fastbootProduct", "fastboot product", device.fastbootProduct, it) },
                prop(
                    "ro.virtual_ab.enabled",
                )?.let { FirmwareFact("virtualAb", "virtual A/B", "${device.virtualAb}", it) },
            ) + sizes + superFacts + listOfNotNull(
                prop(
                    "ro.product.vendor.model",
                    "ro.product.system.model",
                )?.let { FirmwareFact(null, "model", null, it) },
                prop("ro.vendor.build.fingerprint", "ro.system.build.fingerprint")
                    ?.let { FirmwareFact(null, "fingerprint", null, it) },
                prop("ro.build.version.release")?.let { r ->
                    FirmwareFact(null, "Android", null, "$r (SDK ${prop("ro.build.version.sdk") ?: "?"})")
                },
                prop("ro.build.version.security_patch")?.let { FirmwareFact(null, "security patch", null, it) },
            )
        }

        /** [device] with [fact]'s firmware value; a fact for reference only changes nothing. */
        public fun apply(device: TargetDevice, fact: FirmwareFact): TargetDevice {
            val v = fact.firmware
            return when (fact.setting) {
                "codename" -> device.copy(codename = v)

                "assertModels" -> device.copy(assertModels = device.assertModels + v)

                "fastbootProduct" -> device.copy(fastbootProduct = v)

                "virtualAb" -> device.copy(virtualAb = v == "true")

                "bootPartitionBytes" -> device.copy(bootPartitionBytes = v.toLong())

                "vendorBootPartitionBytes" -> device.copy(vendorBootPartitionBytes = v.toLong())

                "initBootPartitionBytes" -> device.copy(initBootPartitionBytes = v.toLong())

                "dtboPartitionBytes" -> device.copy(dtboPartitionBytes = v.toLong())

                "superPartitionBytes" -> device.copy(superPartitionBytes = v.toLong())

                "superGroupName" -> device.copy(superGroupName = v)

                "superGroupBytes" -> device.copy(superGroupBytes = v.toLong())

                // Keeps the device's order for the partitions both name; the firmware's new ones go last.
                "dynamicPartitions" -> v.split(", ").toSet().let { stock ->
                    device.copy(
                        dynamicPartitions = device.dynamicPartitions.filter { it in stock } +
                            stock.filter { it !in device.dynamicPartitions }.sorted(),
                    )
                }

                else -> device
            }
        }

        private fun sizeOf(device: TargetDevice, field: String): Long = when (field) {
            "bootPartitionBytes" -> device.bootPartitionBytes
            "vendorBootPartitionBytes" -> device.vendorBootPartitionBytes
            "initBootPartitionBytes" -> device.initBootPartitionBytes
            else -> device.dtboPartitionBytes
        }
    }
}
