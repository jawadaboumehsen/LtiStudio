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
import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.core.ports.RemoteTransportPort
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
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.repository.setup.ToolchainSetupRepositoryImpl
import org.ide.lti.core.datastore.ToolchainPreferencesDataSource
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.model.setup.ChildIntentRecord
import org.ide.lti.core.model.setup.SetupAttemptRecord
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubmoduleSyncEngineTest {

    private class RecordingCli : WslCliExecutor() {
        val executedCommands = mutableListOf<List<String>>()

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult {
            executedCommands.add(command)
            return when {
                command.contains("test") && command.contains("-d") -> CliExecutionResult(1, "", "not found")
                else -> CliExecutionResult(0, "", "")
            }
        }
    }

    private class RecordingTransport : RemoteTransportPort {
        val startedRuns = mutableListOf<StartRunRequest>()
        val attachedRunIds = mutableListOf<String>()
        var runStatus: RunStatus = RunStatus(
            runId = "run-sub-1",
            status = RunStatusValue.COMPLETED,
            exitCode = 0,
            startedAtEpochMs = 1000L,
            endedAtEpochMs = 2000L,
        )

        override suspend fun startRun(request: StartRunRequest): RunHandle {
            startedRuns.add(request)
            return RunHandle(runId = "run-sub-1", status = RunStatusValue.RUNNING, startedAtEpochMs = 1000L)
        }

        override fun attachRun(runId: String, fromSeq: Long): Flow<SequencedStreamEvent> {
            attachedRunIds.add(runId)
            return listOf(
                SequencedStreamEvent(1L, StreamEvent.OutputChunk("Cloning into android-tools...")),
                SequencedStreamEvent(2L, StreamEvent.ExecutionFinished(0, 500L)),
            ).asFlow()
        }

        override suspend fun getRun(runId: String): RunStatus? = runStatus

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse =
            ToolExecutionResponse(0, "", "", 1L)
        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()
        override suspend fun checkHealth(): WslServerInfo? = null
        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(emptyList())
        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(emptyList())
        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true
        override suspend fun downloadFile(remotePath: String): ByteArray? = null
        override suspend fun shutdown(): Boolean = true
    }

    private suspend fun journaledRepository(attemptId: String = "att-xyz"): ToolchainSetupRepositoryImpl {
        val repo = ToolchainSetupRepositoryImpl(
            ToolchainPreferencesDataSource(settings = MapSettings(), ioDispatcher = Dispatchers.Unconfined),
        )
        repo.recordAttemptAuthorized(
            SetupAttemptRecord(
                attemptId = attemptId,
                environmentKey = "Ubuntu",
                planId = "plan-1",
                planRevisionHash = "rev-1",
                planKind = "FULL_SETUP",
            ),
        ).getOrThrow()
        return repo
    }

    @Test
    fun `syncAll executes explicit sequential commands without bash -c or ampersands`() = runTest {
        val cli = RecordingCli()
        val transport = RecordingTransport()
        val repo = journaledRepository()
        val engine = SubmoduleSyncEngine(cli = cli, repository = repo, transport = transport)

        val testSub = SubmoduleSyncEngine.PinnedSubmodule(
            "test-sub",
            "https://example.com/test.git",
            "abcdef1234567890",
        )
        val result = engine.syncAll(
            "Ubuntu",
            "/home/lti/LtiRomTools/external",
            listOf(testSub),
            journal = SetupJournalContext("Ubuntu", "att-xyz"),
        )

        assertTrue(result.isSuccess)

        // Assert startRun called with attempt-scoped idempotency key (never a static one)
        // The clone, then the nested-submodule update: both durable runs under the attempt's identity.
        assertEquals(2, transport.startedRuns.size)
        assertEquals("Ubuntu:att-xyz:submodule:test-sub:submodules", transport.startedRuns[1].idempotencyKey)
        val req = transport.startedRuns.first()
        assertEquals("Ubuntu:att-xyz:submodule:test-sub", req.idempotencyKey)
        assertEquals("git", req.request.toolId)
        assertEquals(
            listOf("clone", "--recursive", "https://example.com/test.git", "/home/lti/LtiRomTools/external/test-sub"),
            req.request.arguments,
        )

        // Assert attachRun was invoked
        assertTrue(transport.attachedRunIds.contains("run-sub-1"))

        // Assert no command or argument across cli or transport contains bash -c or &&
        for (cmd in cli.executedCommands) {
            assertFalse(cmd.contains("bash"), "Command must not invoke bash: $cmd")
            assertFalse(cmd.contains("-c"), "Command must not contain -c flag: $cmd")
            assertFalse(cmd.any { it.contains("&&") }, "Command must not contain && shell operator: $cmd")
        }
        for (startReq in transport.startedRuns) {
            assertFalse(startReq.request.toolId == "bash", "Transport request must not invoke bash")
            assertFalse(startReq.request.arguments.contains("-c"), "Transport args must not contain -c")
            assertFalse(startReq.request.arguments.any { it.contains("&&") }, "Transport args must not contain &&")
        }

        // Verify git checkout executed explicitly
        val checkoutCmd = cli.executedCommands.find { it.contains("checkout") }
        assertTrue(checkoutCmd != null, "Must execute git checkout")
        assertEquals(
            listOf("git", "-C", "/home/lti/LtiRomTools/external/test-sub", "checkout", "-f", "abcdef1234567890"),
            checkoutCmd,
        )
    }

    @Test
    fun `syncAll reattaches to in-flight clone run without duplicate process`() = runTest {
        val cli = RecordingCli()
        val transport = RecordingTransport()
        transport.runStatus = RunStatus(
            runId = "existing-run-99",
            status = RunStatusValue.RUNNING,
            startedAtEpochMs = 500L,
        )

        val repo = journaledRepository()
        val engine = SubmoduleSyncEngine(cli = cli, repository = repo, transport = transport)
        val testSub = SubmoduleSyncEngine.PinnedSubmodule(
            "test-sub",
            "https://example.com/test.git",
            "abcdef1234567890",
        )

        val logs = mutableListOf<String>()
        val result = engine.syncAll(
            "Ubuntu",
            "/home/lti/LtiRomTools/external",
            listOf(testSub),
            journal = SetupJournalContext("Ubuntu", "att-xyz"),
        ) { logs.add(it) }

        // getRun still reports RUNNING, so the streamed ExecutionFinished(0) is the terminal evidence.
        assertTrue(result.isSuccess)
        assertTrue(transport.attachedRunIds.isNotEmpty(), "Must attach to the clone run")
        assertTrue(logs.any { it.contains("Cloning into android-tools...") })
    }

    @Test
    fun `syncAll journals intent before startRun and runId before attach`() = runTest {
        val cli = RecordingCli()
        val repo = journaledRepository("att-order")
        val observedAtStart = mutableListOf<ChildIntentRecord?>()
        val observedAtAttach = mutableListOf<ChildIntentRecord?>()
        val transport = object : RemoteTransportPort by RecordingTransport() {
            override suspend fun startRun(request: StartRunRequest): RunHandle {
                observedAtStart.add(repo.currentState.activeAttempt?.orderedIntents?.lastOrNull())
                return RunHandle(runId = "run-order-1", status = RunStatusValue.RUNNING, startedAtEpochMs = 1L)
            }

            override fun attachRun(runId: String, fromSeq: Long): Flow<SequencedStreamEvent> {
                observedAtAttach.add(repo.currentState.activeAttempt?.orderedIntents?.lastOrNull())
                return listOf(
                    SequencedStreamEvent(1L, StreamEvent.OutputChunk("line one")),
                    SequencedStreamEvent(2L, StreamEvent.Heartbeat(5L)),
                    SequencedStreamEvent(3L, StreamEvent.ExecutionFinished(0, 10L)),
                ).asFlow()
            }

            override suspend fun getRun(runId: String): RunStatus? =
                RunStatus(runId = runId, status = RunStatusValue.COMPLETED, exitCode = 0, startedAtEpochMs = 1L)
        }
        val engine = SubmoduleSyncEngine(cli = cli, repository = repo, transport = transport)
        val testSub = SubmoduleSyncEngine.PinnedSubmodule(
            "test-sub",
            "https://example.com/test.git",
            "abcdef1234567890",
        )

        val result = engine.syncAll(
            "Ubuntu",
            "/home/lti/LtiRomTools/external",
            listOf(testSub),
            journal = SetupJournalContext("Ubuntu", "att-order"),
        )
        assertTrue(result.isSuccess)

        // "Persist child intent before startRun": the exact request was journaled, without a runId, before submission.
        val atStart = observedAtStart.first() // the clone
        assertEquals("submodule:test-sub", atStart?.actionId)
        assertEquals("git", atStart?.request?.toolId)
        assertEquals(
            listOf("clone", "--recursive", "https://example.com/test.git", "/home/lti/LtiRomTools/external/test-sub"),
            atStart?.request?.arguments,
        )
        assertNull(atStart?.runId)
        // "Persist runId before acknowledging attachment"
        assertEquals("run-order-1", observedAtAttach.first()?.runId)

        val intent = repo.currentState.activeAttempt?.orderedIntents?.first()
        assertEquals(3L, intent?.lastSeq, "Cursor advanced to the last committed sequence")
        assertEquals(
            listOf("line one"),
            intent?.replayLogs,
            "Only output lines are replay evidence; heartbeats add none",
        )
        assertEquals("COMPLETED", intent?.terminalStatus)
        assertEquals("run-order-1", repo.currentState.activeAttempt?.terminalProofs?.first()?.runId)
    }

    @Test
    fun `syncAll refuses an unjournaled durable run`() = runTest {
        val cli = RecordingCli()
        val transport = RecordingTransport()
        val engine = SubmoduleSyncEngine(cli = cli, transport = transport)
        val testSub = SubmoduleSyncEngine.PinnedSubmodule(
            "test-sub",
            "https://example.com/test.git",
            "abcdef1234567890",
        )

        val result = engine.syncAll("Ubuntu", "/home/lti/LtiRomTools/external", listOf(testSub))

        assertTrue(
            result.isFailure,
            "A durable server run without a journal is refused, never started with a static key",
        )
        assertEquals(0, transport.startedRuns.size)
    }

    @Test
    fun `syncAll journal write failure prevents submission`() = runTest {
        val cli = RecordingCli()
        val transport = RecordingTransport()
        val repo = journaledRepository("att-fail")
        val refusing = object : ToolchainSetupRepository by repo {
            override suspend fun appendChildIntent(attemptId: String, intent: ChildIntentRecord): Result<Int> =
                Result.failure(IllegalStateException("disk full"))
        }
        val engine = SubmoduleSyncEngine(cli = cli, repository = refusing, transport = transport)
        val testSub = SubmoduleSyncEngine.PinnedSubmodule(
            "test-sub",
            "https://example.com/test.git",
            "abcdef1234567890",
        )

        val result = engine.syncAll(
            "Ubuntu",
            "/home/lti/LtiRomTools/external",
            listOf(testSub),
            journal = SetupJournalContext("Ubuntu", "att-fail"),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("nothing was submitted") == true)
        assertEquals(0, transport.startedRuns.size, "Journal write failure prevents submission")
    }
}
