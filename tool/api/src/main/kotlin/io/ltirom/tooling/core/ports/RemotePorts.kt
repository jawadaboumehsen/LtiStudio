package io.ltirom.tooling.core.ports

import io.ltirom.tooling.core.RunOutputUnavailableException
import io.ltirom.tooling.core.remote.PathTranslationDirection
import io.ltirom.tooling.core.remote.RunCancelResponse
import io.ltirom.tooling.core.remote.RunHandle
import io.ltirom.tooling.core.remote.RunStatus
import io.ltirom.tooling.core.remote.RunStatusValue
import io.ltirom.tooling.core.remote.SequencedStreamEvent
import io.ltirom.tooling.core.remote.SessionSummary
import io.ltirom.tooling.core.remote.StartRunRequest
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Serializable
data class StopClientResult(val stopped: Boolean, val unconfirmedExecutionIds: List<String> = emptyList())

/**
 * Domain descriptor for remote server connection parameters.
 */
data class ServerConnectionDescriptor(
    val host: String,
    val port: Int,
    val token: String,
    val pid: Long,
    val distro: String,
) {
    val httpBaseUrl: String get() = "http://$host:$port"
    val wsBaseUrl: String get() = "ws://$host:$port"
}

/**
 * Port for bi-directional path translation between local host and remote environment.
 * Adheres to the Open/Closed Principle: consumers only interact with this port,
 * allowing WSL, SSH, or Docker implementations to be plugged in seamlessly.
 */
interface PathTranslatorPort {
    /** Translates a local path (e.g. Windows C:\...) to a remote path (e.g. WSL /mnt/c/...). */
    fun toRemote(localPath: String): String

    /** Translates a remote path (e.g. WSL /mnt/c/...) to a local path (e.g. Windows C:\...). */
    fun toLocal(remotePath: String): String

    /** Translates path via remote server API if local heuristics are insufficient. */
    suspend fun translateRemote(path: String, direction: PathTranslationDirection): String
}

/**
 * Port for remote transport communication (HTTP REST and WebSockets).
 * Decouples domain repositories from concrete network libraries (e.g. Ktor).
 */
interface RemoteTransportPort {
    /** Executes a tool command remotely and returns the full execution response. */
    suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse

    /** Streams tool command output remotely as a reactive [Flow] of [StreamEvent]s. */
    fun stream(request: ToolExecutionRequest): Flow<StreamEvent>

    /** Checks the health and status of the remote server. */
    suspend fun checkHealth(): WslServerInfo?

    /** Retrieves the list of available tools on the remote server. */
    suspend fun listTools(): ToolListResult

    /** Re-scans dynamic tool definitions on the remote server and returns the updated tool list. */
    suspend fun refreshTools(): ToolListResult

    /** Uploads binary content to a remote path under allowed directories. */
    suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean

    /** Downloads binary content from a remote path under allowed directories. */
    suspend fun downloadFile(remotePath: String): ByteArray?

    /** Requests graceful shutdown of the remote server. */
    suspend fun shutdown(): Boolean

    /** Sends a client heartbeat to keep command admission open. Returns true if accepted (204). */
    suspend fun sendHeartbeat(): Boolean = true

    /** Sends a client heartbeat to a specific connection descriptor. Returns true if accepted (204). */
    suspend fun sendHeartbeat(descriptor: ServerConnectionDescriptor): Boolean = sendHeartbeat()

    /** Requests the server to close command admission, cancel active executions, and stop. */
    suspend fun stopClient(): StopClientResult = StopClientResult(stopped = true)

    /** Cancels an active remote execution by ID. */
    suspend fun cancel(executionId: String, signal: String = "SIGTERM"): Boolean = false

    /** Lists execution sessions available on the remote server. */
    suspend fun listSessions(): List<SessionSummary> = emptyList()

    /** Gets details for an execution session by ID. */
    suspend fun getSession(sessionId: String): SessionSummary? = null

    /** Replays buffered stream events from a session starting at sequence number [fromSeq]. */
    suspend fun replaySession(sessionId: String, fromSeq: Long = 0L): List<SequencedStreamEvent> = emptyList()

