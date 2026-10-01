/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.execute.handlers

import org.ide.lti.core.data.setup.AptCommandBuilder
import org.ide.lti.core.data.setup.RequirementCatalog
import org.ide.lti.core.data.setup.execute.ExecutionContext
import org.ide.lti.core.data.setup.execute.PlanActionHandler
import org.ide.lti.core.data.setup.execute.actionId
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.UserRepairHandoff
import org.ide.lti.core.model.setup.PersistedPlanAction
import org.ide.lti.core.model.setup.PersistedSetupPlan
import org.ide.lti.core.model.setup.PersistedUserHandoff

public class InstallPackagesHandler(
    private val remediator: (suspend (SetupPlanAction.InstallPackages, ExecutionContext) -> SetupOutcome?)? = null,
) : PlanActionHandler<SetupPlanAction.InstallPackages> {
    override suspend fun execute(action: SetupPlanAction.InstallPackages, context: ExecutionContext): SetupOutcome? =
        if (action.packages.isNotEmpty() || action.configureLoopMountElevation) {
            handleElevatedInstall(action, context)
        } else {
            remediator?.invoke(action, context)
        }

    private suspend fun handleElevatedInstall(
        action: SetupPlanAction.InstallPackages,
        context: ExecutionContext,
    ): SetupOutcome {
        val distro = context.distro
        val currentPlan = context.plan
        val journal = context.journal
        val repository = context.repository

        val handoffPackages = action.packages.filter { RequirementCatalog.isPackageApproved(it) }
        val pkgString = handoffPackages.joinToString(" ")
        val terminalCommand = if (handoffPackages.isNotEmpty()) {
            AptCommandBuilder.install(handoffPackages)
        } else {
            "echo 'No elevated package installation required.'"
        }
        val handoff = UserRepairHandoff(
            actionId = action.actionId(),
            description = action.description,
            terminalCommand = terminalCommand,
            packages = handoffPackages,
            distro = distro,
        )
        val persistedHandoff = PersistedUserHandoff(
            actionId = action.actionId(),
            distro = distro,
            command = terminalCommand,
            packages = handoffPackages,
            timestampEpochMs = System.currentTimeMillis(),
        )
        val persistedPlan = PersistedSetupPlan(
            planId = currentPlan.planId,
            revisionHash = currentPlan.revisionHash,
            environmentKey = currentPlan.environmentKey,
            kind = currentPlan.kind.name,
            targetStageOrToolId = currentPlan.targetStageOrToolId,
            autoDoctorEnabled = currentPlan.autoDoctorEnabled,
            actions = currentPlan.orderedActions.map { act ->
                when (act) {
                    is SetupPlanAction.InstallPackages -> PersistedPlanAction(
                        act.actionId(),
                        "INSTALL_PACKAGES",
                        act.packages,
                    )
                    is SetupPlanAction.SyncSources -> PersistedPlanAction(
                        act.actionId(),
                        "SYNC_SOURCES",
                        act.submodules,
                    )
                    is SetupPlanAction.BuildRecipes -> PersistedPlanAction(
                        act.actionId(),
                        "BUILD_RECIPES",
                        act.recipeGroups,
                    )
                    is SetupPlanAction.PublishTools -> PersistedPlanAction(
                        act.actionId(),
                        "PUBLISH_TOOLS",
                        act.toolIds,
                    )
                    is SetupPlanAction.SwitchToolchain -> PersistedPlanAction(
                        act.actionId(),
                        "SWITCH_TOOLCHAIN",
                        act.inputs.keys.map { it.value },
                    )
                }
            },
        )

        val targetStage = if (currentPlan.kind == SetupPlanKind.BOOTSTRAP_PACKAGES) {
            SetupStepStage.SYSTEM_PACKAGES
        } else {
            SetupStepStage.SYSTEM_DIAGNOSTICS
        }

        if (repository == null || journal == null) {
            context.appendLog("[Plan] ERROR: Cannot pause for user authorization without a configured repository.")
            context.setStepStatus(
                targetStage,
                StepStatus.FAILED,
                "No repository configured to persist handoff",
                "No repository configured to persist handoff",
                null,
                null,
            )
            return SetupOutcome.Failed(
                stage = targetStage.name,
                reason = "User authorization handoff requires a configured repository to survive and resume.",
            )
        }

        val recordResult = repository.recordAttemptAwaitingUserAction(
            journal.attemptId,
            persistedHandoff,
            persistedPlan,
        )
        return if (recordResult.isFailure) {
            val err = recordResult.exceptionOrNull()?.message ?: "Unknown journal error"
            context.appendLog("[Plan] Journal write failed while recording pending handoff: $err")
            SetupOutcome.Interrupted("Journal write failed while recording pending handoff: $err")
        } else {
            context.setStepStatus(
                targetStage,
                StepStatus.WARNING,
                "Awaiting external authorization in WSL terminal: $terminalCommand",
                null,
                null,
                null,
            )
            context.appendLog("[Plan] Paused for user terminal authorization: $terminalCommand")
            SetupOutcome.AwaitingUserAction(
                pendingPlanId = currentPlan.planId,
                stage = targetStage,
                reason = "Privileged packages require installation in WSL terminal: $pkgString",
                handoff = handoff,
            )
        }
    }
}
