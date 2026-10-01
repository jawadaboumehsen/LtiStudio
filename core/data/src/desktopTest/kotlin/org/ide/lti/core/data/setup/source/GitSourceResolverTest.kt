package org.ide.lti.core.data.setup.source

import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.setup.install.GitCache
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ports.ResolveOutcome
import org.ide.lti.core.model.setup.ToolRef
import org.ide.lti.core.model.setup.ToolSelection
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GitSourceResolverTest {

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
        tempDir = Files.createTempDirectory("git-resolver-test").toFile()
        fakeCli = FakeWslCliExecutor()
        gitCache = GitCache(tempDir.absolutePath.replace('\\', '/'), fakeCli)
        resolver = GitSourceResolver(fakeCli, gitCache, distro = "Ubuntu")
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `listRefs parses tags and branches from ls-remote`() = runTest {
        val lsRemoteOutput = """
            9fce389148d88e6e58f0004b3cfb9b8ba1234567	refs/heads/master
            a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2	refs/heads/develop
            1111222233334444555566667777888899990000	refs/tags/v1.0.0
            2222333344445555666677778888999900001111	refs/tags/v1.0.0^{}
            5555666677778888999900001111222233334444	refs/tags/v1.1.0
        """.trimIndent()

        fakeCli.responseProvider = { cmd ->
            if (cmd.any { it.contains("ls-remote") }) {
                CliExecutionResult(0, lsRemoteOutput, "")
            } else {
                CliExecutionResult(0, "", "")
            }
        }

        val refListing = resolver.listRefs(
            group = ToolGroupId("android-tools"),
            repoUrl = "https://github.com/example/repo.git",
        )

        assertEquals(listOf("v1.0.0", "v1.1.0"), refListing.tags)
        assertEquals(listOf("develop", "master"), refListing.branches)
    }

    @Test
    fun `resolve tag uses peeled commit sha for annotated tag`() = runTest {
        val lsRemoteOutput = """
            1111222233334444555566667777888899990000	refs/tags/v1.0.0
            2222333344445555666677778888999900001111	refs/tags/v1.0.0^{}
        """.trimIndent()

        fakeCli.responseProvider = { cmd ->
            if (cmd.any { it.contains("ls-remote") }) {
                CliExecutionResult(0, lsRemoteOutput, "")
            } else {
                CliExecutionResult(0, "", "")
            }
        }

        val selection = ToolSelection(
            group = "android-tools",
            repoUrl = "https://github.com/example/repo.git",
            ref = ToolRef.Tag("v1.0.0"),
        )

        val outcome = resolver.resolve(selection)
        assertTrue(outcome is ResolveOutcome.Resolved, "Must resolve annotated tag")
        val gitInput = outcome.input as ResolvedInput.Git
        assertEquals("2222333344445555666677778888999900001111", gitInput.commit)
    }

    @Test
    fun `resolve 40-hex commit confirmed by shallow fetch`() = runTest {
        val commitSha = "abcdef1234567890abcdef1234567890abcdef12"

        fakeCli.responseProvider = { cmd ->
            CliExecutionResult(0, "", "")
        }

        val selection = ToolSelection(
            group = "android-tools",
            repoUrl = "https://github.com/example/repo.git",
            ref = ToolRef.Commit(commitSha),
        )

        val outcome = resolver.resolve(selection)
        assertTrue(outcome is ResolveOutcome.Resolved)
        val gitInput = outcome.input as ResolvedInput.Git
        assertEquals(commitSha, gitInput.commit)
    }

    @Test
    fun `resolve 40-hex commit falls back to full fetch when shallow fetch fails`() = runTest {
        val commitSha = "abcdef1234567890abcdef1234567890abcdef12"

        fakeCli.responseProvider = { cmd ->
            val cmdStr = cmd.joinToString(" ")
            when {
                cmdStr.contains("--depth 1") -> CliExecutionResult(
                    exitCode = 1,
                    output = "",
                    error = "fatal: dumb http transport does not support shallow capabilities",
                )
                cmdStr.contains("cat-file") || cmdStr.contains("rev-parse") -> CliExecutionResult(0, commitSha, "")
                else -> CliExecutionResult(0, "", "")
            }
        }

        val selection = ToolSelection(
            group = "android-tools",
            repoUrl = "https://github.com/example/repo.git",
            ref = ToolRef.Commit(commitSha),
        )

        val outcome = resolver.resolve(selection)
        assertTrue(outcome is ResolveOutcome.Resolved)
        val gitInput = outcome.input as ResolvedInput.Git
        assertEquals(commitSha, gitInput.commit)
    }

    @Test
    fun `resolve distinguishes network unreachable from not found and access denied`() = runTest {
        val networkErr = "fatal: unable to access 'https://github.com/example/repo.git/': " +
            "Could not resolve host: github.com"
        fakeCli.responseProvider = { _ ->
            CliExecutionResult(
                exitCode = 128,
                output = "",
                error = networkErr,
            )
        }

        val selection = ToolSelection(
            group = "android-tools",
            repoUrl = "https://github.com/example/repo.git",
            ref = ToolRef.Branch("main"),
        )

        val unreachableOutcome = resolver.resolve(selection)
        assertTrue(unreachableOutcome is ResolveOutcome.Unreachable, "Network error must yield Unreachable")

        // Access denied
        val authErr = "fatal: Authentication failed for 'https://github.com/example/repo.git/'"
        fakeCli.responseProvider = { _ ->
            CliExecutionResult(
                exitCode = 128,
                output = "",
                error = authErr,
            )
        }
        val deniedOutcome = resolver.resolve(selection)
        assertTrue(deniedOutcome is ResolveOutcome.AccessDenied, "Auth failure must yield AccessDenied")

        // Not found
        fakeCli.responseProvider = { _ ->
            CliExecutionResult(0, "", "") // ls-remote succeeded but ref not present
        }
        val notFoundOutcome = resolver.resolve(selection)
        assertTrue(notFoundOutcome is ResolveOutcome.NotFound, "Missing ref must yield NotFound")
    }

    @Test
    fun `credentials in URL are rejected by ToolSelection and cannot be resolved`() {
        assertFailsWith<IllegalArgumentException> {
            ToolSelection(
                group = "android-tools",
                repoUrl = "https://user:password@github.com/example/repo.git",
                ref = ToolRef.Branch("main"),
            )
        }
    }
}
