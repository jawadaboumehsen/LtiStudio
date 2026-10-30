/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.toolkit

import org.ide.lti.core.domain.pipeline.toolkit.ArchiveExtractor
import org.ide.lti.core.domain.pipeline.toolkit.PackageFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ArchiveExtractorTest {
    @Test
    fun generates7zzCommandForSevenZAndZip() {
        val sevenZCmd = ArchiveExtractor.extractionCommand(
            format = PackageFormat.SevenZ,
            archivePath = "/ws/firmware/fw.7z",
            destDir = "/ws/firmware/extracted",
            workingDir = "/ws",
        )
        assertEquals("7zz", sevenZCmd.toolId)
        assertTrue(sevenZCmd.args.contains("x"))
        assertTrue(sevenZCmd.args.contains("-o/ws/firmware/extracted"))

        val zipCmd = ArchiveExtractor.extractionCommand(
            format = PackageFormat.Zip,
            archivePath = "/ws/firmware/fw.zip",
            destDir = "/ws/firmware/extracted",
            workingDir = "/ws",
        )
        // A ZIP is unpacked with unzip, which every setup installs.
        assertEquals("unzip", zipCmd.toolId)
        assertEquals(listOf("-o", "/ws/firmware/fw.zip", "-d", "/ws/firmware/extracted"), zipCmd.args)
    }

    @Test
    fun generates7zzCommandForRar() {
        val rarCmd = ArchiveExtractor.extractionCommand(
            format = PackageFormat.Rar,
            archivePath = "/ws/firmware/fw.rar",
            destDir = "/ws/firmware/extracted",
            workingDir = "/ws",
        )
        // The full 7-Zip reads RAR 1.5 to 5; no separate unrar is installed.
        assertEquals("7zz", rarCmd.toolId)
        assertTrue(rarCmd.args.contains("x"))
    }

    @Test
    fun generatesTarCommandForTar() {
        val tarCmd = ArchiveExtractor.extractionCommand(
            format = PackageFormat.Tar,
            archivePath = "/ws/firmware/fw.tar",
            destDir = "/ws/firmware/extracted",
            workingDir = "/ws",
        )
        assertEquals("tar", tarCmd.toolId)
        assertEquals(listOf("-xf", "/ws/firmware/fw.tar", "-C", "/ws/firmware/extracted"), tarCmd.args)
    }

    @Test
    fun generatesSingleFileCompressorCommands() {
        for (format in listOf(
            PackageFormat.Gz,
            PackageFormat.Xz,
            PackageFormat.Lz4,
            PackageFormat.Bz2,
            PackageFormat.Zstd,
            PackageFormat.Brotli,
        )) {
            val cmd = ArchiveExtractor.extractionCommand(
                format = format,
                archivePath = "/ws/firmware/super.img.${format.name.lowercase()}",
                destDir = "/ws/firmware/extracted",
                workingDir = "/ws",
            )
            assertEquals("sh", cmd.toolId)
            assertTrue(cmd.args.any { it.contains("super.img") })
        }
    }

    @Test
    fun throwsOnNonArchiveFormats() {
        assertFailsWith<IllegalArgumentException> {
            ArchiveExtractor.extractionCommand(
                format = PackageFormat.Unknown,
                archivePath = "/ws/firmware/fw.bin",
                destDir = "/ws/firmware/extracted",
                workingDir = "/ws",
            )
        }
    }
}
