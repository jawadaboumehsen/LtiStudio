/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.recipe

import com.russhwolf.settings.MapSettings
import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.RunHandle
import io.ltirom.tooling.core.remote.RunStatus
import io.ltirom.tooling.core.remote.RunStatusValue
import io.ltirom.tooling.core.remote.SequencedStreamEvent
import io.ltirom.tooling.core.remote.StartRunRequest
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.repository.setup.ToolchainSetupRepositoryImpl
import org.ide.lti.core.data.setup.SetupJournalContext
import org.ide.lti.core.data.setup.fakeSha256
import org.ide.lti.core.datastore.ToolchainPreferencesDataSource
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.model.setup.SetupAttemptRecord
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BuildRecipeTest {

    private class TestCli(
        var missingPaths: Set<String> = emptySet(),
        var failingCommands: Set<String> = emptySet(),
        var customSha256: Map<String, String> = emptyMap(),
    ) : WslCliExecutor() {
        val executedCommands = mutableListOf<List<String>>()

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult {
            executedCommands.add(command)
            val fullCmd = command.joinToString(" ")
            if (failingCommands.any { fullCmd.contains(it) }) {
                return CliExecutionResult(1, "", "command failed: $fullCmd")
            }
            return when {
                command.first() == "sha256sum" -> {
                    val target = command.getOrNull(1).orEmpty()
                    val override = customSha256.entries.firstOrNull { target.contains(it.key) }
                    if (override != null) {
                        CliExecutionResult(0, "${override.value}  $target", "")
                    } else {
                        fakeSha256(target)
                    }
                }
                command.contains("test") -> {
                    val path = command.lastOrNull().orEmpty()
                    if (missingPaths.any { path.contains(it) }) {
                        CliExecutionResult(1, "", "file not found: $path")
                    } else {
                        CliExecutionResult(0, "", "")
                    }
                }
                else -> CliExecutionResult(0, "", "")
            }
        }
    }

    private class TestTransport : RemoteTransportPort {
        var failingTool: String? = null
        var failingStderr: List<String> = emptyList()
        val startedRuns = mutableListOf<StartRunRequest>()

        private fun fails(runId: String): Boolean =
            startedRuns[runId.removePrefix("run-").toInt() - 1].request.toolId == failingTool

        override suspend fun startRun(request: StartRunRequest): RunHandle {
            startedRuns.add(request)
            val runId = "run-${startedRuns.size}"
            return RunHandle(runId = runId, status = RunStatusValue.RUNNING, startedAtEpochMs = 1000L)
        }

        override fun attachRun(runId: String, fromSeq: Long): Flow<SequencedStreamEvent> {
            if (fails(runId)) {
                val errors = failingStderr.mapIndexed { i, line ->
                    SequencedStreamEvent(i + 1L, StreamEvent.OutputChunk(line, isError = true))
                }
                return (errors + SequencedStreamEvent(errors.size + 1L, StreamEvent.ExecutionFinished(1, 1000L)))
                    .asFlow()
            }
            return listOf(
                SequencedStreamEvent(1L, StreamEvent.OutputChunk("Build progress...")),
                SequencedStreamEvent(2L, StreamEvent.ExecutionFinished(0, 1000L)),
            ).asFlow()
        }

        override suspend fun getRun(runId: String): RunStatus = RunStatus(
            runId = runId,
            status = if (fails(runId)) RunStatusValue.FAILED else RunStatusValue.COMPLETED,
            exitCode = if (fails(runId)) 1 else 0,
            startedAtEpochMs = 1000L,
            endedAtEpochMs = 2000L,
        )

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse =
            ToolExecutionResponse(0, "", "", 1L)
        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()
        override suspend fun checkHealth(): WslServerInfo? = null
        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(emptyList())
        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(emptyList())
        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true
        override suspend fun downloadFile(remotePath: String): ByteArray? = null
        override suspend fun shutdown(): Boolean = true
    }

    private suspend fun testRepository(attemptId: String): ToolchainSetupRepositoryImpl {
        val repo = ToolchainSetupRepositoryImpl(
            ToolchainPreferencesDataSource(settings = MapSettings(), ioDispatcher = Dispatchers.Unconfined),
        )
        repo.recordAttemptAuthorized(
            SetupAttemptRecord(
                attemptId = attemptId,
                environmentKey = "Ubuntu",
                planId = "plan-test",
                planRevisionHash = "rev-test",
                planKind = "FULL_SETUP",
            ),
        ).getOrThrow()
        return repo
    }

    @Test
    fun androidToolsRecipe_buildsSuccessfully() = runTest {
        val cli = TestCli()
        val transport = TestTransport()
        val repo = testRepository("att-android-1")
        val recipe = AndroidToolsRecipe()
        val ctx = RecipeContext(
            distro = "Ubuntu",
            extDir = "/ext",
            binDir = "/bin",
            group = ToolGroupCatalog.ANDROID_TOOLS,
            cli = cli,
            repository = repo,
            transport = transport,
            journal = SetupJournalContext("Ubuntu", "att-android-1"),
            forceRebuild = true,
        )

        val result = recipe.build(ctx, ToolGroupCatalog.ANDROID_TOOLS.recipe as RecipeConfig.AndroidTools)
        assertIs<RecipeResult.Built>(result)
        assertTrue(result.outputs.isNotEmpty())
    }

    @Test
    fun androidToolsRecipe_failsWithStderrTail() = runTest {
        val transport = TestTransport().apply {
            failingTool = "cmake"
            failingStderr = listOf("CMake Error: BZip2 not found", "Stopping configuration")
        }
        val repo = testRepository("att-android-2")
        val recipe = AndroidToolsRecipe()
        val ctx = RecipeContext(
            distro = "Ubuntu",
            extDir = "/ext",
            binDir = "/bin",
            group = ToolGroupCatalog.ANDROID_TOOLS,
            cli = TestCli(),
            repository = repo,
            transport = transport,
            journal = SetupJournalContext("Ubuntu", "att-android-2"),
            forceRebuild = true,
        )

        val result = recipe.build(ctx, ToolGroupCatalog.ANDROID_TOOLS.recipe as RecipeConfig.AndroidTools)
        assertIs<RecipeResult.Failed>(result)
        assertTrue(result.stderrTail.contains("BZip2 not found") || result.reason.contains("BZip2 not found"))
    }

    @Test
    fun androidToolsRecipe_returnsUnsupportedWhenSourcesMissing() = runTest {
        val cli = TestCli(missingPaths = setOf("/ext/android-tools"))
        val recipe = AndroidToolsRecipe()
        val ctx = RecipeContext(
            distro = "Ubuntu",
            extDir = "/ext",
            binDir = "/bin",
            group = ToolGroupCatalog.ANDROID_TOOLS,
            cli = cli,
            forceRebuild = true,
        )

        val result = recipe.build(ctx, ToolGroupCatalog.ANDROID_TOOLS.recipe as RecipeConfig.AndroidTools)
        assertIs<RecipeResult.Unsupported>(result)
        assertTrue(result.missing.isNotEmpty())
    }

    @Test
    fun cmakeRecipe_buildsSuccessfully() = runTest {
        val cli = TestCli()
        val transport = TestTransport()
        val repo = testRepository("att-cmake-1")
        val recipe = CMakeRecipe()
        val ctx = RecipeContext(
            distro = "Ubuntu",
            extDir = "/ext",
            binDir = "/bin",
            group = ToolGroupCatalog.EROFS_UTILS,
            cli = cli,
            repository = repo,
            transport = transport,
            journal = SetupJournalContext("Ubuntu", "att-cmake-1"),
            forceRebuild = true,
        )

        val result = recipe.build(ctx, ToolGroupCatalog.EROFS_UTILS.recipe as RecipeConfig.CMake)
        assertIs<RecipeResult.Built>(result)
        assertTrue(result.outputs.isNotEmpty())
    }

    @Test
    fun cmakeRecipe_failsWithStderrTail() = runTest {
        val transport = TestTransport().apply {
            failingTool = "make"
            failingStderr = listOf("fatal error: 'libfuse.h' file not found", "compilation terminated.")
        }
        val repo = testRepository("att-cmake-2")
        val recipe = CMakeRecipe()
        val ctx = RecipeContext(
            distro = "Ubuntu",
            extDir = "/ext",
            binDir = "/bin",
            group = ToolGroupCatalog.EROFS_UTILS,
            cli = TestCli(),
            repository = repo,
            transport = transport,
            journal = SetupJournalContext("Ubuntu", "att-cmake-2"),
            forceRebuild = true,
        )

        val result = recipe.build(ctx, ToolGroupCatalog.EROFS_UTILS.recipe as RecipeConfig.CMake)
        assertIs<RecipeResult.Failed>(result)
        assertTrue(result.stderrTail.contains("libfuse.h") || result.reason.contains("libfuse.h"))
    }

    @Test
    fun cmakeRecipe_returnsUnsupportedWhenSourcesMissing() = runTest {
        val cli = TestCli(missingPaths = setOf("/ext/erofs-utils"))
        val recipe = CMakeRecipe()
        val ctx = RecipeContext(
            distro = "Ubuntu",
            extDir = "/ext",
            binDir = "/bin",
            group = ToolGroupCatalog.EROFS_UTILS,
            cli = cli,
            forceRebuild = true,
        )

        val result = recipe.build(ctx, ToolGroupCatalog.EROFS_UTILS.recipe as RecipeConfig.CMake)
        assertIs<RecipeResult.Unsupported>(result)
        assertTrue(result.missing.isNotEmpty())
    }

    @Test
    fun gradleJarRecipe_buildsSuccessfully() = runTest {
        val cli = TestCli()
        val transport = TestTransport()
        val repo = testRepository("att-gradle-1")
        val recipe = GradleJarRecipe()
        val ctx = RecipeContext(
            distro = "Ubuntu",
            extDir = "/ext",
            binDir = "/bin",
            group = ToolGroupCatalog.APKTOOL,
            cli = cli,
            repository = repo,
            transport = transport,
            journal = SetupJournalContext("Ubuntu", "att-gradle-1"),
            forceRebuild = true,
        )

        val result = recipe.build(ctx, ToolGroupCatalog.APKTOOL.recipe as RecipeConfig.GradleJar)
        assertIs<RecipeResult.Built>(result)
        assertTrue(result.outputs.isNotEmpty())
    }

    @Test
    fun gradleJarRecipe_failsWithStderrTail() = runTest {
        val transport = TestTransport().apply {
            failingTool = "sh"
            failingStderr = listOf("FAILURE: Build failed with an exception.", "Task :shadowJar failed")
        }
        val repo = testRepository("att-gradle-2")
        val recipe = GradleJarRecipe()
        val ctx = RecipeContext(
            distro = "Ubuntu",
            extDir = "/ext",
            binDir = "/bin",
            group = ToolGroupCatalog.APKTOOL,
            cli = TestCli(),
            repository = repo,
            transport = transport,
            journal = SetupJournalContext("Ubuntu", "att-gradle-2"),
            forceRebuild = true,
        )

        val result = recipe.build(ctx, ToolGroupCatalog.APKTOOL.recipe as RecipeConfig.GradleJar)
        assertIs<RecipeResult.Failed>(result)
        assertTrue(result.stderrTail.contains("shadowJar") || result.reason.contains("shadowJar"))
    }

    @Test
    fun gradleJarRecipe_returnsUnsupportedWhenWrapperMissing() = runTest {
        val cli = TestCli(missingPaths = setOf("/ext/apktool/gradlew"))
        val recipe = GradleJarRecipe()
        val ctx = RecipeContext(
            distro = "Ubuntu",
            extDir = "/ext",
            binDir = "/bin",
            group = ToolGroupCatalog.APKTOOL,
            cli = cli,
            forceRebuild = true,
        )

        val result = recipe.build(ctx, ToolGroupCatalog.APKTOOL.recipe as RecipeConfig.GradleJar)
        assertIs<RecipeResult.Unsupported>(result)
        assertTrue(result.missing.isNotEmpty())
    }

    @Test
    fun scriptCopyRecipe_copiesAndMarksExecutable() = runTest {
        val cli = TestCli()
        val recipe = ScriptCopyRecipe()
        val ctx = RecipeContext(
            distro = "Ubuntu",
            extDir = "/ext",
            binDir = "/bin",
            group = ToolGroupCatalog.IMG2SDAT,
            cli = cli,
            forceRebuild = true,
        )

        val result = recipe.build(ctx, ToolGroupCatalog.IMG2SDAT.recipe as RecipeConfig.ScriptCopy)
        assertIs<RecipeResult.Built>(result)
        assertTrue(cli.executedCommands.any { it.contains("cp") && it.contains("/ext/img2sdat/img2sdat") })
        assertTrue(cli.executedCommands.any { it.contains("chmod") && it.contains("+x") })
    }

    @Test
    fun scriptCopyRecipe_failsWhenCopyFails() = runTest {
        val cli = TestCli(failingCommands = setOf("cp -a /ext/img2sdat/img2sdat"))
        val recipe = ScriptCopyRecipe()
        val ctx = RecipeContext(
            distro = "Ubuntu",
            extDir = "/ext",
            binDir = "/bin",
            group = ToolGroupCatalog.IMG2SDAT,
            cli = cli,
            forceRebuild = true,
        )

        val result = recipe.build(ctx, ToolGroupCatalog.IMG2SDAT.recipe as RecipeConfig.ScriptCopy)
        assertIs<RecipeResult.Failed>(result)
    }

    @Test
    fun scriptCopyRecipe_returnsUnsupportedWhenSourcesMissing() = runTest {
        val cli = TestCli(missingPaths = setOf("/ext/img2sdat/img2sdat"))
        val recipe = ScriptCopyRecipe()
        val ctx = RecipeContext(
            distro = "Ubuntu",
            extDir = "/ext",
            binDir = "/bin",
            group = ToolGroupCatalog.IMG2SDAT,
            cli = cli,
            forceRebuild = true,
        )

        val result = recipe.build(ctx, ToolGroupCatalog.IMG2SDAT.recipe as RecipeConfig.ScriptCopy)
        assertIs<RecipeResult.Unsupported>(result)
    }

    @Test
    fun releaseDownloadRecipe_downloadsAndInstallsSuccessfully() = runTest {
        val cli = TestCli()
        val recipe = ReleaseDownloadRecipe()
        val ctx = RecipeContext(
            distro = "Ubuntu",
            extDir = "/ext",
            binDir = "/bin",
            group = ToolGroupCatalog.GH,
            cli = cli,
            forceRebuild = true,
        )

        val result = recipe.build(ctx, ToolGroupCatalog.GH.recipe as RecipeConfig.Release)
        assertIs<RecipeResult.Built>(result)
        assertTrue(cli.executedCommands.any { it.contains("curl") })
        assertTrue(cli.executedCommands.any { it.contains("tar") })
    }

    @Test
    fun releaseDownloadRecipe_failsWhenChecksumMismatches() = runTest {
        val mismatchHash = "0000000000000000000000000000000000000000000000000000000000000000"
        val cli = TestCli(
            customSha256 = mapOf("extract" to mismatchHash),
        )
        val recipe = ReleaseDownloadRecipe()
        val ctx = RecipeContext(
            distro = "Ubuntu",
            extDir = "/ext",
            binDir = "/bin",
            group = ToolGroupCatalog.GH,
            cli = cli,
            forceRebuild = true,
        )

        val result = recipe.build(ctx, ToolGroupCatalog.GH.recipe as RecipeConfig.Release)
        assertIs<RecipeResult.Failed>(result)
        assertTrue(result.reason.contains("mismatch") || result.stderrTail.contains("mismatch"))
    }

    @Test
    fun releaseDownloadRecipe_returnsUnsupportedWhenVersionListEmpty() = runTest {
        val recipe = ReleaseDownloadRecipe()
        val ctx = RecipeContext(
            distro = "Ubuntu",
            extDir = "/ext",
            binDir = "/bin",
            group = ToolGroupCatalog.GH,
            forceRebuild = true,
        )

        val result = recipe.build(ctx, RecipeConfig.Release(versions = emptyList()))
        assertIs<RecipeResult.Unsupported>(result)
    }

    @Test
    fun buildRecipeDispatcher_dispatchesExhaustively() = runTest {
        val cli = TestCli()
        val dispatcher = BuildRecipeDispatcher()
        for (group in ToolGroupCatalog.allGroups) {
            val ctx = RecipeContext(
                distro = "Ubuntu",
                extDir = "/ext",
                binDir = "/bin",
                group = group,
                cli = cli,
                forceRebuild = false,
            )
            val result = dispatcher.dispatch(ctx, group.recipe)
            // Each group dispatches and returns a valid RecipeResult (either Built or Unsupported depending on fakes)
            val isValid = result is RecipeResult.Built ||
                result is RecipeResult.Unsupported ||
                result is RecipeResult.Failed
            assertTrue(isValid)
        }
    }
}
