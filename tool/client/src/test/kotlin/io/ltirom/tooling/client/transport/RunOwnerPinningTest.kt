package io.ltirom.tooling.client.transport

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import io.ltirom.tooling.core.remote.RunCancelResponse
import io.ltirom.tooling.core.remote.RunHandle
import io.ltirom.tooling.core.remote.RunStatus
import io.ltirom.tooling.core.remote.RunStatusValue
import io.ltirom.tooling.core.remote.StartRunRequest
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A run lives only in the memory of the service that accepted it. Its status and cancel must go to that
 * service even when the supervisor has since switched to another one (the "run not found" failure).
 */
class RunOwnerPinningTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val servers = mutableListOf<HttpServer>()

    @AfterTest
    fun stop() = servers.forEach { it.stop(0) }

    /** A fake service: knows run `r1` when [ownsRun], answers 404 otherwise; records every request path. */
    private fun service(ownsRun: Boolean, hits: MutableList<String>): Int {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            hits += "${ex.requestMethod} ${ex.requestURI.path}"
            val path = ex.requestURI.path
            val (code, body) = when {
                path == "/api/v1/runs" && ex.requestMethod == "POST" ->
                    201 to json.encodeToString(RunHandle("r1", RunStatusValue.QUEUED, 1L))
                !ownsRun -> 404 to "Run 'r1' not found"
                path == "/api/v1/runs/r1" ->
                    200 to json.encodeToString(RunStatus("r1", RunStatusValue.COMPLETED, exitCode = 0, startedAtEpochMs = 1L))
                path == "/api/v1/runs/r1/cancel" ->
                    200 to json.encodeToString(RunCancelResponse(false, RunStatusValue.COMPLETED))
                else -> 404 to "not found"
            }
            val bytes = body.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(code, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
        servers += server
        return server.address.port
    }

    /** Resolves to [first] until the run has started, then to [second] (a restart or update). */
    private class SwitchingSupervisor(
        val first: ServerConnectionDescriptor,
        val second: ServerConnectionDescriptor,
    ) : DaemonSupervisorPort {
        var switched = false
        override suspend fun ensureStarted() = if (switched) second else first
        override suspend fun getConnectionInfo() = ensureStarted()
        override suspend fun isHealthy(info: ServerConnectionDescriptor) = true
        override suspend fun shutdownDaemon() = true
        override fun close() {}
    }

    private fun descriptor(port: Int, pid: Long) = ServerConnectionDescriptor("127.0.0.1", port, "t", pid, "Ubuntu")

    @Test
    fun `status and cancel go to the service that started the run`() = runBlocking {
        val ownerHits = CopyOnWriteArrayList<String>()
        val otherHits = CopyOnWriteArrayList<String>()
        val supervisor = SwitchingSupervisor(
            descriptor(service(ownsRun = true, hits = ownerHits), pid = 1),
            descriptor(service(ownsRun = false, hits = otherHits), pid = 2),
        )
        val client = HttpClient(CIO) { install(ContentNegotiation) { json(json) } }
        val transport = KtorRemoteTransportAdapter(supervisor, client)

        val handle = transport.startRun(
            StartRunRequest(ToolExecutionRequest(toolId = "cmake", arguments = emptyList()), idempotencyKey = "k"),
        )
        supervisor.switched = true

        assertEquals(RunStatusValue.COMPLETED, transport.getRun(handle.runId)?.status)
        transport.cancelRun(handle.runId, "SIGTERM")
        assertTrue(otherHits.isEmpty(), "Nothing about r1 may go to the other service: $otherHits")
        assertEquals(listOf("POST /api/v1/runs", "GET /api/v1/runs/r1", "POST /api/v1/runs/r1/cancel"), ownerHits)
        client.close()
    }

    @Test
    fun `a run this session did not start falls back to the current service`() = runBlocking {
        val hits = CopyOnWriteArrayList<String>()
        val current = descriptor(service(ownsRun = true, hits = hits), pid = 3)
        val supervisor = SwitchingSupervisor(current, current)
        val client = HttpClient(CIO) { install(ContentNegotiation) { json(json) } }

        // e.g. recovery after an app restart: the run was journaled by the previous session
        val status = KtorRemoteTransportAdapter(supervisor, client).getRun("r1")

        assertEquals(RunStatusValue.COMPLETED, status?.status)
        assertEquals(listOf("GET /api/v1/runs/r1"), hits)
        client.close()
    }
}
