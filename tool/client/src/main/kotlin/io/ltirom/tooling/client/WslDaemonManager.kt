package io.ltirom.tooling.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ltirom.tooling.client.wsl.DaemonHealthProber
import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import io.ltirom.tooling.client.wsl.WslExt4Deployer
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.TimeUnit

public typealias ServerConnectionInfo = ServerConnectionDescriptor

public data class WslDaemonConfig(
    val distroName: String = "Ubuntu",
    val serverBinaryWslPath: String? = null,
    val autoStart: Boolean = true,
    val startupTimeoutMs: Long = 20_000L
)

/**
 * Supervisor orchestrating the lifecycle of the LtiRomServer daemon in WSL.
 * Adheres to Clean Architecture: delegates OS detection to [WslEnvironmentDetector],
 * ext4 deployment to [WslExt4Deployer], lockfile reading to [WslStateStore],
 * and implements [DaemonSupervisorPort].
 */
public class WslDaemonManager(
    private val config: WslDaemonConfig = WslDaemonConfig(),
    private val cli: WslCliExecutor = WslCliExecutor(),
    private val detector: WslEnvironmentDetector = WslEnvironmentDetector(cli),
    private val deployer: WslExt4Deployer = WslExt4Deployer(cli),
    private val stateStore: WslStateStore = WslStateStore(cli),
    private val launcher: WslProcessLauncher = WslProcessLauncher(config.startupTimeoutMs)
) : DaemonSupervisorPort {

    private val mutex = Mutex()
    private var process: Process? = null
    private var connectionInfo: ServerConnectionDescriptor? = null
    private val prober: DaemonHealthProber by lazy { DaemonHealthProber(httpClient) }

    override var lastStartAdopted: Boolean = false
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
        }
    }

    public fun isRunning(): Boolean = connectionInfo != null && process?.isAlive == true

    override suspend fun ensureStarted(): ServerConnectionDescriptor = mutex.withLock {
        val current = connectionInfo
        if (current != null && isHealthy(current)) {
            lastStartAdopted = true
            return@withLock current
        }

        // 1. Check existing daemon lockfile in WSL (VS Code pattern for instant zero-latency reconnection)
        val existing = checkExistingDaemon(config.distroName)
        if (existing != null && isHealthy(existing)) {
            connectionInfo = existing
            lastStartAdopted = true
            return@withLock existing
        }

        // 2. Launch daemon
        lastStartAdopted = false
        val info = launchDaemon()
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

    public fun findServerBinary(distro: String): String {
        val home = detector.resolveWslUserHome(distro)
        return deployer.ensureInstalled(distro, home, config.serverBinaryWslPath)
    }

    private suspend fun launchDaemon(): ServerConnectionDescriptor = withContext(Dispatchers.IO) {
        val distro = config.distroName
        val binary = findServerBinary(distro)
        val wslIp = detector.resolveWslIp(distro)

        val launchResult = launcher.launch(distro, binary)
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
                distro = distro
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
            distro = distro
        )
    }

    override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean {
        return prober.isHealthy(info)
    }

    public suspend fun isHealthy(): Boolean {
        val info = getConnectionInfo() ?: return false
        return isHealthy(info)
    }

    public suspend fun getServerInfo(): WslServerInfo? {
        val info = ensureStarted()
        return prober.getServerInfo(info)
    }

    public suspend fun getAvailableTools(): List<ToolStatusInfo> {
        val info = ensureStarted()
        return prober.getAvailableTools(info)
    }

    override suspend fun shutdownDaemon(): Boolean = mutex.withLock {
        val info = connectionInfo ?: return@withLock true
        var stoppedCleanly = false
        try {
            val response = httpClient.post("${info.httpBaseUrl}/api/v1/system/shutdown") {
                header(HttpHeaders.Authorization, "Bearer ${info.token}")
            }
            stoppedCleanly = response.status.isSuccess()
        } catch (_: Exception) {}

        process?.let { p ->
            if (p.isAlive) {
                p.waitFor(2, TimeUnit.SECONDS)
                if (p.isAlive) p.destroyForcibly()
            }
        }
        process = null
        connectionInfo = null
        stoppedCleanly
    }

    override fun close() {
        runBlocking {
            runCatching { shutdownDaemon() }
            runCatching { httpClient.close() }
        }
    }
}
