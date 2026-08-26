/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.repository.workspace

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession

/**
 * Default implementation of WorkspaceRepository.
 * TODO: Implement actual file system persistence using datastore module.
 */
class WorkspaceRepositoryImpl : WorkspaceRepository {

    private val workspaces = mutableMapOf<String, Workspace>()
    private val sessions = mutableMapOf<String, WorkspaceSession>()
    private val recentProjects = mutableListOf<RecentProject>()

    override fun getWorkspaces(): Flow<List<Workspace>> {
        return flowOf(workspaces.values.toList())
    }

    override suspend fun getWorkspace(id: String): Workspace? {
        return workspaces[id]
    }

    override suspend fun saveWorkspace(workspace: Workspace) {
        workspaces[workspace.id] = workspace
    }

    override suspend fun deleteWorkspace(id: String) {
        workspaces.remove(id)
        sessions.remove(id)
    }

    override suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession? {
        return sessions[workspaceId]
    }

    override suspend fun saveWorkspaceSession(session: WorkspaceSession) {
        sessions[session.workspaceId] = session
    }

    override fun getRecentProjects(): Flow<List<RecentProject>> {
        return flowOf(recentProjects.toList())
    }

    override suspend fun addRecentProject(project: RecentProject) {
        // Remove if already exists
        recentProjects.removeAll { it.workspaceId == project.workspaceId }
        // Add to front of list
        recentProjects.add(0, project)
        // Keep only last 10
        if (recentProjects.size > 10) {
            recentProjects.removeAt(recentProjects.size - 1)
        }
    }

    override suspend fun removeRecentProject(workspaceId: String) {
        recentProjects.removeAll { it.workspaceId == workspaceId }
    }

    override suspend fun clearRecentProjects() {
        recentProjects.clear()
    }
}
