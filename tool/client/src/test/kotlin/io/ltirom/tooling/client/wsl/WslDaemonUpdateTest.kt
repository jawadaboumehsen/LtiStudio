package io.ltirom.tooling.client.wsl

import org.ide.lti.core.model.setup.SetupEnvironment
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
        /** What `/proc/<pid>` says about the old service; null = can't be told. */
        var alive: () -> Boolean? = { null }

        override fun readState(distro: String, wslIp: String): ServerConnectionDescriptor? =
            existingDescriptor?.takeIf { it.distro == distro }

        override fun isProcessAlive(distro: String, pid: Long): Boolean? = alive()
    }

    private class FakeProber(
        var healthy: Boolean = true,
        var serverInfo: WslServerInfo? = null,
        /** The service accepts a non-forced shutdown (it refuses when a run is active). */
        var acceptShutdown: Boolean = true,
        /** After accepting, the service process actually exits. */
        var exitsAfterShutdown: Boolean = true,
    ) : DaemonHealthProber(null) {
        val shutdownRequests = mutableListOf<Boolean>()

        /** The old service's process, as the state store would see it. */
        var processAlive: Boolean? = true

        override suspend fun isHealthy(info: ServerConnectionDescriptor, timeoutMs: Long): Boolean = healthy
        override suspend fun getServerInfo(info: ServerConnectionDescriptor): WslServerInfo? = serverInfo
        override suspend fun requestShutdown(info: ServerConnectionDescriptor, force: Boolean): Boolean {
            shutdownRequests += force
            if (acceptShutdown) {
                // An accepted shutdown stops health answers at once; the process may still be alive.
                healthy = false
                if (exitsAfterShutdown) processAlive = false
            }
            return acceptShutdown
        }
    }

    /** A launched service process; records whether anything destroyed it. */
    private class FakeProcess : Process() {
        @Volatile
        var destroyed = false

        override fun getInputStream(): java.io.InputStream = java.io.InputStream.nullInputStream()
        override fun getOutputStream(): java.io.OutputStream = java.io.OutputStream.nullOutputStream()
        override fun getErrorStream(): java.io.InputStream = java.io.InputStream.nullInputStream()
        override fun waitFor(): Int = 0
        override fun exitValue(): Int = 0
        override fun isAlive(): Boolean = !destroyed
        override fun destroy() {
            destroyed = true
        }
        override fun destroyForcibly(): Process = apply { destroy() }
    }

    private class FakeProcessLauncher : WslProcessLauncher(5000L) {
        val launched = mutableListOf<FakeProcess>()
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
            val p = FakeProcess().also { launched += it }
            return WslProcessLaunchResult(p, 9999, "mocktoken", 1000L + launchCount)
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
        stateStore.alive = { prober.processAlive }
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

    @Test
    fun `a deferred update survives status reads and is applied once active runs finish`() = runTest {
        val existing = ServerConnectionDescriptor("127.0.0.1", 8080, "oldtoken", 100L, "Ubuntu-24.04")
        val oldBusy = WslServerInfo(
            status = "UP",
            distro = "Ubuntu-24.04",
            kernelRelease = "6.6.0",
            architecture = "x86_64",
            javaVersion = "21",
            serverUptimeMs = 1000L,
            serverVersion = "1.0.0",
            activeRuns = 2,
        )
        val prober = FakeProber(healthy = true, serverInfo = oldBusy)
        val launcher = FakeProcessLauncher()
        val manager = WslDaemonManager(
            config = WslDaemonConfig(
                distroName = "Ubuntu-24.04",
                serverBinaryWslPath = "/home/testuser/.ltirom/server/current/bin/ltirom-server",
            ),
            stateStore = FakeStateStore(existing).also { it.alive = { prober.processAlive } },
            launcher = launcher,
            customProber = prober,
        )
        val env = SetupEnvironment("Ubuntu-24.04", 2, "ubuntu", "24.04", "testuser", "/home/testuser")

        manager.ensureRunningVersion(distro = "Ubuntu-24.04", activeVersion = "2.0.0")
        assertTrue(manager.isUpdateWaiting)

        // A status read (what ServerArtifactAdapter does right after starting) must not clear the flag.
        assertEquals("1.0.0", manager.getServerInfo(env)?.serverVersion)
        assertTrue(manager.isUpdateWaiting, "Reading service info must not hide the pending update")

        // The transport's per-request start keeps honouring the desired version while runs are active.
        manager.ensureStarted()
        assertTrue(manager.isUpdateWaiting)
        assertEquals(0, launcher.launchCount)

        // Once the old service is idle, the next start applies the update.
        prober.serverInfo = oldBusy.copy(activeRuns = 0)
        manager.ensureStarted()
        assertEquals(1, launcher.launchCount, "The deferred update must be applied when runs finish")
        assertFalse(manager.isUpdateWaiting)
    }

    private fun oldIdleService(runs: Int = 0) = WslServerInfo(
        status = "UP",
        distro = "Ubuntu-24.04",
        kernelRelease = "6.6.0",
        architecture = "x86_64",
        javaVersion = "21",
        serverUptimeMs = 1000L,
        serverVersion = "1.0.0",
        activeRuns = runs,
    )

    private fun managerWith(prober: FakeProber, launcher: FakeProcessLauncher) = WslDaemonManager(
        config = WslDaemonConfig(
            distroName = "Ubuntu-24.04",
            serverBinaryWslPath = "/home/testuser/.ltirom/server/current/bin/ltirom-server",
        ),
        stateStore = FakeStateStore(ServerConnectionDescriptor("127.0.0.1", 8080, "oldtoken", 100L, "Ubuntu-24.04"))
            .also { it.alive = { prober.processAlive } },
        launcher = launcher,
        customProber = prober,
    )

    @Test
    fun `a run that starts during the update makes the service refuse shutdown and nothing is killed`() = runTest {
        // Idle when we looked, but a run is admitted before the shutdown request lands: the service refuses.
        val prober = FakeProber(serverInfo = oldIdleService(), acceptShutdown = false)
        val launcher = FakeProcessLauncher()
        val manager = managerWith(prober, launcher)

        manager.ensureRunningVersion(distro = "Ubuntu-24.04", activeVersion = "2.0.0")

        assertEquals(listOf(false), prober.shutdownRequests, "Updates never force a shutdown")
        assertEquals(0, launcher.launchCount, "No replacement while the old service keeps its work")
        assertTrue(manager.isUpdateWaiting)
    }

    @Test
    fun `a failed health check after shutdown is not proof of exit`() = runTest {
        val prober = FakeProber(serverInfo = oldIdleService(), acceptShutdown = true, exitsAfterShutdown = false)
        val launcher = FakeProcessLauncher()
        val manager = managerWith(prober, launcher)

        manager.ensureRunningVersion(distro = "Ubuntu-24.04", activeVersion = "2.0.0")

        assertEquals(0, launcher.launchCount, "Termination must be confirmed before a replacement starts")
        assertTrue(manager.isUpdateWaiting)
    }

    @Test
    fun `an old service whose exit cannot be established keeps the update waiting`() = runTest {
        val prober = FakeProber(serverInfo = oldIdleService())
        val launcher = FakeProcessLauncher()
        val manager = managerWith(prober, launcher)
        prober.processAlive = null
        prober.exitsAfterShutdown = false

        manager.ensureRunningVersion(distro = "Ubuntu-24.04", activeVersion = "2.0.0")

        assertEquals(0, launcher.launchCount)
        assertTrue(manager.isUpdateWaiting)
    }

    @Test
    fun `retiring a service in one distro never kills the process launched in another`() = runTest {
        val prober = FakeProber(serverInfo = oldIdleService())
        val launcher = FakeProcessLauncher()
        // Distro A has no service yet; distro B runs an old one this manager did not launch.
        val manager = WslDaemonManager(
            config = WslDaemonConfig(serverBinaryWslPath = "/home/testuser/.ltirom/server/current/bin/ltirom-server"),
            stateStore = FakeStateStore(ServerConnectionDescriptor("127.0.0.1", 8080, "oldtoken", 100L, "Distro-B"))
                .also { it.alive = { prober.processAlive } },
            launcher = launcher,
            customProber = prober,
        )

        manager.ensureRunningVersion(distro = "Distro-A", activeVersion = "2.0.0")
        val launchedInA = launcher.launched.single()

        manager.ensureRunningVersion(distro = "Distro-B", activeVersion = "2.0.0")

        assertEquals(listOf(false), prober.shutdownRequests, "B's old service was asked to stop")
        assertFalse(launchedInA.destroyed, "A's service process must survive B's retirement")
        assertEquals(2, launcher.launchCount, "B gets its replacement")
    }
}
