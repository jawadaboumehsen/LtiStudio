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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** fastboot's own output format; partitions and sizes as PQ84P01's firmware has them. */
class FlashPlanTest {
    private val getvar = """
        (bootloader) cpu-abi:arm64-v8a
        (bootloader) version-bootloader:abl-1.0:2507
        (bootloader) partition-size:boot_a:0x6000000
        (bootloader) partition-type:boot_a:raw
        (bootloader) partition-size:abl_a:0x800000
        (bootloader) partition-size:persist:0x2000000
        (bootloader) partition-size:super:0x400000000
        (bootloader) has-slot:boot:yes
        (bootloader) max-download-size:0x10000000
        (bootloader) current-slot:a
        (bootloader) slot-count:2
        (bootloader) unlocked:yes
        (bootloader) is-userspace:no
        (bootloader) snapshot-update-status:none
        (bootloader) super-partition-name:super
        (bootloader) product:sun
        all: Done!!
        Finished. Total time: 0.051s
    """.trimIndent()

    private val device = TargetDevice.EMPTY.copy(codename = "PQ84P01", fastbootProduct = "sun")
    private val phone = PhoneFacts("ZTE1234", PhoneMode.BOOTLOADER, PhoneReadout.fastbootVars(getvar))

    private fun image(name: String, bytes: Long, built: Boolean = true, sparse: Boolean = false, full: Long = bytes) =
        ProjectImage("out/images/$name.img", name, bytes, full, sparse, built)

    @Test
    fun getvarAndDevicesAreRead() {
        assertEquals("abl-1.0:2507", phone.values["version-bootloader"])
        assertEquals(0x6000000L, phone.partition("boot_a")?.sizeBytes)
        assertEquals("_a", phone.currentSlot)
        assertEquals(true, phone.unlocked)
        assertEquals(0x10000000L, phone.maxDownloadBytes)
        assertFalse("all" in phone.values)
        val listed = PhoneReadout.devices(
            "List of devices attached\nR5CT1 device usb:1-1 product:sun model:NX809J device:PQ84P01\n" +
                "R5CT2 unauthorized usb:1-2\n",
            "ZTE1234\tfastboot\n",
        )
        assertEquals(
            listOf("R5CT1" to PhoneMode.ANDROID, "R5CT2" to PhoneMode.UNAUTHORIZED, "ZTE1234" to PhoneMode.BOOTLOADER),
            listed,
        )
        assertEquals(
            "PQ84P01",
            PhoneReadout.props("[ro.product.device]: [PQ84P01]\n[ro.boot.slot_suffix]: [_b]")["ro.product.device"],
        )
    }

    @Test
    fun eachImageLandsOnTheRunningSlotAndIsJudged() {
        val plan = FlashPlanner.plan(
            phone,
            device,
            listOf(
                image("boot", 0x6000000),
                image("abl", 0x100000),
                image("persist", 0x100000),
                image("system", 0x40000000),
                image("vendor_boot", 0x7000000),
            ),
        )
        val by = plan.rows.associateBy { it.image.partition }
        assertEquals("boot_a", by.getValue("boot").destination)
        assertEquals(PlanVerdict.FITS, by.getValue("boot").verdict)
        assertTrue(by.getValue("boot").measured)
        assertEquals(PlanVerdict.NAMED_CONFIRMATION, by.getValue("abl").verdict)
        assertEquals(PlanVerdict.REFUSED, by.getValue("persist").verdict)
        // The bootloader does not show logical partitions: system lives inside super.
        assertTrue(by.getValue("system").reasons.first().startsWith("lives inside super"))
        assertNull(by.getValue("vendor_boot").destination)
        assertTrue(plan.refusals.isEmpty())
    }

    @Test
    fun tooBigDuplicatesSuperMembersAndTheWholeSetAreRefused() {
        val stockBoot = image("boot", 0x6000000, built = false).copy(path = "firmware/extracted/boot.img")
        val plan = FlashPlanner.plan(
            phone,
            device,
            listOf(image("boot", 0x6000001), stockBoot, image("super", 0x300000000, sparse = true), image("system", 1)),
        )
        val boots = plan.rows.filter { it.image.partition == "boot" }
        assertTrue(boots.all { it.verdict == PlanVerdict.REFUSED })
        assertTrue(boots.first().reasons.any { it.startsWith("is ") })
        assertTrue("super.img is picked too and already holds it" in plan.rows.last().reasons)
        val locked = phone.copy(
            values = phone.values + ("unlocked" to "no") + ("snapshot-update-status" to "merging") + ("product" to "x"),
        )
        assertEquals(3, FlashPlanner.plan(locked, device, emptyList()).refusals.size)
    }

    @Test
    fun withoutAPhoneTheDeviceConfigurationIsAssumed() {
        val plan = FlashPlanner.plan(null, device, listOf(image("boot", device.bootPartitionBytes + 1)))
        val row = plan.rows.single()
        assertEquals("boot_a", row.destination)
        assertFalse(row.measured)
        assertEquals(PlanVerdict.REFUSED, row.verdict)
        assertTrue(plan.notes.single().startsWith("No phone read"))
        // Firmware partitions have a slot on an A/B phone; super and the identity ones do not.
        val assumed = FlashPlanner.plan(
            null,
            device,
            listOf(image("xbl_config", 1), image("persist", 1), image("super", 1)),
        )
        assertEquals(listOf("xbl_config_a", "persist", "super"), assumed.rows.map { it.destination })
    }
}
