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

import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ToolRepairTest {

    private class SpyDaemonSupervisor(
        var descriptor: ServerConnectionDescriptor? = ServerConnectionDescriptor("127.0.0.1", 9090, "token", 1234L, "Ubuntu"),
    ) : DaemonSupervisorPort {
        override suspend fun getConnectionInfo(): ServerConnectionDescriptor? = descriptor
        override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean = true
        override suspend fun ensureStarted(): ServerConnectionDescriptor = descriptor ?: throw IllegalStateException("No descriptor")
        override suspend fun shutdownDaemon(): Boolean = true
        override fun close() {}
    }

    private class SpyRemoteTransport(
        var testXExitCode: Int = 0,
        var helpExitCode: Int = 0,
        var helpOutput: String = "Usage: lpmake [options]",
        var buildExitCode: Int = 0,
    ) : RemoteTransportPort {
        val executedRequests = mutableListOf<ToolExecutionRequest>()
        val startedRuns = mutableListOf<StartRunRequest>()

        override suspend fun checkHealth(): WslServerInfo = WslServerInfo(
            status = "HEALTHY",
            distro = "Ubuntu",
            kernelRelease = "5.15.0",
            architecture = "x86_64",
            javaVersion = "21.0.11",
            serverUptimeMs = 100_000L,
            availableTools = listOf("adb", "fastboot")
        )

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse {
            executedRequests.add(request)
            return when {
                request.toolId == "printenv" -> ToolExecutionResponse(0, "/home/lti\n", "", 1L)
                request.toolId == "test" && request.arguments.contains("-x") -> {
                    ToolExecutionResponse(testXExitCode, "", "", 1L)
                }
                request.toolId == "test" -> ToolExecutionResponse(0, "", "", 1L)
                request.arguments.contains("--help") || request.toolId.endsWith("lpmake") || request.arguments.contains("-h") -> {
                    ToolExecutionResponse(helpExitCode, helpOutput, if (helpExitCode != 0) "Error: failed" else "", 1L)
                }
                request.toolId in listOf("cmake", "make") -> {
                    ToolExecutionResponse(buildExitCode, if (buildExitCode == 0) "Build completed" else "Build error", "", 1L)
                }
                else -> ToolExecutionResponse(0, "ok", "", 1L)
            }
        }

        override suspend fun startRun(request: StartRunRequest): RunHandle {
            startedRuns.add(request)
            return RunHandle(runId = "run-test-1", status = RunStatusValue.RUNNING, startedAtEpochMs = 1000L)
        }

        override fun attachRun(runId: String, fromSequence: Long): Flow<SequencedStreamEvent> = emptyFlow()

        override suspend fun getRun(runId: String): RunStatus = RunStatus(
            runId = runId,
            status = if (buildExitCode == 0) RunStatusValue.COMPLETED else RunStatusValue.FAILED,
            exitCode = buildExitCode,
            startedAtEpochMs = 1000L,
            endedAtEpochMs = 1100L,
        )

        override suspend fun listTools(): List<ToolStatusInfo> = emptyList()
        override suspend fun refreshTools(): List<ToolStatusInfo> = emptyList()
        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()
        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true
        override suspend fun downloadFile(remotePath: String): ByteArray? = null
        override suspend fun shutdown(): Boolean = true
    }

    private class StubCliExecutor : WslCliExecutor() {
        val executedCommands = mutableListOf<String>()

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?
        ): CliExecutionResult {
            val cmd = command.joinToString(" ")
            executedCommands.add(cmd)
            return CliExecutionResult(0, "ok", "")
        }
    }

    @Test
    fun testVerificationRequiresExitCodeZeroAndDoesNotInfersSuccessFromStdoutAlone() = runTest {
        val transport = SpyRemoteTransport(
            testXExitCode = 0,
            helpExitCode = 1, // Non-zero exit code, but non-empty stdout!
            helpOutput = "Syntax error or usage text on failure"
        )
        val provisioner = WslToolchainProvisioner(
            supervisor = SpyDaemonSupervisor(),
            transport = transport,
            repository = inMemoryToolchainRepository(),
            cli = StubCliExecutor(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val status = provisioner.testTool("lpmake")
        assertTrue(
            status is SetupOutcome.Failed,
            "Tool test must not infer success from non-empty stdout when exit code is non-zero (FR-009)",
        )

        // When exit code is 0, status must be SUCCESS
        transport.helpExitCode = 0
        val successStatus = provisioner.testTool("lpmake")
        assertTrue(successStatus is SetupOutcome.Succeeded)
    }

    @Test
    fun testVerificationFailsWhenBinaryDoesNotExist() = runTest {
        val transport = SpyRemoteTransport(
            testXExitCode = 1, // Binary does not exist
        )
        val provisioner = WslToolchainProvisioner(
            supervisor = SpyDaemonSupervisor(),
            transport = transport,
            repository = inMemoryToolchainRepository(),
            cli = StubCliExecutor(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val status = provisioner.testTool("lpmake")
        assertTrue(status is SetupOutcome.Failed, "Tool test must fail when binary is not found or not executable")
    }

    @Test
    fun testRepairRequiresSupportedRecipeOtherwiseRejectsAction() = runTest {
        val transport = SpyRemoteTransport()
        val provisioner = WslToolchainProvisioner(
            supervisor = SpyDaemonSupervisor(),
            transport = transport,
            repository = inMemoryToolchainRepository(),
            cli = StubCliExecutor(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        // "gh" has no supported compilation/acquisition recipe in the toolchain engine
        val unsupportedResult = provisioner.repairTool("gh")
        assertFalse(
            unsupportedResult,
            "Repair requires a supported recipe; otherwise the action is unavailable with a reason (FR-009)",
        )

        // "lpmake" is supported
        val supportedResult = provisioner.repairTool("lpmake")
        assertTrue(supportedResult, "Supported recipe must execute and succeed when build succeeds")
    }

    @Test
    fun testSingleFlightMutationBlocksConcurrentToolRepair() = runTest {
        val transport = SpyRemoteTransport()
        val coordinator = SetupOperationCoordinator()
        val provisioner = WslToolchainProvisioner(
            supervisor = SpyDaemonSupervisor(),
            transport = transport,
            repository = inMemoryToolchainRepository(),
            cli = StubCliExecutor(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            coordinator = coordinator,
        )

        // A real operation holds the environment owner in the shared coordinator; repair must be
        // refused by admission (Busy), not by a flag the test flips.
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val ownerThread = Thread {
            kotlinx.coroutines.runBlocking {
                coordinator.withAdmission("Ubuntu", "setup-in-flight", "FULL_SETUP") {
                    started.countDown()
                    release.await()
                    SetupOutcome.Succeeded()
                }
            }
        }
        ownerThread.start()
        try {
            started.await()
            assertTrue(coordinator.isBusy("Ubuntu"))

            val result = provisioner.repairTool("lpmake")
            assertFalse(
                result,
                "At most one setup mutation is active per environment; concurrent repair must be blocked",
            )
            assertTrue(transport.startedRuns.isEmpty(), "A refused repair must not start any build run")
            assertEquals("setup-in-flight", coordinator.currentOwner("Ubuntu")?.operationId)
        } finally {
            release.countDown()
            ownerThread.join()
        }
        assertFalse(coordinator.isBusy("Ubuntu"))
    }

    @Test
    fun testFailureReleasesBusyStateInFinally() = runTest {
        val transport = SpyRemoteTransport(
            buildExitCode = 1, // Build fails
        )
        val provisioner = WslToolchainProvisioner(
            supervisor = SpyDaemonSupervisor(),
            transport = transport,
            repository = inMemoryToolchainRepository(),
            cli = StubCliExecutor(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val result = provisioner.repairTool("lpmake")
        assertFalse(result)
        assertFalse(provisioner.state.value.isBusy, "Busy state must be released after repair failure")
        assertFalse(provisioner.state.value.isRunning)
    }
}
