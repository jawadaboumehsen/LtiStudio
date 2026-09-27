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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import com.russhwolf.settings.MapSettings
import io.ltirom.tooling.core.remote.ToolListResult
import org.ide.lti.core.domain.ports.PublicationReport
import org.ide.lti.core.domain.ports.ToolPublicationPort
import org.ide.lti.core.domain.ports.ToolRegistryResult
import org.ide.lti.core.domain.ports.ToolSource
import org.ide.lti.core.domain.ports.ToolStatus
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.model.setup.AttemptStatus
import org.ide.lti.core.model.setup.PersistedSetupPlan
import org.ide.lti.core.model.setup.PersistedUserHandoff
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WslToolchainProvisionerTest {

    private class FakeDaemonSupervisor(
        var descriptor: ServerConnectionDescriptor? = ServerConnectionDescriptor("127.0.0.1", 9090, "token", 1234L, "Ubuntu"),
        var isHealthyResult: Boolean = true,
        var shouldFailEnsureStarted: Boolean = false,
        var onEnsureStarted: (suspend () -> Unit)? = null
    ) : DaemonSupervisorPort {
        var getConnectionInfoCount = 0
        var isHealthyCount = 0
        var ensureStartedCount = 0

        override suspend fun getConnectionInfo(): ServerConnectionDescriptor? {
            getConnectionInfoCount++
            return descriptor
        }

        override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean {
            isHealthyCount++
            return isHealthyResult
        }

        override suspend fun ensureStarted(): ServerConnectionDescriptor {
            ensureStartedCount++
            onEnsureStarted?.invoke()
            if (shouldFailEnsureStarted) {
                throw IllegalStateException("Failed to launch daemon")
            }
            return descriptor ?: throw IllegalStateException("No descriptor")
        }

        override suspend fun shutdownDaemon(): Boolean = true
        override fun close() {}
    }

    private class FakeRemoteTransport(
        val fakeCli: FakeWslCliExecutor? = null,
        var healthInfo: WslServerInfo? = WslServerInfo(
            status = "UP",
            distro = "Ubuntu",
            kernelRelease = "5.15.0",
            architecture = "x86_64",
            javaVersion = "21.0.11",
            serverUptimeMs = 100_000L,
            availableTools = listOf("adb", "fastboot")
        ),
        var publishedTools: MutableList<ToolStatusInfo> = mutableListOf(
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
    ) : RemoteTransportPort {
        var checkHealthCount = 0

        override suspend fun checkHealth(): WslServerInfo? {
            checkHealthCount++
            return healthInfo
        }

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse {
            if (fakeCli != null) {
                val fullCmd = listOf(request.toolId) + request.arguments
                val res = fakeCli.execute("Ubuntu", fullCmd)
                return ToolExecutionResponse(res.exitCode, res.output, res.error, 10L)
            }
            return when {
                request.toolId == "find" && request.arguments.any { it.contains("/dev") } -> {
                    val devNodes = listOf("fuse", "loop-control", "null", "zero", "random", "urandom")
                    ToolExecutionResponse(0, devNodes.joinToString("\n"), "", 10L)
                }
                request.toolId == "find" && request.arguments.contains("-L") -> {
                    val output = listOf(
                        "payload-dumper-go", "lpunpack", "lpmake", "simg2img",
                        "erofsfuse", "mkfs.erofs", "avbtool", "unpack_bootimg",
                        "img2sdat", "signapk", "adb", "fastboot"
                    ).joinToString("\n")
                    ToolExecutionResponse(0, output, "", 10L)
                }
                request.toolId == "find" && request.arguments.any { it.contains("external") } -> {
                    val output = listOf("android-tools", "apktool", "erofs-utils", "img2sdat", "signapk").joinToString("\n")
                    ToolExecutionResponse(0, output, "", 10L)
                }
                request.toolId == "test" -> ToolExecutionResponse(0, "", "", 10L)
                request.toolId == "dpkg-query" -> ToolExecutionResponse(0, "all ok installed", "", 2L)
                request.toolId == "df" -> ToolExecutionResponse(0, "Filesystem 100G 50G 50G 50% /home/lti\n", "", 2L)
                request.toolId == "mkfs.erofs" -> ToolExecutionResponse(0, "mkfs.erofs 1.7.1 --fs-config-file", "", 10L)
                else -> ToolExecutionResponse(0, "ok", "", 10L)
            }
        }

        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()

        override suspend fun startRun(request: StartRunRequest): RunHandle {
            if (fakeCli != null) {
                if (request.request.toolId == "git" && request.request.arguments.firstOrNull() == "clone") {
                    val targetDir = request.request.arguments.lastOrNull() ?: ""
                    if (targetDir.isNotBlank()) {
                        fakeCli.existingPaths.add(targetDir)
                    }
                }
            }
            return RunHandle(runId = "fake-run-${request.idempotencyKey}", status = RunStatusValue.RUNNING, startedAtEpochMs = 1000L)
        }

        override fun attachRun(runId: String, fromSeq: Long): Flow<SequencedStreamEvent> = listOf(
            SequencedStreamEvent(1L, StreamEvent.OutputChunk("running...")),
            SequencedStreamEvent(2L, StreamEvent.ExecutionFinished(0, 100L))
        ).asFlow()

        override suspend fun getRun(runId: String): RunStatus = RunStatus(
            runId = runId,
            status = RunStatusValue.COMPLETED,
            exitCode = 0,
            startedAtEpochMs = 1000L,
            endedAtEpochMs = 1100L
        )

        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(publishedTools)
        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(publishedTools)
        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true
        override suspend fun downloadFile(remotePath: String): ByteArray? = null
        override suspend fun shutdown(): Boolean = true
    }

    private class FakeToolPublicationPort(
        var publishedResult: PublicationReport = PublicationReport(
            requested = ToolchainReadinessPolicy.REQUIRED_PRODUCT_TOOLS,
            registered = ToolchainReadinessPolicy.REQUIRED_PRODUCT_TOOLS,
            failed = emptyMap(),
            pruned = emptySet()
        )
    ) : ToolPublicationPort {
        var publishCallCount = 0
        var lastBinDir: String? = null

        override suspend fun publish(binDir: String, toolIds: Set<String>): PublicationReport {
            publishCallCount++
            lastBinDir = binDir
            return publishedResult
        }

        override suspend fun resolvedTools(): ToolRegistryResult = ToolRegistryResult.Tools(
            publishedResult.registered.map {
                ToolStatus(tool = it, installed = true, path = "/bin/$it", source = ToolSource.DYNAMIC, reason = null)
            }
        )
    }

    private class FakeWslCliExecutor : WslCliExecutor() {
        var hostDistros: List<String> = listOf("Ubuntu")
        var userHome: String = "/home/lti"
        var missingDevPackages: Boolean = false
        var pythonMissing: Boolean = false
        val existingPaths: MutableSet<String> = mutableSetOf()
        val commandLog = mutableListOf<String>()

        override fun executeHost(command: List<String>, timeoutSeconds: Long, charset: Charset): CliExecutionResult {
            val cmdStr = command.joinToString(" ")
            commandLog.add("HOST: $cmdStr")
            if (command.contains("-l") && command.contains("-q")) {
                return CliExecutionResult(0, hostDistros.joinToString("\n"), "")
            }
            if (command.contains("--status")) {
                return if (hostDistros.isNotEmpty()) {
                    CliExecutionResult(0, "Default Version: 2", "")
                } else {
                    CliExecutionResult(1, "", "WSL not found")
                }
            }
            return CliExecutionResult(0, "", "")
        }

        override fun execute(distro: String, command: List<String>, timeoutSeconds: Long, charset: Charset, user: String?): CliExecutionResult {
            val cmdStr = command.joinToString(" ")
            commandLog.add("WSL: $cmdStr")
            if (cmdStr.contains("echo -n \$HOME") || cmdStr.contains("printenv HOME") || cmdStr.contains("printenv")) {
                return CliExecutionResult(0, userHome, "")
            }
            if (cmdStr.contains("dpkg-query")) {
                if (missingDevPackages) {
                    return CliExecutionResult(0, "", "no packages installed")
                }
                val pkgLines = WslToolchainProvisioner.REQUIRED_DEV_PACKAGES.joinToString("\n") { "$it install ok installed" }
                return CliExecutionResult(0, pkgLines, "")
            }
            if (pythonMissing && (cmdStr.contains("python") || cmdStr.contains("pip"))) {
                return CliExecutionResult(1, "", "python3: command not found")
            }
            if (cmdStr.contains("python3 --version") || cmdStr.contains("python3 -V")) {
                return CliExecutionResult(0, "Python 3.12.3", "")
            }
            if (cmdStr.contains("java -version")) {
                return CliExecutionResult(0, "openjdk version \"21.0.11\" 2024-04-16", "")
            }
            if (cmdStr.contains("df -BG")) {
                return CliExecutionResult(0, "Filesystem 1G-blocks Used Available Use% Mounted on\n/dev/sdc 250G 50G 190G 21% /home/lti\n", "")
            }
            if (cmdStr.contains("git clone")) {
                val subDir = command.lastOrNull() ?: ""
                existingPaths.add(subDir)
            }
            if (cmdStr.contains("cp") || cmdStr.contains("mkdir") || cmdStr.contains("chmod")) {
                val target = command.lastOrNull() ?: ""
                if (target.isNotBlank()) {
                    existingPaths.add(target)
                }
                if (cmdStr.contains("mkdir")) {
                    val paths = command.dropWhile { it != "mkdir" }.drop(1).filter { !it.startsWith("-") }
                    existingPaths.addAll(paths)
                }
                if (cmdStr.contains("/bin")) {
                    WslToolchainProvisioner.CORE_BINARIES.forEach {
                        existingPaths.add("$userHome/LtiRomTools/bin/$it")
                    }
                    WslToolchainProvisioner.ALL_CORE_TOOLS.forEach {
                        existingPaths.add("$userHome/LtiRomTools/bin/$it")
                    }
                }
            }
            if (cmdStr.contains("> '")) {
                val filePath = cmdStr.substringAfter("> '").substringBefore("'")
                if (filePath.isNotBlank() && filePath != cmdStr) {
                    existingPaths.add(filePath)
                }
            }
            if (cmdStr.contains("openssl") && cmdStr.contains("-out")) {
                val outIdx = command.indexOf("-out")
                if (outIdx >= 0 && outIdx + 1 < command.size) {
                    existingPaths.add(command[outIdx + 1])
                }
            }
            if (command.firstOrNull() == "find" && command.contains("-printf")) {
                val dir = (if (command.getOrNull(1) == "-L") command.getOrNull(2) else command.getOrNull(1))?.trimEnd('/') ?: ""
                val names = existingPaths.filter { it.startsWith("$dir/") && !it.removePrefix("$dir/").contains('/') }
                    .map { it.removePrefix("$dir/") }
                return CliExecutionResult(0, names.joinToString("\n"), "")
            }
            if (cmdStr.contains("test -e") || cmdStr.contains("test -d") || cmdStr.contains("test -x") || cmdStr.contains("test -r") || cmdStr.contains("test -f")) {
                if (cmdStr.contains("java-17-openjdk") || cmdStr.contains("java-21-openjdk")) {
                    return CliExecutionResult(0, "", "")
                }
                if (cmdStr.contains("/dev/fuse") || cmdStr.contains("/dev/loop-control")) {
                    return CliExecutionResult(0, "", "")
                }
                val targetPath = command.lastOrNull() ?: ""
                return if (existingPaths.contains(targetPath)) {
                    CliExecutionResult(0, "", "")
                } else {
                    CliExecutionResult(1, "", "Path does not exist: $targetPath")
                }
            }
            if (cmdStr.contains("mkfs.erofs")) {
                return CliExecutionResult(0, "mkfs.erofs 1.7.1\noptions: --fs-config-file -C -T", "")
            }
            return CliExecutionResult(0, "ok", "")
        }
    }

    @Test
    fun `checkStatus reports failure when WSL is not installed`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor().apply { hostDistros = emptyList() }
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport()

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher
        )

        val state = provisioner.checkStatus()

        val wslStep = state.steps.first { it.stage == SetupStepStage.WSL_DETECTION }
        assertEquals(StepStatus.FAILED, wslStep.status)
        assertTrue(wslStep.error?.contains("WSL 2 is not installed") == true)

        val serverStep = state.steps.first { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        assertEquals(StepStatus.PENDING, serverStep.status)
        // Not ready because the toolchain is absent in this fixture, not because the daemon is idle.
        assertFalse(state.isAllReady)
    }

    @Test
    fun `checkStatus fails when server daemon cannot be started`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor().apply {
            existingPaths.add("/home/lti/LtiRomWorkDir")
            existingPaths.add("/home/lti/LtiRomTools/external")
            WslToolchainProvisioner.SUBMODULES.forEach { existingPaths.add("/home/lti/LtiRomTools/external/${it.name}") }
            WslToolchainProvisioner.CORE_BINARIES.forEach { existingPaths.add("/home/lti/LtiRomTools/bin/$it") }
            WslToolchainProvisioner.ALL_CORE_TOOLS.forEach { existingPaths.add("/home/lti/LtiRomTools/bin/$it") }
        }
        val provisioner = WslToolchainProvisioner(
            supervisor = FakeDaemonSupervisor(descriptor = null, isHealthyResult = false),
            transport = FakeRemoteTransport(fakeCli = fakeCli, healthInfo = null),
            repository = inMemoryToolchainRepository(),
            detector = WslEnvironmentDetector(cli = fakeCli),
            cli = fakeCli,
            dispatcher = testDispatcher
        )

        val state = provisioner.checkStatus()

        assertEquals(StepStatus.FAILED, state.steps.first { it.stage == SetupStepStage.SERVER_CONNECTIVITY }.status)
        assertFalse(state.isAllReady)
    }

    @Test
    fun `checkStatus reports failed when server daemon is not running`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor(descriptor = null, isHealthyResult = false)
        val transport = FakeRemoteTransport(fakeCli = fakeCli, healthInfo = null)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher
        )

        val state = provisioner.checkStatus()

        val wslStep = state.steps.first { it.stage == SetupStepStage.WSL_DETECTION }
        assertEquals(StepStatus.SUCCESS, wslStep.status)

        val serverStep = state.steps.first { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        assertEquals(StepStatus.FAILED, serverStep.status)
        assertFalse(state.isAllReady)
    }

    @Test
    fun `checkStatus reports success when all components exist`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor().apply {
            existingPaths.add("/home/lti/LtiRomWorkDir")
            existingPaths.add("/home/lti/LtiRomTools/external")
            WslToolchainProvisioner.SUBMODULES.forEach {
                existingPaths.add("/home/lti/LtiRomTools/external/${it.name}")
            }
            WslToolchainProvisioner.CORE_BINARIES.forEach {
                existingPaths.add("/home/lti/LtiRomTools/bin/$it")
            }
            WslToolchainProvisioner.ALL_CORE_TOOLS.forEach {
                existingPaths.add("/home/lti/LtiRomTools/bin/$it")
            }
        }
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor(isHealthyResult = true)
        val transport = FakeRemoteTransport(fakeCli = fakeCli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher
        )

        val state = provisioner.checkStatus()

        assertTrue(state.steps.all { it.status == StepStatus.SUCCESS })
        assertTrue(state.isAllReady)
    }

    @Test
    fun `confirmed FULL_SETUP plan executes end-to-end pipeline and live check marks state ready`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher
        )

        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP)
        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)

        assertTrue(outcome is SetupOutcome.Succeeded, "Confirmed plan must succeed: $outcome")
        val finalState = provisioner.state.value
        assertTrue(finalState.isAllReady)
        assertFalse(finalState.isRunning)
        assertEquals(5, finalState.steps.filter { it.status == StepStatus.SUCCESS }.size)
        assertTrue(finalState.logs.any { it.contains("completed") })
        assertEquals(finalState.checkedAt, finalState.lastReadyAt, "Readiness after setup comes from the live check")
    }

    @Test
    fun `confirmed plan fails closed at SERVER_CONNECTIVITY when daemon health is null`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)
        val publication = FakeToolPublicationPort()

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher,
            toolPublicationPort = publication,
        )

        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP)
        transport.healthInfo = null
        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)

        assertTrue(outcome is SetupOutcome.Failed, "Null health is a terminal prerequisite failure: $outcome")
        assertEquals(SetupStepStage.SERVER_CONNECTIVITY.name, outcome.stage)
        assertEquals(0, publication.publishCallCount, "Nothing may run after a failed health prerequisite")
        val finalState = provisioner.state.value
        assertFalse(finalState.isAllReady)
        assertFalse(finalState.isRunning)
        assertFalse(finalState.isBusy, "Owner and busy flags must be released on the failure path")
    }

    @Test
    fun `confirmed FULL_SETUP plan creates LtiRomWorkDir if not present`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher
        )

        assertFalse(fakeCli.existingPaths.contains("/home/lti/LtiRomWorkDir"))
        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP)
        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)
        assertTrue(outcome is SetupOutcome.Succeeded, "$outcome")
        assertTrue(fakeCli.existingPaths.contains("/home/lti/LtiRomWorkDir"))
        assertTrue(
            provisioner.state.value.logs.any {
                it.contains("Ensuring workspace root directory exists at /home/lti/LtiRomWorkDir")
            }
        )
    }

    @Test
    fun `confirmed FULL_SETUP plan invokes toolPublicationPort and surfaces published tool IDs in state`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)
        val fakePublication = FakeToolPublicationPort()
        // The daemon's live listing is the publication fact surfaced in state, so the fake daemon
        // must report exactly what the publication port published.
        transport.publishedTools = fakePublication.publishedResult.registered
            .map { ToolStatusInfo(tool = it, installed = true) }
            .toMutableList()

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher,
            toolPublicationPort = fakePublication
        )

        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP)
        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)
        assertTrue(outcome is SetupOutcome.Succeeded, "$outcome")

        val finalState = provisioner.state.value
        assertTrue(finalState.isAllReady)
        assertEquals(1, fakePublication.publishCallCount)
        assertEquals("/home/lti/LtiRomTools/bin", fakePublication.lastBinDir)
        assertEquals(
            ToolchainReadinessPolicy.REQUIRED_PRODUCT_TOOLS,
            finalState.publishedToolIds
        )
        assertTrue(finalState.unpublishedToolIds.isEmpty())
    }

    private class FailingJournalRepository(
        private val delegate: ToolchainSetupRepository
    ) : ToolchainSetupRepository by delegate {
        override suspend fun recordAttemptAwaitingUserAction(
            attemptId: String,
            handoff: PersistedUserHandoff,
            plan: PersistedSetupPlan
        ): Result<Unit> = Result.failure(IllegalStateException("Simulated journal write failure"))
    }

    @Test
    fun `confirmed plan requiring packages pauses with AwaitingUserAction, survives restart and recovery, and resumes successfully`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)
        val sharedSettings = MapSettings()
        val repo1 = inMemoryToolchainRepository(sharedSettings)

        // 1. Initial check finds dev packages missing
        fakeCli.missingDevPackages = true
        val provisioner1 = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = repo1,
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher
        )

        provisioner1.verifyEnvironment()
        assertFalse(provisioner1.state.value.isAllReady)

        // 2. Prepare FULL_SETUP plan: derives InstallPackages because host_libraries is FAILED
        val plan = provisioner1.prepare(SetupPlanKind.FULL_SETUP)
        val outcome = provisioner1.confirm(plan.planId, plan.revisionHash)

        // 3. Must pause with AwaitingUserAction
        assertTrue(outcome is SetupOutcome.AwaitingUserAction, "Expected AwaitingUserAction but got $outcome")
        assertEquals(plan.planId, outcome.pendingPlanId)
        assertTrue(outcome.handoff.terminalCommand.contains("apt-get install"))
        assertEquals(AttemptStatus.AWAITING_USER_ACTION, repo1.currentState.activeAttempt?.status)

        // 4. Simulate App Restart: create new provisioner over shared persistence
        val repo2 = inMemoryToolchainRepository(sharedSettings)
        val provisioner2 = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = repo2,
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher
        )

        // Initial restored state detects pending attempt in AWAITING_USER_ACTION
        assertEquals(repo1.currentState.activeAttempt?.attemptId, provisioner2.state.value.pendingAttemptId)

        // 5. Recovery restores the pending attempt and returns AwaitingUserAction
        val recoveryOutcome = provisioner2.recover()
        assertTrue(recoveryOutcome is SetupOutcome.AwaitingUserAction, "Recovery must yield AwaitingUserAction: $recoveryOutcome")
        assertEquals(plan.planId, recoveryOutcome.pendingPlanId)

        // 6. User installs packages in WSL: now dpkg-query succeeds
        fakeCli.missingDevPackages = false

        // 7. Resume using planId
        val resumeOutcome = provisioner2.resume(recoveryOutcome.pendingPlanId)
        assertTrue(resumeOutcome is SetupOutcome.Succeeded, "Resume must succeed: $resumeOutcome")

        // 8. Attempt is now marked SUCCEEDED
        val finalAttempt = repo2.currentState.activeAttempt
        assertEquals(null, finalAttempt, "Active attempt must be cleared after archiving terminal success")
        assertTrue(provisioner2.state.value.isAllReady)
    }

    @Test
    fun `executePlan fails closed with Interrupted when repository fails to record AwaitingUserAction`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)
        val underlyingRepo = inMemoryToolchainRepository()
        val failingRepo = FailingJournalRepository(underlyingRepo)

        fakeCli.missingDevPackages = true
        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = failingRepo,
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher
        )

        provisioner.verifyEnvironment()
        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP)
        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)

        assertTrue(outcome is SetupOutcome.Interrupted, "Must fail closed with Interrupted on journal failure: $outcome")
        assertTrue(outcome.reason.contains("Journal write failed"))
    }

    @Test
    fun `resume fails closed when a different prerequisite becomes unhealthy during pause`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)
        val repo = inMemoryToolchainRepository()

        // 1. Initial check finds dev packages missing
        fakeCli.missingDevPackages = true
        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = repo,
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher
        )

        provisioner.verifyEnvironment()
        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP)
        val pauseOutcome = provisioner.confirm(plan.planId, plan.revisionHash)
        assertTrue(pauseOutcome is SetupOutcome.AwaitingUserAction)

        // 2. Dev packages are installed during the pause...
        fakeCli.missingDevPackages = false
        // ...BUT Python runtime is accidentally broken/uninstalled during pause!
        fakeCli.pythonMissing = true

        // 3. Resume must NOT succeed or mark diagnostics as successful!
        val resumeOutcome = provisioner.resume(pauseOutcome.pendingPlanId)
        assertTrue(
            resumeOutcome is SetupOutcome.Failed,
            "Resume must fail closed when another prerequisite fails: $resumeOutcome"
        )
        assertEquals(SetupStepStage.SYSTEM_DIAGNOSTICS.name, resumeOutcome.stage)
        assertTrue(resumeOutcome.reason.contains("Environment check failed before resumption"))
        assertTrue(resumeOutcome.reason.contains("Python"))

        val activeAttempt = repo.currentState.activeAttempt
        kotlin.test.assertNotNull(activeAttempt, "Attempt must not be cleared or archived as succeeded")
        assertEquals(AttemptStatus.AWAITING_USER_ACTION, activeAttempt.status)

        // 4. Fixing Python allows resumption to pass and complete
        fakeCli.pythonMissing = false
        val fixedResumeOutcome = provisioner.resume(pauseOutcome.pendingPlanId)
        assertTrue(fixedResumeOutcome is SetupOutcome.Succeeded, "Resume must succeed once all prerequisites pass: $fixedResumeOutcome")
        assertTrue(provisioner.state.value.isAllReady)
    }

    @Test
    fun `executePlan fails closed with Failed when constructed without repository and handoff is required`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)

        fakeCli.missingDevPackages = true
        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = null, // Constructed without repository
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher
        )

        provisioner.verifyEnvironment()
        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP)
        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)

        assertTrue(
            outcome is SetupOutcome.Failed,
            "Must fail closed with Failed when no repository is present to persist handoff: $outcome"
        )
        assertEquals(SetupStepStage.SYSTEM_DIAGNOSTICS.name, outcome.stage)
        assertTrue(outcome.reason.contains("requires a configured repository"))
    }

    @Test
    fun `concurrent verifyEnvironment calls share single verification run and single daemon start`() = runTest {
        val testDispatcher = Dispatchers.Default
        val fakeCli = FakeWslCliExecutor().apply {
            existingPaths.add("/home/lti/LtiRomWorkDir")
            existingPaths.add("/home/lti/LtiRomTools/external")
            WslToolchainProvisioner.SUBMODULES.forEach {
                existingPaths.add("/home/lti/LtiRomTools/external/${it.name}")
            }
            WslToolchainProvisioner.CORE_BINARIES.forEach {
                existingPaths.add("/home/lti/LtiRomTools/bin/$it")
            }
            WslToolchainProvisioner.ALL_CORE_TOOLS.forEach {
                existingPaths.add("/home/lti/LtiRomTools/bin/$it")
            }
        }
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val inFlightGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val supervisor = FakeDaemonSupervisor(isHealthyResult = false).apply {
            onEnsureStarted = {
                inFlightGate.await()
            }
        }
        val transport = FakeRemoteTransport(fakeCli = fakeCli)
        val repo = inMemoryToolchainRepository()

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = repo,
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher
        )

        val deferred1 = async(testDispatcher) { provisioner.verifyEnvironment() }
        while (supervisor.ensureStartedCount == 0) {
            kotlinx.coroutines.delay(10)
        }
        val deferred2 = async(testDispatcher) { provisioner.verifyEnvironment() }

        inFlightGate.complete(Unit)

        val result1 = deferred1.await()
        val result2 = deferred2.await()

        assertEquals(result1, result2, "Concurrent callers must receive identical completed results")
        assertEquals(1, supervisor.ensureStartedCount, "ensureStarted() must be called exactly once")
        assertTrue(result1.isAllReady)
    }

    @Test
    fun `caller cancellation does not abort shared verification for other callers`() = runTest {
        val testDispatcher = Dispatchers.Default
        val fakeCli = FakeWslCliExecutor().apply {
            existingPaths.add("/home/lti/LtiRomWorkDir")
            existingPaths.add("/home/lti/LtiRomTools/external")
            WslToolchainProvisioner.SUBMODULES.forEach {
                existingPaths.add("/home/lti/LtiRomTools/external/${it.name}")
            }
            WslToolchainProvisioner.CORE_BINARIES.forEach {
                existingPaths.add("/home/lti/LtiRomTools/bin/$it")
            }
            WslToolchainProvisioner.ALL_CORE_TOOLS.forEach {
                existingPaths.add("/home/lti/LtiRomTools/bin/$it")
            }
        }
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val inFlightGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val supervisor = FakeDaemonSupervisor(isHealthyResult = false).apply {
            onEnsureStarted = {
                inFlightGate.await()
            }
        }
        val transport = FakeRemoteTransport(fakeCli = fakeCli)
        val repo = inMemoryToolchainRepository()

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = repo,
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher
        )

        val caller1Job = launch(testDispatcher) {
            provisioner.verifyEnvironment()
        }
        while (supervisor.ensureStartedCount == 0) {
            kotlinx.coroutines.delay(10)
        }
        val caller2Deferred = async(testDispatcher) {
            provisioner.verifyEnvironment()
        }

        // Cancel caller 1 while verification is in flight
        caller1Job.cancel()

        // Release the barrier so the shared verification completes
        inFlightGate.complete(Unit)

        // Caller 2 should complete normally
        val result2 = caller2Deferred.await()
        assertTrue(result2.isAllReady, "Caller 2 must successfully complete verification")
        assertEquals(1, supervisor.ensureStartedCount)
    }

    private class FailingTerminalArchiveRepository(
        private val delegate: ToolchainSetupRepository,
        var shouldFailRecordAttemptTerminal: Boolean = true,
    ) : ToolchainSetupRepository by delegate {
        override suspend fun recordAttemptTerminal(
            attemptId: String,
            status: AttemptStatus,
            outcome: SetupOutcome,
        ): Result<Unit> {
            if (shouldFailRecordAttemptTerminal) {
                return Result.failure(IllegalStateException("Simulated terminal write failure"))
            }
            return delegate.recordAttemptTerminal(attemptId, status, outcome)
        }
    }

    @Test
    fun `cancel awaiting user action returns Interrupted when journal write fails and leaves attempt active`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor().apply { missingDevPackages = true }
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)
        val baseRepo = inMemoryToolchainRepository()
        val failingRepo = FailingTerminalArchiveRepository(baseRepo)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = failingRepo,
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher,
        )

        provisioner.verifyEnvironment()
        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP)
        val pauseOutcome = provisioner.confirm(plan.planId, plan.revisionHash)
        assertTrue(pauseOutcome is SetupOutcome.AwaitingUserAction)
        assertEquals(AttemptStatus.AWAITING_USER_ACTION, baseRepo.currentState.activeAttempt?.status)

        // Cancel while terminal archive fails
        val cancelOutcome = provisioner.cancel()

        // Must report Interrupted, NOT falsely claim Cancelled!
        assertTrue(cancelOutcome is SetupOutcome.Interrupted, "Expected Interrupted but got $cancelOutcome")
        assertTrue(cancelOutcome.reason.contains("could not be archived"))
        assertEquals(AttemptStatus.AWAITING_USER_ACTION, baseRepo.currentState.activeAttempt?.status)

        // Now fix the journal write failure and cancel again
        failingRepo.shouldFailRecordAttemptTerminal = false
        val secondCancelOutcome = provisioner.cancel()
        assertTrue(secondCancelOutcome is SetupOutcome.Cancelled, "Expected Cancelled once journal write succeeds")
        assertNull(baseRepo.currentState.activeAttempt)
        assertEquals(AttemptStatus.CANCELLED, baseRepo.currentState.attemptHistory.lastOrNull()?.status)
    }
}
