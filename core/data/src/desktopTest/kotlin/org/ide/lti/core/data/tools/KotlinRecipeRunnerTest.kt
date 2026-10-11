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
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ToolOutput
import org.ide.lti.core.domain.setup.ToolOutputKind
import org.ide.lti.core.domain.setup.ToolProbe
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.model.setup.ToolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val COMMIT = "0123456789abcdef0123456789abcdef01234567"

class KotlinRecipeRunnerTest {
    private class RecordingTransport : RemoteTransportPort {
        val requests = mutableListOf<ToolExecutionRequest>()
        val streamRequests = mutableListOf<ToolExecutionRequest>()

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse {
            requests += request
            return ToolExecutionResponse(0, "", "", 1L)
        }

        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> {
            streamRequests += request
            return flowOf(StreamEvent.ExecutionFinished(0, 1L))
        }

        override suspend fun checkHealth(): WslServerInfo? = null

        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(emptyList())

        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(emptyList())

        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true

        override suspend fun downloadFile(remotePath: String): ByteArray? = null

        override suspend fun shutdown(): Boolean = true
    }

    @Test
    fun `native and jar recipes quote complete destinations once`() = runTest {
        val recipes =
            listOf(
                RecipeConfig.AndroidTools(patchVendor = false),
                RecipeConfig.CMake(),
                RecipeConfig.GradleJar(task = "jar", outputPath = "build/libs/tool.jar"),
            )
        for (recipe in recipes) {
            val transport = RecordingTransport()
            val destination = "/home/a b/o'ut/tool"
            val result =
                KotlinRecipeRunner(transport).executeRecipe(
                    srcDir = "/home/a b/s'rc",
                    outDir = "/home/a b/o'ut",
                    source = ResolvedSource.Git("https://example.test/tool", ToolRef.Commit(COMMIT), COMMIT),
                    recipe = recipe,
                    outputs =
                    listOf(
                        ToolOutput("tool", "tool", ToolOutputKind.NATIVE, ToolProbe(emptyList())),
                    ),
                    supportFiles = emptyList(),
                    onLog = {},
                )
            assertTrue(result.isSuccess)
            val script = transport.requests.last { it.toolId == "sh" }.arguments.last()
            assertTrue(script.contains(KotlinStoreLayout.shellQuote(destination)))
            assertFalse(script.contains("/o'ut/'tool'\""))
        }
    }

    @Test
    fun `androidTools passes expected cmake arguments and git identity env without git apply`() = runTest {
        val transport = RecordingTransport()
        val recipe =
            RecipeConfig.AndroidTools(
                cmakeArgs = listOf("-DEXTRA_FLAG=ON"),
                gitIdentity = true,
                patchVendor = true,
            )
        val result =
            KotlinRecipeRunner(transport).executeRecipe(
                srcDir = "/home/user/src",
                outDir = "/home/user/out",
                source = ResolvedSource.Git("https://example.test/android-tools", ToolRef.Commit(COMMIT), COMMIT),
                recipe = recipe,
                outputs =
                listOf(
                    ToolOutput("adb", "adb", ToolOutputKind.NATIVE, ToolProbe(emptyList())),
                ),
                supportFiles = emptyList(),
                onLog = {},
            )
        assertTrue(result.isSuccess)

        // Verify cmake configure request
        val cmakeReq = transport.requests.first { it.toolId == "cmake" }
        val expectedArgs =
            listOf(
                "-S", "/home/user/src",
                "-B", "/home/user/src/build",
                "-DCMAKE_BUILD_TYPE=Release",
                "-DCMAKE_C_COMPILER=clang",
                "-DCMAKE_CXX_COMPILER=clang++",
                "-DANDROID_TOOLS_USE_BUNDLED_FMT=ON",
                "-DANDROID_TOOLS_USE_BUNDLED_LIBUSB=ON",
                "-DANDROID_TOOLS_PATCH_VENDOR=ON",
                "-DEXTRA_FLAG=ON",
            )
        assertEquals(expectedArgs, cmakeReq.arguments)
        assertEquals("LtiRom Studio", cmakeReq.environment["GIT_COMMITTER_NAME"])
        assertEquals("studio@ltirom.local", cmakeReq.environment["GIT_COMMITTER_EMAIL"])
        assertEquals("LtiRom Studio", cmakeReq.environment["GIT_AUTHOR_NAME"])
        assertEquals("studio@ltirom.local", cmakeReq.environment["GIT_AUTHOR_EMAIL"])

        // Verify cmake build ran as stream with -j$(nproc) and git env
        val buildStreamReq = transport.streamRequests.first()
        val buildScript = buildStreamReq.arguments.last()
        assertTrue(buildScript.contains("cmake --build '/home/user/src/build' -j\$(nproc)"))
        assertEquals("LtiRom Studio", buildStreamReq.environment["GIT_COMMITTER_NAME"])

        // Verify no git config or git apply commands ran
        for (req in transport.requests) {
            val script = req.arguments.joinToString(" ")
            assertFalse(script.contains("git apply"), "git apply must not be executed")
            assertFalse(script.contains("git config"), "git config must not be executed")
        }
    }

