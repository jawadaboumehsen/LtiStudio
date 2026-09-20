package io.ltirom.tooling.core

import java.io.File

public open class ToolingException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

public class ToolResolutionException(
    public val toolId: ToolId,
    public val searchedPaths: List<File>,
    message: String
) : ToolingException(message)

public class ToolFingerprintException(
    public val toolId: ToolId,
    public val expectedSha256: String,
    public val actualSha256: String,
    message: String
) : ToolingException(message)

public class ToolValidationException(
    public val toolId: ToolId,
    message: String
) : ToolingException(message)

public class ToolSafetyException(
    public val toolId: ToolId,
    public val violation: String,
    message: String
) : ToolingException(message)

public class ToolTimeoutException(
    public val toolId: ToolId,
    public val timeoutMs: Long,
    message: String,
    cause: Throwable? = null
) : ToolingException(message, cause)

public class ToolCancellationException(
    public val toolId: ToolId,
    message: String,
    cause: Throwable? = null
) : ToolingException(message, cause)

public class ToolExitException(
    public val toolId: ToolId,
    public val exitCode: Int,
    public val stdout: String,
    public val stderr: String,
    message: String
) : ToolingException(message)

public class ToolParseException(
    public val toolId: ToolId,
    public val rawOutput: String,
    message: String,
    cause: Throwable? = null
) : ToolingException(message, cause)

public class ToolArtifactException(
    public val toolId: ToolId,
    public val expectedArtifact: File,
    message: String
) : ToolingException(message)
