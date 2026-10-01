/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.ToolComponentItem
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.DistroStatus
import org.ide.lti.core.model.setup.SetupAttemptRecord

/**
 * Scope for mutating active execution/operation state on [ToolchainSetupState].
 * Dropped automatically when superseded by a newer operation id.
 */
public class OperationScope(
    public val environmentKey: String,
    public val operationId: String,
    private val stateFlow: MutableStateFlow<ToolchainSetupState>,
    private val activeOpId: () -> String?,
) {
    private inline fun updateIfActive(crossinline transform: (ToolchainSetupState) -> ToolchainSetupState) {
        if (activeOpId() != operationId) return
        stateFlow.update { current ->
            if (activeOpId() != operationId) current else transform(current)
        }
    }

    public fun start(stage: SetupStepStage? = null) {
        updateIfActive {
            it.copy(
                isRunning = true,
                currentStage = stage,
                activeOperationId = operationId,
            )
        }
    }

    public fun setCurrentStage(stage: SetupStepStage?) {
        updateIfActive { it.copy(currentStage = stage) }
    }

    public fun finish() {
        updateIfActive {
            it.copy(
                isRunning = false,
                currentStage = null,
                activeOperationId = if (it.activeOperationId == operationId) null else it.activeOperationId,
            )
        }
    }

    public fun appendLog(line: String) {
        updateIfActive {
            it.copy(
                logs = it.logs + line,
                activeLogLine = line,
            )
        }
    }

    public fun setStepRunId(stage: SetupStepStage, runId: String) {
        updateIfActive {
            it.copy(stepRunIds = it.stepRunIds + (stage to runId))
        }
    }
}

/**
 * Scope for mutating environment inspection and diagnostic check results.
 * Dropped automatically when superseded by a newer check id.
 */
public class CheckScope(
    public val environmentKey: String,
    public val checkId: String,
    private val stateFlow: MutableStateFlow<ToolchainSetupState>,
    private val activeCheckId: () -> String?,
) {
    private inline fun updateIfActive(crossinline transform: (ToolchainSetupState) -> ToolchainSetupState) {
        if (activeCheckId() != checkId) return
        stateFlow.update { current ->
            if (activeCheckId() != checkId) current else transform(current)
        }
    }

    public fun setChecking(checking: Boolean) {
        updateIfActive { it.copy(isChecking = checking) }
    }

    public fun setDistroInfo(
        activeDistro: String?,
        installedDistros: List<String>,
        distroStatuses: List<DistroStatus>,
    ) {
        updateIfActive {
            it.copy(
                activeDistro = activeDistro,
                installedDistros = installedDistros,
                distroStatuses = distroStatuses,
            )
        }
    }

    public fun setDaemonPing(pingMs: Long?) {
        updateIfActive { it.copy(daemonPingMs = pingMs) }
    }

    public fun setDiagnostics(diagnostics: List<DiagnosticCheckItem>) {
        updateIfActive { it.copy(diagnostics = diagnostics) }
    }

    public fun setSteps(steps: List<SetupStepDetail>) {
        updateIfActive { it.copy(steps = steps) }
    }

    public fun setStorage(availableBytes: Long?, measuredAt: Long?) {
        updateIfActive {
            it.copy(
                storageAvailableBytes = availableBytes,
                storageMeasuredAt = measuredAt,
            )
        }
    }

    public fun setPaths(userHome: String?, workDirLinuxPath: String?) {
        updateIfActive {
            it.copy(
                userHome = userHome,
                workDirLinuxPath = workDirLinuxPath,
            )
        }
    }

    public fun setCheckTimestamps(checkedAt: Long?, lastReadyAt: Long?) {
        updateIfActive {
            it.copy(
                checkedAt = checkedAt,
                lastReadyAt = lastReadyAt,
            )
        }
    }
}

/**
 * Scope for mutating installed and verified toolchain inventory.
 */
public class InstalledScope(
    public val environmentKey: String,
    private val stateFlow: MutableStateFlow<ToolchainSetupState>,
) {
    public fun setToolsMatrix(matrix: List<ToolComponentItem>) {
        stateFlow.update { it.copy(toolsMatrix = matrix) }
    }

    public fun updateTool(toolId: String, transform: (ToolComponentItem) -> ToolComponentItem) {
        stateFlow.update { state ->
            state.copy(
                toolsMatrix = state.toolsMatrix.map {
                    if (it.id == toolId) transform(it) else it
                },
            )
        }
    }

    public fun setPublishedToolIds(published: Set<String>, unpublished: Set<String> = emptySet()) {
        stateFlow.update {
            it.copy(
                publishedToolIds = published,
                unpublishedToolIds = unpublished,
            )
        }
    }

    public fun setVerifiedTimestamp(timestamp: Long?) {
        stateFlow.update { it.copy(lastVerifiedTimestamp = timestamp) }
    }

    public fun setAvbKey(provisioned: Boolean, path: String? = null) {
        stateFlow.update {
            it.copy(
                isAvbKeyProvisioned = provisioned,
                avbKeyPath = path,
            )
        }
    }
}

/**
 * Scope for mutating journal recovery and pending attempt states.
 */
public class RecoveryScope(
    public val environmentKey: String,
    private val stateFlow: MutableStateFlow<ToolchainSetupState>,
) {
    public fun setPendingAttempt(attemptId: String?, attempt: SetupAttemptRecord?, reason: String? = null) {
        stateFlow.update {
            it.copy(
                pendingAttemptId = attemptId,
                pendingAttempt = attempt,
                recoveryBlockReason = reason,
            )
        }
    }

    public fun clearPendingAttempt() {
        stateFlow.update {
            it.copy(
                pendingAttemptId = null,
                pendingAttempt = null,
                recoveryBlockReason = null,
            )
        }
    }
}
