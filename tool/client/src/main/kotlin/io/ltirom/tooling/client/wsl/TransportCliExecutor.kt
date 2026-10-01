package io.ltirom.tooling.client.wsl

import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.RunPurpose
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.nio.charset.Charset

/**
 * Executes commands through [RemoteTransportPort] via the LtiRomServer daemon.
 *
 * Adheres to:
 * - Substituted for [WslCliExecutor] after the server bridge connects (LSP).
 * - Forbids shell-string executions (`bash -c`, `sh -c`).
 * - Applies non-interactive root elevation (`sudo -n`) when user == "root".
 */
public class TransportCliExecutor(
    private val transport: RemoteTransportPort,
    private val defaultWorkingDirectory: String = "/home/lti",
    private val purpose: RunPurpose = RunPurpose.SETUP,
    private val commandValidator: ((List<String>) -> Unit)? = null,
) : WslCliExecutor() {

    override fun execute(
        distro: String,
        command: List<String>,
        timeoutSeconds: Long,
        charset: Charset,
        user: String?,
        stdin: String?,
    ): CliExecutionResult {
        val request = toRequest(command, timeoutSeconds, user, stdin)
        return try {
            toResult(runBlocking { transport.execute(request) })
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            failure(e)
        }
    }

    /**
     * Runs the request in the caller's coroutine instead of a detached `runBlocking` scope, so cancelling the
     * caller cancels the remote call and nothing after it starts.
     */
    override suspend fun run(
        distro: String,
        command: List<String>,
        timeoutSeconds: Long,
        user: String?,
        stdin: String?,
    ): CliExecutionResult {
        val request = toRequest(command, timeoutSeconds, user, stdin)
        return try {
            toResult(transport.execute(request))
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            failure(e)
        }
    }

    private fun toRequest(
        command: List<String>,
        timeoutSeconds: Long,
        user: String?,
        stdin: String? = null,
    ): ToolExecutionRequest {
        require(command.isNotEmpty()) { "Command cannot be empty" }
        commandValidator?.invoke(command)
        val first = command.first()
        require(!((first == "bash" || first == "sh") && command.contains("-c"))) {
            "bash/sh -c commands are forbidden in TransportCliExecutor: $command"
        }
        val effectiveCommand = when {
            user == "root" && first != "sudo" -> listOf("sudo", "-n") + command
            user == "root" && first == "sudo" && command.getOrNull(1) != "-n" ->
                listOf("sudo", "-n") + command.drop(1)
            else -> command
        }
        return ToolExecutionRequest(
            toolId = effectiveCommand.first(),
            arguments = effectiveCommand.drop(1),
            workingDirectory = defaultWorkingDirectory,
            timeoutMs = timeoutSeconds * 1000L,
            purpose = purpose,
            stdinText = stdin,
        )
    }

    private fun toResult(response: io.ltirom.tooling.core.remote.ToolExecutionResponse) = CliExecutionResult(
        exitCode = response.exitCode,
        output = response.stdout,
        error = response.stderr,
    )

    private fun failure(e: Exception) = CliExecutionResult(
        exitCode = -1,
        output = "",
        error = e.message ?: "Failed to execute remote command",
    )
}
