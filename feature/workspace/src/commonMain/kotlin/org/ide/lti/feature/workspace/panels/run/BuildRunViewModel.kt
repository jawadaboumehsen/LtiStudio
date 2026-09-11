package org.ide.lti.feature.workspace.panels.run

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.usecase.build.ObserveRunUseCase
import org.ide.lti.core.domain.usecase.build.StartPipelineRunUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.workspace.Workspace

public data class BuildRunUiState(
    val currentWorkspace: Workspace? = null,
    val latestRun: BuildRun? = null,
    val isStartingRun: Boolean = false,
    val errorMessage: String? = null,
    val logs: List<String> = emptyList(),
    val canStartRun: Boolean = true,
) {
    val isRunning: Boolean
        get() = latestRun?.state?.isActive == true || isStartingRun

    val isRunDisabled: Boolean
        get() = isRunning || currentWorkspace == null
}

public class BuildRunViewModel(
    private val workspaceManager: WorkspaceManager,
    private val startPipelineRunUseCase: StartPipelineRunUseCase,
    private val observeRunUseCase: ObserveRunUseCase,
    private val runRepository: RunRepository? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuildRunUiState())
    public val uiState: StateFlow<BuildRunUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            workspaceManager.currentWorkspace.collectLatest { ws ->
                _uiState.update { it.copy(currentWorkspace = ws, errorMessage = null) }
                if (ws != null) {
                    observeRunUseCase.observeRuns(ws.id).collectLatest { runs ->
                        val latest = runs.maxByOrNull { it.startedAt }
                        val cachedLogs = if (latest != null && runRepository != null) {
                            runCatching { runRepository.cachedEvents(latest.id) }.getOrDefault(emptyList())
                        } else emptyList()

                        _uiState.update { state ->
                            state.copy(
                                latestRun = latest,
                                logs = if (cachedLogs.isNotEmpty()) cachedLogs else state.logs,
                                canStartRun = latest?.state?.isActive != true
                            )
                        }
                    }
                }
            }
        }
    }

    public fun startBuild() {
        val ws = _uiState.value.currentWorkspace ?: return
        if (_uiState.value.isRunning) return

        viewModelScope.launch {
            _uiState.update { it.copy(isStartingRun = true, errorMessage = null) }
            val result = startPipelineRunUseCase(ws.id)
            result.fold(
                onSuccess = { run ->
                    _uiState.update { it.copy(latestRun = run, isStartingRun = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isStartingRun = false,
                            errorMessage = error.message ?: "Failed to start pipeline run"
                        )
                    }
                }
            )
        }
    }

    public fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
