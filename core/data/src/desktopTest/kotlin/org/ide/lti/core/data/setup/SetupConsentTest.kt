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
import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
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
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.setup.doctor.DiagnosticContext
import org.ide.lti.core.data.setup.doctor.NativeLibrariesInspector
import org.ide.lti.core.data.setup.doctor.StorageHeadroomInspector
import org.ide.lti.core.data.setup.doctor.ToolchainIntegrityInspector
import org.ide.lti.core.domain.ports.PublishedTools
import org.ide.lti.core.domain.ports.ToolPublicationPort
import org.ide.lti.core.domain.setup.DiagnosticCategory
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SetupConsentTest {

    private class SpyDaemonSupervisor(
        var descriptor: ServerConnectionDescriptor? = ServerConnectionDescriptor("127.0.0.1", 9090, "token", 1234L, "Ubuntu"),
        var shouldThrow: Boolean = false,
    ) : DaemonSupervisorPort {
        var ensureStartedCount = 0

        override suspend fun getConnectionInfo(): ServerConnectionDescriptor? = descriptor
        override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean = !shouldThrow
        override suspend fun ensureStarted(): ServerConnectionDescriptor {
            ensureStartedCount++
            if (shouldThrow) throw IllegalStateException("Daemon start failure simulated")
            return descriptor ?: throw IllegalStateException("No descriptor")
        }
        override suspend fun shutdownDaemon(): Boolean = true
        override fun close() {}
    }

    private class SpyRemoteTransport(
        var dfBytes: Long = 100_000_000_000L,
        var dfFails: Boolean = false,
        var runIdGenerator: (String) -> String = { "durable-run-$it" }
    ) : RemoteTransportPort {
        val executedRequests = mutableListOf<ToolExecutionRequest>()
        val startedRuns = mutableListOf<StartRunRequest>()
        var toolsList: MutableList<ToolStatusInfo> = mutableListOf()

        override suspend fun checkHealth(): WslServerInfo? = WslServerInfo(
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
                request.toolId == "df" && dfFails -> ToolExecutionResponse(1, "", "df: failed to query mount", 2L)
                request.toolId == "df" -> {
                    val out = "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/root 200000000 50000000 60G 25% /home/lti\n"
                    ToolExecutionResponse(0, out, "", 2L)
                }
                request.toolId == "find" && (request.arguments.contains("/usr/bin") || request.arguments.contains("/bin")) -> {
                    val systemTools = listOf(
                        "cmake", "make", "clang", "gcc", "clang++", "g++", "ccache",
                        "zip", "unzip", "gzip", "bzip2", "xz", "brotli", "curl", "wget", "git", "rsync", "tar", "file",
                        "xxd", "truncate", "attr", "getfattr", "openssl", "keytool", "python3", "pip3", "pip",
                        "java", "javac", "fusermount3", "fusermount", "fuse", "loop-control",
                        "java-17-openjdk-amd64", "java-21-openjdk-amd64", "java-17-openjdk", "java-21-openjdk"
                    ) + ToolchainIntegrityInspector.DEFAULT_CORE_TOOLS
                    ToolExecutionResponse(0, systemTools.joinToString("\n"), "", 2L)
                }
                request.toolId == "find" && request.arguments.contains("-L") -> {
                    ToolExecutionResponse(0, "payload-dumper-go\nlpunpack\nlpmake\nsimg2img\nerofsfuse\nmkfs.erofs\navbtool\nunpack_bootimg\nimg2sdat\nsignapk\nadb\nfastboot", "", 2L)
                }
                request.toolId == "find" -> ToolExecutionResponse(0, "android-tools\napktool\nerofs-utils\nimg2sdat\nsignapk", "", 2L)
                request.toolId == "dpkg-query" -> {
                    val pkgs = NativeLibrariesInspector.DEFAULT_REQUIRED_DEV_PACKAGES
                        .joinToString("\n") { "$it install ok installed" }
                    ToolExecutionResponse(0, pkgs, "", 2L)
                }
                request.toolId == "test" -> ToolExecutionResponse(0, "", "", 1L)
                request.toolId == "printenv" -> ToolExecutionResponse(0, "/home/lti\n", "", 1L)
                else -> ToolExecutionResponse(0, "ok", "", 1L)
            }
        }

        override suspend fun listTools(): List<ToolStatusInfo> = toolsList
        override suspend fun refreshTools(): List<ToolStatusInfo> = toolsList
        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()
        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true
        override suspend fun downloadFile(remotePath: String): ByteArray? = null
        override suspend fun shutdown(): Boolean = true

        override suspend fun startRun(request: StartRunRequest): RunHandle {
            startedRuns.add(request)
            val id = runIdGenerator(request.idempotencyKey ?: "key")
            return RunHandle(runId = id, status = RunStatusValue.RUNNING, startedAtEpochMs = 1000L)
        }

        override fun attachRun(runId: String, fromSequence: Long): Flow<SequencedStreamEvent> {
            val events = listOf(
                SequencedStreamEvent(1L, StreamEvent.OutputChunk("running...")),
                SequencedStreamEvent(2L, StreamEvent.ExecutionFinished(0, 100L))
            )
            return events.asFlow()
        }

        override suspend fun getRun(runId: String): RunStatus = RunStatus(
            runId = runId,
            status = RunStatusValue.COMPLETED,
            exitCode = 0,
            startedAtEpochMs = 1000L,
            endedAtEpochMs = 1100L
        )
    }

    private class SpyPublicationPort : ToolPublicationPort {
        var publishCount = 0
        var publishedBinDir: String? = null

        override suspend fun publish(toolsBinDir: String): PublishedTools {
            publishCount++
            publishedBinDir = toolsBinDir
            return PublishedTools(
                published = setOf("payload-dumper-go", "lpunpack", "lpmake", "simg2img", "erofsfuse", "mkfs.erofs"),
                skipped = emptySet()
            )
        }

        override suspend fun resolvedTools(): Set<String> = emptySet()
    }

    private class SpyCliExecutor(
        var dfExitCode: Int = 0,
        var dfOutput: String = "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/root 100G 50G 50G 50% /home/lti\n"
    ) : WslCliExecutor() {
        val executedCommands = mutableListOf<String>()

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?
        ): CliExecutionResult {
            val cmdStr = command.joinToString(" ")
            executedCommands.add(cmdStr)
            return when {
                cmdStr.contains("wsl.exe --status") || cmdStr.contains("--status") -> CliExecutionResult(0, "Default Distribution: Ubuntu", "")
                cmdStr.contains("echo -n \$HOME") -> CliExecutionResult(0, "/home/lti", "")
                command.firstOrNull() == "df" -> CliExecutionResult(dfExitCode, if (dfExitCode == 0) dfOutput else "", if (dfExitCode != 0) "df failed" else "")
                else -> CliExecutionResult(0, "ok", "")
            }
        }
    }

    @Test
    fun testProbeInspectionZeroInitialMutations() = runTest {
        val supervisor = SpyDaemonSupervisor()
        val transport = SpyRemoteTransport()
        val publicationPort = SpyPublicationPort()
        val cli = SpyCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = cli,
            toolPublicationPort = publicationPort,
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val probeResult = provisioner.verifyEnvironment()

        // Assert probe was inspection only
        assertEquals(0, publicationPort.publishCount, "Initial probe must NEVER trigger tool publication")
        assertFalse(probeResult.isBusy, "State must not remain busy after probe")
        assertNull(probeResult.currentStage, "Current stage must be cleared after probe")

        // No git clone commands during probe
        val hasGitClone = transport.executedRequests.any { it.toolId == "git" && it.arguments.contains("clone") } ||
            cli.executedCommands.any { it.contains("git clone") }
        assertFalse(hasGitClone, "Initial probe must NEVER clone git repositories")

        // No compiler invocations during probe
        val hasBuild = transport.executedRequests.any { it.toolId == "make" || it.toolId == "cmake" } ||
            cli.executedCommands.any { it.contains("cmake") || it.contains("make") }
        assertFalse(hasBuild, "Initial probe must NEVER run cmake or make compilation")

        // No package installation commands during probe
        val hasApt = transport.executedRequests.any { it.arguments.contains("apt-get") } ||
            cli.executedCommands.any { it.contains("apt-get") }
        assertFalse(hasApt, "Initial probe must NEVER execute apt-get install")
    }

    @Test
    fun testRealStorageHeadroomReadingsWithoutHardcodedFallback() = runTest {
        val cliSuccess = SpyCliExecutor(
            dfExitCode = 0,
            dfOutput = "Filesystem 1G-blocks Used Available Use% Mounted on\n/dev/root 100G 40G 60G 40% /home/lti\n"
        )
        val inspector = StorageHeadroomInspector(cliSuccess)
        val context = DiagnosticContext(distro = "Ubuntu", home = "/home/lti", binDir = "/home/lti/LtiRomTools/bin", binariesPresent = false)

        val resultSuccess = inspector.inspect(context)
        assertEquals(StepStatus.SUCCESS, resultSuccess.status)
        assertTrue(resultSuccess.detail.contains("60 GB free"))

        // When df command fails, StorageHeadroomInspector must return FAILED without fabricating 50L fallback
        val cliFail = SpyCliExecutor(dfExitCode = 1, dfOutput = "")
        val inspectorFail = StorageHeadroomInspector(cliFail)
        val resultFail = inspectorFail.inspect(context)

        assertEquals(StepStatus.FAILED, resultFail.status, "Failed df measurement must result in FAILED status")
        assertTrue(resultFail.detail.contains("Unable to measure available disk space"), "Detail must report failure instead of fabricated 50L: ${resultFail.detail}")
    }

    @Test
    fun testTargetedPerStageRetryExecutesOnlyRequestedStage() = runTest {
        val supervisor = SpyDaemonSupervisor()
        val transport = SpyRemoteTransport()
        val publicationPort = SpyPublicationPort()
        val cli = SpyCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = cli,
            toolPublicationPort = publicationPort,
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        // Retry compilation stage only
        provisioner.retryStep(SetupStepStage.TOOLCHAIN_COMPILATION)

        // Publication port is invoked during compilation retry
        assertEquals(1, publicationPort.publishCount, "Retrying TOOLCHAIN_COMPILATION must invoke tool publication")

        // Submodules sync was NOT triggered by targeted compilation retry
        val hasClone = transport.startedRuns.any { it.request.toolId == "git" }
        assertFalse(hasClone, "Retrying TOOLCHAIN_COMPILATION must NOT trigger git clone submodules")
    }

    @Test
    fun testFailureCleanupFinallyRestoresBusyState() = runTest {
        val supervisor = SpyDaemonSupervisor(shouldThrow = true)
        val transport = SpyRemoteTransport()
        val cli = SpyCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = cli,
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        provisioner.runFullSetup()

        val state = provisioner.state.value
        assertFalse(state.isBusy, "isBusy must be cleaned up to false in finally block")
        assertFalse(state.isRunning, "isRunning must be cleaned up to false in finally block")
        assertNull(state.currentStage, "currentStage must be cleared in finally block")
        assertEquals(StepStatus.FAILED, state.steps.first { it.stage == SetupStepStage.SERVER_CONNECTIVITY }.status)
    }

    @Test
    fun testDurableIdAttachOnReconnection() = runTest {
        val supervisor = SpyDaemonSupervisor()
        val transport = SpyRemoteTransport(runIdGenerator = { key -> "durable-$key-789" })
        val publicationPort = SpyPublicationPort()
        val cli = SpyCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = cli,
            toolPublicationPort = publicationPort,
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        // Execute full setup which triggers durable runs with idempotency keys
        provisioner.runFullSetup()

        val state = provisioner.state.value
        // Verify durable run IDs were recorded in stepRunIds and steps
        assertNotNull(state.stepRunIds[SetupStepStage.TOOLCHAIN_COMPILATION], "Durable run ID must be recorded for toolchain compilation")
        assertTrue(state.stepRunIds[SetupStepStage.TOOLCHAIN_COMPILATION]?.contains("durable-") == true)
        val compilationStep = state.steps.first { it.stage == SetupStepStage.TOOLCHAIN_COMPILATION }
        assertEquals(state.stepRunIds[SetupStepStage.TOOLCHAIN_COMPILATION], compilationStep.runId)
    }
}
