package io.ltirom.tooling.client.wsl

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.concurrent.thread
import kotlin.time.measureTime

class WslProcessLauncherHandshakeTest {

    /** A process whose output the test writes by hand; it stays alive until destroyed. */
    private class ScriptedProcess : Process() {
        private val sink = PipedOutputStream()
        private val source = PipedInputStream(sink)

        @Volatile
        var destroyed = false

        fun emit(text: String) {
            sink.write(text.toByteArray())
            sink.flush()
        }

        override fun getInputStream(): InputStream = source
        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
        override fun getErrorStream(): InputStream = InputStream.nullInputStream()
        override fun waitFor(): Int = 0
        override fun exitValue(): Int = 0
        override fun isAlive(): Boolean = !destroyed
        override fun destroy() {
            destroyed = true
            runCatching { sink.close() }
        }
        override fun destroyForcibly(): Process = apply { destroy() }
    }

    @Test
    fun `a partial line with no newline cannot outlast the startup deadline`() = runBlocking {
        val process = ScriptedProcess()
        process.emit("Starting LtiRomServer, loading tools") // no newline, then silence
        val launcher = WslProcessLauncher(startupTimeoutMs = 500L)

        val elapsed = measureTime {
            assertFailsWith<IllegalStateException> { launcher.awaitHandshake(process, "Ubuntu") }
        }

        assertTrue(elapsed.inWholeMilliseconds < 5_000, "Deadline must hold: took $elapsed")
        assertTrue(process.destroyed, "A process that never became ready is destroyed")
    }

    @Test
    fun `the handshake line is parsed`() = runBlocking {
        val process = ScriptedProcess()
        val launcher = WslProcessLauncher(startupTimeoutMs = 5_000L)
        val result = async { launcher.awaitHandshake(process, "Ubuntu") }

        process.emit("booting\nLTI_WSL_SERVER_READY port=41234 token=abc pid=77\n")

        assertEquals(41234 to 77L, result.await())
        assertTrue(!process.destroyed)
    }

    @Test
    fun `cancelling the wait destroys the process`() = runBlocking {
        val process = ScriptedProcess()
        process.emit("partial")
        val launcher = WslProcessLauncher(startupTimeoutMs = 60_000L)
        val waiting = async { runCatching { launcher.awaitHandshake(process, "Ubuntu") } }

        delay(200)
        waiting.cancel()
        waiting.join()

        assertTrue(process.destroyed, "Cancellation must not leave a half-started service behind")
    }

    @Test
    fun `the handshake survives a large output burst right after it`() = runBlocking {
        val process = ScriptedProcess()
        val launcher = WslProcessLauncher(startupTimeoutMs = 10_000L)
        val burst = (1..5_000).joinToString("") { "log line $it after startup\n" }
        // The pipe is small, so the writer blocks until the reader drains it: write from another thread.
        thread { process.emit("LTI_WSL_SERVER_READY port=5000 token=t pid=9\n$burst") }

        assertEquals(5000 to 9L, launcher.awaitHandshake(process, "Ubuntu"))
        assertTrue(!process.destroyed)
    }

    @Test
    fun `output ending before the handshake fails at once with the log tail`() = runBlocking {
        val process = ScriptedProcess()
        val launcher = WslProcessLauncher(startupTimeoutMs = 60_000L)
        process.emit("Error: no java\n")
        process.destroy()

        val elapsed = measureTime {
            val error = assertFailsWith<IllegalStateException> { launcher.awaitHandshake(process, "Ubuntu") }
            assertTrue("Error: no java" in error.message.orEmpty(), error.message)
        }
        assertTrue(elapsed.inWholeMilliseconds < 5_000, "Must not wait for the deadline: took $elapsed")
    }
}
