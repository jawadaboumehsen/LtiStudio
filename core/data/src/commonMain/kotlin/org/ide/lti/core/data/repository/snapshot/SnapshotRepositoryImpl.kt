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

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.datastore.SnapshotPreferencesDataSource
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

/**
 * Clean Architecture adapter for [SnapshotRepository] backed by [SnapshotPreferencesDataSource].
 */
public class SnapshotRepositoryImpl(
    private val dataSource: SnapshotPreferencesDataSource,
) : SnapshotRepository {
    override val snapshots: Flow<List<ConfigurationSnapshot>>
        get() = dataSource.snapshots

    override fun snapshotsForWorkspace(workspaceId: String): Flow<List<ConfigurationSnapshot>> {
        return dataSource.snapshotsForWorkspace(workspaceId)
    }

    override suspend fun getSnapshot(id: String): ConfigurationSnapshot? {
        return dataSource.getSnapshot(id)
    }

    override suspend fun saveSnapshot(snapshot: ConfigurationSnapshot) {
        dataSource.saveSnapshot(snapshot)
    }
}
