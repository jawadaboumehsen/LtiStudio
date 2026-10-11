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
import io.ltirom.tooling.client.resilience.CommandAdmissionException
import io.ltirom.tooling.core.RemoteCancellationUnconfirmedException
import io.ltirom.tooling.core.ToolingException
import io.ltirom.tooling.core.TraceContext
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import io.ltirom.tooling.core.ports.StopClientResult
import io.ltirom.tooling.core.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Adapter implementing [RemoteTransportPort] via Ktor HTTP and WebSocket clients.
 * Protected with [CircuitBreaker] resilience and distributed [TraceContext] propagation.
 */
public class KtorRemoteTransportAdapter(
    private val supervisor: DaemonSupervisorPort,
    private val httpClient: HttpClient,
    private val json: Json =
        Json {
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
        private val CLIENT_ERROR_RANGE = 400..499
        private val COUNTED_CLIENT_ERRORS = setOf(401, 403, 408, 429)

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

    private fun ToolExecutionRequest.normalize(): ToolExecutionRequest =
        if (workingDirectory.isNullOrBlank()) copy(workingDirectory = "/tmp") else this

    override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse {
        // The connection is resolved first: the breaker belongs to it, so a dead server's failures never gate
        // its replacement.
        val conn = supervisor.ensureStarted()
        return circuitBreaker.execute(conn) {
            val req = request.normalize()
            val trace = TraceContext.create()
            try {
                val response = postExecute(conn, req, trace)
                if (response.status == HttpStatusCode.ServiceUnavailable) {
                    val errorCode = response.headers["X-LtiRom-Error-Code"]
                    if (errorCode == "CLIENT_HEARTBEAT_REQUIRED") {
                        val currentConn = supervisor.getConnectionInfo()
                        if (currentConn == conn && sendHeartbeat(conn)) {
                            val retryResponse = postExecute(conn, req, trace)
                            if (retryResponse.status.isSuccess()) {
                                return@execute retryResponse.body()
                            }
                            if (retryResponse.status == HttpStatusCode.ServiceUnavailable &&
                                retryResponse.headers["X-LtiRom-Error-Code"] == "CLIENT_HEARTBEAT_REQUIRED"
                            ) {
                                throw CommandAdmissionException(
                                    "Command admission is closed; client heartbeat required",
                                )
                            }
                            throw serverError(retryResponse.status, retryResponse.bodyAsText())
                        } else {
                            throw CommandAdmissionException("Command admission is closed; client heartbeat required")
                        }
                    }
                    if (errorCode == "SERVER_STOPPING" || errorCode == "CANCELLATION_IN_PROGRESS") {
                        throw ToolingException("Build service is stopping or cancelling executions")
                    }
                }
                if (!response.status.isSuccess()) {
                    throw serverError(response.status, response.bodyAsText())
                }
                response.body()
            } catch (ce: CancellationException) {
                cancelExecutionBestEffort(conn, trace.traceId, "SIGTERM")
                throw ce
            } catch (cae: CommandAdmissionException) {
                throw cae
            } catch (te: ToolingException) {
                throw te
            } catch (e: Exception) {
                throw ToolingException(
                    "Failed to execute command on remote daemon at ${conn.httpBaseUrl} [trace=${trace.traceId}]: ${e.message}",
                    e,
                )
            }
        }
    }

    /**
     * A request the service understood and refused (bad id, conflict, validation) says nothing about its health, so
     * it must not count towards opening the circuit; authentication, timeouts, throttling and 5xx do.
     */
    private fun serverError(status: HttpStatusCode, body: String): ToolingException {
        val message = "Server returned HTTP ${status.value}: $body"
        val business = status.value in CLIENT_ERROR_RANGE && status.value !in COUNTED_CLIENT_ERRORS
        return if (business) {
            io.ltirom.tooling.client.resilience.ToolingClientException(message)
        } else {
            ToolingException(message)
        }
    }

    private suspend fun postExecute(
        conn: ServerConnectionDescriptor,
        request: ToolExecutionRequest,
        trace: TraceContext,
    ) = httpClient.post("${conn.httpBaseUrl}/api/v1/tools/execute") {
        if (httpClient.pluginOrNull(HttpTimeout) != null) {
            timeout { requestTimeoutMillis = request.timeoutMs + EXECUTE_RESPONSE_MARGIN_MS }
        }
        header(HttpHeaders.Authorization, "Bearer ${conn.token}")
        header(TraceContext.HEADER_TRACE_ID, trace.traceId)
        header("X-Trace-Id", trace.traceId)
        header(TraceContext.HEADER_SPAN_ID, trace.spanId)
        header(HEADER_PROTOCOL_VERSION, PROTOCOL_VERSION)
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = channelFlow {
        val req = request.normalize()
        val conn = supervisor.ensureStarted()
        val trace = TraceContext.create()
        val traceId = req.environment["LTI_TRACE_ID"] ?: trace.traceId
        val requestJson = json.encodeToString(req)
        var finished = false
        var emittedAny = false

        try {
            httpClient.webSocket(
                urlString = "${conn.wsBaseUrl}/api/v1/tools/stream?token=${conn.token}&traceId=$traceId",
            ) {
                send(Frame.Text(requestJson))
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val event =
                            runCatching {
                                json.decodeFromString<StreamEvent>(text)
                            }.getOrNull()

                        if (event != null) {
                            emittedAny = true
                            send(event)
                            if (event is StreamEvent.ExecutionFinished) {
                                finished = true
                                break
                            }
                        }
                    }
                }
                if (!finished) {
                    val reason = closeReason.await()
                    if (!emittedAny && reason?.message == "CLIENT_HEARTBEAT_REQUIRED") {
                        val currentConn = supervisor.getConnectionInfo()
                        if (currentConn == conn && sendHeartbeat(conn)) {
                            httpClient.webSocket(
                                urlString = "${conn.wsBaseUrl}/api/v1/tools/stream?token=${conn.token}&traceId=$traceId",
                            ) {
                                send(Frame.Text(requestJson))
                                for (frame in incoming) {
                                    if (frame is Frame.Text) {
                                        val text = frame.readText()
                                        val event =
                                            runCatching {
                                                json.decodeFromString<StreamEvent>(
                                                    text,
                                                )
                                            }.getOrNull()
                                        if (event != null) {
                                            send(event)
                                            if (event is StreamEvent.ExecutionFinished) {
                                                finished = true
                                                break
                                            }
                                        }
                                    }
                                }
                                if (!finished) {
                                    val retryReason = closeReason.await()
                                    if (retryReason?.message == "CLIENT_HEARTBEAT_REQUIRED") {
                                        throw CommandAdmissionException(
                                            "Command admission is closed; client heartbeat required",
                                        )
                                    }
                                    throw ToolingException(
                                        "Stream closed without ExecutionFinished after retry: ${retryReason?.message ?: "unknown"}",
                                    )
                                }
                            }
                        } else {
                            throw CommandAdmissionException(
                                "Command admission is closed; client heartbeat required",
                            )
                        }
                    } else {
                        throw ToolingException(
                            "Stream closed without ExecutionFinished: ${reason?.message ?: "socket closed"}",
                        )
                    }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (cae: CommandAdmissionException) {
            throw cae
        } catch (te: ToolingException) {
            throw te
        } catch (e: Exception) {
            throw ToolingException("Stream error on remote daemon at ${conn.httpBaseUrl}: ${e.message}", e)
        } finally {
            if (!finished) {
                withContext(NonCancellable) {
                    withTimeoutOrNull(5_000L) {
                        cancelExecutionDirect(conn, traceId, "SIGTERM")
                    }
                }
            }
        }
    }

    override fun streamSequenced(request: ToolExecutionRequest): Flow<SequencedStreamEvent> = channelFlow {
        val req = request.normalize()
        val conn = supervisor.ensureStarted()
        val trace = TraceContext.create()
        val traceId = req.environment["LTI_TRACE_ID"] ?: trace.traceId
        val requestJson = json.encodeToString(req)
        var finished = false
        var emittedAny = false

        try {
            httpClient.webSocket(
                urlString = "${conn.wsBaseUrl}/api/v1/tools/stream?token=${conn.token}&traceId=$traceId&sequenced=true",
            ) {
                send(Frame.Text(requestJson))
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val event =
                            runCatching {
                                json.decodeFromString<SequencedStreamEvent>(text)
                            }.getOrNull()

                        if (event != null) {
                            emittedAny = true
                            send(event)
                            if (event.event is StreamEvent.ExecutionFinished) {
                                finished = true
                                break
                            }
                        }
                    }
                }
                if (!finished) {
                    val reason = closeReason.await()
                    if (!emittedAny && reason?.message == "CLIENT_HEARTBEAT_REQUIRED") {
                        val currentConn = supervisor.getConnectionInfo()
                        if (currentConn == conn && sendHeartbeat(conn)) {
                            httpClient.webSocket(
                                urlString = "${conn.wsBaseUrl}/api/v1/tools/stream?token=${conn.token}&traceId=$traceId&sequenced=true",
                            ) {
                                send(Frame.Text(requestJson))
                                for (frame in incoming) {
                                    if (frame is Frame.Text) {
                                        val text = frame.readText()
                                        val event =
                                            runCatching {
                                                json.decodeFromString<SequencedStreamEvent>(text)
                                            }.getOrNull()

                                        if (event != null) {
                                            send(event)
                                            if (event.event is StreamEvent.ExecutionFinished) {
                                                finished = true
                                                break
                                            }
                                        }
                                    }
                                }
                                if (!finished) {
                                    val retryReason = closeReason.await()
                                    if (retryReason?.message == "CLIENT_HEARTBEAT_REQUIRED") {
                                        throw CommandAdmissionException(
                                            "Command admission is closed; client heartbeat required",
                                        )
                                    }
                                    throw ToolingException(
                                        "Stream closed without ExecutionFinished after retry: ${retryReason?.message ?: "unknown"}",
                                    )
                                }
                            }
                        } else {
                            throw CommandAdmissionException(
                                "Command admission is closed; client heartbeat required",
                            )
                        }
                    } else {
                        throw ToolingException(
                            "Stream closed without ExecutionFinished: ${reason?.message ?: "socket closed"}",
                        )
                    }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (cae: CommandAdmissionException) {
            throw cae
        } catch (te: ToolingException) {
            throw te
        } catch (e: Exception) {
            throw ToolingException("Stream error on remote daemon at ${conn.httpBaseUrl}: ${e.message}", e)
        } finally {
            if (!finished) {
                withContext(NonCancellable) {
                    withTimeoutOrNull(5_000L) {
                        cancelExecutionDirect(conn, traceId, "SIGTERM")
                    }
                }
            }
        }
    }

    override suspend fun checkHealth(): WslServerInfo? {
        val conn = supervisor.ensureStarted()
        val trace = TraceContext.create()
        return try {
            val response =
                httpClient.get("${conn.httpBaseUrl}/api/v1/health") {
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
            val response =
                httpClient.get("${conn.httpBaseUrl}/api/v1/tools") {
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
        } catch (e: java.io.IOException) {
            ToolListResult.Unreachable(e.message ?: "No answer while listing tools")
        } catch (e: Exception) {
            ToolListResult.ServiceError(e.message ?: "Failed to list tools")
        }
    }

    override suspend fun refreshTools(): ToolListResult {
        val conn = supervisor.ensureStarted()
        val trace = TraceContext.create()
        return try {
            val response =
                httpClient.post("${conn.httpBaseUrl}/api/v1/tools/refresh") {
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
        } catch (e: java.io.IOException) {
            ToolListResult.Unreachable(e.message ?: "No answer while reloading tools")
        } catch (e: Exception) {
            ToolListResult.ServiceError(e.message ?: "Failed to refresh tools")
        }
    }

    override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean {
        val conn = supervisor.ensureStarted()
        val trace = TraceContext.create()
        return try {
            val response =
                httpClient.post("${conn.httpBaseUrl}/api/v1/binary/upload") {
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
            val response =
                httpClient.get("${conn.httpBaseUrl}/api/v1/binary/download") {
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

    override suspend fun sendHeartbeat(): Boolean {
        val conn = supervisor.getConnectionInfo() ?: supervisor.ensureStarted()
        return sendHeartbeat(conn)
    }

    override suspend fun sendHeartbeat(descriptor: ServerConnectionDescriptor): Boolean = withContext(Dispatchers.IO) {
        try {
            withTimeoutOrNull(10_000L) {
                val response =
                    httpClient.post("${descriptor.httpBaseUrl}/api/v1/client/heartbeat") {
                        header(HttpHeaders.Authorization, "Bearer ${descriptor.token}")
                        header(HEADER_PROTOCOL_VERSION, PROTOCOL_VERSION)
                    }
                response.status == HttpStatusCode.NoContent
            } ?: false
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun stopClient(): StopClientResult = try {
        val conn = supervisor.getConnectionInfo() ?: return StopClientResult(stopped = false)
        val response =
            httpClient.post("${conn.httpBaseUrl}/api/v1/client/stop") {
                header(HttpHeaders.Authorization, "Bearer ${conn.token}")
                header(HEADER_PROTOCOL_VERSION, PROTOCOL_VERSION)
            }
        if (response.status == HttpStatusCode.OK) {
            response.body<StopClientResult>()
        } else {
            StopClientResult(stopped = false)
        }
    } catch (ce: CancellationException) {
        throw ce
    } catch (_: Exception) {
        StopClientResult(stopped = false)
    }

    /**
     * The bundled service acknowledges cancellation only after observing process-tree termination.
     * HTTP 200 alone is not acknowledgement: false or malformed payloads remain unconfirmed.
     */
    override suspend fun cancel(executionId: String, signal: String): Boolean {
        val conn = supervisor.getConnectionInfo() ?: return false
        return cancelExecutionDirect(conn, executionId, signal)
    }

    private suspend fun cancelExecutionDirect(
        conn: ServerConnectionDescriptor,
        executionId: String,
        signal: String,
    ): Boolean = try {
        val response =
            httpClient.post("${conn.httpBaseUrl}/api/v1/tools/execute/$executionId/cancel") {
                parameter("signal", signal)
                header(HttpHeaders.Authorization, "Bearer ${conn.token}")
            }
        response.status.isSuccess() &&
            response.body<ProcessCancellationResponse>().let {
                it.executionId == executionId && it.success
            }
    } catch (ce: CancellationException) {
        throw ce
    } catch (_: Exception) {
        false
    }

    /** Keep cleanup on the accepting service even after cancellation or a supervisor switch. */
    private suspend fun cancelExecutionBestEffort(
        conn: ServerConnectionDescriptor,
        executionId: String,
        signal: String,
    ) {
        val confirmed =
            withContext(NonCancellable) {
                withTimeoutOrNull(5_000L) {
                    cancelExecutionDirect(conn, executionId, signal)
                } ?: false
            }
        if (!confirmed) throw RemoteCancellationUnconfirmedException(executionId)
    }

    @Serializable
    private data class ProcessCancellationResponse(val success: Boolean, val executionId: String)

    override suspend fun listSessions(): List<SessionSummary> {
        val conn = supervisor.getConnectionInfo() ?: return emptyList()
        val trace = TraceContext.create()
        return try {
            val response =
                httpClient.get("${conn.httpBaseUrl}/api/v1/sessions") {
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
            val response =
                httpClient.get("${conn.httpBaseUrl}/api/v1/sessions/$sessionId") {
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
            val response =
                httpClient.get("${conn.httpBaseUrl}/api/v1/sessions/$sessionId/replay?fromSeq=$fromSeq") {
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

    override suspend fun startRun(request: StartRunRequest): RunHandle {
        val conn = supervisor.ensureStarted()
        return circuitBreaker.execute(conn) {
            val trace = TraceContext.create()
            try {
                var response = postStartRun(conn, request, trace)
                if (response.status == HttpStatusCode.ServiceUnavailable) {
                    val errorCode = response.headers["X-LtiRom-Error-Code"]
                    if (errorCode == "CLIENT_HEARTBEAT_REQUIRED") {
                        val currentConn = supervisor.getConnectionInfo()
                        if (currentConn == conn && sendHeartbeat(conn)) {
                            response = postStartRun(conn, request, trace)
                            if (response.status == HttpStatusCode.ServiceUnavailable &&
                                response.headers["X-LtiRom-Error-Code"] == "CLIENT_HEARTBEAT_REQUIRED"
                            ) {
                                throw CommandAdmissionException(
                                    "Command admission is closed; client heartbeat required",
                                )
                            }
                        } else {
                            throw CommandAdmissionException("Command admission is closed; client heartbeat required")
                        }
                    } else if (errorCode == "SERVER_STOPPING" || errorCode == "CANCELLATION_IN_PROGRESS") {
                        throw ToolingException("Build service is stopping or cancelling executions")
                    }
                }
                when (response.status) {
                    HttpStatusCode.Created, HttpStatusCode.OK ->
                        response.body<RunHandle>().also {
                            runOwners[it.runId] = conn
                        }
                    HttpStatusCode.Conflict -> {
                        val conflict = runCatching { response.body<RunConflict>() }.getOrNull()
                        throw io.ltirom.tooling.client.resilience.ToolingClientException(
                            "Run conflict: workspace locked by active run ${conflict?.existingRunId}",
                        )
                    }
                    HttpStatusCode.UnprocessableEntity -> {
                        val err = runCatching { response.body<InvalidWorkingDirectory>() }.getOrNull()
                        throw io.ltirom.tooling.client.resilience.ToolingClientException(
                            "Invalid working directory: ${err?.path} (${err?.reason})",
                        )
                    }
                    HttpStatusCode.ServiceUnavailable -> {
                        if (response.headers["X-LtiRom-Error-Code"] == "CLIENT_HEARTBEAT_REQUIRED") {
                            throw CommandAdmissionException("Command admission is closed; client heartbeat required")
                        }
                        throw ToolingException("The build service is unavailable: ${response.bodyAsText()}")
                    }
                    else -> throw ToolingException("Failed to start run: status=${response.status}")
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (cae: CommandAdmissionException) {
                throw cae
            } catch (te: ToolingException) {
                throw te
            } catch (e: Exception) {
                throw ToolingException(
                    "Failed to start run on remote daemon at ${conn.httpBaseUrl} [trace=${trace.traceId}]: ${e.message}",
                    e,
                )
            }
        }
    }

    private suspend fun postStartRun(conn: ServerConnectionDescriptor, request: StartRunRequest, trace: TraceContext) =
        httpClient.post("${conn.httpBaseUrl}/api/v1/runs") {
            header(HttpHeaders.Authorization, "Bearer ${conn.token}")
            header(TraceContext.HEADER_TRACE_ID, trace.traceId)
            header(TraceContext.HEADER_SPAN_ID, trace.spanId)
            header(HEADER_PROTOCOL_VERSION, PROTOCOL_VERSION)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    override suspend fun getRun(runId: String): RunStatus? {
        val conn = connectionFor(runId)
        return circuitBreaker.execute(conn) { getRunStatusDirect(conn, runId) }
    }

    private suspend fun getRunStatusDirect(conn: ServerConnectionDescriptor, runId: String): RunStatus? {
        val trace = TraceContext.create()
        val response =
            httpClient.get("${conn.httpBaseUrl}/api/v1/runs/$runId") {
                header(HttpHeaders.Authorization, "Bearer ${conn.token}")
                header(TraceContext.HEADER_TRACE_ID, trace.traceId)
                header(TraceContext.HEADER_SPAN_ID, trace.spanId)
                header(HEADER_PROTOCOL_VERSION, PROTOCOL_VERSION)
            }
        return if (response.status == HttpStatusCode.NotFound) {
            null
        } else {
            response.body<RunStatus>()
        }
    }

    override suspend fun listRuns(workspaceLock: String?): List<RunStatus> {
        val conn = supervisor.ensureStarted()
        return circuitBreaker.execute(conn) {
            val trace = TraceContext.create()
            val url =
                buildString {
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
        val conn = connectionFor(runId)
        val trace = TraceContext.create()
        return try {
            val response =
                httpClient.post("${conn.httpBaseUrl}/api/v1/runs/$runId/cancel?signal=$signal") {
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
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            throw ToolingException(
                "Failed to cancel run '$runId' on remote daemon at ${conn.httpBaseUrl} [trace=${trace.traceId}]: ${e.message}",
                e,
            )
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
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Throwable,
            ) {
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
                        val frame =
                            try {
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
                            shouldReconnect = !finished && reason?.message != STREAM_COMPLETED
                            break
                        }

                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            val seqEvent =
                                runCatching {
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
                if (runOwners.containsKey(runId) && !supervisor.isHealthy(conn)) {
                    throw ToolingException(
                        "The build service that was running '$runId' (pid ${conn.pid}) is no longer reachable",
                    )
                }
                shouldReconnect = true
            }

            if (shouldReconnect && !finished) {
                if (++failuresWithoutProgress >= MAX_RECONNECTS_WITHOUT_PROGRESS) {
                    val finalStatus =
                        try {
                            getRunStatusDirect(conn, runId)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            null
                        }
                    if (finalStatus != null &&
                        finalStatus.status in
                        setOf(RunStatusValue.COMPLETED, RunStatusValue.FAILED, RunStatusValue.CANCELLED, RunStatusValue.INTERRUPTED)
                    ) {
                        throw io.ltirom.tooling.core.RunOutputUnavailableException(runId, finalStatus)
                    }
                    throw ToolingException(
                        "Lost the output of run '$runId' after $failuresWithoutProgress reconnect attempts without progress",
                    )
                }
                delay((100L * failuresWithoutProgress).coerceAtMost(2000L))
            }

            if (!shouldReconnect || finished) {
                break
            }
        }
    }

    /** Wraps a failure thrown by the flow's collector so the reconnect logic never mistakes it for the network. */
    private class CollectorFailure(cause: Throwable) : RuntimeException(cause)

    override suspend fun shutdownDaemon(force: Boolean): Boolean = supervisor.shutdownDaemon(force)
}
