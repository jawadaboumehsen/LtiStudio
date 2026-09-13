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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.usecase.workspace.DiscoverWorkdirWorkspacesUseCase
import org.ide.lti.core.domain.usecase.workspace.GetRecentWorkspacesUseCase
import org.ide.lti.core.domain.usecase.workspace.OpenWorkspaceByPathUseCase
import org.ide.lti.core.domain.usecase.workspace.OpenWorkspaceUseCase
import org.ide.lti.core.domain.usecase.workspace.RemoveRecentWorkspaceUseCase
import org.ide.lti.core.domain.usecase.workspace.SelectAndOpenDirectoryUseCase
import org.ide.lti.core.model.setup.EnvironmentReadiness
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
 */
class WorkspaceBrowserState(
    private val scope: CoroutineScope,
    private val getRecentWorkspacesUseCase: GetRecentWorkspacesUseCase,
    private val openWorkspaceUseCase: OpenWorkspaceUseCase,
    private val removeRecentWorkspaceUseCase: RemoveRecentWorkspaceUseCase,
    private val openWorkspaceByPathUseCase: OpenWorkspaceByPathUseCase? = null,
    private val selectAndOpenDirectoryUseCase: SelectAndOpenDirectoryUseCase? = null,
    private val discoverWorkdirWorkspacesUseCase: DiscoverWorkdirWorkspacesUseCase? = null,
    private val environmentReadinessPort: EnvironmentReadinessPort? = null,
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

    fun openRecentProject(project: RecentProject, onWorkspaceOpened: (Workspace) -> Unit = {}) {
        scope.launch {
            val result = runCatching { openWorkspaceUseCase(project.workspaceId) }.getOrElse { Result.failure(it) }
            result.fold(
                onSuccess = { workspace ->
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

    fun openFolder(onWorkspaceOpened: (Workspace) -> Unit = {}) {
        if (selectAndOpenDirectoryUseCase == null) return
        scope.launch {
            val result = runCatching { selectAndOpenDirectoryUseCase() }.getOrElse { Result.failure(it) }
            result.fold(
                onSuccess = { workspace ->
                    if (workspace != null) {
                        _workspaceError.value = null
                        onWorkspaceOpened(workspace)
                    }
                },
                onFailure = { error ->
                    _workspaceError.value = WorkspaceError(
                        action = WorkspaceAction.OPEN,
                        workspaceId = null,
                        workspacePath = null,
                        reason = error.message ?: "Failed to open selected directory",
                        retryAction = { openFolder(onWorkspaceOpened) },
                    )
                },
            )
        }
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
                IllegalStateException("Execution environment readiness check is unavailable. WSL / daemon service required for creation.")
            )
        }
        val readiness = environmentReadinessPort.refresh()
        return if (readiness.state == EnvironmentReadinessState.READY) {
            Result.success(Unit)
        } else {
            val reason = readiness.failingCheck
                ?: "Execution environment not ready (${readiness.state}). Setup WSL2 and execution service to create workspaces."
            Result.failure(IllegalStateException(reason))
        }
    }
}
