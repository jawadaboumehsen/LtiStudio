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

import org.ide.lti.core.domain.pipeline.PipelineStageSequence
import org.ide.lti.core.model.run.StageId
import org.ide.lti.feature.rom.studio.api.StageEditor
import org.ide.lti.feature.rom.studio.api.StageEditorRegistry
import org.ide.lti.feature.rom.studio.api.StudioSubobjectId

public class MissingStageEditorException(public val missingStageIds: Set<StageId>) :
    IllegalStateException(
        "Missing stage editor registration for canonical stage(s): ${missingStageIds.joinToString()}",
    )

public class DuplicateStageEditorException(public val duplicateStageIds: Set<StageId>) :
    IllegalStateException(
        "Duplicate stage editor registration detected for stage(s): ${duplicateStageIds.joinToString()}",
    )

public class UnknownSubobjectException(public val stageId: StageId, public val subobjectId: StudioSubobjectId) :
    IllegalArgumentException(
        "Subobject '$subobjectId' is not defined in descriptor for stage $stageId",
    )

/**
 * Concrete implementation of [StageEditorRegistry] with fail-fast validation against [PipelineStageSequence.ORDER].
 */
public class DefaultStageEditorRegistry(editors: List<StageEditor>, enforceFullPipeline: Boolean = true) :
    StageEditorRegistry {

    private val editorMap: Map<StageId, StageEditor>

    init {
        val duplicates = editors.groupBy { it.stageId }.filterValues { it.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            throw DuplicateStageEditorException(duplicates)
        }

        val map = editors.associateBy { it.stageId }

        if (enforceFullPipeline) {
            val missing = PipelineStageSequence.ORDER.filter { !map.containsKey(it) }.toSet()
            if (missing.isNotEmpty()) {
                throw MissingStageEditorException(missing)
            }
        }

        editorMap = map
    }

    override fun editorFor(stageId: StageId): StageEditor? = editorMap[stageId]

    override fun allEditors(): List<StageEditor> {
        // Returned in canonical pipeline sequence order
        return PipelineStageSequence.ORDER.mapNotNull { editorMap[it] }
    }

    public fun validateSubobject(stageId: StageId, subobjectId: StudioSubobjectId) {
        val editor = editorFor(stageId) ?: return
        if (editor.descriptor.subobject(subobjectId) == null) {
            throw UnknownSubobjectException(stageId, subobjectId)
        }
    }
}
