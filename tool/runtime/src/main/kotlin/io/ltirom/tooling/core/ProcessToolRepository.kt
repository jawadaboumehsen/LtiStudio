package io.ltirom.tooling.core

import kotlinx.coroutines.*
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

public class ProcessToolRepository(
    private val binaryResolver: (ToolId) -> ToolBinary,
    private val environment: ProcessEnvironment,
    private val safetyPolicy: ToolSafetyPolicy,
    private val redactor: Redactor,
    private val auditLogger: (AuditRecord) -> Unit = {},
    private val maxCaptureBytes: Int = 10 * 1024 * 1024,
    private val onOutput: ((String, String) -> Unit)? = null
) : ToolRepository {

    override suspend fun <R> execute(
        command: ToolCommand<R>,
        context: ExecutionContext
    ): R {
        // 1. Safety Check
        safetyPolicy.checkSafety(command, context)
        command.validateInputArtifacts()

        // 2. Binary Resolution
        val binary = binaryResolver(command.toolId)
        if (!binary.isValid()) {
            throw ToolResolutionException(
                command.toolId,
                listOf(binary.file),
                "Binary path ${binary.file.absolutePath} is not a valid executable file"
            )
        }

        val cmd = when (binary.type) {
            BinaryType.JAR_WRAPPER -> listOf("java", "-jar", binary.file.absolutePath) + command.getArguments()
            else -> listOf(binary.file.absolutePath) + command.getArguments()
        }

        val pb = ProcessBuilder(cmd)
        environment.applyTo(pb)

        var process: Process? = null
        val durationMs: Long
        var exitCode = -1
        var stdout = ""
        var stderr = ""
        var stdoutBytes: ByteArray? = null
        val knownDescendants = ConcurrentHashMap.newKeySet<ProcessHandle>()

        try {
            durationMs = measureTimeMillis {
                coroutineScope {
                    val p = withContext(Dispatchers.IO) { pb.start() }
                    process = p
                    // A shell parent can exit before cancellation cleanup runs. Retain every
                    // observed descendant while it is alive so cleanup still reaches children.
                    val descendantWatcher = launch(Dispatchers.IO) {
                        while (p.isAlive) {
                            rememberDescendants(p, knownDescendants)
                            delay(DESCENDANT_SCAN_MILLIS)
                        }
                    }

                    var processExited = false
                    try {
                        // Write stdin
                        if (command.stdinMode == StdinMode.PIPE_TEXT && command.stdinText != null) {
                            launch(Dispatchers.IO) {
                                p.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                                    writer.write(command.stdinText)
                                }
                            }
                        } else {
                            p.outputStream.close()
                        }

                        // Concurrent stream reading
                        val stdoutJob = async(Dispatchers.IO) { readStreamBytes(p.inputStream, maxCaptureBytes) }
                        val stderrJob = async(Dispatchers.IO) { readStreamBytes(p.errorStream, maxCaptureBytes) }

                        exitCode = withTimeout(command.timeoutMs) {
                            runInterruptible(Dispatchers.IO) {
                                p.waitFor()
                            }
                        }
                        processExited = true

                        val stdoutData = stdoutJob.await()
                        val stderrData = stderrJob.await()
                        stdout = stdoutData.toString(Charsets.UTF_8)
                        stderr = stderrData.toString(Charsets.UTF_8)
                        stdoutBytes = if (command is BinaryToolCommand<*>) stdoutData else null
                        onOutput?.invoke(stdout, stderr)
                    } finally {
                        // This must run before coroutineScope waits for stdout/stderr readers:
                        // they remain blocked on their pipes until the child process is gone.
                        if (!processExited) cleanupProcess(p, knownDescendants)
                        descendantWatcher.cancelAndJoin()
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            cleanupProcess(process, knownDescendants)
            throw ToolTimeoutException(
                command.toolId,
                command.timeoutMs,
                "Command ${cmd.first()} timed out after ${command.timeoutMs}ms",
                e
            )
        } catch (e: CancellationException) {
            cleanupProcess(process, knownDescendants)
            throw ToolCancellationException(
                command.toolId,
                "Command execution cancelled",
                e
            )
        } catch (e: Exception) {
            cleanupProcess(process, knownDescendants)
            throw ToolingException("Failed to execute process: ${e::class.simpleName}: ${e.message}", e)
        }

        val executionResult = CommandExecutionResult(
            exitCode = exitCode,
            stdout = stdout,
            stderr = stderr,
            durationMs = durationMs,
            stdoutBytes = stdoutBytes
        )

        // 3. Log Audit Record
        val auditRecord = AuditRecord.create(binary, command, executionResult, redactor)
        auditLogger(auditRecord)

        if (executionResult.exitCode !in command.acceptedExitCodes) {
            throw ToolExitException(
                command.toolId,
                executionResult.exitCode,
                executionResult.stdout,
                executionResult.stderr,
                "${command.toolId.logicalName} exited with status ${executionResult.exitCode} " +
                    "(accepted: ${command.acceptedExitCodes.sorted().joinToString()})"
            )
        }

        command.validateOutputArtifacts()

        // 4. Parse Result
        return try {
            command.parseResult(executionResult)
        } catch (e: Exception) {
            throw ToolParseException(
                command.toolId,
                stdout,
                "Failed to parse command result",
                e
            )
        }
    }

    private fun cleanupProcess(process: Process?, knownDescendants: MutableSet<ProcessHandle>) {
        if (process == null) return
        try {
            rememberDescendants(process, knownDescendants)
            val descendants = knownDescendants.toList().asReversed()
            descendants.forEach { child -> runCatching { child.destroy() } }
            process.destroy()
            Thread.sleep(PROCESS_GRACE_MILLIS)
            descendants.forEach { child -> if (child.isAlive) runCatching { child.destroyForcibly() } }
            if (process.isAlive) process.destroyForcibly()
        } catch (ignored: Exception) {}
    }

    private fun rememberDescendants(process: Process, knownDescendants: MutableSet<ProcessHandle>) {
        process.descendants().use { stream -> stream.forEach(knownDescendants::add) }
    }

    private suspend fun readStreamBytes(stream: InputStream, maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 8192))
        val buffer = ByteArray(8192)
        var totalBytes = 0
        try {
            stream.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    if (totalBytes < maxBytes) {
                        val remaining = maxBytes - totalBytes
                        val toAppend = minOf(read, remaining)
                        output.write(buffer, 0, toAppend)
                        totalBytes += toAppend
                    }
                }
            }
        } catch (e: java.io.IOException) {
            currentCoroutineContext().ensureActive()
            if (e.message?.contains("Stream closed", ignoreCase = true) == true) {
                return output.toByteArray()
            }
            throw e
        }
        return output.toByteArray()
    }

    private companion object {
        const val PROCESS_GRACE_MILLIS: Long = 250
        const val DESCENDANT_SCAN_MILLIS: Long = 10
    }
}
