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

import kotlinx.serialization.encodeToString
import org.ide.lti.core.domain.pipeline.CacheKeys
import org.ide.lti.core.domain.pipeline.PipelineStep
import org.ide.lti.core.domain.pipeline.StageContext
import org.ide.lti.core.domain.pipeline.StageDefinition
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

/**
 * Stage 4. Disabled debloat still materialises a digested no-removal result so downstream stages
 * consume a verified artifact rather than an absent one; enabled debloat fails explicitly until
 * the resolver and inventory adapter land.
 */
public class DebloatStage : StageDefinition {
    override val id: StageId = StageId.DEBLOAT
    override val requiredToolIds: Set<String> = emptySet()

    override fun computeCacheKey(ctx: StageContext, previousStageCacheKey: String): String {
        val payload = ConfigurationSnapshot.canonicalJson.encodeToString(ctx.snapshot.debloat)
        return CacheKeys.sha256(previousStageCacheKey + payload)
    }

    override fun outputs(ctx: StageContext): List<String> = listOf("work/debloat/result.json")

    override fun plan(ctx: StageContext): List<PipelineStep> = if (!ctx.snapshot.debloat.enabled) {
        listOf(
            PipelineStep.WriteFile(
                relPath = "work/debloat/result.json",
                content = """{"enabled":false,"removed":[]}""".encodeToByteArray(),
            ),
        )
    } else {
        listOf(
            PipelineStep.Check(
                label = "Debloat Checks",
                verify = { "Debloat removals are not implemented yet" },
            ),
        )
    }
}
