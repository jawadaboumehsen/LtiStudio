/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup

import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState

/**
 * Pure decision gate for the automatic initial tools run (FR-019, T034).
 *
 * Rules:
 * - Starts only when stages 1-3 (WSL_DETECTION, SERVER_CONNECTIVITY, SYSTEM_PACKAGES) are SUCCESS.
 * - Tools stage (TOOLCHAIN_COMPILATION) must NOT already be SUCCESS.
 * - No recovery is pending (pendingAttemptId, pendingAttempt, recoveryBlockReason all null).
 * - Nothing is running (not isBusy / isRunning / isChecking / activeOperationId).
 * - Never starts twice in one launch (attemptedThisLaunch == true blocks it).
 * - Never starts while disconnected (!isServerConnected blocks it).
 */
public object AutoToolsGate {
    public fun shouldStart(state: ToolchainSetupState, attemptedThisLaunch: Boolean): Boolean =
        blockReason(state, attemptedThisLaunch) == null

    /** Why the automatic run does not start, or null when it should; written to the activity log. */
    public fun blockReason(state: ToolchainSetupState, attemptedThisLaunch: Boolean): String? {
        fun status(stage: SetupStepStage) = state.steps.firstOrNull { it.stage == stage }?.status
        val notReady =
            listOf(SetupStepStage.WSL_DETECTION, SetupStepStage.SERVER_CONNECTIVITY, SetupStepStage.SYSTEM_PACKAGES)
                .firstOrNull { status(it) != StepStatus.SUCCESS }
        return when {
            attemptedThisLaunch -> "already ran once this launch"
            state.isBusy -> "another operation is running"
            state.pendingAttemptId != null || state.pendingAttempt != null ->
                "setup attempt ${state.pendingAttemptId} is still open"
            state.recoveryBlockReason != null -> "recovery needed: ${state.recoveryBlockReason}"
            notReady != null -> "${notReady.displayName} is ${status(notReady)}"
            status(SetupStepStage.TOOLCHAIN_COMPILATION) == StepStatus.SUCCESS -> "tools are ready"
            else -> null
        }
    }
}
