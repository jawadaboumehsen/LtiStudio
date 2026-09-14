/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.model.run.StageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PipelineDefinitionRegistryTest {

    @Test
    fun `each of the 8 IDs encodes to its exact name string`() {
        val json = Json { encodeDefaults = true }
        StageId.entries.forEach { id ->
            val encoded = json.encodeToString(id)
            assertEquals("\"${id.name}\"", encoded)
        }
        assertEquals(8, StageId.entries.size)
    }

    @Test
    fun `registry order is exactly the eight in the documented order and is not StageId entries`() {
        val expectedOrder = listOf(
            StageId.FIRMWARE_ACQUISITION,
            StageId.FIRMWARE_EXTRACTION,
            StageId.WORK_TREE_ASSEMBLY,
            StageId.DEBLOAT,
            StageId.MODULE_APPLICATION,
            StageId.BUILD_FLASHABLE_ZIP,
            StageId.GENERATE_OTA_MANIFEST,
            StageId.PUBLISH_RELEASE,
        )

        val registry = PipelineDefinitionRegistry()
        assertEquals(expectedOrder, registry.order)

        // It is not StageId.entries (which could be reordered)
        val shuffledMap = DefaultPipelineStages.all.associateBy { it.id }.entries.shuffled().associate {
            it.key to
                it.value
        }
        val registryWithShuffled = PipelineDefinitionRegistry(shuffledMap)
        assertEquals(expectedOrder, registryWithShuffled.order)
    }

    @Test
    fun `executableStages has 7 entries ending with GENERATE_OTA_MANIFEST`() {
        val registry = PipelineDefinitionRegistry()
        val stages = registry.executableStages()
        assertEquals(7, stages.size)
        assertEquals(StageId.GENERATE_OTA_MANIFEST, stages.last().id)
    }

    @Test
    fun `a map missing DEBLOAT throws IllegalStateException mentioning DEBLOAT`() {
        val mapWithoutDebloat = DefaultPipelineStages.all.filter { it.id != StageId.DEBLOAT }.associateBy { it.id }
        val registry = PipelineDefinitionRegistry(mapWithoutDebloat)

        val ex = assertFailsWith<IllegalStateException> {
            registry.executableStages()
        }
        assertTrue(ex.message!!.contains("DEBLOAT"), "Exception should mention DEBLOAT")
    }
}
