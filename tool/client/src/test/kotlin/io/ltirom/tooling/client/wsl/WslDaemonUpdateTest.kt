package io.ltirom.tooling.client.wsl

import io.ltirom.tooling.client.WslDaemonConfig
import io.ltirom.tooling.client.WslDaemonManager
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WslDaemonUpdateTest {

    private class FakeStateStore(
        var existingDescriptor: ServerConnectionDescriptor? = null
    ) : WslStateStore(WslCliExecutor()) {
        override fun readState(distro: String, wslIp: String): ServerConnectionDescriptor? = existingDescriptor
    }

    private class FakeProber(
        var healthy: Boolean = true,
        var serverInfo: WslServerInfo? = null
    ) : DaemonHealthProber(null) {
        override suspend fun isHealthy(info: ServerConnectionDescriptor, timeoutMs: Long): Boolean = healthy
        override suspend fun getServerInfo(info: ServerConnectionDescriptor): WslServerInfo? = serverInfo
    }

    private class FakeProcessLauncher : WslProcessLauncher(5000L) {
        var launchCount = 0
        var lastLaunchedBinary: String? = null
        var lastLaunchedJavaHome: String? = null

        override suspend fun launch(
            distro: String,
            binaryPath: String,
            javaHome: String?
        ): WslProcessLaunchResult {
            launchCount++
            lastLaunchedBinary = binaryPath
            lastLaunchedJavaHome = javaHome
            // Mock a process for launch
            val pb = ProcessBuilder("cmd.exe", "/c", "echo LTI_WSL_SERVER_READY port=9999 token=mocktoken pid=1234")
            val p = pb.start()
            return WslProcessLaunchResult(p, 9999, "mocktoken", 1234L)
        }
    }

    @Test
    fun `adopting daemon with serverVersion unequal to active and activeRuns zero restarts it`() = runTest {
        val existing = ServerConnectionDescriptor("127.0.0.1", 8080, "oldtoken", 100L, "Ubuntu-24.04")
        val stateStore = FakeStateStore(existing)
        val prober = FakeProber(
            healthy = true,
            serverInfo = WslServerInfo(
                status = "UP",
                distro = "Ubuntu-24.04",
                kernelRelease = "6.6.0",
                architecture = "x86_64",
                javaVersion = "21",
                serverUptimeMs = 1000L,
                serverVersion = "1.0.0",
                activeRuns = 0
            )
        )
        val launcher = FakeProcessLauncher()
        val manager = WslDaemonManager(
            config = WslDaemonConfig(
                distroName = "Ubuntu-24.04",
                serverBinaryWslPath = "/home/testuser/.ltirom/server/current/bin/ltirom-server"
            ),
            stateStore = stateStore,
            launcher = launcher,
            customProber = prober
        )

        val result = manager.ensureRunningVersion(
            distro = "Ubuntu-24.04",
            activeVersion = "2.0.0"
        )

        // Must restart since activeRuns == 0 and versions differ
        assertEquals(1, launcher.launchCount, "Launcher should have been invoked to restart daemon")
        assertFalse(manager.isUpdateWaiting, "Should not be update waiting when restarted")
        assertFalse(manager.lastStartAdopted, "Should not be adopted without restart")
    }

    @Test
    fun `adopting daemon with serverVersion unequal to active and activeRuns greater than zero does not restart and sets updateWaiting`() = runTest {
        val existing = ServerConnectionDescriptor("127.0.0.1", 8080, "oldtoken", 100L, "Ubuntu-24.04")
        val stateStore = FakeStateStore(existing)
        val prober = FakeProber(
            healthy = true,
            serverInfo = WslServerInfo(
                status = "UP",
                distro = "Ubuntu-24.04",
                kernelRelease = "6.6.0",
                architecture = "x86_64",
                javaVersion = "21",
                serverUptimeMs = 1000L,
                serverVersion = "1.0.0",
                activeRuns = 2
            )
        )
        val launcher = FakeProcessLauncher()
        val manager = WslDaemonManager(
            config = WslDaemonConfig(
                distroName = "Ubuntu-24.04",
                serverBinaryWslPath = "/home/testuser/.ltirom/server/current/bin/ltirom-server"
            ),
            stateStore = stateStore,
            launcher = launcher,
            customProber = prober
        )

        val result = manager.ensureRunningVersion(
            distro = "Ubuntu-24.04",
            activeVersion = "2.0.0"
        )

        // Must NOT restart since activeRuns > 0
        assertEquals(0, launcher.launchCount, "Launcher must not restart daemon with active runs")
        assertTrue(manager.isUpdateWaiting, "Must flag update-waiting")
        assertTrue(manager.lastStartAdopted, "Must adopt running daemon for now")
        assertEquals(existing.port, result.port)
    }

    @Test
    fun `adopting daemon with equal version is adopted without restart`() = runTest {
        val existing = ServerConnectionDescriptor("127.0.0.1", 8080, "oldtoken", 100L, "Ubuntu-24.04")
        val stateStore = FakeStateStore(existing)
        val prober = FakeProber(
            healthy = true,
            serverInfo = WslServerInfo(
                status = "UP",
                distro = "Ubuntu-24.04",
                kernelRelease = "6.6.0",
                architecture = "x86_64",
                javaVersion = "21",
                serverUptimeMs = 1000L,
                serverVersion = "2.0.0",
                activeRuns = 0
            )
        )
        val launcher = FakeProcessLauncher()
        val manager = WslDaemonManager(
            config = WslDaemonConfig(
                distroName = "Ubuntu-24.04",
                serverBinaryWslPath = "/home/testuser/.ltirom/server/current/bin/ltirom-server"
            ),
            stateStore = stateStore,
            launcher = launcher,
            customProber = prober
        )

        val result = manager.ensureRunningVersion(
            distro = "Ubuntu-24.04",
            activeVersion = "2.0.0"
        )

        assertEquals(0, launcher.launchCount, "Launcher should not be invoked when version matches")
        assertFalse(manager.isUpdateWaiting)
        assertTrue(manager.lastStartAdopted)
        assertEquals(existing.port, result.port)
    }
}
