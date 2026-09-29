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
                command.first() == "sha256sum" -> fakeSha256(command.getOrNull(1).orEmpty())
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

    /** [failingTool]'s runs write [failingStderr] and exit 1; every other run succeeds. */
    private class RecordingTransport : RemoteTransportPort {
        var failingTool: String? = null
        var failingStderr: List<String> = emptyList()
        val startedRuns = mutableListOf<StartRunRequest>()
        val attachedRunIds = mutableListOf<String>()

        private fun fails(runId: String): Boolean =
            startedRuns[runId.removePrefix("run-").toInt() - 1].request.toolId == failingTool

        override suspend fun startRun(request: StartRunRequest): RunHandle {
            startedRuns.add(request)
            val runId = "run-${startedRuns.size}"
            return RunHandle(runId = runId, status = RunStatusValue.RUNNING, startedAtEpochMs = 1000L)
        }

        override fun attachRun(runId: String, fromSeq: Long): Flow<SequencedStreamEvent> {
            attachedRunIds.add(runId)
            if (fails(runId)) {
                val errors = failingStderr.mapIndexed { i, line ->
                    SequencedStreamEvent(i + 1L, StreamEvent.OutputChunk(line, isError = true))
                }
                return (errors + SequencedStreamEvent(errors.size + 1L, StreamEvent.ExecutionFinished(1, 1000L)))
                    .asFlow()
            }
            return listOf(
                SequencedStreamEvent(1L, StreamEvent.OutputChunk("Compiling target...")),
                SequencedStreamEvent(2L, StreamEvent.ExecutionFinished(0, 1000L)),
            ).asFlow()
        }

        override suspend fun getRun(runId: String): RunStatus = RunStatus(
            runId = runId,
            status = if (fails(runId)) RunStatusValue.FAILED else RunStatusValue.COMPLETED,
            exitCode = if (fails(runId)) 1 else 0,
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
        // android-tools and erofs-utils cmake + make, plus the apktool and signapk Gradle builds.
        assertEquals(
            6,
            repo.currentState.activeAttempt?.orderedIntents?.size,
            "Every server command is a journaled child",
        )
        assertEquals(
            6,
            repo.currentState.activeAttempt?.terminalProofs?.size,
            "Every child has authoritative terminal proof",
        )

        // Assert attachRun was invoked for each long step
        assertEquals(6, transport.attachedRunIds.size, "cmake + make for two groups, plus two Gradle builds")

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

    @Test
    fun aFailedCmakeConfigurationSaysWhyItFailed() = runTest {
        val transport = RecordingTransport().apply {
            failingTool = "cmake"
            failingStderr = listOf(
                "CMake Error at FindPackageHandleStandardArgs.cmake:230 (message):",
                "  Could NOT find BZip2 (missing: BZIP2_LIBRARIES BZIP2_INCLUDE_DIR)",
            )
        }
        val engine = ToolchainBuildEngine(
            cli = RecordingCli(),
            repository = journaledRepository("att-build-6"),
            transport = transport,
        )

        val result = engine.buildAndDistributeAll(
            distro = "Ubuntu",
            extDir = "/home/lti/LtiRomTools/external",
            binDir = "/home/lti/LtiRomTools/bin",
            journal = SetupJournalContext("Ubuntu", "att-build-6"),
            targetRecipeGroups = setOf("android-tools"),
            forceRebuild = true,
        )

        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue("android-tools cmake configuration failed" in message, message)
        assertTrue("Could NOT find BZip2" in message, "cmake's own reason is in the error: $message")
    }

    @Test
    fun androidToolsAppliesItsOwnVendorPatchesWithAGitIdentity() = runTest {
        val transport = RecordingTransport()
        val engine = ToolchainBuildEngine(
            cli = RecordingCli(),
            repository = journaledRepository("att-build-7"),
            transport = transport,
        )

        engine.buildAndDistributeAll(
            distro = "Ubuntu",
            extDir = "/ext",
            binDir = "/bin",
            journal = SetupJournalContext("Ubuntu", "att-build-7"),
            targetRecipeGroups = setOf("android-tools"),
            forceRebuild = true,
        )

        val cmake = transport.startedRuns.first { it.idempotencyKey.endsWith("android-tools:cmake") }.request
        assertTrue("-DANDROID_TOOLS_PATCH_VENDOR=ON" in cmake.arguments, "${cmake.arguments}")
        // `git am` fails on a machine without a git identity; the user's git config is never written.
        assertEquals("LtiRom Studio", cmake.environment["GIT_COMMITTER_NAME"])
        assertTrue(cmake.environment["GIT_COMMITTER_EMAIL"].orEmpty().isNotBlank())
        val make = transport.startedRuns.first { it.idempotencyKey.endsWith("android-tools:make") }.request
        assertTrue(make.environment.isEmpty(), "only configure applies patches: ${make.environment}")
    }

    @Test
    fun rebuildDiscardsTheOldCmakeConfigurationAndConfiguresErofsWithClang() = runTest {
        // The cached configuration pinned /usr/bin/llvm-ar, which no longer existed: every rebuild failed.
        val cli = RecordingCli()
        ToolchainBuildEngine(cli = cli).buildAndDistributeAll(
            distro = "Ubuntu",
            extDir = "/ext",
            binDir = "/bin-dir",
            targetRecipeGroups = setOf(org.ide.lti.core.domain.setup.ToolCapabilities.GROUP_EROFS_UTILS),
            forceRebuild = true,
        )

        val discard = cli.executedCommands.indexOfFirst { it.contains("/ext/erofs-utils/out/CMakeCache.txt") }
        val configure = cli.executedCommands.indexOfFirst { it.firstOrNull() == "cmake" }
        assertTrue(discard in 0 until configure, "the old cache goes before configuring: ${cli.executedCommands}")
        val cmake = cli.executedCommands[configure]
        assertTrue("-DCMAKE_C_COMPILER=clang" in cmake && "-DCMAKE_CXX_COMPILER=clang++" in cmake, "$cmake")
        assertTrue("-DCMAKE_BUILD_TYPE=Release" in cmake, "$cmake")
    }
}
