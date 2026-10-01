package io.ltirom.tooling.core.ports

import io.ltirom.tooling.core.remote.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Domain descriptor for remote server connection parameters.
 */
public data class ServerConnectionDescriptor(
    val host: String,
    val port: Int,
    val token: String,
    val pid: Long,
    val distro: String
) {
    val httpBaseUrl: String get() = "http://$host:$port"
    val wsBaseUrl: String get() = "ws://$host:$port"
}

/**
 * Port for bi-directional path translation between local host and remote environment.
 * Adheres to the Open/Closed Principle: consumers only interact with this port,
 * allowing WSL, SSH, or Docker implementations to be plugged in seamlessly.
 */
public interface PathTranslatorPort {
    /** Translates a local path (e.g. Windows C:\...) to a remote path (e.g. WSL /mnt/c/...). */
    public fun toRemote(localPath: String): String

    /** Translates a remote path (e.g. WSL /mnt/c/...) to a local path (e.g. Windows C:\...). */
    public fun toLocal(remotePath: String): String

    /** Translates path via remote server API if local heuristics are insufficient. */
    public suspend fun translateRemote(path: String, direction: PathTranslationDirection): String
}

/**
 * Port for remote transport communication (HTTP REST and WebSockets).
 * Decouples domain repositories from concrete network libraries (e.g. Ktor).
 */
public interface RemoteTransportPort {
    /** Executes a tool command remotely and returns the full execution response. */
    public suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse

    /** Streams tool command output remotely as a reactive [Flow] of [StreamEvent]s. */
    public fun stream(request: ToolExecutionRequest): Flow<StreamEvent>

    /** Checks the health and status of the remote server. */
    public suspend fun checkHealth(): WslServerInfo?

    /** Retrieves the list of available tools on the remote server. */
    public suspend fun listTools(): ToolListResult

    /** Re-scans dynamic tool definitions on the remote server and returns the updated tool list. */
    public suspend fun refreshTools(): ToolListResult

    /** Uploads binary content to a remote path under allowed directories. */
    public suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean

    /** Downloads binary content from a remote path under allowed directories. */
    public suspend fun downloadFile(remotePath: String): ByteArray?

    /** Requests graceful shutdown of the remote server. */
    public suspend fun shutdown(): Boolean

    /** Cancels an active remote execution by ID. */
    public suspend fun cancel(executionId: String, signal: String = "SIGTERM"): Boolean = false

    /** Lists execution sessions available on the remote server. */
    public suspend fun listSessions(): List<SessionSummary> = emptyList()

    /** Gets details for an execution session by ID. */
    public suspend fun getSession(sessionId: String): SessionSummary? = null

    /** Replays buffered stream events from a session starting at sequence number [fromSeq]. */
    public suspend fun replaySession(sessionId: String, fromSeq: Long = 0L): List<SequencedStreamEvent> = emptyList()

    /** Starts a durable execution run on the remote daemon. */
    public suspend fun startRun(request: StartRunRequest): RunHandle = error("startRun not implemented")

    /** Retrieves the status of a specific run by ID. */
    public suspend fun getRun(runId: String): RunStatus? = null

    /** Lists runs on the remote daemon, optionally filtered by workspace lock. */
    public suspend fun listRuns(workspaceLock: String? = null): List<RunStatus> = emptyList()

    /** Cancels a remote run by ID. */
    public suspend fun cancelRun(runId: String, signal: String = "SIGTERM"): RunCancelResponse? = null

    /** Streams tool command output remotely as a reactive [Flow] of [SequencedStreamEvent]s. */
    public fun streamSequenced(request: ToolExecutionRequest): Flow<SequencedStreamEvent> = flow {
        var seq = 1L
        stream(request).collect { ev ->
            emit(SequencedStreamEvent(seq++, ev))
        }
    }

    /** Attaches to an execution run, replaying buffered events from [fromSeq] and streaming live events. */
    public fun attachRun(runId: String, fromSeq: Long = 1L): Flow<SequencedStreamEvent> = flow {
        var nextSeq = fromSeq
        val initial = runCatching { replaySession(runId, nextSeq) }.getOrDefault(emptyList())
        for (ev in initial) {
            if (ev.seq >= nextSeq) {
                emit(ev)
                nextSeq = ev.seq + 1
            }
        }
        var pollDelayMs = 50L
        while (true) {
            val run = runCatching { getRun(runId) }.getOrNull()
            if (run != null && run.status in setOf(RunStatusValue.COMPLETED, RunStatusValue.FAILED, RunStatusValue.CANCELLED, RunStatusValue.INTERRUPTED)) {
                val more = runCatching { replaySession(runId, nextSeq) }.getOrDefault(emptyList())
                for (ev in more) {
                    if (ev.seq >= nextSeq) {
                        emit(ev)
                        nextSeq = ev.seq + 1
                    }
                }
                val exitCode = run.exitCode ?: if (run.status == RunStatusValue.FAILED) 1 else 0
                emit(SequencedStreamEvent(nextSeq, StreamEvent.ExecutionFinished(exitCode, 0L)))
                return@flow
            }
            emit(SequencedStreamEvent(nextSeq, StreamEvent.Heartbeat(System.currentTimeMillis())))
            delay(pollDelayMs)
            // Capped exponential backoff: fast polling while a run is likely still starting,
            // settling to a 2s cadence for long-running builds instead of hammering getRun()
            // every 50ms for the run's entire duration.
            pollDelayMs = (pollDelayMs * 2).coerceAtMost(2_000L)
        }
    }

    /** Requests graceful shutdown of the remote server, optionally forcing even with active runs. */
    public suspend fun shutdownDaemon(force: Boolean = false): Boolean = shutdown()

    /** Sends an activation request to activate a target toolchain install. */
    public suspend fun activateToolchain(request: io.ltirom.tooling.core.remote.ActivateToolchainRequest): io.ltirom.tooling.core.remote.ActivateToolchainResponse =
        error("activateToolchain not implemented")

    /** Retrieves the current toolchain state from the remote server. */
    public suspend fun getToolchainState(): io.ltirom.tooling.core.remote.ToolchainStateResponse? = null

    /** Retrieves the stored activation record for an activation request id. */
    public suspend fun getActivation(requestId: String): String? = null
}

/**
 * Port for supervising the lifecycle of a remote daemon process.
 * Separates lifecycle management (bootstrapping, health monitoring, shutdown)
 * from tool command execution (Interface Segregation Principle).
 */
public interface DaemonSupervisorPort : AutoCloseable {
    /** Ensures the daemon is running and returns its active connection descriptor. */
    public suspend fun ensureStarted(): ServerConnectionDescriptor

    /** Ensures the daemon is running and returns its active connection descriptor for the specified distro. */
    public suspend fun ensureStarted(distro: String): ServerConnectionDescriptor = ensureStarted()

    /** Returns current connection info if already started, without launching a new instance. */
    public suspend fun getConnectionInfo(): ServerConnectionDescriptor?

    /** Verifies whether the specified connection descriptor is currently responsive. */
    public suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean

    /** Shuts down the daemon process cleanly. */
    public suspend fun shutdownDaemon(): Boolean

    /** Shuts down the daemon process cleanly, optionally forcing shutdown even if runs are active. */
    public suspend fun shutdownDaemon(force: Boolean = false): Boolean = shutdownDaemon()

    /** Whether the most recent ensureStarted() call adopted an existing lockfile daemon without launching a process. */
    public val lastStartAdopted: Boolean get() = false

    /** Recent launcher log tail if the most recent launch attempt failed. */
    public val lastLaunchErrorLogTail: String? get() = null
}
