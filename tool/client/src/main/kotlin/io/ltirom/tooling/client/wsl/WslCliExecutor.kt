package io.ltirom.tooling.client.wsl

import java.io.File
import java.util.concurrent.TimeUnit

public data class CliExecutionResult(
    val exitCode: Int,
    val output: String,
    val error: String
)

/**
 * Single Responsibility: Raw execution of wsl.exe host commands with timeouts,
 * stream drainage, and character set handling.
 */
public open class WslCliExecutor {
    public open fun execute(
        distro: String,
        command: List<String>,
        timeoutSeconds: Long = 10,
        charset: java.nio.charset.Charset = Charsets.UTF_8,
        user: String? = null
    ): CliExecutionResult {
        val fullCmd = buildList {
            add("wsl.exe")
            add("-d")
            add(distro)
            if (user != null) {
                add("-u")
                add(user)
            }
            add("-e")
            addAll(command)
        }
        val pb = ProcessBuilder(fullCmd)
        return try {
            val p = pb.start()
            val out = p.inputStream.bufferedReader(charset).readText().trim()
            val err = p.errorStream.bufferedReader(charset).readText().trim()
            val exited = p.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!exited) {
                p.destroyForcibly()
                CliExecutionResult(-1, out, "Command timed out after $timeoutSeconds seconds")
            } else {
                CliExecutionResult(p.exitValue(), out, err)
            }
        } catch (e: Exception) {
            CliExecutionResult(-1, "", e.message ?: "Failed to execute wsl command")
        }
    }

    public open fun executeHost(
        command: List<String>,
        timeoutSeconds: Long = 10,
        charset: java.nio.charset.Charset = Charsets.UTF_8
    ): CliExecutionResult {
        val pb = ProcessBuilder(command)
        return try {
            val p = pb.start()
            val out = p.inputStream.bufferedReader(charset).readText().trim()
            val err = p.errorStream.bufferedReader(charset).readText().trim()
            val exited = p.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!exited) {
                p.destroyForcibly()
                CliExecutionResult(-1, out, "Command timed out after $timeoutSeconds seconds")
            } else {
                CliExecutionResult(p.exitValue(), out, err)
            }
        } catch (e: Exception) {
            CliExecutionResult(-1, "", e.message ?: "Failed to execute command")
        }
    }
}
