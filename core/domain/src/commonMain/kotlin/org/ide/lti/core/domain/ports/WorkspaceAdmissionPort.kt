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

import kotlinx.serialization.Serializable

@Serializable
public enum class MutationKind {
    SAVE,
    MATERIALIZE,
    IMPORT,
    START,
    RESUME,
    PUBLISH,
}

@Serializable
public enum class LeaseReconciliation {
    RELEASED_BY_OPERATOR,
    CONFIRMED_COMPLETE,
    CONFIRMED_ABANDONED,
}

@Serializable
public data class WorkspaceWriteLease(
    val workspaceId: String,
    val ownerKind: MutationKind,
    val operationId: String,
    val expectedRevision: Int?,
    val acquiredAtEpochMs: Long,
    val durable: Boolean,
)

public sealed interface AdmissionResult {
    public data class Admitted(val lease: WorkspaceWriteLease) : AdmissionResult
    public data class Busy(val owner: WorkspaceWriteLease) : AdmissionResult
    public data class RevisionMismatch(val expected: Int, val actual: Int) : AdmissionResult
}

/**
 * Port for managing workspace write admission leases.
 *
 * Rules:
 * "Busy reports owner, never silently queues a future mutation." Pure drafts/presentation never acquire a lease.
 */
public interface WorkspaceAdmissionPort {
    public suspend fun tryAcquire(
        workspaceId: String,
        kind: MutationKind,
        operationId: String,
        expectedRevision: Int?,
        currentRevision: Int?,
    ): AdmissionResult

    public suspend fun release(lease: WorkspaceWriteLease)

    public suspend fun currentOwner(workspaceId: String): WorkspaceWriteLease?

    /** Durable leases found after restart; never silently dropped. */
    public suspend fun unreconciledLeases(): List<WorkspaceWriteLease>

    public suspend fun reconcile(lease: WorkspaceWriteLease, outcome: LeaseReconciliation)
}
