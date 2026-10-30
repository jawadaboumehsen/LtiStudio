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

class SuperblockTest {
    @Test
    fun eachToolsSuperblockLinesBecomeTheFieldsWorthComparing() {
        // fw-baseline's stock odm.img (dump.erofs) and a 32 MiB read-only F2FS (dump.f2fs 1.17.0), trimmed.
        val erofs = """
            Filesystem magic number:                      0xE0F5E1E2
            Filesystem blocksize:                         4096
            Filesystem blocks:                            190
            Filesystem inode count:                       17
            Filesystem created:                           Thu Jan  1 02:00:00 2009
            Filesystem features:                          sb_csum mtime xattr_filter 0padding
            Filesystem UUID:                              c2704d7d-9092-5e6a-bafe-608ca1b62102
            Required upstream Linux kernel version:       5.4
        """.trimIndent()
        assertEquals(
            listOf(
                "Filesystem blocksize" to "4096",
                "Filesystem blocks" to "190",
                "Filesystem inode count" to "17",
                "Filesystem features" to "sb_csum mtime xattr_filter 0padding",
                "Filesystem created" to "Thu Jan  1 02:00:00 2009",
                "Filesystem UUID" to "c2704d7d-9092-5e6a-bafe-608ca1b62102",
                "Required upstream Linux kernel version" to "5.4",
            ),
            Superblock.fields(FsType.Erofs, erofs),
        )
        val f2fs = """
            Info: MKFS version
              "6.18.33.2-microsoft-standard-WSL2"
            Info: superblock features = 4000 :  ro
            Info: Segments per section = 1
            Info: Sections per zone = 1
            Info: total FS sectors = 65536 (32 MB)
            Info: checkpoint state = 185 :  trimmed nat_bits compacted_summary unmount
            Done: 0.000185 secs
        """.trimIndent()
        assertEquals(
            listOf(
                "superblock features" to "4000 :  ro",
                "total FS sectors" to "65536 (32 MB)",
                "Segments per section" to "1",
                "Sections per zone" to "1",
                "checkpoint state" to "185 :  trimmed nat_bits compacted_summary unmount",
            ),
            Superblock.fields(FsType.F2fs, f2fs),
        )
        assertEquals(
            listOf("Filesystem volume name" to "odm", "Block count" to "8192", "Free blocks" to "7667"),
            Superblock.fields(
                FsType.Ext4,
                "Filesystem volume name:   odm\nLast mounted on:          <not available>\n" +
                    "Block count:              8192\nFree blocks:              7667\n",
            ),
        )
    }
}
