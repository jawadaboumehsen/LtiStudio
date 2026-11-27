package io.ltirom.tooling.core.remote

import kotlinx.serialization.Serializable

@Serializable
enum class RunStatusValue {
    QUEUED,
    RUNNING,
    CANCELLING,
    CANCELLED,
    COMPLETED,
    FAILED,
    INTERRUPTED,
}

@Serializable
enum class RunPurpose {
    SETUP,
    WORKSPACE,
}

@Serializable
data class StartRunRequest(
    val request: ToolExecutionRequest,
    val idempotencyKey: String,
    val workspaceLock: String? = null,
    val purpose: RunPurpose = RunPurpose.WORKSPACE,
)

@Serializable
data class RunHandle(val runId: String, val status: RunStatusValue, val startedAtEpochMs: Long)

@Serializable
data class RunConflict(val existingRunId: String, val message: String? = null)

@Serializable
data class InvalidWorkingDirectory(val path: String? = null, val reason: String = "working directory missing")

@Serializable
data class RunStatus(
    val runId: String,
    val status: RunStatusValue,
    val exitCode: Int? = null,
    val pid: Long? = null,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val lastSeq: Long = 0L,
    val unconfirmedSurvivors: List<Long> = emptyList(),
)

@Serializable
data class RunCancelResponse(val accepted: Boolean, val status: RunStatusValue, val message: String? = null)

@Serializable
data class ActiveRuns(val runIds: List<String>, val message: String? = null)
