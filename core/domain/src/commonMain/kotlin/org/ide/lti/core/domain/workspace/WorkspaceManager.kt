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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import org.ide.lti.core.model.workspace.WorkspaceType

/**
 * Manages workspace state and operations.
 * Handles opening, closing, and switching between workspaces.
 */
class WorkspaceManager(val repository: WorkspaceRepository, private val coroutineScope: CoroutineScope) {
    private val _currentWorkspace = MutableStateFlow<Workspace?>(null)
    val currentWorkspace: StateFlow<Workspace?> = _currentWorkspace.asStateFlow()

    private val _currentSession = MutableStateFlow<WorkspaceSession?>(null)
    val currentSession: StateFlow<WorkspaceSession?> = _currentSession.asStateFlow()

    /** Opens and closes one at a time, in the order asked: the last request is the workspace that stays. */
    private val lifecycle = Mutex()

    /**
     * Get all available workspaces.
     */
    fun getWorkspaces(): Flow<List<Workspace>> = repository.getWorkspaces()

    /**
     * Get recent projects.
     */
    fun getRecentProjects(): Flow<List<RecentProject>> = repository.getRecentProjects()

    /**
     * Open a workspace by ID.
     */
    suspend fun openWorkspace(workspaceId: String): Result<Workspace> = lifecycle.withLock {
        try {
            val workspace = repository.getWorkspace(workspaceId)
                ?: return@withLock Result.failure(Exception("Workspace not found: $workspaceId"))

            // Load session if exists
            val session = repository.getWorkspaceSession(workspaceId)
                ?: WorkspaceSession(workspaceId = workspaceId)

            _currentWorkspace.value = workspace
            _currentSession.value = session

            // Update recent projects, keeping what the entry already knew (its device name)
            val previous = repository.getRecentProjects().first().firstOrNull { it.workspaceId == workspace.id }
            val recentProject = RecentProject(
                workspaceId = workspace.id,
                name = workspace.name,
                path = workspace.path,
                lastOpened = Clock.System.now(),
                type = workspace.type,
                targetBinding = workspace.targetBinding,
                targetDisplayName = previous?.targetDisplayName,
            )
            // The workspace is open either way: a failed recents update is not an open failure.
            runCatching { repository.addRecentProject(recentProject) }

            Result.success(workspace)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Open a workspace by path.
     */
    suspend fun openWorkspaceByPath(path: String): Result<Workspace> {
        val workspace = workspaceForPath(repository, path)
        return runCatching { repository.saveWorkspace(workspace) }.fold(
            onSuccess = { openWorkspace(workspace.id) },
            onFailure = { Result.failure(it) },
        )
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
        if (_currentSession.value?.workspaceId != workspace?.id) {
            _currentSession.value = workspace?.let { WorkspaceSession(workspaceId = it.id) }
        }
    }

    /**
     * Close the current workspace.
     */
    suspend fun closeWorkspace(): Unit = lifecycle.withLock {
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
            val updated = update(currentSession)
            _currentSession.value = updated
            // Auto-save in background: the edited session itself, not whichever is current when it runs.
            coroutineScope.launch { repository.saveWorkspaceSession(updated) }
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

    companion object {
        /**
         * The workspace registered for [path] (compared as [canonicalPath]: separators, trailing slash and, for a
         * Windows path, letter case do not matter), keeping all it records; else a new one with an id derived from
         * the canonical path by SHA-256 (String.hashCode collides: "Aa" and "BB").
         */
        suspend fun workspaceForPath(
            repository: WorkspaceRepository,
            path: String,
            type: WorkspaceType = WorkspaceType.LOCAL,
        ): Workspace {
            val key = canonicalPath(path)
            val now = Clock.System.now()
            repository.getWorkspaces().first().firstOrNull { canonicalPath(it.path) == key }
                ?.let { return it.copy(lastOpened = now) }
            // A drive root keeps its separator: "C:" alone means that drive's current folder.
            val trimmed = path.trimEnd('/', '\\').ifEmpty { path }
                .let { if (DRIVE_ONLY.matches(it)) it + path.drop(it.length).take(1) else it }
            val name = trimmed.substringAfterLast('/').substringAfterLast('\\').ifEmpty { path }
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(key.encodeToByteArray())
            val id = "ws-" + digest.take(ID_BYTES).joinToString("") { "%02x".format(it) }
            return Workspace(id = id, name = name, path = trimmed, type = type, lastOpened = now)
        }

        fun canonicalPath(path: String): String {
            val forward = path.replace('\\', '/')
            // A UNC root (//server/share) keeps its two slashes: collapsing them would make it a Linux path.
            val unc = forward.startsWith("//")
            val slashes = (if (unc) "/" else "") + forward.replace(Regex("/+"), "/").trimEnd('/').ifEmpty { "/" }
            // Inside WSL the folders are Linux ones: only the host and distribution ignore case.
            WSL_ROOT.find(slashes)?.let { return it.value.lowercase() + slashes.substring(it.range.last + 1) }
            return if (unc || DRIVE.containsMatchIn(slashes)) slashes.lowercase() else slashes
        }

        private val DRIVE_ONLY = Regex("^[A-Za-z]:$")

        private val DRIVE = Regex("^[A-Za-z]:")
        private val WSL_ROOT = Regex("^//wsl(\\.localhost|\\$)/[^/]+", RegexOption.IGNORE_CASE)
        private const val ID_BYTES = 12
    }
}
