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

import org.ide.lti.core.domain.pipeline.toolkit.OdinExtractor
import org.ide.lti.core.domain.pipeline.toolkit.OdinSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OdinExtractorTest {

    @Test
    fun classifiesOdinSlotsFromFileName() {
        assertEquals(OdinSlot.AP, OdinExtractor.classifySlot("AP_G998BXXU1AUB6.tar.md5"))
        assertEquals(OdinSlot.BL, OdinExtractor.classifySlot("BL_G998BXXU1AUB6.tar.md5"))
        assertEquals(OdinSlot.CP, OdinExtractor.classifySlot("CP_G998BXXU1AUB6.tar.md5"))
        assertEquals(OdinSlot.CSC, OdinExtractor.classifySlot("CSC_OXM_G998BXXU1AUB6.tar.md5"))
        assertEquals(OdinSlot.HOME_CSC, OdinExtractor.classifySlot("HOME_CSC_OXM_G998BXXU1AUB6.tar.md5"))
        assertEquals(null, OdinExtractor.classifySlot("custom_recovery.tar"))
    }

    @Test
    fun detectsTarMd5Extension() {
        assertTrue(OdinExtractor.isTarMd5("AP_file.tar.md5"))
        assertTrue(OdinExtractor.isTarMd5("recovery.md5"))
        assertFalse(OdinExtractor.isTarMd5("AP_file.tar"))
        assertFalse(OdinExtractor.isTarMd5("firmware.zip"))
    }

    @Test
    fun generatesTarMd5VerificationCommand() {
        val cmd = OdinExtractor.verifyMd5Command(
            tarMd5Path = "/ws/firmware/AP_G998B.tar.md5",
            workingDir = "/ws",
        )
        assertEquals("sh", cmd.toolId)
        assertTrue(cmd.args.any { it.contains("md5sum") && it.contains("grep -aoE") })
        // The path is the script's $1, never part of the script text.
        assertEquals("/ws/firmware/AP_G998B.tar.md5", cmd.args.last())
        assertEquals("/ws", cmd.workingDir)
    }

    @Test
    fun generatesTarExtractionCommand() {
        val cmd = OdinExtractor.extractTarCommand(
            tarPath = "/ws/firmware/AP_G998B.tar.md5",
            destDir = "/ws/firmware/extracted",
            workingDir = "/ws",
        )
        assertEquals("tar", cmd.toolId)
        assertEquals(listOf("-xf", "/ws/firmware/AP_G998B.tar.md5", "-C", "/ws/firmware/extracted"), cmd.args)
        assertEquals("/ws", cmd.workingDir)
    }

    @Test
    fun generatesLz4DecompressCommand() {
        val cmd = OdinExtractor.decompressLz4Command(
            lz4Path = "/ws/firmware/extracted/boot.img.lz4",
            outImagePath = "/ws/firmware/extracted/boot.img",
            workingDir = "/ws",
        )
        // The paths are arguments of lz4 itself, never spliced into a shell script.
        assertEquals("lz4", cmd.toolId)
        assertEquals(
            listOf("-d", "-f", "/ws/firmware/extracted/boot.img.lz4", "/ws/firmware/extracted/boot.img"),
            cmd.args,
        )
    }

    @Test
    fun discoversPartitionNamesFromTarMembers() {
        val members = listOf(
            "boot.img.lz4",
            "recovery.img.lz4",
            "system.img.ext4",
            "userdata.img",
            "metadata.img.lz4",
            "vbmeta.img",
        )
        val partitions = OdinExtractor.discoverPartitions(members)
        assertEquals(listOf("boot", "recovery", "system", "userdata", "metadata", "vbmeta"), partitions)
    }

    @Test
    fun extractsDeclaredMd5Trailer() {
        val payload = "dummy tar archive content".encodeToByteArray()
        val md5Hex = "d41d8cd98f00b204e9800998ecf8427e"
        // `md5sum AP.tar >> AP.tar`: the hash, two spaces, the name and a newline after the tar data.
        val fullData = payload + "$md5Hex  AP_G998BXXU1AUB6.tar\n".encodeToByteArray()

        val extracted = OdinExtractor.extractDeclaredMd5(fullData)
        assertEquals(md5Hex, extracted)
    }
}
