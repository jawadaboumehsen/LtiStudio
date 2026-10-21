/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.run

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.pipeline.configuration.DeviceConfigReadiness
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.repository.target.WorkspaceDeviceRepository
import org.ide.lti.core.domain.usecase.build.CancelRunUseCase
import org.ide.lti.core.domain.usecase.build.ObserveRunUseCase
import org.ide.lti.core.domain.usecase.build.ReattachRunUseCase
import org.ide.lti.core.domain.usecase.build.StartPipelineRunUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.setup.EnvironmentReadiness
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.Workspace

public data class BuildRunUiState(
    val currentWorkspace: Workspace? = null,
    val latestRun: BuildRun? = null,
    val isStartingRun: Boolean = false,
    val isCancelling: Boolean = false,
    val errorMessage: String? = null,
    val logs: List<String> = emptyList(),
    val canStartRun: Boolean = true,
    val failingCheck: String? = null,
    val remediation: String? = null,
    val environmentReadiness: EnvironmentReadiness? = null,
    /** Blocking problems with the device configuration of the workspace; a build cannot start until empty. */
    val deviceIssues: List<ValidationError> = emptyList(),
    /** The next run stops after this stage; null builds everything. */
    val throughStage: StageId? = null,
) {
    val isRunning: Boolean
        get() = latestRun?.state?.isActive == true || isStartingRun

    val isRunDisabled: Boolean
        get() = isRunning || currentWorkspace == null || !canStartRun || deviceIssues.isNotEmpty()

    val canCancelRun: Boolean
        get() = latestRun?.state?.isActive == true && !isCancelling && latestRun.state != RunState.CANCELLING

    val isInterrupted: Boolean
        get() = latestRun?.state == RunState.INTERRUPTED

    val isPaused: Boolean
        get() = latestRun?.state == RunState.PAUSED_WAITING_FOR_APP
}

