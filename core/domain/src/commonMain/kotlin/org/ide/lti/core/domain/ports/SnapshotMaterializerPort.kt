/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.ports

data class SnapshotMaterializationIntent(val workspaceId: String, val snapshotId: String, val status: String)

interface SnapshotMaterializerPort {
    suspend fun markMaterializing(workspaceId: String, snapshotId: String)
    suspend fun clearIntent(workspaceId: String, snapshotId: String)
    suspend fun findIncompleteMaterializations(): List<SnapshotMaterializationIntent>
}
