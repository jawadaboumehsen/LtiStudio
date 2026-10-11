package org.ide.lti.desktop.realhost

import io.ktor.client.HttpClient
import io.ltirom.tooling.client.transport.KtorRemoteTransportAdapter
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import org.ide.lti.core.data.tools.KotlinToolStoreAdapter
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.tools.ToolManager
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.util.UUID

/**
 * Real-host test harness (T003).
 *
 * Runs against the real staged build server process in linux mode or in a WSL test distro.
 * If LTI_REAL_HOST is unset, real-host tests skip cleanly.
 */
class RealToolHost(
    val mode: String,
    val tempHome: File,
    val serverProcess: Process?,
    val supervisor: DaemonSupervisorPort,
    val transport: KtorRemoteTransportAdapter,
    val store: KotlinToolStoreAdapter,
    val manager: ToolManager,
    private val closeAction: () -> Unit = {},
) : AutoCloseable {
    val remoteHome: String
        get() =
            if (mode == "wsl") {
                io.ltirom.tooling.client.WslPathTranslator().toWslPath(tempHome.path)
            } else {
                tempHome.path
            }

    fun hostPath(sub: String = ""): String = if (sub.isBlank()) remoteHome else "$remoteHome/$sub"

    var allowRelaunch: Boolean
        get() = (supervisor as? TestSupervisor)?.allowRelaunch ?: true
        set(value) {
            (supervisor as? TestSupervisor)?.allowRelaunch = value
        }

    override fun close() {
        try {
            serverProcess?.destroyForcibly()
        } catch (_: Throwable) {
        }
        try {
            closeAction()
        } catch (_: Throwable) {
        }
    }

    companion object {
        fun isAvailable(): Boolean =
            !System.getenv("LTI_REAL_HOST").isNullOrBlank() || !System.getProperty("lti.real.host").isNullOrBlank()

        fun create(): RealToolHost {
            val mode =
                System.getenv("LTI_REAL_HOST") ?: System.getProperty("lti.real.host")
                    ?: throw IllegalStateException("LTI_REAL_HOST is not set. Real-host tests require LTI_REAL_HOST=linux or LTI_REAL_HOST=wsl.")

            val tempHome: File
            val process: Process?
            val supervisor: DaemonSupervisorPort
            val httpClient: HttpClient
            val closeAction: () -> Unit

            if (mode == "linux") {
                val linuxTempHome = Files.createTempDirectory("ltirom-realhost-home").toFile()
                tempHome = linuxTempHome
                closeAction = { linuxTempHome.deleteRecursively() }
                val token = UUID.randomUUID().toString()

                // Find staged server bundle
                val candidateDirs =
                    listOf(
                        File("build/bundledServer/ltirom-server"),
                        File("lti-desktop/build/bundledServer/ltirom-server"),
                        File("../LtiRomServer/build/install/ltirom-server"),
                    )
                val serverDir =
                    candidateDirs.firstOrNull { it.exists() }
                        ?: error("Could not find server bundle. Run :stageBundledServer or :installDist.")

                val binExecutable = File(serverDir, "bin/ltirom-server").canonicalPath
                val pb =
                    ProcessBuilder(
                        binExecutable,
                        "--port",
                        "0",
                        "--token",
                        token,
                        "--host",
                        "127.0.0.1",
                    )
                pb.environment()["HOME"] = tempHome.canonicalPath
                val proc = pb.start()
                process = proc

                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                var allocatedPort = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.contains("LTI_WSL_SERVER_READY")) {
                        val parts = line.split(":")
                        if (parts.size >= 2) {
                            allocatedPort = parts[1].trim().toIntOrNull() ?: 0
                            break
                        }
                    }
                }
                if (allocatedPort == 0) {
                    proc.destroyForcibly()
                    error("Failed to read LTI_WSL_SERVER_READY port from server startup")
                }

                val clientManager = io.ltirom.tooling.client.WslDaemonManager()
                httpClient = clientManager.httpClient

                supervisor =
                    object : DaemonSupervisorPort {
                        override suspend fun ensureStarted(): ServerConnectionDescriptor =
                            ServerConnectionDescriptor(host = "127.0.0.1", port = allocatedPort, token = token, pid = proc.pid(), distro = "linux")

                        override suspend fun getConnectionInfo(): ServerConnectionDescriptor? =
                            ServerConnectionDescriptor(host = "127.0.0.1", port = allocatedPort, token = token, pid = proc.pid(), distro = "linux")

                        override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean = true

                        override suspend fun shutdownDaemon(): Boolean = true

                        override fun close() {}
                    }
            } else {
                // wsl mode
                val distro = System.getenv("LTI_TEST_DISTRO") ?: System.getProperty("lti.test.distro") ?: "LtiRomTest"
                val cli = io.ltirom.tooling.client.wsl.WslCliExecutor()
                val detector = io.ltirom.tooling.client.wsl.WslEnvironmentDetector(cli)
                val wslSupervisor =
                    io.ltirom.tooling.client.WslDaemonManager(
                        config = io.ltirom.tooling.client.WslDaemonConfig(distroName = distro),
                        cli = cli,
                        detector = detector,
                    )
                supervisor = wslSupervisor
                httpClient = wslSupervisor.httpClient
                process = null

                val userPair = detector.readUser(distro)
                val wslUser = userPair?.first ?: "lti"
                val userHome = userPair?.second ?: detector.resolveWslUserHome(distro)
                val installer = io.ltirom.tooling.client.wsl.ServerArtifactInstaller(cli = cli)
                val setupEnv =
                    org.ide.lti.core.model.setup.SetupEnvironment(
                        distro = distro,
                        wslVersion = 2,
                        osId = "ubuntu",
                        osVersionId = "24.04",
                        user = wslUser,
                        home = userHome,
                    )
                installer.ensureInstalled(setupEnv)

                val testDirName = "test-host-${UUID.randomUUID().toString().take(8)}"
                val wslHome = "$userHome/$testDirName"
                cli.execute(distro, listOf("mkdir", "-p", wslHome))
                val translator = io.ltirom.tooling.client.WslPathTranslator(distro)
                tempHome = File(translator.toWindowsPath(wslHome, distro))
                closeAction = {
                    cli.execute(distro, listOf("sh", "-c", "rm -rf $wslHome; rm -f ~/.ltirom/tools.d/working-tool.json"))
                }
            }

            val testSupervisor = TestSupervisor(supervisor)

            val transport =
                KtorRemoteTransportAdapter(
                    supervisor = testSupervisor,
                    httpClient = httpClient,
                )

            val monitor =
                io.ltirom.tooling.client.resilience.DaemonHeartbeatMonitor(
                    supervisor = testSupervisor,
                    transport = transport,
                    intervalMs = 3_000L,
                ).apply { start() }

            val originalClose = closeAction
            val monitoredClose: () -> Unit = {
                try {
                    monitor.close()
                } catch (_: Throwable) {
                }
                originalClose()
            }

            val store =
                KotlinToolStoreAdapter(
                    transport = transport,
                    serverDirProvider = {
                        val translator = io.ltirom.tooling.client.WslPathTranslator()
                        val p = translator.toWslPath(tempHome.path)
                        "$p/.ltirom/server/current"
                    },
                )

            val manager =
                ToolManager(
                    port = store,
                    catalog = ToolGroupCatalog,
                )

            return RealToolHost(
                mode = mode,
                tempHome = tempHome,
                serverProcess = process,
                supervisor = testSupervisor,
                transport = transport,
                store = store,
                manager = manager,
                closeAction = monitoredClose,
            )
        }
    }
}

