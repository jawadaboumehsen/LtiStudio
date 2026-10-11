package io.ltirom.tooling.client.wsl

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The shutdown POST runs against a real socket: the client has no default timeout, so each call sets its own. */
class ShutdownRequestTimeoutTest {
    /** Accepts one request and answers 200 after [replyAfterMs]; a negative delay never answers. */
    private fun service(replyAfterMs: Long): Pair<ServerSocket, ServerConnectionDescriptor> {
        val socket = ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            runCatching {
                socket.accept().use { c ->
                    c.getInputStream().read(ByteArray(4096))
                    if (replyAfterMs < 0) Thread.sleep(60_000)
                    Thread.sleep(replyAfterMs)
                    c.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                }
            }
        }
        return socket to ServerConnectionDescriptor("127.0.0.1", socket.localPort, "tok", 1L, "Ubuntu")
    }

    /** Sends the status line and half a body, then goes silent: the headers are in, the body read would hang. */
    private fun stallingBodyService(): Pair<ServerSocket, ServerConnectionDescriptor> {
        val socket = ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            runCatching {
                socket.accept().use { c ->
                    c.getInputStream().read(ByteArray(4096))
                    c.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 500\r\n\r\n{\"sta".toByteArray())
                    c.getOutputStream().flush()
                    Thread.sleep(60_000)
                }
            }
        }
        return socket to ServerConnectionDescriptor("127.0.0.1", socket.localPort, "tok", 1L, "Ubuntu")
    }

    @Test
    fun `server info cannot stall on a service that stops after the headers`() = runBlocking {
        val (socket, info) = stallingBodyService()
        socket.use {
            val start = System.nanoTime()
            val serverInfo = DaemonHealthProber(HttpClient(CIO)).getServerInfo(info)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            assertEquals(null, serverInfo)
            assertEquals(true, elapsedMs <= DaemonHealthProber.TELEMETRY_TIMEOUT_MS + 2_000L, "took $elapsedMs ms")
        }
    }

    @Test
    fun `server info cannot stall on a service that never answers`() = runBlocking {
        val (socket, info) = service(replyAfterMs = -1)
        socket.use {
            val start = System.nanoTime()
            assertEquals(null, DaemonHealthProber(HttpClient(CIO)).getServerInfo(info))
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            assertEquals(true, elapsedMs <= DaemonHealthProber.TELEMETRY_TIMEOUT_MS + 2_000L, "took $elapsedMs ms")
        }
    }

    @Test
    fun `cancelling the caller is not swallowed by the telemetry bound`() = runBlocking<Unit> {
        val (socket, info) = service(replyAfterMs = -1)
        socket.use {
            val job = async { DaemonHealthProber(HttpClient(CIO)).getServerInfo(info) }
            kotlinx.coroutines.delay(300)
            job.cancel()
            assertFailsWith<kotlinx.coroutines.CancellationException> { job.await() }
        }
    }

    @Test
    fun `a forced shutdown outlasts the service's seven second cancellation`() = runBlocking {
        val budgetMs = 7_000L // the service's SHUTDOWN_STOP_WAIT_MS
        assertTrue(DaemonHealthProber.FORCED_SHUTDOWN_TIMEOUT_MS >= budgetMs + 1_000L, "needs margin over the service")
        assertTrue(DaemonHealthProber.FORCED_SHUTDOWN_TIMEOUT_MS < 10_000L, "must fit the app's shutdown deadline")

        val (socket, info) = service(replyAfterMs = budgetMs)
        socket.use {
            val prober = DaemonHealthProber(HttpClient(CIO))
            assertEquals(ShutdownOutcome.ACCEPTED, prober.requestShutdown(info, force = true), "a reply at ${budgetMs}ms is still accepted")
        }
    }

    @Test
    fun `a service that never answers cannot hold a shutdown past its bound`() = runBlocking {
        val (socket, info) = service(replyAfterMs = -1)
        socket.use {
            val start = System.nanoTime()
            val accepted = DaemonHealthProber(HttpClient(CIO)).requestShutdown(info, force = false)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            assertEquals(ShutdownOutcome.NO_ANSWER, accepted)
            assertEquals(true, elapsedMs in DaemonHealthProber.SHUTDOWN_TIMEOUT_MS..DaemonHealthProber.SHUTDOWN_TIMEOUT_MS + 2_000L, "took $elapsedMs ms")
        }
    }
}
