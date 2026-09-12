package io.ltirom.tooling.client.transport

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import io.ltirom.tooling.client.resilience.CircuitBreaker
import io.ltirom.tooling.core.ToolingException
import io.ltirom.tooling.core.TraceContext
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
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
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    public val circuitBreaker: CircuitBreaker = CircuitBreaker()
) : RemoteTransportPort {

    public companion object {
        public const val PROTOCOL_VERSION: String = "1.2.0"
        public const val HEADER_PROTOCOL_VERSION: String = "X-LtiRom-Protocol-Version"
        public const val DEFAULT_HEARTBEAT_TIMEOUT_MS: Long = 45_000L
    }

    override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse {
        return circuitBreaker.execute {
            val conn = supervisor.ensureStarted()
            val trace = TraceContext.create()
            try {
                httpClient.post("${conn.httpBaseUrl}/api/v1/tools/execute") {
                    header(HttpHeaders.Authorization, "Bearer ${conn.token}")
                    header(TraceContext.HEADER_TRACE_ID, trace.traceId)
                    header(TraceContext.HEADER_SPAN_ID, trace.spanId)
                    header(HEADER_PROTOCOL_VERSION, PROTOCOL_VERSION)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body()
            } catch (e: Exception) {
                throw ToolingException(
                    "Failed to execute command on remote daemon at ${conn.httpBaseUrl} [trace=${trace.traceId}]: ${e.message}",
                    e
                )
            }
        }
    }

    override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = flow {
        val conn = supervisor.ensureStarted()
        val trace = TraceContext.create()
        val requestJson = json.encodeToString(request)
        var finished = false

        try {
            httpClient.webSocket(
                urlString = "${conn.wsBaseUrl}/api/v1/tools/stream?token=${conn.token}&traceId=${trace.traceId}"
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
                urlString = "${conn.wsBaseUrl}/api/v1/tools/stream?token=${conn.token}&traceId=${trace.traceId}&sequenced=true"
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
            } else null
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun listTools(): List<ToolStatusInfo> {
        val conn = supervisor.ensureStarted()
        val trace = TraceContext.create()
        return try {
            val response = httpClient.get("${conn.httpBaseUrl}/api/v1/tools") {
                header(HttpHeaders.Authorization, "Bearer ${conn.token}")
                header(TraceContext.HEADER_TRACE_ID, trace.traceId)
            }
            if (response.status.isSuccess()) {
                response.body<List<ToolStatusInfo>>()
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun refreshTools(): List<ToolStatusInfo> {
        val conn = supervisor.ensureStarted()
        val trace = TraceContext.create()
        return try {
            val response = httpClient.post("${conn.httpBaseUrl}/api/v1/tools/refresh") {
                header(HttpHeaders.Authorization, "Bearer ${conn.token}")
                header(TraceContext.HEADER_TRACE_ID, trace.traceId)
            }
            if (response.status.isSuccess()) {
                response.body<List<ToolStatusInfo>>()
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
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
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun shutdown(): Boolean {
        return supervisor.shutdownDaemon()
    }

    override suspend fun cancel(executionId: String, signal: String): Boolean {
        val conn = supervisor.getConnectionInfo() ?: return false
        return try {
            val response = httpClient.post("${conn.httpBaseUrl}/api/v1/tools/execute/$executionId/cancel?signal=$signal") {
                header(HttpHeaders.Authorization, "Bearer ${conn.token}")
            }
            response.status.isSuccess()
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
            } else emptyList()
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
            } else null
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
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun startRun(request: StartRunRequest): RunHandle {
        return circuitBreaker.execute {
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
                HttpStatusCode.Created, HttpStatusCode.OK -> response.body<RunHandle>()
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
    }

    override suspend fun getRun(runId: String): RunStatus? {
        return circuitBreaker.execute {
            val conn = supervisor.ensureStarted()
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
    }

    override suspend fun listRuns(workspaceLock: String?): List<RunStatus> {
        return circuitBreaker.execute {
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
    }

    override suspend fun cancelRun(runId: String, signal: String): RunCancelResponse? {
        return circuitBreaker.execute {
            val conn = supervisor.ensureStarted()
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
    }

    override fun attachRun(runId: String, fromSeq: Long): Flow<SequencedStreamEvent> =
        attachRun(runId, fromSeq, DEFAULT_HEARTBEAT_TIMEOUT_MS)

    public fun attachRun(
        runId: String,
        fromSeq: Long = 1L,
        heartbeatTimeoutMs: Long = DEFAULT_HEARTBEAT_TIMEOUT_MS
    ): Flow<SequencedStreamEvent> = flow {
        var lastSeq = fromSeq - 1
        var finished = false

        while (!finished) {
            val conn = supervisor.ensureStarted()
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
                            shouldReconnect = !finished
                            break
                        }

                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            val seqEvent = runCatching {
                                json.decodeFromString<SequencedStreamEvent>(text)
                            }.getOrNull()

                            if (seqEvent != null) {
                                if (seqEvent.seq == 0L || seqEvent.event is StreamEvent.Heartbeat) {
                                    emit(seqEvent)
                                } else if (seqEvent.seq > lastSeq) {
                                    lastSeq = seqEvent.seq
                                    emit(seqEvent)
                                    if (seqEvent.event is StreamEvent.ExecutionFinished) {
                                        finished = true
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                shouldReconnect = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: ToolingException) {
                throw e
            } catch (_: Exception) {
                if (finished) break
                shouldReconnect = true
                delay(100)
            }

            if (!shouldReconnect || finished) {
                break
            }
        }
    }

    override suspend fun shutdownDaemon(force: Boolean): Boolean {
        return supervisor.shutdownDaemon(force)
    }
}

