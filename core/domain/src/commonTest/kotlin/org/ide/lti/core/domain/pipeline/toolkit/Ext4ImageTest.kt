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

import org.ide.lti.core.domain.pipeline.text.FstabEditor
import org.ide.lti.core.model.workspace.BuildSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class Ext4ImageTest {
    @Test
    fun theImageIsSizedFromATrialBuild() {
        // PQ84P01's vendor tree (fw-baseline): MEASURE_SCRIPT's output, then the trial's dumpe2fs.
        val measure = Ext4Image.parseMeasure("1929366941 4059\n")!!
        assertEquals(4123, measure.inodes)
        val trial = Ext4Image.trialBlocks(measure, 4096)
        assertEquals(530391, trial)
        val free = Ext4Image.parseFreeBlocks("Block count:              530391\nFree blocks:              76229\n")!!
        // The 454162 blocks the trial used, 1% more and 64 spare: built that way, e2fsck passes with 1% free.
        assertEquals(458767, Ext4Image.finalBlocks(trial, free))
    }

    @Test
    fun anF2fsImageIsSizedFromSloadsMaxImageSize() {
        // fw-baseline vendor: PREPARE_SCRIPT's output, then the trial sload's check line.
        val prepared = F2fsImage.parsePrepare("1929366941 4059 0:2000\n")!!
        assertEquals("0:2000", prepared.rootOwner)
        assertEquals(2172481536, F2fsImage.trialBytes(prepared.measure))
        val max = F2fsImage.parseMaxImageMib(
            "…Free segments: 0x8\n[FSCK] Max image size: 1884 MB, Free space: 186 MB",
        )!!
        // 1884 + 6 MiB (AOSP) ran out of space; 1918 MiB built and passed fsck with 32 MiB free.
        assertEquals(2011168768, F2fsImage.finalBytes(max))
        assertEquals(32L * 1024 * 1024, F2fsImage.finalBytes(16), "make_f2fs refuses a smaller volume")
    }

    @Test
    fun aReadBackSymlinkTakesTheModeItsSidecarAsks() {
        val output = "0 2000 755 /w/out/verify/vendor\n0 0 777 /w/out/verify/vendor/lib64/x.so\n" +
            "0 0 L /w/out/verify/vendor/lib64/libEGL.so\n#--\n# file: /w/out/verify/vendor\nsecurity.selinux=\"l\"\n"
        val fsConfig = "/ 0 2000 755 capabilities=0x0\nvendor/lib64/libEGL.so 0 0 644 capabilities=0x0\n"
        val (stat, labels) = F2fsImage.splitReadback(output, "vendor", "/w/out/verify/vendor", fsConfig)
        assertEquals("0 0 644 /w/out/verify/vendor/lib64/libEGL.so", stat.lines()[2])
        assertEquals("0 0 777 /w/out/verify/vendor/lib64/x.so", stat.lines()[1], "a real file keeps its mode")
        assertEquals("# file: /w/out/verify/vendor", labels.lines().first())
    }

    @Test
    fun eachPartitionTakesItsOwnFilesystemOrTheStages() {
        val build = BuildSettings(filesystem = "erofs", partitionFilesystem = mapOf("vendor" to "ext4"))
        assertEquals("ext4", BuildFilesystems.of(build, "vendor"))
        assertEquals("erofs", BuildFilesystems.of(build, "system"))
    }
}

class MountTableTest {
    // The first-stage fstab of PQ84P01's vendor_boot and its vendor/etc copy (fw-baseline), trimmed.
    private val fstab = listOf(
        "system  /system  erofs  ro  wait,slotselect,avb=vbmeta_system,logical,first_stage_mount",
        "system  /system  ext4  ro,barrier=1,discard  wait,slotselect,avb=vbmeta_system,logical,first_stage_mount",
        "vendor  /vendor  erofs  ro  wait,slotselect,avb=vbmeta,logical,first_stage_mount",
        "/dev/block/bootdevice/by-name/userdata  /data  f2fs  noatime,nosuid  latemount,wait,check",
        "# vendor  /vendor  ext4  ro  wait,logical",
    )
    private val output = (
        fstab.map { "work/vendor/etc/fstab.qcom\t$it" } +
            fstab.map { "vendor_boot.img:first_stage_ramdisk/fstab.qcom\t$it" }
        ).joinToString("\n")

    @Test
    fun onlyTheMountTablesThatDeclareAPartitionWithoutItsFilesystemAreReported() {
        val entries = MountTable.parse(output)
        assertEquals(6, entries.size, "logical lines only, comments left out")
        assertEquals(emptyList(), MountTable.missing(entries, mapOf("system" to "ext4", "vendor" to "erofs")))
        assertEquals(
            listOf("work/vendor/etc/fstab.qcom", "vendor_boot.img:first_stage_ramdisk/fstab.qcom"),
            MountTable.missing(entries, mapOf("vendor" to "ext4", "odm" to "ext4")).map { it.source },
        )
    }

    @Test
    fun aMissingLineIsAddedAfterTheStockOne() {
        val text = fstab.joinToString("\n")
        val edited = FstabEditor.withFilesystems(text, mapOf("/vendor" to "ext4", "/system" to "ext4"))
        val lines = edited.lines()
        assertEquals(fstab.size + 1, lines.size, "system already mounts as ext4")
        assertEquals(
            "vendor  /vendor  ext4  ro,barrier=1,discard  wait,slotselect,avb=vbmeta,logical,first_stage_mount",
            lines[3],
        )
        assertEquals(edited, FstabEditor.withFilesystems(edited, mapOf("/vendor" to "ext4")), "added once")
        assertEquals(listOf("/system", "/vendor"), FstabEditor.logicalMountPoints(text))
    }
}
