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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RamdiskCheckTest {
    @Test
    fun returnsCleanResultWhenArchiveMatchesFolder() {
        val folder = listOf(
            RamdiskEntry("init", 1024L),
            RamdiskEntry("default.prop", 256L),
            RamdiskEntry("etc/recovery.fstab", 512L),
        )
        val archive = listOf(
            RamdiskEntry("./init", 1024L),
            RamdiskEntry("default.prop", 256L),
            RamdiskEntry("/etc/recovery.fstab", 512L),
        )

        val result = RamdiskCheck.compare(archiveEntries = archive, folderEntries = folder)
        assertTrue(result.ok)
        assertTrue(result.differences.isEmpty())
        assertTrue(result.missingInArchive.isEmpty())
        assertTrue(result.extraInArchive.isEmpty())
        assertTrue(result.sizeDifferences.isEmpty())
    }

    @Test
    fun detectsMissingEntriesInArchive() {
        val folder = listOf(
            RamdiskEntry("init", 1024L),
            RamdiskEntry("system/bin/sh", 2048L),
        )
        val archive = listOf(
            RamdiskEntry("init", 1024L),
        )

        val result = RamdiskCheck.compare(archiveEntries = archive, folderEntries = folder)
        assertFalse(result.ok)
        assertEquals(listOf("system/bin/sh"), result.missingInArchive)
        assertEquals(1, result.differences.size)
        assertTrue(result.differences[0].contains("Missing in archive: system/bin/sh"))
    }

    @Test
    fun detectsExtraEntriesInArchive() {
        val folder = listOf(
            RamdiskEntry("init", 1024L),
        )
        val archive = listOf(
            RamdiskEntry("init", 1024L),
            RamdiskEntry("stray_file", 100L),
        )

        val result = RamdiskCheck.compare(archiveEntries = archive, folderEntries = folder)
        assertFalse(result.ok)
        assertEquals(listOf("stray_file"), result.extraInArchive)
        assertEquals(1, result.differences.size)
        assertTrue(result.differences[0].contains("Extra in archive: stray_file"))
    }

    @Test
    fun detectsSizeMismatches() {
        val folder = listOf(
            RamdiskEntry("init", 2048L),
        )
        val archive = listOf(
            RamdiskEntry("init", 1024L),
        )

        val result = RamdiskCheck.compare(archiveEntries = archive, folderEntries = folder)
        assertFalse(result.ok)
        assertEquals(1, result.sizeDifferences.size)
        assertTrue(result.differences[0].contains("Size mismatch for init"))
    }

    @Test
    fun serializesAndDeserializesRoundTrip() {
        val result = RamdiskCheckResult(
            ok = false,
            differences = listOf("Missing in archive: init"),
            missingInArchive = listOf("init"),
        )
        val json = RamdiskCheckResult.toJson(result)
        val parsed = RamdiskCheckResult.fromJson(json)
        assertEquals(result, parsed)
    }

    @Test
    fun aBootRamdiskWithoutInitIsRefusedButAVendorRamdiskIsNot() {
        val withoutInit = listOf("system/bin/sh", "./first_stage_ramdisk/fstab.qcom")
        assertNotNull(RamdiskCheck.missingInit("init_boot", withoutInit))
        assertNotNull(RamdiskCheck.missingInit("boot", withoutInit))
        assertNull(RamdiskCheck.missingInit("init_boot", withoutInit + "./init"))
        assertNull(RamdiskCheck.missingInit("vendor_boot", withoutInit))
    }
}
