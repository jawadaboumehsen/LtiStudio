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

import org.ide.lti.core.domain.pipeline.toolkit.PayloadExtractor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PayloadExtractorTest {

    @Test
    fun generatesStreamingExtractionCommandWithoutTempCopy() {
        val cmd = PayloadExtractor.extractionCommand(
            archiveOrPayloadPath = "/ws/firmware/full-ota.zip",
            destDir = "/ws/firmware/extracted",
            workingDir = "/ws",
        )
        assertEquals("payload-dumper-go", cmd.toolId)
        assertEquals(listOf("-o", "/ws/firmware/extracted", "/ws/firmware/full-ota.zip"), cmd.args)
        assertEquals("/ws", cmd.workingDir)
    }

    @Test
    fun supportsPartitionFilteringAndConcurrency() {
        val cmd = PayloadExtractor.extractionCommand(
            archiveOrPayloadPath = "/ws/firmware/payload.bin",
            destDir = "/ws/firmware/extracted",
            workingDir = "/ws",
            partitions = listOf("boot", "system", "vendor"),
            concurrency = 4,
        )
        assertEquals("payload-dumper-go", cmd.toolId)
        assertEquals(
            listOf(
                "-o",
                "/ws/firmware/extracted",
                "-partitions",
                "boot,system,vendor",
                "-concurrency",
                "4",
                "/ws/firmware/payload.bin",
            ),
            cmd.args,
        )
    }

    @Test
    fun detectsIncrementalFromMetadata() {
        val fullMeta = """
            post-build=google/walleye/walleye:8.1.0/OPM1.171019.011/4448085:user/release-keys
            post-timestamp=1508892404
            ota-type=AB
            ota-required-cache=0
        """.trimIndent()
        assertFalse(PayloadExtractor.isIncrementalMetadata(fullMeta))

        val incMeta = """
            pre-build=google/walleye/walleye:8.0.0/OPR6.170623.013/4374187:user/release-keys
            pre-device=walleye
            post-build=google/walleye/walleye:8.1.0/OPM1.171019.011/4448085:user/release-keys
            ota-type=AB
        """.trimIndent()
        assertTrue(PayloadExtractor.isIncrementalMetadata(incMeta))
    }

    @Test
    fun constructsIncrementalRefusalMatchingSpecification() {
        val refusal = PayloadExtractor.incrementalRefusal(
            item = "/ws/firmware/incremental-ota.zip",
            details = "pre-build: OPR6.170623.013",
        )
        assertEquals("The package is an incremental update.", refusal.cause)
        assertEquals("/ws/firmware/incremental-ota.zip", refusal.item)
        assertEquals("Import the full update instead.", refusal.nextStep)
        assertEquals("pre-build: OPR6.170623.013", refusal.details)
    }
}
