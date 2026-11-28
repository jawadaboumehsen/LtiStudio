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
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.common.utils.runCatchingCancellable
import org.ide.lti.core.domain.provenance.BuildProvenanceGenerator
import org.ide.lti.core.domain.provenance.ProvenanceVerifier
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.usecase.build.GetRunHistoryUseCase
import org.ide.lti.core.domain.workbench.DeviceCheckResult
import org.ide.lti.core.domain.workbench.WorkbenchRunner
import org.ide.lti.core.domain.workspace.RunComparison
import org.ide.lti.core.domain.workspace.RunComparisonText
import org.ide.lti.core.domain.workspace.RunRecord
import org.ide.lti.core.domain.workspace.RunTimeline
import org.ide.lti.core.domain.workspace.TimelineStepNode
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.workspace.Workspace

data class RunHistoryUiState(
    val currentWorkspace: Workspace? = null,
    val runs: List<BuildRun> = emptyList(),
    val selectedRun: BuildRun? = null,
    val selectedRunLogs: List<String> = emptyList(),
    val selectedStageFilter: StageId? = null,
    val selectedStepFilter: TimelineStepNode? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** The comparison being read, or shown; null when none is open. */
    val isComparing: Boolean = false,
    val comparison: RunComparisonText? = null,
    /** The run inspector's Markdown for the selected run; null when it is closed. */
    val inspection: String? = null,
    /** Non-null while verifying SBOM provenance for [selectedRun]. */
    val isVerifyingProvenance: Boolean = false,
    val provenanceTitle: String? = null,
    val provenanceReport: String? = null,
    /** The newest phone check of each run that has one, by run id (the history list's badge). */
    val deviceChecks: Map<String, DeviceCheckResult> = emptyMap(),
)