    @Test
    fun `androidTools respects patchVendor false and gitIdentity false`() = runTest {
        val transport = RecordingTransport()
        val recipe =
            RecipeConfig.AndroidTools(
                gitIdentity = false,
                patchVendor = false,
            )
        val result =
            KotlinRecipeRunner(transport).executeRecipe(
                srcDir = "/home/user/src",
                outDir = "/home/user/out",
                source = ResolvedSource.Git("https://example.test/android-tools", ToolRef.Commit(COMMIT), COMMIT),
                recipe = recipe,
                outputs =
                listOf(
                    ToolOutput("adb", "adb", ToolOutputKind.NATIVE, ToolProbe(emptyList())),
                ),
                supportFiles = emptyList(),
                onLog = {},
            )
        assertTrue(result.isSuccess)

        val cmakeReq = transport.requests.first { it.toolId == "cmake" }
        assertTrue(cmakeReq.arguments.contains("-DANDROID_TOOLS_PATCH_VENDOR=OFF"))
        assertTrue(cmakeReq.environment.isEmpty(), "git env must be empty when gitIdentity = false")

        val buildStreamReq = transport.streamRequests.first()
        val gitKeys = buildStreamReq.environment.filterKeys { it.startsWith("GIT_") }
        assertTrue(gitKeys.isEmpty(), "git env must not be present in stream")
    }

    @Test
    fun `androidTools output harvesting discovers aliases and copies supportFiles`() = runTest {
        val transport = RecordingTransport()
        val recipe = RecipeConfig.AndroidTools(patchVendor = false)
        val outputs =
            listOf(
                ToolOutput(
                    toolId = "mke2fs",
                    file = "mke2fs",
                    kind = ToolOutputKind.NATIVE,
                    probe = ToolProbe(emptyList()),
                    aliases = mapOf("mke2fs.android" to "mke2fs"),
                ),
            )
        val result =
            KotlinRecipeRunner(transport).executeRecipe(
                srcDir = "/home/user/src",
                outDir = "/home/user/out",
                source = ResolvedSource.Git("https://example.test/android-tools", ToolRef.Commit(COMMIT), COMMIT),
                recipe = recipe,
                outputs = outputs,
                supportFiles = listOf("gki/"),
                onLog = {},
            )
        assertTrue(result.isSuccess)

        // Find the copy script for mke2fs
        val copyScripts = transport.requests.filter { it.toolId == "sh" }.map { it.arguments.last() }
        val mke2fsScript = copyScripts.first { it.contains("mke2fs") }
        assertTrue(mke2fsScript.contains("mke2fs.android"), "mke2fs script must include candidate mke2fs.android")
        assertTrue(mke2fsScript.contains("chmod 755 '/home/user/out/mke2fs'"))

        // Find support files script
        val suppScript = copyScripts.first { it.contains("gki") }
        assertTrue(suppScript.contains("cp -r '/home/user/src/gki' '/home/user/out/gki'"))
    }

    @Test
    fun `build errors containing not found are reported as BuildFailed, not MissingPackages`() = runTest {
        val transport =
            object : RemoteTransportPort {
                override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse {
                    if (request.toolId == "cmake") {
                        return ToolExecutionResponse(
                            exitCode = 1,
                            stdout = "",
                            stderr =
                            "CMake Error at CMakeLists.txt:5: Could NOT find ZLIB (missing: ZLIB_LIBRARY)\n" +
                                "fatal error: zlib.h: No such file or directory",
                            durationMs = 100L,
                        )
                    }
                    return ToolExecutionResponse(0, "", "", 1L)
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

        val runner = KotlinRecipeRunner(transport)
        val res =
            runner.executeRecipe(
                srcDir = "/tmp/src",
                outDir = "/tmp/out",
                source = ResolvedSource.Git("https://example.test/tool", ToolRef.Commit(COMMIT), COMMIT),
                recipe = RecipeConfig.CMake(),
                outputs =
                listOf(
                    ToolOutput("tool", "tool", ToolOutputKind.NATIVE, ToolProbe(emptyList())),
                ),
                supportFiles = emptyList(),
                onLog = {},
            )

        assertTrue(res.isFailure)
        val exception = res.exceptionOrNull()
        kotlin.test.assertIs<ToolErrorException>(exception)
        kotlin.test.assertIs<ToolError.BuildFailed>(
            exception.toolError,
            "Ordinary build errors must be BuildFailed, not MissingPackages",
        )
    }
}
