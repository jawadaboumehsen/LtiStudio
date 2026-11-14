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

import org.ide.lti.core.model.target.TargetDevice

/** How one image would fare. */
public enum class PlanVerdict { FITS, NOTE, NAMED_CONFIRMATION, REFUSED }

/**
 * Where [image] would land and why it may or may not. [destination] is the phone's partition; [measured] says whether
 * its name and size came from the phone, or are assumed from the device configuration.
 */
public data class PlanRow(
    val image: ProjectImage,
    val destination: String?,
    val measured: Boolean,
    val limitBytes: Long?,
    val verdict: PlanVerdict,
    val reasons: List<String>,
)

/** The picked images mapped to the phone, and what stops the whole set. Nothing here writes. */
public data class FlashPlan(val rows: List<PlanRow>, val refusals: List<String>, val notes: List<String>) {
    public val refused: Boolean get() = refusals.isNotEmpty() || rows.any { it.verdict == PlanVerdict.REFUSED }
}

public object FlashPlanner {
    /**
     * What holds the phone's identity, its calibration and the keys written once on the factory line (IMEI and
     * modem NV, sensor and DDR calibration, device-unique keys, lock state): refused on every transport, because
     * nothing could put it back.
     */
    public val IDENTITY: Set<String> = setOf(
        "persist", "persistbak", "modemst1", "modemst2", "fsg", "fsc", "efs", "efs1", "efs2", "sec_efs", "nvdata",
        "nvram", "nvcfg", "protect1", "protect2", "proinfo", "prodinfo", "devinfo", "frp", "cdt", "ddr", "secdata",
        "otp", "seccfg",
    )

    /** What holds the boot loaders and their trusted firmware: a wrong image costs fastboot itself. */
    public val BOOTLOADER: Set<String> = setOf(
        "abl", "xbl", "xbl_config", "xbl_ramdump", "sbl1", "aboot", "tz", "hyp", "rpm", "pmic", "keymaster",
        "cmnlib", "cmnlib64", "devcfg", "qupfw", "uefi", "uefisecapp", "imagefv", "aop", "aop_config", "shrm",
        "cpucp", "cpucp_dtb", "preloader", "lk", "bootloader", "tee", "sspm", "spmfw", "mcupm", "gz", "scp",
    )

    public fun plan(phone: PhoneFacts?, device: TargetDevice?, picked: List<ProjectImage>): FlashPlan {
        val rows = picked.map { row(it, phone, device, picked) }
        val clashes = rows.filter { it.destination != null }.groupBy { it.destination }.filterValues { it.size > 1 }
        val checked = rows.map { r ->
            val others = clashes[r.destination]?.filter { it !== r }.orEmpty()
            if (others.isEmpty()) {
                r
            } else {
                r.copy(
                    verdict = PlanVerdict.REFUSED,
                    reasons = r.reasons + "${others.joinToString { it.image.path }} is aimed at ${r.destination} too",
                )
            }
        }
        return FlashPlan(checked, refusals(phone, device), notes(phone))
    }

    private fun refusals(phone: PhoneFacts?, device: TargetDevice?): List<String> = buildList {
        phone ?: return@buildList
        if (phone.unlocked == false) add("The bootloader is locked: fastboot refuses every write until it is unlocked.")
        phone.snapshotStatus?.takeIf { it != "none" }?.let {
            add(
                "A Virtual A/B update is still in flight (snapshot-update-status: $it): boot Android and let it " +
                    "finish merging first; writing now can leave both slots unbootable.",
            )
        }
        val product = phone.product
        val expected = device?.let { listOf(it.fastbootProduct, it.codename, it.productName) + it.assertModels }
        if (product != null && expected != null && expected.none { it.equals(product, ignoreCase = true) }) {
            add("This phone reports $product; the project is for ${device.codename}.")
        }
    }

    private fun notes(phone: PhoneFacts?): List<String> = when {
        phone == null -> listOf(
            "No phone read: destinations and sizes are assumed from the device configuration.",
        )

        !phone.mode.fastboot -> listOf(
            "The phone is in ${phone.mode.words}: it lists no partitions there. Reboot it to the bootloader or " +
                "fastbootd to measure them.",
        )

        else -> emptyList()
    }

