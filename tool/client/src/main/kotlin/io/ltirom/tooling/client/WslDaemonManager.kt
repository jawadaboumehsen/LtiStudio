package io.ltirom.tooling.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ltirom.tooling.client.wsl.DaemonHealthProber
import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import io.ltirom.tooling.client.wsl.WslProcessLauncher
import io.ltirom.tooling.client.wsl.WslStateStore
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import io.ltirom.tooling.core.remote.ToolStatusInfo
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.ide.lti.core.model.setup.SetupEnvironment
import java.util.concurrent.TimeUnit

public typealias ServerConnectionInfo = ServerConnectionDescriptor

public data class WslDaemonConfig(
    val distroName: String? = null,
    val serverBinaryWslPath: String? = null,
    val autoStart: Boolean = true,
    val startupTimeoutMs: Long = 20_000L,
)

/**
 * Supervisor orchestrating the lifecycle of the LtiRomServer daemon in WSL.
 * Adheres to Clean Architecture: delegates OS detection to [WslEnvironmentDetector],
 * lockfile reading to [WslStateStore] (installation belongs to `ServerArtifactInstaller`),
 * and implements [DaemonSupervisorPort].
 */
public class WslDaemonManager(
    private val config: WslDaemonConfig = WslDaemonConfig(),
    private val cli: WslCliExecutor = WslCliExecutor(),
    private val detector: WslEnvironmentDetector = WslEnvironmentDetector(cli),
    private val stateStore: WslStateStore = WslStateStore(cli),
    private val launcher: WslProcessLauncher = WslProcessLauncher(config.startupTimeoutMs),
    customProber: DaemonHealthProber? = null,
) : DaemonSupervisorPort {

    private val mutex = Mutex()
    private var process: Process? = null
    private var connectionInfo: ServerConnectionDescriptor? = null

    /**
     * Target of the last admitted start (distro, home, Java). Callers without an operation context
     * (the transport's per-request `ensureStarted()`) reconnect or relaunch against this same target;
     * it is never a UI selection and is only replaced by the next admitted start.
     */
    private var boundDistro: String? = null
    private var boundHome: String? = null
    private var boundJavaHome: String? = null

    /**
     * Version the last admitted start asked for. A start without a version (the transport's per-request
     * `ensureStarted()`) is still judged against it, so a deferred update stays visible and is applied
     * once the old service has no active runs, instead of being forgotten by the next status read.
     */
    private var desiredVersion: String? = null
    private val prober: DaemonHealthProber by lazy { customProber ?: DaemonHealthProber(httpClient) }

    override var lastStartAdopted: Boolean = false
        private set

    public var isUpdateWaiting: Boolean = false
        private set

    override val lastLaunchErrorLogTail: String? get() = launcher.lastLogTail

    public val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    public val httpClient: HttpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(this@WslDaemonManager.json)
            }
            install(WebSockets)
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.NONE
            }
        }
    }

    public fun isRunning(): Boolean = connectionInfo != null && process?.isAlive == true

    public suspend fun ensureStarted(environment: SetupEnvironment): ServerConnectionDescriptor = ensureRunningVersion(
        distro = environment.distro,
        activeVersion = null,
        home = environment.home,
        javaHome = null,
    )

    override suspend fun ensureStarted(distro: String): ServerConnectionDescriptor =
        ensureRunningVersion(distro = distro, activeVersion = null, home = null, javaHome = null)

    public suspend fun ensureStarted(distro: String, home: String? = null): ServerConnectionDescriptor =
        ensureRunningVersion(distro = distro, activeVersion = null, home = home, javaHome = null)

    override suspend fun ensureStarted(): ServerConnectionDescriptor {
        val distro = connectionInfo?.distro
            ?: boundDistro
            ?: config.distroName
            ?: error("Build service has not been started yet: run the environment check first.")
        return ensureRunningVersion(distro = distro, activeVersion = null, home = null, javaHome = null)
    }

    public suspend fun ensureRunningVersion(
        distro: String,
        activeVersion: String? = null,
        home: String? = null,
        javaHome: String? = null,
    ): ServerConnectionDescriptor = mutex.withLock {
        if (distro != boundDistro) {
            boundHome = null
            boundJavaHome = null
            desiredVersion = null
        }
        boundDistro = distro
        activeVersion?.let { desiredVersion = it }
        val wanted = desiredVersion
        home?.let { boundHome = it }
        javaHome?.let { boundJavaHome = it }
        val current = connectionInfo
        if (current != null && current.distro == distro && isHealthy(current)) {
            val sInfo = prober.getServerInfo(current)
            if (sInfo != null && (wanted == null || sInfo.serverVersion == wanted)) {
                lastStartAdopted = true
                isUpdateWaiting = false
                return@withLock current
            }
        }

        val existing = checkExistingDaemon(distro)
        if (existing != null && isHealthy(existing)) {
            val sInfo = prober.getServerInfo(existing)
            if (sInfo != null) {
                if (wanted == null || sInfo.serverVersion == wanted) {
                    connectionInfo = existing
                    lastStartAdopted = true
                    isUpdateWaiting = false
                    return@withLock existing
                } else {
                    if (sInfo.activeRuns == 0) {
                        stopDaemonInternal(existing, force = true)
                    } else {
                        connectionInfo = existing
                        lastStartAdopted = true
                        isUpdateWaiting = true
                        return@withLock existing
                    }
                }
            } else {
                connectionInfo = existing
                lastStartAdopted = true
                isUpdateWaiting = false
                return@withLock existing
            }
        }

        lastStartAdopted = false
        isUpdateWaiting = false
        val info = launchDaemon(distro, boundHome, boundJavaHome)
        connectionInfo = info
        info
    }

    override suspend fun getConnectionInfo(): ServerConnectionDescriptor? = mutex.withLock { connectionInfo }

    public fun checkExistingDaemon(distro: String): ServerConnectionDescriptor? {
        val wslIp = detector.resolveWslIp(distro)
        return stateStore.readState(distro, wslIp)
    }

    public fun listWslDistros(): List<String> = detector.listDistros()

    public fun resolveWslIp(distro: String): String = detector.resolveWslIp(distro)

    public fun resolveWslUserHome(distro: String): String = detector.resolveWslUserHome(distro)

    public fun findServerBinary(distro: String, home: String? = null): String {
        if (config.serverBinaryWslPath != null) return config.serverBinaryWslPath
        val userHome = home ?: detector.resolveWslUserHome(distro)
        val ext4Current = "$userHome/.ltirom/server/current/bin/ltirom-server"
        val exists = cli.execute(distro, listOf("test", "-x", ext4Current), timeoutSeconds = 3).exitCode == 0
        check(exists) {
            "Build service is not installed in $distro ($ext4Current missing). Run the environment check to install it."
        }
        return ext4Current
    }

    private suspend fun launchDaemon(
        distro: String,
        home: String? = null,
        javaHome: String? = null,
    ): ServerConnectionDescriptor = withContext(Dispatchers.IO) {
        val binary = findServerBinary(distro, home)
        val wslIp = detector.resolveWslIp(distro)

        val launchResult = if (javaHome != null) {
            launcher.launch(distro, binary, javaHome)
        } else {
            launcher.launch(distro, binary)
        }
        process = launchResult.process

        // Determine working host IP: test WSL IP then fallback to 127.0.0.1
        val candidateHosts = listOf(wslIp, "127.0.0.1")
        var workingHost: String? = null

        for (host in candidateHosts) {
            val testInfo = ServerConnectionDescriptor(
                host = host,
                port = launchResult.port,
                token = launchResult.token,
                pid = launchResult.pid,
                distro = distro,
            )
            if (isHealthy(testInfo)) {
                workingHost = host
                break
            }
        }

        val resolvedHost = workingHost ?: wslIp

        ServerConnectionDescriptor(
            host = resolvedHost,
            port = launchResult.port,
            token = launchResult.token,
            pid = launchResult.pid,
            distro = distro,
        )
    }

    override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean = prober.isHealthy(info)

    public suspend fun isHealthy(): Boolean {
        val info = getConnectionInfo() ?: return false
        return isHealthy(info)
    }

    public suspend fun getServerInfo(): WslServerInfo? {
        val info = ensureStarted()
        return prober.getServerInfo(info)
    }

    /**
     * Reads the running service's info for [environment] without starting, adopting or restarting
     * anything, so a status read never changes [isUpdateWaiting] or the connection.
     */
    public suspend fun getServerInfo(environment: SetupEnvironment): WslServerInfo? {
        val info = mutex.withLock { connectionInfo }?.takeIf { it.distro == environment.distro }
            ?: checkExistingDaemon(environment.distro)
            ?: return null
        return prober.getServerInfo(info)
    }

    public suspend fun getAvailableTools(): List<ToolStatusInfo> {
        val info = ensureStarted()
        return prober.getAvailableTools(info)
    }

    public suspend fun getAvailableTools(environment: SetupEnvironment): List<ToolStatusInfo> {
        val info = ensureStarted(environment)
        return prober.getAvailableTools(info)
    }

    override suspend fun shutdownDaemon(): Boolean = shutdownDaemon(force = false)

    override suspend fun shutdownDaemon(force: Boolean): Boolean = mutex.withLock {
        val info = connectionInfo ?: return@withLock true
        stopDaemonInternal(info, force)
    }

    private suspend fun stopDaemonInternal(info: ServerConnectionDescriptor, force: Boolean): Boolean {
        var stoppedCleanly = false
        try {
            val response = httpClient.post("${info.httpBaseUrl}/api/v1/system/shutdown?force=$force") {
                header(HttpHeaders.Authorization, "Bearer ${info.token}")
            }
            stoppedCleanly = response.status.isSuccess()
        } catch (_: Exception) {}

        if (stoppedCleanly || force) {
            process?.let { p ->
                if (p.isAlive) {
                    p.waitFor(2, TimeUnit.SECONDS)
                    if (p.isAlive) p.destroyForcibly()
                }
            }
            process = null
            connectionInfo = null
        }
        return stoppedCleanly
    }

    /**
     * Closes only this manager's local HTTP client -- deliberately does NOT call
     * [shutdownDaemon]. The remote daemon is meant to outlive a single GUI session
     * (VS Code Remote-style lockfile reconnection via [WslStateStore.readState]/
     * [checkExistingDaemon]), so closing this manager must not kill it.
     */
    override fun close() {
        runCatching { httpClient.close() }
    }
}
