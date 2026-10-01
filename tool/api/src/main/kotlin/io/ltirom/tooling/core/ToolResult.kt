package io.ltirom.tooling.core

/**
 * Railway-Oriented Programming Result Monad for tool operations.
 * Replaces unconstrained exception throwing with explicit, typed outcomes.
 */
public sealed interface ToolResult<out T> {

    /** Successful execution with computed value and elapsed duration */
    public data class Success<T>(
        val value: T,
        val durationMs: Long
    ) : ToolResult<T>

    /** Typed failure representing distinct error domains */
    public sealed interface Failure : ToolResult<Nothing> {
        public val message: String

        /** WSL or remote server daemon could not be reached over network */
        public data class DaemonUnreachable(
            val host: String,
            val port: Int,
            override val message: String
        ) : Failure

        /** Execution or connection timed out */
        public data class ConnectionTimeout(
            val timeoutMs: Long,
            override val message: String
        ) : Failure

        /** The requested tool binary does not exist or is not executable */
        public data class ToolNotFound(
            val toolId: String,
            override val message: String
        ) : Failure

        /** Process completed with a non-zero / unaccepted exit code */
        public data class NonZeroExit(
            val toolId: String,
            val exitCode: Int,
            val stdout: String,
            val stderr: String,
            override val message: String = "Command '$toolId' exited with code $exitCode: ${stderr.ifBlank { stdout }}"
        ) : Failure

        /** Command was rejected by safety policy or sandboxing */
        public data class SecurityViolation(
            override val message: String
        ) : Failure

        /** Input, output, or argument validation failure */
        public data class ValidationFailed(
            override val message: String,
            val cause: Throwable? = null
        ) : Failure

        /** Generic execution or IO failure */
        public data class ExecutionError(
            override val message: String,
            val cause: Throwable? = null
        ) : Failure
    }

    public val isSuccess: Boolean get() = this is Success
    public val isFailure: Boolean get() = this is Failure
}

public inline fun <T, R> ToolResult<T>.map(transform: (T) -> R): ToolResult<R> {
    return when (this) {
        is ToolResult.Success -> ToolResult.Success(transform(value), durationMs)
        is ToolResult.Failure -> this
    }
}

public inline fun <T, R> ToolResult<T>.flatMap(transform: (T) -> ToolResult<R>): ToolResult<R> {
    return when (this) {
        is ToolResult.Success -> transform(value)
        is ToolResult.Failure -> this
    }
}

public fun <T> ToolResult<T>.getOrThrow(): T {
    return when (this) {
        is ToolResult.Success -> value
        is ToolResult.Failure.ToolNotFound -> throw ToolingException(message)
        is ToolResult.Failure.NonZeroExit -> {
            val id = ToolId.fromLogicalNameOrNull(toolId) ?: ToolId.ADB
            throw ToolExitException(id, exitCode, stdout, stderr, message)
        }
        is ToolResult.Failure.SecurityViolation -> throw SecurityException(message)
        is ToolResult.Failure -> throw ToolingException(message)
    }
}

public fun <T> ToolResult<T>.getOrElse(default: (ToolResult.Failure) -> T): T {
    return when (this) {
        is ToolResult.Success -> value
        is ToolResult.Failure -> default(this)
    }
}

public inline fun <T> ToolResult<T>.onSuccess(action: (T) -> Unit): ToolResult<T> {
    if (this is ToolResult.Success) action(value)
    return this
}

public inline fun <T> ToolResult<T>.onFailure(action: (ToolResult.Failure) -> Unit): ToolResult<T> {
    if (this is ToolResult.Failure) action(this)
    return this
}

/**
 * Executes a tool command catching exceptions and wrapping into a typed [ToolResult].
 */
public suspend fun <R> ToolRepository.executeResult(
    command: ToolCommand<R>,
    context: ExecutionContext
): ToolResult<R> {
    val startTime = System.currentTimeMillis()
    return try {
        val res = execute(command, context)
        val elapsed = System.currentTimeMillis() - startTime
        ToolResult.Success(res, elapsed)
    } catch (e: ToolExitException) {
        ToolResult.Failure.NonZeroExit(
            toolId = e.toolId.logicalName,
            exitCode = e.exitCode,
            stdout = e.stdout,
            stderr = e.stderr,
            message = e.message ?: "Non-zero exit"
        )
    } catch (e: SecurityException) {
        ToolResult.Failure.SecurityViolation(e.message ?: "Security violation")
    } catch (e: Exception) {
        val msg = e.message ?: "Execution error"
        if (msg.contains("not found", ignoreCase = true)) {
            ToolResult.Failure.ToolNotFound(command.toolId.logicalName, msg)
        } else if (msg.contains("timed out", ignoreCase = true)) {
            ToolResult.Failure.ConnectionTimeout(command.timeoutMs, msg)
        } else {
            ToolResult.Failure.ExecutionError(msg, e)
        }
    }
}
