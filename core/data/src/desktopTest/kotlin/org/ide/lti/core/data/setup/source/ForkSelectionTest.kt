/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.source

import com.russhwolf.settings.MapSettings
import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.repository.setup.ToolchainSelectionRepositoryImpl
import org.ide.lti.core.data.setup.SubmoduleSyncEngine
import org.ide.lti.core.data.setup.install.GitCache
import org.ide.lti.core.datastore.ToolchainPreferencesDataSource
import org.ide.lti.core.domain.setup.CompatibilityResult
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.model.setup.ToolRef
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForkSelectionTest {

    private lateinit var tempDir: File
    private lateinit var fakeCli: ForkFakeCliExecutor
    private lateinit var gitCache: GitCache
    private lateinit var resolver: GitSourceResolver
    private lateinit var preferencesDataSource: ToolchainPreferencesDataSource
    private lateinit var repository: ToolchainSelectionRepositoryImpl

    class ForkFakeCliExecutor : WslCliExecutor() {
        val executedCommands = mutableListOf<List<String>>()
        var responseProvider: (List<String>) -> CliExecutionResult = {
            CliExecutionResult(0, "", "")
        }

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult {
            executedCommands.add(command)
            return responseProvider(command)
        }

        override suspend fun run(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            user: String?,
        ): CliExecutionResult = execute(distro, command, timeoutSeconds, Charsets.UTF_8, user)
    }

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("fork-selection-test").toFile()
        fakeCli = ForkFakeCliExecutor()
        gitCache = GitCache(tempDir.absolutePath.replace('\\', '/'), fakeCli)
        resolver = GitSourceResolver(fakeCli, gitCache, distro = "Ubuntu")
        preferencesDataSource = ToolchainPreferencesDataSource(settings = MapSettings())
        repository = ToolchainSelectionRepositoryImpl(preferencesDataSource)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `trust is recorded and verified per normalised URL`() = runTest {
        val rawUrl = "https://github.com/myfork/erofs-utils.git"
        repository.recordTrust(rawUrl)

        assertTrue(repository.isTrusted("https://github.com/myfork/erofs-utils.git"))
        assertTrue(repository.isTrusted("https://github.com/myfork/erofs-utils"))
        assertTrue(repository.isTrusted("https://github.com/myfork/erofs-utils/"))
        assertTrue(repository.isTrusted("HTTPS://GITHUB.COM/myfork/erofs-utils.git"))

        assertFalse(repository.isTrusted("https://github.com/other/erofs-utils.git"))
    }

    @Test
    fun `origin mismatch triggers fresh workspace clone from fork`() = runTest {
        val extDir = "/home/test/LtiRomTools/external"
        val subDir = "$extDir/erofs-utils"
        val forkUrl = "https://github.com/myfork/erofs-tools.git"
        val upstreamUrl = "https://github.com/sekaiacg/erofs-tools.git"
        val targetCommit = "1111222233334444555566667777888899990000"

        fakeCli.responseProvider = { cmd ->
            when {
                cmd.contains("test") && cmd.contains(subDir) -> CliExecutionResult(0, "", "")
                cmd.contains("rev-parse") && cmd.contains("--show-toplevel") -> CliExecutionResult(0, subDir, "")
                cmd.contains("remote") && cmd.contains("get-url") -> CliExecutionResult(0, upstreamUrl, "")
                cmd.contains("clone") -> CliExecutionResult(0, "", "")
                cmd.contains("mv") -> CliExecutionResult(0, "", "")
                cmd.contains("submodule") -> CliExecutionResult(0, "", "")
                else -> CliExecutionResult(0, "", "")
            }
        }

        val syncEngine = SubmoduleSyncEngine(cli = fakeCli)
        val submodule = SubmoduleSyncEngine.PinnedSubmodule("erofs-utils", forkUrl, targetCommit)

        val result = syncEngine.syncAll(
            distro = "Ubuntu",
            extDir = extDir,
            submodules = listOf(submodule),
        )

        assertTrue(result.isSuccess)
        val cloneCommand = fakeCli.executedCommands.firstOrNull { it.contains("clone") }
        assertTrue(cloneCommand != null, "Fresh clone must be initiated on origin mismatch")
        assertTrue(cloneCommand.contains(forkUrl), "Fresh clone must use fork URL")
    }

    @Test
    fun `unsupported layout blocks layout check with missing files`() = runTest {
        val commitSha = "0123456789abcdef0123456789abcdef01234567"
        val mirrorPath = gitCache.getMirrorPath("https://github.com/myfork/erofs-tools.git")

        fakeCli.responseProvider = { cmd ->
            if (cmd.contains("ls-tree") && cmd.contains(mirrorPath)) {
                // Return tree missing CMakeLists.txt
                CliExecutionResult(0, "README.md\nsource.c\n", "")
            } else {
                CliExecutionResult(0, "", "")
            }
        }

        val input = ResolvedInput.Git(
            group = ToolGroupId("erofs-utils"),
            repoUrl = "https://github.com/myfork/erofs-tools.git",
            ref = ToolRef.Commit(commitSha),
            commit = commitSha,
            resolvedAt = 1000L,
        )

        val compatibility = resolver.checkLayout(input)
        assertTrue(compatibility is CompatibilityResult.Unsupported)
        assertTrue(compatibility.missingPaths.isNotEmpty())
    }
}
