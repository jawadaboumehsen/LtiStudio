/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data

import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.ports.StopClientResult
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.ide.lti.core.domain.tools.ShutdownReport
import org.ide.lti.core.domain.tools.ToolManager
import org.ide.lti.core.domain.tools.ToolStorePort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AppShutdownCoordinatorTest {
    private class TestTransport(var stopResult: StopClientResult = StopClientResult(stopped = true)) :
        RemoteTransportPort {
        var stopCalled = false

        override suspend fun stopClient(): StopClientResult {
            stopCalled = true
            return stopResult
        }

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse = error("unused")

        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()

        override suspend fun checkHealth(): WslServerInfo? = null

        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(emptyList())

        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(emptyList())

        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true

        override suspend fun downloadFile(remotePath: String): ByteArray? = null

        override suspend fun shutdown(): Boolean = true
    }

    @Test
    fun `successful stop reports Stopped`() = runTest {
        val transport = TestTransport(stopResult = StopClientResult(stopped = true))
        val coordinator = AppShutdownCoordinator(transport)

        val report = coordinator.shutdown()
        assertTrue(report is ShutdownReport.Stopped)
        assertTrue(transport.stopCalled)
    }

    @Test
    fun `failed stop reports Unconfirmed`() = runTest {
        val transport = TestTransport(stopResult = StopClientResult(stopped = false))
        val coordinator = AppShutdownCoordinator(transport)

        val report = coordinator.shutdown(timeout = 50.milliseconds)
        assertTrue(report is ShutdownReport.Unconfirmed)
    }

    @Test
    fun `unconfirmed execution IDs are propagated in ShutdownReport Unconfirmed`() = runTest {
        val transport =
            TestTransport(
                stopResult =
                StopClientResult(
                    stopped = false,
                    unconfirmedExecutionIds = listOf("exec-1", "run-2"),
                ),
            )
        val coordinator = AppShutdownCoordinator(transport)

        val report = coordinator.shutdown()
        assertTrue(report is ShutdownReport.Unconfirmed)
        assertEquals(listOf("exec-1", "run-2"), report.executionIds)
    }

    private class TestSupervisor : io.ltirom.tooling.core.ports.DaemonSupervisorPort {
        override suspend fun ensureStarted(): io.ltirom.tooling.core.ports.ServerConnectionDescriptor = error("unused")

        override suspend fun getConnectionInfo(): io.ltirom.tooling.core.ports.ServerConnectionDescriptor? = null

        override suspend fun isHealthy(info: io.ltirom.tooling.core.ports.ServerConnectionDescriptor): Boolean = true

        override suspend fun shutdownDaemon(): Boolean = true

        override fun close() {}
    }

    @Test
    fun `heartbeat monitor is quiesced before stopClient and marks disconnected on unconfirmed stop`() = runTest {
        var monitorShutdownBeforeStopClient = false
        var monitorRef: io.ltirom.tooling.client.resilience.DaemonHeartbeatMonitor? = null
        val transport =
            object : RemoteTransportPort by TestTransport() {
                override suspend fun stopClient(): StopClientResult {
                    monitorShutdownBeforeStopClient = monitorRef?.isShutdown == true
                    return StopClientResult(
                        stopped = false,
                        unconfirmedExecutionIds = listOf("run-1"),
                    )
                }
            }
        val monitor = io.ltirom.tooling.client.resilience.DaemonHeartbeatMonitor(TestSupervisor(), scope = this)
        monitorRef = monitor
        val coordinator = AppShutdownCoordinator(transport, monitor)

        val report = coordinator.shutdown()
        assertTrue(report is ShutdownReport.Unconfirmed)
        assertTrue(
            monitorShutdownBeforeStopClient,
            "Heartbeat monitor must be shut down/quiesced BEFORE stopClient",
        )
        assertEquals(
            io.ltirom.tooling.client.resilience.ConnectionState.Disconnected("Shutdown unconfirmed"),
            monitor.currentState,
            "Heartbeat monitor must transition to explicit Disconnected state on unconfirmed shutdown",
        )

        coordinator.forceShutdown()
        assertTrue(monitor.isShutdown, "Heartbeat monitor must be stopped on forceShutdown")
    }

    @Test
    fun `heartbeat monitor is shut down when stop client confirms clean termination`() = runTest {
        var monitorShutdownBeforeStopClient = false
        var monitorRef: io.ltirom.tooling.client.resilience.DaemonHeartbeatMonitor? = null
        val stopResult =
            StopClientResult(
                stopped = true,
                unconfirmedExecutionIds = emptyList(),
            )
        val transport =
            object : RemoteTransportPort by TestTransport(stopResult = stopResult) {
                override suspend fun stopClient(): StopClientResult {
                    monitorShutdownBeforeStopClient = monitorRef?.isShutdown == true
                    return stopResult
                }
            }
        val monitor = io.ltirom.tooling.client.resilience.DaemonHeartbeatMonitor(TestSupervisor(), scope = this)
        monitorRef = monitor
        val coordinator = AppShutdownCoordinator(transport, monitor)

        val report = coordinator.shutdown()
        assertTrue(report is ShutdownReport.Stopped)
        assertTrue(monitorShutdownBeforeStopClient, "Heartbeat monitor must be quiesced BEFORE stopClient")
        assertTrue(monitor.isShutdown, "Heartbeat monitor must be stopped on successful clean shutdown")
    }

    @Test
    fun `shutdown cancels via toolManager when available and not toolStore twice`() = runTest {
        var storeCancelAllCount = 0
        val fakeStore =
            object : ToolStorePort by ToolStorePort.NoOp {
                override suspend fun cancelAll(budgetMs: Long): List<Result<Unit>> {
                    storeCancelAllCount++
                    return emptyList()
                }
            }
        val manager = ToolManager(fakeStore)
        val transport = TestTransport(stopResult = StopClientResult(stopped = true))
        val coordinator = AppShutdownCoordinator(transport, toolManager = manager, toolStore = fakeStore)

        coordinator.shutdown()
        assertEquals(1, storeCancelAllCount, "cancelAll must be called exactly once")
    }

    @Test
    fun `tool cleanup gets a share of the shutdown deadline, not all of it`() = runTest {
        val budgets = mutableListOf<Long>()
        val store =
            object : ToolStorePort by ToolStorePort.NoOp {
                override suspend fun cancelAll(budgetMs: Long): List<Result<Unit>> {
                    budgets += budgetMs
                    return emptyList()
                }
            }
        val coordinator = AppShutdownCoordinator(TestTransport(), toolStore = store)

        coordinator.shutdown(10.seconds)
        coordinator.shutdown(5.seconds)

        assertEquals(listOf(6_000L, 3_000L), budgets, "60% of the deadline, leaving time to stop the client")
    }

    @Test
    fun `a monitor stuck behind its lock cannot stretch shutdown past its deadline`() = runBlocking<Unit> {
        val connection = io.ltirom.tooling.core.ports.ServerConnectionDescriptor("127.0.0.1", 1, "t", 1L, "Ubuntu")
        val stuck = CompletableDeferred<Unit>()
        // The second read of the connection happens inside the monitor's lock and never returns.
        var reads = 0
        val supervisor =
            object : io.ltirom.tooling.core.ports.DaemonSupervisorPort {
                override suspend fun ensureStarted() = connection

                override suspend fun getConnectionInfo(): io.ltirom.tooling.core.ports.ServerConnectionDescriptor? {
                    if (++reads == 2) {
                        stuck.complete(Unit)
                        awaitCancellation()
                    }
                    return connection
                }

                override suspend fun isHealthy(info: io.ltirom.tooling.core.ports.ServerConnectionDescriptor) = true

                override suspend fun shutdownDaemon() = true

                override fun close() {}
            }
        val monitor = io.ltirom.tooling.client.resilience.DaemonHeartbeatMonitor(supervisor, intervalMs = 60_000)
        val check = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { monitor.checkLiveness() }
        withTimeout(5_000) { stuck.await() }
        val coordinator = AppShutdownCoordinator(TestTransport(), monitor)

        val started = System.nanoTime()
        val report = withTimeout(10_000) { coordinator.shutdown(timeout = 500.milliseconds) }
        val tookMs = (System.nanoTime() - started) / 1_000_000

        assertTrue(report is ShutdownReport.Unconfirmed, "an orderly stop that did not finish is not Stopped: $report")
        assertTrue(tookMs < 3_000, "took ${tookMs}ms for a 500 ms deadline")
        assertTrue(monitor.isShutdown, "the monitor must be refused further work")
        check.cancel()
    }
}
