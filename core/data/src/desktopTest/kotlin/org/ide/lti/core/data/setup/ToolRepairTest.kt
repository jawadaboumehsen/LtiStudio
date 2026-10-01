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
import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolCapabilities
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ToolRepairTest {

    private class SpyDaemonSupervisor(
        var descriptor: ServerConnectionDescriptor? =
            ServerConnectionDescriptor("127.0.0.1", 9090, "token", 1234L, "Ubuntu"),
    ) : DaemonSupervisorPort {
        override suspend fun getConnectionInfo(): ServerConnectionDescriptor? = descriptor
        override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean = true
        override suspend fun ensureStarted(): ServerConnectionDescriptor =
            descriptor ?: throw IllegalStateException("No descriptor")
        override suspend fun shutdownDaemon(): Boolean = true
        override fun close() {}
    }

    private class SpyRemoteTransport(
        var testXExitCode: Int = 0,
        var helpExitCode: Int = 0,
        var helpOutput: String = "Usage: lpmake [options]",
        var buildExitCode: Int = 0,
        /** Exit code returned when a binary with this basename is executed directly (its probe). */
        val probeExitCodes: MutableMap<String, Int> = mutableMapOf(),
        /** Basenames reported by the `find` listing of the bin directory. */
        var installedBinaries: List<String> = emptyList(),
    ) : RemoteTransportPort {
        val executedRequests = mutableListOf<ToolExecutionRequest>()
        val startedRuns = mutableListOf<StartRunRequest>()

        override suspend fun checkHealth(): WslServerInfo = WslServerInfo(
            status = "UP",
            distro = "Ubuntu",
            kernelRelease = "5.15.0",
            architecture = "x86_64",
            javaVersion = "21.0.11",
            serverUptimeMs = 100_000L,
            availableTools = listOf("adb", "fastboot"),
        )

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse {
            executedRequests.add(request)
            return scriptedResponse(request)
        }

        @Suppress("CyclomaticComplexMethod") // one branch per scripted tool shape is the point of the spy
        private fun scriptedResponse(request: ToolExecutionRequest): ToolExecutionResponse {
            val basename = request.toolId.substringAfterLast('/')
            return when {
                request.toolId == "printenv" -> ToolExecutionResponse(0, "/home/lti\n", "", 1L)
                request.toolId == "find" -> ToolExecutionResponse(0, fakeBinListing(installedBinaries), "", 1L)
                basename in probeExitCodes -> ToolExecutionResponse(probeExitCodes.getValue(basename), "", "", 1L)
                request.toolId == "test" && request.arguments.contains("-x") -> {
                    ToolExecutionResponse(testXExitCode, "", "", 1L)
                }
                request.toolId == "test" -> ToolExecutionResponse(0, "", "", 1L)
                request.arguments.contains("--help") ||
                    request.toolId.endsWith("lpmake") ||
                    request.arguments.contains("-h") -> {
                    ToolExecutionResponse(helpExitCode, helpOutput, if (helpExitCode != 0) "Error: failed" else "", 1L)
                }
                request.toolId in listOf("cmake", "make") -> {
                    ToolExecutionResponse(
                        buildExitCode,
                        if (buildExitCode ==
                            0
                        ) {
                            "Build completed"
                        } else {
                            "Build error"
                        },
                        "",
                        1L,
                    )
                }
                // A healthy binary answers its inventory probe with an accepted exit code.
                basename in ToolCapabilities.SUPPORTED_TOOL_IDS -> {
                    ToolExecutionResponse(ToolCapabilities.acceptedExitCodesFor(basename).first(), "ok", "", 1L)
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

        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(emptyList())
        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(emptyList())
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
            user: String?,
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
            helpExitCode = 1,
            helpOutput = "Syntax error or usage text on failure",
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
    fun `a required tool failing its test revokes workspace readiness at once`() = runTest {
        val repository = inMemoryToolchainRepository()
        repository.markStageCompleted(org.ide.lti.core.domain.setup.SetupStepStage.TOOLCHAIN_COMPILATION.name, true)
        val provisioner = WslToolchainProvisioner(
            supervisor = SpyDaemonSupervisor(),
            // lpmake deleted or no longer executable
            transport = SpyRemoteTransport(testXExitCode = 1),
            repository = repository,
            cli = StubCliExecutor(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        provisioner.testTool("lpmake")

        val toolchain = provisioner.state.value.steps
            .first { it.stage == org.ide.lti.core.domain.setup.SetupStepStage.TOOLCHAIN_COMPILATION }
        assertEquals(StepStatus.FAILED, toolchain.status, "admission reads the stage, so the stage must fail")
        assertFalse(provisioner.state.value.canLaunchWorkspace)
        assertFalse(
            org.ide.lti.core.domain.setup.SetupStepStage.TOOLCHAIN_COMPILATION.name in
                repository.currentState.completedStages,
            "a restart must not restore readiness",
        )
    }

    @Test
    fun testVerificationFailsWhenBinaryDoesNotExist() = runTest {
        val transport = SpyRemoteTransport(
            testXExitCode = 1,
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

    private suspend fun WslToolchainProvisioner.repairTool(toolId: String): SetupOutcome {
        val plan = prepare(SetupPlanKind.REPAIR_TOOL, targetId = toolId)
        return confirm(plan.planId, plan.revisionHash)
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

        // A tool no recipe builds or downloads
        val unsupportedResult = provisioner.repairTool("custom-vendor-tool")
        assertTrue(
            unsupportedResult is SetupOutcome.Failed,
            "Repair requires a supported recipe; otherwise the action is unavailable with a reason (FR-009)",
        )

        val supportedResult = provisioner.repairTool("lpmake")
        assertTrue(
            supportedResult is SetupOutcome.Succeeded,
            "Supported recipe must execute and succeed when build succeeds",
        )
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
            assertTrue(
                result is SetupOutcome.Busy,
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
    fun testAdbSentinelPresentWithBrokenSiblingRebuildsAndroidToolsGroup() = runTest {
        val transport = SpyRemoteTransport()
        val repo = inMemoryToolchainRepository()
        // adb sentinel is recorded as compiled and verified
        repo.updateTool("adb") {
            it.copy(isCompiled = true, isVerified = true, binaryPath = "/home/lti/LtiRomTools/bin/adb")
        }
        val coordinator = SetupOperationCoordinator(repo)
        val provisioner = WslToolchainProvisioner(
            supervisor = SpyDaemonSupervisor(),
            transport = transport,
            repository = repo,
            cli = StubCliExecutor(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            coordinator = coordinator,
        )

        // Repairing lpunpack must force rebuilding android-tools despite adb sentinel
        val result = provisioner.repairTool("lpunpack")
        assertTrue(result is SetupOutcome.Succeeded, "repair must succeed: $result")
        assertTrue(
            transport.startedRuns.any { it.idempotencyKey.contains("android-tools") },
            "android-tools recipe group must be rebuilt even when adb sentinel is present (FR-009)",
        )
        assertFalse(
            transport.startedRuns.any { it.idempotencyKey.contains("erofs-utils") },
            "Unrelated recipe group (erofs-utils) must not be built when repairing android-tools (FR-009)",
        )
    }

    @Test
    fun testErofsSentinelPresentWithBrokenSiblingRebuildsErofsUtilsGroup() = runTest {
        val transport = SpyRemoteTransport()
        val repo = inMemoryToolchainRepository()
        // mkfs.erofs sentinel is recorded as compiled and verified
        repo.updateTool("mkfs.erofs") {
            it.copy(isCompiled = true, isVerified = true, binaryPath = "/home/lti/LtiRomTools/bin/mkfs.erofs")
        }
        val coordinator = SetupOperationCoordinator(repo)
        val provisioner = WslToolchainProvisioner(
            supervisor = SpyDaemonSupervisor(),
            transport = transport,
            repository = repo,
            cli = StubCliExecutor(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            coordinator = coordinator,
        )

        // Repairing dump.erofs must force rebuilding erofs-utils despite mkfs.erofs sentinel
        val result = provisioner.repairTool("dump.erofs")
        assertTrue(result is SetupOutcome.Succeeded, "repair must succeed: $result")
        assertTrue(
            transport.startedRuns.any { it.idempotencyKey.contains("erofs-utils") },
            "erofs-utils recipe group must be rebuilt even when mkfs.erofs sentinel is present (FR-009)",
        )
        assertFalse(
            transport.startedRuns.any { it.idempotencyKey.contains("android-tools") },
            "Unrelated recipe group (android-tools) must not be built when repairing erofs-utils (FR-009)",
        )
    }

    @Test
    fun testForcedCacheInvalidationOfOnlyOwningGroup() = runTest {
        val transport = SpyRemoteTransport()
        val repo = inMemoryToolchainRepository()
        // Both groups initially have verified tools
        repo.updateTool("adb") {
            it.copy(isCompiled = true, isVerified = true, binaryPath = "/home/lti/LtiRomTools/bin/adb")
        }
        repo.updateTool("mkfs.erofs") {
            it.copy(isCompiled = true, isVerified = true, binaryPath = "/home/lti/LtiRomTools/bin/mkfs.erofs")
        }

        val coordinator = SetupOperationCoordinator(repo)
        val provisioner = WslToolchainProvisioner(
            supervisor = SpyDaemonSupervisor(),
            transport = transport,
            repository = repo,
            cli = StubCliExecutor(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            coordinator = coordinator,
        )

        // Repair lpunpack (belongs to android-tools)
        provisioner.repairTool("lpunpack")

        // android-tools tools are invalidated / verified as part of repair
        // Unrelated erofs-utils tool must retain its verified status
        val erofsTool = repo.currentState.tools["mkfs.erofs"]
        assertTrue(
            erofsTool?.isVerified == true,
            "mkfs.erofs must remain verified when android-tools is repaired (FR-009)",
        )
    }

    @Test
    fun testAcceptedExitCodesComeFromDomainInventory() = runTest {
        // simg2img has no help flag: the inventory probes it bare and accepts exit(-1) == 255.
        val transport = SpyRemoteTransport(probeExitCodes = mutableMapOf("simg2img" to 255))
        val provisioner = WslToolchainProvisioner(
            supervisor = SpyDaemonSupervisor(),
            transport = transport,
            repository = inMemoryToolchainRepository(),
            cli = StubCliExecutor(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val accepted = provisioner.testTool("simg2img")
        assertTrue(accepted is SetupOutcome.Succeeded, "Inventory-accepted exit code 255 must verify simg2img")
        val probe = transport.executedRequests.last { it.toolId.endsWith("/simg2img") }
        assertEquals(
            ToolCapabilities.probeArgumentsFor("simg2img"),
            probe.arguments,
            "The adapter must execute the domain probe argv, not a hard-coded --help",
        )

        // An exit code the inventory does not accept is a failed verification even when it is 0.
        transport.probeExitCodes["simg2img"] = 0
        val rejected = provisioner.testTool("simg2img")
        assertTrue(rejected is SetupOutcome.Failed, "Exit 0 is not an accepted code for simg2img")
        val failedTool = provisioner.state.value.toolsMatrix.first { it.id == "simg2img" }
        assertEquals(StepStatus.FAILED, failedTool.status)
        assertFalse(failedTool.lastTested == "Just now", "A rejected probe must never be labelled Just now")
    }

    @Test
    fun testFileDiscoveryAfterRepairPreservesFailedProbeOfUnrelatedTool() = runTest {
        // lpmake fails its execution probe; the binary still exists on disk.
        val transport = SpyRemoteTransport(
            helpExitCode = 1,
            installedBinaries = listOf("lpmake", "mkfs.erofs", "dump.erofs", "fsck.erofs"),
        )
        val repo = inMemoryToolchainRepository()
        val coordinator = SetupOperationCoordinator(repo)
        val provisioner = WslToolchainProvisioner(
            supervisor = SpyDaemonSupervisor(),
            transport = transport,
            repository = repo,
            cli = StubCliExecutor(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            coordinator = coordinator,
        )
        assertTrue(provisioner.testTool("lpmake") is SetupOutcome.Failed)
        assertEquals(StepStatus.FAILED, provisioner.state.value.toolsMatrix.first { it.id == "lpmake" }.status)

        // Repairing the erofs group runs the post-plan file discovery over the whole bin directory.
        transport.helpExitCode = 0
        val repair = provisioner.repairTool("mkfs.erofs")
        assertTrue(repair is SetupOutcome.Succeeded, "erofs repair must succeed: $repair")

        val lpmake = provisioner.state.value.toolsMatrix.first { it.id == "lpmake" }
        assertEquals(
            StepStatus.FAILED,
            lpmake.status,
            "File discovery must never overwrite a failed execution verification (FR-010)",
        )
        assertEquals("/home/lti/LtiRomTools/bin/lpmake", lpmake.path, "Installed remains a separate, updated fact")
        assertFalse(lpmake.lastTested == "Just now")
    }

    @Test
    fun testRepairVerifiesEverySiblingOfTheOwningGroupAndOnlyThatGroup() = runTest {
        val transport = SpyRemoteTransport(installedBinaries = listOf("mkfs.erofs", "dump.erofs", "fsck.erofs"))
        val repo = inMemoryToolchainRepository()
        val coordinator = SetupOperationCoordinator(repo)
        val provisioner = WslToolchainProvisioner(
            supervisor = SpyDaemonSupervisor(),
            transport = transport,
            repository = repo,
            cli = StubCliExecutor(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            coordinator = coordinator,
        )

        val outcome = provisioner.repairTool("dump.erofs")
        assertTrue(outcome is SetupOutcome.Succeeded, "repair must succeed: $outcome")

        val probedBasenames = transport.executedRequests
            .filter { it.toolId.startsWith("/home/lti/LtiRomTools/bin/") }
            .map { it.toolId.substringAfterLast('/') }
            .toSet()
        val erofsSiblings = ToolCapabilities.toolsForRecipeGroup(ToolCapabilities.GROUP_EROFS_UTILS)
            .filter { id -> provisioner.state.value.toolsMatrix.any { it.id == id } }
            .toSet()
        assertEquals(erofsSiblings, probedBasenames, "Every sibling of the owning group is probed after a repair")
        assertTrue(
            ToolCapabilities.toolsForRecipeGroup(ToolCapabilities.GROUP_ANDROID_TOOLS).none { it in probedBasenames },
            "Tools outside the owning group are never probed by a repair",
        )
        for (sibling in erofsSiblings) {
            assertEquals(true, repo.currentState.tools[sibling]?.isVerified, "$sibling is verified by a real probe")
        }
    }

    @Test
    fun testFailureReleasesBusyStateInFinally() = runTest {
        val transport = SpyRemoteTransport(
            buildExitCode = 1,
        )
        val provisioner = WslToolchainProvisioner(
            supervisor = SpyDaemonSupervisor(),
            transport = transport,
            repository = inMemoryToolchainRepository(),
            cli = StubCliExecutor(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val result = provisioner.repairTool("lpmake")
        assertTrue(result is SetupOutcome.Failed)
        assertFalse(provisioner.state.value.isBusy, "Busy state must be released after repair failure")
        assertFalse(provisioner.state.value.isRunning)
    }
}