class RunHistoryViewModel(
    private val workspaceManager: WorkspaceManager,
    private val getRunHistoryUseCase: GetRunHistoryUseCase,
    private val runRepository: RunRepository,
    private val runComparison: RunComparison? = null,
    private val runner: WorkbenchRunner? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RunHistoryUiState())
    val uiState: StateFlow<RunHistoryUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null

    init {
        viewModelScope.launch {
            workspaceManager.currentWorkspace.collectLatest { ws ->
                analysisJob?.cancel()
                _uiState.update {
                    it.copy(
                        currentWorkspace = ws,
                        runs = emptyList(),
                        selectedRun = null,
                        selectedRunLogs = emptyList(),
                        isLoading = false,
                        errorMessage = null,
                        deviceChecks = emptyMap(),
                        isComparing = false,
                        comparison = null,
                        inspection = null,
                    )
                }
                if (ws != null) {
                    launch { loadDeviceChecks(ws.id) }
                    getRunHistoryUseCase.observe(ws.id).collectLatest { runs ->
                        _uiState.update { state ->
                            val currentSelId = state.selectedRun?.id
                            val sel = runs.find { it.id == currentSelId } ?: runs.firstOrNull()
                            state.withSelection(sel).copy(runs = runs)
                        }
                        _uiState.value.selectedRun?.let { showLogs(it.id) }
                    }
                }
            }
        }
    }

    /** Reads the phone checks of [workspaceId]'s runs; none are shown when they cannot be read. */
    private suspend fun loadDeviceChecks(workspaceId: String) {
        val reader = runner ?: return
        val checks = runCatchingCancellable { RunRecord.deviceChecks(reader, workspaceId) }.getOrDefault(emptyMap())
        _uiState.update { if (it.currentWorkspace?.id == workspaceId) it.copy(deviceChecks = checks) else it }
    }

    /** Reads [runId]'s log; a late read lands only on the run it was read for. */
    private suspend fun showLogs(runId: String) {
        val logs = runCatchingCancellable { runRepository.cachedEvents(runId) }.getOrDefault(emptyList())
        _uiState.update { if (it.selectedRun?.id == runId) it.copy(selectedRunLogs = logs) else it }
    }

    fun selectRun(run: BuildRun) {
        if (_uiState.value.currentWorkspace?.id != run.workspaceId) return
        _uiState.update { it.withSelection(run) }
        viewModelScope.launch { showLogs(run.id) }
    }

    /** Compares the selected run with [other]: the earlier of the two is "before". */
    fun compareWith(other: BuildRun) {
        val comparer = runComparison
        val selected = _uiState.value.selectedRun
        if (comparer == null || selected == null || _uiState.value.isComparing) return
        if (selected.workspaceId != other.workspaceId || selected.id == other.id) return
        val (older, newer) = listOf(selected, other).sortedBy { it.startedAt }
        analyze(selected) {
            val result = runCatchingCancellable { comparer.compare(older, newer) }
            val show: (RunHistoryUiState) -> RunHistoryUiState = { state ->
                result.fold(
                    onSuccess = { state.copy(comparison = it) },
                    onFailure = { e ->
                        state.copy(errorMessage = "The runs could not be compared: ${e.message}")
                    },
                )
            }
            show
        }
    }

    /**
     * The run inspector: where the selected run's time went (stages, their share, why each ran, slowest steps), then
     * what differs from the previous successful run, when there is one.
     */
    fun inspect() {
        val run = _uiState.value.selectedRun ?: return
        if (_uiState.value.isComparing) return
        val previous = _uiState.value.runs
            .filter { it.state == RunState.SUCCEEDED && it.startedAt < run.startedAt }
            .maxByOrNull { it.startedAt }
        analyze(run) {
            val header = "# Run inspector\n\n- Run: ${run.id} (${run.state.name.lowercase()})\n\n"
            val text = header + RunTimeline.of(run) + "\n" + sincePrevious(previous, run)
            val show: (RunHistoryUiState) -> RunHistoryUiState = { it.copy(inspection = text) }
            show
        }
    }

    private fun analyze(run: BuildRun, read: suspend () -> (RunHistoryUiState) -> RunHistoryUiState) {
        val workspaceId = _uiState.value.currentWorkspace?.id ?: return
        _uiState.update { it.copy(isComparing = true, errorMessage = null) }
        analysisJob = viewModelScope.launch {
            val show = read()
            coroutineContext.ensureActive()
            _uiState.update {
                if (it.currentWorkspace?.id == workspaceId && it.selectedRun?.id == run.id) {
                    show(it).copy(isComparing = false)
                } else {
                    it
                }
            }
        }
    }

    private fun RunHistoryUiState.withSelection(run: BuildRun?): RunHistoryUiState {
        if (selectedRun?.id == run?.id) return copy(selectedRun = run)
        analysisJob?.cancel()
        return copy(
            selectedRun = run,
            selectedRunLogs = emptyList(),
            selectedStageFilter = null,
            selectedStepFilter = null,
            isComparing = false,
            inspection = null,
            comparison = null,
            isVerifyingProvenance = false,
            provenanceTitle = null,
            provenanceReport = null,
        )
    }

    fun selectStageFilter(stageId: StageId?, step: TimelineStepNode? = null) {
        _uiState.update { it.copy(selectedStageFilter = stageId, selectedStepFilter = step) }
    }

    fun verifyProvenance() {
        val run = _uiState.value.selectedRun
        val workspaceId = _uiState.value.currentWorkspace?.id
        val reader = runner
        val ready = run != null && workspaceId != null && reader != null && !_uiState.value.isVerifyingProvenance
        if (!ready) return
        val targetRun = requireNotNull(run)
        val wsId = requireNotNull(workspaceId)
        val wb = requireNotNull(reader)
        _uiState.update { it.copy(isVerifyingProvenance = true, errorMessage = null) }
        viewModelScope.launch {
            val res = ProvenanceVerifier.readAndVerify(wb, wsId, targetRun.id)
            _uiState.update { state ->
                if (state.currentWorkspace?.id != wsId || state.selectedRun?.id != targetRun.id) {
                    state
                } else {
                    res.fold(
                        onSuccess = { (manifest, verification) ->
                            state.copy(
                                isVerifyingProvenance = false,
                                provenanceTitle = "SBOM Provenance (${targetRun.id})",
                                provenanceReport = BuildProvenanceGenerator.toMarkdownReport(manifest, verification),
                            )
                        },
                        onFailure = { err ->
                            state.copy(
                                isVerifyingProvenance = false,
                                errorMessage = err.message ?: "Failed to verify provenance manifest.",
                            )
                        },
                    )
                }
            }
        }
    }

    fun closeProvenance() {
        _uiState.update { it.copy(provenanceTitle = null, provenanceReport = null) }
    }

    /** The comparison with [previous] (the inspector's second part), its headings one level down. */
    private suspend fun sincePrevious(previous: BuildRun?, run: BuildRun): String {
        val comparer = runComparison
        return when {
            previous == null -> "No earlier run succeeded here, so there is nothing to compare.\n"

            comparer == null -> ""

            else -> runCatchingCancellable { comparer.compare(previous, run).markdown }.fold(
                onSuccess = { it.replaceFirst("# Compare runs", SINCE_HEADING).replace("\n## ", "\n### ") },
                onFailure = { "The previous run could not be compared: ${it.message}\n" },
            )
        }
    }

    fun closeInspection() {
        _uiState.update { it.copy(inspection = null) }
    }

    fun closeComparison() {
        _uiState.update { it.copy(comparison = null) }
    }

    fun refreshHistory() {
        val ws = _uiState.value.currentWorkspace ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val runs = getRunHistoryUseCase(ws.id, refreshArtifactPresence = true)
                if (_uiState.value.currentWorkspace?.id != ws.id) return@launch
                _uiState.update { state ->
                    val sel = runs.find { it.id == state.selectedRun?.id } ?: runs.firstOrNull()
                    state.withSelection(sel).copy(runs = runs, isLoading = false)
                }
                _uiState.value.selectedRun?.let { showLogs(it.id) }
                loadDeviceChecks(ws.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    if (it.currentWorkspace?.id == ws.id) it.copy(isLoading = false, errorMessage = e.message) else it
                }
            }
        }
    }

    fun deleteRun(runId: String) {
        if (_uiState.value.runs.firstOrNull { it.id == runId }?.state?.isActive == true) return
        viewModelScope.launch {
            try {
                runRepository.deleteRun(runId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Could not delete the run: ${e.message}") }
                return@launch
            }
            _uiState.update { state ->
                val remaining = state.runs.filterNot { it.id == runId }
                val newSelected = if (state.selectedRun?.id == runId) remaining.firstOrNull() else state.selectedRun
                state.withSelection(newSelected).copy(runs = remaining, errorMessage = null)
            }
            _uiState.value.selectedRun?.let { showLogs(it.id) }
        }
    }
}

private const val SINCE_HEADING = "## Since the previous successful run"
