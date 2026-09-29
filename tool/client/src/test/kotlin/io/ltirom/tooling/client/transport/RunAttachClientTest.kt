package io.ltirom.tooling.client.transport

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import io.ltirom.tooling.client.WslDaemonConfig
import io.ltirom.tooling.client.WslDaemonManager
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import io.ltirom.tooling.core.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

class RunAttachClientTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private class TestSupervisor(
        private val descriptor: ServerConnectionDescriptor
    ) : DaemonSupervisorPort {
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
        val connectionPaths = CopyOnWriteArrayList<String>()

        @Volatile
        var handler: ((Socket, String, InputStream, OutputStream) -> Unit)? = null

        @Volatile
        private var running = true

        private val thread = Thread {
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
        }.apply { isDaemon = true; start() }

        private fun handleSocket(socket: Socket) {
            socket.use {
                try {
                    val input = socket.getInputStream()
                    val output = socket.getOutputStream()
                    val requestLine = readLine(input) ?: return
                    receivedRequests.add(requestLine)

                    val custom = handler
                    if (custom != null) {
                        custom(socket, requestLine, input, output)
                        return
                    }

                    // Default WebSocket handshake or HTTP response
                    if (requestLine.startsWith("GET ")) {
                        val path = requestLine.split(" ")[1]
                        connectionPaths.add(path)
                        val headers = readHeaders(input)
                        val wsKey = headers["sec-websocket-key"]
                        if (wsKey != null) {
                            performWsHandshake(output, wsKey)
                            readIncomingFrames(socket, input, output)
                        } else {
                            val body = "{}"
                            output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\n\r\n$body".toByteArray())
                            output.flush()
                        }
                    } else if (requestLine.startsWith("POST ")) {
                        val body = "{}"
                        output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\n\r\n$body".toByteArray())
                        output.flush()
                    }
                } catch (_: Exception) {
                }
            }
        }

        fun performWsHandshake(output: OutputStream, key: String) {
            val acceptKey = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-1").digest(
                    (key.trim() + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()
                )
            )
            val response = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: $acceptKey\r\n\r\n"
            output.write(response.toByteArray(Charsets.UTF_8))
            output.flush()
        }

        fun sendWsText(output: OutputStream, text: String) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            output.write(0x81) // FIN + text opcode
            if (bytes.size <= 125) {
                output.write(bytes.size)
            } else if (bytes.size <= 65535) {
                output.write(126)
                output.write((bytes.size shr 8) and 0xFF)
                output.write(bytes.size and 0xFF)
            } else {
                output.write(127)
                for (i in 7 downTo 0) {
                    output.write((bytes.size.toLong() shr (i * 8)).toInt() and 0xFF)
                }
            }
            output.write(bytes)
            output.flush()
        }

        fun readIncomingFrames(socket: Socket, input: InputStream, output: OutputStream) {
            while (!socket.isClosed) {
                val b0 = input.read()
                if (b0 == -1) break
                val b1 = input.read()
                if (b1 == -1) break
                val opcode = b0 and 0x0F
                val masked = (b1 and 0x80) != 0
                var len = (b1 and 0x7F).toLong()
                if (len == 126L) {
                    val high = input.read()
                    val low = input.read()
                    if (high == -1 || low == -1) break
                    len = ((high shl 8) or low).toLong()
                } else if (len == 127L) {
                    var l = 0L
                    for (i in 0..7) {
                        val b = input.read()
                        if (b == -1) break
                        l = (l shl 8) or b.toLong()
                    }
                    len = l
                }
                val mask = ByteArray(4)
                if (masked) {
                    var mRead = 0
                    while (mRead < 4) {
                        val r = input.read(mask, mRead, 4 - mRead)
                        if (r == -1) break
                        mRead += r
                    }
                }
                val payload = ByteArray(len.toInt())
                var totalRead = 0
                while (totalRead < len) {
                    val r = input.read(payload, totalRead, (len - totalRead).toInt())
                    if (r == -1) break
                    totalRead += r
                }
                if (opcode == 0x08) { // Close frame
                    try {
                        output.write(byteArrayOf(0x88.toByte(), 0x00.toByte()))
                        output.flush()
                    } catch (_: Exception) {}
                    break
                }
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

        fun readHeaders(input: InputStream): Map<String, String> {
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    headers[parts[0].trim().lowercase()] = parts[1].trim()
                }
            }
            return headers
        }

        override fun close() {
            running = false
            runCatching { serverSocket.close() }
        }
    }

    private fun createHttpClient(): HttpClient {
        return HttpClient(CIO) {
            install(ContentNegotiation) {
                json(this@RunAttachClientTest.json)
            }
            install(WebSockets)
        }
    }

    @Test
    fun testSequencedStreamReturnsSequencedEvents() = runBlocking {
        SimpleMockServer().use { server ->
            server.handler = { socket, requestLine, input, output ->
                server.connectionPaths.add(requestLine.split(" ")[1])
                val headers = server.readHeaders(input)
                server.performWsHandshake(output, headers["sec-websocket-key"]!!)

                val seqEvent = SequencedStreamEvent(
                    seq = 1L,
                    event = StreamEvent.OutputChunk(text = "Hello sequenced", isError = false)
                )
                server.sendWsText(output, json.encodeToString(seqEvent))

                val finishEvent = SequencedStreamEvent(
                    seq = 2L,
                    event = StreamEvent.ExecutionFinished(exitCode = 0, durationMs = 10L, summary = "Done")
                )
                server.sendWsText(output, json.encodeToString(finishEvent))
                server.readIncomingFrames(socket, input, output)
            }

            val httpClient = createHttpClient()
            try {
                val descriptor = ServerConnectionDescriptor("127.0.0.1", server.port, "tok", 1L, "Ubuntu")
                val adapter = KtorRemoteTransportAdapter(TestSupervisor(descriptor), httpClient, json)

                val events = adapter.streamSequenced(
                    ToolExecutionRequest(toolId = "test", arguments = emptyList())
                ).toList()

                assertEquals(2, events.size)
                assertEquals(1L, events[0].seq)
                assertEquals("Hello sequenced", (events[0].event as StreamEvent.OutputChunk).text)
                assertEquals(2L, events[1].seq)
                assertTrue(events[1].event is StreamEvent.ExecutionFinished)
                assertTrue(server.connectionPaths.any { it.contains("sequenced=true") }, "Must include sequenced=true query param")
            } finally {
                httpClient.close()
            }
        }
    }

    @Test
    fun testAttachRunDeduplicatesLowerOrEqualSeq() = runBlocking {
        SimpleMockServer().use { server ->
            server.handler = { socket, requestLine, input, output ->
                server.connectionPaths.add(requestLine.split(" ")[1])
                val headers = server.readHeaders(input)
                server.performWsHandshake(output, headers["sec-websocket-key"]!!)

                val events = listOf(
                    SequencedStreamEvent(1L, StreamEvent.OutputChunk("frame-1", false)),
                    SequencedStreamEvent(2L, StreamEvent.OutputChunk("frame-2", false)),
                    SequencedStreamEvent(2L, StreamEvent.OutputChunk("duplicate-2", false)),
                    SequencedStreamEvent(1L, StreamEvent.OutputChunk("duplicate-1", false)),
                    SequencedStreamEvent(3L, StreamEvent.OutputChunk("frame-3", false)),
                    SequencedStreamEvent(4L, StreamEvent.ExecutionFinished(0, 10L, "Done"))
                )
                for (event in events) {
                    server.sendWsText(output, json.encodeToString(event))
                }
                server.readIncomingFrames(socket, input, output)
            }

            val httpClient = createHttpClient()
            try {
                val descriptor = ServerConnectionDescriptor("127.0.0.1", server.port, "tok", 1L, "Ubuntu")
                val adapter = KtorRemoteTransportAdapter(TestSupervisor(descriptor), httpClient, json)

                val emitted = adapter.attachRun("run-abc", fromSeq = 1L).toList()
                assertEquals(4, emitted.size)
                assertEquals(listOf(1L, 2L, 3L, 4L), emitted.map { it.seq })
                assertEquals("frame-1", (emitted[0].event as StreamEvent.OutputChunk).text)
                assertEquals("frame-2", (emitted[1].event as StreamEvent.OutputChunk).text)
                assertEquals("frame-3", (emitted[2].event as StreamEvent.OutputChunk).text)
            } finally {
                httpClient.close()
            }
        }
    }

    @Test
    fun testAttachRunReconnectsAfterMissedHeartbeats() = runBlocking {
        SimpleMockServer().use { server ->
            var connectionCount = 0
            server.handler = { socket, requestLine, input, output ->
                val path = requestLine.split(" ")[1]
                server.connectionPaths.add(path)
                val headers = server.readHeaders(input)
                server.performWsHandshake(output, headers["sec-websocket-key"]!!)
                val connIdx = ++connectionCount

                if (connIdx == 1) {
                    server.sendWsText(output, json.encodeToString(SequencedStreamEvent(1L, StreamEvent.OutputChunk("event-1", false))))
                    server.readIncomingFrames(socket, input, output)
                } else {
                    server.sendWsText(output, json.encodeToString(SequencedStreamEvent(2L, StreamEvent.OutputChunk("event-2", false))))
                    server.sendWsText(output, json.encodeToString(SequencedStreamEvent(3L, StreamEvent.ExecutionFinished(0, 5L, "Done"))))
                    server.readIncomingFrames(socket, input, output)
                }
            }

            val httpClient = createHttpClient()
            try {
                val descriptor = ServerConnectionDescriptor("127.0.0.1", server.port, "tok", 1L, "Ubuntu")
                val adapter = KtorRemoteTransportAdapter(TestSupervisor(descriptor), httpClient, json)

                val emitted = adapter.attachRun("run-reconnect", fromSeq = 1L, heartbeatTimeoutMs = 200L).toList()

                assertEquals(3, emitted.size)
                assertEquals(listOf(1L, 2L, 3L), emitted.map { it.seq })
                assertTrue(server.connectionPaths.size >= 2, "Expected at least 2 connections (initial + reconnect)")
                assertTrue(server.connectionPaths.any { it.contains("fromSeq=2") }, "Reconnection must specify fromSeq=2: ${server.connectionPaths}")
            } finally {
                httpClient.close()
            }
        }
    }

    @Test
    fun testNoCancelOnStreamEnd() = runBlocking {
        SimpleMockServer().use { server ->
            server.handler = { socket, requestLine, input, output ->
                server.connectionPaths.add(requestLine.split(" ")[1])
                val headers = server.readHeaders(input)
                server.performWsHandshake(output, headers["sec-websocket-key"]!!)
                server.sendWsText(output, json.encodeToString<StreamEvent>(StreamEvent.OutputChunk("live", false)))
                server.sendWsText(output, json.encodeToString<StreamEvent>(StreamEvent.ExecutionFinished(0, 5L, "Done")))
                server.readIncomingFrames(socket, input, output)
            }

            val httpClient = createHttpClient()
            try {
                val descriptor = ServerConnectionDescriptor("127.0.0.1", server.port, "tok", 1L, "Ubuntu")
                val adapter = KtorRemoteTransportAdapter(TestSupervisor(descriptor), httpClient, json)

                // Collect the stream to completion
                val events = adapter.stream(ToolExecutionRequest(toolId = "test", arguments = emptyList())).toList()
                assertEquals(2, events.size)

                delay(100)
                // Assert no cancel endpoint was ever called (regression test for deleted finally { cancel })
                val cancelCalls = server.receivedRequests.filter { it.contains("/cancel") }
                assertTrue(cancelCalls.isEmpty(), "Stream termination must not send cancel request to server: $cancelCalls")
            } finally {
                httpClient.close()
            }
        }
    }

    @Test
    fun testWslDaemonManagerCloseDoesNotPostShutdown() = runBlocking {
        SimpleMockServer().use { server ->
            val descriptor = ServerConnectionDescriptor("127.0.0.1", server.port, "tok", 1L, "Ubuntu")
            val manager = WslDaemonManager(
                config = WslDaemonConfig(autoStart = false)
            )
            // Set connectionInfo to point to mock server
            val connField = WslDaemonManager::class.java.getDeclaredField("connectionInfo")
            connField.isAccessible = true
            connField.set(manager, descriptor)

            // When close() is called, verify it does NOT invoke POST /system/shutdown
            manager.close()

            delay(100)
            val shutdownCalls = server.receivedRequests.filter { it.contains("/system/shutdown") }
            assertTrue(shutdownCalls.isEmpty(), "WslDaemonManager.close() must not issue POST /shutdown: $shutdownCalls")
        }
    }

    @Test
    fun testCollectorFailureIsRethrownNotTreatedAsADroppedConnection() = runBlocking {
        SimpleMockServer().use { server ->
            server.handler = { socket, requestLine, input, output ->
                server.connectionPaths.add(requestLine.split(" ")[1])
                val headers = server.readHeaders(input)
                server.performWsHandshake(output, headers["sec-websocket-key"]!!)
                server.sendWsText(output, json.encodeToString(SequencedStreamEvent(1L, StreamEvent.OutputChunk("line", false))))
                server.sendWsText(output, json.encodeToString(SequencedStreamEvent(2L, StreamEvent.ExecutionFinished(0, 1L, ""))))
                server.readIncomingFrames(socket, input, output)
            }
            val httpClient = createHttpClient()
            try {
                val descriptor = ServerConnectionDescriptor("127.0.0.1", server.port, "tok", 1L, "Ubuntu")
                val adapter = KtorRemoteTransportAdapter(TestSupervisor(descriptor), httpClient, json)

                // Before: the failure looked like a dropped socket; the client reconnected past the event
                // (cursor advanced first) and at the end waited forever for events after the last one.
                val error = assertFailsWith<IllegalStateException> {
                    withTimeout(10_000) {
                        adapter.attachRun("run-x", fromSeq = 1L).collect { error("journal commit failed") }
                    }
                }
                assertEquals("journal commit failed", error.message)
                assertEquals(1, server.connectionPaths.size, "no reconnect for a collector failure")
            } finally {
                httpClient.close()
            }
        }
    }
}
