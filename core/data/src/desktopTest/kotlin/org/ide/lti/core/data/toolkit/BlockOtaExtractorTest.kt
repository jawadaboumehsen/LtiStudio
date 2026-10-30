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

import org.ide.lti.core.domain.pipeline.toolkit.BlockOtaExtractor
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockOtaExtractorTest {

    @Test
    fun generatesBrotliDecompressionCommand() {
        val cmd = BlockOtaExtractor.decompressBrotliCommand(
            brPath = "/ws/firmware/extracted/system.new.dat.br",
            outPath = "/ws/firmware/extracted/system.new.dat",
            workingDir = "/ws",
        )
        assertEquals("brotli", cmd.toolId)
        assertEquals(
            listOf(
                "-d",
                "-f",
                "/ws/firmware/extracted/system.new.dat.br",
                "-o",
                "/ws/firmware/extracted/system.new.dat",
            ),
            cmd.args,
        )
        assertEquals("/ws", cmd.workingDir)
    }

    @Test
    fun generatesSdat2imgCommand() {
        val cmd = BlockOtaExtractor.sdat2imgCommand(
            transferListPath = "/ws/firmware/extracted/system.transfer.list",
            newDatPath = "/ws/firmware/extracted/system.new.dat",
            outImagePath = "/ws/firmware/extracted/system.img",
            workingDir = "/ws",
        )
        assertEquals("sdat2img.py", cmd.toolId)
        assertEquals(
            listOf(
                "/ws/firmware/extracted/system.transfer.list",
                "/ws/firmware/extracted/system.new.dat",
                "/ws/firmware/extracted/system.img",
            ),
            cmd.args,
        )
        assertEquals("/ws", cmd.workingDir)
    }

    @Test
    fun discoversPartitionsFromArchiveMembers() {
        val members = listOf(
            "META-INF/com/google/android/updater-script",
            "system.transfer.list",
            "system.new.dat.br",
            "system.patch.dat",
            "vendor.transfer.list",
            "vendor.new.dat",
            "boot.img",
        )
        val partitions = BlockOtaExtractor.discoverPartitions(members)
        assertEquals(listOf("system", "vendor"), partitions)
    }

    @Test
    fun generatesConversionStepsForBrotliCompressedPartition() {
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
    fun generatesConversionStepsForUncompressedPartition() {
        val steps = BlockOtaExtractor.conversionSteps(
            partition = "vendor",
            isBrotli = false,
            sourceDir = "/ws/firmware/extracted",
            outDir = "/ws/firmware/extracted",
            workingDir = "/ws",
        )
        assertEquals(1, steps.size)
        assertEquals("sdat2img.py", steps[0].toolId)
    }

    @Test
    fun constructsIncrementalRefusalMatchingSpecification() {
        val refusal = BlockOtaExtractor.incrementalRefusal(
            item = "system.transfer.list",
            details = "Found bsdiff operations in transfer list",
        )
        assertEquals("The package is an incremental update.", refusal.cause)
        assertEquals("system.transfer.list", refusal.item)
        assertEquals("Import the full update instead.", refusal.nextStep)
        assertEquals("Found bsdiff operations in transfer list", refusal.details)
    }
}
