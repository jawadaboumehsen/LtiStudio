package io.ltirom.tooling.client.setup

import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
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
import org.ide.lti.core.domain.ports.PublishedTools
import org.ide.lti.core.domain.ports.ToolPublicationPort
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WslToolchainProvisionerTest {

    private class FakeDaemonSupervisor(
        var descriptor: ServerConnectionDescriptor? = ServerConnectionDescriptor("127.0.0.1", 9090, "token", 1234L, "Ubuntu"),
        var isHealthyResult: Boolean = true,
        var shouldFailEnsureStarted: Boolean = false
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
            status = "HEALTHY",
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
                else -> ToolExecutionResponse(0, "ok", "", 10L)
            }
        }

        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()
        override suspend fun listTools(): List<ToolStatusInfo> = publishedTools
        override suspend fun refreshTools(): List<ToolStatusInfo> = publishedTools
        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true
        override suspend fun downloadFile(remotePath: String): ByteArray? = null
        override suspend fun shutdown(): Boolean = true
    }

    private class FakeToolPublicationPort(
        var publishedResult: PublishedTools = PublishedTools(
            published = setOf("lpmake", "img2sdat", "unpack_bootimg", "erofsfuse", "signapk"),
            skipped = emptySet()
        )
    ) : ToolPublicationPort {
        var publishCallCount = 0
        var lastBinDir: String? = null

        override suspend fun publish(binDir: String): PublishedTools {
            publishCallCount++
            lastBinDir = binDir
            return publishedResult
        }

        override suspend fun resolvedTools(): Set<String> = publishedResult.published
    }

    private class FakeWslCliExecutor : WslCliExecutor() {
        var hostDistros: List<String> = listOf("Ubuntu")
        var userHome: String = "/home/lti"
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
            if (cmdStr.contains("echo -n \$HOME")) {
                return CliExecutionResult(0, userHome, "")
            }
            if (cmdStr.contains("dpkg-query")) {
                val pkgLines = WslToolchainProvisioner.REQUIRED_DEV_PACKAGES.joinToString("\n") { "$it install ok installed" }
                return CliExecutionResult(0, pkgLines, "")
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
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher
        )

        val state = provisioner.checkStatus()

        assertTrue(state.steps.all { it.status == StepStatus.SUCCESS })
        assertTrue(state.isAllReady)
    }

    @Test
    fun `runFullSetup executes end-to-end pipeline and marks state ready`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher
        )

        provisioner.runFullSetup()

        val finalState = provisioner.state.value
        assertTrue(finalState.isAllReady)
        assertFalse(finalState.isRunning)
        assertEquals(5, finalState.steps.filter { it.status == StepStatus.SUCCESS }.size)
        assertTrue(finalState.logs.any { it.contains("Complete") || it.contains("successfully built") })
    }

    @Test
    fun `runFullSetup halts when daemon cannot be started`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor(shouldFailEnsureStarted = true)
        val transport = FakeRemoteTransport(fakeCli = fakeCli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher
        )

        provisioner.runFullSetup()

        val finalState = provisioner.state.value
        assertFalse(finalState.isAllReady)
        assertFalse(finalState.isRunning)

        val serverStep = finalState.steps.first { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        assertEquals(StepStatus.FAILED, serverStep.status)
        assertTrue(serverStep.error?.contains("Failed to launch or attach") == true)
    }

    @Test
    fun `runFullSetup creates LtiRomWorkDir if not present`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher
        )

        assertFalse(fakeCli.existingPaths.contains("/home/lti/LtiRomWorkDir"))
        provisioner.runFullSetup()
        assertTrue(fakeCli.existingPaths.contains("/home/lti/LtiRomWorkDir"))
        assertTrue(provisioner.state.value.logs.any { it.contains("Initialized workspace root directory at /home/lti/LtiRomWorkDir") })
    }

    @Test
    fun `runFullSetup invokes toolPublicationPort and surfaces published tool IDs in state`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)
        val fakePublication = FakeToolPublicationPort()

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher,
            toolPublicationPort = fakePublication
        )

        provisioner.runFullSetup()

        val finalState = provisioner.state.value
        assertTrue(finalState.isAllReady)
        assertEquals(1, fakePublication.publishCallCount)
        assertEquals("/home/lti/LtiRomTools/bin", fakePublication.lastBinDir)
        assertEquals(
            setOf("lpmake", "img2sdat", "unpack_bootimg", "erofsfuse", "signapk"),
            finalState.publishedToolIds
        )
        assertTrue(finalState.unpublishedToolIds.isEmpty())
    }
}
