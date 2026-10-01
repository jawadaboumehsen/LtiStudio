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

public const val MAX_TRANSFER_CHUNK_BYTES: Long = 8L * 1024 * 1024

public sealed interface TransferSource {
    public data class LocalFile(val absolutePath: String) : TransferSource
    public data class RemoteUrl(val url: String) : TransferSource
    public data class ExistingWorkspaceFile(val relPath: String) : TransferSource
}

public enum class TransferState {
    SELECTED,
    VALIDATING,
    TRANSFERRING,
    VERIFYING,
    FINALIZED,
    FAILED,
    CANCELLED,
}

public data class TransferSession(
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

public data class TransferReceipt(
    val sessionId: String,
    val finalRelPath: String,
    val sizeBytes: Long,
    val sha256: String,
    val verifiedAgainstExpected: Boolean,
)

public sealed interface TransferProgress {
    public data class Progress(val session: TransferSession) : TransferProgress
    public data class Finalized(val receipt: TransferReceipt) : TransferProgress
    public data class Failed(val session: TransferSession, val reason: String) : TransferProgress
    public data class Cancelled(val session: TransferSession) : TransferProgress
}

/**
 * Result of probing an existing file in a workspace. Identity is bytes, not filename.
 */
public data class ExistingFileProbe(
    val exists: Boolean,
    val isRegularFile: Boolean,
    val sizeBytes: Long?,
    val sha256: String?,
)

public interface BoundedTransferPort {
    public fun transfer(ws: Workspace, session: TransferSession, expectedSha256: String?): Flow<TransferProgress>
    public suspend fun cancel(sessionId: String): Boolean
    public suspend fun probeExisting(ws: Workspace, relPath: String): ExistingFileProbe
}
