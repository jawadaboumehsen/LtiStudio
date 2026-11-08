/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.text

import kotlin.test.Test
import kotlin.test.assertEquals

class FstabEditorTest {
    /** Lines of PQ84P01's `vendor/etc/fstab.qcom`. */
    private val system = "system  /system  erofs  ro  wait,slotselect,avb=vbmeta_system,logical,first_stage_mount"
    private val data = "/dev/block/bootdevice/by-name/userdata  /data  f2fs  noatime,nosuid,nodev,inlinecrypt  " +
        "latemount,wait,check,formattable,fileencryption=aes-256-xts:aes-256-cts:v2+inlinecrypt_optimized+" +
        "wrappedkey_v0,keydirectory=/metadata/vold/metadata_encryption,metadata_encryption=aes-256-xts:wrappedkey_v0," +
        "quota,reservedsize=128M,checkpoint=fs"
    private val sdcard = "/devices/platform/soc/8804000.sdhci/mmc_host*  /storage/sdcard1  vfat  nosuid,nodev  " +
        "wait,voldmanaged=sdcard1:auto,encryptable=footer"

    @Test
    fun onlyTheDataLineLosesItsEncryptionFlags() {
        val fstab = listOf("# comment /data", system, data, sdcard, "").joinToString("\n")
        val edited = FstabEditor.withoutDataEncryption(fstab).lines()
        assertEquals(
            "/dev/block/bootdevice/by-name/userdata  /data  f2fs  noatime,nosuid,nodev,inlinecrypt  " +
                "latemount,wait,check,formattable,quota,reservedsize=128M,checkpoint=fs",
            edited[2],
        )
        // Every other line, comments and the trailing newline stay byte for byte.
        assertEquals(listOf("# comment /data", system, sdcard, ""), edited - edited[2])
    }

    @Test
    fun theAvbAndVerityFlagsLeaveEveryLineAndNothingElseDoes() {
        val vendor = "vendor  /vendor  erofs  ro  wait,slotselect,avb=vbmeta,logical,first_stage_mount"
        val legacy = "/dev/block/by-name/system  /  ext4  ro  wait,verify,avb_keys=/avb/q-gsi.avbpubkey,avb"
        val fstab = listOf("# avb=vbmeta comment", system, vendor, legacy, data, "").joinToString("\n")
        assertEquals(
            listOf(
                "# avb=vbmeta comment",
                "system  /system  erofs  ro  wait,slotselect,logical,first_stage_mount",
                "vendor  /vendor  erofs  ro  wait,slotselect,logical,first_stage_mount",
                "/dev/block/by-name/system  /  ext4  ro  wait",
                data,
                "",
            ),
            FstabEditor.withoutIntegrity(fstab).lines(),
        )
    }

    @Test
    fun aDataLineWithNothingToRemoveIsLeftAlone() {
        val plain = "/dev/block/by-name/userdata /data ext4 noatime wait,check"
        assertEquals(plain, FstabEditor.withoutDataEncryption(plain))
        assertEquals(true, FstabEditor.mountsData(plain))
        assertEquals(false, FstabEditor.mountsData(system))
    }

    /** PQ84P01's work/vendor/etc/fstab.qcom: system (AVB and GSI keys) and /data (file and metadata encryption). */
    private val qcom = listOf(
        "system  /system  erofs  ro  wait,slotselect,avb=vbmeta_system,logical,first_stage_mount," +
            "avb_keys=/avb/q-gsi.avbpubkey:/avb/v-gsi.avbpubkey",
        "/dev/block/bootdevice/by-name/userdata  /data  f2fs  noatime,nosuid  latemount,wait,check,formattable," +
            "fileencryption=aes-256-xts:aes-256-cts:v2+inlinecrypt_optimized+wrappedkey_v0," +
            "keydirectory=/metadata/vold/metadata_encryption,metadata_encryption=aes-256-xts:wrappedkey_v0,quota",
    ).joinToString("\n")

    @Test
    fun rowsCarryTheFlagsAndOneFlagIsRemovedFromItsMountPointOnly() {
        val rows = FstabEditor.rows(qcom)
        assertEquals(listOf("/system", "/data"), rows.map { it.mountPoint })
        assertEquals(true, rows[0].flags.any(FstabEditor::isIntegrity))
        assertEquals(true, rows[1].flags.any(FstabEditor::isEncryption))

        val edited = FstabEditor.withoutFlags(qcom, listOf("/system" to "avb_keys", "/data" to "fileencryption"))
        val after = FstabEditor.rows(edited)
        assertEquals(
            listOf("wait", "slotselect", "avb=vbmeta_system", "logical", "first_stage_mount"),
            after[0].flags,
        )
        // Only fileencryption goes: the metadata encryption flags of /data stay until removed themselves.
        assertEquals(false, after[1].flags.any { it.startsWith("fileencryption") })
        assertEquals(true, after[1].flags.any { it.startsWith("metadata_encryption") })
        assertEquals(qcom, FstabEditor.withoutFlags(qcom, listOf("/vendor" to "avb")))
    }
}
