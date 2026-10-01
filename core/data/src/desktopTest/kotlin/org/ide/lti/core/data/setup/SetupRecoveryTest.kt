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

import com.russhwolf.settings.MapSettings
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.RunCancelResponse
import io.ltirom.tooling.core.remote.RunHandle
import io.ltirom.tooling.core.remote.RunStatus
import io.ltirom.tooling.core.remote.RunStatusValue
import io.ltirom.tooling.core.remote.SequencedStreamEvent
import io.ltirom.tooling.core.remote.StartRunRequest
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.repository.setup.ToolchainSetupRepositoryImpl
import org.ide.lti.core.datastore.ToolchainPreferencesDataSource
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.model.setup.AttemptStatus
import org.ide.lti.core.model.setup.ChildIntentRecord
import org.ide.lti.core.model.setup.PersistedExecutionRequest
import org.ide.lti.core.model.setup.SetupAttemptRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Restart recovery (US3, SC-003): one persistent fake server and one persistent journal (MapSettings) survive
 * across recreated repositories / coordinators / recovery engines, which is what a GUI restart looks like to
 * the adapter. Each test journals the attempt exactly the way [JournaledRunLauncher] does, then "crashes" at
 * a specific window and recovers with fresh objects.
 */
class SetupRecoveryTest {

    private class PersistentFakeServer : RemoteTransportPort {
        val runs = linkedMapOf<String, RunStatus>()
        val runLocks = mutableMapOf<String, String?>()
        val runEvents = mutableMapOf<String, MutableList<SequencedStreamEvent>>()
        val startRunCalls = mutableListOf<StartRunRequest>()
        val attachCalls = mutableListOf<Pair<String, Long>>()
        private var nextRunId = 1

        fun registerRun(
            runId: String,
            status: RunStatusValue,
            workspaceLock: String?,
            exitCode: Int? = null,
            startedAtEpochMs: Long = System.currentTimeMillis(),
        ) {
            runs[runId] = RunStatus(
                runId = runId,
                status = status,
                exitCode = exitCode,
                startedAtEpochMs = startedAtEpochMs,
            )
            runLocks[runId] = workspaceLock
            runEvents.getOrPut(runId) { mutableListOf() }
        }

        fun addEvents(runId: String, vararg events: StreamEvent) {
            val list = runEvents.getOrPut(runId) { mutableListOf() }
            var seq = (list.lastOrNull()?.seq ?: 0L) + 1L
            for (ev in events) list.add(SequencedStreamEvent(seq++, ev))
            runs[runId]?.let { runs[runId] = it.copy(lastSeq = seq - 1L) }
        }

        override suspend fun startRun(request: StartRunRequest): RunHandle {
            startRunCalls.add(request)
            val runId = "server-run-${nextRunId++}"
            registerRun(runId, RunStatusValue.RUNNING, request.workspaceLock)
            return RunHandle(
                runId = runId,
                status = RunStatusValue.RUNNING,
                startedAtEpochMs = System.currentTimeMillis(),
            )
        }

        override suspend fun getRun(runId: String): RunStatus? = runs[runId]

        override suspend fun listRuns(workspaceLock: String?): List<RunStatus> = if (workspaceLock == null) {
            runs.values.toList()
        } else {
            runs.values.filter { runLocks[it.runId] == workspaceLock }
        }

        override fun attachRun(runId: String, fromSeq: Long): Flow<SequencedStreamEvent> = flow {
            attachCalls.add(runId to fromSeq)
            for (ev in runEvents[runId].orEmpty()) if (ev.seq >= fromSeq) emit(ev)
        }

        override suspend fun cancelRun(runId: String, signal: String): RunCancelResponse? = null
        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse =
            ToolExecutionResponse(0, "", "", 1L)
        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = flow {}
        override suspend fun checkHealth(): WslServerInfo? = null
        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(emptyList())
        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(emptyList())
        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true
        override suspend fun downloadFile(remotePath: String): ByteArray? = null
        override suspend fun shutdown(): Boolean = true
    }

