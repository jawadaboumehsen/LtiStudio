package org.ide.lti.core.data.remote

import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.ToolStatusInfo
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.ide.lti.core.domain.ports.ToolRegistryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ToolPublicationAdapterTest {

    private class FakeRemoteTransport : RemoteTransportPort {
        val executedRequests = mutableListOf<ToolExecutionRequest>()
        val uploadedFiles = mutableMapOf<String, ByteArray>()
        var refreshToolsCallCount = 0
        var availableToolsList = listOf<ToolStatusInfo>()
        var findOutput: String = ""
        var findExitCode: Int = 0

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse {
            executedRequests.add(request)
            return if (request.toolId == "find") {
                ToolExecutionResponse(
                    exitCode = findExitCode,
                    stdout = findOutput,
                    stderr = "",
                    durationMs = 5L
                )
            } else {
                ToolExecutionResponse(exitCode = 0, stdout = "", stderr = "", durationMs = 5L)
            }
        }

        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()

        override suspend fun checkHealth(): WslServerInfo? = null

        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(availableToolsList)

        override suspend fun refreshTools(): ToolListResult {
            refreshToolsCallCount++
            return ToolListResult.Tools(availableToolsList)
        }

        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean {
            uploadedFiles[remotePath] = content
            return true
        }

        override suspend fun downloadFile(remotePath: String): ByteArray? = uploadedFiles[remotePath]

        override suspend fun shutdown(): Boolean = true
    }

    @Test
    fun testPublishDiscoversBinariesAndUploadsManifestsAndCallsRefreshOnce() = runTest {
        val fakeTransport = FakeRemoteTransport()
        val binDir = "/home/testuser/LtiRomTools/bin"
        fakeTransport.findOutput = listOf(
            "$binDir/lpmake",
            "$binDir/unpack_bootimg",
            "$binDir/signapk.jar"
        ).joinToString("\n")

        val expectedTools = setOf("lpmake", "unpack_bootimg", "signapk", "img2sdat")
        val adapter = ToolPublicationAdapter(fakeTransport, expectedTools)

        val result = adapter.publish(binDir, expectedTools)

        assertEquals(1, fakeTransport.refreshToolsCallCount, "refreshTools must be called exactly once")
        assertEquals(setOf("lpmake", "unpack_bootimg", "signapk"), result.registered)
        assertEquals(setOf("img2sdat"), result.failed.keys)

        // Verify manifests written to ~/.ltirom/tools.d/<id>.json
        assertEquals(3, fakeTransport.uploadedFiles.size)
        assertTrue(fakeTransport.uploadedFiles.containsKey("~/.ltirom/tools.d/lpmake.json"))
        assertTrue(fakeTransport.uploadedFiles.containsKey("~/.ltirom/tools.d/unpack_bootimg.json"))
        assertTrue(fakeTransport.uploadedFiles.containsKey("~/.ltirom/tools.d/signapk.json"))

        // Verify signapk manifest points to signapk.jar
        val signapkContent = fakeTransport.uploadedFiles["~/.ltirom/tools.d/signapk.json"]!!.decodeToString()
        val signapkJson = Json.parseToJsonElement(signapkContent).jsonObject
        assertEquals("signapk", signapkJson["name"]?.jsonPrimitive?.content)
        assertEquals("$binDir/signapk.jar", signapkJson["executable"]?.jsonPrimitive?.content)

        // Verify lpmake manifest
        val lpmakeContent = fakeTransport.uploadedFiles["~/.ltirom/tools.d/lpmake.json"]!!.decodeToString()
        val lpmakeJson = Json.parseToJsonElement(lpmakeContent).jsonObject
        assertEquals("lpmake", lpmakeJson["name"]?.jsonPrimitive?.content)
        assertEquals("$binDir/lpmake", lpmakeJson["executable"]?.jsonPrimitive?.content)
    }

    @Test
    fun testPublishWithMissingBinariesProducesNoManifests() = runTest {
        val fakeTransport = FakeRemoteTransport()
        val binDir = "/home/testuser/LtiRomTools/bin"
        fakeTransport.findOutput = ""

        val expectedTools = setOf("lpmake", "signapk")
        val adapter = ToolPublicationAdapter(fakeTransport, expectedTools)

        val result = adapter.publish(binDir, expectedTools)

        assertEquals(1, fakeTransport.refreshToolsCallCount)
        assertTrue(result.registered.isEmpty())
        assertEquals(expectedTools, result.failed.keys)
        assertTrue(fakeTransport.uploadedFiles.isEmpty())
    }

    @Test
    fun testResolvedToolsFiltersInstalled() = runTest {
        val fakeTransport = FakeRemoteTransport()
        fakeTransport.availableToolsList = listOf(
            ToolStatusInfo(tool = "lpmake", installed = true, path = "/bin/lpmake"),
            ToolStatusInfo(tool = "signapk", installed = true, path = "/bin/signapk.jar"),
            ToolStatusInfo(tool = "missing_tool", installed = false, path = "")
        )

        val adapter = ToolPublicationAdapter(fakeTransport)
        val resolved = adapter.resolvedTools()

        assertIs<ToolRegistryResult.Tools>(resolved)
        assertEquals(setOf("lpmake", "signapk"), resolved.list.filter { it.installed }.map { it.tool }.toSet())
    }
}
