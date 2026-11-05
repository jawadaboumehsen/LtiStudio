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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.usecase.build.GetRunHistoryUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.workspace.Workspace

public data class RunHistoryUiState(
    val currentWorkspace: Workspace? = null,
    val runs: List<BuildRun> = emptyList(),
    val selectedRun: BuildRun? = null,
    val selectedRunLogs: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

public class RunHistoryViewModel(
    private val workspaceManager: WorkspaceManager,
    private val getRunHistoryUseCase: GetRunHistoryUseCase,
    private val runRepository: RunRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RunHistoryUiState())
    public val uiState: StateFlow<RunHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            workspaceManager.currentWorkspace.collectLatest { ws ->
                _uiState.update {
                    it.copy(
                        currentWorkspace = ws,
                        runs = emptyList(),
                        selectedRun = null,
                        selectedRunLogs = emptyList(),
                        isLoading = false,
                        errorMessage = null,
                    )
                }
                if (ws != null) {
                    getRunHistoryUseCase.observe(ws.id).collectLatest { runs ->
                        _uiState.update { state ->
                            val currentSelId = state.selectedRun?.id
                            val sel = runs.find { it.id == currentSelId } ?: runs.firstOrNull()
                            state.copy(
                                runs = runs,
                                selectedRun = sel,
                            )
                        }
                        _uiState.value.selectedRun?.let { showLogs(it.id) }
                    }
                }
            }
        }
    }

    /** Reads [runId]'s log; a late read lands only on the run it was read for. */
    private suspend fun showLogs(runId: String) {
        val logs = runCatching { runRepository.cachedEvents(runId) }.getOrDefault(emptyList())
        _uiState.update { if (it.selectedRun?.id == runId) it.copy(selectedRunLogs = logs) else it }
    }

    public fun selectRun(run: BuildRun) {
        _uiState.update { it.copy(selectedRun = run, selectedRunLogs = emptyList()) }
        viewModelScope.launch { showLogs(run.id) }
    }

    public fun refreshHistory() {
        val ws = _uiState.value.currentWorkspace ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val runs = getRunHistoryUseCase(ws.id, refreshArtifactPresence = true)
                if (_uiState.value.currentWorkspace?.id != ws.id) return@launch
                _uiState.update { state ->
                    val sel = runs.find { it.id == state.selectedRun?.id } ?: runs.firstOrNull()
                    state.copy(runs = runs, selectedRun = sel, isLoading = false)
                }
                _uiState.value.selectedRun?.let { showLogs(it.id) }
            } catch (e: Exception) {
                _uiState.update {
                    if (it.currentWorkspace?.id == ws.id) it.copy(isLoading = false, errorMessage = e.message) else it
                }
            }
        }
    }

    public fun deleteRun(runId: String) {
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
                val logs = if (newSelected == null) emptyList() else state.selectedRunLogs
                state.copy(runs = remaining, selectedRun = newSelected, selectedRunLogs = logs, errorMessage = null)
            }
            _uiState.value.selectedRun?.let { showLogs(it.id) }
        }
    }
}
