/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.configuration

import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

/**
 * Whether a workspace's device configuration is complete enough to start the 8-stage build.
 *
 * Only the fields the pipeline cannot run without are checked; every other field keeps its default. The
 * stage-scoped rules already in [StageSettingsValidator] are reused, so each rule lives in one place.
 */
public object DeviceConfigReadiness {
    public const val REQUIRED: String = "REQUIRED"
    public const val INVALID_FORMAT: String = "INVALID_FORMAT"
    public const val UNKNOWN_PARTITION: String = "UNKNOWN_PARTITION"
    public const val DUPLICATE_PARTITION: String = "DUPLICATE_PARTITION"
    public const val INVALID_SIZE: String = "INVALID_SIZE"
    public const val SIGNING_DISABLED: String = "SIGNING_DISABLED"
    public const val CHECKSUM_CONFLICT: String = "CHECKSUM_CONFLICT"

    private val KNOWN_DYNAMIC = setOf(
        "system",
        "vendor",
        "product",
        "system_ext",
        "odm",
        "vendor_dlkm",
        "odm_dlkm",
        "system_dlkm",
    )
    private val KNOWN_BOOT = setOf("boot", "init_boot", "vendor_boot", "dtbo")
    private val CODENAME = Regex("[A-Za-z0-9_]+")
    private val SHA256 = Regex("[0-9a-f]{64}")

    // Fields reported here that belong to a stage's settings (Acquire, Build), not to the device.
    private val STAGE_FIELD_PREFIXES = listOf("firmware", "signing.")
    private val STAGE_FIELDS = setOf("region", "blockSize", "alignment", "filesystem")

    /** The [ValidationError.objectId] of a problem fixed in the device configuration (see [isDeviceIssue]). */
    public const val DEVICE_OBJECT: String = "device"

    /** Whether [error] is fixed in the device configuration rather than in a stage's settings. */
    public fun isDeviceIssue(error: ValidationError): Boolean = error.stageId == null || error.objectId == DEVICE_OBJECT

    /** Whether [fieldPath] belongs to the device configuration rather than to a stage's settings. */
    public fun isDeviceField(fieldPath: String): Boolean =
        fieldPath !in STAGE_FIELDS && STAGE_FIELD_PREFIXES.none { fieldPath.startsWith(it) }

    /** Blocking problems with [target] and [snapshot]; empty means the build may start. */
    public fun check(target: TargetDevice, snapshot: ConfigurationSnapshot): List<ValidationError> {
        val problems = Problems()
        problems.identity(target)
        problems.firmware(target, snapshot)
        problems.layout(target)
        problems.superPartition(target)
        problems.bootChain(target)
        problems.signing(snapshot)
        // Every stage a build runs: a problem in one stops the run there, so the build must not start.
        problems.list += StageSettingsValidator.validateAcquisition(snapshot.acquisition, target).errors
        problems.list += StageSettingsValidator.validateExtraction(snapshot.extraction, target).errors
        problems.list += StageSettingsValidator.validateAssembly(snapshot.assembly, target).errors
        problems.list += StageSettingsValidator.validateDebloat(snapshot.debloat).errors
        problems.list += StageSettingsValidator.validateCustomization(snapshot.customization).errors
        problems.list += StageSettingsValidator.validateBuild(snapshot.build, target).errors
        problems.list += StageSettingsValidator.validateRelease(snapshot.release, target).errors
        return problems.list.filter { it.severity == Severity.ERROR }
    }

    private class Problems {
        val list = mutableListOf<ValidationError>()

        /** Records a blocking problem when [failed] is true. */
        fun rule(failed: Boolean, stage: StageId?, field: String, code: String, message: String) {
            // A device field is fixed in the device configuration, whichever stage it matters to.
            val origin = DEVICE_OBJECT.takeIf { isDeviceField(field) }
            if (failed) list += ValidationError(stage, origin, field, code, Severity.ERROR, message)
        }

        fun identity(t: TargetDevice) {
            val zip = StageId.BUILD_FLASHABLE_ZIP
            rule(t.name.isBlank(), null, "name", REQUIRED, "Device name is required")
            rule(t.codename.isBlank(), null, "codename", REQUIRED, "Codename is required")
            rule(
                t.codename.isNotBlank() && !CODENAME.matches(t.codename),
                null,
                "codename",
                INVALID_FORMAT,
                "Codename may only contain letters, digits and _",
            )
            rule(t.productName.isBlank(), zip, "productName", REQUIRED, "Product name is required")
            rule(t.fastbootProduct.isBlank(), zip, "fastbootProduct", REQUIRED, "Fastboot product is required")
        }

