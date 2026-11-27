package io.ltirom.tooling.client.wsl

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WslDaemonDecompositionTest {

    @Test
    fun `DaemonHealthProbe returns false when server is unreachable`() = runBlocking {
        val client = HttpClient(CIO)
        val probe = DaemonHealthProbe(client)
        val info = ServerConnectionDescriptor(
            host = "127.0.0.1",
            port = 59999,
            token = "fake-token",
            pid = 1234L,
            distro = "Ubuntu"
        )

        try {
            val isHealthy = probe.isHealthy(info, timeoutMs = 500L)
            assertFalse(isHealthy)
        } finally {
            client.close()
        }
    }

    @Test
    fun `WslProcessLauncher initializes with configured startup timeout`() {
        val launcher = WslProcessLauncher(startupTimeoutMs = 15_000L)
        assertEquals(15_000L, launcher.startupTimeoutMs)
    }
}

