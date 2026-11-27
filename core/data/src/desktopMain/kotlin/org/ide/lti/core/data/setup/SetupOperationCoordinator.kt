/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.model.setup.AttemptStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * Application-lifetime coordinator enforcing single-owner environment operations and honest typed outcomes.
 *
 * Enforces:
 * - "One environment-level atomic operation owner covers setup, retry, remediation, repair, tool test and cache reset;
 *    concurrent requests return Busy rather than queue surprise work." (FR-005)
 * - "Lock admission atomically, release lock before IO; concurrent mutation/test/reset returns Busy with owner."
 * - "Cleanup occurs on every terminal path." (FR-005, FR-006)
 * - "CancellationException is rethrown after cleanup; durable detach and explicit cancel are distinct." (FR-006)
 * - "Fresh retry requires persisted authoritative terminal proof for every possibly submitted child;
 *    cancellation acknowledgement alone never unlocks mutation." (US3)
 */
class SetupOperationCoordinator(
    private val repository: ToolchainSetupRepository? = null,
) {
    companion object {
        /** Operation kinds admitted while the journal still holds an unreconciled attempt or a recovery block. */
        const val KIND_CHECK: String = "CHECK"
        const val KIND_RECOVERY: String = "RECOVERY"
        const val KIND_CACHE_RESET: String = "CACHE_RESET"
        const val KIND_RESUME: String = "RESUME"
    }

    data class OperationOwner(
        val operationId: String,
        val kind: String,
        val environmentKey: String,
        val startedAtEpochMs: Long = System.currentTimeMillis(),
        val job: Job? = null,
    )

    private val activeOwners = ConcurrentHashMap<String, OperationOwner>()
    private val admissionLock = Any()

    fun isBusy(environmentKey: String): Boolean = activeOwners.containsKey(environmentKey)

    fun currentOwner(environmentKey: String): OperationOwner? = activeOwners[environmentKey]

    /**
     * Attempts atomic admission for an operation on [environmentKey].
     * Admission decision is held only momentarily; lock is released before invoking [block].
     */
    @Suppress("ReturnCount") // admission has three distinct early exits: journal gate, busy owner, outcome
    suspend fun withAdmission(
        environmentKey: String,
        operationId: String,
        kind: String,
        block: suspend () -> SetupOutcome,
    ): SetupOutcome {
        journalGate(environmentKey, kind)?.let { return it }

        val job = currentCoroutineContext()[Job]
        val existing = synchronized(admissionLock) {
            val current = activeOwners[environmentKey]
            if (current != null) {
                return@synchronized current
            }
            activeOwners[environmentKey] = OperationOwner(operationId, kind, environmentKey, job = job)
            null
        }

        if (existing != null) {
            return SetupOutcome.Busy(ownerId = existing.operationId)
        }

        return try {
            block()
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            SetupOutcome.Failed(
                stage = null,
                reason = t.message ?: "Operation failed with unexpected exception",
            )
        } finally {
            synchronized(admissionLock) {
                activeOwners.remove(environmentKey)
            }
        }
    }

    /**
     * Journal gate (US3): a mutation is admitted only when the journal holds no unreconciled attempt and no
     * recovery block. Checks, recovery itself and the explicitly confirmed cache reset (the user-directed
     * escape from an unreconcilable journal) pass through. Reads only in-memory journal state - no IO.
     * Enforces "Fresh retry requires persisted authoritative terminal proof for every possibly submitted child".
     */
    @Suppress("ReturnCount") // each early return is one distinct gate verdict
    private fun journalGate(environmentKey: String, kind: String): SetupOutcome? {
        val repo = repository ?: return null
        if (kind == KIND_CHECK || kind == KIND_RECOVERY || kind == KIND_CACHE_RESET) return null
        val state = repo.currentState
        if (state.recoveryBlocked) {
            return SetupOutcome.Interrupted(
                state.recoveryBlockReason ?: "Environment '$environmentKey' is recovery-blocked.",
            )
        }
        val active = state.activeAttempt ?: return null
        val unproven = active.unprovenIntents
        if (kind == KIND_RESUME && active.status == AttemptStatus.AWAITING_USER_ACTION && unproven.isEmpty()) {
            return null
        }
        return when {
            unproven.isNotEmpty() -> SetupOutcome.Interrupted(
                "Unresolved prior execution: '${unproven.first().actionId}' of attempt ${active.attemptId} " +
                    "lacks authoritative terminal proof; reconnect before starting new work.",
            )
            active.isActive -> SetupOutcome.Interrupted(
                "Attempt ${active.attemptId} is still journaled as ${active.status}; " +
                    "reconnect before starting new work.",
            )
            else -> null
        }
    }

    fun cancelActiveOperation(environmentKey: String): SetupOutcome {
        val owner = synchronized(admissionLock) {
            activeOwners[environmentKey]
        } ?: return SetupOutcome.Failed(stage = null, reason = "No active operation to cancel.")

        owner.job?.cancel(CancellationException("Operation explicitly cancelled"))
        return SetupOutcome.Cancelled
    }
}
