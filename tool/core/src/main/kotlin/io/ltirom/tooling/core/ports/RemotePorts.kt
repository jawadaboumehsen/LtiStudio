package io.ltirom.tooling.core.ports

import io.ltirom.tooling.core.remote.*
import kotlinx.coroutines.flow.Flow

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
    public suspend fun listTools(): List<ToolStatusInfo>

    /** Re-scans dynamic tool definitions on the remote server and returns the updated tool list. */
    public suspend fun refreshTools(): List<ToolStatusInfo>

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
}

/**
 * Port for supervising the lifecycle of a remote daemon process.
 * Separates lifecycle management (bootstrapping, health monitoring, shutdown)
 * from tool command execution (Interface Segregation Principle).
 */
public interface DaemonSupervisorPort : AutoCloseable {
    /** Ensures the daemon is running and returns its active connection descriptor. */
    public suspend fun ensureStarted(): ServerConnectionDescriptor

    /** Returns current connection info if already started, without launching a new instance. */
    public suspend fun getConnectionInfo(): ServerConnectionDescriptor?

    /** Verifies whether the specified connection descriptor is currently responsive. */
    public suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean

    /** Shuts down the daemon process cleanly. */
    public suspend fun shutdownDaemon(): Boolean
}
