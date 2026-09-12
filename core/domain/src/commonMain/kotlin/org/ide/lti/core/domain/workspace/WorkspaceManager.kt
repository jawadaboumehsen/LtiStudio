/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workspace

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession

/**
 * Manages workspace state and operations.
 * Handles opening, closing, and switching between workspaces.
 */
class WorkspaceManager(
    val repository: WorkspaceRepository,
    private val coroutineScope: CoroutineScope,
) {
    private val _currentWorkspace = MutableStateFlow<Workspace?>(null)
    val currentWorkspace: StateFlow<Workspace?> = _currentWorkspace.asStateFlow()

    private val _currentSession = MutableStateFlow<WorkspaceSession?>(null)
    val currentSession: StateFlow<WorkspaceSession?> = _currentSession.asStateFlow()

    /**
     * Get all available workspaces.
     */
    fun getWorkspaces(): Flow<List<Workspace>> {
        return repository.getWorkspaces()
    }

    /**
     * Get recent projects.
     */
    fun getRecentProjects(): Flow<List<RecentProject>> {
        return repository.getRecentProjects()
    }

    /**
     * Open a workspace by ID.
     */
    suspend fun openWorkspace(workspaceId: String): Result<Workspace> {
        return try {
            val workspace = repository.getWorkspace(workspaceId)
                ?: return Result.failure(Exception("Workspace not found: $workspaceId"))

            // Load session if exists
            val session = repository.getWorkspaceSession(workspaceId)
                ?: WorkspaceSession(workspaceId = workspaceId)

            _currentWorkspace.value = workspace
            _currentSession.value = session

            // Update recent projects
            val recentProject = RecentProject(
                workspaceId = workspace.id,
                name = workspace.name,
                path = workspace.path,
                lastOpened = Clock.System.now(),
                type = workspace.type,
            )
            repository.addRecentProject(recentProject)

            Result.success(workspace)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Open a workspace by path.
     */
    suspend fun openWorkspaceByPath(path: String): Result<Workspace> {
        val workspaceId = path.hashCode().toString()
        val normalized = path.trimEnd('/', '\\')
        val name = normalized.substringAfterLast('/').substringAfterLast('\\').ifEmpty { path }

        val workspace = Workspace(
            id = workspaceId,
            name = name,
            path = path,
            lastOpened = Clock.System.now(),
        )

        repository.saveWorkspace(workspace)
        return openWorkspace(workspaceId)
    }

    /**
     * Shows a directory selection dialog and opens the chosen directory as a workspace.
     * Returns Result.success(workspace) if opened, Result.success(null) if selection was cancelled,
     * or Result.failure(exception) on error.
     */
    suspend fun selectAndOpenDirectory(): Result<Workspace?> {
        val selectedPath = org.ide.lti.core.common.utils.DialogUtils.selectDirectory()
        return if (selectedPath != null && selectedPath.isNotBlank()) {
            openWorkspaceByPath(selectedPath).map { it }
        } else {
            Result.success(null)
        }
    }

    /**
     * Sets the current workspace directly, without loading a session or touching recent
     * projects. Used as a best-effort fallback when the full [openWorkspace] flow fails but the
     * workspace itself was already persisted.
     */
    fun setCurrentWorkspace(workspace: Workspace?) {
        _currentWorkspace.value = workspace
    }

    /**
     * Close the current workspace.
     */
    suspend fun closeWorkspace() {
        _currentSession.value?.let { session ->
            repository.saveWorkspaceSession(session)
        }
        _currentWorkspace.value = null
        _currentSession.value = null
    }

    /**
     * Update current session state.
     */
    fun updateSession(update: (WorkspaceSession) -> WorkspaceSession) {
        _currentSession.value?.let { currentSession ->
            _currentSession.value = update(currentSession)
            // Auto-save session in background
            coroutineScope.launch {
                _currentSession.value?.let { repository.saveWorkspaceSession(it) }
            }
        }
    }

    /**
     * Delete a workspace.
     */
    suspend fun deleteWorkspace(workspaceId: String) {
        repository.deleteWorkspace(workspaceId)
        repository.removeRecentProject(workspaceId)
        if (_currentWorkspace.value?.id == workspaceId) {
            closeWorkspace()
        }
    }
}
