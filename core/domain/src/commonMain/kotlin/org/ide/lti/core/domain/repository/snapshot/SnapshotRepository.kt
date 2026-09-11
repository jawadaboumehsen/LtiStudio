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
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

/**
 * Domain port for querying and saving immutable configuration snapshots.
 */
interface SnapshotRepository {
    val snapshots: Flow<List<ConfigurationSnapshot>>
    fun snapshotsForWorkspace(workspaceId: String): Flow<List<ConfigurationSnapshot>>
    suspend fun getSnapshot(id: String): ConfigurationSnapshot?
    suspend fun saveSnapshot(snapshot: ConfigurationSnapshot)
}
