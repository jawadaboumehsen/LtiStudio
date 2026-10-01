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
import io.ltirom.tooling.core.remote.RunStatusValue
import io.ltirom.tooling.core.remote.StartRunRequest
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.CancellationException
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.model.setup.ChildIntentRecord
import org.ide.lti.core.model.setup.ChildTerminalProof
import org.ide.lti.core.model.setup.PersistedExecutionRequest

/**
 * Journal identity of the attempt on whose behalf server commands are submitted.
 */
public data class SetupJournalContext(
    val environmentKey: String,
    val attemptId: String,
    /**
     * Optional bounded activity sink. When present, streamed output is recorded with its real
     * journal identity (attemptId, childRunId, sequence) so replay/reconnect deduplicates instead
     * of re-appending the same lines under a synthetic counter (FR-011, FR-012).
     */
    val activity: SetupActivitySink? = null,
)

/**
 * Typed result of one journaled server command.
 */
public sealed interface JournaledRunResult {
    /**
     * The run reached an authoritative terminal state; [exitCode] is the server-reported code and [errorTail]
     * the last lines it wrote to stderr (why it failed, e.g. CMake's "Could NOT find BZip2").
     */
    public data class Finished(
        val runId: String,
        val status: RunStatusValue,
        val exitCode: Int,
        val errorTail: String = "",
    ) : JournaledRunResult

    /** The journal could not be written before submission: nothing was submitted. */
    public data class JournalRejected(val reason: String) : JournaledRunResult

    /**
     * The command may have been submitted but its identity or evidence could not be durably recorded, or the
     * stream ended without an authoritative terminal state. Recovery is blocked until reconciled.
     */
    public data class Interrupted(val reason: String) : JournaledRunResult
}

/**
 * Submits server commands under journal-backed identity, in this exact order:
 * 1. append the child intent (exact request, attempt-scoped idempotency key, unique child lock) — a write
 *    failure means nothing is submitted ("Journal write failure prevents submission");
 * 2. `startRun`;
 * 3. persist the runId ("persist runId before acknowledging attachment") — a write failure blocks recovery
 *    instead of continuing on an unrecorded run;
 * 4. attach from the committed cursor + 1, committing every output line together with its sequence
 *    ("Persisted lastSeq never advances beyond durably stored replay evidence"); heartbeats never advance it;
 * 5. record the authoritative terminal proof.
 *
 * Idempotency keys and locks are derived from environment/attempt/child action identity, never static.
 */
