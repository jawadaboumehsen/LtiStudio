/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.creation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.ports.KeySource
import org.ide.lti.core.domain.ports.ProvisioningEvent
import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.usecase.workspace.CreateWorkspaceParams
import org.ide.lti.core.domain.usecase.workspace.CreateWorkspaceUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.setup.EnvironmentReadiness
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.Workspace

/**
 * Sequential provisioning steps displayed on the UI stepper.
 */
enum class CreationStage(val stepId: String, val label: String) {
    LAYOUT("layout", "Creating directory layout"),
    CONFIG("config", "Writing workspace configuration"),
    KEY("key", "Provisioning signing keys"),
    VALIDATE("validate", "Validating environment"),
    PROMOTE("promote", "Promoting to active workspace"),
    ;

    companion object {
        fun fromStepId(id: String): CreationStage? = entries.find { it.stepId.equals(id, ignoreCase = true) }
    }
}

data class CreateWorkspaceUiState(
    val name: String = "",
    val nameError: String? = null,
    val selectedTarget: TargetDevice? = null,
    val availableTargets: List<TargetDevice> = emptyList(),
    val keySource: KeySource = KeySource.GENERATE,
    val isProvisioning: Boolean = false,
    val currentStage: CreationStage = CreationStage.LAYOUT,
    val logs: List<String> = emptyList(),
    val errorMessage: String? = null,
    val createdWorkspace: Workspace? = null,
    val canCreate: Boolean = true,
    val failingCheck: String? = null,
    val remediation: String? = null,
    val environmentReadiness: EnvironmentReadiness? = null,
)

/**
 * ViewModel governing workspace creation with stepper progression, log streaming,
 * validation, and cancellation.
 */
class CreateWorkspaceViewModel(
    private val createWorkspaceUseCase: CreateWorkspaceUseCase,
    private val targetRepository: TargetRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val workspaceManager: WorkspaceManager? = null,
    private val readinessPort: EnvironmentReadinessPort? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateWorkspaceUiState())
    val uiState: StateFlow<CreateWorkspaceUiState> = _uiState.asStateFlow()

    private var provisioningJob: Job? = null

    init {
        loadTargets()
        if (readinessPort != null) {
            viewModelScope.launch {
                readinessPort.observe().collectLatest { readiness ->
                    val isReady = readiness.state == EnvironmentReadinessState.READY
                    _uiState.update { s ->
                        s.copy(
                            environmentReadiness = readiness,
                            canCreate = isReady,
                            failingCheck = readiness.failingCheck,
                            remediation = readiness.remediation,
                        )
                    }
                }
            }
        }
    }

    private fun loadTargets() {
        viewModelScope.launch {
            targetRepository.getAvailableTargets().collectLatest { targets ->
                _uiState.update { state ->
                    state.copy(
                        availableTargets = targets,
                        selectedTarget = state.selectedTarget ?: targets.firstOrNull(),
                    )
                }
            }
        }
    }

    fun setName(name: String) {
        _uiState.update { it.copy(name = name, nameError = null, errorMessage = null) }
    }

    fun selectTarget(target: TargetDevice) {
        _uiState.update { it.copy(selectedTarget = target, errorMessage = null) }
    }

    fun setKeySource(keySource: KeySource) {
        _uiState.update { it.copy(keySource = keySource) }
    }

    /**
     * Returns the target to create the workspace for, or null after recording why creation cannot start.
     * Checks run in a fixed order: environment readiness, then the name, then the target.
     */
    private fun validateCreationState(state: CreateWorkspaceUiState): TargetDevice? {
        val target = state.selectedTarget
        when {
            !state.canCreate -> _uiState.update {
                it.copy(errorMessage = state.remediation ?: state.failingCheck ?: "Environment is not ready")
            }
            state.name.isBlank() -> _uiState.update { it.copy(nameError = "Workspace name cannot be blank") }
            target == null -> _uiState.update { it.copy(errorMessage = "Target device is required") }
            else -> return target
        }
        return null
    }

    fun startCreation() {
        val state = _uiState.value
        val target = validateCreationState(state) ?: return

        _uiState.update {
            it.copy(
                isProvisioning = true,
                currentStage = CreationStage.LAYOUT,
                logs = emptyList(),
                errorMessage = null,
            )
        }

        provisioningJob = viewModelScope.launch {
            try {
                val params = CreateWorkspaceParams(
                    name = state.name.trim(),
                    target = target,
                    keySource = state.keySource,
                )

                createWorkspaceUseCase(params).collect { event ->
                    when (event) {
                        is ProvisioningEvent.Step -> {
                            val stage = CreationStage.fromStepId(event.id) ?: _uiState.value.currentStage
                            _uiState.update { s ->
                                s.copy(
                                    currentStage = stage,
                                    logs = s.logs + "[STEP] ${event.label}",
                                )
                            }
                        }
                        is ProvisioningEvent.Output -> {
                            _uiState.update { s ->
                                s.copy(logs = s.logs + event.text)
                            }
                        }
                        is ProvisioningEvent.Failed -> {
                            _uiState.update { s ->
                                s.copy(
                                    isProvisioning = false,
                                    errorMessage = event.message,
                                    logs = s.logs + "[FAILED] ${event.step}: ${event.message}",
                                )
                            }
                        }
                        is ProvisioningEvent.Completed -> {
                            val workspaces = workspaceRepository.getWorkspaces().firstOrNull().orEmpty()
                            val ws = workspaces.find { it.name.equals(state.name.trim(), ignoreCase = true) }
                            ws?.let { workspaceManager?.setCurrentWorkspace(it) }
                            _uiState.update { s ->
                                s.copy(
                                    isProvisioning = false,
                                    currentStage = CreationStage.PROMOTE,
                                    createdWorkspace = ws,
                                    logs = s.logs + "[COMPLETED] Workspace provisioned successfully",
                                )
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { s ->
                    s.copy(
                        isProvisioning = false,
                        errorMessage = e.message ?: "Failed to provision workspace",
                        logs = s.logs + "[ERROR] ${e.message}",
                    )
                }
            }
        }
    }

    fun cancel() {
        provisioningJob?.cancel()
        provisioningJob = null
        viewModelScope.launch {
            createWorkspaceUseCase.cancel()
        }
        _uiState.update { it.copy(isProvisioning = false, errorMessage = "Provisioning cancelled") }
    }
}