    /** Starts a durable execution run on the remote daemon. */
    suspend fun startRun(request: StartRunRequest): RunHandle =
        throw UnsupportedOperationException("startRun not implemented")

    /** Retrieves the status of a specific run by ID. */
    suspend fun getRun(runId: String): RunStatus? = null

    /** Lists runs on the remote daemon, optionally filtered by workspace lock. */
    suspend fun listRuns(workspaceLock: String? = null): List<RunStatus> = emptyList()

    /** Cancels a remote run by ID. */
    suspend fun cancelRun(runId: String, signal: String = "SIGTERM"): RunCancelResponse? = null

    /** Streams tool command output remotely as a reactive [Flow] of [SequencedStreamEvent]s. */
    fun streamSequenced(request: ToolExecutionRequest): Flow<SequencedStreamEvent> = flow {
        var seq = 1L
        stream(request).collect { ev ->
            emit(SequencedStreamEvent(seq++, ev))
        }
    }

    /** Attaches to an execution run, replaying buffered events from [fromSeq] and streaming live events. */
    fun attachRun(runId: String, fromSeq: Long = 1L): Flow<SequencedStreamEvent> = flow {
        var nextSeq = fromSeq
        val initial = runCatching { replaySession(runId, nextSeq) }.getOrDefault(emptyList())
        for (ev in initial) {
            if (ev.seq >= nextSeq) {
                emit(ev)
                nextSeq = ev.seq + 1
            }
        }
        var pollDelay = 50.milliseconds
        while (true) {
            val run = runCatching { getRun(runId) }.getOrNull()
            if (run != null &&
                run.status in
                setOf(RunStatusValue.COMPLETED, RunStatusValue.FAILED, RunStatusValue.CANCELLED, RunStatusValue.INTERRUPTED)
            ) {
                val more = runCatching { replaySession(runId, nextSeq) }.getOrDefault(emptyList())
                var finishedEmitted = false
                for (ev in more) {
                    if (ev.seq >= nextSeq) {
                        emit(ev)
                        nextSeq = ev.seq + 1
                        if (ev.event is StreamEvent.ExecutionFinished) {
                            finishedEmitted = true
                        }
                    }
                }
                if (!finishedEmitted) {
                    throw RunOutputUnavailableException(runId, run)
                }
                return@flow
            }
            emit(SequencedStreamEvent(nextSeq, StreamEvent.Heartbeat(System.currentTimeMillis())))
            delay(pollDelay)
            // Capped exponential backoff: fast polling while a run is likely still starting,
            // settling to a 2s cadence for long-running builds instead of hammering getRun()
            // every 50ms for the run's entire duration.
            pollDelay = (pollDelay * 2).coerceAtMost(2.seconds)
        }
    }

    /** Requests graceful shutdown of the remote server, optionally forcing even with active runs. */
    suspend fun shutdownDaemon(force: Boolean = false): Boolean = shutdown()
}

/**
 * Port for supervising the lifecycle of a remote daemon process.
 * Separates lifecycle management (bootstrapping, health monitoring, shutdown)
 * from tool command execution (Interface Segregation Principle).
 */
interface DaemonSupervisorPort : AutoCloseable {
    /** Ensures the daemon is running and returns its active connection descriptor. */
    suspend fun ensureStarted(): ServerConnectionDescriptor

    /** Ensures the daemon is running and returns its active connection descriptor for the specified distro. */
    suspend fun ensureStarted(distro: String): ServerConnectionDescriptor = ensureStarted()

    /** Returns current connection info if already started, without launching a new instance. */
    suspend fun getConnectionInfo(): ServerConnectionDescriptor?

    /** Verifies whether the specified connection descriptor is currently responsive. */
    suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean

    /** Shuts down the daemon process cleanly. */
    suspend fun shutdownDaemon(): Boolean

    /** Shuts down the daemon process cleanly, optionally forcing shutdown even if runs are active. */
    suspend fun shutdownDaemon(force: Boolean = false): Boolean = shutdownDaemon()

    /** Whether the most recent ensureStarted() call adopted an existing lockfile daemon without launching a process. */
    val lastStartAdopted: Boolean get() = false

    /** Recent launcher log tail if the most recent launch attempt failed. */
    val lastLaunchErrorLogTail: String? get() = null
}
