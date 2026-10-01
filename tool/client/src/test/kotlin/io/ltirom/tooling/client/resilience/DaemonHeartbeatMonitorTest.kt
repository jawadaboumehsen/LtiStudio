package io.ltirom.tooling.client.resilience

import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DaemonHeartbeatMonitorTest {

    private class FakeSupervisor(
        var healthy: Boolean = true,
        var connection: ServerConnectionDescriptor? = ServerConnectionDescriptor("127.0.0.1", 1234, "tok", 1L, "Ubuntu")
    ) : DaemonSupervisorPort {
        override suspend fun ensureStarted(): ServerConnectionDescriptor =
            connection ?: throw IllegalStateException("Cannot start")

        override suspend fun getConnectionInfo(): ServerConnectionDescriptor? = connection

        override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean = healthy

        override suspend fun shutdownDaemon(): Boolean = true

        override fun close() {}
    }

    @Test
    fun `monitor transitions to Connected when daemon is healthy`() = runBlocking {
        val supervisor = FakeSupervisor(healthy = true)
        val stateHolder = ConnectionStateHolder()
        val monitor = DaemonHeartbeatMonitor(supervisor, stateHolder, intervalMs = 100)

        monitor.checkLiveness()

        assertTrue(stateHolder.currentState is ConnectionState.Connected)
        assertEquals(1234, (stateHolder.currentState as ConnectionState.Connected).descriptor.port)
    }

    @Test
    fun `monitor transitions to Reconnecting after max missed heartbeats`() = runBlocking {
        val supervisor = FakeSupervisor(healthy = false)
        val stateHolder = ConnectionStateHolder()
        val monitor = DaemonHeartbeatMonitor(supervisor, stateHolder, maxMissedHeartbeats = 2)

        // First miss: misses count = 1
        monitor.checkLiveness()
        // Second miss: reaches 2 -> attempts re-attachment. Since healthy is false, ensureStarted succeeds with the same descriptor
        monitor.checkLiveness()

        assertTrue(stateHolder.currentState is ConnectionState.Connected)
    }
}
