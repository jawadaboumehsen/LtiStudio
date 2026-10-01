package io.ltirom.tooling.client

import io.ltirom.tooling.client.transport.KtorRemoteTransportAdapter
import io.ltirom.tooling.core.*
import io.ltirom.tooling.core.ports.PathTranslatorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import io.ltirom.tooling.core.events.ToolEventBus
import io.ltirom.tooling.core.pipeline.*

/**
 * Remote tool repository that executes commands via a remote server bridge.
 * Adheres strictly to:
 * - Dependency Inversion Principle: depends on [RemoteTransportPort] and [PathTranslatorPort].
 * - Open/Closed Principle: agnostic of whether the backend is WSL, SSH, or Docker.
 * - Interface Segregation Principle: implements [StreamingToolRepository] which segregates
 *   batch [execute] from reactive [stream].
 */
public class RemoteWslToolRepository(
    private val transport: RemoteTransportPort,
    private val pathTranslator: PathTranslatorPort = WslPathTranslator(),
    private val safetyPolicy: ToolSafetyPolicy = ToolSafetyPolicy(),
    private val redactor: Redactor = Redactor(),
    private val auditLogger: (AuditRecord) -> Unit = {},
    private val onOutput: ((stdoutLine: String, stderrLine: String) -> Unit)? = null,
    eventBus: ToolEventBus? = null,
    allowedRoots: List<java.io.File> = emptyList(),
    customInterceptors: List<ToolExecutionInterceptor> = emptyList()
) : StreamingToolRepository {

    private val pipeline: ToolExecutionPipeline = ToolExecutionPipeline(
        listOfNotNull(
            WorkspaceJailInterceptor(allowedRoots),
            SafetyCheckInterceptor(safetyPolicy),
            eventBus?.let { EventPublishingInterceptor(it) },
            AuditLoggingInterceptor(redactor, auditLogger)
        ) + customInterceptors
    )

    /**
     * Backward-compatible convenience constructor accepting [WslDaemonManager].
     */
    public constructor(
        daemonManager: WslDaemonManager,
        pathTranslator: PathTranslatorPort = WslPathTranslator(),
        safetyPolicy: ToolSafetyPolicy = ToolSafetyPolicy(),
        redactor: Redactor = Redactor(),
        auditLogger: (AuditRecord) -> Unit = {},
        onOutput: ((stdoutLine: String, stderrLine: String) -> Unit)? = null,
        eventBus: ToolEventBus? = null,
        allowedRoots: List<java.io.File> = emptyList()
    ) : this(
        transport = KtorRemoteTransportAdapter(daemonManager, daemonManager.httpClient, daemonManager.json),
        pathTranslator = pathTranslator,
        safetyPolicy = safetyPolicy,
        redactor = redactor,
        auditLogger = auditLogger,
        onOutput = onOutput,
        eventBus = eventBus,
        allowedRoots = allowedRoots
    )

    private val windowsDriveRegex = Regex("""^[a-zA-Z]:[\\/].*""")

    public fun translateArgument(arg: String): String {
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

    override suspend fun <R> execute(
        command: ToolCommand<R>,
        context: ExecutionContext
    ): R {
        val result = pipeline.execute(command, context) { cmd, ctx ->
            executeTerminal(cmd, ctx)
        }
        return result.getOrThrow()
    }

    private suspend fun <R> executeTerminal(
        command: ToolCommand<R>,
        context: ExecutionContext
    ): ToolResult<R> {
        val rawArgs = command.getArguments()
        val translatedArgs = rawArgs.map { translateArgument(it) }

        val request = ToolExecutionRequest(
            toolId = command.toolId.logicalName,
            arguments = translatedArgs,
            workingDirectory = null,
            stdinText = if (command.stdinMode == StdinMode.PIPE_TEXT) command.stdinText else null,
            timeoutMs = command.timeoutMs,
            acceptedExitCodes = command.acceptedExitCodes
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
                    message = "Command failed with exit code ${response.exitCode}: ${response.stderr.ifBlank { response.stdout }}"
                )
            } else {
                val executionResult = CommandExecutionResult(
                    exitCode = response.exitCode,
                    stdout = response.stdout,
                    stderr = response.stderr,
                    durationMs = response.durationMs
                )
                ToolResult.Success(command.parseResult(executionResult), response.durationMs)
            }
        } catch (e: Exception) {
            ToolResult.Failure.ExecutionError(e.message ?: "Remote execution failed", e)
        }
    }

    override fun <R> stream(
        command: ToolCommand<R>,
        context: ExecutionContext
    ): Flow<StreamEvent> {
        safetyPolicy.checkSafety(command, context)
        command.validateInputArtifacts()

        val translatedArgs = command.getArguments().map { translateArgument(it) }

        val request = ToolExecutionRequest(
            toolId = command.toolId.logicalName,
            arguments = translatedArgs,
            workingDirectory = null,
            stdinText = if (command.stdinMode == StdinMode.PIPE_TEXT) command.stdinText else null,
            timeoutMs = command.timeoutMs,
            acceptedExitCodes = command.acceptedExitCodes,
            streamOutput = true
        )

        return transport.stream(request)
    }

    /**
     * Backward-compatible alias for [stream].
     */
    public fun <R> executeStreaming(
        command: ToolCommand<R>,
        context: ExecutionContext
    ): Flow<StreamEvent> = stream(command, context)

    private fun sha256(text: String): String {
        if (text.isEmpty()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
