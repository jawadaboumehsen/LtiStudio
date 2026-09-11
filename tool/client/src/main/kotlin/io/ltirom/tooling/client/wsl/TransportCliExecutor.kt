package io.ltirom.tooling.client.wsl

import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.ToolExecutionRequest
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
) : WslCliExecutor() {

    override fun execute(
        distro: String,
        command: List<String>,
        timeoutSeconds: Long,
        charset: Charset,
        user: String?,
    ): CliExecutionResult {
        require(command.isNotEmpty()) { "Command cannot be empty" }
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

        val request = ToolExecutionRequest(
            toolId = effectiveCommand.first(),
            arguments = effectiveCommand.drop(1),
            workingDirectory = defaultWorkingDirectory,
            timeoutMs = timeoutSeconds * 1000L,
        )

        return try {
            val response = runBlocking { transport.execute(request) }
            CliExecutionResult(
                exitCode = response.exitCode,
                output = response.stdout,
                error = response.stderr,
            )
        } catch (e: Exception) {
            CliExecutionResult(
                exitCode = -1,
                output = "",
                error = e.message ?: "Failed to execute remote command",
            )
        }
    }
}
