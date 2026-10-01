/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
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
        val deletedFiles = mutableListOf<String>()
        var refreshToolsCallCount = 0
        var availableToolsList = listOf<ToolStatusInfo>()
        var existingTestPaths = mutableSetOf<String>()
        var toolsDManifestFiles = mutableMapOf<String, String>()

        /** Order of registry refreshes and removals. */
        val calls = mutableListOf<String>()

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse {
            executedRequests.add(request)
            return when (request.toolId) {
                "test" -> {
                    // e.g. test -x <path> or test -r <path>
                    val path = request.arguments.lastOrNull() ?: ""
                    val exists = path in existingTestPaths
                    ToolExecutionResponse(
                        exitCode = if (exists) 0 else 1,
                        stdout = "",
                        stderr = if (exists) "" else "File not found or permission denied",
                        durationMs = 1L,
                    )
                }
                "rm" -> {
                    calls += "rm"
                    val paths = request.arguments.filter { it != "-f" }
                    deletedFiles.addAll(paths)
                    paths.forEach { toolsDManifestFiles.remove(it) }
                    ToolExecutionResponse(exitCode = 0, stdout = "", stderr = "", durationMs = 1L)
                }
                "sh" -> {
                    // The manifest listing: `path<TAB>one-line json` per manifest.
                    val listing = toolsDManifestFiles.entries.joinToString("\n") { (path, text) ->
                        "$path\t${text.replace("\n", "")}"
                    }
                    ToolExecutionResponse(exitCode = 0, stdout = listing, stderr = "", durationMs = 1L)
                }
                else -> ToolExecutionResponse(exitCode = 0, stdout = "", stderr = "", durationMs = 1L)
            }
        }

        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()

        override suspend fun checkHealth(): WslServerInfo? = null

        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(availableToolsList)

        override suspend fun refreshTools(): ToolListResult {
            refreshToolsCallCount++
            calls += "refresh"
            return ToolListResult.Tools(availableToolsList)
        }

        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean {
            uploadedFiles[remotePath] = content
            toolsDManifestFiles[remotePath] = content.decodeToString()
            return true
        }

        override suspend fun downloadFile(remotePath: String): ByteArray? =
            toolsDManifestFiles[remotePath]?.encodeToByteArray() ?: uploadedFiles[remotePath]

        override suspend fun shutdown(): Boolean = true
    }

    @Test
    fun testErofsfuseSymlinkIsRegistered() = runTest {
        val fakeTransport = FakeRemoteTransport()
        val binDir = "/home/testuser/LtiRomTools/bin"
        // test -x on symlink resolves target and succeeds
        fakeTransport.existingTestPaths.add("$binDir/erofsfuse")
        fakeTransport.availableToolsList = listOf(
            ToolStatusInfo(
                tool = "erofsfuse",
                installed = true,
                path = "$binDir/erofsfuse",
                source = "DYNAMIC",
            ),
        )

        val adapter = ToolPublicationAdapter(fakeTransport)
        val report = adapter.publish(binDir, setOf("erofsfuse"))

        assertEquals(1, fakeTransport.refreshToolsCallCount)
        assertEquals(setOf("erofsfuse"), report.registered)
        assertTrue(report.failed.isEmpty(), "erofsfuse should succeed: ${report.failed}")
        assertTrue(fakeTransport.uploadedFiles.containsKey("~/.ltirom/tools.d/erofsfuse.json"))

        val content = fakeTransport.uploadedFiles["~/.ltirom/tools.d/erofsfuse.json"]!!.decodeToString()
        val json = Json.parseToJsonElement(content).jsonObject
        assertEquals("erofsfuse", json["name"]?.jsonPrimitive?.content)
        assertEquals("$binDir/erofsfuse", json["executable"]?.jsonPrimitive?.content)
        assertEquals("Published binary for erofsfuse", json["description"]?.jsonPrimitive?.content)
    }

    @Test
    fun testSignapkJarReadableNotExecutableIsRegistered() = runTest {
        val fakeTransport = FakeRemoteTransport()
        val binDir = "/home/testuser/LtiRomTools/bin"
        // signapk.jar is readable via test -r (not executable)
        fakeTransport.existingTestPaths.add("$binDir/signapk.jar")
        fakeTransport.availableToolsList = listOf(
            ToolStatusInfo(
                tool = "signapk",
                installed = true,
                path = "$binDir/signapk.jar",
                source = "DYNAMIC",
            ),
        )

        val adapter = ToolPublicationAdapter(fakeTransport)
        val report = adapter.publish(binDir, setOf("signapk"))

        assertEquals(setOf("signapk"), report.registered)
        assertTrue(report.failed.isEmpty())
        assertTrue(fakeTransport.uploadedFiles.containsKey("~/.ltirom/tools.d/signapk.json"))

        val content = fakeTransport.uploadedFiles["~/.ltirom/tools.d/signapk.json"]!!.decodeToString()
        val json = Json.parseToJsonElement(content).jsonObject
        assertEquals("signapk", json["name"]?.jsonPrimitive?.content)
        assertEquals("$binDir/signapk.jar", json["executable"]?.jsonPrimitive?.content)
    }

    @Test
    fun testNonCatalogFilesAreNeverRegisteredAndOnlyRequestedIdsAreWritten() = runTest {
        val fakeTransport = FakeRemoteTransport()
        val binDir = "/home/testuser/LtiRomTools/bin"
        // binDir contains junk files and lpmake
        fakeTransport.existingTestPaths.addAll(
            listOf(
                "$binDir/LICENSE",
                "$binDir/Makefile",
                "$binDir/liblibbase.a",
                "$binDir/message.pb.h",
                "$binDir/lpmake",
            ),
        )
        fakeTransport.availableToolsList = listOf(
            ToolStatusInfo(tool = "lpmake", installed = true, path = "$binDir/lpmake", source = "DYNAMIC"),
        )

        val adapter = ToolPublicationAdapter(fakeTransport)
        // Request only lpmake
        val report = adapter.publish(binDir, setOf("lpmake"))

        assertEquals(setOf("lpmake"), report.registered)
        // Only lpmake.json was written
        assertEquals(setOf("~/.ltirom/tools.d/lpmake.json"), fakeTransport.uploadedFiles.keys)
    }

    @Test
    fun testResolvedToolsWithSourceSystemPathGivesFailedResolvedElsewhere() = runTest {
        val fakeTransport = FakeRemoteTransport()
        val binDir = "/home/testuser/LtiRomTools/bin"
        fakeTransport.existingTestPaths.add("$binDir/adb")
        // Server reports adb was resolved from system PATH instead of our dynamic manifest
        fakeTransport.availableToolsList = listOf(
            ToolStatusInfo(
                tool = "adb",
                installed = true,
                path = "/usr/bin/adb",
                source = "SYSTEM_PATH",
            ),
        )

        val adapter = ToolPublicationAdapter(fakeTransport)
        val report = adapter.publish(binDir, setOf("adb"))

        assertTrue("adb" !in report.registered, "adb resolved from system PATH must not count as registered")
        assertEquals(
            "resolved elsewhere: /usr/bin/adb",
            report.failed["adb"],
            "failed reason must indicate tool was resolved elsewhere",
        )
    }

    @Test
    fun testPruningRemovesOnlyPublishedBinaryManifestsForNonCatalogToolsUnderBinDir() = runTest {
        val fakeTransport = FakeRemoteTransport()
        val binDir = "/home/testuser/LtiRomTools/bin"
        fakeTransport.existingTestPaths.add("$binDir/lpmake")
        fakeTransport.availableToolsList = listOf(
            ToolStatusInfo(tool = "lpmake", installed = true, path = "$binDir/lpmake", source = "DYNAMIC"),
        )

        // Seed existing manifests in ~/.ltirom/tools.d/
        // 1. App-published non-catalog tool under binDir -> MUST BE PRUNED
        fakeTransport.toolsDManifestFiles["~/.ltirom/tools.d/old_junk.json"] = """
            {"name":"old_junk","executable":"$binDir/old_junk","description":"Published binary for old_junk"}
        """.trimIndent()

        // 2. User manifest (different description) -> MUST BE KEPT
        fakeTransport.toolsDManifestFiles["~/.ltirom/tools.d/custom_user.json"] = """
            {"name":"custom_user","executable":"$binDir/custom_user","description":"My custom manifest"}
        """.trimIndent()

        // 3. App-published tool under different directory -> MUST BE KEPT
        fakeTransport.toolsDManifestFiles["~/.ltirom/tools.d/other_dir.json"] = """
            {"name":"other_dir","executable":"/usr/local/bin/other","description":"Published binary for other_dir"}
        """.trimIndent()

        // 4. Catalog tool -> MUST BE KEPT
        fakeTransport.toolsDManifestFiles["~/.ltirom/tools.d/lpmake.json"] = """
            {"name":"lpmake","executable":"$binDir/lpmake","description":"Published binary for lpmake"}
        """.trimIndent()

        val adapter = ToolPublicationAdapter(fakeTransport)
        val report = adapter.publish(binDir, setOf("lpmake"))

        assertEquals(setOf("old_junk"), report.pruned, "Only old_junk should be pruned")
        assertTrue(
            fakeTransport.deletedFiles.any { it.contains("old_junk.json") },
            "old_junk.json must have been deleted",
        )
        assertTrue(
            fakeTransport.deletedFiles.none { it.contains("custom_user.json") },
            "custom_user.json must NOT be deleted",
        )
        assertTrue(
            fakeTransport.deletedFiles.none { it.contains("other_dir.json") },
            "other_dir.json must NOT be deleted",
        )
        // New manifests go live before the cleanup, so an interrupted prune can't leave them unloaded;
        // all stale manifests go in one removal.
        assertEquals(listOf("refresh", "rm"), fakeTransport.calls)
    }

    @Test
    fun testResolvedToolsMapsToDomainResult() = runTest {
        val fakeTransport = FakeRemoteTransport()
        fakeTransport.availableToolsList = listOf(
            ToolStatusInfo(tool = "lpmake", installed = true, path = "/bin/lpmake", source = "DYNAMIC"),
            ToolStatusInfo(tool = "adb", installed = true, path = "/usr/bin/adb", source = "SYSTEM_PATH"),
        )

        val adapter = ToolPublicationAdapter(fakeTransport)
        val resolved = adapter.resolvedTools()

        assertIs<ToolRegistryResult.Tools>(resolved)
        assertEquals(1, fakeTransport.refreshToolsCallCount, "A check rescans the manifests, never a cached list")
        assertEquals(2, resolved.list.size)
        val lpmake = resolved.list.first { it.tool == "lpmake" }
        assertEquals(true, lpmake.installed)
    }

    @Test
    fun testNoLiteralTildeReachesShellLessCommands() = runTest {
        val fakeTransport = FakeRemoteTransport()
        ToolPublicationAdapter(fakeTransport).publish("/home/dev/LtiRomTools/bin", setOf("lpmake"))

        // The service runs tools without a shell: only an `sh -c` script may rely on home expansion.
        val shellLess = fakeTransport.executedRequests.filter { it.toolId != "sh" }
        assertTrue(shellLess.none { req -> req.arguments.any { it.startsWith("~") } }, "$shellLess")
    }
}
