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

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RefusalTest {

    @Test
    fun testValidRefusal() {
        val refusal = Refusal(
            cause = "The package is an incremental update.",
            item = "payload.bin",
            nextStep = "Import the full update instead.",
        )
        assertEquals("The package is an incremental update.", refusal.cause)
        assertEquals("payload.bin", refusal.item)
        assertEquals("Import the full update instead.", refusal.nextStep)
    }

    @Test
    fun testBlankCauseThrows() {
        assertFailsWith<IllegalArgumentException> {
            Refusal(
                cause = "   ",
                item = "system.img",
                nextStep = "Re-extract.",
            )
        }
    }

    @Test
    fun testBlankItemThrows() {
        assertFailsWith<IllegalArgumentException> {
            Refusal(
                cause = "Extraction failed.",
                item = "",
                nextStep = "Retry.",
            )
        }
    }

    @Test
    fun testBlankNextStepThrows() {
        assertFailsWith<IllegalArgumentException> {
            Refusal(
                cause = "Header corrupted.",
                item = "boot.img",
                nextStep = "   ",
            )
        }
    }

    @Test
    fun testSerializationRoundTrip() {
        val original = Refusal(
            cause = "Sparse format unsupported for this operation.",
            item = "vendor.img",
            nextStep = "Convert to raw image first.",
            details = "Exit code 1 from simg2img: invalid magic header",
        )
        val json = Json.encodeToString(Refusal.serializer(), original)
        val restored = Json.decodeFromString(Refusal.serializer(), json)

        assertEquals(original, restored)
        assertEquals("Exit code 1 from simg2img: invalid magic header", restored.details)
    }

    @Test
    fun testRefusalCatalogComplianceWithSc009() {
        val sampleSet = RefusalCatalog.ALL_SAMPLES
        assertTrue(sampleSet.isNotEmpty(), "Sample set must not be empty")

        sampleSet.forEach { refusal ->
            assertTrue(refusal.cause.isNotBlank(), "Refusal cause must not be blank")
            assertTrue(refusal.item.isNotBlank(), "Refusal item must not be blank")
            assertTrue(refusal.nextStep.isNotBlank(), "Refusal nextStep must not be blank")

            assertTrue(
                refusal.cause.endsWith("."),
                "Refusal cause must be a plain sentence ending with a period: '${refusal.cause}'",
            )
            assertTrue(
                refusal.nextStep.endsWith("."),
                "Refusal nextStep must be a plain sentence ending with a period: '${refusal.nextStep}'",
            )
            assertFalse(
                refusal.cause.contains("Exception") || refusal.cause.contains("Exit code"),
                "Refusal headline cause must not contain raw exceptions or exit codes: '${refusal.cause}'",
            )
        }
    }

    @Test
    fun testWorkspaceLockedNamesHolder() {
        val refusal = RefusalCatalog.workspaceLocked(holder = "StageDebloat")
        assertTrue(
            refusal.nextStep.contains("StageDebloat"),
            "Blocked start message must name lock holder: ${refusal.nextStep}",
        )
        assertEquals("Lock held by: StageDebloat", refusal.details)
    }
}
