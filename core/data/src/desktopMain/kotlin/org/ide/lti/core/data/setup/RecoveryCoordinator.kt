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

import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.setup.ports.InstallId

/**
 * Coordinates recovery of interrupted setup operations and journals (T031, T085, T086).
 */
public class RecoveryCoordinator(
    private val transport: RemoteTransportPort,
    private val supervisor: DaemonSupervisorPort,
    private val repository: ToolchainSetupRepository?,
    private val activity: SetupActivitySink? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val engine: SetupRunRecovery? = repository?.let {
        SetupRunRecovery(transport, it, activity = activity)
    }

    public fun checkHealth(
        selectedInstallId: InstallId?,
        isIntact: Boolean,
        serviceResolvesTools: Boolean,
        lastOperationCompleted: Boolean,
    ): ToolchainHealthReport = ToolchainHealthReport(
        selectedInstallId = selectedInstallId,
        isIntact = isIntact,
        serviceResolvesTools = serviceResolvesTools,
        lastOperationCompleted = lastOperationCompleted,
    )

    public suspend fun recover(
        distro: String,
        attemptId: String?,
        ensureConnected: suspend () -> Unit,
        verifyEnvironment: suspend () -> ToolchainSetupState,
        appendLog: (String) -> Unit = {},
    ): SetupOutcome = withContext(dispatcher) {
        if (supervisor.getConnectionInfo() == null) {
            ensureConnected()
        }
        val recoveryEngine = engine ?: return@withContext SetupOutcome.Failed(
            stage = null,
            reason = "No durable journal is configured; nothing to recover.",
        )
        val outcome = recoveryEngine.recover(distro, attemptId) { appendLog(it) }
        if (outcome is SetupOutcome.Succeeded) {
            val verified = verifyEnvironment()
            if (!verified.canLaunchWorkspace) {
                val firstFailed = verified.steps.firstOrNull { it.status == StepStatus.FAILED }
                SetupOutcome.Failed(
                    stage = firstFailed?.stage?.name,
                    reason = firstFailed?.error ?: "Environment is not ready to launch workspace after recovery.",
                )
            } else {
                outcome
            }
        } else {
            outcome
        }
    }
}
