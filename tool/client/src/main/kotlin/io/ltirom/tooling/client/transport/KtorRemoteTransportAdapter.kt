package io.ltirom.tooling.client.transport

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.websocket.*
import io.ltirom.tooling.client.resilience.CircuitBreaker
import io.ltirom.tooling.core.ToolingException
import io.ltirom.tooling.core.TraceContext
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import io.ltirom.tooling.core.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Adapter implementing [RemoteTransportPort] via Ktor HTTP and WebSocket clients.
 * Protected with [CircuitBreaker] resilience and distributed [TraceContext] propagation.
 */
public class KtorRemoteTransportAdapter(
    private val supervisor: DaemonSupervisorPort,
    private val httpClient: HttpClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
    public val circuitBreaker: CircuitBreaker = CircuitBreaker(),
) : RemoteTransportPort {

    public companion object {
        public const val PROTOCOL_VERSION: String = "1.2.0"
        public const val HEADER_PROTOCOL_VERSION: String = "X-LtiRom-Protocol-Version"
        public const val DEFAULT_HEARTBEAT_TIMEOUT_MS: Long = 45_000L

        /** Time on top of a command's own timeout for the service to finish and answer. */
        private const val EXECUTE_RESPONSE_MARGIN_MS: Long = 30_000L
        private const val MAX_RECONNECTS_WITHOUT_PROGRESS = 10

        /** Close reason the service sends when a run's stream has ended. */
        private const val STREAM_COMPLETED = "Completed"
    }

    /**
     * The service that accepted each run. A run lives only in that service's memory, so its attach, status
     * and cancel go there, never to whatever [DaemonSupervisorPort.ensureStarted] resolves to at that moment
     * (which may be another service after a restart or update). Runs this session didn't start (recovery
     * after an app restart) fall back to the current service.
     */
    private val runOwners = java.util.concurrent.ConcurrentHashMap<String, ServerConnectionDescriptor>()

    private suspend fun connectionFor(runId: String): ServerConnectionDescriptor =
        runOwners[runId] ?: supervisor.ensureStarted()

    override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse = circuitBreaker.execute {
        val conn = supervisor.ensureStarted()
        val trace = TraceContext.create()
        try {
            httpClient.post("${conn.httpBaseUrl}/api/v1/tools/execute") {
                // The command may run for its whole timeoutMs; the request must outlive it.
                if (httpClient.pluginOrNull(HttpTimeout) != null) {
                    timeout { requestTimeoutMillis = request.timeoutMs + EXECUTE_RESPONSE_MARGIN_MS }
                }
                header(HttpHeaders.Authorization, "Bearer ${conn.token}")
                header(TraceContext.HEADER_TRACE_ID, trace.traceId)
                header(TraceContext.HEADER_SPAN_ID, trace.spanId)
                header(HEADER_PROTOCOL_VERSION, PROTOCOL_VERSION)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            throw ToolingException(
                "Failed to execute command on remote daemon at ${conn.httpBaseUrl} [trace=${trace.traceId}]: ${e.message}",
                e,
            )
        }
    }

    override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = flow {
        val conn = supervisor.ensureStarted()
        val trace = TraceContext.create()
        val requestJson = json.encodeToString(request)
        var finished = false

        try {
            httpClient.webSocket(
                urlString = "${conn.wsBaseUrl}/api/v1/tools/stream?token=${conn.token}&traceId=${trace.traceId}",
            ) {
                send(Frame.Text(requestJson))
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val event = runCatching {
                            json.decodeFromString<StreamEvent>(text)
                        }.getOrNull()

                        if (event != null) {
                            emit(event)
                            if (event is StreamEvent.ExecutionFinished) {
                                finished = true
                                break
                            }
                        }
                    }
                }
            }
        } finally {
            if (!finished) {
                runCatching { cancel(trace.traceId, "INTERRUPT") }
            }
        }
    }

    override fun streamSequenced(request: ToolExecutionRequest): Flow<SequencedStreamEvent> = flow {
        val conn = supervisor.ensureStarted()
        val trace = TraceContext.create()
        val requestJson = json.encodeToString(request)
        var finished = false

        try {
            httpClient.webSocket(
                urlString = "${conn.wsBaseUrl}/api/v1/tools/stream?token=${conn.token}&traceId=${trace.traceId}&sequenced=true",
            ) {
                send(Frame.Text(requestJson))
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val event = runCatching {
                            json.decodeFromString<SequencedStreamEvent>(text)
                        }.getOrNull()

                        if (event != null) {
                            emit(event)
                            if (event.event is StreamEvent.ExecutionFinished) {
                                finished = true
                                break
                            }
                        }
                    }
                }
            }
        } finally {
            if (!finished) {
                runCatching { cancel(trace.traceId, "INTERRUPT") }
            }
        }
    }

    override suspend fun checkHealth(): WslServerInfo? {
        val conn = supervisor.ensureStarted()
        val trace = TraceContext.create()
        return try {
            val response = httpClient.get("${conn.httpBaseUrl}/api/v1/health") {
                header(TraceContext.HEADER_TRACE_ID, trace.traceId)
            }
            if (response.status.isSuccess()) {
                response.body<WslServerInfo>()
            } else {
                null
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun listTools(): ToolListResult {
        val conn = supervisor.ensureStarted()
        val trace = TraceContext.create()
        return try {
            val response = httpClient.get("${conn.httpBaseUrl}/api/v1/tools") {
                header(HttpHeaders.Authorization, "Bearer ${conn.token}")
                header(TraceContext.HEADER_TRACE_ID, trace.traceId)
            }
            if (response.status.isSuccess()) {
                ToolListResult.Tools(response.body<List<ToolStatusInfo>>())
            } else {
                ToolListResult.ServiceError("Server responded with HTTP ${response.status.value}")
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            ToolListResult.ServiceError(e.message ?: "Failed to list tools")
        }
    }

    override suspend fun refreshTools(): ToolListResult {
        val conn = supervisor.ensureStarted()
        val trace = TraceContext.create()
        return try {
            val response = httpClient.post("${conn.httpBaseUrl}/api/v1/tools/refresh") {
                header(HttpHeaders.Authorization, "Bearer ${conn.token}")
                header(TraceContext.HEADER_TRACE_ID, trace.traceId)
            }
            if (response.status.isSuccess()) {
                ToolListResult.Tools(response.body<List<ToolStatusInfo>>())
            } else {
                ToolListResult.ServiceError("Server responded with HTTP ${response.status.value}")
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            ToolListResult.ServiceError(e.message ?: "Failed to refresh tools")
        }
    }

    override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean {
        val conn = supervisor.ensureStarted()
        val trace = TraceContext.create()
        return try {
            val response = httpClient.post("${conn.httpBaseUrl}/api/v1/binary/upload") {
                header(HttpHeaders.Authorization, "Bearer ${conn.token}")
                header(TraceContext.HEADER_TRACE_ID, trace.traceId)
                parameter("path", remotePath)
                setBody(content)
            }
            response.status.isSuccess()
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun downloadFile(remotePath: String): ByteArray? {
        val conn = supervisor.ensureStarted()
        val trace = TraceContext.create()
        return try {
            val response = httpClient.get("${conn.httpBaseUrl}/api/v1/binary/download") {
                header(HttpHeaders.Authorization, "Bearer ${conn.token}")
                header(TraceContext.HEADER_TRACE_ID, trace.traceId)
                parameter("path", remotePath)
            }
            if (response.status.isSuccess()) {
                response.body<ByteArray>()
            } else {
                null
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun shutdown(): Boolean = supervisor.shutdownDaemon()

    override suspend fun cancel(executionId: String, signal: String): Boolean {
        val conn = supervisor.getConnectionInfo() ?: return false
        return try {
            val response = httpClient.post(
                "${conn.httpBaseUrl}/api/v1/tools/execute/$executionId/cancel?signal=$signal",
            ) {
                header(HttpHeaders.Authorization, "Bearer ${conn.token}")
            }
            response.status.isSuccess()
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun listSessions(): List<SessionSummary> {
        val conn = supervisor.getConnectionInfo() ?: return emptyList()
        val trace = TraceContext.create()
        return try {
            val response = httpClient.get("${conn.httpBaseUrl}/api/v1/sessions") {
                header(HttpHeaders.Authorization, "Bearer ${conn.token}")
                header(TraceContext.HEADER_TRACE_ID, trace.traceId)
            }
            if (response.status.isSuccess()) {
                response.body<List<SessionSummary>>()
            } else {
                emptyList()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getSession(sessionId: String): SessionSummary? {
        val conn = supervisor.getConnectionInfo() ?: return null
        val trace = TraceContext.create()
        return try {
            val response = httpClient.get("${conn.httpBaseUrl}/api/v1/sessions/$sessionId") {
                header(HttpHeaders.Authorization, "Bearer ${conn.token}")
                header(TraceContext.HEADER_TRACE_ID, trace.traceId)
            }
            if (response.status.isSuccess()) {
                response.body<SessionSummary>()
            } else {
                null
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun replaySession(sessionId: String, fromSeq: Long): List<SequencedStreamEvent> {
        val conn = supervisor.getConnectionInfo() ?: return emptyList()
        val trace = TraceContext.create()
        return try {
            val response = httpClient.get("${conn.httpBaseUrl}/api/v1/sessions/$sessionId/replay?fromSeq=$fromSeq") {
                header(HttpHeaders.Authorization, "Bearer ${conn.token}")
                header(TraceContext.HEADER_TRACE_ID, trace.traceId)
            }
            if (response.status.isSuccess()) {
                response.body<List<SequencedStreamEvent>>()
            } else {
                emptyList()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun startRun(request: StartRunRequest): RunHandle = circuitBreaker.execute {
        val conn = supervisor.ensureStarted()
        val trace = TraceContext.create()
        val response = httpClient.post("${conn.httpBaseUrl}/api/v1/runs") {
            header(HttpHeaders.Authorization, "Bearer ${conn.token}")
            header(TraceContext.HEADER_TRACE_ID, trace.traceId)
            header(TraceContext.HEADER_SPAN_ID, trace.spanId)
            header(HEADER_PROTOCOL_VERSION, PROTOCOL_VERSION)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        when (response.status) {
            HttpStatusCode.Created, HttpStatusCode.OK -> response.body<RunHandle>().also {
                runOwners[it.runId] =
                    conn
            }
            HttpStatusCode.Conflict -> {
                val conflict = runCatching { response.body<RunConflict>() }.getOrNull()
                throw ToolingException("Run conflict: workspace locked by active run ${conflict?.existingRunId}")
            }
            HttpStatusCode.UnprocessableEntity -> {
                val err = runCatching { response.body<InvalidWorkingDirectory>() }.getOrNull()
                throw ToolingException("Invalid working directory: ${err?.path} (${err?.reason})")
            }
            else -> throw ToolingException("Failed to start run: status=${response.status}")
        }
    }

    override suspend fun getRun(runId: String): RunStatus? = circuitBreaker.execute {
        val conn = connectionFor(runId)
        val trace = TraceContext.create()
        val response = httpClient.get("${conn.httpBaseUrl}/api/v1/runs/$runId") {
            header(HttpHeaders.Authorization, "Bearer ${conn.token}")
            header(TraceContext.HEADER_TRACE_ID, trace.traceId)
            header(TraceContext.HEADER_SPAN_ID, trace.spanId)
            header(HEADER_PROTOCOL_VERSION, PROTOCOL_VERSION)
        }
        if (response.status == HttpStatusCode.NotFound) {
            null
        } else {
            response.body<RunStatus>()
        }
    }

    override suspend fun listRuns(workspaceLock: String?): List<RunStatus> = circuitBreaker.execute {
        val conn = supervisor.ensureStarted()
        val trace = TraceContext.create()
        val url = buildString {
            append("${conn.httpBaseUrl}/api/v1/runs")
            if (workspaceLock != null) {
                append("?workspaceLock=")
                append(workspaceLock.encodeURLParameter())
            }
        }
        httpClient.get(url) {
            header(HttpHeaders.Authorization, "Bearer ${conn.token}")
            header(TraceContext.HEADER_TRACE_ID, trace.traceId)
            header(TraceContext.HEADER_SPAN_ID, trace.spanId)
            header(HEADER_PROTOCOL_VERSION, PROTOCOL_VERSION)
        }.body()
    }

    override suspend fun cancelRun(runId: String, signal: String): RunCancelResponse? = circuitBreaker.execute {
        val conn = connectionFor(runId)
        val trace = TraceContext.create()
        val response = httpClient.post("${conn.httpBaseUrl}/api/v1/runs/$runId/cancel?signal=$signal") {
            header(HttpHeaders.Authorization, "Bearer ${conn.token}")
            header(TraceContext.HEADER_TRACE_ID, trace.traceId)
            header(TraceContext.HEADER_SPAN_ID, trace.spanId)
            header(HEADER_PROTOCOL_VERSION, PROTOCOL_VERSION)
        }
        if (response.status == HttpStatusCode.NotFound) {
            null
        } else {
            response.body<RunCancelResponse>()
        }
    }

    override fun attachRun(runId: String, fromSeq: Long): Flow<SequencedStreamEvent> =
        attachRun(runId, fromSeq, DEFAULT_HEARTBEAT_TIMEOUT_MS)

    /**
     * Streams run [runId] from [fromSeq], reconnecting from the last delivered event after a dropped
     * connection. Only transport failures reconnect: a failure in the collector (e.g. a journal commit)
     * is rethrown to it, and the cursor advances only after an event was delivered, so nothing is skipped.
     * A normal "Completed" close ends the stream; persistent failures stop after [MAX_RECONNECTS_WITHOUT_PROGRESS].
     */
    public fun attachRun(
        runId: String,
        fromSeq: Long = 1L,
        heartbeatTimeoutMs: Long = DEFAULT_HEARTBEAT_TIMEOUT_MS,
    ): Flow<SequencedStreamEvent> = flow {
        var lastSeq = fromSeq - 1
        var finished = false
        var failuresWithoutProgress = 0
        val deliver: suspend (SequencedStreamEvent) -> Unit = { event ->
            try {
                emit(event)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                throw CollectorFailure(e)
            }
        }

        while (!finished) {
            val conn = connectionFor(runId)
            val currentFromSeq = lastSeq + 1
            val socketUrl = "${conn.wsBaseUrl}/api/v1/runs/$runId/attach?fromSeq=$currentFromSeq&token=${conn.token}"
            var shouldReconnect = false

            try {
                httpClient.webSocket(urlString = socketUrl) {
                    while (true) {
                        val frame = try {
                            withTimeout(heartbeatTimeoutMs) {
                                incoming.receiveCatching().getOrNull()
                            }
                        } catch (_: TimeoutCancellationException) {
                            // Watchdog triggered: 3 missed heartbeats -> reconnect from lastSeq + 1
                            shouldReconnect = true
                            runCatching { close(CloseReason(CloseReason.Codes.GOING_AWAY, "Watchdog timeout")) }
                            break
                        }

                        if (frame == null) {
                            val reason = runCatching { closeReason.await() }.getOrNull()
                            if (reason?.message?.contains("not found", ignoreCase = true) == true) {
                                throw ToolingException("Run '$runId' not found on remote server")
                            }
                            // The service ended the stream itself: the caller reads the run's final state.
                            shouldReconnect = !finished && reason?.message != STREAM_COMPLETED
                            break
                        }

                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            // An event this client cannot read is a protocol mismatch; dropping it would lose
                            // the run's output (and its errors) without a trace.
                            val seqEvent = runCatching {
                                json.decodeFromString<SequencedStreamEvent>(text)
                            }.getOrElse {
                                throw ToolingException("Unreadable event from run '$runId': ${it.message}")
                            }

                            if (seqEvent.seq == 0L || seqEvent.event is StreamEvent.Heartbeat) {
                                deliver(seqEvent)
                            } else if (seqEvent.seq > lastSeq) {
                                deliver(seqEvent)
                                lastSeq = seqEvent.seq
                                failuresWithoutProgress = 0
                                if (seqEvent.event is StreamEvent.ExecutionFinished) {
                                    finished = true
                                    break
                                }
                            }
                        }
                    }
                }
            } catch (e: CollectorFailure) {
                throw e.cause ?: e
            } catch (_: TimeoutCancellationException) {
                shouldReconnect = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: ToolingException) {
                throw e
            } catch (e: Exception) {
                if (finished) break
                // Reconnecting only makes sense while the service that owns the run is still there.
                if (runOwners.containsKey(runId) && !supervisor.isHealthy(conn)) {
                    throw ToolingException(
                        "The build service that was running '$runId' (pid ${conn.pid}) is no longer reachable",
                    )
                }
                if (++failuresWithoutProgress >= MAX_RECONNECTS_WITHOUT_PROGRESS) {
                    throw ToolingException(
                        "Lost the output of run '$runId' after $failuresWithoutProgress reconnect attempts: ${e.message}",
                    )
                }
                shouldReconnect = true
                delay(100)
            }

            if (!shouldReconnect || finished) {
                break
            }
        }
    }

    /** Wraps a failure thrown by the flow's collector so the reconnect logic never mistakes it for the network. */
    private class CollectorFailure(cause: Throwable) : RuntimeException(cause)

    override suspend fun shutdownDaemon(force: Boolean): Boolean = supervisor.shutdownDaemon(force)

    override suspend fun activateToolchain(
        request: io.ltirom.tooling.core.remote.ActivateToolchainRequest
    ): io.ltirom.tooling.core.remote.ActivateToolchainResponse {
        val conn = try {
            supervisor.ensureStarted()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return io.ltirom.tooling.core.remote.ActivateToolchainResponse(
                code = "SERVICE_UNAVAILABLE",
                reason = "Build service daemon is not running: ${e.message}"
            )
        }
        return circuitBreaker.execute {
            val trace = TraceContext.create()
            val response = httpClient.post("${conn.httpBaseUrl}/api/v1/toolchain/activate") {
                header(HttpHeaders.Authorization, "Bearer ${conn.token}")
                header(TraceContext.HEADER_TRACE_ID, trace.traceId)
                header(TraceContext.HEADER_SPAN_ID, trace.spanId)
                header(HEADER_PROTOCOL_VERSION, PROTOCOL_VERSION)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            val text = response.bodyAsText()
            runCatching {
                json.decodeFromString<io.ltirom.tooling.core.remote.ActivateToolchainResponse>(text)
            }.getOrElse {
                io.ltirom.tooling.core.remote.ActivateToolchainResponse(
                    code = "UNKNOWN",
                    reason = "HTTP ${response.status.value}: $text"
                )
            }
        }
    }

    override suspend fun getToolchainState(): io.ltirom.tooling.core.remote.ToolchainStateResponse? {
        val conn = try {
            supervisor.ensureStarted()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return circuitBreaker.execute {
            val trace = TraceContext.create()
            val response = httpClient.get("${conn.httpBaseUrl}/api/v1/toolchain/state") {
                header(HttpHeaders.Authorization, "Bearer ${conn.token}")
                header(TraceContext.HEADER_TRACE_ID, trace.traceId)
                header(TraceContext.HEADER_SPAN_ID, trace.spanId)
                header(HEADER_PROTOCOL_VERSION, PROTOCOL_VERSION)
            }
            if (response.status == HttpStatusCode.OK) {
                val text = response.bodyAsText()
                json.decodeFromString<io.ltirom.tooling.core.remote.ToolchainStateResponse>(text)
            } else {
                null
            }
        }
    }

    override suspend fun getActivation(requestId: String): String? {
        val conn = try {
            supervisor.ensureStarted()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return circuitBreaker.execute {
            val trace = TraceContext.create()
            val response = httpClient.get("${conn.httpBaseUrl}/api/v1/toolchain/activations/$requestId") {
                header(HttpHeaders.Authorization, "Bearer ${conn.token}")
                header(TraceContext.HEADER_TRACE_ID, trace.traceId)
                header(TraceContext.HEADER_SPAN_ID, trace.spanId)
                header(HEADER_PROTOCOL_VERSION, PROTOCOL_VERSION)
            }
            if (response.status == HttpStatusCode.OK) {
                response.bodyAsText()
            } else {
                null
            }
        }
    }
}
