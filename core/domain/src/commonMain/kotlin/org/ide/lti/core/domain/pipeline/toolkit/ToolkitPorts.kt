/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.toolkit

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.ide.lti.core.model.workspace.Workspace

/**
 * Port for managing destructive-tool backup records and index under `restore/` in a workspace (FR-038).
 */
public interface RestorePointStore {
    public suspend fun readIndex(workspace: Workspace): RestoreIndex
    public suspend fun writeIndex(workspace: Workspace, index: RestoreIndex)
    public suspend fun addRestorePoint(workspace: Workspace, point: RestorePoint)
    public suspend fun updateRestorePointState(workspace: Workspace, id: String, state: RestorePointState)
}

/**
 * Port for reading and persisting user acknowledgements per workspace (FR-035).
 */
public interface AckStore {
    public suspend fun readAcknowledgements(workspace: Workspace): Acknowledgements
    public suspend fun acknowledge(workspace: Workspace, featureId: String, acceptedAt: String): Acknowledgements
    public suspend fun isAcknowledged(workspace: Workspace, featureId: String): Boolean
}

/**
 * Result of attempting to acquire a workspace editing job lock.
 */
public sealed interface LockResult {
    public data class Acquired(val handle: JobLockHandle) : LockResult
    public data class Locked(val holderName: String) : LockResult
}

/**
 * An active lock handle on a workspace.
 */
public interface JobLockHandle : AutoCloseable {
    public val holderName: String
    public val workspace: Workspace
    public suspend fun release()
}

/**
 * Mutual exclusion for editing operations on a workspace (FR-039).
 * Only one editing job per workspace may run at a time; blocked attempts name the current holder.
 */
public interface JobLock {
    public suspend fun acquire(workspace: Workspace, holderName: String): LockResult
    public suspend fun currentHolder(workspace: Workspace): String?
    public suspend fun release(workspace: Workspace, holderName: String? = null)
}

/**
 * Execute [block] holding the workspace [JobLock]. If locked, invokes [onLocked] with the blocking holder name.
 * If lock is acquired, executes [block] and guarantees lock release on completion, cancellation, or failure.
 */
public suspend fun <T> JobLock.withLock(
    workspace: Workspace,
    holderName: String,
    onLocked: (holderName: String) -> T,
    block: suspend () -> T,
): T {
    val lockResult = acquire(workspace, holderName)
    return when (lockResult) {
        is LockResult.Locked -> onLocked(lockResult.holderName)

        is LockResult.Acquired -> {
            try {
                block()
            } finally {
                withContext(NonCancellable) { lockResult.handle.release() }
            }
        }
    }
}
