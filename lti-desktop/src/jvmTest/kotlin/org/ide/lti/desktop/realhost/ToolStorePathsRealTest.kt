package org.ide.lti.desktop.realhost

import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.OperationResult
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.domain.tools.ToolErrorException
import org.ide.lti.core.model.setup.ToolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real-host test verifying ToolStorePort.paths() contract (T081 / US5):
 * - paths() returns <base>/bin/<tool>/<readable id>/<file> and each tool's version id from real current links
 * - A required group without current -> NotFound
 */
class ToolStorePathsRealTest {
    private fun hostPath(host: RealToolHost, sub: String = ""): String {
        val h = io.ltirom.tooling.client.WslPathTranslator().toWslPath(host.tempHome.path)
        return if (sub.isBlank()) h else "$h/$sub"
    }

    @Test
    fun pathsReturnsOutputPathsAndVersionIdsFromCurrentLinks(): Unit = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repoPath = hostPath(host, "paths-tool-repo")
            val commit =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "paths-tool",
                    probeExitCode = 0,
                    tag = "v1.2.3",
                ).getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "paths-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.2.3"),
                    commit = commit,
                )

            ToolGroupCatalog.withIsolatedTestGroups(listOf(group)) {
                val req =
                    InstallRequest(
                        opId = "op-paths-install",
                        group = group.id,
                        label = "v1.2.3",
                        source =
                        ResolvedSource.Git(
                            repoUrl = repoPath,
                            ref = ToolRef.Tag("v1.2.3"),
                            commit = commit,
                        ),
                        recipe = group.recipe,
                        outputs = group.outputs,
                        select = true,
                    )

                val events = host.store.install(req).toList()
                val finish = events.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(finish, "Install must finish")
                assertIs<OperationResult.Installed>(finish.result)
                val versionId = (finish.result as OperationResult.Installed).versionId
                assertEquals("v1.2.3", versionId)

                // Call paths()
                val pathsRes = host.store.paths(ToolGroupCatalog)
                assertTrue(pathsRes.isSuccess, "paths() must succeed: ${pathsRes.exceptionOrNull()?.message}")
                val pinned = pathsRes.getOrThrow()

                // Check paths and versions
                val expectedPath = "${hostPath(host)}/LtiRomTools/bin/${group.id.value}/$versionId/paths-tool"
                assertEquals(expectedPath, pinned.paths["paths-tool"])
                assertEquals("v1.2.3", pinned.versions["paths-tool"])
            }
        }
    }

    @Test
    fun missingCurrentLinkReturnsNotFound(): Unit = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repoPath = hostPath(host, "missing-current-repo")
            val commit =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "missing-tool",
                    probeExitCode = 0,
                    tag = "v1.0.0",
                ).getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "missing-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit,
                )

            ToolGroupCatalog.withIsolatedTestGroups(listOf(group)) {
                // Install but don't select, or delete the current symlink
                val req =
                    InstallRequest(
                        opId = "op-missing-install",
                        group = group.id,
                        label = "v1.0.0",
                        source =
                        ResolvedSource.Git(
                            repoUrl = repoPath,
                            ref = ToolRef.Tag("v1.0.0"),
                            commit = commit,
                        ),
                        recipe = group.recipe,
                        outputs = group.outputs,
                        select = true,
                    )

                val events = host.store.install(req).toList()
                val finish = events.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(finish, "Install must finish")

                // Remove current symlink
                val currentLink = "${hostPath(host)}/LtiRomTools/bin/${group.id.value}/current"
                val rmReq =
                    ToolExecutionRequest(
                        toolId = "rm",
                        arguments = listOf("-f", currentLink),
                        workingDirectory = "/tmp",
                    )
                host.transport.execute(rmReq)

                // Now call paths(), which must return NotFound
                val pathsRes = host.store.paths(ToolGroupCatalog)
                assertTrue(pathsRes.isFailure, "paths() must fail when current symlink is missing")
                val ex = pathsRes.exceptionOrNull()
                val toolErr = (ex as? ToolErrorException)?.toolError ?: (ex as? ToolError)
                assertNotNull(toolErr, "Error must be a ToolError: $ex")
                assertIs<ToolError.NotFound>(toolErr, "Error must be ToolError.NotFound, got: $toolErr")
            }
        }
    }
}
