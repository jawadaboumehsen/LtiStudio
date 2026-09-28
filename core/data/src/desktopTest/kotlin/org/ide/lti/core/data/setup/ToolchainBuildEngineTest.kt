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
import org.ide.lti.core.model.setup.SetupAttemptRecord
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
            user: String?,
        ): CliExecutionResult {
            executedCommands.add(command)
            return when {
                command.contains(
                    "test",
                ) &&
                    (command.contains("-x") || command.contains("-f") || command.contains("-d")) -> {
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
                SequencedStreamEvent(2L, StreamEvent.ExecutionFinished(0, 1000L)),
            ).asFlow()
        }

        override suspend fun getRun(runId: String): RunStatus = RunStatus(
            runId = runId,
            status = RunStatusValue.COMPLETED,
            exitCode = 0,
            startedAtEpochMs = 1000L,
            endedAtEpochMs = 2000L,
        )

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

    private suspend fun journaledRepository(attemptId: String): ToolchainSetupRepositoryImpl {
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
    fun `buildAndDistributeAll executes explicit sequential commands without bash -c or ampersands`() = runTest {
        val cli = RecordingCli()
        val transport = RecordingTransport()
        val repo = journaledRepository("att-build-1")
        val engine = ToolchainBuildEngine(cli = cli, repository = repo, transport = transport)

        val result = engine.buildAndDistributeAll(
            distro = "Ubuntu",
            extDir = "/home/lti/LtiRomTools/external",
            binDir = "/home/lti/LtiRomTools/bin",
            journal = SetupJournalContext("Ubuntu", "att-build-1"),
        )

        assertTrue(result.isSuccess, "buildAndDistributeAll must succeed: ${result.exceptionOrNull()?.message}")

        // Check started runs and idempotency keys
        val idempotencyKeys = transport.startedRuns.map { it.idempotencyKey }
        assertTrue(
            idempotencyKeys.contains("Ubuntu:att-build-1:build:android-tools:cmake"),
            "android-tools cmake runs under attempt identity",
        )
        assertTrue(
            idempotencyKeys.contains("Ubuntu:att-build-1:build:android-tools:make"),
            "android-tools make runs under attempt identity",
        )
        assertTrue(
            idempotencyKeys.contains("Ubuntu:att-build-1:build:erofs-utils:cmake"),
            "erofs-utils cmake runs under attempt identity",
        )
        assertTrue(
            idempotencyKeys.contains("Ubuntu:att-build-1:build:erofs-utils:make"),
            "erofs-utils make runs under attempt identity",
        )
        assertTrue(idempotencyKeys.none { it.startsWith("setup:") }, "No static idempotency keys remain")
        assertEquals(
            4,
            repo.currentState.activeAttempt?.orderedIntents?.size,
            "Every server command is a journaled child",
        )
        assertEquals(
            4,
            repo.currentState.activeAttempt?.terminalProofs?.size,
            "Every child has authoritative terminal proof",
        )

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
        val repo = journaledRepository("att-build-2")
        val engine = ToolchainBuildEngine(cli = cli, repository = repo, transport = transport)

        val logs = mutableListOf<String>()
        val result = engine.buildAndDistributeAll(
            distro = "Ubuntu",
            extDir = "/home/lti/LtiRomTools/external",
            binDir = "/home/lti/LtiRomTools/bin",
            journal = SetupJournalContext("Ubuntu", "att-build-2"),
        ) { logs.add(it) }

        assertTrue(result.isSuccess)
        assertTrue(logs.any { it.contains("Attached to build run") })
        assertTrue(logs.any { it.contains("Compiling target...") })
    }

    @Test
    fun `buildAndDistributeAll refuses an unjournaled durable run`() = runTest {
        val cli = RecordingCli()
        val transport = RecordingTransport()
        val engine = ToolchainBuildEngine(cli = cli, transport = transport)

        val result = engine.buildAndDistributeAll(
            distro = "Ubuntu",
            extDir = "/home/lti/LtiRomTools/external",
            binDir = "/home/lti/LtiRomTools/bin",
        )

        assertTrue(result.isFailure, "A durable build step without a journal is refused")
        assertEquals(0, transport.startedRuns.size, "Nothing is submitted under a static key")
    }

    @Test
    fun `buildAndDistributeAll with targetRecipeGroups builds only requested group`() = runTest {
        val cli = RecordingCli()
        val transport = RecordingTransport()
        val repo = journaledRepository("att-build-3")
        val engine = ToolchainBuildEngine(cli = cli, repository = repo, transport = transport)

        val result = engine.buildAndDistributeAll(
            distro = "Ubuntu",
            extDir = "/home/lti/LtiRomTools/external",
            binDir = "/home/lti/LtiRomTools/bin",
            journal = SetupJournalContext("Ubuntu", "att-build-3"),
            targetRecipeGroups = setOf("android-tools"),
        )

        assertTrue(result.isSuccess, "Scoped build of android-tools must succeed: ${result.exceptionOrNull()?.message}")
        val idempotencyKeys = transport.startedRuns.map { it.idempotencyKey }
        assertTrue(
            idempotencyKeys.any { it.contains("android-tools") },
            "android-tools recipes must be executed",
        )
        assertFalse(
            idempotencyKeys.any { it.contains("erofs-utils") },
            "erofs-utils must not be executed when only android-tools is targeted (FR-009)",
        )
    }

    @Test
    fun `buildAndDistributeAll with forceRebuild ignores sentinels for requested group`() = runTest {
        val cli = RecordingCli()
        val transport = RecordingTransport()
        val repo = journaledRepository("att-build-4")
        // adb sentinel is recorded as compiled
        repo.updateTool("adb") {
            it.copy(isCompiled = true, isVerified = true, binaryPath = "/home/lti/LtiRomTools/bin/adb")
        }

        val engine = ToolchainBuildEngine(cli = cli, repository = repo, transport = transport)

        val result = engine.buildAndDistributeAll(
            distro = "Ubuntu",
            extDir = "/home/lti/LtiRomTools/external",
            binDir = "/home/lti/LtiRomTools/bin",
            journal = SetupJournalContext("Ubuntu", "att-build-4"),
            targetRecipeGroups = setOf("android-tools"),
            forceRebuild = true,
        )

        assertTrue(result.isSuccess)
        val idempotencyKeys = transport.startedRuns.map { it.idempotencyKey }
        assertTrue(
            idempotencyKeys.any { it.contains("android-tools:cmake") },
            "forceRebuild must force android-tools compilation despite existing adb sentinel (FR-009)",
        )
    }
}
