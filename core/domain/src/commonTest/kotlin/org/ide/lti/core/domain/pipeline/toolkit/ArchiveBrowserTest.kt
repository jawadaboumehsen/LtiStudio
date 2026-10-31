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
import kotlin.test.assertTrue

/** `ArchiveBrowser.SCRIPT`'s output as 7-Zip 26.05 printed it in WSL for PQ84P01's update.zip and a nested sample. */
class ArchiveBrowserTest {
    @Test
    fun aZipListsItsMembersWithSizesAndMethod() {
        val listing = ArchiveBrowser.parse(
            """
            magic 504b03040a00000800000000213a98e8d1acae020000ae0200001d000400
            Path = META-INF/com/android/metadata
            Folder = -
            Size = 686
            Packed Size = 686
            Modified = 2009-01-01 00:00:00
            Created =
            CRC = ACD1E898
            Method = Store

            Path = payload.bin
            Folder = -
            Size = 7232743564
            Packed Size = 7232743564
            Method = Store
            """.trimIndent(),
            "firmware/downloaded/update.zip",
        )
        assertEquals(PackageFormat.Zip, listing.format)
        assertEquals(
            ArchiveMember("payload.bin", 7232743564, 7232743564, "Store"),
            listing.members.last(),
        )
        assertEquals(2, listing.members.size)
    }

    @Test
    fun aSingleStreamHasOneMemberNamedAfterTheFile() {
        // 7-Zip gives an xz stream no Path: the member is the file without ".xz".
        val xz = ArchiveBrowser.parse(
            "magic fd377a585a000004e6d6b446\nSize = 5\nPacked Size = 64\nMethod = LZMA2:23 CRC64\n",
            "boot.img.xz",
        )
        assertEquals(listOf(ArchiveMember("boot.img", 5, 64, "LZMA2:23 CRC64")), xz.members)
        // 7-Zip cannot read lz4: the script says so and the member is named the same way.
        val lz4 = ArchiveBrowser.parse("magic 04224d1864608540\nlz4\n", "super.img.lz4")
        assertEquals(PackageFormat.Lz4, lz4.format)
        assertEquals(listOf(ArchiveMember("super.img", null, null, "LZ4")), lz4.members)
    }

    @Test
    fun aMemberThatIsNotAnArchiveIsSaidSo() {
        val payload = ArchiveBrowser.parse(
            "magic 437241550000000000000002000000000003ee77\nnotarchive payload.bin\n",
            "payload.bin",
        )
        assertTrue(payload.notArchive)
        assertEquals(PackageFormat.AndroidPayload, payload.format)
        assertTrue(payload.members.isEmpty())
    }

    @Test
    fun tarFoldersAreKeptApart() {
        val tar = ArchiveBrowser.parse(
            "magic 612e7478740000\nPath = d\nFolder = +\nSize = 0\n\nPath = d/b.bin\nFolder = -\nSize = 5000\n" +
                "Packed Size = 5120\n",
            "t.tar",
        )
        assertEquals(listOf(true, false), tar.members.map { it.folder })
        assertEquals(null, tar.members.last().method)
    }
}
