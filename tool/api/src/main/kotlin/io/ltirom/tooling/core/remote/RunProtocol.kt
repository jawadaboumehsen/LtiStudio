package io.ltirom.tooling.core.remote

import kotlinx.serialization.Serializable

@Serializable
public enum class RunStatusValue {
    QUEUED,
    RUNNING,
    CANCELLING,
    CANCELLED,
    COMPLETED,
    FAILED,
    INTERRUPTED
}

@Serializable
public enum class RunPurpose {
    SETUP,
    WORKSPACE
}

@Serializable
public data class StartRunRequest(
    val request: ToolExecutionRequest,
    val idempotencyKey: String,
    val workspaceLock: String? = null,
    val purpose: RunPurpose = RunPurpose.WORKSPACE
)

@Serializable
public data class RunHandle(
    val runId: String,
    val status: RunStatusValue,
    val startedAtEpochMs: Long
)

@Serializable
public data class RunConflict(
    val existingRunId: String,
    val message: String? = null
)

@Serializable
public data class InvalidWorkingDirectory(
    val path: String? = null,
    val reason: String = "working directory missing"
)

@Serializable
public data class RunStatus(
    val runId: String,
    val status: RunStatusValue,
    val exitCode: Int? = null,
    val pid: Long? = null,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val lastSeq: Long = 0L
)

@Serializable
public data class RunCancelResponse(
    val accepted: Boolean,
    val status: RunStatusValue,
    val message: String? = null
)

@Serializable
public data class ActiveRuns(
    val runIds: List<String>,
    val message: String? = null
)
