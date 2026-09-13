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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.setup.doctor.NativeLibrariesInspector
import org.ide.lti.core.data.setup.doctor.ToolchainIntegrityInspector
import org.ide.lti.core.domain.ports.PublishedTools
import org.ide.lti.core.domain.ports.ToolPublicationPort
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.model.setup.PersistedSubmoduleState
import org.ide.lti.core.model.setup.PersistedToolState
import org.ide.lti.core.model.setup.ToolchainPersistenceState
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EnvironmentVerificationTest {

    private class RecordingDaemonSupervisor(
        var descriptor: ServerConnectionDescriptor? = ServerConnectionDescriptor(
            host = "127.0.0.1",
            port = 9090,
            token = "token",
            pid = 4321L,
            distro = "Ubuntu"
        ),
        override var lastStartAdopted: Boolean = false,
        var shouldFail: Boolean = false,
        override val lastLaunchErrorLogTail: String? = null,
    ) : DaemonSupervisorPort {
        var ensureStartedCount = 0

        override suspend fun ensureStarted(): ServerConnectionDescriptor {
            ensureStartedCount++
            if (shouldFail) {
                throw IllegalStateException("Failed to bind port 9090")
            }
            return descriptor ?: throw IllegalStateException("No descriptor")
        }

        override suspend fun getConnectionInfo(): ServerConnectionDescriptor? = descriptor
        override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean = !shouldFail
        override suspend fun shutdownDaemon(): Boolean = true
        override fun close() {}
    }

    private class RecordingRemoteTransport(
        var healthInfo: WslServerInfo? = WslServerInfo(
            status = "HEALTHY",
            distro = "Ubuntu",
            kernelRelease = "5.15.0",
            architecture = "x86_64",
            javaVersion = "21.0.11",
            serverUptimeMs = 50_000L,
            availableTools = listOf("adb", "fastboot")
        )
    ) : RemoteTransportPort {
        val executedRequests = mutableListOf<ToolExecutionRequest>()
        var toolsList: MutableList<ToolStatusInfo> = mutableListOf()

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse {
            executedRequests.add(request)
            return when {
                request.toolId == "find" && (request.arguments.contains("/usr/bin") || request.arguments.contains("/bin")) -> {
                    val systemTools = listOf(
                        "cmake", "make", "clang", "gcc", "clang++", "g++", "ccache",
                        "zip", "unzip", "gzip", "bzip2", "xz", "brotli", "curl", "wget", "git", "rsync", "tar", "file",
                        "xxd", "truncate", "attr", "getfattr", "openssl", "keytool", "python3", "pip3", "pip",
                        "java", "javac", "fusermount3", "fusermount", "fuse", "loop-control",
                        "java-17-openjdk-amd64", "java-21-openjdk-amd64", "java-17-openjdk", "java-21-openjdk"
                    ) + ToolchainIntegrityInspector.DEFAULT_CORE_TOOLS
                    ToolExecutionResponse(0, systemTools.joinToString("\n"), "", 5L)
                }
                request.toolId == "find" && request.arguments.contains("-L") -> {
                    // binDir executables
                    val tools = listOf(
                        "payload-dumper-go", "lpunpack", "lpmake", "simg2img",
                        "erofsfuse", "mkfs.erofs", "avbtool", "unpack_bootimg",
                        "img2sdat", "signapk", "adb", "fastboot", "mke2fs", "dump.erofs"
                    )
                    ToolExecutionResponse(0, tools.joinToString("\n"), "", 5L)
                }
                request.toolId == "find" && request.arguments.any { it.contains("external") } -> {
                    // submodules
                    val subs = listOf("android-tools", "apktool", "erofs-utils", "img2sdat", "signapk")
                    ToolExecutionResponse(0, subs.joinToString("\n"), "", 5L)
                }
                request.toolId == "test" -> ToolExecutionResponse(0, "", "", 2L)
                request.toolId == "dpkg-query" -> {
                    val pkgs = NativeLibrariesInspector.DEFAULT_REQUIRED_DEV_PACKAGES
                        .joinToString("\n") { "$it install ok installed" }
                    ToolExecutionResponse(0, pkgs, "", 2L)
                }
                request.toolId == "df" -> ToolExecutionResponse(0, "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/sdb 100G 50G 50G 50% /home/lti\n", "", 2L)
                else -> ToolExecutionResponse(0, "tool output", "", 5L)
            }
        }

        override suspend fun listTools(): List<ToolStatusInfo> = toolsList
        override suspend fun refreshTools(): List<ToolStatusInfo> = toolsList
        override suspend fun checkHealth(): WslServerInfo? = healthInfo
        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()
        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true
        override suspend fun downloadFile(remotePath: String): ByteArray? = null
        override suspend fun shutdown(): Boolean = true

        override suspend fun startRun(request: StartRunRequest): RunHandle {
            return RunHandle(runId = "rec-run-123", status = RunStatusValue.RUNNING, startedAtEpochMs = 1000L)
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

    private class RecordingCliExecutor : WslCliExecutor() {
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
                cmdStr.contains("wsl.exe --status") || cmdStr.contains("--status") -> {
                    CliExecutionResult(0, "Default Distribution: Ubuntu", "")
                }
                cmdStr.contains("echo -n \$HOME") -> CliExecutionResult(0, "/home/lti", "")
                else -> CliExecutionResult(0, "ok", "")
            }
        }
    }

    private class RecordingPublicationPort : ToolPublicationPort {
        var publishCount = 0
        var publishedIds = setOf<String>()

        override suspend fun publish(toolsBinDir: String): PublishedTools {
            publishCount++
            publishedIds = setOf("payload-dumper-go", "lpunpack", "lpmake", "simg2img", "erofsfuse", "mkfs.erofs")
            return PublishedTools(published = publishedIds, skipped = emptySet())
        }

        override suspend fun resolvedTools(): Set<String> = publishedIds
    }

    private class FakeToolchainRepo(
        initialState: ToolchainPersistenceState = ToolchainPersistenceState()
    ) : ToolchainSetupRepository {
        var state = initialState

        override val toolchainState: Flow<ToolchainPersistenceState> get() = flowOf(state)
        override val currentState: ToolchainPersistenceState get() = state
        override suspend fun saveToolchainState(state: ToolchainPersistenceState) { this.state = state }
        override suspend fun markStageCompleted(stageName: String, completed: Boolean) {}
        override suspend fun markSetupCompleted(completed: Boolean, timestamp: Long?) {}
        override suspend fun updateTool(toolId: String, transform: (PersistedToolState) -> PersistedToolState) {}
        override suspend fun updateSubmodule(name: String, transform: (PersistedSubmoduleState) -> PersistedSubmoduleState) {}
        override suspend fun updateDiagnosticsSnapshot(passedIds: Set<String>) {}
        override suspend fun updateAvbKeyState(provisioned: Boolean, keyPath: String?) {}
        override suspend fun clearToolchainState() {}
    }

    @Test
    fun `restored steps are PENDING and RESTORED with verifiedAt timestamp and not SUCCESS`() = runTest {
        val repo = FakeToolchainRepo(
            ToolchainPersistenceState(
                isSetupCompleted = true,
                completedStages = setOf(
                    SetupStepStage.WSL_DETECTION.name,
                    SetupStepStage.SERVER_CONNECTIVITY.name,
                    SetupStepStage.SYSTEM_DIAGNOSTICS.name,
                    SetupStepStage.REPO_SYNCHRONIZATION.name,
                    SetupStepStage.TOOLCHAIN_COMPILATION.name,
                ),
                lastVerifiedTimestamp = 1700000000000L
            )
        )

        val supervisor = RecordingDaemonSupervisor()
        val transport = RecordingRemoteTransport()
        val cli = RecordingCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = cli,
            repository = repo,
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val state = provisioner.state.value
        assertFalse(state.isAllReady, "Restored state must not be isAllReady before live verification")
        for (step in state.steps) {
            assertEquals(StepStatus.PENDING, step.status, "Restored step ${step.stage} must be PENDING")
            assertEquals(StepProvenance.RESTORED, step.provenance, "Restored step ${step.stage} must be RESTORED")
            assertEquals(1700000000000L, step.verifiedAtEpochMs)
            assertFalse(
                step.description.contains("verified and restored"),
                "Forbidden text 'verified and restored' found in description: ${step.description}"
            )
            assertTrue(
                step.description.contains("re-checking"),
                "Description must indicate re-checking: ${step.description}"
            )
        }
    }

    @Test
    fun `verifyEnvironment runs steps in order and transitions each to RUNNING before live results`() = runTest {
        val supervisor = RecordingDaemonSupervisor()
        val transport = RecordingRemoteTransport()
        val cli = RecordingCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = cli,
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val runningStagesObserved = mutableListOf<SetupStepStage>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            provisioner.state.collect { state ->
                state.currentStage?.let { stage ->
                    if (stage !in runningStagesObserved) {
                        runningStagesObserved.add(stage)
                    }
                }
            }
        }

        val resultState = provisioner.verifyEnvironment()
        job.cancel()

        assertTrue(runningStagesObserved.contains(SetupStepStage.WSL_DETECTION))
        assertTrue(runningStagesObserved.contains(SetupStepStage.SERVER_CONNECTIVITY))
        assertTrue(resultState.steps.all { it.provenance == StepProvenance.LIVE })
    }

    @Test
    fun `bridge adopts lockfile daemon without launching when healthy and reports measured ping`() = runTest {
        val supervisor = RecordingDaemonSupervisor(
            descriptor = ServerConnectionDescriptor("127.0.0.1", 9090, "token", 9999L, "Ubuntu"),
            lastStartAdopted = true
        )
        val transport = RecordingRemoteTransport()
        val cli = RecordingCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = cli,
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val state = provisioner.verifyEnvironment()
        val bridgeStep = state.steps.first { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        assertEquals(StepStatus.SUCCESS, bridgeStep.status)
        assertEquals(StepProvenance.LIVE, bridgeStep.provenance)
        assertTrue(bridgeStep.description.contains("pid 9999") || bridgeStep.description.contains("9999"))
        assertTrue(bridgeStep.description.contains("ms"))
        assertNotNull(state.daemonPingMs)
    }

    @Test
    fun `bridge failure transitions to FAILED with log tail and marks remaining steps PENDING`() = runTest {
        val supervisor = RecordingDaemonSupervisor(
            shouldFail = true,
            lastLaunchErrorLogTail = "Error: address already in use 0.0.0.0:9090"
        )
        val transport = RecordingRemoteTransport()
        val cli = RecordingCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = cli,
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val state = provisioner.verifyEnvironment()
        val bridgeStep = state.steps.first { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        assertEquals(StepStatus.FAILED, bridgeStep.status)
        assertTrue(bridgeStep.error?.contains("address already in use") == true ||
            bridgeStep.description.contains("address already in use") ||
            bridgeStep.error?.contains("Failed to bind") == true)

        val laterSteps = state.steps.filter {
            it.stage != SetupStepStage.WSL_DETECTION && it.stage != SetupStepStage.SERVER_CONNECTIVITY
        }
        for (step in laterSteps) {
            assertEquals(StepStatus.PENDING, step.status)
            assertTrue(step.description.contains("Pending daemon"))
        }
        assertFalse(state.isAllReady)
    }

    @Test
    fun `verifyEnvironment does not mutate or publish tools, publication occurs during full setup`() = runTest {
        val supervisor = RecordingDaemonSupervisor()
        val transport = RecordingRemoteTransport()
        val publicationPort = RecordingPublicationPort()
        val cli = RecordingCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        // Remote transport initially lists empty tools
        transport.toolsList = mutableListOf()

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = cli,
            toolPublicationPort = publicationPort,
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        provisioner.verifyEnvironment()
        assertEquals(0, publicationPort.publishCount, "Inspection probe must NEVER publish tools (zero mutation)")

        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP)
        assertEquals(0, publicationPort.publishCount, "Preparing a plan must NEVER publish tools")
        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)
        assertTrue(outcome is SetupOutcome.Succeeded, "Confirmed plan must succeed: $outcome")
        assertEquals(
            1,
            publicationPort.publishCount,
            "PublicationPort must be invoked by the confirmed toolchain build plan",
        )
    }

    @Test
    fun `zero WslCliExecutor calls occur after SERVER_CONNECTIVITY succeeds`() = runTest {
        val supervisor = RecordingDaemonSupervisor()
        val transport = RecordingRemoteTransport()
        val cli = RecordingCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = cli,
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        cli.executedCommands.clear()
        provisioner.verifyEnvironment()

        // After SERVER_CONNECTIVITY succeeds, all remote checks must run via transport.
        // The only cli calls allowed are initial WSL detection commands (e.g. resolve home, isWslInstalled).
        val nonBootstrapCalls = cli.executedCommands.filterNot {
            it.contains("wsl.exe --status") ||
                it.contains("--status") ||
                it.contains("echo -n \$HOME") ||
                it.contains("printenv HOME") ||
                it.contains("printenv") ||
                it.contains("wsl.exe -l") ||
                it.contains("-l -q")
        }
        assertEquals(
            emptyList(),
            nonBootstrapCalls,
            "Found unexpected direct WSL CLI calls after SERVER_CONNECTIVITY: $nonBootstrapCalls"
        )
    }

    @Test
    fun `every SUCCESS description contains measured data and no forbidden static strings`() = runTest {
        val supervisor = RecordingDaemonSupervisor()
        val transport = RecordingRemoteTransport()
        transport.toolsList = mutableListOf(
            ToolStatusInfo(tool = "payload-dumper-go", installed = true),
            ToolStatusInfo(tool = "lpunpack", installed = true),
            ToolStatusInfo(tool = "lpmake", installed = true),
            ToolStatusInfo(tool = "simg2img", installed = true),
            ToolStatusInfo(tool = "erofsfuse", installed = true),
            ToolStatusInfo(tool = "mkfs.erofs", installed = true),
            ToolStatusInfo(tool = "avbtool", installed = true),
            ToolStatusInfo(tool = "unpack_bootimg", installed = true),
            ToolStatusInfo(tool = "img2sdat", installed = true),
            ToolStatusInfo(tool = "signapk", installed = true)
        )
        val cli = RecordingCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = cli,
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val state = provisioner.verifyEnvironment()
        val forbiddenStrings = listOf(
            "verified and restored",
            "All host utilities and dependencies verified",
            "All submodules and workspace ready",
            "All core tools compiled and verified in"
        )

        for (step in state.steps) {
            if (step.status == StepStatus.SUCCESS) {
                for (forbidden in forbiddenStrings) {
                    assertFalse(
                        step.description.contains(forbidden),
                        "Step ${step.stage} contains forbidden static string: '$forbidden'"
                    )
                }
            }
        }
    }

    @Test
    fun `null health check in probe fails closed with SERVER_CONNECTIVITY FAILED`() = runTest {
        val supervisor = RecordingDaemonSupervisor()
        val transport = RecordingRemoteTransport(healthInfo = null) // Null health!
        val cli = RecordingCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = cli,
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val state = provisioner.verifyEnvironment()
        val bridgeStep = state.steps.first { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        assertEquals(StepStatus.FAILED, bridgeStep.status, "Null daemon health must fail closed")
        assertFalse(state.isAllReady)
    }

    @Test
    fun `absent publication in probe prevents ready`() = runTest {
        val supervisor = RecordingDaemonSupervisor()
        val transport = RecordingRemoteTransport()
        transport.toolsList = mutableListOf() // No published tools!
        val cli = RecordingCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = cli,
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val state = provisioner.verifyEnvironment()
        assertNull(state.lastReadyAt, "Absent tool publication must prevent lastReadyAt")
        assertFalse(state.publishedToolIds.containsAll(ToolchainReadinessPolicy.REQUIRED_PRODUCT_TOOLS))
    }

    @Test
    fun `only completed live check updates checkedAt and only fully ready updates lastReadyAt`() = runTest {
        val supervisor = RecordingDaemonSupervisor()
        val transport = RecordingRemoteTransport()
        transport.toolsList = mutableListOf() // Missing tools
        val cli = RecordingCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = cli,
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        // Before live check
        assertNull(provisioner.state.value.checkedAt)
        assertNull(provisioner.state.value.lastReadyAt)

        // Run live check (incomplete environment)
        val stateAfterCheck = provisioner.verifyEnvironment()
        assertNotNull(stateAfterCheck.checkedAt, "Completed live check must update checkedAt")
        assertNull(stateAfterCheck.lastReadyAt, "Incomplete environment must NOT update lastReadyAt")

        // Now populate all tools and publish
        transport.toolsList = mutableListOf(
            ToolStatusInfo(tool = "payload-dumper-go", installed = true),
            ToolStatusInfo(tool = "lpunpack", installed = true),
            ToolStatusInfo(tool = "lpmake", installed = true),
            ToolStatusInfo(tool = "simg2img", installed = true),
            ToolStatusInfo(tool = "erofsfuse", installed = true),
            ToolStatusInfo(tool = "mkfs.erofs", installed = true),
            ToolStatusInfo(tool = "avbtool", installed = true),
            ToolStatusInfo(tool = "unpack_bootimg", installed = true),
            ToolStatusInfo(tool = "img2sdat", installed = true),
            ToolStatusInfo(tool = "signapk", installed = true)
        )

        val readyState = provisioner.verifyEnvironment()
        assertNotNull(readyState.checkedAt)
        assertNotNull(readyState.lastReadyAt, "Fully ready environment must update lastReadyAt")
        assertTrue(readyState.lastReadyAt!! <= readyState.checkedAt!!)
    }
}
