/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.tools

import io.ltirom.tooling.core.RemoteCancellationUnconfirmedException
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.RunHandle
import io.ltirom.tooling.core.remote.RunPurpose
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.domain.tools.ToolErrorException
import java.util.UUID

public enum class CommandCategory(public val defaultTimeoutSeconds: Long) {
    SHORT(60L),
    NETWORK(1200L),
    BUILD(3600L),
    GRADLE(1800L),
}

/**
 * Single class used by KotlinToolStoreAdapter and KotlinRecipeRunner to execute commands
 * through RemoteTransportPort with categorized timeouts, streaming, cancellation, and network retries.
 */
public class RemoteCommandRunner(
    private val transport: RemoteTransportPort,
    private val networkBackoffMs: List<Long> = listOf(1000L, 2000L, 4000L),
) {
    private val activeExecutions = java.util.concurrent.ConcurrentHashMap<String, String>()

    @Suppress("ReturnCount")
    public suspend fun execute(
        script: String,
        category: CommandCategory = CommandCategory.SHORT,
        timeoutSeconds: Long = category.defaultTimeoutSeconds,
        cwd: String? = null,
        env: Map<String, String> = emptyMap(),
    ): Result<ToolExecutionResponse> {
        val maxAttempts = if (category == CommandCategory.NETWORK) 3 else 1
        var lastResponse: ToolExecutionResponse? = null
        var lastError: Throwable? = null

        for (attempt in 0 until maxAttempts) {
            try {
                val effectiveCwd = cwd?.takeIf { it.isNotBlank() && it.startsWith("/") } ?: "/tmp"
                val req =
                    ToolExecutionRequest(
                        toolId = "sh",
                        arguments = listOf("-c", script),
                        workingDirectory = effectiveCwd,
                        environment = env,
                        timeoutMs = timeoutSeconds * 1000L,
                        purpose = RunPurpose.WORKSPACE,
                    )
                val resp =
                    withContext(Dispatchers.IO) {
                        withTimeout(timeoutSeconds * 1000L + 15_000L) {
                            transport.execute(req)
                        }
                    }
                if (resp.exitCode == 0) {
                    return Result.success(resp)
                }
                lastResponse = resp
                if (category != CommandCategory.NETWORK) {
                    return Result.success(resp)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                lastError = t
                if (category != CommandCategory.NETWORK) {
                    return Result.failure(t)
                }
            }

            if (category == CommandCategory.NETWORK && attempt < maxAttempts - 1) {
                val backoff = networkBackoffMs.getOrElse(attempt) { 1000L }
                delay(backoff)
            }
        }

        val errorMsg =
            lastError?.message ?: lastResponse?.stderr ?: "Network command failed after $maxAttempts attempts"
        return Result.failure(ToolErrorException(ToolError.Network(errorMsg)))
    }

    public companion object {
        /** Compiler command lines (erofs-utils forces CMAKE_VERBOSE_MAKEFILE) run to kilobytes each. */
        public const val MAX_LOG_LINE_CHARS: Int = 1000

        /** The service ends a run that hit its time limit with a summary containing this. */
        public const val TIMED_OUT_MARKER: String = "timed out"

        private const val STOP_CONFIRM_MS = 6_000L
        private const val STOP_POLL_MS = 100L
        private val FINISHED_STATES =
            setOf(
                io.ltirom.tooling.core.remote.RunStatusValue.COMPLETED,
                io.ltirom.tooling.core.remote.RunStatusValue.FAILED,
                io.ltirom.tooling.core.remote.RunStatusValue.CANCELLED,
                io.ltirom.tooling.core.remote.RunStatusValue.INTERRUPTED,
            )
        private const val START_ATTEMPTS = 3
        private const val START_RETRY_DELAY_MS = 2_000L

        private const val NO_RESULT = "the run ended without a result (was the build service restarted?)"

        public fun isTimeout(summary: String?): Boolean = summary?.contains(TIMED_OUT_MARKER) == true

        public const val DEFAULT_MAX_OUTPUT_CHARS: Int = 512 * 1024
        public const val MAX_DIAGNOSTIC_CHARS: Int = 64 * 1024
    }

    @Suppress("ReturnCount")
    public suspend fun streamCollect(
        script: String,
        category: CommandCategory = CommandCategory.SHORT,
        timeoutSeconds: Long = category.defaultTimeoutSeconds,
        cwd: String? = null,
        env: Map<String, String> = emptyMap(),
        maxOutputChars: Int = DEFAULT_MAX_OUTPUT_CHARS,
    ): Result<String> {
        val maxAttempts = if (category == CommandCategory.NETWORK) 3 else 1
        var lastError: Throwable? = null
        var lastExitCode = -1

        for (attempt in 0 until maxAttempts) {
            try {
                val outputBuffer = StringBuilder()
                val diagnosticTail = StringBuilder()
                var exitCode = -1
                var summary: String? = null
                stream(
                    script = script,
                    category = category,
                    timeoutSeconds = timeoutSeconds,
                    cwd = cwd,
                    env = env,
                    onFinished = { exitCode = it },
                    onSummary = { summary = it },
                ).collect { chunk ->
                    val line = if (chunk.endsWith("\n")) chunk else "$chunk\n"
                    if (outputBuffer.length < maxOutputChars) {
                        val available = maxOutputChars - outputBuffer.length
                        if (line.length <= available) {
                            outputBuffer.append(line)
                        } else {
                            outputBuffer.append(line.take(available))
                        }
                    }
                    diagnosticTail.append(line)
                    if (diagnosticTail.length > MAX_DIAGNOSTIC_CHARS * 2) {
                        diagnosticTail.delete(0, diagnosticTail.length - MAX_DIAGNOSTIC_CHARS)
                    }
                }

                if (exitCode == 0) {
                    return Result.success(outputBuffer.toString())
                }
                lastExitCode = exitCode
                if (isTimeout(summary)) {
                    return Result.failure(ToolErrorException(ToolError.Timeout(summary.orEmpty())))
                }
                val errOutput = diagnosticTail.toString().trim()
                lastError = Exception(if (errOutput.isNotBlank()) errOutput else "Command exited with code $exitCode")
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                lastError = t
            }

            if (category == CommandCategory.NETWORK && attempt < maxAttempts - 1) {
                val backoff = networkBackoffMs.getOrElse(attempt) { 1000L }
                delay(backoff)
            }
        }

        val errorMsg = lastError?.message ?: "Command failed with exit code $lastExitCode after $maxAttempts attempts"
        return Result.failure(ToolErrorException(ToolError.Network(errorMsg)))
    }

    @Suppress("CyclomaticComplexMethod")
    public fun stream(
        script: String,
        category: CommandCategory = CommandCategory.BUILD,
        timeoutSeconds: Long = category.defaultTimeoutSeconds,
        cwd: String? = null,
        env: Map<String, String> = emptyMap(),
        onFinished: ((Int) -> Unit)? = null,
        onSummary: ((String?) -> Unit)? = null,
    ): Flow<String> = channelFlow {
        val execId = UUID.randomUUID().toString()
        val key = env["LTI_TOOL_GROUP"] ?: execId
        activeExecutions[key] = execId
        var finished = false

        try {
            val effectiveCwd = cwd?.takeIf { it.isNotBlank() && it.startsWith("/") } ?: "/tmp"
            val envWithTrace = env + ("LTI_TRACE_ID" to execId)
            val req =
                ToolExecutionRequest(
                    toolId = "sh",
                    arguments = listOf("-c", script),
                    workingDirectory = effectiveCwd,
                    environment = envWithTrace,
                    timeoutMs = timeoutSeconds * 1000L,
                    streamOutput = true,
                    purpose = RunPurpose.WORKSPACE,
                )

            val startReq =
                io.ltirom.tooling.core.remote.StartRunRequest(
                    request = req,
                    idempotencyKey = execId,
                    workspaceLock = null,
                    purpose = RunPurpose.WORKSPACE,
                )

            val runHandle = startRunRetrying(startReq)

            if (runHandle != null) {
                activeExecutions[key] = runHandle.runId
                withContext(Dispatchers.IO) {
                    withTimeout(timeoutSeconds * 1000L + 30_000L) {
                        transport.attachRun(runHandle.runId).collect { event ->
                            when (val ev = event.event) {
                                is StreamEvent.OutputChunk -> send(ev.text.forLog())
                                is StreamEvent.ExecutionFinished -> {
                                    finishedReason(ev.exitCode, ev.summary)?.let { send(it) }
                                    onSummary?.invoke(ev.summary)
                                    onFinished?.invoke(ev.exitCode)
                                    finished = true
                                }
                                is StreamEvent.ProgressUpdate -> {}
                                is StreamEvent.Heartbeat -> {}
                            }
                        }
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    withTimeout(timeoutSeconds * 1000L + 30_000L) {
                        transport.stream(req).collect { event ->
                            when (event) {
                                is StreamEvent.OutputChunk -> send(event.text.forLog())
                                is StreamEvent.ExecutionFinished -> {
                                    finishedReason(event.exitCode, event.summary)?.let { send(it) }
                                    onSummary?.invoke(event.summary)
                                    onFinished?.invoke(event.exitCode)
                                    finished = true
                                }
                                is StreamEvent.ProgressUpdate -> {}
                                is StreamEvent.Heartbeat -> {}
                            }
                        }
                    }
                }
            }
            // A run the service interrupted (e.g. it restarted) replays its output and ends with no result.
            // Callers would keep their default exit code, so a lost run must never read as a success.
            if (!finished) {
                send("[run] exit -1: $NO_RESULT")
                onSummary?.invoke(NO_RESULT)
                onFinished?.invoke(-1)
            }
        } catch (timeout: TimeoutCancellationException) {
            // Our own deadline passed without a finish event: stop the run and report it as a timeout,
            // not as a user cancel.
            val currentId = activeExecutions[key] ?: execId
            val stopped =
                withContext(NonCancellable) { withTimeoutOrNull(STOP_CONFIRM_MS) { stopRemote(currentId) } }
            if (stopped != true) send("[run] could not confirm that the timed-out process stopped")
            send("[run] exit -1: $TIMED_OUT_MARKER after ${timeoutSeconds}s")
            onSummary?.invoke("$TIMED_OUT_MARKER after ${timeoutSeconds}s")
            onFinished?.invoke(-1)
        } catch (ce: CancellationException) {
            // This coroutine is already cancelled: the request to stop the remote build has to run outside that
            // cancellation, or it is abandoned at its first suspension and the build keeps compiling unseen.
            val currentId = activeExecutions[key] ?: execId
            val stopped =
                withContext(NonCancellable) { withTimeoutOrNull(STOP_CONFIRM_MS) { stopRemote(currentId) } }
            if (stopped != true) throw RemoteCancellationUnconfirmedException(currentId)
            throw ce
        } finally {
            activeExecutions.remove(key)
        }
    }

    /**
     * Starts the durable run, retrying with the same idempotency key: a start whose answer was lost was
     * already accepted, and the service hands back that run instead of starting the script again. Falling
     * back to a plain stream on any error used to run the script twice, and even after a cancel. Null only
     * for a transport without durable runs.
     */
    private suspend fun startRunRetrying(request: io.ltirom.tooling.core.remote.StartRunRequest): RunHandle? {
        var attempt = 1
        while (true) {
            val started = runCatching { withContext(Dispatchers.IO) { transport.startRun(request) } }
            val error = started.exceptionOrNull()
            when {
                error == null -> return started.getOrThrow()
                error is UnsupportedOperationException -> return null
                error is CancellationException || attempt >= START_ATTEMPTS -> throw error
            }
            attempt++
            delay(START_RETRY_DELAY_MS)
        }
    }

    /** Asks the service to stop [id] and waits until it reports the run finished; true only when it did. */
    private suspend fun stopRemote(id: String): Boolean {
        val response = runCatching { transport.cancelRun(id) }.getOrNull()
        if (response == null || !response.accepted) return runCatching { transport.cancel(id) }.getOrDefault(false)
        while (true) {
            // A transport without run status cannot say more than "accepted".
            val status = runCatching { transport.getRun(id) }.getOrNull()
            if (status == null || status.status in FINISHED_STATES) return status?.unconfirmedSurvivors.isNullOrEmpty()
            delay(STOP_POLL_MS)
        }
    }

    private fun String.forLog(): String = if (length <=
        MAX_LOG_LINE_CHARS
    ) {
        this
    } else {
        take(MAX_LOG_LINE_CHARS) + " … (${length - MAX_LOG_LINE_CHARS} more chars)"
    }

    private fun finishedReason(exitCode: Int, summary: String?): String? =
        summary?.takeIf { exitCode != 0 && it.isNotBlank() }?.let { "[run] exit $exitCode: $it" }

    @Suppress("ReturnCount")
    public suspend fun cancelActive(key: String? = null): Boolean {
        if (key != null) {
            val id = activeExecutions.remove(key) ?: return true
            val runResult = runCatching { transport.cancelRun(id) }.getOrNull()
            if (runResult != null && runResult.accepted) return true
            return transport.cancel(id)
        }
        val ids = activeExecutions.values.toList()
        activeExecutions.clear()
        if (ids.isEmpty()) return true
        return ids.all { id ->
            val runResult = runCatching { transport.cancelRun(id) }.getOrNull()
            if (runResult != null && runResult.accepted) true else transport.cancel(id)
        }
    }
}
