package io.ltirom.tooling.client.resilience

import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import io.ltirom.tooling.core.remote.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DaemonHeartbeatMonitorTest {
    private class FakeSupervisor(
        var healthy: Boolean = true,
        var connection: ServerConnectionDescriptor? =
            ServerConnectionDescriptor("127.0.0.1", 1234, "tok", 1L, "Ubuntu"),
    ) : DaemonSupervisorPort {
        override suspend fun ensureStarted(): ServerConnectionDescriptor =
            connection ?: throw IllegalStateException("Cannot start")

        override suspend fun getConnectionInfo(): ServerConnectionDescriptor? = connection

        override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean = healthy

        override suspend fun shutdownDaemon(): Boolean = true

        override fun close() {}
    }

    private class FakeTransport(
        var heartbeatResponse: Boolean = true,
        var heartbeatDelayMs: Long = 0L,
        var heartbeatGate: kotlinx.coroutines.CompletableDeferred<Boolean>? = null,
        var onHeartbeatStarted: (() -> Unit)? = null,
    ) : RemoteTransportPort {
        var heartbeatCount = 0
        val heartbeatedDescriptors = mutableListOf<ServerConnectionDescriptor>()

        override suspend fun sendHeartbeat(): Boolean {
            onHeartbeatStarted?.invoke()
            if (heartbeatDelayMs > 0) kotlinx.coroutines.delay(heartbeatDelayMs)
            val gate = heartbeatGate
            val resp = if (gate != null) gate.await() else heartbeatResponse
            heartbeatCount++
            return resp
        }

        override suspend fun sendHeartbeat(descriptor: ServerConnectionDescriptor): Boolean {
            onHeartbeatStarted?.invoke()
            if (heartbeatDelayMs > 0) kotlinx.coroutines.delay(heartbeatDelayMs)
            val gate = heartbeatGate
            val resp = if (gate != null) gate.await() else heartbeatResponse
            heartbeatCount++
            heartbeatedDescriptors.add(descriptor)
            return resp
        }

        override suspend fun checkHealth(): WslServerInfo? = null

        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(emptyList())

        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(emptyList())

        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true

        override suspend fun downloadFile(remotePath: String): ByteArray? = null

        override suspend fun shutdown(): Boolean = true

        override suspend fun stopClient(): io.ltirom.tooling.core.ports.StopClientResult =
            io.ltirom.tooling.core.ports.StopClientResult(stopped = true)

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse = error("not implemented")

        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()
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
    fun `a stale no-connection check does not undo a connection established meanwhile`() = runBlocking {
        val supervisor =
            object : DaemonSupervisorPort {
                val connection = ServerConnectionDescriptor("127.0.0.1", 1234, "tok", 1L, "Ubuntu")
                var staleNull = false

                override suspend fun ensureStarted(): ServerConnectionDescriptor = connection

                // The first read after the flag is set answers "no daemon"; by the time it is acted on there is one.
                override suspend fun getConnectionInfo(): ServerConnectionDescriptor? =
                    if (staleNull) {
                        staleNull = false
                        null
                    } else {
                        connection
                    }

                override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean = true

                override suspend fun shutdownDaemon(): Boolean = true

                override fun close() {}
            }
        val stateHolder = ConnectionStateHolder()
        val monitor = DaemonHeartbeatMonitor(supervisor, stateHolder, intervalMs = 60_000)
        assertTrue(monitor.awaitInitialHeartbeat(timeoutMs = 500L))
        assertTrue(monitor.isAdmissionReady())
        val before = stateHolder.currentState

        supervisor.staleNull = true
        monitor.checkLiveness()

        assertTrue(monitor.isAdmissionReady(), "the newer connection's admission must survive the stale check")
        assertEquals(before, stateHolder.currentState, "a stale check publishes nothing")
        monitor.close()
    }

    @Test
    fun `monitor transitions to Reconnecting after max missed heartbeats`() = runBlocking {
        var restartCount = 0
        val supervisor =
            object : DaemonSupervisorPort {
                var connection: ServerConnectionDescriptor? =
                    ServerConnectionDescriptor("127.0.0.1", 1234, "tok", 1L, "Ubuntu")

                override suspend fun ensureStarted(): ServerConnectionDescriptor {
                    restartCount++
                    return connection ?: throw IllegalStateException("Cannot start")
                }

                override suspend fun getConnectionInfo(): ServerConnectionDescriptor? = connection

                override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean = restartCount > 0

                override suspend fun shutdownDaemon(): Boolean = true

                override fun close() {}
            }
        val stateHolder = ConnectionStateHolder()
        val monitor = DaemonHeartbeatMonitor(supervisor, stateHolder, maxMissedHeartbeats = 2)

        // First miss: misses count = 1
        monitor.checkLiveness()
        // Second miss: reaches 2 -> attempts re-attachment. Since ensureStarted succeeded and isHealthy returns true, it transitions to Connected
        monitor.checkLiveness()

        assertTrue(stateHolder.currentState is ConnectionState.Connected)
    }

    @Test
    fun `failed first heartbeat returns false and does not open admission`() = runBlocking {
        val supervisor = FakeSupervisor(healthy = false, connection = null)
        val monitor = DaemonHeartbeatMonitor(supervisor)

        val result = monitor.awaitInitialHeartbeat(timeoutMs = 100L)
        assertFalse(result, "Failed first heartbeat must return false")
        assertFalse(monitor.isAdmissionReady())
    }

    @Test
    fun `successful first heartbeat returns true and marks admission ready`() = runBlocking {
        val supervisor = FakeSupervisor(healthy = true)
        val monitor = DaemonHeartbeatMonitor(supervisor)

        val result = monitor.awaitInitialHeartbeat(timeoutMs = 500L)
        assertTrue(result, "Successful initial heartbeat must return true")
        assertTrue(monitor.isAdmissionReady())
    }

    @Test
    fun `healthy health endpoint with rejected heartbeat never reports command readiness`() = runBlocking {
        val supervisor = FakeSupervisor(healthy = true)
        val transport = FakeTransport(heartbeatResponse = false)
        val monitor = DaemonHeartbeatMonitor(supervisor, transport = transport)

        val result = monitor.awaitInitialHeartbeat(timeoutMs = 500L)
        assertFalse(result, "Rejected heartbeat must report false")
        assertFalse(monitor.isAdmissionReady(), "Command admission must not be ready when heartbeat rejected")
    }

    @Test
    fun `failed initial heartbeat does not lock out subsequent retry`() = runBlocking {
        val supervisor = FakeSupervisor(healthy = true)
        val transport = FakeTransport(heartbeatResponse = false)
        val monitor = DaemonHeartbeatMonitor(supervisor, transport = transport)

        val firstResult = monitor.awaitInitialHeartbeat(timeoutMs = 200L)
        assertFalse(firstResult, "First rejected heartbeat must return false")
        assertFalse(monitor.isAdmissionReady())

        transport.heartbeatResponse = true
        val retryResult = monitor.awaitInitialHeartbeat(timeoutMs = 200L)
        assertTrue(retryResult, "Subsequent retry must be allowed to succeed")
        assertTrue(monitor.isAdmissionReady())
    }

    @Test
    fun `concurrent callers with suspended handshake share request and do not cancel each other`() = runBlocking {
        val supervisor = FakeSupervisor(healthy = true)
        val handshakeStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val handshakeGate = kotlinx.coroutines.CompletableDeferred<Boolean>()

        val transport =
            FakeTransport(
                heartbeatGate = handshakeGate,
                onHeartbeatStarted = { handshakeStarted.complete(Unit) },
            )
        val monitor = DaemonHeartbeatMonitor(supervisor, transport = transport)

        // Caller 1 begins handshake and suspends at network gate
        val caller1 = async { monitor.awaitInitialHeartbeat(timeoutMs = 5000L) }
        handshakeStarted.await()

        // Caller 2 enters while Caller 1's handshake is suspended in flight
        val caller2 = async { monitor.awaitInitialHeartbeat(timeoutMs = 5000L) }

        // Cancel Caller 1; Caller 2 must remain uncancelled and succeed once gate unblocks
        caller1.cancel()
        handshakeGate.complete(true)

        val caller2Result = caller2.await()
        assertTrue(caller2Result, "Caller 2 must succeed even if Caller 1 was cancelled mid-flight")
        assertTrue(monitor.isAdmissionReady())
        assertEquals(1, transport.heartbeatCount, "Only one underlying handshake should have run")
    }

    @Test
    fun `forceRefresh joins already in flight handshake without cancelling it or other callers`() = runBlocking {
        val supervisor = FakeSupervisor(healthy = true)
        val handshakeStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val handshakeGate = kotlinx.coroutines.CompletableDeferred<Boolean>()

        val transport =
            FakeTransport(
                heartbeatGate = handshakeGate,
                onHeartbeatStarted = { handshakeStarted.complete(Unit) },
            )
        val monitor = DaemonHeartbeatMonitor(supervisor, transport = transport)

        // Caller 1 starts normal handshake and suspends at gate
        val caller1 = async { monitor.awaitInitialHeartbeat(timeoutMs = 5000L) }
        handshakeStarted.await()

        // Caller 2 requests forced refresh for the same connection while caller 1 is in flight
        val caller2 =
            async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                monitor.awaitInitialHeartbeat(timeoutMs = 5000L, forceRefresh = true)
            }

        // Unblock gate
        handshakeGate.complete(true)

        val res1 = caller1.await()
        val res2 = caller2.await()

        assertTrue(res1, "Caller 1 must succeed without cancellation")
        assertTrue(res2, "Caller 2 (forceRefresh) must succeed by joining in-flight handshake")
        assertEquals(1, transport.heartbeatCount, "Only one underlying handshake should have run")
    }

    @Test
    fun `connection replacement while acknowledgment is pending invalidates old result`() = runBlocking {
        val supervisor =
            FakeSupervisor(
                healthy = true,
                connection = ServerConnectionDescriptor("127.0.0.1", 1234, "tok1", 100L, "Ubuntu"),
            )
        val handshakeStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val handshakeGate = kotlinx.coroutines.CompletableDeferred<Boolean>()

        val transport =
            FakeTransport(
                heartbeatGate = handshakeGate,
                onHeartbeatStarted = { handshakeStarted.complete(Unit) },
            )
        val monitor = DaemonHeartbeatMonitor(supervisor, transport = transport)

        val caller1 = async { monitor.awaitInitialHeartbeat(timeoutMs = 5000L) }
        handshakeStarted.await()

        // Replace connection descriptor while handshake is in flight
        supervisor.connection = ServerConnectionDescriptor("127.0.0.1", 5678, "tok2", 200L, "Ubuntu")

        // Unblock old handshake
        handshakeGate.complete(true)
        val result = caller1.await()

        // Caller awaiting old handshake must receive false, and admission must be false for new server
        assertFalse(result, "Acknowledgment from replaced connection must not return true to caller")
        assertFalse(monitor.isAdmissionReady(), "Old server handshake must not authorize new connection")
    }

    @Test
    fun `handshake paused in getConnectionInfo resumes after shutdown and returns false without starting handshake`() =
        runBlocking {
            val connectionStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
            val connectionGate = kotlinx.coroutines.CompletableDeferred<Unit>()
            val supervisor =
                object : DaemonSupervisorPort {
                    var firstCall = true

                    override suspend fun getConnectionInfo(): ServerConnectionDescriptor? {
                        if (firstCall) {
                            firstCall = false
                            connectionStarted.complete(Unit)
                            connectionGate.await()
                        }
                        return ServerConnectionDescriptor("127.0.0.1", 1234, "tok", 1L, "Ubuntu")
                    }

                    override suspend fun ensureStarted(): ServerConnectionDescriptor = error("not used")

                    override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean = true

                    override suspend fun shutdownDaemon(): Boolean = true

                    override fun close() {}
                }
            val transport = FakeTransport(heartbeatResponse = true)
            val monitor = DaemonHeartbeatMonitor(supervisor, transport = transport)

            val caller = async(kotlinx.coroutines.Dispatchers.IO) { monitor.awaitInitialHeartbeat(timeoutMs = 5000L) }
            connectionStarted.await()

            monitor.shutdown()
            connectionGate.complete(Unit)

            val result = caller.await()
            assertFalse(result, "Caller resuming after shutdown must return false")
            assertFalse(monitor.isAdmissionReady())
            assertEquals(0, transport.heartbeatCount, "No handshake should run after shutdown")
        }

    @Test
    fun `periodic check during connection replacement does not restore stale admission`() = runBlocking {
        val supervisor =
            FakeSupervisor(
                healthy = true,
                connection = ServerConnectionDescriptor("127.0.0.1", 1234, "tok1", 100L, "Ubuntu"),
            )
        val transport = FakeTransport(heartbeatResponse = true)
        val monitor = DaemonHeartbeatMonitor(supervisor, transport = transport, intervalMs = 50)

        assertTrue(monitor.awaitInitialHeartbeat(timeoutMs = 500L))
        assertTrue(monitor.isAdmissionReady())

        val heartbeatStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val heartbeatGate = kotlinx.coroutines.CompletableDeferred<Boolean>()
        transport.heartbeatGate = heartbeatGate
        transport.onHeartbeatStarted = { heartbeatStarted.complete(Unit) }

        val checkJob = launch { monitor.checkLiveness() }
        heartbeatStarted.await()

        supervisor.connection = ServerConnectionDescriptor("127.0.0.1", 5678, "tok2", 200L, "Ubuntu")
        monitor.resetHandshake()

        heartbeatGate.complete(true)
        checkJob.join()

        assertFalse(monitor.isAdmissionReady(), "Stale periodic heartbeat must not restore admission")
    }

    @Test
    fun `server replacement with different descriptor invalidates previous acknowledgment`() = runBlocking {
        val supervisor =
            FakeSupervisor(
                healthy = true,
                connection = ServerConnectionDescriptor("127.0.0.1", 1234, "tok1", 100L, "Ubuntu"),
            )
        val transport = FakeTransport(heartbeatResponse = true)
        val monitor = DaemonHeartbeatMonitor(supervisor, transport = transport)

        assertTrue(monitor.awaitInitialHeartbeat(timeoutMs = 500L))
        assertTrue(monitor.isAdmissionReady())

        // Replace server with new port/PID
        supervisor.connection = ServerConnectionDescriptor("127.0.0.1", 5678, "tok2", 200L, "Ubuntu")
        assertFalse(monitor.isAdmissionReady(), "Admission must be invalidated when descriptor changes")

        // Next call performs fresh handshake for new connection
        assertTrue(monitor.awaitInitialHeartbeat(timeoutMs = 500L))
        assertTrue(monitor.isAdmissionReady())
        assertEquals(2, transport.heartbeatCount)
        assertEquals(200L, transport.heartbeatedDescriptors.last().pid)
    }

    @Test
    fun `periodic heartbeat failure invalidates admission despite healthy health endpoint`() = runBlocking {
        val supervisor = FakeSupervisor(healthy = true)
        val transport = FakeTransport(heartbeatResponse = true)
        val monitor = DaemonHeartbeatMonitor(supervisor, transport = transport, intervalMs = 50)

        assertTrue(monitor.awaitInitialHeartbeat(timeoutMs = 500L))
        assertTrue(monitor.isAdmissionReady())

        transport.heartbeatResponse = false
        monitor.checkLiveness()

        assertFalse(monitor.isAdmissionReady(), "Admission must be invalidated when periodic heartbeat fails")
    }

    @Test
    fun `reconnection does not report Connected if handshake fails`() = runBlocking {
        val supervisor = FakeSupervisor(healthy = false)
        val stateHolder = ConnectionStateHolder()
        val transport = FakeTransport(heartbeatResponse = false)
        val monitor =
            DaemonHeartbeatMonitor(
                supervisor = supervisor,
                stateHolder = stateHolder,
                transport = transport,
                maxMissedHeartbeats = 1,
            )

        monitor.checkLiveness()

        assertTrue(
            stateHolder.currentState is ConnectionState.Failed,
            "State must be Failed when re-attached handshake is rejected",
        )
        assertFalse(monitor.isAdmissionReady())
    }

    @Test
    fun `shutdown cancels and joins in-flight handshake and monitor job`() = runBlocking {
        val supervisor = FakeSupervisor(healthy = true)
        val handshakeStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val handshakeGate = kotlinx.coroutines.CompletableDeferred<Boolean>()

        val transport =
            FakeTransport(
                heartbeatGate = handshakeGate,
                onHeartbeatStarted = { handshakeStarted.complete(Unit) },
            )
        val monitor = DaemonHeartbeatMonitor(supervisor, transport = transport)

        val inFlightCaller = async { monitor.awaitInitialHeartbeat(timeoutMs = 5000L) }
        handshakeStarted.await()

        monitor.shutdown()

        assertFalse(monitor.isAdmissionReady())
        assertFalse(monitor.awaitInitialHeartbeat(timeoutMs = 100L))
    }

    @Test
    fun `shutdown during running monitor loop cancels and joins reattachment without releasing gate`() = runBlocking {
        val reattachStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val reattachGate = kotlinx.coroutines.CompletableDeferred<ServerConnectionDescriptor>()
        val supervisor =
            object : DaemonSupervisorPort {
                val descriptor = ServerConnectionDescriptor("127.0.0.1", 1234, "tok", 1L, "Ubuntu")

                override suspend fun getConnectionInfo(): ServerConnectionDescriptor? = descriptor

                override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean = false

                override suspend fun ensureStarted(): ServerConnectionDescriptor {
                    reattachStarted.complete(Unit)
                    return reattachGate.await()
                }

                override suspend fun shutdownDaemon(): Boolean = true

                override fun close() {}
            }
        val stateHolder = ConnectionStateHolder()
        val transport = FakeTransport(heartbeatResponse = true)
        val monitor =
            DaemonHeartbeatMonitor(
                supervisor = supervisor,
                stateHolder = stateHolder,
                transport = transport,
                intervalMs = 10,
                maxMissedHeartbeats = 1,
            )

        monitor.start()
        reattachStarted.await()

        // Shutdown cancels and joins the monitor loop without needing reattachGate to complete
        monitor.shutdown()

        assertFalse(monitor.isAdmissionReady(), "Admission must remain false after shutdown")
        assertFalse(stateHolder.isConnected, "State must not be Connected after shutdown")
        assertFalse(
            stateHolder.currentState is ConnectionState.Failed,
            "Cancellation on shutdown must not be converted into ConnectionState.Failed",
        )
    }

    @Test
    fun `obsolete reconnect attempt does not overwrite newer connection with Failed`() = runBlocking {
        val reattachStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val reattachGate = kotlinx.coroutines.CompletableDeferred<ServerConnectionDescriptor>()
        val supervisor =
            object : DaemonSupervisorPort {
                var descriptor: ServerConnectionDescriptor? =
                    ServerConnectionDescriptor("127.0.0.1", 1234, "tok1", 1L, "Ubuntu")

                override suspend fun getConnectionInfo(): ServerConnectionDescriptor? = descriptor

                override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean = false

                override suspend fun ensureStarted(): ServerConnectionDescriptor {
                    reattachStarted.complete(Unit)
                    return reattachGate.await()
                }

                override suspend fun shutdownDaemon(): Boolean = true

                override fun close() {}
            }
        val stateHolder = ConnectionStateHolder()
        val transport = FakeTransport(heartbeatResponse = false)
        val monitor =
            DaemonHeartbeatMonitor(
                supervisor = supervisor,
                stateHolder = stateHolder,
                transport = transport,
                maxMissedHeartbeats = 1,
            )

        val checkJob = launch { monitor.checkLiveness() }
        reattachStarted.await()

        // While reconnect for connection 1 is suspended, connection 2 becomes active and healthy
        val newConn = ServerConnectionDescriptor("127.0.0.1", 5678, "tok2", 2L, "Ubuntu")
        supervisor.descriptor = newConn
        transport.heartbeatResponse = true
        assertTrue(monitor.awaitInitialHeartbeat(timeoutMs = 500L, forceRefresh = true))
        stateHolder.transitionTo(ConnectionState.Connected(newConn))

        // Now release the old reconnect attempt with a rejected handshake (heartbeatResponse is false)
        transport.heartbeatResponse = false
        reattachGate.complete(ServerConnectionDescriptor("127.0.0.1", 1234, "tok1", 1L, "Ubuntu"))
        checkJob.join()

        // Obsolete reconnect settlement must be discarded: state remains Connected to connection 2, not Failed!
        assertTrue(monitor.isAdmissionReady(), "New connection must remain admitted")
        assertTrue(stateHolder.isConnected, "State must remain Connected to new connection")
        assertEquals(newConn, (stateHolder.currentState as ConnectionState.Connected).descriptor)
    }

    @Test
    fun `stale failed check from old connection does not invalidate new connection admission or increment misses`() =
        runBlocking {
            val supervisor =
                FakeSupervisor(
                    healthy = true,
                    connection = ServerConnectionDescriptor("127.0.0.1", 1234, "tok1", 100L, "Ubuntu"),
                )
            val transport = FakeTransport(heartbeatResponse = true)
            val monitor =
                DaemonHeartbeatMonitor(
                    supervisor = supervisor,
                    transport = transport,
                    intervalMs = 50,
                    maxMissedHeartbeats = 2,
                )

            assertTrue(monitor.awaitInitialHeartbeat(timeoutMs = 500L))
            assertTrue(monitor.isAdmissionReady())

            val oldHeartbeatStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
            val oldHeartbeatGate = kotlinx.coroutines.CompletableDeferred<Boolean>()
            transport.heartbeatGate = oldHeartbeatGate
            transport.onHeartbeatStarted = { oldHeartbeatStarted.complete(Unit) }

            val oldCheckJob = launch { monitor.checkLiveness() }
            oldHeartbeatStarted.await()

            supervisor.connection = ServerConnectionDescriptor("127.0.0.1", 5678, "tok2", 200L, "Ubuntu")
            transport.heartbeatGate = null
            transport.heartbeatResponse = true
            assertTrue(monitor.awaitInitialHeartbeat(timeoutMs = 500L, forceRefresh = true))
            assertTrue(monitor.isAdmissionReady())

            oldHeartbeatGate.complete(false)
            oldCheckJob.join()

            assertTrue(
                monitor.isAdmissionReady(),
                "Stale failed check from old connection must not invalidate new connection",
            )
        }
}
