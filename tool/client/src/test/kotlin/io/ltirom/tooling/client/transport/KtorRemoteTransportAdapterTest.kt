package io.ltirom.tooling.client.transport

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ltirom.tooling.client.resilience.CircuitState
import io.ltirom.tooling.core.ToolingException
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.ToolStatusInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

class KtorRemoteTransportAdapterTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private class TestSupervisor(private val descriptor: ServerConnectionDescriptor) : DaemonSupervisorPort {
        override suspend fun ensureStarted(): ServerConnectionDescriptor = descriptor

        override suspend fun getConnectionInfo(): ServerConnectionDescriptor = descriptor

        override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean = true

        override suspend fun shutdownDaemon(): Boolean = true

        override fun close() {}
    }

    private class SimpleMockServer : AutoCloseable {
        val serverSocket = ServerSocket(0)
        val port: Int get() = serverSocket.localPort
        val receivedRequests = CopyOnWriteArrayList<String>()

        @Volatile
        var handler: ((Socket, String, InputStream, OutputStream) -> Unit)? = null

        @Volatile
        private var running = true

        private val thread =
            Thread {
                while (running) {
                    try {
                        val socket = serverSocket.accept()
                        Thread {
                            handleSocket(socket)
                        }.apply { isDaemon = true }.start()
                    } catch (_: Exception) {
                        break
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }

        private fun handleSocket(socket: Socket) {
            socket.use { s ->
                val input = s.getInputStream()
                val output = s.getOutputStream()
                val requestLine = readLine(input) ?: return
                receivedRequests.add(requestLine)

                val customHandler = handler
                if (customHandler != null) {
                    customHandler(s, requestLine, input, output)
                    return
                }

                // Default dummy 200
                output.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".toByteArray())
                output.flush()
            }
        }

        private fun readLine(input: InputStream): String? {
            val sb = StringBuilder()
            while (true) {
                val b = input.read()
                if (b == -1) return if (sb.isEmpty()) null else sb.toString()
                if (b == '\n'.code) break
                if (b != '\r'.code) sb.append(b.toChar())
            }
            return sb.toString()
        }

        override fun close() {
            running = false
            runCatching { serverSocket.close() }
        }
    }

    private fun createHttpClient(): HttpClient = HttpClient(CIO) {
        install(io.ktor.client.plugins.websocket.WebSockets)
        install(ContentNegotiation) {
            json(this@KtorRemoteTransportAdapterTest.json)
        }
    }

    @Test
    fun `listTools returns Tools on 200 OK`() = runTest {
        SimpleMockServer().use { server ->
            val expectedTools =
                listOf(
                    ToolStatusInfo(tool = "adb", installed = true, path = "/usr/bin/adb", source = "PINNED"),
                    ToolStatusInfo(
                        tool = "erofsfuse",
                        installed = true,
                        path = "/tools/erofsfuse",
                        source = "DYNAMIC",
                    ),
                )
            val jsonBody = json.encodeToString(expectedTools)

            server.handler = { _, _, _, output ->
                output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${jsonBody.toByteArray().size}\r\n\r\n$jsonBody".toByteArray())
                output.flush()
            }

            val client = createHttpClient()
            try {
                val adapter =
                    KtorRemoteTransportAdapter(
                        supervisor =
                        TestSupervisor(
                            ServerConnectionDescriptor("127.0.0.1", server.port, "token", 123L, "Ubuntu"),
                        ),
                        httpClient = client,
                        json = json,
                    )

                val result = adapter.listTools()
                assertIs<ToolListResult.Tools>(result)
                assertEquals(2, result.list.size)
                assertEquals("adb", result.list[0].tool)
                assertEquals("erofsfuse", result.list[1].tool)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `listTools returns ServiceError on HTTP 500`() = runTest {
        SimpleMockServer().use { server ->
            server.handler = { _, _, _, output ->
                output.write("HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\n\r\n".toByteArray())
                output.flush()
            }

            val client = createHttpClient()
            try {
                val adapter =
                    KtorRemoteTransportAdapter(
                        supervisor =
                        TestSupervisor(
                            ServerConnectionDescriptor("127.0.0.1", server.port, "token", 123L, "Ubuntu"),
                        ),
                        httpClient = client,
                        json = json,
                    )

                val result = adapter.listTools()
                assertIs<ToolListResult.ServiceError>(result)
                assertTrue(result.reason.contains("500"))
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `listTools reports no answer at all as Unreachable, not as a bad answer`() = runTest {
        SimpleMockServer().use { server ->
            // Immediately close the socket when request arrives
            server.handler = { socket, _, _, _ ->
                socket.close()
            }

            val client = createHttpClient()
            try {
                val adapter =
                    KtorRemoteTransportAdapter(
                        supervisor =
                        TestSupervisor(
                            ServerConnectionDescriptor("127.0.0.1", server.port, "token", 123L, "Ubuntu"),
                        ),
                        httpClient = client,
                        json = json,
                    )

                val result = adapter.listTools()
                assertIs<ToolListResult.Unreachable>(result)
                assertTrue(result.reason.isNotBlank())
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `listTools rethrows CancellationException when cancelled`() = runTest {
        SimpleMockServer().use { server ->
            server.handler = { _, _, _, _ ->
                // Hang without responding to allow cancellation
                Thread.sleep(2000)
            }

            val client = createHttpClient()
            try {
                val adapter =
                    KtorRemoteTransportAdapter(
                        supervisor =
                        TestSupervisor(
                            ServerConnectionDescriptor("127.0.0.1", server.port, "token", 123L, "Ubuntu"),
                        ),
                        httpClient = client,
                        json = json,
                    )

                assertFailsWith<CancellationException> {
                    withTimeout(100) {
                        adapter.listTools()
                    }
                }
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `checkHealth rethrows CancellationException when cancelled`() = runTest {
        SimpleMockServer().use { server ->
            server.handler = { _, _, _, _ ->
                Thread.sleep(2000)
            }

            val client = createHttpClient()
            try {
                val adapter =
                    KtorRemoteTransportAdapter(
                        supervisor =
                        TestSupervisor(
                            ServerConnectionDescriptor("127.0.0.1", server.port, "token", 123L, "Ubuntu"),
                        ),
                        httpClient = client,
                        json = json,
                    )

                assertFailsWith<CancellationException> {
                    withTimeout(100) {
                        adapter.checkHealth()
                    }
                }
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `explicit CLIENT_HEARTBEAT_REQUIRED 503 re-establishes heartbeat on same descriptor and retries command once`() =
        runTest {
            SimpleMockServer().use { server ->
                val responseBody = json.encodeToString(ToolExecutionResponse(0, "success", "", 10L))
                var executeCalls = 0
                var heartbeatCalls = 0

                server.handler = { _, requestLine, input, output ->
                    // Drain the request before closing the socket; unread body bytes can cause a
                    // TCP reset on Windows and discard the response despite a successful flush.
                    val headers = mutableListOf<String>()
                    val line = StringBuilder()
                    while (true) {
                        val byte = input.read()
                        if (byte == -1) break
                        if (byte == '\n'.code) {
                            val value = line.toString().trimEnd('\r')
                            line.setLength(0)
                            if (value.isEmpty()) break
                            headers += value
                        } else {
                            line.append(byte.toChar())
                        }
                    }
                    val length =
                        headers.firstOrNull { it.startsWith("Content-Length:", true) }
                            ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
                    repeat(length) { input.read() }
                    when {
                        requestLine.contains("/api/v1/client/heartbeat") -> {
                            heartbeatCalls++
                            output.write("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n".toByteArray())
                            output.flush()
                        }
                        requestLine.contains("/api/v1/tools/execute") -> {
                            executeCalls++
                            if (executeCalls == 1) {
                                output.write(
                                    "HTTP/1.1 503 Service Unavailable\r\nX-LtiRom-Error-Code: CLIENT_HEARTBEAT_REQUIRED\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray(),
                                )
                            } else {
                                output.write(
                                    "HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Type: application/json\r\nContent-Length: ${responseBody.toByteArray().size}\r\n\r\n$responseBody".toByteArray(),
                                )
                            }
                            output.flush()
                        }
                        else -> {
                            output.write("HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray())
                            output.flush()
                        }
                    }
                }

                val client = createHttpClient()
                try {
                    val adapter =
                        KtorRemoteTransportAdapter(
                            supervisor =
                            TestSupervisor(
                                ServerConnectionDescriptor("127.0.0.1", server.port, "token", 123L, "Ubuntu"),
                            ),
                            httpClient = client,
                            json = json,
                        )

                    val req =
                        ToolExecutionRequest(toolId = "echo", arguments = listOf("hello"), workingDirectory = "/home")
                    val response = adapter.execute(req)
                    assertEquals("success", response.stdout)
                    assertEquals(2, executeCalls, "Command should have been executed once, rejected, and retried once")
                    assertEquals(1, heartbeatCalls, "Heartbeat should have been sent to re-establish admission")
                    assertEquals(CircuitState.CLOSED, adapter.circuitBreaker.getState())
                } finally {
                    client.close()
                }
            }
        }

    @Test
    fun `generic 503 without error code throws ToolingException, does not replay, and trips circuit breaker`() =
        runTest {
            SimpleMockServer().use { server ->
                var executeCalls = 0

                server.handler = { _, requestLine, _, output ->
                    if (requestLine.contains("/api/v1/tools/execute")) {
                        executeCalls++
                        output.write("HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\n\r\n".toByteArray())
                        output.flush()
                    } else {
                        output.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".toByteArray())
                        output.flush()
                    }
                }

                val client = createHttpClient()
                try {
                    val adapter =
                        KtorRemoteTransportAdapter(
                            supervisor =
                            TestSupervisor(
                                ServerConnectionDescriptor("127.0.0.1", server.port, "token", 123L, "Ubuntu"),
                            ),
                            httpClient = client,
                            json = json,
                        )

                    val req =
                        ToolExecutionRequest(toolId = "echo", arguments = listOf("hello"), workingDirectory = "/home")

                    // 3 failures to trip the circuit breaker
                    repeat(3) {
                        assertFailsWith<ToolingException> {
                            adapter.execute(req)
                        }
                    }

                    assertEquals(3, executeCalls, "Generic 503 must not automatically replay commands")
                    assertEquals(
                        CircuitState.OPEN,
                        adapter.circuitBreaker.getState(),
                        "Circuit breaker must be OPEN after 3 failures",
                    )
                } finally {
                    client.close()
                }
            }
        }

    @Test
    fun `cancellation does not increment circuit breaker failures`() = runTest {
        SimpleMockServer().use { server ->
            server.handler = { _, _, _, _ ->
                Thread.sleep(2000)
            }

            val client = createHttpClient()
            try {
                val adapter =
                    KtorRemoteTransportAdapter(
                        supervisor =
                        TestSupervisor(
                            ServerConnectionDescriptor("127.0.0.1", server.port, "token", 123L, "Ubuntu"),
                        ),
                        httpClient = client,
                        json = json,
                    )

                val req =
                    ToolExecutionRequest(toolId = "echo", arguments = listOf("hello"), workingDirectory = "/home")

                assertFailsWith<io.ltirom.tooling.core.RemoteCancellationUnconfirmedException> {
                    withTimeout(100) {
                        adapter.execute(req)
                    }
                }

                assertEquals(
                    CircuitState.CLOSED,
                    adapter.circuitBreaker.getState(),
                    "Cancellation must not trip circuit breaker",
                )
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `retry failure with HTTP 500 throws ToolingException and trips circuit breaker`() = runTest {
        SimpleMockServer().use { server ->
            server.handler = { _, requestLine, _, output ->
                when {
                    requestLine.contains("/api/v1/client/heartbeat") -> {
                        output.write("HTTP/1.1 204 No Content\r\n\r\n".toByteArray())
                        output.flush()
                    }
                    requestLine.contains("/api/v1/tools/execute") -> {
                        // First returns 503 CLIENT_HEARTBEAT_REQUIRED, retry returns 500 Internal Server Error
                        output.write(
                            "HTTP/1.1 500 Internal Server Error\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray(),
                        )
                        output.flush()
                    }
                    else -> {
                        output.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".toByteArray())
                        output.flush()
                    }
                }
            }

            val client = createHttpClient()
            try {
                val adapter =
                    KtorRemoteTransportAdapter(
                        supervisor =
                        TestSupervisor(
                            ServerConnectionDescriptor("127.0.0.1", server.port, "token", 123L, "Ubuntu"),
                        ),
                        httpClient = client,
                        json = json,
                    )

                val req =
                    ToolExecutionRequest(toolId = "echo", arguments = listOf("hello"), workingDirectory = "/home")

                repeat(3) {
                    assertFailsWith<ToolingException> {
                        adapter.execute(req)
                    }
                }

                assertEquals(
                    CircuitState.OPEN,
                    adapter.circuitBreaker.getState(),
                    "HTTP 500 retry failure must trip breaker",
                )
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `requests the service refuses as invalid or conflicting do not open the circuit`() = runTest {
        SimpleMockServer().use { server ->
            server.handler = { _, requestLine, _, output ->
                val status = if (requestLine.contains("/api/v1/tools/execute")) "409 Conflict" else "200 OK"
                output.write("HTTP/1.1 $status\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray())
                output.flush()
            }
            val client = createHttpClient()
            try {
                val adapter =
                    KtorRemoteTransportAdapter(
                        supervisor = TestSupervisor(
                            ServerConnectionDescriptor("127.0.0.1", server.port, "t", 1L, "U"),
                        ),
                        httpClient = client,
                        json = json,
                    )
                val req = ToolExecutionRequest(toolId = "echo", arguments = emptyList(), workingDirectory = "/home")

                repeat(6) { assertFailsWith<ToolingException> { adapter.execute(req) } }

                assertEquals(
                    CircuitState.CLOSED,
                    adapter.circuitBreaker.getState(),
                    "a business refusal says nothing about the service's health",
                )
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `unconfirmed stopClient propagates unconfirmed execution IDs`() = runTest {
        SimpleMockServer().use { server ->
            val stopBody =
                json.encodeToString(
                    io.ltirom.tooling.core.ports.StopClientResult(
                        stopped = false,
                        unconfirmedExecutionIds = listOf("run-1", "run-2"),
                    ),
                )
            server.handler = { _, requestLine, _, output ->
                if (requestLine.contains("/api/v1/client/stop")) {
                    output.write(
                        "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${stopBody.toByteArray().size}\r\n\r\n$stopBody".toByteArray(),
                    )
                    output.flush()
                } else {
                    output.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".toByteArray())
                    output.flush()
                }
            }

            val client = createHttpClient()
            try {
                val adapter =
                    KtorRemoteTransportAdapter(
                        supervisor =
                        TestSupervisor(
                            ServerConnectionDescriptor("127.0.0.1", server.port, "token", 123L, "Ubuntu"),
                        ),
                        httpClient = client,
                        json = json,
                    )

                val result = adapter.stopClient()
                assertFalse(result.stopped)
                assertEquals(listOf("run-1", "run-2"), result.unconfirmedExecutionIds)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `legacy cancel decodes dispatch success rather than HTTP 200`() = runTest {
        SimpleMockServer().use { server ->
            var body = """{"success":false,"executionId":"exec-1","message":"not found"}"""
            server.handler = { _, _, _, output ->
                output.write("HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\n\r\n$body".toByteArray())
                output.flush()
            }
            createHttpClient().use { client ->
                val adapter =
                    KtorRemoteTransportAdapter(
                        TestSupervisor(ServerConnectionDescriptor("127.0.0.1", server.port, "token", 1L, "Ubuntu")),
                        client,
                    )
                assertFalse(adapter.cancel("exec-1", "SIGTERM"))
                body = """{"success":true,"executionId":"exec-1","message":"Signal dispatched"}"""
                assertTrue(adapter.cancel("exec-1", "SIGTERM"))
                body = """{"success":true,"executionId":"different-execution"}"""
                assertFalse(adapter.cancel("exec-1", "SIGTERM"))
                body = "{}"
                assertFalse(adapter.cancel("exec-1", "SIGTERM"))
            }
        }
    }

    @Test
    fun `execute cancellation sends trace ID and owner token to accepting service after supervisor switches`() =
        runTest {
            // Real IO dispatcher keeps the cleanup deadline real while CIO serves these sockets.
            withContext(Dispatchers.IO) {
                SimpleMockServer().use { owner ->
                    SimpleMockServer().use { replacement ->
                        val accepted = CompletableDeferred<String>()
                        val cancelled = CompletableDeferred<String>()
                        owner.handler = { _, requestLine, input, output ->
                            val headers =
                                input.bufferedReader().let { reader ->
                                    buildList {
                                        while (true) {
                                            val line = reader.readLine() ?: break
                                            if (line.isEmpty()) break
                                            add(line)
                                        }
                                    }
                                }
                            if (requestLine.contains("/cancel")) {
                                cancelled.complete(headers.joinToString("\n"))
                                val id = requestLine.substringAfter("/execute/").substringBefore("/cancel")
                                val body = """{"success":true,"executionId":"$id","message":"termination confirmed"}"""
                                output.write("HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\n\r\n$body".toByteArray())
                                output.flush()
                            } else {
                                accepted.complete(
                                    headers.first {
                                        it.startsWith("X-Trace-Id:", ignoreCase = true)
                                    }.substringAfter(":").trim(),
                                )
                                Thread.sleep(1000)
                            }
                        }
                        val first = ServerConnectionDescriptor("127.0.0.1", owner.port, "owner-token", 1L, "Ubuntu")
                        val second =
                            ServerConnectionDescriptor("127.0.0.1", replacement.port, "new-token", 2L, "Ubuntu")
                        var current = first
                        val supervisor =
                            object : DaemonSupervisorPort {
                                override suspend fun ensureStarted() = current

                                override suspend fun getConnectionInfo() = current

                                override suspend fun isHealthy(info: ServerConnectionDescriptor) = true

                                override suspend fun shutdownDaemon() = true

                                override fun close() {}
                            }
                        createHttpClient().use { client ->
                            val adapter = KtorRemoteTransportAdapter(supervisor, client)
                            val execution = launch { adapter.execute(ToolExecutionRequest("echo", emptyList())) }
                            val traceId = withTimeout(5000) { accepted.await() }
                            current = second
                            execution.cancelAndJoin()
                            val cancelHeaders = withTimeout(5000) { cancelled.await() }
                            assertTrue(cancelHeaders.contains("Bearer owner-token"))
                            assertTrue(
                                owner.receivedRequests.any { it.contains("/execute/$traceId/cancel?signal=SIGTERM") },
                            )
                            assertTrue(replacement.receivedRequests.isEmpty())
                            assertTrue(execution.isCancelled)
                            assertEquals(CircuitState.CLOSED, adapter.circuitBreaker.getState())
                        }
                    }
                }
            }
        }

    @Test
    fun `stream terminating without finished event throws ToolingException`() = runTest {
        SimpleMockServer().use { server ->
            // Socket closes without sending ExecutionFinished frame
            server.handler = { socket, _, _, _ ->
                socket.close()
            }

            val client = createHttpClient()
            try {
                val adapter =
                    KtorRemoteTransportAdapter(
                        supervisor =
                        TestSupervisor(
                            ServerConnectionDescriptor("127.0.0.1", server.port, "token", 123L, "Ubuntu"),
                        ),
                        httpClient = client,
                        json = json,
                    )

                val req =
                    ToolExecutionRequest(toolId = "echo", arguments = listOf("hello"), workingDirectory = "/home")

                assertFailsWith<ToolingException> {
                    adapter.stream(req).collect {}
                }
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `stream retry on heartbeat required retains original traceId from environment`() = runTest {
        SimpleMockServer().use { server ->
            val closeFrameMessage = "CLIENT_HEARTBEAT_REQUIRED"
            val messageBytes = closeFrameMessage.toByteArray(Charsets.UTF_8)
            val closePayload = ByteArray(2 + messageBytes.size)
            closePayload[0] = 0x03.toByte()
            closePayload[1] = 0xE8.toByte()
            System.arraycopy(messageBytes, 0, closePayload, 2, messageBytes.size)
            val closeFrame = byteArrayOf(0x88.toByte(), closePayload.size.toByte()) + closePayload

            var streamAttempt = 0
            server.handler = { socket, requestLine, input, output ->
                when {
                    requestLine.contains("/api/v1/client/heartbeat") -> {
                        output.write("HTTP/1.1 204 No Content\r\n\r\n".toByteArray())
                        output.flush()
                    }
                    requestLine.contains("/api/v1/tools/stream") -> {
                        streamAttempt++
                        val reader = input.bufferedReader()
                        val headers = mutableListOf<String>()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            headers.add(line)
                        }
                        val key =
                            headers.firstOrNull { it.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }
                                ?.substringAfter(":")?.trim().orEmpty()
                        val md = java.security.MessageDigest.getInstance("SHA-1")
                        val accept =
                            java.util.Base64.getEncoder().encodeToString(
                                md.digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.UTF_8)),
                            )
                        output.write(
                            (
                                "HTTP/1.1 101 Switching Protocols\r\n" +
                                    "Upgrade: websocket\r\n" +
                                    "Connection: Upgrade\r\n" +
                                    "Sec-WebSocket-Accept: $accept\r\n\r\n"
                                ).toByteArray(Charsets.UTF_8),
                        )
                        output.flush()

                        if (streamAttempt == 1) {
                            Thread.sleep(50)
                            while (input.available() > 0) {
                                input.read()
                            }
                            output.write(closeFrame)
                            output.flush()
                            Thread.sleep(50)
                            socket.close()
                        } else {
                            // On 2nd attempt, close cleanly
                            socket.close()
                        }
                    }
                    else -> {
                        output.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".toByteArray())
                        output.flush()
                    }
                }
            }

            val client = createHttpClient()
            try {
                val adapter =
                    KtorRemoteTransportAdapter(
                        supervisor =
                        TestSupervisor(
                            ServerConnectionDescriptor("127.0.0.1", server.port, "token", 123L, "Ubuntu"),
                        ),
                        httpClient = client,
                        json = json,
                    )

                val req =
                    ToolExecutionRequest(
                        toolId = "echo",
                        arguments = listOf("hello"),
                        workingDirectory = "/home",
                        environment = mapOf("LTI_TRACE_ID" to "custom-trace-id-123"),
                    )

                val failure =
                    runCatching {
                        adapter.stream(req).collect {}
                    }.exceptionOrNull()

                val streamRequests = server.receivedRequests.filter { it.contains("/api/v1/tools/stream") }
                assertTrue(
                    streamRequests.size >= 2,
                    "Expected at least 2 stream connection attempts, got: ${server.receivedRequests}, failure=$failure",
                )
                val retryRequest = streamRequests[1]
                assertTrue(
                    retryRequest.contains("traceId=custom-trace-id-123"),
                    "Second stream attempt must retain original traceId from environment, got: $retryRequest",
                )
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `streamSequenced retry on heartbeat required retains original traceId from environment`() = runTest {
        SimpleMockServer().use { server ->
            val closeFrameMessage = "CLIENT_HEARTBEAT_REQUIRED"
            val messageBytes = closeFrameMessage.toByteArray(Charsets.UTF_8)
            val closePayload = ByteArray(2 + messageBytes.size)
            closePayload[0] = 0x03.toByte()
            closePayload[1] = 0xE8.toByte()
            System.arraycopy(messageBytes, 0, closePayload, 2, messageBytes.size)
            val closeFrame = byteArrayOf(0x88.toByte(), closePayload.size.toByte()) + closePayload

            var streamAttempt = 0
            server.handler = { socket, requestLine, input, output ->
                when {
                    requestLine.contains("/api/v1/client/heartbeat") -> {
                        output.write("HTTP/1.1 204 No Content\r\n\r\n".toByteArray())
                        output.flush()
                    }
                    requestLine.contains("/api/v1/tools/stream") -> {
                        streamAttempt++
                        val reader = input.bufferedReader()
                        val headers = mutableListOf<String>()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            headers.add(line)
                        }
                        val key =
                            headers.firstOrNull { it.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }
                                ?.substringAfter(":")?.trim().orEmpty()
                        val md = java.security.MessageDigest.getInstance("SHA-1")
                        val accept =
                            java.util.Base64.getEncoder().encodeToString(
                                md.digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.UTF_8)),
                            )
                        output.write(
                            (
                                "HTTP/1.1 101 Switching Protocols\r\n" +
                                    "Upgrade: websocket\r\n" +
                                    "Connection: Upgrade\r\n" +
                                    "Sec-WebSocket-Accept: $accept\r\n\r\n"
                                ).toByteArray(Charsets.UTF_8),
                        )
                        output.flush()

                        if (streamAttempt == 1) {
                            Thread.sleep(50)
                            while (input.available() > 0) {
                                input.read()
                            }
                            output.write(closeFrame)
                            output.flush()
                            Thread.sleep(50)
                            socket.close()
                        } else {
                            socket.close()
                        }
                    }
                    else -> {
                        output.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".toByteArray())
                        output.flush()
                    }
                }
            }

            val client = createHttpClient()
            try {
                val adapter =
                    KtorRemoteTransportAdapter(
                        supervisor =
                        TestSupervisor(
                            ServerConnectionDescriptor("127.0.0.1", server.port, "token", 123L, "Ubuntu"),
                        ),
                        httpClient = client,
                        json = json,
                    )

                val req =
                    ToolExecutionRequest(
                        toolId = "echo",
                        arguments = listOf("hello"),
                        workingDirectory = "/home",
                        environment = mapOf("LTI_TRACE_ID" to "custom-trace-id-123"),
                    )

                runCatching {
                    adapter.streamSequenced(req).collect {}
                }

                val streamRequests = server.receivedRequests.filter { it.contains("/api/v1/tools/stream") }
                assertTrue(streamRequests.size >= 2, "Expected at least 2 stream connection attempts")
                val retryRequest = streamRequests[1]
                assertTrue(
                    retryRequest.contains("traceId=custom-trace-id-123"),
                    "Second streamSequenced attempt must retain original traceId from environment, got: $retryRequest",
                )
            } finally {
                client.close()
            }
        }
    }
}
