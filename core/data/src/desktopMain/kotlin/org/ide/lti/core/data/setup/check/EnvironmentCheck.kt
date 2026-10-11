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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield
import org.ide.lti.core.data.setup.state.CheckScope
import org.ide.lti.core.domain.setup.SetupLogKind
import org.ide.lti.core.domain.setup.SetupStepStage

/**
 * Orchestrates environment inspection exclusively through allowlisted [ReadOnlyCommands]
 * and discrete [CheckStage]s (FR-003).
 */
public class EnvironmentCheck(private val stages: List<CheckStage> = emptyList()) {
    public suspend fun run(ctx: CheckContext, scope: CheckScope): Map<SetupStepStage, StageOutcome> {
        scope.setChecking(true)
        val outcomes = mutableMapOf<SetupStepStage, StageOutcome>()

        try {
            var failedStage: CheckStage? = null
            for (i in stages.indices) {
                val stage = stages[i]
                if (failedStage != null) {
                    val pendingDesc = "Waiting for ${failedStage.stage.displayName}"
                    val pendingOutcome = StageOutcome.Pending(pendingDesc)
                    outcomes[stage.stage] = pendingOutcome
                    scope.setStepOutcome(stage.stage, pendingOutcome)
                    continue
                }

                scope.setStepRunning(stage.stage)
                yield()
                ctx.log("[check] ${stage.stage.displayName}: started", SetupLogKind.CHECK)
                val startMs = System.currentTimeMillis()
                val outcome =
                    try {
                        stage.run(ctx, scope)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        StageOutcome.Failed("Check failed: ${e.message ?: e::class.simpleName}")
                    }
                val durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                outcomes[stage.stage] = outcome
                scope.setStepOutcome(stage.stage, outcome, durationMs)

                val name = stage.stage.displayName
                val resultText =
                    when (outcome) {
                        is StageOutcome.Success -> outcome.description
                        is StageOutcome.Failed -> outcome.description
                        is StageOutcome.Warning -> outcome.description
                        is StageOutcome.Pending -> outcome.description
                    }
                ctx.log(
                    "[check] $name: $resultText ($durationMs ms)",
                    if (outcome is StageOutcome.Failed) SetupLogKind.ERROR else SetupLogKind.CHECK,
                )
                if (outcome is StageOutcome.Failed || outcome is StageOutcome.Warning) {
                    failedStage = stage
                }
            }
            val now = System.currentTimeMillis()
            scope.setCheckTimestamps(checkedAt = now, lastReadyAt = null)
        } finally {
            scope.setCurrentStage(null)
            scope.setChecking(false)
        }

        return outcomes
    }
}
