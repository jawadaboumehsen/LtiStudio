package io.ltirom.tooling.client

import io.ltirom.tooling.client.transport.KtorRemoteTransportAdapter
import io.ltirom.tooling.core.AuditRecord
import io.ltirom.tooling.core.BinaryToolCommand
import io.ltirom.tooling.core.CommandExecutionResult
import io.ltirom.tooling.core.ExecutionContext
import io.ltirom.tooling.core.Redactor
import io.ltirom.tooling.core.StdinMode
import io.ltirom.tooling.core.StreamingToolRepository
import io.ltirom.tooling.core.ToolCommand
import io.ltirom.tooling.core.ToolResult
import io.ltirom.tooling.core.ToolSafetyException
import io.ltirom.tooling.core.ToolSafetyPolicy
import io.ltirom.tooling.core.boundTo
import io.ltirom.tooling.core.events.ToolEventBus
import io.ltirom.tooling.core.getOrThrow
import io.ltirom.tooling.core.pipeline.AuditLoggingInterceptor
import io.ltirom.tooling.core.pipeline.EventPublishingInterceptor
import io.ltirom.tooling.core.pipeline.SafetyCheckInterceptor
import io.ltirom.tooling.core.pipeline.ToolExecutionInterceptor
import io.ltirom.tooling.core.pipeline.ToolExecutionPipeline
import io.ltirom.tooling.core.pipeline.WorkspaceJailInterceptor
import io.ltirom.tooling.core.ports.PathTranslatorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Remote tool repository that executes commands via a remote server bridge.
 * Adheres strictly to:
 * - Dependency Inversion Principle: depends on [RemoteTransportPort] and [PathTranslatorPort].
 * - Open/Closed Principle: agnostic of whether the backend is WSL, SSH, or Docker.
 * - Interface Segregation Principle: implements [StreamingToolRepository] which segregates
 *   batch [execute] from reactive [stream].
 */
