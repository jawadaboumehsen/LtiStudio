/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.versions

import org.ide.lti.core.domain.setup.ports.ActivationOutcome

/**
 * Console stages traversed during a toolchain build and switch flow.
 */
public enum class SwitchStage {
    RESOLVING,
    DOWNLOADING,
    BUILDING,
    VERIFYING,
    SWITCHING,
}

/**
 * UI representation of an in-flight, blocked, reconciling, or terminal switch outcome.
 */
public data class SwitchProgressUiState(
    val message: String,
    val canRetry: Boolean = false,
    val isTerminalSuccess: Boolean = false,
    val isReconciling: Boolean = false,
    val requiresRecovery: Boolean = false,
)

/**
 * Maps switch progress, console stages, and activation outcomes to UI presentation state (008 T084, T087).
 */
public object SwitchProgressMapper {

    public fun mapStage(stage: SwitchStage): String = when (stage) {
        SwitchStage.RESOLVING -> "Resolving sources…"
        SwitchStage.DOWNLOADING -> "Downloading packages…"
        SwitchStage.BUILDING -> "Building tools…"
        SwitchStage.VERIFYING -> "Verifying toolchain…"
        SwitchStage.SWITCHING -> "Switching active toolchain…"
    }

    public fun mapOutcome(outcome: ActivationOutcome): SwitchProgressUiState = when (outcome) {
        is ActivationOutcome.Committed -> SwitchProgressUiState(
            message = "Toolchain switched successfully to ${outcome.activeInstallId.value}",
            isTerminalSuccess = true,
        )
        is ActivationOutcome.Blocked -> SwitchProgressUiState(
            message = "Activation blocked by ${outcome.activeWork} running jobs",
            canRetry = true,
        )
        is ActivationOutcome.Unknown, ActivationOutcome.InProgress -> SwitchProgressUiState(
            message = "Switch outcome unknown — reconciling…",
            isReconciling = true,
        )
        is ActivationOutcome.VerifyFailedRestored -> SwitchProgressUiState(
            message = "Switched toolchain failed verification; restored previous toolchain: ${outcome.reason}",
            canRetry = false,
        )
        is ActivationOutcome.MaintenanceFailed -> SwitchProgressUiState(
            message = "Toolchain restore failed; environment requires manual recovery",
            requiresRecovery = true,
        )
        is ActivationOutcome.Conflict -> SwitchProgressUiState(
            message = "Activation conflict with active toolchain installation",
            canRetry = false,
        )
        is ActivationOutcome.RequestMismatch -> SwitchProgressUiState(
            message = "Activation request mismatch",
            canRetry = false,
        )
        is ActivationOutcome.InvalidTarget -> SwitchProgressUiState(
            message = "Invalid activation target: ${outcome.reason}",
            canRetry = false,
        )
    }
}
