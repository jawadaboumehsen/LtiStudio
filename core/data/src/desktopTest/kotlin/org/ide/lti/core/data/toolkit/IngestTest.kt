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
import org.ide.lti.core.domain.pipeline.toolkit.BlockOtaExtractor
import org.ide.lti.core.domain.pipeline.toolkit.ChunkExtractor
import org.ide.lti.core.domain.pipeline.toolkit.FirmwareLayout
import org.ide.lti.core.domain.pipeline.toolkit.FirmwareLayouts
import org.ide.lti.core.domain.pipeline.toolkit.FormatDetector
import org.ide.lti.core.domain.pipeline.toolkit.OdinExtractor
import org.ide.lti.core.domain.pipeline.toolkit.OdinSlot
import org.ide.lti.core.domain.pipeline.toolkit.PackageFormat
import org.ide.lti.core.domain.pipeline.toolkit.PayloadExtractor
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IngestTest {

    @Test
    fun testZipPackageShapeIngestion() {
        val tempDir = Files.createTempDirectory("lti-ingest-zip-").toFile()
        try {
            val zipFile = File(tempDir, "firmware.zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                zos.putNextEntry(ZipEntry("boot.img"))
                zos.write("dummy boot image".encodeToByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("system.img"))
                zos.write("dummy system image".encodeToByteArray())
                zos.closeEntry()
            }

            val headerBytes = zipFile.inputStream().use { it.readNBytes(16) }
            val detected = FormatDetector.detect(headerBytes)
            assertEquals(PackageFormat.Zip, detected)

            val destDir = File(tempDir, "extracted")
            destDir.mkdirs()

            val cmd = ArchiveExtractor.extractionCommand(
                format = detected,
                archivePath = zipFile.absolutePath,
                destDir = destDir.absolutePath,
                workingDir = tempDir.absolutePath,
            )
            assertEquals("unzip", cmd.toolId)
            assertEquals(listOf("-o", zipFile.absolutePath, "-d", destDir.absolutePath), cmd.args)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testAndroidPayloadShapeStreamsDirectlyAndDetectsIncremental() {
        val tempDir = Files.createTempDirectory("lti-ingest-payload-").toFile()
        try {
            val payloadFile = File(tempDir, "payload.bin")
            // Magic CrAU + version 2 + header
            val header = byteArrayOf(0x43, 0x72, 0x41, 0x55, 0, 0, 0, 0, 0, 0, 0, 2)
            payloadFile.writeBytes(header)

            val detected = FormatDetector.detect(payloadFile.readBytes())
            assertEquals(PackageFormat.AndroidPayload, detected)

            val destDir = File(tempDir, "extracted")
            destDir.mkdirs()

            val cmd = PayloadExtractor.extractionCommand(
                archiveOrPayloadPath = payloadFile.absolutePath,
                destDir = destDir.absolutePath,
                workingDir = tempDir.absolutePath,
            )
            assertEquals("payload-dumper-go", cmd.toolId)
            assertEquals(listOf("-o", destDir.absolutePath, payloadFile.absolutePath), cmd.args)

            // Incremental detection from metadata
            val incMeta = "pre-build=OPR6.170623.013\npost-build=OPM1.171019.011\nota-type=AB"
            assertTrue(PayloadExtractor.isIncrementalMetadata(incMeta))

            val refusal = PayloadExtractor.incrementalRefusal(payloadFile.absolutePath)
            assertTrue(refusal.cause.isNotBlank())
            assertEquals(payloadFile.absolutePath, refusal.item)
            assertTrue(refusal.nextStep.isNotBlank())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testBlockOtaShapeDiscoversAndPlansConversion() {
        val members = listOf(
            "META-INF/com/google/android/updater-script",
            "system.transfer.list",
            "system.new.dat.br",
            "vendor.transfer.list",
            "vendor.new.dat",
            "boot.img",
        )
        assertEquals(FirmwareLayout.BlockOta, FirmwareLayouts.classify(members))

        val partitions = BlockOtaExtractor.discoverPartitions(members)
        assertEquals(listOf("system", "vendor"), partitions)

        val steps = BlockOtaExtractor.conversionSteps(
            partition = "system",
            isBrotli = true,
            sourceDir = "/ws/firmware/extracted",
            outDir = "/ws/firmware/extracted",
            workingDir = "/ws",
        )
        assertEquals(2, steps.size)
        assertEquals("brotli", steps[0].toolId)
        assertEquals("sdat2img.py", steps[1].toolId)
    }

    @Test
    fun testOdinSetShapeExtractsTrailerAndPlansExtraction() {
        val tempDir = Files.createTempDirectory("lti-ingest-odin-").toFile()
        try {
            val md5Hex = "d41d8cd98f00b204e9800998ecf8427e"
            val tarContent = "dummy tar payload".encodeToByteArray()
            val odinFile = File(tempDir, "AP_G998BXXU1AUB6.tar.md5")
            odinFile.writeBytes(tarContent + "$md5Hex  AP_G998BXXU1AUB6.tar\n".encodeToByteArray())

            assertEquals(OdinSlot.AP, OdinExtractor.classifySlot(odinFile.name))
            assertTrue(OdinExtractor.isTarMd5(odinFile.name))

            val extractedMd5 = OdinExtractor.extractDeclaredMd5(odinFile.readBytes())
            assertEquals(md5Hex, extractedMd5)

            val verifyCmd = OdinExtractor.verifyMd5Command(odinFile.absolutePath, tempDir.absolutePath)
            assertEquals("sh", verifyCmd.toolId)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testSparseChunkShapeGroupsAndSortsNumerically() {
        val chunks = listOf(
            "/ws/super.img_sparsechunk.2",
            "/ws/super.img_sparsechunk.0",
            "/ws/super.img_sparsechunk.1",
        )
        val grouped = ChunkExtractor.groupSparseChunks(chunks)
        assertEquals(1, grouped.size)
        assertEquals(
            listOf(
                "/ws/super.img_sparsechunk.0",
                "/ws/super.img_sparsechunk.1",
                "/ws/super.img_sparsechunk.2",
            ),
            grouped["super"],
        )

        val joinCmd = ChunkExtractor.joinChunksCommand(
            chunkPaths = grouped["super"]!!,
            outImagePath = "/ws/super.img",
            workingDir = "/ws",
        )
        assertEquals("simg2img", joinCmd.toolId)
        assertEquals(
            listOf(
                "/ws/super.img_sparsechunk.0",
                "/ws/super.img_sparsechunk.1",
                "/ws/super.img_sparsechunk.2",
                "/ws/super.img",
            ),
            joinCmd.args,
        )
    }

    @Test
    fun testNtpiPackageRefusesWithPlainReason() {
        val ntpiHeader = byteArrayOf(0x4E, 0x54, 0x50, 0x49) // NTPI
        val format = FormatDetector.detect(ntpiHeader)
        assertEquals(PackageFormat.Ntpi, format)

        val refusal = ChunkExtractor.ntpiRefusal("firmware.ntpi")
        assertTrue(refusal.cause.contains("NTPI"))
        assertEquals("firmware.ntpi", refusal.item)
        assertTrue(refusal.nextStep.isNotBlank())
    }
}
