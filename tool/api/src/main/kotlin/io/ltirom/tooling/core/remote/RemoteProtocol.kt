package io.ltirom.tooling.core.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class ToolExecutionRequest(
    val toolId: String,
    val arguments: List<String>,
    val workingDirectory: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val stdinText: String? = null,
    val timeoutMs: Long = 900_000L,
    val acceptedExitCodes: Set<Int> = setOf(0),
    val streamOutput: Boolean = false,
    val purpose: RunPurpose = RunPurpose.WORKSPACE
)

@Serializable
public data class ToolExecutionResponse(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long
)

/**
 * Events of a run's stream. The `@SerialName`s are the wire contract: they are the type names the build service
 * writes (and has persisted in its run journals), so they must not follow this package.
 */
@Serializable
public sealed class StreamEvent {
    @Serializable
    @SerialName("io.ltirom.server.model.StreamEvent.OutputChunk")
    public data class OutputChunk(val text: String, val isError: Boolean = false) : StreamEvent()

    @Serializable
    @SerialName("io.ltirom.server.model.StreamEvent.ProgressUpdate")
    public data class ProgressUpdate(val fraction: Float, val message: String? = null) : StreamEvent()

    @Serializable
    @SerialName("io.ltirom.server.model.StreamEvent.ExecutionFinished")
    public data class ExecutionFinished(
        val exitCode: Int,
        val durationMs: Long,
        val summary: String? = null
    ) : StreamEvent()

    @Serializable
    @SerialName("io.ltirom.server.model.StreamEvent.Heartbeat")
    public data class Heartbeat(val timestampEpochMs: Long) : StreamEvent()
}

@Serializable
public data class WslServerInfo(
    val status: String,
    val distro: String,
    val kernelRelease: String,
    val architecture: String,
    val javaVersion: String,
    val serverUptimeMs: Long,
    val availableTools: List<String> = emptyList(),
    val activeRuns: Int = 0,
    val protocolVersion: String = "1.2.0",
    val serverVersion: String? = null
)

@Serializable
public data class PathTranslationRequest(
    val path: String,
    val direction: PathTranslationDirection
)

@Serializable
public enum class PathTranslationDirection {
    WINDOWS_TO_WSL,
    WSL_TO_WINDOWS
}

@Serializable
public data class PathTranslationResponse(
    val originalPath: String,
    val translatedPath: String
)

@Serializable
public data class ToolStatusInfo(
    val tool: String,
    val installed: Boolean,
    val path: String = "",
    val capabilities: List<String> = emptyList(),
    val source: String? = null,
    val reason: String? = null
)

public sealed interface ToolListResult {
    public data class Tools(val list: List<ToolStatusInfo>) : ToolListResult
    public data class ServiceError(val reason: String) : ToolListResult
}

@Serializable
public data class ShutdownResponse(
    val status: String
)

@Serializable
public data class BinaryTransferResponse(
    val path: String,
    val bytesTransferred: Long,
    val durationMs: Long
)

@Serializable
public enum class SessionStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Serializable
public data class SessionSummary(
    val sessionId: String,
    val toolId: String,
    val arguments: List<String>,
    val startTimeEpochMs: Long,
    val status: SessionStatus,
    val exitCode: Int? = null,
    val durationMs: Long? = null
)

@Serializable
public data class SequencedStreamEvent(
    val seq: Long,
    val event: StreamEvent
)

@Serializable
public data class ActivateToolchainRequest(
    val activationRequestId: String,
    val expectedActiveInstallId: String? = null,
    val targetInstallId: String? = null
)

@Serializable
public data class ActivateToolchainResponse(
    val state: String? = null,
    val activeInstallId: String? = null,
    val code: String? = null,
    val reason: String? = null,
    val actual: String? = null,
    val activeWork: Int? = null,
    val toolIds: List<String>? = null
)

@Serializable
public data class ToolchainStateResponse(
    val state: String,
    val activeInstallId: String? = null,
    val previousInstallId: String? = null,
    val holds: List<String> = emptyList(),
    val lastActivation: LastActivationSummary? = null,
    val health: ToolchainHealthSummary? = null
)

@Serializable
public data class LastActivationSummary(
    val requestId: String,
    val state: String
)

@Serializable
public data class ToolchainHealthSummary(
    val intact: Boolean,
    val resolves: Boolean
)
