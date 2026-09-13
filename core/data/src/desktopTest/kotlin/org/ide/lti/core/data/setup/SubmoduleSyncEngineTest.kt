package org.ide.lti.core.data.setup

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
import io.ltirom.tooling.core.remote.ToolStatusInfo
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubmoduleSyncEngineTest {

    private class RecordingCli : WslCliExecutor() {
        val executedCommands = mutableListOf<List<String>>()

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?
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
            endedAtEpochMs = 2000L
        )

        override suspend fun startRun(request: StartRunRequest): RunHandle {
            startedRuns.add(request)
            return RunHandle(runId = "run-sub-1", status = RunStatusValue.RUNNING, startedAtEpochMs = 1000L)
        }

        override fun attachRun(runId: String, fromSeq: Long): Flow<SequencedStreamEvent> {
            attachedRunIds.add(runId)
            return listOf(
                SequencedStreamEvent(1L, StreamEvent.OutputChunk("Cloning into android-tools...")),
                SequencedStreamEvent(2L, StreamEvent.ExecutionFinished(0, 500L))
            ).asFlow()
        }

        override suspend fun getRun(runId: String): RunStatus? = runStatus

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse = ToolExecutionResponse(0, "", "", 1L)
        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()
        override suspend fun checkHealth(): WslServerInfo? = null
        override suspend fun listTools(): List<ToolStatusInfo> = emptyList()
        override suspend fun refreshTools(): List<ToolStatusInfo> = emptyList()
        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true
        override suspend fun downloadFile(remotePath: String): ByteArray? = null
        override suspend fun shutdown(): Boolean = true
    }

    @Test
    fun `syncAll executes explicit sequential commands without bash -c or ampersands`() = runTest {
        val cli = RecordingCli()
        val transport = RecordingTransport()
        val engine = SubmoduleSyncEngine(cli = cli, transport = transport)

        val testSub = SubmoduleSyncEngine.PinnedSubmodule("test-sub", "https://example.com/test.git", "abcdef1234567890")
        val result = engine.syncAll("Ubuntu", "/home/lti/LtiRomTools/external", listOf(testSub))

        assertTrue(result.isSuccess)

        // Assert startRun called with idempotency key
        assertEquals(1, transport.startedRuns.size)
        val req = transport.startedRuns.first()
        assertEquals("setup:submodule:test-sub", req.idempotencyKey)
        assertEquals("git", req.request.toolId)
        assertEquals(listOf("clone", "--recursive", "https://example.com/test.git", "/home/lti/LtiRomTools/external/test-sub"), req.request.arguments)

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
        assertEquals(listOf("git", "-C", "/home/lti/LtiRomTools/external/test-sub", "checkout", "-f", "abcdef1234567890"), checkoutCmd)
    }

    @Test
    fun `syncAll reattaches to in-flight clone run without duplicate process`() = runTest {
        val cli = RecordingCli()
        val transport = RecordingTransport()
        transport.runStatus = RunStatus(
            runId = "existing-run-99",
            status = RunStatusValue.RUNNING,
            startedAtEpochMs = 500L
        )

        val engine = SubmoduleSyncEngine(cli = cli, transport = transport)
        val testSub = SubmoduleSyncEngine.PinnedSubmodule("test-sub", "https://example.com/test.git", "abcdef1234567890")

        val logs = mutableListOf<String>()
        val result = engine.syncAll("Ubuntu", "/home/lti/LtiRomTools/external", listOf(testSub)) { logs.add(it) }

        assertTrue(result.isSuccess)
        assertTrue(transport.attachedRunIds.isNotEmpty(), "Must attach to the clone run")
        assertTrue(logs.any { it.contains("Cloning into android-tools...") })
    }
}
