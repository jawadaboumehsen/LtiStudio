package io.ltirom.tooling.client.wsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WslCliExecutorTest {

    private val executor = WslCliExecutor()

    @Test
    fun executeHostCapturesOutputSuccessfully() {
        val result = executor.executeHost(listOf("cmd.exe", "/c", "echo", "HelloHost"), timeoutSeconds = 5)
        assertEquals(0, result.exitCode)
        assertTrue(result.output.contains("HelloHost"))
    }

    @Test
    fun executeHostDrainsLargeOutputWithoutPipeDeadlock() {
        // Generate > 200KB of output. Under unbuffered/sequential reading, this would deadlock
        // against the 64KB OS pipe buffer.
        val script = "for /L %i in (1,1,5000) do @echo Line %i of large output block"
        val result = executor.executeHost(listOf("cmd.exe", "/c", script), timeoutSeconds = 10)
        assertEquals(0, result.exitCode)
        assertTrue(result.output.isNotEmpty())
    }

    @Test
    fun executeHostTimesOutAndForciblyKillsHangingProcess() {
        // ping 127.0.0.1 -n 10 takes 9 seconds; timeout is 1 second
        val result = executor.executeHost(listOf("ping", "127.0.0.1", "-n", "10"), timeoutSeconds = 1)
        assertEquals(-1, result.exitCode)
        assertTrue(result.error.contains("Command timed out after 1 seconds"))
    }

    @Test
    fun executeHostTimesOutAndKillsProcessTreeWithDescendants() {
        var childHandle: ProcessHandle? = null
        val testExecutor = WslCliExecutor().apply {
            onProcessStarted = { p ->
                val deadline = System.currentTimeMillis() + 3000L
                while (System.currentTimeMillis() < deadline && childHandle == null) {
                    childHandle = p.descendants().findFirst().orElse(null)
                    if (childHandle == null) {
                        Thread.sleep(20L)
                    }
                }
            }
        }

        val result = testExecutor.executeHost(
            listOf("cmd.exe", "/c", "ping", "127.0.0.1", "-n", "10"),
            timeoutSeconds = 1
        )
        assertEquals(-1, result.exitCode)
        assertTrue(result.error.contains("Command timed out after 1 seconds"))

        // Assert that the child process was spawned and captured
        kotlin.test.assertNotNull(childHandle, "Child process (ping.exe) must have been spawned")
        // Assert that the child process was forcibly terminated and is no longer alive
        kotlin.test.assertFalse(childHandle.isAlive, "Child process must not remain alive after timeout")
    }

    private class FakeSurvivingProcess(private val pidValue: Long = 99999L) : Process() {
        override fun getOutputStream(): java.io.OutputStream = java.io.ByteArrayOutputStream()
        override fun getInputStream(): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
        override fun getErrorStream(): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
        override fun waitFor(): Int = 0
        override fun exitValue(): Int = throw IllegalThreadStateException()
        override fun destroy() {}
        override fun destroyForcibly(): Process = this
        override fun isAlive(): Boolean = true
        override fun pid(): Long = pidValue
        override fun waitFor(timeout: Long, unit: java.util.concurrent.TimeUnit): Boolean = false
        override fun toHandle(): ProcessHandle = throw UnsupportedOperationException()
    }

    @Test
    fun killProcessTreeReportsIncompleteCleanupWhenProcessSurvives() {
        val fakeProcess = FakeSurvivingProcess(88888L)
        val cleanup = executor.killProcessTree(fakeProcess)
        kotlin.test.assertFalse(cleanup.isClean, "Cleanup must report incomplete when a process survives")
        assertEquals(listOf(88888L), cleanup.survivingPids)
    }

    @Test
    fun commandsWithoutDoubleQuotesArePassedUnchanged() {
        assertEquals(
            listOf("wsl.exe", "-d", "Ubuntu", "-u", "root", "-e", "sh", "-c", "chmod +x '/a b'/bin/*"),
            executor.wslCommandLine("Ubuntu", listOf("sh", "-c", "chmod +x '/a b'/bin/*"), user = "root"),
        )
    }

    @Test
    fun doubleQuotesNeverReachTheWindowsCommandLine() {
        // The package probe's Java line: Windows used to cut it at the space before -version.
        val script = "for d in /usr/lib/jvm/*; do echo \"\$d|\$(\"\$d/bin/java\" -version 2>&1)\"; done; echo 'it''s'"

        val argv = executor.wslCommandLine("Ubuntu", listOf("sh", "-c", script))

        assertTrue(argv.none { '"' in it }, "$argv")
        assertEquals(listOf("wsl.exe", "-d", "Ubuntu", "-e", "sh", "-c"), argv.take(6))
        val encoded = argv.last().removePrefix("echo ").removeSuffix(" | base64 -d | sh")
        val decoded = String(java.util.Base64.getDecoder().decode(encoded), Charsets.UTF_8)
        // Each argument single-quoted for sh, embedded single quotes closed and escaped.
        val quoted = "'" + script.replace("'", "'\\''") + "'"
        assertEquals("'sh' '-c' $quoted", decoded)
    }
}