class TestSupervisor(private val delegate: DaemonSupervisorPort) : DaemonSupervisorPort {
    @Volatile
    var allowRelaunch: Boolean = true

    override suspend fun ensureStarted(): ServerConnectionDescriptor {
        if (!allowRelaunch) {
            val current = delegate.getConnectionInfo()
            if (current != null) return current
            throw java.net.ConnectException("Build service is offline (relaunch disabled for test)")
        }
        return delegate.ensureStarted()
    }

    override suspend fun ensureStarted(distro: String): ServerConnectionDescriptor {
        if (!allowRelaunch) {
            val current = delegate.getConnectionInfo()
            if (current != null) return current
            throw java.net.ConnectException("Build service is offline (relaunch disabled for test)")
        }
        return delegate.ensureStarted(distro)
    }

    override suspend fun getConnectionInfo(): ServerConnectionDescriptor? = delegate.getConnectionInfo()

    override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean = if (!allowRelaunch) {
        false
    } else {
        delegate.isHealthy(
            info,
        )
    }

    override suspend fun shutdownDaemon(): Boolean = delegate.shutdownDaemon()

    override suspend fun shutdownDaemon(force: Boolean): Boolean = delegate.shutdownDaemon(force)

    override val lastStartAdopted: Boolean get() = delegate.lastStartAdopted
    override val lastLaunchErrorLogTail: String? get() = delegate.lastLaunchErrorLogTail

    override fun close() = delegate.close()
}
