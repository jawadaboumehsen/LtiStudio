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
import org.ide.lti.core.domain.debloat.DebloatDecision
import org.ide.lti.core.domain.debloat.DebloatResolution
import org.ide.lti.core.domain.pipeline.CacheKeys
import org.ide.lti.core.domain.pipeline.PipelineStep
import org.ide.lti.core.domain.pipeline.StageContext
import org.ide.lti.core.domain.pipeline.StageDefinition
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

private val DEFAULT_DEBLOAT_PLANNER: (StageContext) -> DebloatResolution = { ctx ->
    if (!ctx.snapshot.debloat.enabled) {
        DebloatResolution.Resolved(
            DebloatDecision(
                removals = emptyList(),
                kept = emptyList(),
                logicalBytesFreed = 0L,
            ),
        )
    } else {
        DebloatResolution.Rejected(
            ValidationReport(
                errors = listOf(
                    ValidationError(
                        stageId = StageId.DEBLOAT,
                        objectId = null,
                        fieldPath = "debloat",
                        code = "DEBLOAT_INVENTORY_UNAVAILABLE",
                        severity = Severity.ERROR,
                        message = "debloat inventory unavailable",
                    ),
                ),
            ),
        )
    }
}

/**
 * Stage 4. Disabled debloat still materialises a digested no-removal result so downstream stages
 * consume a verified artifact rather than an absent one. Enabled debloat resolves candidate removals
 * through the injected [debloatPlanner], writes the decision to work/debloat/selection.json, and asserts
 * application success via a runtime check step.
 */
public class DebloatStage(private val debloatPlanner: (StageContext) -> DebloatResolution = DEFAULT_DEBLOAT_PLANNER) :
    StageDefinition {
    override val id: StageId = StageId.DEBLOAT
    override val requiredToolIds: Set<String> = emptySet()

    override fun computeCacheKey(ctx: StageContext, previousStageCacheKey: String): String {
        val settingsPayload = ConfigurationSnapshot.canonicalJson.encodeToString(ctx.snapshot.debloat)
        val decisionPayload = if (ctx.snapshot.debloat.enabled) {
            when (val resolution = debloatPlanner(ctx)) {
                is DebloatResolution.Resolved -> ConfigurationSnapshot.canonicalJson.encodeToString(resolution.decision)
                is DebloatResolution.Rejected -> ""
            }
        } else {
            ""
        }
        return CacheKeys.sha256(previousStageCacheKey + settingsPayload + decisionPayload)
    }

    override fun outputs(ctx: StageContext): List<String> = if (!ctx.snapshot.debloat.enabled) {
        listOf("work/debloat/result.json")
    } else {
        listOf("work/debloat/selection.json")
    }

    override fun plan(ctx: StageContext): List<PipelineStep> {
        if (!ctx.snapshot.debloat.enabled) {
            return listOf(
                PipelineStep.WriteFile(
                    relPath = "work/debloat/result.json",
                    content = """{"enabled":false,"removed":[]}""".encodeToByteArray(),
                ),
            )
        }

        return when (val resolution = debloatPlanner(ctx)) {
            is DebloatResolution.Rejected -> {
                val firstCode = resolution.report.errors.firstOrNull()?.code ?: "DEBLOAT_INVENTORY_UNAVAILABLE"
                val message = resolution.report.errors.firstOrNull()?.message.orEmpty()
                listOf(
                    PipelineStep.Check(
                        label = "Debloat Checks",
                        verify = { if (message.isNotEmpty()) "$firstCode: $message" else firstCode },
                    ),
                )
            }
            is DebloatResolution.Resolved -> {
                val decisionJson = ConfigurationSnapshot.canonicalJson.encodeToString(resolution.decision)
                listOf(
                    PipelineStep.WriteFile(
                        relPath = "work/debloat/selection.json",
                        content = decisionJson.encodeToByteArray(),
                    ),
                    PipelineStep.Check(
                        label = "debloat applied",
                        verify = { runtimeValues ->
                            val result = runtimeValues["debloat.apply.result"]
                            if (result != "ok") {
                                "Debloat application did not succeed: ${result ?: "no result recorded"}"
                            } else {
                                null
                            }
                        },
                    ),
                )
            }
        }
    }
}
