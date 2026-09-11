/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.repository.snapshot

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

/**
 * In-memory test double implementation of [SnapshotRepository].
 */
class InMemorySnapshotRepository : SnapshotRepository {
    private val _snapshots = MutableStateFlow<List<ConfigurationSnapshot>>(emptyList())
    override val snapshots: Flow<List<ConfigurationSnapshot>> = _snapshots.asStateFlow()

    override fun snapshotsForWorkspace(workspaceId: String): Flow<List<ConfigurationSnapshot>> {
        return _snapshots.map { list -> list.filter { it.workspaceId == workspaceId } }
    }

    override suspend fun getSnapshot(id: String): ConfigurationSnapshot? {
        return _snapshots.value.firstOrNull { it.id == id }
    }

    override suspend fun saveSnapshot(snapshot: ConfigurationSnapshot) {
        val current = _snapshots.value
        require(current.none { it.id == snapshot.id }) {
            "ConfigurationSnapshot with id '${snapshot.id}' already exists and is immutable"
        }
        _snapshots.value = current + snapshot
    }

    fun clear() {
        _snapshots.value = emptyList()
    }
}
