/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState

/**
 * Whether a plan that ran to its end really reached its goal, from the state the post-check left: a full setup can
 * launch a workspace, a retried stage or repaired tool is ready, the packages a bootstrap installed are no longer in
 * [missingPackages]. A failed or cancelled [originalOutcome] stays as it is.
 */
@Suppress("CyclomaticComplexMethod")
internal fun completionOutcome(
    plan: SetupPlan,
    state: ToolchainSetupState,
    originalOutcome: SetupOutcome,
    missingPackages: List<String>,
): SetupOutcome {
    if (originalOutcome !is SetupOutcome.Succeeded) return originalOutcome
    return when (plan.kind) {
        SetupPlanKind.CACHE_RESET -> originalOutcome

        SetupPlanKind.FULL_SETUP -> {
            if (state.canLaunchWorkspace) {
                originalOutcome
            } else {
                val firstFailed = state.steps.firstOrNull { it.status == StepStatus.FAILED }
                SetupOutcome.Failed(
                    stage = firstFailed?.stage?.name,
                    reason = firstFailed?.error ?: "Environment is not ready to launch workspace after full setup.",
                )
            }
        }

        SetupPlanKind.STAGE_RETRY -> {
            val target = plan.targetStageOrToolId
            val step = state.steps.firstOrNull { it.stage.name == target }
            if (step != null) {
                if (step.status == StepStatus.SUCCESS || step.status == StepStatus.WARNING) {
                    originalOutcome
                } else {
                    SetupOutcome.Failed(
                        stage = target,
                        reason = step.error ?: "Target stage '$target' is not ready after retry.",
                    )
                }
            } else {
                val tool = state.toolsMatrix.firstOrNull { it.id == target }
                if (tool != null && (tool.status == StepStatus.SUCCESS || tool.status == StepStatus.WARNING)) {
                    originalOutcome
                } else {
                    SetupOutcome.Failed(
                        stage = target,
                        reason = "Target '$target' is not ready after retry.",
                    )
                }
            }
        }

        SetupPlanKind.REPAIR_TOOL -> {
            val toolId = plan.targetStageOrToolId
            val tool = state.toolsMatrix.firstOrNull { it.id == toolId }
            if (tool != null && (tool.status == StepStatus.SUCCESS || tool.status == StepStatus.WARNING)) {
                originalOutcome
            } else {
                SetupOutcome.Failed(
                    stage = SetupStepStage.TOOLCHAIN_COMPILATION.name,
                    reason = "Target tool '$toolId' is not ready after repair.",
                )
            }
        }

        SetupPlanKind.BOOTSTRAP_PACKAGES -> {
            val action = plan.orderedActions.filterIsInstance<SetupPlanAction.InstallPackages>().firstOrNull()
            val expectedPackages = action?.packages.orEmpty()
            val stillMissing = expectedPackages.filter { it in missingPackages }
            if (stillMissing.isEmpty()) {
                originalOutcome
            } else {
                SetupOutcome.Failed(
                    stage = SetupStepStage.SYSTEM_PACKAGES.name,
                    reason = "Required packages are still missing: ${stillMissing.joinToString()}",
                )
            }
        }
    }
}