    /** One image on its way: where it lands and what it is checked against. */
    private class Landing(
        val image: ProjectImage,
        val phone: PhoneFacts?,
        val device: TargetDevice?,
        val picked: List<ProjectImage>,
    ) {
        val name = image.partition

        /** [name] without a slot suffix: `persist_a` is still the identity partition `persist`. */
        val base = name.removeSuffix("_a").removeSuffix("_b")
        val measured = phone?.partitions?.isNotEmpty() == true
        private val slot = phone?.currentSlot ?: device?.activeSlotSuffix
        val target = phone?.takeIf { measured }?.let { it.partition(name) ?: slot?.let { s -> it.partition(name + s) } }
        val destination = when {
            target != null -> target.name

            measured -> null

            // On an A/B phone every firmware partition has a slot but super and the identity ones.
            device != null && name != "super" && name !in IDENTITY -> name + slot.orEmpty()

            else -> name
        }
        val limit = target?.sizeBytes ?: assumedSize(name, device).takeUnless { measured }
        val inSuper = device?.dynamicPartitions?.contains(name) == true
    }

    private fun row(
        image: ProjectImage,
        phone: PhoneFacts?,
        device: TargetDevice?,
        picked: List<ProjectImage>,
    ): PlanRow {
        val l = Landing(image, phone, device, picked)
        val found = RULES.mapNotNull { it(l) }
        val verdict = found.maxOfOrNull { it.first } ?: PlanVerdict.FITS
        return PlanRow(image, l.destination, l.target != null, l.limit, verdict, found.map { it.second })
    }

    /** Each rule judges one thing about an image; together, in this order, they are its reasons. */
    private val RULES: List<(Landing) -> Pair<PlanVerdict, String>?> = listOf(
        { l ->
            (PlanVerdict.REFUSED to "holds the phone's identity, calibration or factory keys: nothing puts it back")
                .takeIf { l.name in IDENTITY || l.base in IDENTITY }
        },
        { l ->
            (
                PlanVerdict.NAMED_CONFIRMATION to
                    "holds the bootloader: a wrong image costs you fastboot; " +
                    "it would take typing \"${l.name}\" to confirm"
                ).takeIf { l.name in BOOTLOADER || l.base in BOOTLOADER }
        },
        { l ->
            when {
                !l.measured -> null

                // Whether or not the bootloader lists it, a logical partition is written by fastbootd only.
                (l.inSuper || l.target?.logical == true) && l.phone?.mode == PhoneMode.BOOTLOADER ->
                    PlanVerdict.REFUSED to
                        "lives inside ${l.phone.superName ?: "super"} on this phone: " +
                        "the bootloader has no partition of that name (fastbootd has)"

                l.target != null -> null

                else -> PlanVerdict.REFUSED to "the phone has no partition named ${l.name}"
            }
        },
        { l ->
            (PlanVerdict.REFUSED to "super.img is picked too and already holds it")
                .takeIf { l.name != "super" && l.inSuper && l.picked.any { it.partition == "super" } }
        },
        { l ->
            val limit = l.limit
            when {
                l.target?.logical == true -> PlanVerdict.NOTE to "logical: fastbootd resizes it to the image"

                limit != null && l.image.fullBytes > limit ->
                    PlanVerdict.REFUSED to "is ${l.image.fullBytes} bytes; the partition holds $limit"

                else -> null
            }
        },
        { l ->
            l.phone?.maxDownloadBytes?.takeIf { !l.image.sparse && l.image.fullBytes > it }?.let {
                PlanVerdict.NOTE to "larger than one transfer ($it bytes): fastboot converts it to sparse first"
            }
        },
        { l ->
            (PlanVerdict.NOTE to "flash it once with --disable-verity --disable-verification; packages never hold it")
                .takeIf { l.name.startsWith("vbmeta") }
        },
    )

    /** The sizes the device configuration states (`device.json`), for a phone not measured. */
    private fun assumedSize(name: String, device: TargetDevice?): Long? = device?.let {
        when (name) {
            "super" -> it.superPartitionBytes
            "boot" -> it.bootPartitionBytes
            "vendor_boot" -> it.vendorBootPartitionBytes
            "init_boot" -> it.initBootPartitionBytes
            "dtbo" -> it.dtboPartitionBytes
            else -> null
        }
    }
}
