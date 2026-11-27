package io.ltirom.tooling.core.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ToolExecutionRequest(
    val toolId: String,
    val arguments: List<String>,
    val workingDirectory: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val stdinText: String? = null,
    val timeoutMs: Long = 900_000L,
    val acceptedExitCodes: Set<Int> = setOf(0),
    val streamOutput: Boolean = false,
    val purpose: RunPurpose = RunPurpose.WORKSPACE,
)

@Serializable
data class ToolExecutionResponse(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
)

/**
 * Events of a run's stream. The `@SerialName`s are the wire contract: they are the type names the build service
 * writes (and has persisted in its run journals), so they must not follow this package.
 */
@Serializable
sealed class StreamEvent {
    @Serializable
    @SerialName("io.ltirom.server.model.StreamEvent.OutputChunk")
    data class OutputChunk(val text: String, val isError: Boolean = false) : StreamEvent()

    @Serializable
    @SerialName("io.ltirom.server.model.StreamEvent.ProgressUpdate")
    data class ProgressUpdate(val fraction: Float, val message: String? = null) : StreamEvent()

    @Serializable
    @SerialName("io.ltirom.server.model.StreamEvent.ExecutionFinished")
    data class ExecutionFinished(val exitCode: Int, val durationMs: Long, val summary: String? = null) :
        StreamEvent()

    @Serializable
    @SerialName("io.ltirom.server.model.StreamEvent.Heartbeat")
    data class Heartbeat(val timestampEpochMs: Long) : StreamEvent()
}

@Serializable
data class WslServerInfo(
    val status: String,
    val distro: String,
    val kernelRelease: String,
    val architecture: String,
    val javaVersion: String,
    val serverUptimeMs: Long,
    val availableTools: List<String> = emptyList(),
    val activeRuns: Int = 0,
    val protocolVersion: String = "1.2.0",
    val serverVersion: String? = null,
    /** What the service still tracks; null from a service that does not report it. */
    val activeWork: List<String>? = null,
)

@Serializable
data class PathTranslationRequest(val path: String, val direction: PathTranslationDirection)

@Serializable
enum class PathTranslationDirection {
    WINDOWS_TO_WSL,
    WSL_TO_WINDOWS,
}

@Serializable
data class PathTranslationResponse(val originalPath: String, val translatedPath: String)

@Serializable
data class ToolStatusInfo(
    val tool: String,
    val installed: Boolean,
    val path: String = "",
    val capabilities: List<String> = emptyList(),
    val source: String? = null,
    val reason: String? = null,
)

sealed interface ToolListResult {
    data class Tools(val list: List<ToolStatusInfo>) : ToolListResult

    /** The service answered, but not with a tool list (an HTTP error, an unreadable body). */
    data class ServiceError(val reason: String) : ToolListResult

    /** There was no answer at all: the connection failed or timed out. */
    data class Unreachable(val reason: String) : ToolListResult
}

@Serializable
data class ShutdownResponse(val status: String)

@Serializable
data class BinaryTransferResponse(val path: String, val bytesTransferred: Long, val durationMs: Long)

@Serializable
enum class SessionStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

@Serializable
data class SessionSummary(
    val sessionId: String,
    val toolId: String,
    val arguments: List<String>,
    val startTimeEpochMs: Long,
    val status: SessionStatus,
    val exitCode: Int? = null,
    val durationMs: Long? = null,
)

@Serializable
data class SequencedStreamEvent(val seq: Long, val event: StreamEvent)
