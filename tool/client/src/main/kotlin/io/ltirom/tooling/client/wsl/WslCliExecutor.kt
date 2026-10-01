package io.ltirom.tooling.client.wsl

import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.Base64
import java.util.concurrent.TimeUnit

public data class CliExecutionResult(val exitCode: Int, val output: String, val error: String)

/**
 * Single Responsibility: Raw execution of host and WSL commands with timeouts,
 * deadlock-free stream drainage, bounded memory limits, and character set handling.
 */
public open class WslCliExecutor {

    public companion object {
        public const val MAX_CAPTURE_BYTES: Int = 1024 * 1024 // 1 MB ceiling
        public const val STREAM_JOIN_TIMEOUT_MS: Long = 2000L
        public const val PROCESS_TERMINATION_GRACE_MS: Long = 2000L
    }

    private class BoundedStreamDrainer(
        private val stream: InputStream,
        private val charset: Charset,
        private val maxChars: Int = MAX_CAPTURE_BYTES,
    ) : Runnable {
        private val buffer = StringBuilder()

        @Volatile private var isTruncated = false

        @Volatile private var capturedChars = 0

        override fun run() {
            try {
                InputStreamReader(stream, charset).use { reader ->
                    val charBuf = CharArray(8192)
                    var read: Int
                    while (reader.read(charBuf).also { read = it } != -1) {
                        if (capturedChars < maxChars) {
                            val available = maxChars - capturedChars
                            val toAppend = minOf(read, available)
                            buffer.append(charBuf, 0, toAppend)
                            capturedChars += toAppend
                            if (toAppend < read) {
                                isTruncated = true
                            }
                        } else {
                            isTruncated = true
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore stream read exceptions on termination/forced kill
            }
        }

        fun getCapturedText(): String {
            val text = buffer.toString().trim()
            return if (isTruncated) "$text\n[Output truncated at 1MB]" else text
        }
    }

    /**
     * [execute] for coroutine callers: cancelling the caller interrupts the command (the process tree is
     * killed), so work after a cancelled step never starts.
     */
    public open suspend fun run(
        distro: String,
        command: List<String>,
        timeoutSeconds: Long = 10,
        user: String? = null,
        stdin: String? = null,
    ): CliExecutionResult = if (stdin == null) {
        run(distro, command, timeoutSeconds, user)
    } else {
        kotlinx.coroutines.runInterruptible(kotlinx.coroutines.Dispatchers.IO) {
            execute(distro, command, timeoutSeconds, Charsets.UTF_8, user, stdin)
        }
    }

    public open suspend fun run(
        distro: String,
        command: List<String>,
        timeoutSeconds: Long,
        user: String?,
    ): CliExecutionResult = kotlinx.coroutines.runInterruptible(kotlinx.coroutines.Dispatchers.IO) {
        execute(distro, command, timeoutSeconds, Charsets.UTF_8, user, null)
    }

    public open fun execute(
        distro: String,
        command: List<String>,
        timeoutSeconds: Long = 10,
        charset: Charset = Charsets.UTF_8,
        user: String? = null,
        stdin: String? = null,
    ): CliExecutionResult {
        val method5 = runCatching {
            this.javaClass.getMethod(
                "execute",
                String::class.java,
                List::class.java,
                Long::class.javaPrimitiveType,
                Charset::class.java,
                String::class.java,
            )
        }.getOrNull()
        if (method5 != null && method5.declaringClass != WslCliExecutor::class.java) {
            return execute(distro, command, timeoutSeconds, charset, user)
        }
        return runProcess(ProcessBuilder(wslCommandLine(distro, command, user)), timeoutSeconds, charset, stdin)
    }

    public open fun execute(
        distro: String,
        command: List<String>,
        timeoutSeconds: Long,
        charset: Charset,
        user: String?,
    ): CliExecutionResult = runProcess(ProcessBuilder(wslCommandLine(distro, command, user)), timeoutSeconds, charset, null)

    /**
     * The `wsl.exe` argv for [command]. Windows passes argv as one command line that `wsl.exe` splits
     * again, and an embedded `"` is not escaped on the way (`sh -c 'echo "$(x -v)"'` arrives cut at the
     * space). So a command containing `"` is sent as base64 and rebuilt inside the distro, with every
     * argument single-quoted for `sh`; commands without `"` are passed unchanged.
     */
    internal fun wslCommandLine(distro: String, command: List<String>, user: String? = null): List<String> = buildList {
        add("wsl.exe")
        add("-d")
        add(distro)
        if (user != null) {
            add("-u")
            add(user)
        }
        add("-e")
        if (command.none { '"' in it }) {
            addAll(command)
        } else {
            val shellLine = command.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
            val encoded = Base64.getEncoder().encodeToString(shellLine.toByteArray(Charsets.UTF_8))
            addAll(listOf("sh", "-c", "echo $encoded | base64 -d | sh"))
        }
    }

    public open fun executeHost(
        command: List<String>,
        timeoutSeconds: Long = 10,
        charset: Charset = Charsets.UTF_8,
    ): CliExecutionResult = runProcess(ProcessBuilder(command), timeoutSeconds, charset)

    /** Hook for tests to observe the spawned process hierarchy before completion or timeout. */
    internal var onProcessStarted: ((Process) -> Unit)? = null

    data class ProcessTreeCleanupResult(val isClean: Boolean, val survivingPids: List<Long> = emptyList())

    /**
     * Forcibly terminates the process tree (parent and all its descendants).
     *
     * Per Oracle java.lang.Process documentation, destroyForcibly() is best-effort and does not
     * guarantee immediate termination across all operating systems or kernel process states.
     * This method sends SIGKILL / TerminateProcess to all descendants, awaits exit up to
     * [PROCESS_TERMINATION_GRACE_MS], and returns a [ProcessTreeCleanupResult] indicating whether all
     * processes terminated cleanly or if any surviving PIDs remain.
     */
    internal fun killProcessTree(process: Process): ProcessTreeCleanupResult {
        val descendants = runCatching {
            process.descendants().toList()
        }.getOrDefault(emptyList())

        // 1. Send destroyForcibly to all descendants and the parent process
        for (descendant in descendants) {
            runCatching { descendant.destroyForcibly() }
        }
        process.destroyForcibly()

        // 2. Wait for the parent process to terminate
        val parentExited = runCatching {
            process.waitFor(PROCESS_TERMINATION_GRACE_MS, TimeUnit.MILLISECONDS)
        }.getOrDefault(false)

        // 3. Await termination of each descendant, waiting up to PROCESS_TERMINATION_GRACE_MS
        val deadline = System.currentTimeMillis() + PROCESS_TERMINATION_GRACE_MS
        for (descendant in descendants) {
            val remainingMs = (deadline - System.currentTimeMillis()).coerceAtLeast(10L)
            runCatching {
                descendant.onExit().get(remainingMs, TimeUnit.MILLISECONDS)
            }
            // 4. Handle any stubborn descendant that remains alive
            if (descendant.isAlive) {
                runCatching { descendant.destroyForcibly() }
                val pollDeadline = System.currentTimeMillis() + 500L
                while (descendant.isAlive && System.currentTimeMillis() < pollDeadline) {
                    Thread.sleep(20L)
                }
            }
        }

        val survivingDescendants = descendants.filter { it.isAlive }.map { it.pid() }
        val parentAlive = if (!parentExited) process.isAlive else false
        val survivingPids = if (parentAlive) {
            listOf(runCatching { process.pid() }.getOrDefault(-1L)) + survivingDescendants
        } else {
            survivingDescendants
        }

        return ProcessTreeCleanupResult(
            isClean = survivingPids.isEmpty(),
            survivingPids = survivingPids,
        )
    }

    private fun runProcess(
        pb: ProcessBuilder,
        timeoutSeconds: Long,
        charset: Charset,
        stdin: String? = null,
    ): CliExecutionResult {
        var process: Process? = null
        var outDrainer: BoundedStreamDrainer? = null
        var outThread: Thread? = null
        var errThread: Thread? = null
        return try {
            val p = pb.start()
            process = p
            onProcessStarted?.invoke(p)
            if (!stdin.isNullOrEmpty()) {
                p.outputStream.bufferedWriter(charset).use { it.write(stdin) }
            } else {
                p.outputStream.close()
            }
            val od = BoundedStreamDrainer(p.inputStream, charset)
            val ed = BoundedStreamDrainer(p.errorStream, charset)
            outDrainer = od
            val ot = Thread(od, "WslCli-StdoutDrainer").apply {
                isDaemon = true
                start()
            }
            val et = Thread(ed, "WslCli-StderrDrainer").apply {
                isDaemon = true
                start()
            }
            outThread = ot
            errThread = et

            val exited = p.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!exited) {
                val cleanup = killProcessTree(p)
                ot.join(STREAM_JOIN_TIMEOUT_MS)
                et.join(STREAM_JOIN_TIMEOUT_MS)
                val cleanupNote = if (cleanup.isClean) {
                    ""
                } else {
                    " (incomplete process tree cleanup: PID(s) ${cleanup.survivingPids} still terminating)"
                }
                CliExecutionResult(
                    -1,
                    od.getCapturedText(),
                    "Command timed out after $timeoutSeconds seconds$cleanupNote",
                )
            } else {
                ot.join(STREAM_JOIN_TIMEOUT_MS)
                et.join(STREAM_JOIN_TIMEOUT_MS)
                CliExecutionResult(p.exitValue(), od.getCapturedText(), ed.getCapturedText())
            }
        } catch (ie: InterruptedException) {
            val cleanup = process?.let { killProcessTree(it) }
            outThread?.join(STREAM_JOIN_TIMEOUT_MS)
            errThread?.join(STREAM_JOIN_TIMEOUT_MS)
            Thread.currentThread().interrupt()
            val cleanupNote = if (cleanup == null ||
                cleanup.isClean
            ) {
                ""
            } else {
                " (incomplete process tree cleanup: PID(s) ${cleanup.survivingPids})"
            }
            CliExecutionResult(
                -1,
                outDrainer?.getCapturedText() ?: "",
                "Command execution interrupted: ${ie.message}$cleanupNote",
            )
        } catch (e: Exception) {
            val cleanup = process?.let { killProcessTree(it) }
            outThread?.join(STREAM_JOIN_TIMEOUT_MS)
            errThread?.join(STREAM_JOIN_TIMEOUT_MS)
            val cleanupNote = if (cleanup == null ||
                cleanup.isClean
            ) {
                ""
            } else {
                " (incomplete process tree cleanup: PID(s) ${cleanup.survivingPids})"
            }
            CliExecutionResult(
                -1,
                outDrainer?.getCapturedText() ?: "",
                (e.message ?: "Failed to execute command") + cleanupNote,
            )
        }
    }
}
