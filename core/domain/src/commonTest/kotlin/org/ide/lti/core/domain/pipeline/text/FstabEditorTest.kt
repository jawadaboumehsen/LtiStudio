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
    fun aDataLineWithNothingToRemoveIsLeftAlone() {
        val plain = "/dev/block/by-name/userdata /data ext4 noatime wait,check"
        assertEquals(plain, FstabEditor.withoutDataEncryption(plain))
        assertEquals(true, FstabEditor.mountsData(plain))
        assertEquals(false, FstabEditor.mountsData(system))
    }
}
