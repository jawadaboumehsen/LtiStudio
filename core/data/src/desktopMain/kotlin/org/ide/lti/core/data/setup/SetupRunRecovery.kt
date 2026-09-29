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

import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.RunStatus
import io.ltirom.tooling.core.remote.RunStatusValue
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.model.setup.AttemptStatus
import org.ide.lti.core.model.setup.ChildIntentRecord
import org.ide.lti.core.model.setup.SetupAttemptRecord

/**
 * Reconciles the journaled active attempt against server truth after a restart or a dropped connection,
 * reusing the tool/core run protocol (`getRun` / `listRuns` / `attachRun`) through the existing transport.
 *
 * Per possibly-submitted child, in journal order:
 * - known runId -> `getRun`; a missing run fails closed; otherwise attach from the committed cursor + 1 and
 *   stream to an authoritative terminal state (heartbeats never advance the cursor);
 * - unknown receipt (runId never persisted) -> `listRuns(uniqueChildLock)`; exactly one candidate is adopted
 *   (its runId is persisted before attachment); zero candidates inside the server's 600,000 ms deduplication
 *   window mean the command was never accepted (the child is closed as NOT_SUBMITTED, nothing is resubmitted);
 *   zero candidates after the window, or several candidates, are ambiguous and fail closed.
 *
 * The attempt then ends as history: SUCCEEDED only when every child completed and every planned action was
 * recorded complete; FAILED / CANCELLED when a child ended that way; INTERRUPTED when the plan still had
 * unsubmitted work (a fresh preview and a new attempt are required - remaining commands are never resumed
 * blindly). Ambiguity sets the durable recovery block and returns [SetupOutcome.Interrupted]; it never
 * resubmits and never expands scope.
 *
 * Enforces: "Reconnect reuses attemptId; explicit retry allocates a new attemptId." and
 * "Journal write failure prevents submission; an ambiguous receipt is reconciled or blocked,
 * never blindly resubmitted."
 * (FR-007, FR-008, SC-003)
 */
