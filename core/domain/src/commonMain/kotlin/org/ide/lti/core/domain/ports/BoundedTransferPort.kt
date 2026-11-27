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

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.model.workspace.Workspace

const val MAX_TRANSFER_CHUNK_BYTES: Long = 8L * 1024 * 1024

sealed interface TransferSource {
    data class LocalFile(val absolutePath: String) : TransferSource
    data class RemoteUrl(val url: String) : TransferSource
    data class ExistingWorkspaceFile(val relPath: String) : TransferSource
}

enum class TransferState {
    SELECTED,
    VALIDATING,
    TRANSFERRING,
    VERIFYING,
    FINALIZED,
    FAILED,
    CANCELLED,
}

data class TransferSession(
    val id: String,
    val workspaceId: String,
    val capturedRevision: Int,
    val source: TransferSource,
    val stagingRelPath: String,
    val bytesTransferred: Long,
    val totalBytes: Long?,
    val state: TransferState,
    val error: String? = null,
    val cleanupEvidence: List<String> = emptyList(),
)

data class TransferReceipt(
    val sessionId: String,
    val finalRelPath: String,
    val sizeBytes: Long,
    val sha256: String,
    val verifiedAgainstExpected: Boolean,
)

sealed interface TransferProgress {
    data class Progress(val session: TransferSession) : TransferProgress
    data class Finalized(val receipt: TransferReceipt) : TransferProgress
    data class Failed(val session: TransferSession, val reason: String) : TransferProgress
    data class Cancelled(val session: TransferSession) : TransferProgress
}

/**
 * Result of probing an existing file in a workspace. Identity is bytes, not filename.
 */
data class ExistingFileProbe(
    val exists: Boolean,
    val isRegularFile: Boolean,
    val sizeBytes: Long?,
    val sha256: String?,
)

interface BoundedTransferPort {
    fun transfer(ws: Workspace, session: TransferSession, expectedSha256: String?): Flow<TransferProgress>
    suspend fun cancel(sessionId: String): Boolean
    suspend fun probeExisting(ws: Workspace, relPath: String): ExistingFileProbe
}
