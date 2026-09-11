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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
                                break
                            }
                        }
                    }
                }
            }
        } finally {
            // Signal cancellation to daemon if flow terminates prematurely
            runCatching { cancel(trace.traceId) }
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
}

