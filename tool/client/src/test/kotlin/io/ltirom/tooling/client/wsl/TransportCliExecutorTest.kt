package io.ltirom.tooling.client.wsl

import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TransportCliExecutorTest {

    private class RecordingRemoteTransport(
        var responseToReturn: ToolExecutionResponse = ToolExecutionResponse(
            exitCode = 0,
            stdout = "success",
            stderr = "",
            durationMs = 100L,
        ),
        var shouldThrow: Boolean = false,
        /** Stand-in for a long download: the call only ends by cancellation. */
        var blocks: kotlinx.coroutines.CompletableDeferred<Unit>? = null,
    ) : RemoteTransportPort {
        val capturedRequests = mutableListOf<ToolExecutionRequest>()

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse {
            capturedRequests.add(request)
            blocks?.let {
                it.complete(Unit)
                kotlinx.coroutines.awaitCancellation()
            }
            if (shouldThrow) {
                error("Transport connection dropped")
            }
            return responseToReturn
        }

        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()
        override suspend fun checkHealth(): WslServerInfo? = null
        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(emptyList())
        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(emptyList())
        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true
        override suspend fun downloadFile(remotePath: String): ByteArray? = null
        override suspend fun shutdown(): Boolean = true
    }

    @Test
    fun `execute transforms command into ToolExecutionRequest with absolute workingDirectory and timeout`() {
        val transport = RecordingRemoteTransport()
        val executor = TransportCliExecutor(transport = transport, defaultWorkingDirectory = "/home/lti")

        val result = executor.execute(
            distro = "Ubuntu",
            command = listOf("find", "/bin", "-type", "f"),
            timeoutSeconds = 15,
        )

        assertEquals(1, transport.capturedRequests.size)
        val request = transport.capturedRequests.first()
        assertEquals("find", request.toolId)
        assertEquals(listOf("/bin", "-type", "f"), request.arguments)
        assertEquals("/home/lti", request.workingDirectory)
        assertEquals(15_000L, request.timeoutMs)
        assertEquals(io.ltirom.tooling.core.remote.RunPurpose.SETUP, request.purpose)
        assertEquals(0, result.exitCode)
        assertEquals("success", result.output)
        assertEquals("", result.error)
    }

    @Test
    fun `execute as root prefixes command with sudo -n`() {
        val transport = RecordingRemoteTransport()
        val executor = TransportCliExecutor(transport = transport, defaultWorkingDirectory = "/home/lti")

        executor.execute(
            distro = "Ubuntu",
            command = listOf("apt-get", "install", "-y", "brotli"),
            user = "root",
        )

        assertEquals(1, transport.capturedRequests.size)
        val request = transport.capturedRequests.first()
        assertEquals("sudo", request.toolId)
        assertEquals(listOf("-n", "apt-get", "install", "-y", "brotli"), request.arguments)
    }

    @Test
    fun `execute as root with command already starting with sudo does not duplicate sudo`() {
        val transport = RecordingRemoteTransport()
        val executor = TransportCliExecutor(transport = transport, defaultWorkingDirectory = "/home/lti")

        executor.execute(
            distro = "Ubuntu",
            command = listOf("sudo", "apt-get", "update"),
            user = "root",
        )

        assertEquals(1, transport.capturedRequests.size)
        val request = transport.capturedRequests.first()
        assertEquals("sudo", request.toolId)
        assertEquals(listOf("-n", "apt-get", "update"), request.arguments)
    }

    @Test
    fun `execute maps non-zero exit code and stderr to CliExecutionResult`() {
        val transport = RecordingRemoteTransport(
            responseToReturn = ToolExecutionResponse(
                exitCode = 127,
                stdout = "",
                stderr = "command not found",
                durationMs = 20L,
            ),
        )
        val executor = TransportCliExecutor(transport = transport)

        val result = executor.execute(
            distro = "Ubuntu",
            command = listOf("missing-tool"),
        )

        assertEquals(127, result.exitCode)
        assertEquals("", result.output)
        assertEquals("command not found", result.error)
    }

    @Test
    fun `execute rejects bash or sh with -c with IllegalArgumentException`() {
        val transport = RecordingRemoteTransport()
        val executor = TransportCliExecutor(transport = transport)

        assertFailsWith<IllegalArgumentException> {
            executor.execute(
                distro = "Ubuntu",
                command = listOf("bash", "-c", "echo hello"),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            executor.execute(
                distro = "Ubuntu",
                command = listOf("sh", "-c", "echo hello"),
            )
        }
    }

    @Test
    fun `execute handles transport failure gracefully and returns exit code -1`() {
        val transport = RecordingRemoteTransport(shouldThrow = true)
        val executor = TransportCliExecutor(transport = transport)

        val result = executor.execute(
            distro = "Ubuntu",
            command = listOf("some-cmd"),
        )

        assertEquals(-1, result.exitCode)
        assertTrue(result.error.contains("Transport connection dropped"))
    }

    @Test
    fun `run is cancelled with its caller instead of returning a result`() = kotlinx.coroutines.runBlocking {
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val executor = TransportCliExecutor(RecordingRemoteTransport(blocks = started))
        var returned: CliExecutionResult? = null

        val job = launch { returned = executor.run("Ubuntu", listOf("curl", "-o", "x", "https://example")) }
        started.await()
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(null, returned, "a cancelled command must not look like a finished one (exit -1)")
    }
}
