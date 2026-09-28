package io.ltirom.tooling.client.wsl

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread

public data class WslProcessLaunchResult(
    val process: Process,
    val port: Int,
    val token: String,
    val pid: Long
)

/**
 * Single Responsibility: Spawns the LtiRomServer process in WSL and parses the handshake.
 */
public open class WslProcessLauncher(
    internal val startupTimeoutMs: Long = 20_000L,
) {
    public var lastLogTail: String? = null
        private set

    public open suspend fun launch(
        distro: String,
        binaryPath: String,
    ): WslProcessLaunchResult = launch(distro, binaryPath, null)

    public open suspend fun launch(
        distro: String,
        binaryPath: String,
        javaHome: String?
    ): WslProcessLaunchResult {
        val token = UUID.randomUUID().toString().replace("-", "")

        val cmd = mutableListOf("wsl.exe", "-d", distro, "-e")
        if (!javaHome.isNullOrBlank()) {
            cmd.add("env")
            cmd.add("JAVA_HOME=$javaHome")
        }
        cmd.addAll(
            listOf(
                binaryPath,
                "--port", "0",
                "--token", token,
                "--host", "0.0.0.0",
                "--distro", distro,
            )
        )

        val process = withContext(Dispatchers.IO) {
            ProcessBuilder(cmd).redirectErrorStream(true).start()
        }
        val (port, pid) = awaitHandshake(process, distro)
        return WslProcessLaunchResult(process = process, port = port, token = token, pid = pid)
    }

    /**
     * Waits for `LTI_WSL_SERVER_READY port=… token=… pid=…` and returns (port, pid).
     *
     * One daemon thread owns the process output for the process's whole life and never blocks anyone else;
     * the wait itself is a coroutine with a hard [startupTimeoutMs] deadline, so a partial line without a
     * newline, a hung process or the caller's cancellation cannot hold it. On timeout, cancellation or an
     * early exit the process is destroyed.
     */
    internal suspend fun awaitHandshake(process: Process, distro: String): Pair<Int, Long> {
        // Bounded: after the handshake the service keeps logging for hours; only recent lines are kept.
        val lines = Channel<String>(capacity = OUTPUT_BUFFER_LINES, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        thread(isDaemon = true, name = "ltirom-server-output") {
            try {
                process.inputStream.bufferedReader().forEachLine { lines.trySend(it) }
            } catch (_: IOException) {
                // Stream closed: the process ended or was destroyed.
            } finally {
                lines.close()
            }
        }

        val captured = ArrayDeque<String>()
        val match = try {
            withTimeout(startupTimeoutMs) {
                var found: MatchResult? = null
                for (line in lines) {
                    captured.addLast(line)
                    if (captured.size > TAIL_LINES) captured.removeFirst()
                    found = READY_REGEX.find(line)
                    if (found != null) break
                }
                found
            }
        } catch (_: TimeoutCancellationException) {
            null
        } catch (e: CancellationException) {
            process.destroyForcibly()
            throw e
        }

        if (match == null) {
            process.destroyForcibly()
            val tail = captured.joinToString("\n").trim()
            lastLogTail = tail.ifEmpty { null }
            val header = "Timed out waiting for LtiRomServer daemon to start in WSL distro '$distro'"
            throw IllegalStateException(if (tail.isNotEmpty()) "$header:\n$tail" else header)
        }
        return match.groupValues[1].toInt() to match.groupValues[3].toLong()
    }

    private companion object {
        val READY_REGEX = Regex("""LTI_WSL_SERVER_READY\s+port=(\d+)\s+token=(\S+)\s+pid=(\d+)""")
        const val OUTPUT_BUFFER_LINES = 256
        const val TAIL_LINES = 10
    }
}
