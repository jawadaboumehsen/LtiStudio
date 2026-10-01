package io.ltirom.tooling.client.wsl

import io.ltirom.tooling.client.WslDaemonConfig
import io.ltirom.tooling.client.WslDaemonManager
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.test.runTest
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every launch used to leave the previous service running (76 of them on one machine); a request that
 * landed on a leftover failed with "run not found". A newly adopted or launched service retires the rest.
 */
class StrayServiceRetirementTest {

    // Real formats: `ps -eo pid=,args=` and `ss -ltnpH`.
    private val ps = """
          76598 /usr/lib/jvm/default-java/bin/java -classpath /home/u/.ltirom/server/versions/1.0.0-a/lib/ltirom-server-1.0.0.jar io.ltirom.server.LtiRomServerMainKt --port 0 --token tokA --host 0.0.0.0 --distro Ubuntu
          71719 /usr/lib/jvm/java-21-openjdk-amd64/bin/java -classpath /home/u/.ltirom/server/versions/1.0.0-b/lib/ltirom-server-1.0.0.jar io.ltirom.server.LtiRomServerMainKt --port 0 --token tokB --host 0.0.0.0 --distro Ubuntu
          12946 java -classpath /home/u/.ltirom/server/lib/ltirom-server.jar io.ltirom.server.LtiRomServerMainKt --port 0 --token tokC
            901 /usr/bin/python3 some-other-daemon --token nope
    """.trimIndent()
    private val ss = """
        LISTEN 0      511                 *:43559       *:* users:(("java",pid=76598,fd=37))
        LISTEN 0      511                 *:38407       *:* users:(("java",pid=71719,fd=37))
        LISTEN 0      511                 *:41961       *:* users:(("java",pid=12946,fd=37))
    """.trimIndent()

    @Test
    fun `ltirom services are found with their port and token`() {
        val services = WslDaemonManager.parseLtiRomServices(ps, ss, host = "172.30.0.1", distro = "Ubuntu")

        assertEquals(listOf(76598L, 71719L, 12946L), services.map { it.pid })
        assertEquals(listOf(43559, 38407, 41961), services.map { it.port })
        assertEquals(listOf("tokA", "tokB", "tokC"), services.map { it.token })
    }

    private class FakeCli(val ps: String, val ss: String) : WslCliExecutor() {
        val kills = mutableListOf<String>()
        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult = when (command.first()) {
            "ps" -> CliExecutionResult(0, ps, "")
            "ss" -> CliExecutionResult(0, ss, "")
            "kill" -> CliExecutionResult(0, "", "").also { kills += command.last() }
            else -> CliExecutionResult(1, "", "")
        }
    }

    /** 71719 has an active run (refuses); 12946 is too old for the shutdown endpoint and idle. */
    private class FakeProber : DaemonHealthProber(null) {
        val shutdownAsked = mutableListOf<Long>()
        override suspend fun isHealthy(info: ServerConnectionDescriptor, timeoutMs: Long) = true
        override suspend fun getServerInfo(info: ServerConnectionDescriptor): WslServerInfo = WslServerInfo(
            status = "UP", distro = "Ubuntu", kernelRelease = "6", architecture = "x86_64", javaVersion = "21",
            serverUptimeMs = 1, serverVersion = "1.0.0-a", activeRuns = if (info.pid == 71719L) 1 else 0,
        )
        override suspend fun requestShutdown(info: ServerConnectionDescriptor, force: Boolean): Boolean {
            assertEquals(false, force, "leftovers are never force-stopped")
            shutdownAsked += info.pid
            return false // 71719 refuses (busy); 12946 has no endpoint
        }
    }

    private class StateStore(val current: ServerConnectionDescriptor) : WslStateStore(WslCliExecutor()) {
        override fun readState(distro: String, wslIp: String) = current
    }

    @Test
    fun `adopting a service retires the others, never the one in use or a busy one`() = runTest {
        val current = ServerConnectionDescriptor("172.30.0.1", 43559, "tokA", 76598L, "Ubuntu")
        val cli = FakeCli(ps, ss)
        val prober = FakeProber()
        val manager = WslDaemonManager(
            config = WslDaemonConfig(distroName = "Ubuntu"),
            cli = cli,
            stateStore = StateStore(current),
            customProber = prober,
        )

        manager.ensureStarted("Ubuntu")

        assertEquals(listOf(71719L, 12946L), prober.shutdownAsked, "every leftover is asked; not the one in use")
        assertEquals(listOf("12946"), cli.kills, "only the idle old build without the endpoint is terminated")
    }
}