public class SetupRunRecovery(
    private val transport: RemoteTransportPort,
    private val repository: ToolchainSetupRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val activity: SetupActivitySink? = null,
) {
    public companion object {
        /** Server-side idempotency deduplication window (research.md). */
        public const val DEDUPLICATION_WINDOW_MS: Long = 600_000L

        public const val NOT_SUBMITTED_STATUS: String = "NOT_SUBMITTED"
    }

    private sealed interface ChildReconciliation {
        data class Terminal(val status: String) : ChildReconciliation
        data class Blocked(val reason: String) : ChildReconciliation
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod") // one guard per fail-closed precondition, in journal order
    public suspend fun recover(
        environmentKey: String,
        attemptId: String? = null,
        log: (String) -> Unit = {},
    ): SetupOutcome {
        val state = repository.currentState
        val active = state.activeAttempt
        if (active == null) {
            return if (state.recoveryBlocked) {
                SetupOutcome.Interrupted(state.recoveryBlockReason ?: "Recovery is blocked for '$environmentKey'.")
            } else {
                SetupOutcome.Failed(stage = null, reason = "No attempt to recover.")
            }
        }
        if (attemptId != null && attemptId != active.attemptId) {
            return SetupOutcome.Failed(
                stage = null,
                reason = "Attempt '$attemptId' is not the active attempt ('${active.attemptId}'); " +
                    "history is never re-run.",
            )
        }
        if (active.environmentKey != environmentKey) {
            val reason = "Attempt '${active.attemptId}' belongs to environment " +
                "'${active.environmentKey}', not '$environmentKey'."
            return block(active, reason)
        }

        val handoff = active.awaitingHandoff
        if (handoff != null &&
            (
                active.status == AttemptStatus.AWAITING_USER_ACTION ||
                    (active.status == AttemptStatus.RUNNING && active.orderedIntents.isEmpty())
                )
        ) {
            if (active.status != AttemptStatus.AWAITING_USER_ACTION) {
                repository.updateAttemptStatus(active.attemptId, AttemptStatus.AWAITING_USER_ACTION)
            }
            val domainHandoff = org.ide.lti.core.domain.setup.UserRepairHandoff(
                actionId = handoff.actionId,
                description = "External terminal authorization required to install packages",
                terminalCommand = handoff.command,
                packages = handoff.packages,
                distro = handoff.distro,
            )
            val recoveryStage = if (active.planKind == "BOOTSTRAP_PACKAGES" ||
                active.bootstrapRequirementIds.isNotEmpty()
            ) {
                org.ide.lti.core.domain.setup.SetupStepStage.SYSTEM_PACKAGES
            } else {
                org.ide.lti.core.domain.setup.SetupStepStage.SYSTEM_DIAGNOSTICS
            }
            log("[Recovery] Restoring active attempt ${active.attemptId} in AWAITING_USER_ACTION state.")
            return SetupOutcome.AwaitingUserAction(
                pendingPlanId = active.planId,
                stage = recoveryStage,
                reason = "Awaiting user terminal authorization to install packages: ${handoff.packages.joinToString()}",
                handoff = domainHandoff,
            )
        }

        repository.updateAttemptStatus(active.attemptId, AttemptStatus.RECONNECTING).onFailure {
            return SetupOutcome.Interrupted("Journal write failed while marking reconnection: ${it.message}")
        }
        log("[Recovery] Reconnecting attempt ${active.attemptId} (${active.orderedIntents.size} journaled commands).")

        val launcher = JournaledRunLauncher(
            transport = transport,
            repository = repository,
            context = SetupJournalContext(environmentKey, active.attemptId, activity),
        )
        var worstStatus: String? = null
        for (intent in active.orderedIntents) {
            val current = repository.currentState.activeAttempt ?: break
            val reconciled = when (val known = current.orderedIntents.getOrNull(intent.childIndex)?.terminalStatus) {
                null -> reconcileChild(current, intent, launcher, log)
                else -> ChildReconciliation.Terminal(known)
            }
            when (reconciled) {
                is ChildReconciliation.Blocked -> return block(current, reconciled.reason)
                is ChildReconciliation.Terminal -> {
                    if (reconciled.status != RunStatusValue.COMPLETED.name && worstStatus == null) {
                        worstStatus = reconciled.status
                    }
                }
            }
        }

        val reconciledAttempt = repository.currentState.activeAttempt ?: return SetupOutcome.Interrupted(
            "Attempt '${active.attemptId}' disappeared from the journal during reconciliation.",
        )
        return finish(reconciledAttempt, worstStatus, log)
    }

    @Suppress("ReturnCount") // known-run and lost-receipt branches each end in their own verdict
    private suspend fun reconcileChild(
        attempt: SetupAttemptRecord,
        intent: ChildIntentRecord,
        launcher: JournaledRunLauncher,
        log: (String) -> Unit,
    ): ChildReconciliation {
        val runId = intent.runId
        if (runId != null) {
            transport.getRun(runId)
                ?: return ChildReconciliation.Blocked("Server run '$runId' for '${intent.actionId}' no longer exists.")
            return attach(intent, runId, launcher, log)
        }
        val candidates = transport.listRuns(intent.workspaceLock)
        return when (candidates.size) {
            0 -> reconcileNeverAccepted(attempt, intent)
            1 -> adoptCandidate(attempt, intent, candidates.single(), launcher, log)
            else -> ChildReconciliation.Blocked(
                "${candidates.size} server runs hold lock '${intent.workspaceLock}' for " +
                    "'${intent.actionId}'; identity is ambiguous.",
            )
        }
    }

    private suspend fun reconcileNeverAccepted(
        attempt: SetupAttemptRecord,
        intent: ChildIntentRecord,
    ): ChildReconciliation {
        val elapsedMs = clock() - intent.submissionTimestampEpochMs
        if (elapsedMs > DEDUPLICATION_WINDOW_MS) {
            return ChildReconciliation.Blocked(
                "No server run holds lock '${intent.workspaceLock}' for '${intent.actionId}' and the " +
                    "$DEDUPLICATION_WINDOW_MS ms deduplication window has expired; the receipt cannot be reconciled.",
            )
        }
        // Inside the window the server would still remember an accepted submission: none exists, so the
        // command was never accepted. It is closed without resubmission; a fresh attempt must preview again.
        return repository.recordChildNotSubmitted(attempt.attemptId, intent.childIndex, NOT_SUBMITTED_STATUS).fold(
            onSuccess = { ChildReconciliation.Terminal(NOT_SUBMITTED_STATUS) },
            onFailure = {
                ChildReconciliation.Blocked("Journal write failed while closing an unsubmitted child: ${it.message}")
            },
        )
    }

    private suspend fun adoptCandidate(
        attempt: SetupAttemptRecord,
        intent: ChildIntentRecord,
        candidate: RunStatus,
        launcher: JournaledRunLauncher,
        log: (String) -> Unit,
    ): ChildReconciliation {
        // "Persist runId before acknowledging attachment."
        repository.recordChildRunId(attempt.attemptId, intent.childIndex, candidate.runId).onFailure {
            return ChildReconciliation.Blocked(
                "Journal write failed while adopting run '${candidate.runId}': ${it.message}",
            )
        }
        log("[Recovery] Reconciled lost receipt for '${intent.actionId}' to run ${candidate.runId}.")
        return attach(intent, candidate.runId, launcher, log)
    }

    private suspend fun attach(
        intent: ChildIntentRecord,
        runId: String,
        launcher: JournaledRunLauncher,
        log: (String) -> Unit,
    ): ChildReconciliation {
        log("[Recovery] Attaching to run $runId for '${intent.actionId}' from seq ${intent.lastSeq + 1}.")
        return when (
            val result = launcher.attachAndCommit(
                childIndex = intent.childIndex,
                runId = runId,
                fromSeq = intent.lastSeq + 1L,
                actionId = intent.actionId,
                log = log,
            )
        ) {
            is JournaledRunResult.Finished -> ChildReconciliation.Terminal(result.status.name)
            is JournaledRunResult.JournalRejected -> ChildReconciliation.Blocked(result.reason)
            is JournaledRunResult.Interrupted -> ChildReconciliation.Blocked(result.reason)
        }
    }

    private suspend fun finish(attempt: SetupAttemptRecord, worstStatus: String?, log: (String) -> Unit): SetupOutcome {
        val remaining = attempt.plannedActionIds.filterNot { it in attempt.completedActionIds }
        val outcome: SetupOutcome
        val status: AttemptStatus
        when {
            worstStatus == RunStatusValue.CANCELLED.name -> {
                outcome = SetupOutcome.Cancelled
                status = AttemptStatus.CANCELLED
            }
            worstStatus == RunStatusValue.FAILED.name || worstStatus == RunStatusValue.INTERRUPTED.name -> {
                outcome = SetupOutcome.Failed(
                    stage = null,
                    reason = "A journaled command ended with status $worstStatus.",
                )
                status = AttemptStatus.FAILED
            }
            worstStatus == NOT_SUBMITTED_STATUS || remaining.isNotEmpty() -> {
                // Completion is journaled after an action ends, so "not completed" also covers an action that
                // started and was cut off (e.g. the app closed mid-way); it must not claim it "never ran".
                outcome = SetupOutcome.Interrupted(
                    "Attempt ${attempt.attemptId} was interrupted: ${remaining.size} planned action(s) " +
                        "(${remaining.joinToString()}) did not finish; preview again to start a new attempt.",
                )
                status = AttemptStatus.INTERRUPTED
            }
            else -> {
                outcome = SetupOutcome.Succeeded()
                status = AttemptStatus.SUCCEEDED
            }
        }
        repository.recordAttemptTerminal(attempt.attemptId, status, outcome).onFailure {
            return SetupOutcome.Interrupted("Journal write failed while archiving attempt: ${it.message}")
        }
        log("[Recovery] Attempt ${attempt.attemptId} archived as $status.")
        return outcome
    }

    private suspend fun block(attempt: SetupAttemptRecord, reason: String): SetupOutcome {
        repository.setRecoveryBlocked(true, reason)
        return SetupOutcome.Interrupted("Recovery of attempt ${attempt.attemptId} is blocked: $reason")
    }
}
