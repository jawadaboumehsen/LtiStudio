/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ide.lti.core.common.utils.DialogUtils
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.usecase.workspace.DiscoverWorkdirWorkspacesUseCase
import org.ide.lti.core.domain.usecase.workspace.GetRecentWorkspacesUseCase
import org.ide.lti.core.domain.usecase.workspace.OpenWorkspaceByPathUseCase
import org.ide.lti.core.domain.usecase.workspace.OpenWorkspaceUseCase
import org.ide.lti.core.domain.usecase.workspace.RemoveRecentWorkspaceUseCase
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace

enum class WorkspaceAction { OPEN, CREATE, REMOVE, BROWSE }

data class WorkspaceError(
    val action: WorkspaceAction,
    val workspaceId: String? = null,
    val workspacePath: String? = null,
    val reason: String,
    val retryAction: (() -> Unit)? = null,
)

sealed interface PendingWorkspaceRequest {
    data class OpenRecent(val project: RecentProject) : PendingWorkspaceRequest
    data class OpenPath(val path: String) : PendingWorkspaceRequest
}

/**
 * Feature-local state holder for workspace browsing in the Setup destination.
 *
 * Responsibilities:
 * - Manages recent projects, search filtering, and list selection.
 * - Preserves session search, selection, and list position across destination navigation (FR-004).
 * - Separates cached workspace browsing from live execution readiness (FR-002, FR-003, SC-001).
 * - Contextual removal deletes only the recent record (never the workspace files).
 * - Automatically updates selection to the nearest remaining row upon removal of a selected item.
 * - Reports creation prerequisites when execution environment (WSL / remote daemon) is unavailable.
 * - Safely intercepts and queues workspace open requests while environment verification runs (Phase 3).
 */