public class JournaledRunLauncher(
    private val transport: RemoteTransportPort,
    private val repository: ToolchainSetupRepository,
    private val context: SetupJournalContext,
) {
    public companion object {
        public val TERMINAL_STATUSES: Set<RunStatusValue> = setOf(
            RunStatusValue.COMPLETED,
            RunStatusValue.FAILED,
            RunStatusValue.CANCELLED,
            RunStatusValue.INTERRUPTED,
        )

        private const val ERROR_TAIL_LINES = 8

        public fun idempotencyKey(context: SetupJournalContext, actionId: String): String =
            "${context.environmentKey}:${context.attemptId}:$actionId"

        public fun workspaceLock(context: SetupJournalContext, actionId: String): String =
            "lock:${context.environmentKey}:${context.attemptId}:$actionId"
    }

    @Suppress("ReturnCount")
    public suspend fun run(
        stage: SetupStepStage,
        actionId: String,
        request: ToolExecutionRequest,
        recipeRevision: String? = null,
        onRunStarted: (SetupStepStage, String) -> Unit = { _, _ -> },
        log: (String) -> Unit = {},
    ): JournaledRunResult {
        val idempotencyKey = idempotencyKey(context, actionId)
        val workspaceLock = workspaceLock(context, actionId)
        val intent = ChildIntentRecord(
            childIndex = -1,
            stage = stage.name,
            actionId = actionId,
            recipeRevision = recipeRevision,
            request = PersistedExecutionRequest(
                toolId = request.toolId,
                arguments = request.arguments,
                workingDirectory = request.workingDirectory ?: "/",
                environment = request.environment,
                timeoutMs = request.timeoutMs,
            ),
            idempotencyKey = idempotencyKey,
            workspaceLock = workspaceLock,
        )
        val childIndex = repository.appendChildIntent(context.attemptId, intent).getOrElse { failure ->
            return JournaledRunResult.JournalRejected(
                "Journal write failed before submitting '$actionId'; nothing was submitted: ${failure.message}",
            )
        }

        val setupRequest = if (request.purpose != io.ltirom.tooling.core.remote.RunPurpose.SETUP) {
            request.copy(purpose = io.ltirom.tooling.core.remote.RunPurpose.SETUP)
        } else {
            request
        }
        val handle = transport.startRun(
            StartRunRequest(
                request = setupRequest,
                idempotencyKey = idempotencyKey,
                workspaceLock = workspaceLock,
                purpose = io.ltirom.tooling.core.remote.RunPurpose.SETUP,
            ),
        )

        repository.recordChildRunId(context.attemptId, childIndex, handle.runId).onFailure { failure ->
            val reason = "Run '${handle.runId}' for '$actionId' started but its identity could not be journaled: " +
                failure.message
            repository.setRecoveryBlocked(true, reason)
            return JournaledRunResult.Interrupted(reason)
        }
        onRunStarted(stage, handle.runId)

        return attachAndCommit(
            childIndex = childIndex,
            runId = handle.runId,
            fromSeq = 1L,
            actionId = actionId,
            log = log,
        )
    }

    /**
     * Attaches to [runId] from [fromSeq], committing evidence and cursor atomically per event, and records
     * the authoritative terminal proof. Shared by first execution and by reconnection.
     */
    @Suppress("ReturnCount")
    public suspend fun attachAndCommit(
        childIndex: Int,
        runId: String,
        fromSeq: Long,
        actionId: String,
        log: (String) -> Unit,
    ): JournaledRunResult {
        var streamedExitCode: Int? = null
        val errorTail = ArrayDeque<String>()
        try {
            transport.attachRun(runId, fromSeq).collect { sequenced ->
                val event = sequenced.event
                if (event is StreamEvent.ExecutionFinished) streamedExitCode = event.exitCode
                keepErrorTail(errorTail, event)
                commitEvent(childIndex, runId, actionId, sequenced.seq, event, log)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: JournalCommitException) {
            val reason = "Streaming '$actionId' (run $runId) stopped: ${e.message}"
            repository.setRecoveryBlocked(true, reason)
            return JournaledRunResult.Interrupted(reason)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            val reason = "Connection to run '$runId' for '$actionId' was lost: ${e.message}"
            repository.setRecoveryBlocked(true, reason)
            return JournaledRunResult.Interrupted(reason)
        }

        val status = transport.getRun(runId)
        val terminalStatus = status?.status?.takeIf { it in TERMINAL_STATUSES }
            ?: streamedExitCode?.let { if (it == 0) RunStatusValue.COMPLETED else RunStatusValue.FAILED }
        if (terminalStatus == null) {
            val reason = "Run '$runId' for '$actionId' has no authoritative terminal state after the stream ended."
            repository.setRecoveryBlocked(true, reason)
            return JournaledRunResult.Interrupted(reason)
        }
        val exitCode = status?.exitCode ?: streamedExitCode ?: if (terminalStatus == RunStatusValue.COMPLETED) 0 else 1

        repository.recordChildTerminalProof(
            ChildTerminalProof(
                environmentKey = context.environmentKey,
                attemptId = context.attemptId,
                runId = runId,
                authoritativeTerminalStatus = terminalStatus.name,
                observedAtEpochMs = System.currentTimeMillis(),
            ),
        ).onFailure { failure ->
            val reason = "Terminal proof for run '$runId' could not be journaled: ${failure.message}"
            repository.setRecoveryBlocked(true, reason)
            return JournaledRunResult.Interrupted(reason)
        }
        return JournaledRunResult.Finished(
            runId = runId,
            status = terminalStatus,
            exitCode = exitCode,
            errorTail = errorTail.joinToString("\n"),
        )
    }

    /**
     * Commits one stream event with its sequence in a single durable write. Heartbeats carry no
     * evidence and never move the cursor; every other event advances it only once its evidence is stored.
     */
    private suspend fun commitEvent(
        childIndex: Int,
        runId: String,
        actionId: String,
        seq: Long,
        event: StreamEvent,
        log: (String) -> Unit,
    ) {
        val commit: Result<Unit> = when (event) {
            is StreamEvent.OutputChunk -> {
                val text = event.text.trimEnd()
                val line = "[$actionId] $text"
                val sink = context.activity
                if (sink != null) sink.record(context.attemptId, runId, seq, line) else log(line)
                repository.commitChildCursorAndEvidence(context.attemptId, childIndex, seq, text)
            }
            is StreamEvent.ExecutionFinished,
            is StreamEvent.ProgressUpdate,
            ->
                repository.commitChildCursorAndEvidence(context.attemptId, childIndex, seq, null)
            is StreamEvent.Heartbeat -> return
        }
        commit.onFailure { failure ->
            throw JournalCommitException("Durable commit failed at seq $seq: ${failure.message}")
        }
    }

    /** Keeps the last [ERROR_TAIL_LINES] non-blank stderr lines: why the run failed. */
    private fun keepErrorTail(tail: ArrayDeque<String>, event: StreamEvent) {
        if (event !is StreamEvent.OutputChunk || !event.isError || event.text.isBlank()) return
        tail.addLast(event.text.trimEnd())
        if (tail.size > ERROR_TAIL_LINES) tail.removeFirst()
    }

    private class JournalCommitException(message: String) : RuntimeException(message)
}
