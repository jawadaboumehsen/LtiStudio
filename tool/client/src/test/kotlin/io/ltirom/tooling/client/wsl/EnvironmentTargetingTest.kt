package io.ltirom.tooling.client.wsl

import io.ltirom.tooling.client.WslDaemonConfig
import io.ltirom.tooling.client.WslDaemonManager
import io.ltirom.tooling.client.WslPathTranslator
import io.ltirom.tooling.client.workspace.WorkspaceVirtualizer
import kotlinx.coroutines.runBlocking
import org.ide.lti.core.model.setup.AttemptStatus
import org.ide.lti.core.model.setup.PersistedEnvironment
import org.ide.lti.core.model.setup.SetupAttemptRecord
import org.ide.lti.core.model.setup.SetupEnvironment
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnvironmentTargetingTest {

    private class RecordingWslCliExecutor : WslCliExecutor() {
        val executedDistros = mutableListOf<String>()
        val executedCommands = mutableListOf<List<String>>()

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult {
            executedDistros.add(distro)
            executedCommands.add(command)
            return when {
                command.any { it.contains("id -un") } ->
                    CliExecutionResult(0, "userB\n/home/userB", "")
                command.any { it.contains("server.state") } ->
                    CliExecutionResult(1, "", "")
                command.contains("test") ->
                    CliExecutionResult(0, "", "")
                else ->
                    CliExecutionResult(0, "1000", "")
            }
        }
    }

    private class RecordingWslProcessLauncher : WslProcessLauncher(startupTimeoutMs = 1000L) {
        val launchedDistros = mutableListOf<String>()

        override suspend fun launch(distro: String, binaryPath: String): WslProcessLaunchResult {
            launchedDistros.add(distro)
            val dummyProcess = ProcessBuilder("cmd.exe", "/c", "exit 0").start()
            return WslProcessLaunchResult(
                process = dummyProcess,
                port = 49152,
                token = "fake-token",
                pid = 12345L,
            )
        }
    }

    private val envA = SetupEnvironment(
        distro = "Ubuntu-22.04",
        wslVersion = 2,
        osId = "ubuntu",
        osVersionId = "22.04",
        user = "userA",
        home = "/home/userA",
    )

    private val envB = SetupEnvironment(
        distro = "Ubuntu-24.04",
        wslVersion = 2,
        osId = "ubuntu",
        osVersionId = "24.04",
        user = "userB",
        home = "/home/userB",
    )

    @Test
    fun `with distros A and B selected B only issues commands targeting B`() = runBlocking {
        val cli = RecordingWslCliExecutor()
        val launcher = RecordingWslProcessLauncher()
        val detector = WslEnvironmentDetector(cli)
        val virtualizer = WorkspaceVirtualizer(cli = cli, detector = detector)
        val translator = WslPathTranslator()
        val daemonManager = WslDaemonManager(
            config = WslDaemonConfig(autoStart = false),
            cli = cli,
            detector = detector,
            launcher = launcher,
        )

        // 1. Path translation with envB
        val uncPath = translator.toWindowsPath(envB, "/home/userB/build.img")
        assertTrue(uncPath.contains("""\\wsl.localhost\Ubuntu-24.04\"""), "UNC path must target Ubuntu-24.04: $uncPath")
        assertFalse(uncPath.contains("Ubuntu-22.04"), "UNC path must not target Ubuntu-22.04")

        // 2. Workspace virtualization with envB
        val ws = virtualizer.allocateWorkspace("ws-test", envB)
        assertEquals("/home/userB/.ltirom/workspaces/ws-test", ws.linuxExt4Path)
        assertTrue(ws.windowsUncPath.contains("""\\wsl.localhost\Ubuntu-24.04\"""))

        // 3. Installer status with envB
        ServerArtifactInstaller(cli, bundleDirProvider = { null }).status(envB)

        // 4. Daemon manager with envB
        daemonManager.ensureStarted(envB)

        // Verify all CLI and launcher executions targeted Ubuntu-24.04 (B), none targeted Ubuntu-22.04 (A)
        assertTrue(cli.executedDistros.isNotEmpty(), "Expected CLI commands to have executed")
        assertTrue(
            cli.executedDistros.all {
                it == "Ubuntu-24.04"
            },
            "All CLI executions must target Ubuntu-24.04: ${cli.executedDistros}",
        )
        assertEquals(listOf("Ubuntu-24.04"), launcher.launchedDistros, "Process launcher must only target Ubuntu-24.04")
    }

    @Test
    fun `changing selection to A during active attempt does not change active attempt commands`() = runBlocking {
        val cli = RecordingWslCliExecutor()
        val launcher = RecordingWslProcessLauncher()
        val detector = WslEnvironmentDetector(cli)
        val virtualizer = WorkspaceVirtualizer(cli = cli, detector = detector)
        val translator = WslPathTranslator()
        val daemonManager = WslDaemonManager(
            config = WslDaemonConfig(autoStart = false),
            cli = cli,
            detector = detector,
            launcher = launcher,
        )

        var currentSelection = envB
        // Operation admits and captures immutable snapshot
        val activeAttemptEnv = currentSelection

        // User changes selection to A mid-operation
        currentSelection = envA

        // Active attempt performs operations using its captured snapshot
        virtualizer.allocateWorkspace("ws-active", activeAttemptEnv)
        ServerArtifactInstaller(cli, bundleDirProvider = { null }).status(activeAttemptEnv)
        daemonManager.ensureStarted(activeAttemptEnv)
        val unc = translator.toWindowsPath(activeAttemptEnv, "/home/userB/active.img")

        assertTrue(unc.contains("Ubuntu-24.04"))
        assertFalse(unc.contains("Ubuntu-22.04"))
        assertTrue(
            cli.executedDistros.all {
                it == "Ubuntu-24.04"
            },
            "Active attempt commands must target B even when currentSelection changed to A: ${cli.executedDistros}",
        )
        assertEquals(listOf("Ubuntu-24.04"), launcher.launchedDistros)
    }

    @Test
    fun `pending handoff restored after restart uses journaled environment even if current selection is A`() =
        runBlocking {
            val cli = RecordingWslCliExecutor()
            val launcher = RecordingWslProcessLauncher()
            val detector = WslEnvironmentDetector(cli)
            val virtualizer = WorkspaceVirtualizer(cli = cli, detector = detector)
            val daemonManager = WslDaemonManager(
                config = WslDaemonConfig(autoStart = false),
                cli = cli,
                detector = detector,
                launcher = launcher,
            )

            // Journal from previous session with B
            val journaledRecord = SetupAttemptRecord(
                attemptId = "attempt-prev",
                environmentKey = envB.distro,
                planId = "plan-1",
                planRevisionHash = "hash-1",
                planKind = "BOOTSTRAP_PACKAGES",
                status = AttemptStatus.AWAITING_USER_ACTION,
                createdAtEpochMs = 1000L,
                environment = PersistedEnvironment(
                    distro = envB.distro,
                    wslVersion = envB.wslVersion,
                    osId = envB.osId,
                    osVersionId = envB.osVersionId,
                    user = envB.user,
                    home = envB.home,
                ),
            )

            // Current UI selection in new session defaults to A
            val currentSelection = envA

            // Resumed operation restores environment from journal
            val restoredEnv = journaledRecord.environment?.let {
                SetupEnvironment(
                    distro = it.distro,
                    wslVersion = it.wslVersion,
                    osId = it.osId,
                    osVersionId = it.osVersionId,
                    user = it.user,
                    home = it.home,
                )
            } ?: currentSelection

            assertEquals("Ubuntu-24.04", restoredEnv.distro)

            // Restored operation continues using restoredEnv
            virtualizer.allocateWorkspace("restored-ws", restoredEnv)
            daemonManager.ensureStarted(restoredEnv)

            assertTrue(
                cli.executedDistros.all {
                    it == "Ubuntu-24.04"
                },
                "Restored handoff commands must target B from journal, not A: ${cli.executedDistros}",
            )
            assertEquals(listOf("Ubuntu-24.04"), launcher.launchedDistros)
        }
}
