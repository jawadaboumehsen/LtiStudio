package io.ltirom.tooling.core

import java.io.File

open class ToolingException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ToolResolutionException(
    val toolId: ToolId,
    val searchedPaths: List<File>,
    message: String,
) : ToolingException(message)

class ToolFingerprintException(
    val toolId: ToolId,
    val expectedSha256: String,
    val actualSha256: String,
    message: String,
) : ToolingException(message)

class ToolValidationException(val toolId: ToolId, message: String) : ToolingException(message)

class ToolSafetyException(val toolId: ToolId, val violation: String, message: String) :
    ToolingException(message)

class ToolTimeoutException(
    val toolId: ToolId,
    val timeoutMs: Long,
    message: String,
    cause: Throwable? = null,
) : ToolingException(message, cause)

class RemoteCancellationUnconfirmedException(val executionId: String) :
    ToolingException("Remote termination was not confirmed for execution '$executionId'")

class ToolCancellationException(val toolId: ToolId, message: String, cause: Throwable? = null) :
    ToolingException(message, cause)

class ToolExitException(
    val toolId: ToolId,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    message: String,
) : ToolingException(message)

class ToolParseException(
    val toolId: ToolId,
    val rawOutput: String,
    message: String,
    cause: Throwable? = null,
) : ToolingException(message, cause)

class ToolArtifactException(val toolId: ToolId, val expectedArtifact: File, message: String) :
    ToolingException(message)

class RunOutputUnavailableException(
    val runId: String,
    val runStatus: io.ltirom.tooling.core.remote.RunStatus,
    message: String = "Lost stream output for terminal run '$runId' (status: ${runStatus.status}, exitCode: ${runStatus.exitCode})",
) : ToolingException(message)
