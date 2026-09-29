/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.repository.workspace

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.ide.lti.core.common.notification.NotificationLevel
import org.ide.lti.core.common.notification.SystemNotification
import org.ide.lti.core.common.notification.SystemNotifier
import org.ide.lti.core.datastore.WorkspacePreferencesDataSource
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.setup.WorkspaceReadiness
import org.ide.lti.core.model.workspace.DecodeWarning
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession

/**
 * Clean Architecture implementation of [WorkspaceRepository].
 *
 * Persists workspaces and recent projects to [WorkspacePreferencesDataSource],
 * dynamically discovers physical workspaces on disk using [WorkDirDiscoveryService],
 * and manages workspace sessions.
 */
public class WorkspaceRepositoryImpl(
    private val preferencesDataSource: WorkspacePreferencesDataSource,
    private val discoveryService: WorkDirDiscoveryService = WorkDirDiscoveryService(),
    private val notifier: SystemNotifier? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WorkspaceRepository {

    private val sessions = mutableMapOf<String, WorkspaceSession>()
    private val sessionMutex = Mutex()

    override fun getWorkspaces(): Flow<List<Workspace>> {
        return preferencesDataSource.workspaces
    }

    override suspend fun getWorkspace(id: String): Workspace? {
        return preferencesDataSource.getWorkspace(id)
    }

    override suspend fun saveWorkspace(workspace: Workspace) {
        preferencesDataSource.saveWorkspace(workspace)
    }

    override suspend fun deleteWorkspace(id: String) {
        preferencesDataSource.deleteWorkspace(id)
        sessionMutex.withLock {
            sessions.remove(id)
        }
    }

    override suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession? {
        return sessionMutex.withLock {
            sessions[workspaceId]
        }
    }

    override suspend fun saveWorkspaceSession(session: WorkspaceSession) {
        sessionMutex.withLock {
            sessions[session.workspaceId] = session
        }
    }

    override fun getRecentProjects(): Flow<List<RecentProject>> {
        return preferencesDataSource.recentProjects
    }

    override suspend fun addRecentProject(project: RecentProject) {
        preferencesDataSource.addRecentProject(project)
    }

    override suspend fun removeRecentProject(workspaceId: String) {
        preferencesDataSource.removeRecentProject(workspaceId)
    }

    override suspend fun clearRecentProjects() {
        preferencesDataSource.clearRecentProjects()
    }

    override suspend fun discoverWorkspacesInWorkDir(
        basePath: String?,
    ): List<RecentProject> = withContext(ioDispatcher) {
        val discovered = discoveryService.discoverWorkspaces(basePath)
        val currentRecents = preferencesDataSource.currentRecentProjects.associateBy { it.workspaceId }

        // Merge: keep user's existing lastOpened if already in recents, else use discovered
        val merged = discovered.map { disc ->
            currentRecents[disc.workspaceId]?.copy(name = disc.name, path = disc.path, type = disc.type) ?: disc
        }

        // Include any user custom workspaces that aren't in discovered
        val discoveredIds = discovered.map { it.workspaceId }.toSet()
        val customRecents = preferencesDataSource.currentRecentProjects.filterNot { it.workspaceId in discoveredIds }
        val allRecents = merged + customRecents

        preferencesDataSource.saveRecentProjects(allRecents)
        allRecents
    }

    override fun readinessOf(workspace: Workspace): WorkspaceReadiness {
        return if (workspace.targetBinding == null) {
            WorkspaceReadiness.NEEDS_TARGET
        } else {
            WorkspaceReadiness.READY
        }
    }

    override fun observeDecodeWarnings(): Flow<DecodeWarning> = preferencesDataSource.decodeWarnings.onEach { warning ->
        notifier?.notify(
            SystemNotification(
                title = "Data Store Warning",
                message = warning.message,
                level = NotificationLevel.Warning,
            ),
        )
    }
}
