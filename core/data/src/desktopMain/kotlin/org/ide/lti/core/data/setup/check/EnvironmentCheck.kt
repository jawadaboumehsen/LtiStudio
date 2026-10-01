/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.check

import org.ide.lti.core.data.setup.check.stages.DoctorStage
import org.ide.lti.core.data.setup.check.stages.SourcesStage
import org.ide.lti.core.data.setup.check.stages.ToolchainStage
import org.ide.lti.core.data.setup.state.CheckScope
import org.ide.lti.core.domain.setup.SetupLogKind
import org.ide.lti.core.domain.setup.SetupStepStage

/**
 * Orchestrates environment inspection exclusively through allowlisted [ReadOnlyCommands]
 * and discrete [CheckStage]s (FR-003).
 */
public class EnvironmentCheck(
    private val stages: List<CheckStage> = listOf(
        DoctorStage(),
        SourcesStage(),
        ToolchainStage(),
    ),
) {
    public suspend fun run(ctx: CheckContext, scope: CheckScope): Map<SetupStepStage, StageOutcome> {
        scope.setChecking(true)
        val outcomes = mutableMapOf<SetupStepStage, StageOutcome>()

        try {
            for (stage in stages) {
                ctx.log("[check] ${stage.stage.displayName}: started", SetupLogKind.CHECK)
                val startMs = System.currentTimeMillis()
                val outcome = stage.run(ctx, scope)
                val durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                outcomes[stage.stage] = outcome
                val name = stage.stage.displayName
                val (msg, kind) = when (outcome) {
                    is StageOutcome.Failed -> {
                        val err = outcome.error ?: outcome.description
                        "[check] $name ERROR: $err" to SetupLogKind.ERROR
                    }
                    else -> {
                        "[check] $name: ${outcome.description} ($durationMs ms)" to SetupLogKind.CHECK
                    }
                }
                ctx.log(msg, kind)
            }
            val allSuccess = outcomes.values.all { it is StageOutcome.Success }
            val now = System.currentTimeMillis()
            scope.setCheckTimestamps(checkedAt = now, lastReadyAt = if (allSuccess) now else null)
        } finally {
            scope.setChecking(false)
        }

        return outcomes
    }
}
