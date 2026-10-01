package io.ltirom.tooling.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
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

    /**
     * The process this manager launched, bound to the service it started (distro + pid). Only a stop of
     * that same service may destroy it; retiring a service in another distro never touches it.
     */
    private class OwnedProcess(val distro: String, val pid: Long, val process: Process) {
        fun belongsTo(info: ServerConnectionDescriptor?) = info != null && info.distro == distro && info.pid == pid
    }

    private var owned: OwnedProcess? = null
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
            // CIO's own default cuts every request at 15 s; each request sets its own limit instead (a command
            // runs as long as its timeoutMs allows).
            engine { requestTimeout = 0 }
            install(HttpTimeout)
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

    public fun isRunning(): Boolean = owned?.takeIf { it.belongsTo(connectionInfo) }?.process?.isAlive == true

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
        val before = connectionInfo
        val info = resolveConnection(distro, activeVersion, home, javaHome)
        // A newly adopted or launched service: any other LtiRom service in the distro is a leftover (every
        // earlier launch kept running) and a request that lands on one fails with "run not found".
        if (info != before) retireStrayServices(info)
        info
    }

    private suspend fun resolveConnection(
        distro: String,
        activeVersion: String?,
        home: String?,
        javaHome: String?,
    ): ServerConnectionDescriptor {
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
                return current
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
                    return existing
                } else if (sInfo.activeRuns > 0 || !retireForUpdate(existing)) {
                    // Busy, or it would not stop cleanly: keep using it rather than kill work or run two.
                    connectionInfo = existing
                    lastStartAdopted = true
                    isUpdateWaiting = true
                    return existing
                }
            } else {
                connectionInfo = existing
                lastStartAdopted = true
                isUpdateWaiting = false
                return existing
            }
        }

        lastStartAdopted = false
        isUpdateWaiting = false
        val info = launchDaemon(distro, boundHome, boundJavaHome)
        connectionInfo = info
        return info
    }

    /**
     * Asks every LtiRom service in [keep]'s distro other than [keep] to shut down. Non-forced: a service with
     * active runs refuses and keeps running. A build too old to have the shutdown endpoint gets SIGTERM, but
     * only when its health report shows no active runs; one that doesn't answer at all is left alone.
     */
    private suspend fun retireStrayServices(keep: ServerConnectionDescriptor) {
        val strays = withContext(Dispatchers.IO) {
            val ps = cli.execute(keep.distro, listOf("ps", "-eo", "pid=,args="), timeoutSeconds = 5).output
            val ss = cli.execute(keep.distro, listOf("ss", "-ltnpH"), timeoutSeconds = 5).output
            parseLtiRomServices(ps, ss, keep.host, keep.distro).filter { it.pid != keep.pid }
        }
        for (stray in strays) {
            if (prober.requestShutdown(stray, force = false)) continue
            if (prober.getServerInfo(stray)?.activeRuns == 0) {
                withContext(Dispatchers.IO) {
                    cli.execute(keep.distro, listOf("kill", "-TERM", stray.pid.toString()), timeoutSeconds = 5)
                }
            }
        }
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
        owned = OwnedProcess(distro, launchResult.pid, launchResult.process)

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

    /**
     * Replaces an outdated service only when it agrees to stop: a non-forced shutdown, which the service
     * refuses (and never kills) if a run started after we looked, followed by confirmation from its process
     * identity that it is gone (a failed health check proves nothing). Returns false when the old service
     * is still there or its exit can't be established, so the caller must not launch a second one.
     */
    private suspend fun retireForUpdate(info: ServerConnectionDescriptor): Boolean {
        if (!stopDaemonInternal(info, force = false)) return false
        repeat(RETIRE_POLLS) {
            when (withContext(Dispatchers.IO) { stateStore.isProcessAlive(info.distro, info.pid) }) {
                false -> return true
                null -> return false
                true -> delay(RETIRE_POLL_MS)
            }
        }
        return false
    }

    private suspend fun stopDaemonInternal(info: ServerConnectionDescriptor, force: Boolean): Boolean {
        val stoppedCleanly = prober.requestShutdown(info, force)

        if (stoppedCleanly || force) {
            owned?.takeIf { it.belongsTo(info) }?.let { o ->
                if (o.process.isAlive) {
                    o.process.waitFor(2, TimeUnit.SECONDS)
                    if (o.process.isAlive) o.process.destroyForcibly()
                }
                owned = null
            }
            if (connectionInfo?.distro == info.distro && connectionInfo?.pid == info.pid) connectionInfo = null
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

    internal companion object {
        private val TOKEN_ARG = Regex("""--token\s+(\S+)""")
        private val LISTENER = Regex(""":(\d+)\s.*\bpid=(\d+),""")

        /**
         * LtiRom services in `ps -eo pid=,args=` output, with their port from `ss -ltnpH` and the token from
         * their own command line (same user, so readable).
         */
        internal fun parseLtiRomServices(
            ps: String,
            ss: String,
            host: String,
            distro: String,
        ): List<ServerConnectionDescriptor> {
            val portByPid = ss.lines().mapNotNull { LISTENER.find(it) }
                .associate { it.groupValues[2].toLong() to it.groupValues[1].toInt() }
            return ps.lines().map { it.trim() }.filter { "ltirom-server" in it }.mapNotNull { line ->
                val pid = line.substringBefore(' ').toLongOrNull() ?: return@mapNotNull null
                val token = TOKEN_ARG.find(line)?.groupValues?.get(1) ?: return@mapNotNull null
                val port = portByPid[pid] ?: return@mapNotNull null
                ServerConnectionDescriptor(host = host, port = port, token = token, pid = pid, distro = distro)
            }
        }

        /** How long a retired service gets to exit before the update is treated as refused (10 s). */
        const val RETIRE_POLLS = 20
        const val RETIRE_POLL_MS = 500L
    }
}
