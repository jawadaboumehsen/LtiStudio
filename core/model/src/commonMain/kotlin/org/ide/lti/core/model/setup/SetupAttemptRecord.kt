/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.setup

import kotlinx.serialization.Serializable

/**
 * Lifecycle status of an environment setup attempt.
 */
@Serializable
public enum class AttemptStatus {
    AUTHORIZED,
    RUNNING,
    RECONNECTING,
    AWAITING_USER_ACTION,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    INTERRUPTED,
    ABANDONED,
}

/**
 * Exact immutable execution request persisted before submission to the daemon runner.
 */
@Serializable
public data class PersistedExecutionRequest(
    val toolId: String,
    val arguments: List<String> = emptyList(),
    val workingDirectory: String = "/",
    val environment: Map<String, String> = emptyMap(),
    val timeoutMs: Long = 60_000L,
)

/**
 * Journaled child command intent within an attempt. One record per server command; it is appended
 * to the journal immediately before `startRun`, so a persisted intent is always "possibly submitted".
 *
 * Enforces:
 * - "Journal write failure prevents submission; an ambiguous receipt is reconciled or blocked,
 *   never blindly resubmitted."
 * - "Persist child intent before startRun; persist runId before acknowledging attachment."
 * - "Persisted lastSeq never advances beyond durably stored replay evidence."
 */
@Serializable
public data class ChildIntentRecord(
    val childIndex: Int,
    val stage: String,
    val actionId: String,
    val recipeRevision: String? = null,
    val request: PersistedExecutionRequest,
    val idempotencyKey: String,
    val workspaceLock: String,
    val submissionTimestampEpochMs: Long = 0L,
    val runId: String? = null,
    val lastSeq: Long = 0L,
    val terminalStatus: String? = null,
    val replayLogs: List<String> = emptyList(),
)

/**
 * Persisted proof of an authoritative terminal state on the server.
 * Enforces: "Fresh retry requires persisted authoritative terminal proof for every possibly submitted child;
 * cancellation acknowledgement alone never unlocks mutation."
 */
@Serializable
public data class ChildTerminalProof(
    val environmentKey: String,
    val attemptId: String,
    val runId: String,
    val authoritativeTerminalStatus: String,
    val observedAtEpochMs: Long,
)

/**
 * Aggregate attempt record journaled in [ToolchainPersistenceState].
 *
 * [plannedActionIds] are the confirmed plan's ordered actions; [completedActionIds] records which of
 * them finished. A reconciled attempt whose planned actions are not all complete cannot be resumed
 * blindly and is archived as interrupted: a fresh preview and a new attempt are required.
 *
 * Enforces: "Reconnect reuses attemptId; explicit retry allocates a new attemptId."
 */
@Serializable
public data class SetupAttemptRecord(
    val attemptId: String,
    val environmentKey: String,
    val planId: String,
    val planRevisionHash: String,
    val planKind: String,
    val status: AttemptStatus = AttemptStatus.AUTHORIZED,
    val plannedActionIds: List<String> = emptyList(),
    val completedActionIds: List<String> = emptyList(),
    val orderedIntents: List<ChildIntentRecord> = emptyList(),
    val activeChildIndex: Int? = null,
    val terminalOutcome: String? = null,
    val terminalReason: String? = null,
    val recoveryBlocked: Boolean = false,
    val terminalProofs: List<ChildTerminalProof> = emptyList(),
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
    val persistedPlan: PersistedSetupPlan? = null,
    val awaitingHandoff: PersistedUserHandoff? = null,
    val environment: PersistedEnvironment? = null,
    val bootstrapRequirementIds: List<String> = emptyList(),
) {
    /** Attempt statuses that still own the environment and block a second attempt. */
    val isActive: Boolean
        get() = status == AttemptStatus.AUTHORIZED ||
            status == AttemptStatus.RUNNING ||
            status == AttemptStatus.RECONNECTING ||
            status == AttemptStatus.AWAITING_USER_ACTION

    /** A child is proven when its own terminal status or a persisted proof for its runId exists. */
    public fun isChildProven(intent: ChildIntentRecord): Boolean =
        intent.terminalStatus != null || terminalProofs.any { it.runId == intent.runId }

    /** Possibly submitted children (every persisted intent) lacking authoritative terminal proof. */
    val unprovenIntents: List<ChildIntentRecord>
        get() = orderedIntents.filterNot { isChildProven(it) }
}

/**
 * Persisted handoff details for an action awaiting user terminal authorization.
 */
@Serializable
public data class PersistedUserHandoff(
    val actionId: String,
    val distro: String,
    val command: String,
    val packages: List<String> = emptyList(),
    val timestampEpochMs: Long = 0L,
)

/**
 * Persisted plan action representation for durable storage across restarts.
 */
@Serializable
public data class PersistedPlanAction(val actionId: String, val type: String, val payload: List<String> = emptyList())

/**
 * Persisted setup plan enabling durable recovery and continuation across sessions.
 */
@Serializable
public data class PersistedSetupPlan(
    val planId: String,
    val revisionHash: String,
    val environmentKey: String,
    val kind: String,
    val targetStageOrToolId: String? = null,
    val autoDoctorEnabled: Boolean = true,
    val actions: List<PersistedPlanAction> = emptyList(),
)

/**
 * Persisted snapshot of the target WSL environment.
 */
@Serializable
public data class PersistedEnvironment(
    val distro: String,
    val wslVersion: Int,
    val osId: String,
    val osVersionId: String,
    val user: String,
    val home: String,
)

public fun SetupEnvironment.toPersisted(): PersistedEnvironment = PersistedEnvironment(
    distro = distro,
    wslVersion = wslVersion,
    osId = osId,
    osVersionId = osVersionId,
    user = user,
    home = home,
)

public fun PersistedEnvironment.toDomain(): SetupEnvironment = SetupEnvironment(
    distro = distro,
    wslVersion = wslVersion,
    osId = osId,
    osVersionId = osVersionId,
    user = user,
    home = home,
)
