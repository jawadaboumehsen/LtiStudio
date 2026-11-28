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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.common.utils.runCatchingCancellable
import org.ide.lti.core.domain.pipeline.RunEstimate
import org.ide.lti.core.domain.pipeline.StagePreview
import org.ide.lti.core.domain.pipeline.configuration.DeviceConfigReadiness
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.stages.Preflight
import org.ide.lti.core.domain.pipeline.stages.PreflightFinding
import org.ide.lti.core.domain.pipeline.stages.PreflightFix
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.provenance.BuildProvenanceGenerator
import org.ide.lti.core.domain.provenance.ProvenanceVerifier
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.repository.target.WorkspaceDeviceRepository
import org.ide.lti.core.domain.usecase.build.CancelRunUseCase
import org.ide.lti.core.domain.usecase.build.ObserveRunUseCase
import org.ide.lti.core.domain.usecase.build.ReattachRunUseCase
import org.ide.lti.core.domain.usecase.build.StartPipelineRunUseCase
import org.ide.lti.core.domain.workbench.DeviceCheckResult
import org.ide.lti.core.domain.workbench.DeviceTest
import org.ide.lti.core.domain.workbench.DeviceVerdict
import org.ide.lti.core.domain.workbench.WorkbenchRunner
import org.ide.lti.core.domain.workspace.BuildReport
import org.ide.lti.core.domain.workspace.BuildReportText
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageState
import org.ide.lti.core.model.setup.EnvironmentReadiness
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.Workspace
import kotlin.time.Duration.Companion.milliseconds

data class BuildRunUiState(
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
    /** What the next run would rerun and why ("What will run?"); null until asked, cleared by any change. */
    val impact: List<StagePreview>? = null,
    /** How long [impact]'s stages took in earlier runs here; null when nothing runs or nothing was measured. */
    val estimate: RunEstimate? = null,
    val isPreviewing: Boolean = false,
    /** Why the last run stopped, with the fix the build check offers for it. */
    val diagnosis: PreflightFinding? = null,
    /** A fix applied from [diagnosis], or why it could not be. */
    val fixMessage: String? = null,
    /** The post-build report of [latestRun], or its phone check ([reportTitle] says which), when asked for. */
    val report: BuildReportText? = null,
    val reportTitle: String = "Build report",
    val isReporting: Boolean = false,
    val isVerifyingProvenance: Boolean = false,
    val isCheckingPhone: Boolean = false,
) {
    /** The message of the stage the last run failed in. */
    val failure: String?
        get() = latestRun?.takeIf { it.state == RunState.FAILED }?.stages
            ?.firstOrNull { it.state == StageState.FAILED }?.message

    val isRunning: Boolean
        get() = latestRun?.state?.isActive == true || isStartingRun

    val isRunDisabled: Boolean
        get() = isRunning || currentWorkspace == null || !canStartRun || blockingIssues.isNotEmpty()

    /** The device and stage problems that stop a run through [throughStage]. */
    val blockingIssues: List<ValidationError>
        get() = DeviceConfigReadiness.upTo(deviceIssues, throughStage)

    val canCancelRun: Boolean
        get() = latestRun?.state?.isActive == true && !isCancelling && latestRun.state != RunState.CANCELLING

    val isInterrupted: Boolean
        get() = latestRun?.state == RunState.INTERRUPTED

    val isPaused: Boolean
        get() = latestRun?.state == RunState.PAUSED_WAITING_FOR_APP
}

