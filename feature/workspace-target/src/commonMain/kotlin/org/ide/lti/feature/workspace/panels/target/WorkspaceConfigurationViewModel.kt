/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.target

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.repository.target.WorkspaceDeviceRepository
import org.ide.lti.core.domain.usecase.workspace.SaveConfigurationSnapshotUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.workspace.ConfigurationDraft
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.toDraft

data class WorkspaceConfigurationUiState(
    val currentWorkspace: Workspace? = null,
    val currentTarget: TargetDevice? = null,
    val initialSnapshot: ConfigurationSnapshot? = null,
    val draft: ConfigurationDraft? = null,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val availableRegions: List<TargetRegion> = listOf(TargetRegion.GLOBAL, TargetRegion.CHINA),
    val availableFirmwares: Map<TargetRegion, List<TargetFirmware>> = emptyMap(),
    val environmentReadiness: EnvironmentReadinessState = EnvironmentReadinessState.READY,
)

/**
 * Presentation ViewModel managing per-workspace target configuration editing.
 *
 * Implements FR-029: local edits in the [ConfigurationDraft] survive environment readiness shifts.
 */
class WorkspaceConfigurationViewModel(
    private val workspaceManager: WorkspaceManager,
    private val snapshotRepository: SnapshotRepository,
    private val workspaceDeviceRepository: WorkspaceDeviceRepository,
    private val saveConfigurationSnapshotUseCase: SaveConfigurationSnapshotUseCase,
    private val readinessPort: EnvironmentReadinessPort? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceConfigurationUiState())
    val uiState: StateFlow<WorkspaceConfigurationUiState> = _uiState.asStateFlow()

    init {
        observeCurrentWorkspace()
        observeEnvironmentReadiness()
    }

    private fun observeCurrentWorkspace() {
        viewModelScope.launch {
            workspaceManager.currentWorkspace.collectLatest { workspace ->
                if (workspace == null) {
                    _uiState.update { it.copy(currentWorkspace = null, draft = null, initialSnapshot = null) }
                    return@collectLatest
                }
                loadWorkspaceConfiguration(workspace)
                followDevice(workspace.id)
            }
        }
    }

    /** The device card follows the workspace's device when it is created or changed (Start from template, Save). */
    private suspend fun followDevice(workspaceId: String) {
        workspaceDeviceRepository.observe(workspaceId).catch { }.collect { device ->
            if (device == null) return@collect
            _uiState.update { state ->
                state.copy(
                    currentTarget = device,
                    availableRegions = device.availableRegions,
                    availableFirmwares = device.availableFirmwares,
                )
            }
        }
    }

    private fun observeEnvironmentReadiness() {
        readinessPort?.let { port ->
            viewModelScope.launch {
                port.observe().collectLatest { readiness ->
                    _uiState.update { state ->
                        state.copy(environmentReadiness = readiness.state)
                    }
                }
            }
        }
    }

    private suspend fun loadWorkspaceConfiguration(workspace: Workspace) {
        val target = runCatching { workspaceDeviceRepository.get(workspace.id) }.getOrNull()
        val regions = target?.availableRegions ?: listOf(TargetRegion.GLOBAL, TargetRegion.CHINA)
        val firmwares = target?.availableFirmwares ?: emptyMap()

        val snapshotId = workspace.effectiveSnapshotId
        val snapshot = if (snapshotId != null) {
            snapshotRepository.getSnapshot(snapshotId)
        } else {
            null
        }

        val draft = snapshot?.toDraft() ?: run {
            val defaultRegion = regions.firstOrNull() ?: TargetRegion.GLOBAL
            val defaultFw = firmwares[defaultRegion]?.firstOrNull() ?: TargetFirmware(
                version = "Default",
                buildId = "default",
                androidVersion = "15",
                securityPatch = "2026-02-01",
            )
            ConfigurationDraft(region = defaultRegion, firmware = defaultFw)
        }

        _uiState.update { state ->
            state.copy(
                currentWorkspace = workspace,
                currentTarget = target,
                initialSnapshot = snapshot,
                draft = draft,
                isDirty = false,
                availableRegions = regions,
                availableFirmwares = firmwares,
            )
        }
    }

    fun setBuildType(buildType: String) {
        _uiState.update { state ->
            val draft = state.draft ?: return@update state
            state.copy(draft = draft.copy(buildType = buildType), isDirty = true)
        }
    }

    fun setRomVersion(romVersion: String) {
        _uiState.update { state ->
            val draft = state.draft ?: return@update state
            state.copy(draft = draft.copy(romVersion = romVersion), isDirty = true)
        }
    }

    fun discardChanges() {
        _uiState.update { state ->
            val initial = state.initialSnapshot ?: return@update state
            state.copy(draft = initial.toDraft(), isDirty = false, errorMessage = null)
        }
    }

    fun saveConfiguration() {
        val state = _uiState.value
        val draft = state.draft
        val workspace = state.currentWorkspace
        if (draft == null || workspace == null) return

        persist(workspace, draft)
    }

    private fun persist(workspace: Workspace, draft: ConfigurationDraft) {
        val workspaceId = workspace.id
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            // Only what this panel edits is written; the rest (firmware source, OTA URL, ...) belongs to the
            // stage editors and is kept as they last saved it.
            val result = if (_uiState.value.initialSnapshot == null) {
                saveConfigurationSnapshotUseCase(workspaceId, draft)
            } else {
                saveConfigurationSnapshotUseCase.update(workspaceId) { current ->
                    current.copy(
                        assembly = current.assembly.copy(buildType = draft.buildType, romVersion = draft.romVersion),
                    )
                }
            }
            result.onSuccess { snapshot ->
                _uiState.update { s ->
                    // A completion for a workspace that is no longer active must never
                    // overwrite the active workspace's draft or claim it was saved.
                    if (s.currentWorkspace?.id != workspaceId) return@update s
                    s.copy(
                        initialSnapshot = snapshot,
                        draft = snapshot.toDraft(),
                        isDirty = false,
                        isSaving = false,
                        successMessage = "Configuration saved successfully",
                    )
                }
            }.onFailure { error ->
                _uiState.update { s ->
                    if (s.currentWorkspace?.id != workspaceId) return@update s
                    s.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "Failed to save configuration",
                    )
                }
            }
        }
    }
}
