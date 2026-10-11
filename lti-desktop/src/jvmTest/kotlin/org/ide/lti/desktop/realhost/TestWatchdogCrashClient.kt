package org.ide.lti.desktop.realhost

import io.ltirom.tooling.client.WslDaemonConfig
import io.ltirom.tooling.client.WslDaemonManager
import io.ltirom.tooling.client.resilience.DaemonHeartbeatMonitor
import io.ltirom.tooling.client.transport.KtorRemoteTransportAdapter
import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.runBlocking
import org.ide.lti.core.data.tools.KotlinToolStoreAdapter
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.model.setup.ToolRef
import kotlin.system.exitProcess

/**
 * Separate client process for Scenario F watchdog crash testing.
 *
 * Runs an active build while sending heartbeats, signals when the build is active in WSL,
 * and remains running until forcibly killed by the host test process without executing
 * coroutine cancellation or sending client-side cancellation requests.
 */
object TestWatchdogCrashClient {
    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        val distro = args.getOrNull(0) ?: "Ubuntu"
        val repoPath = args.getOrNull(1) ?: error("Missing repoPath")
        val commit = args.getOrNull(2) ?: error("Missing commit")
        val opId = args.getOrNull(3) ?: "op-scenario-f-2"
        val home = args.getOrNull(4) ?: error("Missing home")

        val cli = WslCliExecutor()
        val detector = WslEnvironmentDetector(cli)
        val supervisor = WslDaemonManager(WslDaemonConfig(distroName = distro), cli = cli, detector = detector)
        val transport = KtorRemoteTransportAdapter(supervisor, supervisor.httpClient)
        val monitor =
            DaemonHeartbeatMonitor(
                supervisor = supervisor,
                transport = transport,
                intervalMs = 3_000L,
            )
        if (!monitor.awaitInitialHeartbeat()) {
            System.err.println("CRASH_CLIENT_ERR: Heartbeat admission failed")
            exitProcess(1)
        }
        monitor.start()

        val store =
            KotlinToolStoreAdapter(
                transport = transport,
                heartbeatMonitor = monitor,
                serverDirProvider = { "$home/.ltirom/server/current" },
            )

        val group =
            Fixtures.createTestToolGroup(
                id = "scenario-f-tool",
                repoUrl = repoPath,
                ref = ToolRef.Tag("v1.0.0"),
                commit = commit,
            )

        val req =
            InstallRequest(
                opId = opId,
                group = group.id,
                label = "v1.0.0",
                source = ResolvedSource.Git(repoUrl = repoPath, ref = ToolRef.Tag("v1.0.0"), commit = commit),
                recipe = group.recipe,
                outputs = group.outputs,
                select = true,
                choice = ChoiceUpdate.Record,
            )

        store.install(req).launchIn(this)

        // Wait until build is running in WSL
        val waitStart = System.currentTimeMillis()
        var running = false
        while (!running && System.currentTimeMillis() - waitStart < 30_000L) {
            delay(200)
            val pgrep = cli.execute(distro, listOf("sh", "-c", "pgrep -f '[s]leep 60' || true")).output.trim()
            if (pgrep.isNotBlank()) {
                running = true
            }
        }

        if (!running) {
            System.err.println("CRASH_CLIENT_ERR: Build process did not start")
            exitProcess(2)
        }

        // Signal to parent test that build is active
        println("CRASH_CLIENT_ACTIVE")
        System.out.flush()

        // Keep running and sending heartbeats until abruptly killed at OS level
        while (true) {
            delay(10_000L)
        }
    }
}
