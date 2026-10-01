/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.execute

import org.ide.lti.core.data.setup.execute.handlers.BuildRecipesHandler
import org.ide.lti.core.data.setup.execute.handlers.InstallPackagesHandler
import org.ide.lti.core.data.setup.execute.handlers.PublishToolsHandler
import org.ide.lti.core.data.setup.execute.handlers.SyncSourcesHandler
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlanAction

/**
 * Executes a [SetupPlan] by dispatching actions to typed [PlanActionHandler]s (T031).
 */
public class PlanExecutor(
    private val installPackagesHandler: PlanActionHandler<SetupPlanAction.InstallPackages> = InstallPackagesHandler(),
    private val syncSourcesHandler: PlanActionHandler<SetupPlanAction.SyncSources> = SyncSourcesHandler(),
    private val buildRecipesHandler: PlanActionHandler<SetupPlanAction.BuildRecipes> = BuildRecipesHandler(),
    private val publishToolsHandler: PlanActionHandler<SetupPlanAction.PublishTools> = PublishToolsHandler(),
    public val switchToolchainHandler: PlanActionHandler<SetupPlanAction.SwitchToolchain>? = null,
) {
    public suspend fun execute(context: ExecutionContext): SetupOutcome {
        val plan = context.plan

        for (action in plan.orderedActions) {
            if (action.actionId() in context.actionsToSkip) {
                context.appendLog("[Plan] Skipping completed action: ${action.actionId()}")
                continue
            }
            val outcome = executeAction(action, context)
            if (outcome != null) {
                return outcome
            }
        }

        return SetupOutcome.Succeeded()
    }

    private suspend fun executeAction(action: SetupPlanAction, context: ExecutionContext): SetupOutcome? {
        val outcome: SetupOutcome? = when (action) {
            is SetupPlanAction.InstallPackages -> installPackagesHandler.execute(action, context)
            is SetupPlanAction.SyncSources -> syncSourcesHandler.execute(action, context)
            is SetupPlanAction.BuildRecipes -> buildRecipesHandler.execute(action, context)
            is SetupPlanAction.PublishTools -> publishToolsHandler.execute(action, context)
            is SetupPlanAction.SwitchToolchain -> {
                val handler = switchToolchainHandler
                    ?: return SetupOutcome.Failed(null, "No SwitchToolchainHandler registered")
                handler.execute(action, context)
            }
        }
        return outcome ?: recordActionCompletion(action, context)
    }

    private suspend fun recordActionCompletion(action: SetupPlanAction, context: ExecutionContext): SetupOutcome? {
        val journal = context.journal ?: return null
        val result = context.repository?.recordPlanActionCompleted(journal.attemptId, action.actionId())
        return if (result != null && result.isFailure) {
            val reason = "Plan action '${action.actionId()}' completed but could not be journaled: " +
                result.exceptionOrNull()?.message
            context.appendLog("[Journal] ERROR: $reason")
            SetupOutcome.Interrupted(reason)
        } else {
            null
        }
    }
}
