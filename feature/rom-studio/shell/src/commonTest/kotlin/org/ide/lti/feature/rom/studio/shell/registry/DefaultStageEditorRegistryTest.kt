/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.shell.registry

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.ide.lti.core.domain.pipeline.PipelineStageSequence
import org.ide.lti.core.model.run.StageId
import org.ide.lti.feature.rom.studio.api.StageDescriptor
import org.ide.lti.feature.rom.studio.api.StageEditor
import org.ide.lti.feature.rom.studio.api.StageEditorContext
import org.ide.lti.feature.rom.studio.api.StudioIconKey
import org.ide.lti.feature.rom.studio.api.StudioSubobject
import org.ide.lti.feature.rom.studio.api.StudioSubobjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class DefaultStageEditorRegistryTest {

    private fun mockEditor(stageId: StageId, subobjects: List<StudioSubobject> = emptyList()): StageEditor =
        object : StageEditor {
            override val stageId: StageId = stageId
            override val descriptor: StageDescriptor = StageDescriptor(
                stageId = stageId,
                title = stageId.name,
                iconKey = StudioIconKey.ACQUIRE,
                subobjects = subobjects,
            )

            @Composable
            override fun Content(context: StageEditorContext, modifier: Modifier) {}
        }

    private fun allCanonicalEditors(): List<StageEditor> = PipelineStageSequence.ORDER.map { id ->
        mockEditor(id, listOf(StudioSubobject(StudioSubobjectId("overview"), "Overview")))
    }

    @Test
    fun allEightEditorsResolveInCanonicalOrder() {
        val registry = DefaultStageEditorRegistry(allCanonicalEditors())
        val editors = registry.allEditors()
        assertEquals(8, editors.size)
        assertEquals(PipelineStageSequence.ORDER, editors.map { it.stageId })
        assertNotNull(registry.editorFor(StageId.DEBLOAT))
    }

    @Test
    fun missingStageThrowsWhenFullPipelineEnforced() {
        val partial = allCanonicalEditors().filter { it.stageId != StageId.DEBLOAT }
        assertFailsWith<MissingStageEditorException> {
            DefaultStageEditorRegistry(partial, enforceFullPipeline = true)
        }
    }

    @Test
    fun duplicateStageThrowsDuplicateStageEditorException() {
        val duplicated = allCanonicalEditors() + mockEditor(StageId.FIRMWARE_ACQUISITION)
        assertFailsWith<DuplicateStageEditorException> {
            DefaultStageEditorRegistry(duplicated)
        }
    }

    @Test
    fun unknownSubobjectThrowsException() {
        val registry = DefaultStageEditorRegistry(allCanonicalEditors())
        assertFailsWith<UnknownSubobjectException> {
            registry.validateSubobject(StageId.DEBLOAT, StudioSubobjectId("nonexistent-subobject"))
        }
    }
}
