/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.tools

import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.setup.RemotePrerequisiteProbe
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.domain.tools.ToolErrorException
import org.ide.lti.core.model.setup.SetupEnvironment
import org.ide.lti.core.model.setup.ToolRef
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SourceInspectionAndProbeTest {
    private class Transport(var onExecute: (ToolExecutionRequest) -> ToolExecutionResponse) : RemoteTransportPort {
        val scripts = mutableListOf<String>()

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse {
            scripts += request.arguments.lastOrNull().orEmpty()
            return onExecute(request)
        }

        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> =
            flowOf(StreamEvent.ExecutionFinished(0, 1L))

        override suspend fun checkHealth(): WslServerInfo? = null

        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(emptyList())

        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(emptyList())

        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true

        override suspend fun downloadFile(remotePath: String): ByteArray? = null

        override suspend fun shutdown(): Boolean = true
    }

    private val commit = "c".repeat(40)

    private fun resolver(lsTree: (ToolExecutionRequest) -> ToolExecutionResponse) = Transport { request ->
        val script = request.arguments.lastOrNull().orEmpty()
        when {
            "rev-parse" in request.arguments -> ToolExecutionResponse(0, "$commit\n", "", 1L)
            "ls-tree -r" in script -> lsTree(request)
            else -> ToolExecutionResponse(0, "", "", 1L) // mirror, layout checks
        }
    }

    private fun adapter(transport: Transport) =
        KotlinToolStoreAdapter(transport = transport, serverDirProvider = { "/home/u/.ltirom/server/current" })

    private suspend fun resolveErofs(transport: Transport) =
        adapter(transport).resolve(ToolGroupCatalog.EROFS_UTILS.id, "https://example.com/erofs.git", ToolRef.Tag("v1"))

    @Test
    fun `a submodule inspection that fails fails the resolution instead of returning an empty set`() = runTest {
        val result = resolveErofs(resolver { ToolExecutionResponse(128, "", "fatal: bad object", 1L) })

        val error = (result.exceptionOrNull() as ToolErrorException).toolError
        assertIs<ToolError.Network>(error)
        assertTrue(commit in error.reason, error.reason)
    }

    @Test
    fun `a submodule inspection that works returns every gitlink with its commit`() = runTest {
        val sub = "d".repeat(40)
        val result = resolveErofs(
            resolver {
                ToolExecutionResponse(
                    0,
                    "100644 blob ${"e".repeat(40)}\ta.txt\n160000 commit $sub\tvendor/sub\n",
                    "",
                    1L,
                )
            },
        )

        assertEquals(mapOf("vendor/sub" to sub), result.getOrThrow().submodules)
        assertEquals(commit, result.getOrThrow().commit)
    }

    private val environment = SetupEnvironment("Ubuntu", 2, "ubuntu", "24.04", "u", "/home/u")

    @Test
    fun `the probe counts compressed apt indexes as package lists, and none at all as not fresh`() {
        val transport = Transport { ToolExecutionResponse(0, "PACKAGES_FRESH=1\n", "", 1L) }
        runBlocking { RemotePrerequisiteProbe(transport).probe(environment) }
        val script = transport.scripts.single()
        val lines = script.lines()
        val freshnessCheck =
            lines.take(lines.indexOfFirst { it.startsWith("echo PACKAGES_FRESH=") } + 1).joinToString("\n")
        org.junit.Assume.assumeTrue(
            "needs sh",
            runCatching {
                ProcessBuilder("sh", "-c", "true").start().waitFor() == 0
            }.getOrDefault(false),
        )

        fun freshnessWith(vararg files: String): String {
            val lists = Files.createTempDirectory("apt-lists").toFile()
            try {
                files.forEach { File(lists, it).writeText("x") }
                val local = freshnessCheck.replace("/var/lib/apt/lists", lists.absolutePath.replace('\\', '/'))
                val file = File(lists, "check.sh").also { it.writeText(local) }
                val process = ProcessBuilder(
                    "sh",
                    file.absolutePath.replace('\\', '/'),
                ).redirectErrorStream(true).start()
                return process.inputStream.bufferedReader().readText().trim().also { process.waitFor() }
            } finally {
                lists.deleteRecursively()
            }
        }

        assertEquals("PACKAGES_FRESH=1", freshnessWith("archive.ubuntu.com_dists_noble_main_binary-amd64_Packages"))
        assertEquals("PACKAGES_FRESH=1", freshnessWith("archive.ubuntu.com_dists_noble_main_binary-amd64_Packages.lz4"))
        assertEquals("PACKAGES_FRESH=1", freshnessWith("x_Packages.gz"))
        assertEquals("PACKAGES_FRESH=1", freshnessWith("x_Packages.xz"))
        assertEquals("PACKAGES_FRESH=0", freshnessWith("lock", "x_Release"))
        assertEquals("PACKAGES_FRESH=0", freshnessWith())
    }
}
