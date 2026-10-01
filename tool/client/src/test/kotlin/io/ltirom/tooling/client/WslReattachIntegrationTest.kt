package io.ltirom.tooling.client

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WslReattachIntegrationTest {

    @Test
    fun `ensureStarted reattaches to running WSL server with near zero latency`() = runBlocking {
        assumeTrue("Live WSL tests enabled only when LTI_LIVE_WSL=1", System.getenv("LTI_LIVE_WSL") == "1")
        val manager = WslDaemonManager(WslDaemonConfig(distroName = "Ubuntu"))

        try {
            // First launch or re-attach
            val info1 = manager.ensureStarted()
            assertTrue(info1.port > 0)
            assertTrue(info1.token.isNotBlank())

            // Second call: must re-attach instantly to the running daemon without spawning a new one
            val duration = measureTimeMillis {
                val info2 = manager.ensureStarted()
                assertEquals(info1.port, info2.port)
                assertEquals(info1.token, info2.token)
            }

            println("Re-attachment took $duration ms")
            assertTrue(duration < 2500, "In-memory / lockfile re-attachment must take <2500ms, took $duration ms")

            val health = manager.getServerInfo()
            assertEquals("UP", health?.status)
        } finally {
            manager.shutdownDaemon()
        }
    }
}
