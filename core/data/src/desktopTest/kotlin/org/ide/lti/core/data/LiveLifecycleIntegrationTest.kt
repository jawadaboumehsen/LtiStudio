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

import io.ltirom.tooling.client.WslDaemonConfig
import io.ltirom.tooling.client.WslDaemonManager
import io.ltirom.tooling.client.resilience.DaemonHeartbeatMonitor
import io.ltirom.tooling.client.transport.KtorRemoteTransportAdapter
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.runBlocking
import org.ide.lti.core.domain.tools.ShutdownReport
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Live lifecycle integration test against a running WSL daemon.
 *
 * Verifies transport, heartbeat monitor, and shutdown coordinator lifecycle interactions
 * (session launch, heartbeat admission, command execution, graceful stop, and relaunch on
 * a surviving daemon).
 *
 * Note: This verifies daemon connection and lifecycle contracts, not Setup's production
 * initialization, Doctor orchestration, or component inventory.
 *
 * Requires explicit opt-in via LTI_LIVE_WSL=1 to avoid interfering with active work on default distros.
 * Optionally set LTI_TEST_DISTRO to target an isolated test WSL distribution.
 */
class LiveLifecycleIntegrationTest {
    @Test
    fun `live daemon lifecycle launch, command execution, shutdown, and relaunch on surviving daemon`() = runBlocking {
        assumeTrue(
            "Live WSL lifecycle integration test requires explicit opt-in (LTI_LIVE_WSL=1)",
            System.getenv("LTI_LIVE_WSL") == "1",
        )

        val testDistro = System.getenv("LTI_TEST_DISTRO")
        assumeTrue(
            "Live WSL test requires isolated test distro in LTI_TEST_DISTRO to protect real user distros",
            !testDistro.isNullOrBlank(),
        )
        val manager = WslDaemonManager(WslDaemonConfig(distroName = testDistro!!))
        val adapter = KtorRemoteTransportAdapter(manager, manager.httpClient)

        var monitor1: DaemonHeartbeatMonitor? = null
        var monitor2: DaemonHeartbeatMonitor? = null

        try {
            // -------------------------------------------------------------
            // Phase 1: Session 1 Launch, Handshake & Command Execution
            // -------------------------------------------------------------
            val descriptor = manager.ensureStarted()
            assertTrue(descriptor.port > 0)
            assertTrue(descriptor.token.isNotBlank())

            monitor1 =
                DaemonHeartbeatMonitor(
                    supervisor = manager,
                    transport = adapter,
                    intervalMs = 1000L,
                )

            assertTrue(
                monitor1.awaitInitialHeartbeat(timeoutMs = 5000L),
                "Initial handshake must succeed on live daemon",
            )
            monitor1.start()
            assertTrue(monitor1.isAdmissionReady(), "Admission must be open after handshake")

            val doctorReq =
                ToolExecutionRequest(
                    toolId = "mke2fs",
                    arguments = listOf("-V"),
                    workingDirectory = "/home/lti",
                    acceptedExitCodes = setOf(0, 1),
                )
            val response1 = adapter.execute(doctorReq)
            assertEquals(0, response1.exitCode)
            assertTrue(response1.stderr.contains("mke2fs") || response1.stdout.contains("mke2fs"))

            // -------------------------------------------------------------
            // Phase 2: Session 1 Shutdown via Coordinator
            // -------------------------------------------------------------
            val coordinator1 =
                AppShutdownCoordinator(
                    transport = adapter,
                    heartbeatMonitor = monitor1,
                )
            val report1 = coordinator1.shutdown()
            assertEquals(ShutdownReport.Stopped, report1, "Shutdown must report Stopped")
            assertFalse(monitor1.isAdmissionReady(), "Monitor 1 admission must be closed after shutdown")

            // -------------------------------------------------------------
            // Phase 3: Session 2 Relaunch on Surviving Daemon
            // -------------------------------------------------------------
            monitor2 =
                DaemonHeartbeatMonitor(
                    supervisor = manager,
                    transport = adapter,
                    intervalMs = 1000L,
                )

            assertTrue(
                monitor2.awaitInitialHeartbeat(timeoutMs = 5000L),
                "Relaunched session must re-establish handshake on surviving daemon",
            )
            monitor2.start()
            assertTrue(monitor2.isAdmissionReady(), "Admission must be open for relaunched session")

            val response2 = adapter.execute(doctorReq)
            assertEquals(0, response2.exitCode)
            assertTrue(response2.stderr.contains("mke2fs") || response2.stdout.contains("mke2fs"))
        } finally {
            // Always clean up monitors and leave surviving daemon in a consistent state
            try {
                if (monitor2 != null) {
                    val coordinator2 =
                        AppShutdownCoordinator(
                            transport = adapter,
                            heartbeatMonitor = monitor2,
                        )
                    coordinator2.shutdown()
                } else if (monitor1 != null) {
                    val coordinator1 =
                        AppShutdownCoordinator(
                            transport = adapter,
                            heartbeatMonitor = monitor1,
                        )
                    coordinator1.shutdown()
                }
            } catch (_: Exception) {
                // Ignore cleanup failures in finally block
            }
            monitor1?.close()
            monitor2?.close()
        }
    }
}
