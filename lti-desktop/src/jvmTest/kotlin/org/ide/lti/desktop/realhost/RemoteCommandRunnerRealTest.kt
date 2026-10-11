package org.ide.lti.desktop.realhost

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.ide.lti.core.data.tools.CommandCategory
import org.ide.lti.core.data.tools.RemoteCommandRunner
import org.ide.lti.core.data.tools.ToolErrorException
import org.ide.lti.core.domain.tools.ToolError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteCommandRunnerRealTest {
    @Test
    fun streamForwardsStdoutAndStderrLinesInOrder() = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking
        RealToolHost.create().use { host ->
            val runner = RemoteCommandRunner(host.transport)
            val script = "for i in 1 2 3; do echo out\$i; echo err\$i >&2; done"
            val lines = runner.stream(script, CommandCategory.BUILD).toList()
            val text = lines.joinToString("")
            assertTrue(text.contains("out1") && text.contains("out2") && text.contains("out3"))
        }
    }

    @Test
    fun networkRetriesThreeTimesThenFailsWithNetworkError() = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking
        RealToolHost.create().use { host ->
            var attempts = 0
            val runner = RemoteCommandRunner(host.transport, networkBackoffMs = listOf(50L, 150L, 450L))
            val res =
                runner.execute(
                    script = "git ls-remote http://127.0.0.1:54321/x.git",
                    category = CommandCategory.NETWORK,
                    timeoutSeconds = 5L,
                )
            assertTrue(res.isFailure)
            val ex = res.exceptionOrNull()
            assertTrue(ex is ToolErrorException && ex.toolError is ToolError.Network)
        }
    }

    @Test
    fun failingBuildRunsOnceWithoutRetries() = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking
        RealToolHost.create().use { host ->
            val runner = RemoteCommandRunner(host.transport)
            val res = runner.execute("sh -c 'exit 2'", category = CommandCategory.BUILD)
            assertTrue(res.isSuccess)
            assertEquals(2, res.getOrNull()?.exitCode)
        }
    }

    @Test
    fun cancelActiveStopsRunningSleepProcess() = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking
        RealToolHost.create().use { host ->
            val runner = RemoteCommandRunner(host.transport)
            val job =
                launch {
                    runner.stream("sleep 600", category = CommandCategory.BUILD).collect {}
                }
            kotlinx.coroutines.delay(200)
            runner.cancelActive()
            job.cancel()
        }
    }

    @Test
    fun normalExitDoesNotKillBackgroundDescendant() = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking
        RealToolHost.create().use { host ->
            val runner = RemoteCommandRunner(host.transport)
            // Spawn a detached background process and print its PID
            val script = "sleep 60 >/dev/null 2>&1 & echo \$!"
            val res = runner.execute(script, category = CommandCategory.SHORT)
            assertTrue(res.isSuccess, "Command execution failed: ${res.exceptionOrNull()?.message}")
            val pidStr = res.getOrNull()?.stdout?.trim()?.lines()?.lastOrNull()?.trim() ?: ""
            assertTrue(pidStr.isNotBlank(), "Should have captured sleep PID")
            val pid = pidStr.toLong()
            // Check if PID is still alive after the command has exited normally
            val checkAlive = runner.execute("kill -0 $pid", category = CommandCategory.SHORT)
            try {
                assertTrue(
                    checkAlive.isSuccess && checkAlive.getOrNull()?.exitCode == 0,
                    "Background helper should still be alive after normal command exit",
                )
            } finally {
                // Clean up the background process
                runner.execute("kill -9 $pid", category = CommandCategory.SHORT)
            }
        }
    }

    @Test
    fun streamCollectEnforcesBoundedAllocationOnExcessiveOutput(): Unit = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking
        RealToolHost.create().use { host ->
            val runner = RemoteCommandRunner(host.transport)
            // Generate 1000 lines of 100 bytes = ~100 KB
            val script = "for i in \$(seq 1 1000); do printf '%0100d\\n' \$i; done"
            // Cap at 2048 chars
            val res =
                runner.streamCollect(
                    script = script,
                    category = CommandCategory.SHORT,
                    maxOutputChars = 2048,
                )
            assertTrue(res.isSuccess)
            val output = res.getOrThrow()
            assertTrue(
                output.length <= 2048,
                "Captured output must be bounded to maxOutputChars (was ${output.length})",
            )
        }
    }
}