class RemoteWslToolRepository(
    private val transport: RemoteTransportPort,
    private val pathTranslator: PathTranslatorPort = WslPathTranslator(),
    private val safetyPolicy: ToolSafetyPolicy = ToolSafetyPolicy(),
    private val redactor: Redactor = Redactor(),
    private val auditLogger: (AuditRecord) -> Unit = {},
    private val onOutput: ((stdoutLine: String, stderrLine: String) -> Unit)? = null,
    eventBus: ToolEventBus? = null,
    allowedRoots: List<java.io.File> = emptyList(),
    customInterceptors: List<ToolExecutionInterceptor> = emptyList(),
) : StreamingToolRepository {

    private val jail = WorkspaceJailInterceptor(allowedRoots)

    private val pipeline: ToolExecutionPipeline = ToolExecutionPipeline(
        listOfNotNull(
            jail,
            SafetyCheckInterceptor(safetyPolicy),
            eventBus?.let { EventPublishingInterceptor(it) },
            AuditLoggingInterceptor(redactor, auditLogger),
        ) + customInterceptors,
    )

    /**
     * Backward-compatible convenience constructor accepting [WslDaemonManager].
     */
    constructor(
        daemonManager: WslDaemonManager,
        pathTranslator: PathTranslatorPort = WslPathTranslator(),
        safetyPolicy: ToolSafetyPolicy = ToolSafetyPolicy(),
        redactor: Redactor = Redactor(),
        auditLogger: (AuditRecord) -> Unit = {},
        onOutput: ((stdoutLine: String, stderrLine: String) -> Unit)? = null,
        eventBus: ToolEventBus? = null,
        allowedRoots: List<java.io.File> = emptyList(),
    ) : this(
        transport = KtorRemoteTransportAdapter(daemonManager, daemonManager.httpClient, daemonManager.json),
        pathTranslator = pathTranslator,
        safetyPolicy = safetyPolicy,
        redactor = redactor,
        auditLogger = auditLogger,
        onOutput = onOutput,
        eventBus = eventBus,
        allowedRoots = allowedRoots,
    )

    private val windowsDriveRegex = Regex("""^[a-zA-Z]:[\\/].*""")

    fun translateArgument(arg: String): String {
        if (windowsDriveRegex.matches(arg) || arg.startsWith("\\\\wsl")) {
            return pathTranslator.toRemote(arg)
        }
        val eqIndex = arg.indexOf('=')
        if (eqIndex != -1 && eqIndex < arg.lastIndex) {
            val prefix = arg.substring(0, eqIndex + 1)
            val value = arg.substring(eqIndex + 1)
            if (windowsDriveRegex.matches(value) || value.startsWith("\\\\wsl")) {
                return prefix + pathTranslator.toRemote(value)
            }
        }
        return arg
    }

    override suspend fun <R> execute(command: ToolCommand<R>, context: ExecutionContext): R {
        val result = pipeline.execute(command.boundTo(context), context) { cmd, ctx ->
            executeTerminal(cmd, ctx)
        }
        return result.getOrThrow()
    }

    private suspend fun <R> executeTerminal(command: ToolCommand<R>, context: ExecutionContext): ToolResult<R> {
        if (command is BinaryToolCommand<*>) {
            return ToolResult.Failure.ExecutionError(
                "${command.toolId.logicalName} prints bytes, which the service's text transport would alter; " +
                    "run it locally or write its output to a file and download that",
                null,
            )
        }
        val rawArgs = command.getArguments()
        val translatedArgs = rawArgs.map { translateArgument(it) }

        val request = ToolExecutionRequest(
            toolId = command.toolId.logicalName,
            arguments = translatedArgs,
            workingDirectory = null,
            stdinText = if (command.stdinMode == StdinMode.PIPE_TEXT) command.stdinText else null,
            timeoutMs = command.timeoutMs,
            acceptedExitCodes = command.acceptedExitCodes,
        )

        return try {
            val response: ToolExecutionResponse = transport.execute(request)

            if (onOutput != null) {
                if (response.stdout.isNotEmpty()) onOutput.invoke(response.stdout, "")
                if (response.stderr.isNotEmpty()) onOutput.invoke("", response.stderr)
            }

            if (response.exitCode !in command.acceptedExitCodes) {
                ToolResult.Failure.NonZeroExit(
                    toolId = command.toolId.logicalName,
                    exitCode = response.exitCode,
                    stdout = response.stdout,
                    stderr = response.stderr,
                    message = "Command failed with exit code ${response.exitCode}: ${response.stderr.ifBlank {
                        response.stdout
                    }}",
                )
            } else {
                val executionResult = CommandExecutionResult(
                    exitCode = response.exitCode,
                    stdout = response.stdout,
                    stderr = response.stderr,
                    durationMs = response.durationMs,
                )
                ToolResult.Success(command.parseResult(executionResult), response.durationMs)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult.Failure.ExecutionError(e.message ?: "Remote execution failed", e)
        }
    }

    override fun <R> stream(command: ToolCommand<R>, context: ExecutionContext): Flow<StreamEvent> {
        val boundCommand = command.boundTo(context)
        jail.violation(boundCommand)?.let { throw ToolSafetyException(boundCommand.toolId, "WORKSPACE_JAIL", it) }
        safetyPolicy.checkSafety(boundCommand, context)
        boundCommand.validateInputArtifacts()

        val translatedArgs = boundCommand.getArguments().map { translateArgument(it) }

        val request = ToolExecutionRequest(
            toolId = boundCommand.toolId.logicalName,
            arguments = translatedArgs,
            workingDirectory = null,
            stdinText = if (boundCommand.stdinMode == StdinMode.PIPE_TEXT) boundCommand.stdinText else null,
            timeoutMs = boundCommand.timeoutMs,
            acceptedExitCodes = boundCommand.acceptedExitCodes,
            streamOutput = true,
        )

        return transport.stream(request)
    }
}
