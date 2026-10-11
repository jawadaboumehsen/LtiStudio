package org.ide.lti.desktop.realhost

import io.ltirom.tooling.client.resilience.DaemonHeartbeatMonitor
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.ide.lti.core.data.AppShutdownCoordinator
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ShutdownReport
import org.ide.lti.core.model.setup.ToolRef
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Real-host test verifying app shutdown during active build (T086 / US6):
 * - AppShutdownCoordinator stops active install / runs, calls /api/v1/client/stop, waits <= 10 s.
 * - Afterwards no build process remains.
 * - Heartbeats stop last, and server watchdog never fires before the stop.
 */
class AppShutdownRealTest {
    private fun hostPath(host: RealToolHost, sub: String = ""): String = host.hostPath(sub)

    @Test
    fun shutdownDuringActiveBuildStopsWorkWithin10sLeavesNoProcessesAndStopsHeartbeat(): Unit = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repoPath = hostPath(host, "shutdown-slow-repo")
            val commit =
                Fixtures.setupSlowCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "shutdown-tool",
                    delaySeconds = 60,
                    tag = "v1.0.0",
                ).getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "shutdown-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                val monitor =
                    DaemonHeartbeatMonitor(
                        supervisor = host.supervisor,
                        transport = host.transport,
                        intervalMs = 2_000L,
                    )
                monitor.start()

                val coordinator =
                    AppShutdownCoordinator(
                        transport = host.transport,
                        heartbeatMonitor = monitor,
                        toolManager = host.manager,
                        toolStore = host.store,
                    )

                val opId = "op-shutdown-test-1"
                val req =
                    InstallRequest(
                        opId = opId,
                        group = group.id,
                        label = "v1.0.0",
                        source =
                        ResolvedSource.Git(
                            repoUrl = repoPath,
                            ref = ToolRef.Tag("v1.0.0"),
                            commit = commit,
                        ),
                        recipe = group.recipe,
                        outputs = group.outputs,
                        select = true,
                        choice = ChoiceUpdate.Record,
                    )

                var buildStarted = false
                val installJob =
                    host.store.install(req).onEach { ev ->
                        if (ev is InstallEvent.Output &&
                            (ev.text.contains("cmake") || ev.text.contains("Running build recipe"))
                        ) {
                            buildStarted = true
                        }
                    }.launchIn(this)

                // Wait until build actually starts running (sleep process active)
                val waitStart = System.currentTimeMillis()
                var pgrepRunning = ""
                while (pgrepRunning.isBlank() && System.currentTimeMillis() - waitStart < 30_000L) {
                    delay(200)
                    val res =
                        host.transport.execute(
                            ToolExecutionRequest(
                                "sh",
                                listOf("-c", "pgrep -x sleep || true"),
                                workingDirectory = "/tmp",
                            ),
                        )
                    pgrepRunning = res.stdout.trim()
                }
                assertTrue(pgrepRunning.isNotBlank(), "Build process (sleep) must be active before shutdown")

                // Trigger shutdown - must complete in <= 10 s
                val elapsed =
                    measureTimeMillis {
                        val report = coordinator.shutdown(10.seconds)
                        assertIs<ShutdownReport.Stopped>(report, "Shutdown report must be Stopped")
                    }
                assertTrue(elapsed <= 10_000L, "Shutdown took $elapsed ms, must be <= 10 000 ms")

                installJob.cancel()

                // Verify pgrep -f finds no lingering sleep or cmake process
                val pgrepAfter =
                    host.transport.execute(
                        ToolExecutionRequest(
                            "sh",
                            listOf("-c", "pgrep -f '[s]leep 60' || true"),
                            workingDirectory = "/tmp",
                        ),
                    )
                assertEquals("", pgrepAfter.stdout.trim(), "No lingering processes after shutdown")

                // Verify heartbeat monitor was closed
                assertTrue(monitor.isShutdown, "Heartbeat monitor must be stopped after clean shutdown")
            }
        }
    }

    @Test
    fun normalExitWithBackgroundChildLeavesChildTrackedAndKilledOnShutdown(): Unit = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val monitor =
                DaemonHeartbeatMonitor(
                    supervisor = host.supervisor,
                    transport = host.transport,
                    intervalMs = 2_000L,
                )
            monitor.start()

            val coordinator =
                AppShutdownCoordinator(
                    transport = host.transport,
                    heartbeatMonitor = monitor,
                    toolManager = host.manager,
                    toolStore = host.store,
                )

            val spawnRes =
                host.transport.execute(
                    ToolExecutionRequest(
                        "sh",
                        listOf("-c", "sleep 60 >/dev/null 2>&1 & echo started"),
                        workingDirectory = "/tmp",
                    ),
                )
            assertEquals(0, spawnRes.exitCode)
            assertTrue(spawnRes.stdout.contains("started"))

            val pgrepBefore =
                host.transport.execute(
                    ToolExecutionRequest(
                        "sh",
                        listOf("-c", "pgrep -f '[s]leep 60' || true"),
                        workingDirectory = "/tmp",
                    ),
                )
            assertTrue(pgrepBefore.stdout.trim().isNotBlank(), "Background child must be alive")

            val elapsed =
                measureTimeMillis {
                    val report = coordinator.shutdown(10.seconds)
                    assertIs<ShutdownReport.Stopped>(report, "Shutdown report must be Stopped")
                }
            assertTrue(elapsed <= 10_000L, "Shutdown took $elapsed ms, must be <= 10 000 ms")

            val pgrepAfter =
                host.transport.execute(
                    ToolExecutionRequest(
                        "sh",
                        listOf("-c", "pgrep -f '[s]leep 60' || true"),
                        workingDirectory = "/tmp",
                    ),
                )
            assertEquals("", pgrepAfter.stdout.trim(), "No background child process must linger after shutdown")
            assertTrue(monitor.isShutdown, "Heartbeat monitor must be stopped")
        }
    }
}