class BuildRunViewModel(
    private val workspaceManager: WorkspaceManager,
    private val startPipelineRunUseCase: StartPipelineRunUseCase,
    private val observeRunUseCase: ObserveRunUseCase,
    private val cancelRunUseCase: CancelRunUseCase? = null,
    private val reattachRunUseCase: ReattachRunUseCase? = null,
    private val runRepository: RunRepository? = null,
    private val readinessPort: EnvironmentReadinessPort? = null,
    private val workspaceDeviceRepository: WorkspaceDeviceRepository? = null,
    private val snapshotRepository: SnapshotRepository? = null,
    private val preflight: Preflight? = null,
    private val settingsStore: WorkspaceSettingsStore? = null,
    private val buildReport: BuildReport? = null,
    private val deviceTest: DeviceTest? = null,
    private val runner: WorkbenchRunner? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuildRunUiState())
    val uiState: StateFlow<BuildRunUiState> = _uiState.asStateFlow()
    private val autoReattachedRuns = mutableSetOf<String>()

    private var phoneCheckJob: Job? = null

    init {
        observeDeviceIssues()
        observeFailure()
        if (readinessPort != null) {
            viewModelScope.launch {
                readinessPort.observe().collectLatest { readiness ->
                    val ws = _uiState.value.currentWorkspace
                    val effective = if (ws != null) {
                        runCatchingCancellable { readinessPort.forWorkspace(ws) }.getOrDefault(readiness)
                    } else {
                        readiness
                    }
                    val isReady = effective.state == EnvironmentReadinessState.READY
                    _uiState.update { state ->
                        // Asked for a workspace no longer shown: its answer says nothing about this one.
                        if (state.currentWorkspace?.id != ws?.id) return@update state
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
                    runCatchingCancellable { readinessPort.forWorkspace(ws) }.getOrNull()
                } else {
                    null
                }

                val isReady = wsReadiness?.state?.let { it == EnvironmentReadinessState.READY }
                    ?: (
                        _uiState.value.environmentReadiness?.state?.let { it == EnvironmentReadinessState.READY }
                            ?: true
                        )

                _uiState.update {
                    // Another workspace's run is never shown as this one's, not even until its own runs are read.
                    val shown = it.latestRun.takeIf { _ -> it.currentWorkspace?.id == ws?.id }
                    it.withLatestRun(shown).copy(
                        currentWorkspace = ws,
                        logs = if (shown == null) emptyList() else it.logs,
                        isCancelling = it.isCancelling && shown != null,
                        // A start or cancel still running for the workspace shown before is not this one's.
                        isStartingRun = false,
                        errorMessage = null,
                        environmentReadiness = wsReadiness ?: it.environmentReadiness,
                        failingCheck = wsReadiness?.failingCheck ?: it.failingCheck,
                        remediation = wsReadiness?.remediation ?: it.remediation,
                        canStartRun = isReady && shown?.state?.isActive != true,
                    )
                }

                if (ws != null) {
                    observeRunUseCase.observeRuns(ws.id).collectLatest { runs ->
                        val latest = runs.maxByOrNull { it.startedAt }
                        val cachedLogs = if (latest != null && runRepository != null) {
                            runCatchingCancellable { runRepository.cachedEvents(latest.id) }.getOrDefault(emptyList())
                        } else {
                            emptyList()
                        }

                        _uiState.update { state ->
                            val currentIsReady =
                                state.environmentReadiness?.state?.let { it == EnvironmentReadinessState.READY } ?: true
                            state.withLatestRun(latest).copy(
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

                        if (latest != null && latest.state.isActive) reattach(ws.id, latest.id)
                    }
                }
            }
        }
    }

    /**
     * Not admitted (service or device unavailable): tried again until it is, as a run with no driver may never
     * change. A run update or another workspace cancels this, and the next look at the run starts over.
     */
    private suspend fun reattach(workspaceId: String, runId: String) {
        val use = reattachRunUseCase ?: return
        if (!autoReattachedRuns.add(runId)) return
        var attached: Any? = null
        try {
            while (attached == null) {
                attached = runCatching { use.invoke(workspaceId, runId) }
                    .onFailure { if (it is CancellationException) throw it }.getOrNull()
                if (attached == null) delay(REATTACH_RETRY_MS.milliseconds)
            }
        } finally {
            if (attached == null) autoReattachedRuns.remove(runId)
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
                        // An unreadable device blocks Start and says why; it is read again until it can be.
                        combine(devices.observe(ws.id), latest) { device, current -> deviceIssues(current, device) }
                            .retryWhen { error, _ ->
                                (error !is CancellationException).also {
                                    if (it) {
                                        emit(listOf(unreadableDevice(error)))
                                        delay(DEVICE_RETRY_MS.milliseconds)
                                    }
                                }
                            }
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
    fun selectThroughStage(stage: StageId?) {
        _uiState.update { it.copy(throughStage = stage, impact = null) }
    }

    /** "What will run?": the next run's cache decisions, stage by stage, without running anything. */
    fun previewImpact() {
        val ws = _uiState.value.currentWorkspace ?: return
        val through = _uiState.value.throughStage
        viewModelScope.launch {
            _uiState.update { it.copy(isPreviewing = true, impact = null, errorMessage = null) }
            val result = startPipelineRunUseCase.preview(ws.id, through)
            val history = runCatchingCancellable { runRepository?.observeRuns(ws.id)?.first() }.getOrNull().orEmpty()
            val estimate = result.getOrNull()?.let { RunEstimate.of(it, history) }
            _uiState.update { state ->
                if (state.currentWorkspace?.id != ws.id || state.throughStage != through) {
                    return@update state.copy(isPreviewing = false)
                }
                result.fold(
                    onSuccess = { state.copy(isPreviewing = false, impact = it, estimate = estimate) },
                    onFailure = { e ->
                        if (e is CancellationException) throw e
                        state.copy(isPreviewing = false, errorMessage = e.message ?: "The preview failed")
                    },
                )
            }
        }
    }

    /** The post-build report of the last run: its packages hashed again, keys, super budget, stages, changes. */
    fun makeReport() {
        val reporter = buildReport ?: return
        val run = _uiState.value.latestRun ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isReporting = true, errorMessage = null) }
            val result = runCatchingCancellable { reporter.of(run) }
            _uiState.update { state ->
                if (state.latestRun?.id != run.id) return@update state.copy(isReporting = false)
                result.fold(
                    onSuccess = { state.copy(isReporting = false, report = it, reportTitle = "Build report") },
                    onFailure = { e ->
                        if (e is CancellationException) throw e
                        state.copy(isReporting = false, errorMessage = e.message ?: "The report could not be made")
                    },
                )
            }
        }
    }

    /** Reads and verifies the latest build's machine-verifiable provenance SBOM against workspace files and keys. */
    fun verifyProvenance() {
        val ws = _uiState.value.currentWorkspace
        val run = _uiState.value.latestRun
        val wb = runner
        if (ws == null || run == null || wb == null || _uiState.value.isVerifyingProvenance) return
        viewModelScope.launch {
            _uiState.update { it.copy(isVerifyingProvenance = true, errorMessage = null) }
            val result = ProvenanceVerifier.readAndVerify(wb, ws.id, run.id)
            _uiState.update { state ->
                if (state.latestRun?.id != run.id) {
                    state.copy(isVerifyingProvenance = false)
                } else {
                    result.fold(
                        onSuccess = { (manifest, verdict) ->
                            val md = BuildProvenanceGenerator.toMarkdownReport(manifest, verdict)
                            state.copy(
                                isVerifyingProvenance = false,
                                report = BuildReportText(md, verdict.problems),
                                reportTitle = "Build Provenance & SBOM",
                            )
                        },
                        onFailure = { e ->
                            if (e is CancellationException) throw e
                            state.copy(
                                isVerifyingProvenance = false,
                                errorMessage = e.message ?: "The provenance manifest could not be verified",
                            )
                        },
                    )
                }
            }
        }
    }

    /**
     * Checks the connected phone against the last run after its package was installed: this build running, booted,
     * SELinux, verified boot, crashes. The result is kept with the run and shown like the report.
     */
    fun checkPhone() {
        val tester = deviceTest ?: return
        checkPhone(tester::check)
    }

    internal fun checkPhone(check: suspend (BuildRun) -> DeviceCheckResult) {
        val current = _uiState.value
        val run = current.latestRun
        val workspaceId = current.currentWorkspace?.id
        if (run == null || workspaceId == null || current.isCheckingPhone) return
        _uiState.update { it.copy(isCheckingPhone = true, errorMessage = null) }
        phoneCheckJob = viewModelScope.launch {
            val result = runCatchingCancellable { check(run) }
            coroutineContext.ensureActive()
            _uiState.update { state ->
                if (workspaceManager.currentWorkspace.value?.id != workspaceId ||
                    state.currentWorkspace?.id != workspaceId || state.latestRun?.id != run.id
                ) {
                    return@update state
                }
                result.fold(
                    onSuccess = { check ->
                        val problems = check.items.filter {
                            it.verdict == DeviceVerdict.FAIL
                        }.map { "${it.name}: ${it.detail}" }
                        state.copy(
                            isCheckingPhone = false,
                            report = BuildReportText(check.markdown(), problems),
                            reportTitle = "Phone check",
                        )
                    },
                    onFailure = { e ->
                        val why = (e as? RefusalException)?.refusal?.let { "${it.cause}. ${it.nextStep}" } ?: e.message
                        state.copy(isCheckingPhone = false, errorMessage = why ?: "The phone could not be checked")
                    },
                )
            }
        }
    }

    private fun BuildRunUiState.withLatestRun(run: BuildRun?): BuildRunUiState {
        if (latestRun?.id == run?.id) return copy(latestRun = run)
        phoneCheckJob?.cancel()
        return copy(
            latestRun = run,
            isCheckingPhone = false,
            report = report.takeUnless { reportTitle == "Phone check" },
        )
    }

    fun closeReport() {
        _uiState.update { it.copy(report = null) }
    }

    /** Applies the settings fix of [BuildRunUiState.diagnosis]; the next build then runs with it. */
    fun applyFix(fix: PreflightFix.Setting) {
        val ws = _uiState.value.currentWorkspace ?: return
        val store = settingsStore ?: return
        viewModelScope.launch {
            val saved = store.update(ws.id, fix.apply)
            _uiState.update {
                it.copy(
                    impact = null,
                    fixMessage = saved.fold(
                        onSuccess = { "Done: ${fix.label}. Start the build again." },
                        onFailure = { e -> e.message ?: "The setting could not be saved" },
                    ),
                )
            }
        }
    }

    /** Reads why the last run stopped each time it changes; the build's own refusal codes have a fix. */
    private fun observeFailure() {
        val checks = preflight ?: return
        viewModelScope.launch {
            _uiState.map { it.currentWorkspace to it.failure }.distinctUntilChanged().collectLatest { (ws, failure) ->
                val snapshot = ws?.effectiveSnapshotId?.let { snapshotRepository?.getSnapshot(it) }
                val diagnosis = if (failure != null && snapshot != null) checks.diagnose(failure, snapshot) else null
                _uiState.update {
                    if (it.currentWorkspace?.id != ws?.id) it else it.copy(diagnosis = diagnosis, fixMessage = null)
                }
            }
        }
    }

    fun startBuild() {
        val ws = _uiState.value.currentWorkspace ?: return
        if (_uiState.value.isRunDisabled) return

        viewModelScope.launch {
            _uiState.update { it.copy(isStartingRun = true, errorMessage = null, impact = null, fixMessage = null) }
            val result = startPipelineRunUseCase(ws.id, throughStage = _uiState.value.throughStage)
            // The panel may show another workspace by now: its run and error are not this one's.
            val shown = { state: BuildRunUiState -> state.currentWorkspace?.id == ws.id }
            result.fold(
                onSuccess = { run ->
                    _uiState.update { if (shown(it)) it.withLatestRun(run).copy(isStartingRun = false) else it }
                },
                onFailure = { error ->
                    _uiState.update {
                        if (!shown(it)) return@update it
                        it.copy(
                            isStartingRun = false,
                            errorMessage = error.message ?: "Failed to start pipeline run",
                        )
                    }
                },
            )
        }
    }

    fun cancelBuild() {
        val ws = _uiState.value.currentWorkspace
        val run = _uiState.value.latestRun
        if (ws == null || run == null || !run.state.isActive || cancelRunUseCase == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCancelling = true, errorMessage = null) }
            val result = cancelRunUseCase(ws.id, run.id)
            val shown = { state: BuildRunUiState -> state.currentWorkspace?.id == ws.id }
            result.fold(
                onSuccess = { cancelledRun ->
                    _uiState.update {
                        if (shown(
                                it,
                            )
                        ) {
                            it.withLatestRun(cancelledRun).copy(isCancelling = cancelledRun.state.isActive)
                        } else {
                            it
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        if (!shown(it)) return@update it
                        it.copy(
                            isCancelling = false,
                            errorMessage = error.message ?: "Failed to cancel pipeline run",
                        )
                    }
                },
            )
        }
    }

    fun reattachBuild() {
        val ws = _uiState.value.currentWorkspace
        val run = _uiState.value.latestRun
        if (ws == null || run == null || !run.state.isActive || reattachRunUseCase == null) return

        viewModelScope.launch {
            reattachRunUseCase.invoke(ws.id, run.id)
        }
    }

    fun clearError() {
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

private const val DEVICE_RETRY_MS = 5_000L

/** A reattach the service or device refused is tried this many times, this far apart. */
private const val REATTACH_RETRY_MS = 5_000L