        fun firmware(t: TargetDevice, s: ConfigurationSnapshot) {
            val stage = StageId.FIRMWARE_ACQUISITION
            val acquisition = s.acquisition
            val fw = acquisition.firmware
            val download = acquisition.mode == AcquisitionMode.DOWNLOAD
            val hasUrl = !fw.acquisitionUrl.isNullOrBlank() || !fw.otaUrl.isNullOrBlank()
            val sha = acquisition.expectedSha256 ?: fw.sha256
            rule(download && !hasUrl, stage, "firmware.acquisitionUrl", REQUIRED, "A firmware download URL is required")
            rule(
                sha != null && fw.checksumUnavailable,
                stage,
                "firmware.sha256",
                CHECKSUM_CONFLICT,
                "Set a SHA-256 or mark it unavailable, not both",
            )
            rule(
                sha != null && !fw.checksumUnavailable && !SHA256.matches(sha),
                stage,
                "firmware.sha256",
                INVALID_FORMAT,
                "SHA-256 must be 64 lowercase hex characters",
            )
            rule(
                sha == null && !fw.checksumUnavailable && download,
                stage,
                "firmware.sha256",
                REQUIRED,
                "A SHA-256 is required, or mark it unavailable",
            )
            rule(t.filesystemType.isBlank(), stage, "filesystemType", REQUIRED, "Filesystem type is required")
        }

        fun layout(t: TargetDevice) {
            partitions(t.dynamicPartitions, KNOWN_DYNAMIC, "dynamicPartitions")
            partitions(t.bootPartitions, KNOWN_BOOT, "bootPartitions")
            rule(
                t.bootPartitions.isNotEmpty() && t.packagePolicy.bootSlots.isEmpty(),
                StageId.BUILD_FLASHABLE_ZIP,
                "packagePolicy.bootSlots",
                REQUIRED,
                "Boot partitions need at least one slot",
            )
            rule(
                t.activeSlotSuffix.isBlank(),
                StageId.WORK_TREE_ASSEMBLY,
                "activeSlotSuffix",
                REQUIRED,
                "Active slot suffix is required",
            )
        }

        private fun partitions(list: List<String>, known: Set<String>, field: String) {
            val stage = StageId.FIRMWARE_EXTRACTION
            rule(list.isEmpty(), stage, field, REQUIRED, "At least one partition is required")
            rule(list.toSet().size != list.size, stage, field, DUPLICATE_PARTITION, "Partitions must not repeat")
            list.filter { it !in known }.distinct().forEach {
                rule(true, stage, field, UNKNOWN_PARTITION, "Unknown partition '$it'")
            }
        }

        fun superPartition(t: TargetDevice) {
            val stage = StageId.BUILD_FLASHABLE_ZIP
            rule(t.superPartitionBytes <= 0L, stage, "superPartitionBytes", INVALID_SIZE, "Super size must be > 0")
            rule(t.superGroupBytes <= 0L, stage, "superGroupBytes", INVALID_SIZE, "Group size must be above zero")
            rule(
                t.superGroupBytes > t.superPartitionBytes,
                stage,
                "superGroupBytes",
                INVALID_SIZE,
                "Group size must not exceed the super partition size",
            )
            rule(t.superMetadataSlots < 1, stage, "superMetadataSlots", INVALID_SIZE, "Metadata slots must be >= 1")
            rule(t.superGroupName.isBlank(), stage, "superGroupName", REQUIRED, "Super group name is required")
        }

        fun bootChain(t: TargetDevice) {
            val stage = StageId.BUILD_FLASHABLE_ZIP
            val sizes = mapOf(
                "boot" to ("bootPartitionBytes" to t.bootPartitionBytes),
                "vendor_boot" to ("vendorBootPartitionBytes" to t.vendorBootPartitionBytes),
                "init_boot" to ("initBootPartitionBytes" to t.initBootPartitionBytes),
                "dtbo" to ("dtboPartitionBytes" to t.dtboPartitionBytes),
            )
            sizes.forEach { (partition, fieldAndSize) ->
                val failed = partition in t.bootPartitions && fieldAndSize.second <= 0L
                rule(failed, stage, fieldAndSize.first, INVALID_SIZE, "Size of '$partition' must be above zero")
            }
            val relative = !t.bootDevicePath.startsWith("/")
            rule(relative, stage, "bootDevicePath", INVALID_FORMAT, "Boot device path must be absolute")
        }

        fun signing(s: ConfigurationSnapshot) {
            val signing = s.build.signing
            rule(
                !signing.signImages,
                StageId.BUILD_FLASHABLE_ZIP,
                "signing.signImages",
                SIGNING_DISABLED,
                "Image signing must stay on",
            )
            list += StageSettingsValidator.validateSigning(signing).errors
        }
    }
}
