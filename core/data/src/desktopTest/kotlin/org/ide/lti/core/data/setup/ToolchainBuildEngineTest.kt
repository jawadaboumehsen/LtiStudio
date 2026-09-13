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

class ToolchainBuildEngineTest {

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
                command.contains("test") && (command.contains("-x") || command.contains("-f") || command.contains("-d")) -> {
                    // simulate binaries present after build
                    CliExecutionResult(0, "", "")
                }
                else -> CliExecutionResult(0, "", "")
            }
        }
    }

    private class RecordingTransport : RemoteTransportPort {
        val startedRuns = mutableListOf<StartRunRequest>()
        val attachedRunIds = mutableListOf<String>()

        override suspend fun startRun(request: StartRunRequest): RunHandle {
            startedRuns.add(request)
            val runId = "run-${startedRuns.size}"
            return RunHandle(runId = runId, status = RunStatusValue.RUNNING, startedAtEpochMs = 1000L)
        }

        override fun attachRun(runId: String, fromSeq: Long): Flow<SequencedStreamEvent> {
            attachedRunIds.add(runId)
            return listOf(
                SequencedStreamEvent(1L, StreamEvent.OutputChunk("Compiling target...")),
                SequencedStreamEvent(2L, StreamEvent.ExecutionFinished(0, 1000L))
            ).asFlow()
        }

        override suspend fun getRun(runId: String): RunStatus = RunStatus(
            runId = runId,
            status = RunStatusValue.COMPLETED,
            exitCode = 0,
            startedAtEpochMs = 1000L,
            endedAtEpochMs = 2000L
        )

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
    fun `buildAndDistributeAll executes explicit sequential commands without bash -c or ampersands`() = runTest {
        val cli = RecordingCli()
        val transport = RecordingTransport()
        val engine = ToolchainBuildEngine(cli = cli, transport = transport)

        val result = engine.buildAndDistributeAll(
            distro = "Ubuntu",
            extDir = "/home/lti/LtiRomTools/external",
            binDir = "/home/lti/LtiRomTools/bin"
        )

        assertTrue(result.isSuccess, "buildAndDistributeAll must succeed: ${result.exceptionOrNull()?.message}")

        // Check started runs and idempotency keys
        val idempotencyKeys = transport.startedRuns.map { it.idempotencyKey }
        assertTrue(idempotencyKeys.contains("setup:build:android-tools:cmake"), "Must run android-tools cmake with idempotency key")
        assertTrue(idempotencyKeys.contains("setup:build:android-tools:make"), "Must run android-tools make with idempotency key")
        assertTrue(idempotencyKeys.contains("setup:build:erofs-utils:cmake"), "Must run erofs-utils cmake with idempotency key")
        assertTrue(idempotencyKeys.contains("setup:build:erofs-utils:make"), "Must run erofs-utils make with idempotency key")

        // Assert attachRun was invoked for each long step
        assertEquals(4, transport.attachedRunIds.size)

        // Assert NO command on cli or transport contains bash, -c, or &&
        for (cmd in cli.executedCommands) {
            assertFalse(cmd.contains("bash"), "Command must not invoke bash: $cmd")
            assertFalse(cmd.contains("-c"), "Command must not contain -c flag: $cmd")
            assertFalse(cmd.any { it.contains("&&") }, "Command must not contain && shell operator: $cmd")
            assertFalse(cmd.any { it.contains("||") }, "Command must not contain || shell operator: $cmd")
        }
        for (req in transport.startedRuns) {
            assertFalse(req.request.toolId == "bash", "Transport toolId must not be bash")
            assertFalse(req.request.arguments.contains("-c"), "Transport args must not contain -c")
            assertFalse(req.request.arguments.any { it.contains("&&") }, "Transport args must not contain &&")
        }

        // Verify discrete file distribution commands
        assertTrue(cli.executedCommands.any { it.contains("cp") && it.contains("-a") }, "Must use cp -a")
        assertTrue(cli.executedCommands.any { it.contains("ln") && it.contains("-sf") }, "Must use ln -sf")
        assertTrue(cli.executedCommands.any { it.contains("chmod") && it.contains("+x") }, "Must use chmod +x")
    }

    @Test
    fun `buildAndDistributeAll reattaches to in-flight build run without restarting`() = runTest {
        val cli = RecordingCli()
        val transport = RecordingTransport()
        val engine = ToolchainBuildEngine(cli = cli, transport = transport)

        val logs = mutableListOf<String>()
        val result = engine.buildAndDistributeAll(
            distro = "Ubuntu",
            extDir = "/home/lti/LtiRomTools/external",
            binDir = "/home/lti/LtiRomTools/bin"
        ) { logs.add(it) }

        assertTrue(result.isSuccess)
        assertTrue(logs.any { it.contains("Attached to build run") })
        assertTrue(logs.any { it.contains("Compiling target...") })
    }
}
