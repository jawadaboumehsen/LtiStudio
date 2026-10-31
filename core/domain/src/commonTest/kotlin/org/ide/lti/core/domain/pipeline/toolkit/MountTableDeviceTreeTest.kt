/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.toolkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `MountTable.SCRIPT`'s device-tree lines as it printed them in WSL for a header v2 boot.img and a dtbo. */
class MountTableDeviceTreeTest {
    private val deviceTree = """
        boot.img:dtb	system /system erofs ro,barrier=1 wait,slotselect,avb,logical,first_stage_mount
        boot.img:dtb	#vendor /vendor ext4 ro wait,logical
        dtbo.img:dt.0	system /system erofs ro,barrier=1 wait,slotselect,avb,logical,first_stage_mount
        dtbo.img:dt.0	#vendor /vendor ext4 ro wait,logical
    """.trimIndent()

    @Test
    fun theDeviceTreeFstabIsReadWithItsDisabledNodes() {
        val tree = MountTable.deviceTree(deviceTree)
        assertTrue(tree.inUse, "no ramdisk fstab: first-stage init reads the device tree")
        assertEquals(4, tree.mounts.size)
        val system = tree.mounts.first()
        assertEquals(
            MountTable.DeviceTreeMount(
                "boot.img:dtb",
                "/system",
                "system",
                "erofs",
                "ro,barrier=1",
                "wait,slotselect,avb,logical,first_stage_mount",
                disabled = false,
            ),
            system,
        )
        assertTrue(tree.mounts[1].disabled && tree.mounts[1].device == "vendor")
    }

    @Test
    fun aDeviceTreeInUseIsCheckedLikeAnyMountTable() {
        // system is built as ext4: the device tree mounts it only as erofs. The disabled vendor node does not count.
        val missing = MountTable.missing(MountTable.parse(deviceTree), mapOf("system" to "ext4", "vendor" to "f2fs"))
        assertEquals(setOf("boot.img:dtb", "dtbo.img:dt.0"), missing.map { it.source }.toSet())
        assertTrue(missing.all { it.partition == "system" && it.declared == setOf("erofs") })
    }

    @Test
    fun aRamdiskFstabWinsOverTheDeviceTree() {
        val withRamdisk = deviceTree + "\nvendor_boot.img:first_stage_ramdisk/fstab.qcom\tsystem /system ext4 ro " +
            "wait,avb,logical,first_stage_mount"
        assertFalse(MountTable.deviceTree(withRamdisk).inUse)
        assertEquals(
            listOf("vendor_boot.img:first_stage_ramdisk/fstab.qcom"),
            MountTable.parse(withRamdisk).map { it.source },
        )
        assertTrue(MountTable.missing(MountTable.parse(withRamdisk), mapOf("system" to "ext4")).isEmpty())
        // PQ84P01: no device tree declares an fstab.
        assertTrue(
            MountTable.deviceTree("work/vendor/etc/fstab.qcom\tsystem /system erofs ro wait,logical").mounts.isEmpty(),
        )
    }
}
