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
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.domain.tools.ToolErrorException
import org.ide.lti.core.model.setup.ToolRef
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ToolSettingsFileTest {
    private class Transport(var file: String?) : RemoteTransportPort {
        val scripts = mutableListOf<String>()

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse {
            val script = request.arguments.last()
            scripts += script
            val exitCode: Int
            val output: String
            if ("base64 -d" in script) {
                file = String(Base64.getDecoder().decode(script.substringAfter("echo '").substringBefore("'")))
                exitCode = 0
                output = ""
            } else {
                exitCode = if (file == null) 44 else 0
                output = file.orEmpty()
            }
            return ToolExecutionResponse(exitCode, output, "", 1L)
        }

        override fun stream(request: ToolExecutionRequest) =
            kotlinx.coroutines.flow.emptyFlow<io.ltirom.tooling.core.remote.StreamEvent>()

        override suspend fun checkHealth(): WslServerInfo? = null

        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(emptyList())

        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(emptyList())

        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true

        override suspend fun downloadFile(remotePath: String): ByteArray? = null

        override suspend fun shutdown(): Boolean = true
    }

    private fun settings(transport: Transport) = ToolSettingsFile(RemoteCommandRunner(transport), "/home/u/LtiRomTools")

    @Test
    fun `a pick that cannot be read fails the read instead of vanishing`() = runTest {
        val transport =
            Transport(
                """{"schema":1,"selections":{"erofs-utils":{"id":"v1","source":{"kind":"mystery"}}}}""",
            )

        val error = (settings(transport).readSelections().exceptionOrNull() as ToolErrorException).toolError

        assertIs<ToolError.Internal>(error)
        assertTrue("erofs-utils" in error.message, error.message)
    }

    @Test
    fun `a pick whose commit is a branch name is unreadable, not trusted`() = runTest {
        val sha = "a".repeat(40)
        val json =
            """{"schema":1,"selections":{""" +
                """"erofs-utils":{"id":"v1","source":{"kind":"git","repo":"https://x/y.git","commit":"main"}},""" +
                """"android-tools":{"id":"v2","source":{"kind":"git","repo":"https://x/z.git","commit":"$sha"}}}}"""

        val settings = settings(Transport(json)).readSettings().getOrThrow()

        assertEquals(setOf(ToolGroupId("erofs-utils")), settings.unreadable)
        assertEquals(setOf(ToolGroupId("android-tools")), settings.picks.keys)
    }

    private val damaged =
        """{"schema":1,"selections":{"erofs-utils":{"id":"v1","source":{"kind":"mystery"}},""" +
            """"gh":{"id":"2.97.0","source":{"kind":"release","version":"2.97.0",""" +
            """"url":"u","sha256":"s","member":"m"}}}}"""

    @Test
    fun `a damaged pick marks only its own group`() = runTest {
        val settings = settings(Transport(damaged)).readSettings().getOrThrow()

        assertEquals(setOf(ToolGroupId("erofs-utils")), settings.unreadable)
        assertEquals(setOf(ToolGroupId("gh")), settings.picks.keys)
    }

    @Test
    fun `choosing a version for the damaged group repairs it, and other groups stay protected`() = runTest {
        val transport = Transport(damaged)
        val file = settings(transport)
        val source = ResolvedSource.Git("https://x/y.git", ToolRef.Tag("v2"), "2".repeat(40))

        assertTrue(file.record(ToolGroupId("android-tools"), "v2", source).isFailure, "would erase erofs' entry")
        assertEquals(damaged, transport.file)

        assertTrue(file.record(ToolGroupId("erofs-utils"), "v2", source).isSuccess)
        val repaired = file.readSettings().getOrThrow()
        assertTrue(repaired.unreadable.isEmpty())
        assertEquals(setOf(ToolGroupId("erofs-utils"), ToolGroupId("gh")), repaired.picks.keys)
    }

    @Test
    fun `writing never replaces an unreadable file`() = runTest {
        val broken = """{"schema":1,"selections":{"erofs-utils":{"id":"v1","source":{"kind":"mystery"}}}}"""
        val transport = Transport(broken)
        val source = ResolvedSource.Git("https://x/y.git", ToolRef.Tag("v2"), "2".repeat(40))

        val result = settings(transport).record(ToolGroupId("gh"), "v2", source)

        assertTrue(result.isFailure)
        assertEquals(broken, transport.file, "the user's entry must survive")
    }

    @Test
    fun `two groups recorded together both keep their pick, through a per-process temp file`() = runTest {
        val transport = Transport(null)
        val file = settings(transport)
        val a = ResolvedSource.Git("https://x/a.git", ToolRef.Tag("a"), "a".repeat(40))
        val b = ResolvedSource.Git("https://x/b.git", ToolRef.Tag("b"), "b".repeat(40))

        file.record(ToolGroupId("erofs-utils"), "a", a)
        file.record(ToolGroupId("android-tools"), "b", b)

        val read = file.readSelections().getOrThrow()
        assertEquals(setOf(ToolGroupId("erofs-utils"), ToolGroupId("android-tools")), read.keys)
        assertTrue(transport.scripts.filter { "base64 -d" in it }.all { "tmp'\$\$" in it }, "unique temp name")
    }
}
