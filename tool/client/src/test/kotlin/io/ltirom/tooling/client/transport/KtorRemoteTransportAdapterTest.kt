package io.ltirom.tooling.client.transport

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.ToolStatusInfo
import io.ltirom.tooling.core.remote.WslServerInfo
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

    private fun createHttpClient(): HttpClient {
        return HttpClient(CIO) {
            install(ContentNegotiation) {
                json(this@KtorRemoteTransportAdapterTest.json)
            }
        }
    }

    @Test
    fun `listTools returns Tools on 200 OK`() = runTest {
        SimpleMockServer().use { server ->
            val expectedTools = listOf(
                ToolStatusInfo(tool = "adb", installed = true, path = "/usr/bin/adb", source = "PINNED"),
                ToolStatusInfo(tool = "erofsfuse", installed = true, path = "/tools/erofsfuse", source = "DYNAMIC")
            )
            val jsonBody = json.encodeToString(expectedTools)

            server.handler = { _, _, _, output ->
                output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${jsonBody.toByteArray().size}\r\n\r\n$jsonBody".toByteArray())
                output.flush()
            }

            val client = createHttpClient()
            try {
                val adapter = KtorRemoteTransportAdapter(
                    supervisor = TestSupervisor(ServerConnectionDescriptor("127.0.0.1", server.port, "token", 123L, "Ubuntu")),
                    httpClient = client,
                    json = json
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
                val adapter = KtorRemoteTransportAdapter(
                    supervisor = TestSupervisor(ServerConnectionDescriptor("127.0.0.1", server.port, "token", 123L, "Ubuntu")),
                    httpClient = client,
                    json = json
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
    fun `listTools returns ServiceError on IO exception`() = runTest {
        SimpleMockServer().use { server ->
            // Immediately close the socket when request arrives
            server.handler = { socket, _, _, _ ->
                socket.close()
            }

            val client = createHttpClient()
            try {
                val adapter = KtorRemoteTransportAdapter(
                    supervisor = TestSupervisor(ServerConnectionDescriptor("127.0.0.1", server.port, "token", 123L, "Ubuntu")),
                    httpClient = client,
                    json = json
                )

                val result = adapter.listTools()
                assertIs<ToolListResult.ServiceError>(result)
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
                val adapter = KtorRemoteTransportAdapter(
                    supervisor = TestSupervisor(ServerConnectionDescriptor("127.0.0.1", server.port, "token", 123L, "Ubuntu")),
                    httpClient = client,
                    json = json
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
                val adapter = KtorRemoteTransportAdapter(
                    supervisor = TestSupervisor(ServerConnectionDescriptor("127.0.0.1", server.port, "token", 123L, "Ubuntu")),
                    httpClient = client,
                    json = json
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
}