class WorkspaceBrowserState(
    private val scope: CoroutineScope,
    private val getRecentWorkspacesUseCase: GetRecentWorkspacesUseCase,
    private val openWorkspaceUseCase: OpenWorkspaceUseCase,
    private val removeRecentWorkspaceUseCase: RemoveRecentWorkspaceUseCase,
    private val toolchainSetupState: StateFlow<ToolchainSetupState>,
    private val openWorkspaceByPathUseCase: OpenWorkspaceByPathUseCase? = null,
    private val discoverWorkdirWorkspacesUseCase: DiscoverWorkdirWorkspacesUseCase? = null,
    private val environmentReadinessPort: EnvironmentReadinessPort? = null,
    private val selectDirectory: suspend () -> String? = { DialogUtils.selectDirectory() },
    private val onFailingCheckFocus: (() -> Unit)? = null,
) {
    val recentProjects: StateFlow<List<RecentProject>> = getRecentWorkspacesUseCase()
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedWorkspaceId = MutableStateFlow<String?>(null)
    val selectedWorkspaceId: StateFlow<String?> = _selectedWorkspaceId.asStateFlow()

    private val _workspaceError = MutableStateFlow<WorkspaceError?>(null)
    val workspaceError: StateFlow<WorkspaceError?> = _workspaceError.asStateFlow()

    private val _pendingWorkspaceRequest = MutableStateFlow<PendingWorkspaceRequest?>(null)
    val pendingWorkspaceRequest: StateFlow<PendingWorkspaceRequest?> = _pendingWorkspaceRequest.asStateFlow()
    private var pendingCallback: ((Workspace) -> Unit)? = null

    init {
        scope.launch {
            toolchainSetupState
                .map { it.isChecking to it.canLaunchWorkspace }
                .distinctUntilChanged()
                .collect { (isChecking, canLaunch) ->
                    if (!isChecking) {
                        val pending = _pendingWorkspaceRequest.value
                        if (pending != null) {
                            _pendingWorkspaceRequest.value = null
                            val callback = pendingCallback ?: {}
                            pendingCallback = null
                            if (canLaunch) {
                                when (pending) {
                                    is PendingWorkspaceRequest.OpenRecent -> executeOpenRecent(
                                        pending.project,
                                        callback,
                                    )
                                    is PendingWorkspaceRequest.OpenPath -> executeOpenPath(
                                        pending.path,
                                        callback,
                                    )
                                }
                            } else {
                                val failingReason = getFailingReason(toolchainSetupState.value)
                                val (wsId, wsPath) = when (pending) {
                                    is PendingWorkspaceRequest.OpenRecent -> {
                                        pending.project.workspaceId to pending.project.path
                                    }
                                    is PendingWorkspaceRequest.OpenPath -> {
                                        null to pending.path
                                    }
                                }
                                _workspaceError.value = WorkspaceError(
                                    action = WorkspaceAction.OPEN,
                                    workspaceId = wsId,
                                    workspacePath = wsPath,
                                    reason = failingReason,
                                    retryAction = when (pending) {
                                        is PendingWorkspaceRequest.OpenRecent -> {
                                            { openRecentProject(pending.project, callback) }
                                        }
                                        is PendingWorkspaceRequest.OpenPath -> {
                                            { openPath(pending.path, callback) }
                                        }
                                    },
                                )
                                onFailingCheckFocus?.invoke()
                            }
                        }
                    }
                }
        }
    }

    fun clearWorkspaceError() {
        _workspaceError.value = null
    }

    var scrollIndex: Int = 0
        private set
    var scrollOffset: Int = 0
        private set

    val filteredProjects: StateFlow<List<RecentProject>> = combine(
        recentProjects,
        searchQuery,
    ) { projects, query ->
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            projects
        } else {
            projects.filter { project ->
                project.name.contains(trimmed, ignoreCase = true) ||
                    project.path.contains(trimmed, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun selectWorkspace(workspaceId: String?) {
        _selectedWorkspaceId.value = workspaceId
    }

    fun setListPosition(index: Int, offset: Int) {
        scrollIndex = index
        scrollOffset = offset
    }

    fun getListPosition(): Pair<Int, Int> = Pair(scrollIndex, scrollOffset)

    fun cancelPendingWorkspaceRequest() {
        _pendingWorkspaceRequest.value = null
        pendingCallback = null
    }

    fun openRecentProject(project: RecentProject, onWorkspaceOpened: (Workspace) -> Unit = {}) {
        val state = toolchainSetupState.value
        if (state.canLaunchWorkspace) {
            _pendingWorkspaceRequest.value = null
            pendingCallback = null
            executeOpenRecent(project, onWorkspaceOpened)
        } else if (state.isChecking) {
            _workspaceError.value = null
            _pendingWorkspaceRequest.value = PendingWorkspaceRequest.OpenRecent(project)
            pendingCallback = onWorkspaceOpened
        } else {
            _pendingWorkspaceRequest.value = null
            pendingCallback = null
            val failingReason = getFailingReason(state)
            _workspaceError.value = WorkspaceError(
                action = WorkspaceAction.OPEN,
                workspaceId = project.workspaceId,
                workspacePath = project.path,
                reason = failingReason,
                retryAction = { openRecentProject(project, onWorkspaceOpened) },
            )
            onFailingCheckFocus?.invoke()
        }
    }

    fun openFolder(onWorkspaceOpened: (Workspace) -> Unit = {}) {
        if (openWorkspaceByPathUseCase == null) return
        scope.launch {
            val selectedPath = selectDirectory()
            if (selectedPath.isNullOrBlank()) return@launch
            openPath(selectedPath, onWorkspaceOpened)
        }
    }

    fun openPath(path: String, onWorkspaceOpened: (Workspace) -> Unit = {}) {
        if (openWorkspaceByPathUseCase == null) return
        val state = toolchainSetupState.value
        if (state.canLaunchWorkspace) {
            _pendingWorkspaceRequest.value = null
            pendingCallback = null
            executeOpenPath(path, onWorkspaceOpened)
        } else if (state.isChecking) {
            _workspaceError.value = null
            _pendingWorkspaceRequest.value = PendingWorkspaceRequest.OpenPath(path)
            pendingCallback = onWorkspaceOpened
        } else {
            _pendingWorkspaceRequest.value = null
            pendingCallback = null
            val failingReason = getFailingReason(state)
            _workspaceError.value = WorkspaceError(
                action = WorkspaceAction.OPEN,
                workspaceId = null,
                workspacePath = path,
                reason = failingReason,
                retryAction = { openPath(path, onWorkspaceOpened) },
            )
            onFailingCheckFocus?.invoke()
        }
    }

    private fun executeOpenRecent(project: RecentProject, onWorkspaceOpened: (Workspace) -> Unit) {
        if (!toolchainSetupState.value.canLaunchWorkspace) {
            openRecentProject(project, onWorkspaceOpened)
            return
        }
        scope.launch {
            if (!toolchainSetupState.value.canLaunchWorkspace) {
                openRecentProject(project, onWorkspaceOpened)
                return@launch
            }
            val result = runCatching { openWorkspaceUseCase(project.workspaceId) }.getOrElse { Result.failure(it) }
            result.fold(
                onSuccess = { workspace ->
                    if (!toolchainSetupState.value.canLaunchWorkspace) {
                        openRecentProject(project, onWorkspaceOpened)
                        return@fold
                    }
                    _workspaceError.value = null
                    onWorkspaceOpened(workspace)
                },
                onFailure = { error ->
                    _workspaceError.value = WorkspaceError(
                        action = WorkspaceAction.OPEN,
                        workspaceId = project.workspaceId,
                        workspacePath = project.path,
                        reason = error.message ?: "Failed to open workspace ${project.name}",
                        retryAction = { openRecentProject(project, onWorkspaceOpened) },
                    )
                },
            )
        }
    }

    private fun executeOpenPath(path: String, onWorkspaceOpened: (Workspace) -> Unit) {
        if (openWorkspaceByPathUseCase == null) return
        if (!toolchainSetupState.value.canLaunchWorkspace) {
            openPath(path, onWorkspaceOpened)
            return
        }
        scope.launch {
            if (!toolchainSetupState.value.canLaunchWorkspace) {
                openPath(path, onWorkspaceOpened)
                return@launch
            }
            val result = runCatching { openWorkspaceByPathUseCase(path) }.getOrElse { Result.failure(it) }
            result.fold(
                onSuccess = { workspace ->
                    if (!toolchainSetupState.value.canLaunchWorkspace) {
                        openPath(path, onWorkspaceOpened)
                        return@fold
                    }
                    _workspaceError.value = null
                    onWorkspaceOpened(workspace)
                },
                onFailure = { error ->
                    _workspaceError.value = WorkspaceError(
                        action = WorkspaceAction.OPEN,
                        workspaceId = null,
                        workspacePath = path,
                        reason = error.message ?: "Failed to open selected directory",
                        retryAction = { openPath(path, onWorkspaceOpened) },
                    )
                },
            )
        }
    }

    private fun getFailingReason(state: ToolchainSetupState): String {
        val failing = state.steps.firstOrNull { it.status == StepStatus.FAILED }
        return failing?.error
            ?: failing?.description
            ?: "Cannot open workspace: host environment verification has not passed."
    }

    fun removeRecentProject(workspaceId: String) {
        val currentFiltered = filteredProjects.value
        val removedIndex = currentFiltered.indexOfFirst { it.workspaceId == workspaceId }
        val removedProject = currentFiltered.getOrNull(removedIndex)
        val wasSelected = _selectedWorkspaceId.value == workspaceId

        scope.launch {
            val result = runCatching { removeRecentWorkspaceUseCase(workspaceId) }
            result.fold(
                onSuccess = {
                    _workspaceError.value = null
                    if (wasSelected && removedIndex >= 0) {
                        val remaining = currentFiltered.filter { it.workspaceId != workspaceId }
                        if (remaining.isNotEmpty()) {
                            val newIndex = removedIndex.coerceAtMost(remaining.lastIndex)
                            _selectedWorkspaceId.value = remaining[newIndex].workspaceId
                        } else {
                            _selectedWorkspaceId.value = null
                        }
                    }
                },
                onFailure = { error ->
                    _workspaceError.value = WorkspaceError(
                        action = WorkspaceAction.REMOVE,
                        workspaceId = workspaceId,
                        workspacePath = removedProject?.path,
                        reason = error.message
                            ?: "Failed to remove ${removedProject?.name ?: "workspace"} from recents",
                        retryAction = { removeRecentProject(workspaceId) },
                    )
                },
            )
        }
    }

    fun discoverWorkspaces() {
        scope.launch {
            discoverWorkdirWorkspacesUseCase?.invoke()
        }
    }

    /**
     * Creation prerequisite check (FR-002, FR-003, SC-001):
     * Browsing cached workspaces works offline, but remote workspace creation requires
     * a verified, ready execution environment (WSL2 / daemon).
     */
    suspend fun checkCreationPrerequisites(): Result<Unit> {
        if (environmentReadinessPort == null) {
            return Result.failure(
                IllegalStateException(
                    "Execution environment readiness check is unavailable. " +
                        "WSL / daemon service required for creation.",
                ),
            )
        }
        val readiness = environmentReadinessPort.refresh()
        return if (readiness.state == EnvironmentReadinessState.READY) {
            Result.success(Unit)
        } else {
            val reason = readiness.failingCheck
                ?: (
                    "Execution environment not ready (${readiness.state}). " +
                        "Setup WSL2 and execution service to create workspaces."
                    )
            Result.failure(IllegalStateException(reason))
        }
    }
}
