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

import org.ide.lti.core.model.run.StageId

public class PipelineDefinitionRegistry(
    private val definitions: Map<StageId, StageDefinition> = DefaultPipelineStages.all.associateBy { it.id },
) {
    public val order: List<StageId> get() = PipelineStageSequence.ORDER

    public fun executableStages(): List<StageDefinition> = order.filter { it != StageId.PUBLISH_RELEASE }.map { id ->
        definitions[id] ?: throw IllegalStateException("Missing definition for stage $id")
    }
}
