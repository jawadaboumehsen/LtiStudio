/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.stages

import org.ide.lti.core.domain.pipeline.CacheKeys
import org.ide.lti.core.domain.pipeline.PipelineStep
import org.ide.lti.core.domain.pipeline.StageContext
import org.ide.lti.core.domain.pipeline.StageDefinition
import org.ide.lti.core.model.run.StageId

/**
 * Stage 4 — pass-through in feature 001 (no modules). Later features operate on `work/` and must
 * keep the `work/configs` sidecars in lockstep with any file removal.
 */
public class ModuleApplicationStage : StageDefinition {
    override val id: StageId = StageId.MODULE_APPLICATION
    override val requiredToolIds: Set<String> = emptySet()

    override fun computeCacheKey(ctx: StageContext, previousStageCacheKey: String): String =
        CacheKeys.stage4ModuleKey(previousStageCacheKey, ctx.snapshot)

    override fun outputs(ctx: StageContext): List<String> = emptyList()

    override fun plan(ctx: StageContext): List<PipelineStep> = emptyList()
}
