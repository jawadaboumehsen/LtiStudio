/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.ports

import org.ide.lti.core.model.draft.RecoveryDraftReadResult
import org.ide.lti.core.model.draft.RecoveryDraftRecord

public interface RecoveryDraftStorePort {
    suspend fun persist(record: RecoveryDraftRecord): Result<RecoveryDraftRecord>
    suspend fun get(
        workspaceId: String,
        expectedBaseSnapshotId: String? = null,
        expectedBaseSnapshotDigest: String? = null,
    ): RecoveryDraftReadResult?
    suspend fun advanceDiscardGeneration(workspaceId: String, generation: Long): Result<Unit>
    suspend fun delete(workspaceId: String): Result<Unit>
    suspend fun retireIfRevision(workspaceId: String, savedRevision: Long): Result<Boolean>
}