public class BuildRunViewModel(
    private val workspaceManager: WorkspaceManager,
    private val startPipelineRunUseCase: StartPipelineRunUseCase,
    private val observeRunUseCase: ObserveRunUseCase,
    private val cancelRunUseCase: CancelRunUseCase? = null,
    private val reattachRunUseCase: ReattachRunUseCase? = null,
    private val runRepository: RunRepository? = null,
    private val readinessPort: EnvironmentReadinessPort? = null,
    private val workspaceDeviceRepository: WorkspaceDeviceRepository? = null,
    private val snapshotRepository: SnapshotRepository? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuildRunUiState())
    public val uiState: StateFlow<BuildRunUiState> = _uiState.asStateFlow()
    private val autoReattachedRuns = mutableSetOf<String>()

    init {
        observeDeviceIssues()
        if (readinessPort != null) {
            viewModelScope.launch {
                readinessPort.observe().collectLatest { readiness ->
                    val ws = _uiState.value.currentWorkspace
                    val effective = if (ws != null) {
                        runCatching { readinessPort.forWorkspace(ws) }.getOrDefault(readiness)
                    } else {
                        readiness
                    }
                    val isReady = effective.state == EnvironmentReadinessState.READY
                    _uiState.update { state ->
                        state.copy(
                            environmentReadiness = effective,
                            failingCheck = effective.failingCheck,
                            remediation = effective.remediation,
                            canStartRun = isReady && state.latestRun?.state?.isActive != true,
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            workspaceManager.currentWorkspace.collectLatest { ws ->
                val wsReadiness = if (ws != null && readinessPort != null) {
                    runCatching { readinessPort.forWorkspace(ws) }.getOrNull()
                } else {
                    null
                }

                val isReady = wsReadiness?.state?.let { it == EnvironmentReadinessState.READY }
                    ?: (
                        _uiState.value.environmentReadiness?.state?.let { it == EnvironmentReadinessState.READY }
                            ?: true
                        )

                _uiState.update {
                    it.copy(
                        currentWorkspace = ws,
                        errorMessage = null,
                        environmentReadiness = wsReadiness ?: it.environmentReadiness,
                        failingCheck = wsReadiness?.failingCheck ?: it.failingCheck,
                        remediation = wsReadiness?.remediation ?: it.remediation,
                        canStartRun = isReady && it.latestRun?.state?.isActive != true,
                    )
                }

                if (ws != null) {
                    observeRunUseCase.observeRuns(ws.id).collectLatest { runs ->
                        val latest = runs.maxByOrNull { it.startedAt }
                        val cachedLogs = if (latest != null && runRepository != null) {
                            runCatching { runRepository.cachedEvents(latest.id) }.getOrDefault(emptyList())
                        } else {
                            emptyList()
                        }

                        _uiState.update { state ->
                            val currentIsReady =
                                state.environmentReadiness?.state?.let { it == EnvironmentReadinessState.READY } ?: true
                            state.copy(
                                latestRun = latest,
                                isCancelling = latest?.state == RunState.CANCELLING,
                                // A new run starts with its own (empty) log, never the previous run's.
                                logs = if (cachedLogs.isNotEmpty() || latest?.id != state.latestRun?.id) {
                                    cachedLogs
                                } else {
                                    state.logs
                                },
                                canStartRun = currentIsReady && latest?.state?.isActive != true,
                            )
                        }

                        if (latest != null && latest.state.isActive && reattachRunUseCase != null) {
                            if (!autoReattachedRuns.contains(latest.id)) {
                                autoReattachedRuns.add(latest.id)
                                reattachRunUseCase.invoke(ws.id, latest.id)
                            }
                        }
                    }
                }
            }
        }
    }

    /** The device configuration is part of the gate: its problems keep Start disabled and say why. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeDeviceIssues() {
        val devices = workspaceDeviceRepository ?: return
        viewModelScope.launch {
            workspaceManager.currentWorkspace
                .flatMapLatest { ws ->
                    if (ws == null) {
                        flowOf(emptyList())
                    } else {
                        // The opened workspace is a snapshot of the moment it was opened; a saved configuration
                        // only shows up in the repository, so its snapshot id is followed from there.
                        val latest = workspaceManager.getWorkspaces()
                            .map { all -> all.firstOrNull { it.id == ws.id } ?: ws }
                            .distinctUntilChanged()
                        combine(devices.observe(ws.id), latest) { device, current -> deviceIssues(current, device) }
                            .catch { error -> emit(listOf(unreadableDevice(error))) }
                    }
                }
                .collect { issues -> _uiState.update { it.copy(deviceIssues = issues) } }
        }
    }

    private suspend fun deviceIssues(ws: Workspace, device: TargetDevice?): List<ValidationError> {
        val snapshot = ws.effectiveSnapshotId?.let { snapshotRepository?.getSnapshot(it) }
        return when {
            device == null -> listOf(MISSING_DEVICE)
            snapshot == null -> emptyList()
            else -> DeviceConfigReadiness.check(device, snapshot)
        }
    }

    /** Chooses where the next run stops: after [stage], or (null) after the last stage. */
    public fun selectThroughStage(stage: StageId?) {
        _uiState.update { it.copy(throughStage = stage) }
    }

    public fun startBuild() {
        val ws = _uiState.value.currentWorkspace ?: return
        if (_uiState.value.isRunDisabled) return

        viewModelScope.launch {
            _uiState.update { it.copy(isStartingRun = true, errorMessage = null) }
            val result = startPipelineRunUseCase(ws.id, throughStage = _uiState.value.throughStage)
            result.fold(
                onSuccess = { run ->
                    _uiState.update { it.copy(latestRun = run, isStartingRun = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isStartingRun = false,
                            errorMessage = error.message ?: "Failed to start pipeline run",
                        )
                    }
                },
            )
        }
    }

    public fun cancelBuild() {
        val ws = _uiState.value.currentWorkspace
        val run = _uiState.value.latestRun
        if (ws == null || run == null || !run.state.isActive || cancelRunUseCase == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCancelling = true, errorMessage = null) }
            val result = cancelRunUseCase(ws.id, run.id)
            result.fold(
                onSuccess = { cancelledRun ->
                    _uiState.update { it.copy(latestRun = cancelledRun, isCancelling = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isCancelling = false,
                            errorMessage = error.message ?: "Failed to cancel pipeline run",
                        )
                    }
                },
            )
        }
    }

    public fun reattachBuild() {
        val ws = _uiState.value.currentWorkspace
        val run = _uiState.value.latestRun
        if (ws == null || run == null || !run.state.isActive || reattachRunUseCase == null) return

        viewModelScope.launch {
            reattachRunUseCase.invoke(ws.id, run.id)
        }
    }

    public fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

private val MISSING_DEVICE = ValidationError(
    stageId = null,
    objectId = null,
    fieldPath = "device",
    code = "MISSING_DEVICE",
    severity = Severity.ERROR,
    message = "This workspace has no device configuration",
)

/** The device file exists but could not be read (or the service is down): blocking, and never "no device". */
private fun unreadableDevice(error: Throwable) = ValidationError(
    stageId = null,
    objectId = null,
    fieldPath = "device",
    code = "UNREADABLE_DEVICE",
    severity = Severity.ERROR,
    message = "The device configuration could not be read: ${error.message ?: "unknown error"}",
)
