package io.ltirom.tooling.client.wsl

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
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
     * One daemon thread owns the process output for the process's whole life: it recognises the handshake
     * itself (so no amount of output after it can push it out of a buffer), keeps a short tail for errors,
     * then keeps draining so the service never blocks on a full pipe. The wait is a coroutine with a hard
     * [startupTimeoutMs] deadline, so a partial line, a hung process or the caller's cancellation cannot
     * hold it; output ending before the handshake fails at once. On any failure the process is destroyed.
     */
    internal suspend fun awaitHandshake(process: Process, distro: String): Pair<Int, Long> {
        val ready = CompletableDeferred<MatchResult>()
        val tail = ArrayDeque<String>()
        thread(isDaemon = true, name = "ltirom-server-output") {
            try {
                process.inputStream.bufferedReader().forEachLine { line ->
                    if (!ready.isCompleted) {
                        synchronized(tail) {
                            tail.addLast(line)
                            if (tail.size > TAIL_LINES) tail.removeFirst()
                        }
                        READY_REGEX.find(line)?.let { ready.complete(it) }
                    }
                }
            } catch (_: IOException) {
                // Stream closed: the process ended or was destroyed.
            } finally {
                ready.completeExceptionally(IOException("The build service output ended before it was ready"))
            }
        }

        val match = try {
            withTimeout(startupTimeoutMs) { ready.await() }
        } catch (_: TimeoutCancellationException) {
            null
        } catch (e: CancellationException) {
            process.destroyForcibly()
            throw e
        } catch (_: IOException) {
            null
        }

        if (match == null) {
            process.destroyForcibly()
            val text = synchronized(tail) { tail.joinToString("\n") }.trim()
            lastLogTail = text.ifEmpty { null }
            val header = "Timed out waiting for LtiRomServer daemon to start in WSL distro '$distro'"
            throw IllegalStateException(if (text.isNotEmpty()) "$header:\n$text" else header)
        }
        return match.groupValues[1].toInt() to match.groupValues[3].toLong()
    }

    private companion object {
        val READY_REGEX = Regex("""LTI_WSL_SERVER_READY\s+port=(\d+)\s+token=(\S+)\s+pid=(\d+)""")
        const val TAIL_LINES = 10
    }
}
