/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.repository.setup

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.model.setup.AttemptStatus
import org.ide.lti.core.model.setup.ChildIntentRecord
import org.ide.lti.core.model.setup.ChildTerminalProof
import org.ide.lti.core.model.setup.PersistedSetupPlan
import org.ide.lti.core.model.setup.PersistedSubmoduleState
import org.ide.lti.core.model.setup.PersistedToolState
import org.ide.lti.core.model.setup.PersistedUserHandoff
import org.ide.lti.core.model.setup.SetupAttemptRecord
import org.ide.lti.core.model.setup.ToolchainPersistenceState

/**
 * Repository for persisted toolchain setup progress and the durable attempt journal.
 *
 * Enforces:
 * - Acknowledged durable journal writes: every mutating operation returns [Result], surfacing I/O failures
 *   to the caller instead of swallowing them.
 * - "Journal write failure prevents submission; an ambiguous receipt is reconciled or blocked,
 *   never blindly resubmitted."
 * - "Persisted lastSeq never advances beyond durably stored replay evidence."
 * - "Reset protection: reset cannot erase an active attempt without explicit authorization."
 */
public interface ToolchainSetupRepository : ToolchainSelectionRepository {
    public val toolchainState: Flow<ToolchainPersistenceState>

    public val currentState: ToolchainPersistenceState

    public suspend fun saveToolchainState(state: ToolchainPersistenceState): Result<Unit>

    public suspend fun updateTool(toolId: String, transform: (PersistedToolState) -> PersistedToolState): Result<Unit>

    public suspend fun updateSubmodule(
        name: String,
        transform: (PersistedSubmoduleState) -> PersistedSubmoduleState,
    ): Result<Unit>

    public suspend fun markStageCompleted(stageName: String, completed: Boolean): Result<Unit>

    public suspend fun markSetupCompleted(completed: Boolean, timestamp: Long? = null): Result<Unit>

    public suspend fun updateDiagnosticsSnapshot(passedIds: Set<String>): Result<Unit>

    public suspend fun updateAvbKeyState(provisioned: Boolean, keyPath: String?): Result<Unit>

    /**
     * Resets toolchain state. With [force] false an active, un-reconciled attempt is preserved and the
     * call fails; [force] true is the explicit, user-confirmed authorization to discard it.
     */
    public suspend fun clearToolchainState(force: Boolean = false): Result<Unit>

    // ---- Durable ledger and attempt operations (003 US3) ----

    /** Journals a newly confirmed attempt. Fails while another attempt is still active or recovery is blocked. */
    public suspend fun recordAttemptAuthorized(attempt: SetupAttemptRecord): Result<Unit>

    /** Moves the active attempt between AUTHORIZED / RUNNING / RECONNECTING without touching its children. */
    public suspend fun updateAttemptStatus(attemptId: String, status: AttemptStatus): Result<Unit>

    /**
     * Appends a child command intent (exact request, idempotency key, unique lock) to the active attempt
     * BEFORE the command is submitted, returning the assigned child index.
     * Enforces "Persist child intent before startRun".
     */
    public suspend fun appendChildIntent(attemptId: String, intent: ChildIntentRecord): Result<Int>

    /** Enforces "persist runId before acknowledging attachment". */
    public suspend fun recordChildRunId(attemptId: String, childIndex: Int, runId: String): Result<Unit>

    /** Atomically commits replay evidence together with the cursor it proves. */
    public suspend fun commitChildCursorAndEvidence(
        attemptId: String,
        childIndex: Int,
        lastSeq: Long,
        evidenceLine: String? = null,
    ): Result<Unit>

    public suspend fun recordChildTerminalProof(proof: ChildTerminalProof): Result<Unit>

    /**
     * Closes a journaled child that the server provably never accepted (no run under its unique lock inside
     * the deduplication window) with [terminalStatus]; nothing is resubmitted.
     */
    public suspend fun recordChildNotSubmitted(attemptId: String, childIndex: Int, terminalStatus: String): Result<Unit>

    /** Records that one of the attempt's planned actions completed. */
    public suspend fun recordPlanActionCompleted(attemptId: String, actionId: String): Result<Unit>

    public suspend fun recordAttemptTerminal(
        attemptId: String,
        status: AttemptStatus,
        outcome: SetupOutcome,
    ): Result<Unit>

    /** Records that the attempt is paused awaiting external user authorization. */
    public suspend fun recordAttemptAwaitingUserAction(
        attemptId: String,
        handoff: PersistedUserHandoff,
        plan: PersistedSetupPlan,
    ): Result<Unit>

    public suspend fun setRecoveryBlocked(blocked: Boolean, reason: String?): Result<Unit>
}
