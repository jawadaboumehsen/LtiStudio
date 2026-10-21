/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.repository.snapshot

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.ide.lti.core.datastore.SnapshotPreferencesDataSource
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.Workspace

/**
 * Clean Architecture adapter for [SnapshotRepository] backed by [SnapshotPreferencesDataSource].
 *
 * The app's copy is only a cache: a workspace's current configuration is the `config.json` its folder holds
 * (written on every save). When the app does not have it (a new install, or the app store lost it), it is read
 * back from the folder of the workspace that points at it.
 */
public class SnapshotRepositoryImpl(
    private val dataSource: SnapshotPreferencesDataSource,
    private val workspaces: WorkspaceRepository? = null,
    private val folders: PipelineExecutionPort? = null,
) : SnapshotRepository {
    override val snapshots: Flow<List<ConfigurationSnapshot>>
        get() = dataSource.snapshots

    override fun snapshotsForWorkspace(workspaceId: String): Flow<List<ConfigurationSnapshot>> {
        return dataSource.snapshotsForWorkspace(workspaceId)
    }

    override suspend fun getSnapshot(id: String): ConfigurationSnapshot? =
        dataSource.getSnapshot(id) ?: fromWorkspaceFolder(id)?.also { snapshot ->
            // Two readers may race to cache it; the store refuses the second copy, which is fine.
            runCatching { dataSource.saveSnapshot(snapshot) }
        }

    private suspend fun fromWorkspaceFolder(id: String): ConfigurationSnapshot? {
        val workspace = workspaces?.getWorkspaces()?.first()?.firstOrNull { it.effectiveSnapshotId == id }
        // Only the configuration the workspace points at; an older config.json is not that snapshot.
        return workspace?.let { readConfig(it) }?.takeIf { it.id == id }
    }

    private suspend fun readConfig(workspace: Workspace): ConfigurationSnapshot? = try {
        folders?.readFile(workspace, CONFIG_FILE)?.let {
            ConfigurationSnapshot.canonicalJson.decodeFromString<ConfigurationSnapshot>(it)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        co.touchlab.kermit.Logger.w(e) { "Could not read $CONFIG_FILE of ${workspace.name}" }
        null
    }

    override suspend fun saveSnapshot(snapshot: ConfigurationSnapshot) {
        dataSource.saveSnapshot(snapshot)
    }
}

private const val CONFIG_FILE = "config.json"
