/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.api

import org.ide.lti.core.model.run.StageId

/**
 * A selectable subobject/section within a stage editor.
 */
public data class StudioSubobject(
    val id: StudioSubobjectId,
    val title: String,
    val accessibleLabel: String = title,
) {
    init {
        require(title.isNotBlank()) { "StudioSubobject title must not be blank" }
        require(accessibleLabel.isNotBlank()) { "StudioSubobject accessibleLabel must not be blank" }
    }
}

/**
 * Metadata descriptor for a pipeline stage editor.
 * Note: Does not declare a positional index; execution and rail order is derived from canonical PipelineStageSequence.
 */
public data class StageDescriptor(
    val stageId: StageId,
    val title: String,
    val accessibleLabel: String = title,
    val iconKey: StudioIconKey,
    val subobjects: List<StudioSubobject>,
) {
    init {
        require(title.isNotBlank()) { "StageDescriptor title must not be blank for stage $stageId" }
        require(accessibleLabel.isNotBlank()) { "StageDescriptor accessibleLabel must not be blank for stage $stageId" }
        val duplicateIds = subobjects.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicateIds.isEmpty()) {
            "Stage $stageId contains duplicate subobject IDs: ${duplicateIds.joinToString()}"
        }
    }

    public fun subobject(id: StudioSubobjectId): StudioSubobject? = subobjects.firstOrNull { it.id == id }
}