    private companion object {
        const val ENV = "Ubuntu"
        const val ATTEMPT = "attempt-1"
        const val ACTION = "submodule:android-tools"
        val CTX = SetupJournalContext(ENV, ATTEMPT)
        val LOCK = JournaledRunLauncher.workspaceLock(CTX, ACTION)
    }

    /** A fresh repository over the same durable settings = the journal as seen after a restart. */
    private fun reopen(settings: MapSettings) = ToolchainSetupRepositoryImpl(
        ToolchainPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined),
    )

    private suspend fun authorize(
        repo: ToolchainSetupRepositoryImpl,
        plannedActions: List<String> = listOf("sync-sources"),
        environmentKey: String = ENV,
    ) {
        repo.recordAttemptAuthorized(
            SetupAttemptRecord(
                attemptId = ATTEMPT,
                environmentKey = environmentKey,
                planId = "plan-1",
                planRevisionHash = "rev-1",
                planKind = "FULL_SETUP",
                plannedActionIds = plannedActions,
            ),
        ).getOrThrow()
    }

    private suspend fun journalIntent(
        repo: ToolchainSetupRepositoryImpl,
        submittedAt: Long = System.currentTimeMillis(),
    ): Int = repo.appendChildIntent(
        ATTEMPT,
        ChildIntentRecord(
            childIndex = -1,
            stage = "REPO_SYNCHRONIZATION",
            actionId = ACTION,
            request = PersistedExecutionRequest(toolId = "git", arguments = listOf("clone")),
            idempotencyKey = JournaledRunLauncher.idempotencyKey(CTX, ACTION),
            workspaceLock = LOCK,
            submissionTimestampEpochMs = submittedAt,
        ),
    ).getOrThrow()

    @Test
    fun preSubmitCrashClosesChildWithoutResubmissionAndAllowsFreshAttempt() = runTest {
        val server = PersistentFakeServer()
        val settings = MapSettings()
        val before = reopen(settings)
        authorize(before)
        journalIntent(before) // crash between the journal write and startRun: the server never saw it

        val repo = reopen(settings)
        val outcome = SetupRunRecovery(server, repo).recover(ENV)

        assertEquals(0, server.startRunCalls.size, "Nothing is ever resubmitted by recovery")
        assertTrue(
            outcome is SetupOutcome.Interrupted,
            "Unfinished planned work is never reported as success: $outcome",
        )
        assertFalse(repo.currentState.recoveryBlocked, "An unaccepted command inside the window is not ambiguous")
        assertNull(repo.currentState.activeAttempt)
        val archived = repo.currentState.attemptHistory.single()
        assertEquals(AttemptStatus.INTERRUPTED, archived.status)
        assertEquals(SetupRunRecovery.NOT_SUBMITTED_STATUS, archived.orderedIntents.single().terminalStatus)

        // "Explicit retry allocates a new attemptId": a fresh attempt is admitted and gets its own identity.
        val gate = SetupOperationCoordinator(repo).withAdmission(ENV, "plan-2", "FULL_SETUP") {
            SetupOutcome.Succeeded()
        }
        assertTrue(gate is SetupOutcome.Succeeded, "Journal gate admits new work once history is reconciled: $gate")
        repo.recordAttemptAuthorized(
            archived.copy(attemptId = "attempt-2", status = AttemptStatus.AUTHORIZED, orderedIntents = emptyList()),
        ).getOrThrow()
        assertNotEquals(archived.attemptId, repo.currentState.activeAttempt?.attemptId)
    }

    @Test
    fun lostReceiptAdoptsTheUniqueCandidateWithoutDuplicateStart() = runTest {
        val server = PersistentFakeServer()
        val settings = MapSettings()
        val before = reopen(settings)
        authorize(before)
        journalIntent(before)
        // startRun succeeded on the server but the process died before the runId was journaled.
        server.startRun(
            StartRunRequest(
                ToolExecutionRequest("git", listOf("clone")),
                JournaledRunLauncher.idempotencyKey(CTX, ACTION),
                LOCK,
            ),
        )
        server.addEvents("server-run-1", StreamEvent.OutputChunk("Cloning..."), StreamEvent.ExecutionFinished(0, 10L))
        server.runs["server-run-1"] =
            server.runs.getValue("server-run-1").copy(status = RunStatusValue.COMPLETED, exitCode = 0)
        val startsBefore = server.startRunCalls.size

        val repo = reopen(settings)
        val outcome = SetupRunRecovery(server, repo).recover(ENV)

        assertEquals(startsBefore, server.startRunCalls.size, "The lost receipt is reconciled, never resubmitted")
        assertEquals(listOf("server-run-1" to 1L), server.attachCalls, "Attached once, from the committed cursor + 1")
        assertTrue(
            outcome is SetupOutcome.Interrupted,
            "sync-sources was never recorded complete, so this is not success",
        )
        val archived = repo.currentState.attemptHistory.single()
        assertEquals("server-run-1", archived.orderedIntents.single().runId, "Reconciled runId is persisted")
        assertEquals("COMPLETED", archived.orderedIntents.single().terminalStatus)
        assertEquals(listOf("Cloning..."), archived.orderedIntents.single().replayLogs)
    }

    @Test
    fun reattachedOutputIsRecordedWithJournalIdentityAndReplayIsDeduplicated() = runTest {
        val server = PersistentFakeServer()
        val settings = MapSettings()
        val before = reopen(settings)
        authorize(before)
        val index = journalIntent(before)
        server.registerRun("server-run-9", RunStatusValue.RUNNING, LOCK)
        server.addEvents("server-run-9", StreamEvent.OutputChunk("line 1"))
        before.recordChildRunId(ATTEMPT, index, "server-run-9").getOrThrow()
        before.commitChildCursorAndEvidence(ATTEMPT, index, 1L, "line 1").getOrThrow()
        server.addEvents("server-run-9", StreamEvent.OutputChunk("line 2"), StreamEvent.ExecutionFinished(0, 3L))
        server.runs["server-run-9"] =
            server.runs.getValue("server-run-9").copy(status = RunStatusValue.COMPLETED, exitCode = 0)

        val activity = SetupLogBuffer()
        val narration = mutableListOf<String>()
        SetupRunRecovery(server, reopen(settings), activity = activity).recover(ENV) { narration.add(it) }

        val recorded = activity.snapshot().single()
        assertEquals(ATTEMPT, recorded.attemptId)
        assertEquals("server-run-9", recorded.childRunId, "Activity identity is the real child run id")
        assertEquals(2L, recorded.sequence, "Activity identity is the server sequence, not a local counter")
        assertEquals("[$ACTION] line 2", recorded.text)
        assertTrue(narration.none { it.contains("line 2") }, "Run output is not also narrated under a synthetic id")
        assertFalse(
            activity.append(ATTEMPT, "server-run-9", 2L, "[$ACTION] line 2"),
            "A replayed (runId, seq) is rejected instead of duplicating the line",
        )
        assertEquals(1, activity.size)
    }

    @Test
    fun streamingCrashResumesFromCommittedCursorWithoutDuplicateLogSequences() = runTest {
        val server = PersistentFakeServer()
        val settings = MapSettings()
        val before = reopen(settings)
        authorize(before)
        val index = journalIntent(before)
        server.registerRun("server-run-7", RunStatusValue.RUNNING, LOCK)
        server.addEvents("server-run-7", StreamEvent.OutputChunk("line 1"), StreamEvent.OutputChunk("line 2"))
        before.recordChildRunId(ATTEMPT, index, "server-run-7").getOrThrow()
        before.commitChildCursorAndEvidence(ATTEMPT, index, 1L, "line 1").getOrThrow()
        before.commitChildCursorAndEvidence(ATTEMPT, index, 2L, "line 2").getOrThrow()
        // crash here; the run finishes on the server meanwhile
        server.addEvents(
            "server-run-7",
            StreamEvent.Heartbeat(1L),
            StreamEvent.OutputChunk("line 3"),
            StreamEvent.ExecutionFinished(0, 5L),
        )
        server.runs["server-run-7"] =
            server.runs.getValue("server-run-7").copy(status = RunStatusValue.COMPLETED, exitCode = 0)

        val repo = reopen(settings)
        val logs = mutableListOf<String>()
        SetupRunRecovery(server, repo).recover(ENV) { logs.add(it) }

        assertEquals(listOf("server-run-7" to 3L), server.attachCalls, "Reattach starts at committed cursor + 1")
        val child = repo.currentState.attemptHistory.single().orderedIntents.single()
        assertEquals(listOf("line 1", "line 2", "line 3"), child.replayLogs, "No sequence is replayed twice")
        assertEquals(
            5L,
            child.lastSeq,
            "Heartbeat at seq 3 did not add evidence but the cursor follows the last committed event",
        )
        assertEquals(0, server.startRunCalls.size)
    }

    @Test
    fun terminalCrashArchivesCompletedAttemptAsSucceeded() = runTest {
        val server = PersistentFakeServer()
        val settings = MapSettings()
        val before = reopen(settings)
        authorize(before)
        val index = journalIntent(before)
        server.registerRun("server-run-9", RunStatusValue.COMPLETED, LOCK, exitCode = 0)
        before.recordChildRunId(ATTEMPT, index, "server-run-9").getOrThrow()
        before.recordChildTerminalProof(
            org.ide.lti.core.model.setup.ChildTerminalProof(ENV, ATTEMPT, "server-run-9", "COMPLETED", 1L),
        ).getOrThrow()
        before.recordPlanActionCompleted(ATTEMPT, "sync-sources").getOrThrow()
        // crash after all work completed but before recordAttemptTerminal

        val repo = reopen(settings)
        val outcome = SetupRunRecovery(server, repo).recover(ENV)

        assertTrue(outcome is SetupOutcome.Succeeded, "$outcome")
        assertEquals(AttemptStatus.SUCCEEDED, repo.currentState.attemptHistory.single().status)
        assertNull(repo.currentState.activeAttempt)
        assertTrue(server.attachCalls.isEmpty(), "A proven child is not re-attached")
    }

    @Test
    fun expiredDeduplicationWindowWithNoCandidateFailsClosed() = runTest {
        val server = PersistentFakeServer()
        val settings = MapSettings()
        val before = reopen(settings)
        authorize(before)
        journalIntent(before, submittedAt = 1_000_000L)

        val repo = reopen(settings)
        val now = 1_000_000L + SetupRunRecovery.DEDUPLICATION_WINDOW_MS + 1L
        val outcome = SetupRunRecovery(server, repo, clock = { now }).recover(ENV)

        assertTrue(outcome is SetupOutcome.Interrupted)
        assertTrue(repo.currentState.recoveryBlocked, "After 600,000 ms a missing receipt is ambiguous and blocks")
        assertEquals(0, server.startRunCalls.size)
        assertEquals(ATTEMPT, repo.currentState.activeAttempt?.attemptId, "A blocked attempt is not archived")
        val gate = SetupOperationCoordinator(repo).withAdmission(ENV, "plan-2", "FULL_SETUP") {
            SetupOutcome.Succeeded()
        }
        assertTrue(gate is SetupOutcome.Interrupted, "New mutations are refused while blocked")
        val reset = SetupOperationCoordinator(repo).withAdmission(
            ENV,
            "reset",
            SetupOperationCoordinator.KIND_CACHE_RESET,
        ) {
            repo.clearToolchainState(force = true)
                .fold({ SetupOutcome.Succeeded() }, { SetupOutcome.Failed(null, it.message ?: "") })
        }
        assertTrue(reset is SetupOutcome.Succeeded, "The confirmed cache reset is the user-directed escape")
        assertFalse(repo.currentState.recoveryBlocked)
    }

    @Test
    fun multipleLockCandidatesFailClosed() = runTest {
        val server = PersistentFakeServer()
        val settings = MapSettings()
        val before = reopen(settings)
        authorize(before)
        journalIntent(before)
        server.registerRun("a", RunStatusValue.COMPLETED, LOCK, exitCode = 0)
        server.registerRun("b", RunStatusValue.COMPLETED, LOCK, exitCode = 0)

        val repo = reopen(settings)
        val outcome = SetupRunRecovery(server, repo).recover(ENV)

        assertTrue(outcome is SetupOutcome.Interrupted)
        assertTrue(repo.currentState.recoveryBlocked)
        assertNull(repo.currentState.activeAttempt?.orderedIntents?.single()?.runId, "Neither candidate is adopted")
        assertTrue(server.attachCalls.isEmpty())
    }

    @Test
    fun wrongEnvironmentFailsClosed() = runTest {
        val server = PersistentFakeServer()
        val settings = MapSettings()
        val before = reopen(settings)
        authorize(before, environmentKey = "Debian")
        journalIntent(before)

        val repo = reopen(settings)
        val outcome = SetupRunRecovery(server, repo).recover(ENV)

        assertTrue(outcome is SetupOutcome.Interrupted)
        assertTrue(repo.currentState.recoveryBlocked)
        assertTrue(server.attachCalls.isEmpty() && server.startRunCalls.isEmpty())
    }

    @Test
    fun failedChildArchivesAttemptAsFailedAndUnblocksNewWork() = runTest {
        val server = PersistentFakeServer()
        val settings = MapSettings()
        val before = reopen(settings)
        authorize(before)
        val index = journalIntent(before)
        server.registerRun("server-run-3", RunStatusValue.FAILED, LOCK, exitCode = 2)
        server.addEvents("server-run-3", StreamEvent.ExecutionFinished(2, 1L))
        before.recordChildRunId(ATTEMPT, index, "server-run-3").getOrThrow()

        val repo = reopen(settings)
        val outcome = SetupRunRecovery(server, repo).recover(ENV)

        assertTrue(outcome is SetupOutcome.Failed, "$outcome")
        assertEquals(AttemptStatus.FAILED, repo.currentState.attemptHistory.single().status)
        val gate = SetupOperationCoordinator(repo).withAdmission(ENV, "plan-2", "FULL_SETUP") {
            SetupOutcome.Succeeded()
        }
        assertTrue(gate is SetupOutcome.Succeeded, "History never blocks a fresh, separately confirmed attempt")
    }

    @Test
    fun secondRecoveryIsANoOpAndHistoryIsNeverReplayed() = runTest {
        val server = PersistentFakeServer()
        val settings = MapSettings()
        val before = reopen(settings)
        authorize(before)
        val index = journalIntent(before)
        server.registerRun("server-run-5", RunStatusValue.COMPLETED, LOCK, exitCode = 0)
        server.addEvents("server-run-5", StreamEvent.OutputChunk("done"), StreamEvent.ExecutionFinished(0, 1L))
        before.recordChildRunId(ATTEMPT, index, "server-run-5").getOrThrow()
        before.recordPlanActionCompleted(ATTEMPT, "sync-sources").getOrThrow()

        val repo = reopen(settings)
        val recovery = SetupRunRecovery(server, repo)
        assertTrue(recovery.recover(ENV) is SetupOutcome.Succeeded)
        val attachesAfterFirst = server.attachCalls.size

        val again = recovery.recover(ENV, ATTEMPT)
        assertTrue(again is SetupOutcome.Failed, "History is not reconciled twice: $again")
        assertEquals(attachesAfterFirst, server.attachCalls.size)
        assertEquals(0, server.startRunCalls.size)
    }

    @Test
    fun journalGateRefusesMutationWhileAChildLacksTerminalProof() = runTest {
        val settings = MapSettings()
        val before = reopen(settings)
        authorize(before)
        journalIntent(before)

        val repo = reopen(settings)
        val gate = SetupOperationCoordinator(repo).withAdmission(ENV, "plan-2", "FULL_SETUP") {
            SetupOutcome.Succeeded()
        }
        assertTrue(gate is SetupOutcome.Interrupted, "Unproven journaled work blocks new mutations: $gate")
        val recoveryAdmitted = SetupOperationCoordinator(repo).withAdmission(
            ENV,
            ATTEMPT,
            SetupOperationCoordinator.KIND_RECOVERY,
        ) { SetupOutcome.Succeeded() }
        assertTrue(recoveryAdmitted is SetupOutcome.Succeeded, "Recovery itself is admitted")
    }
}
