package org.ide.lti.core.data.setup.source

import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.setup.install.GitCache
import org.ide.lti.core.domain.setup.CompatibilityResult
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.ToolGroup
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.model.setup.ToolRef
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutCheckTest {

    private lateinit var tempDir: File
    private lateinit var gitCache: GitCache
    private lateinit var fakeCli: FakeWslCliExecutor
    private lateinit var resolver: GitSourceResolver

    class FakeWslCliExecutor : WslCliExecutor() {
        val executedCommands = mutableListOf<List<String>>()
        var responseProvider: (List<String>) -> CliExecutionResult = {
            CliExecutionResult(0, "", "")
        }

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: java.nio.charset.Charset,
            user: String?,
        ): CliExecutionResult {
            executedCommands.add(command)
            return responseProvider(command)
        }
    }

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("layout-check-test").toFile()
        fakeCli = FakeWslCliExecutor()
        gitCache = GitCache(tempDir.absolutePath.replace('\\', '/'), fakeCli)
        resolver = GitSourceResolver(fakeCli, gitCache, distro = "Ubuntu")
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `checkLayout returns LayoutCompatible when all required paths exist in ls-tree`() = runTest {
        val testGroup = ToolGroup(
            id = ToolGroupId("test-group"),
            source = ToolSource.Release,
            recipe = RecipeConfig.Release(emptyList()),
            layout = listOf("CMakeLists.txt", "src/main.cpp"),
            outputs = emptyList(),
            buildPackages = emptyList(),
        )

        ToolGroupCatalog.withTestGroup(testGroup) {
            fakeCli.responseProvider = { cmd ->
                if (cmd.any { it.contains("ls-tree") }) {
                    CliExecutionResult(0, "CMakeLists.txt\nsrc/main.cpp\nREADME.md", "")
                } else {
                    CliExecutionResult(0, "", "")
                }
            }

            val input = ResolvedInput.Git(
                group = ToolGroupId("test-group"),
                repoUrl = "https://github.com/example/test.git",
                ref = ToolRef.Branch("main"),
                commit = "0123456789abcdef0123456789abcdef01234567",
                resolvedAt = System.currentTimeMillis(),
            )

            val result = resolver.checkLayout(input)
            assertEquals(CompatibilityResult.LayoutCompatible, result)
        }
    }

    @Test
    fun `checkLayout returns Unsupported with missing paths when files are missing`() = runTest {
        val testGroup = ToolGroup(
            id = ToolGroupId("test-group"),
            source = ToolSource.Release,
            recipe = RecipeConfig.Release(emptyList()),
            layout = listOf("CMakeLists.txt", "vendor/CMakeLists.txt"),
            outputs = emptyList(),
            buildPackages = emptyList(),
        )

        ToolGroupCatalog.withTestGroup(testGroup) {
            fakeCli.responseProvider = { cmd ->
                if (cmd.any { it.contains("ls-tree") }) {
                    CliExecutionResult(0, "CMakeLists.txt\nsrc/main.cpp", "")
                } else {
                    CliExecutionResult(0, "", "")
                }
            }

            val input = ResolvedInput.Git(
                group = ToolGroupId("test-group"),
                repoUrl = "https://github.com/example/test.git",
                ref = ToolRef.Branch("main"),
                commit = "0123456789abcdef0123456789abcdef01234567",
                resolvedAt = System.currentTimeMillis(),
            )

            val result = resolver.checkLayout(input)
            assertTrue(result is CompatibilityResult.Unsupported)
            assertEquals(listOf("vendor/CMakeLists.txt"), result.missingPaths)
        }
    }
}
