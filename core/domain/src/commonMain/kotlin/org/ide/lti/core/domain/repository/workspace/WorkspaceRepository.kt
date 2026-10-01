/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.repository.workspace

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.model.setup.WorkspaceReadiness
import org.ide.lti.core.model.workspace.DecodeWarning
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession

/**
 * Repository for workspace data persistence.
 * Handles saving/loading workspaces, sessions, and recent projects.
 */
interface WorkspaceRepository {
    /**
     * Get all workspaces.
     */
    fun getWorkspaces(): Flow<List<Workspace>>

    /**
     * Get a workspace by ID.
     */
    suspend fun getWorkspace(id: String): Workspace?

    /**
     * Save or update a workspace.
     */
    suspend fun saveWorkspace(workspace: Workspace)

    /**
     * Delete a workspace.
     */
    suspend fun deleteWorkspace(id: String)

    /**
     * Get workspace session (from .idrworkspace file).
     */
    suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession?

    /**
     * Save workspace session (to .idrworkspace file).
     */
    suspend fun saveWorkspaceSession(session: WorkspaceSession)

    /**
     * Get recent projects.
     */
    fun getRecentProjects(): Flow<List<RecentProject>>

    /**
     * Add a project to recent list.
     */
    suspend fun addRecentProject(project: RecentProject)

    /**
     * Remove a project from recent list.
     */
    suspend fun removeRecentProject(workspaceId: String)

    /**
     * Clear all recent projects.
     */
    suspend fun clearRecentProjects()

    /**
     * Scans and discovers physical workspaces present in the workdir on disk,
     * updating persistent records and returning the discovered projects.
     */
    suspend fun discoverWorkspacesInWorkDir(basePath: String? = null): List<RecentProject>

    /**
     * Assesses the readiness of a workspace.
     */
    fun readinessOf(workspace: Workspace): WorkspaceReadiness =
        if (workspace.targetBinding == null) WorkspaceReadiness.NEEDS_TARGET else WorkspaceReadiness.READY

    /**
     * Observes decode warnings produced when workspace data cannot be decoded and is quarantined.
     */
    fun observeDecodeWarnings(): Flow<DecodeWarning> = kotlinx.coroutines.flow.emptyFlow()
}
