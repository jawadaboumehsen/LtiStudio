package org.ide.lti.desktop.realhost

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.OperationResult
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.model.setup.ToolRef
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real-host test verifying reuse and intact detection (T054).
 *
 * Assertions:
 * - Reused: when version.json matches source and recipe revision, outputs are intact,
 *   and probes pass -> Reused(id). Nothing is fetched or built (no new results json files, FETCH_HEAD unchanged).
 * - A real folder without version.json is ignored by status.
 * - A truncated output marks the version not intact.
 */
class ToolStoreReuseRealTest {
    @Test
    fun intactVersionIsReusedWithNoNewResultOrFetch() = runTest {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val repoPath = host.hostPath("reuse-tool-repo")
            val commit =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "reuse-tool",
                    probeExitCode = 0,
                    tag = "v1.0.0",
                ).getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "reuse-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit,
                )

            ToolGroupCatalog.withTestGroup(group) {
                val req1 =
                    InstallRequest(
                        opId = "op-initial-build",
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

                val events1 = host.store.install(req1).toList()
                val finish1 = events1.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(finish1, "First install must finish")
                assertIs<OperationResult.Installed>(finish1.result)
                val versionId = (finish1.result as OperationResult.Installed).versionId

                val resultsDir = File(host.tempHome, "LtiRomTools/results")
                val resultsBefore = resultsDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
                assertTrue("op-initial-build.json" in resultsBefore, "Initial build must write result")

                val mirrorDir = File(host.tempHome, "LtiRomTools/src/${group.id.value}")
                val fetchHead = File(mirrorDir, "FETCH_HEAD")
                val fetchHeadModBefore = if (fetchHead.exists()) fetchHead.lastModified() else -1L

                // Second install with same source and intact version
                val req2 =
                    InstallRequest(
                        opId = "op-second-reuse",
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

                val events2 = host.store.install(req2).toList()
                val finish2 = events2.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(finish2, "Second install must finish")
                assertIs<OperationResult.Reused>(finish2.result)
                assertEquals(versionId, (finish2.result as OperationResult.Reused).versionId)

                // Nothing is fetched or built: no new results/*.json
                val resultsAfter = resultsDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
                assertEquals(resultsBefore, resultsAfter, "Reused operation must write NO new results/*.json")
                assertFalse("op-second-reuse.json" in resultsAfter, "op-second-reuse must not exist in results/")

                if (fetchHead.exists() && fetchHeadModBefore != -1L) {
                    assertEquals(fetchHeadModBefore, fetchHead.lastModified(), "FETCH_HEAD must be unchanged")
                }
            }
        }
    }

    @Test
    fun realFolderWithoutVersionJsonIsIgnoredByStatus() = runTest {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val group = ToolGroupId("apktool")
            val untracked = File(host.tempHome, "LtiRomTools/bin/${group.value}/untracked-folder")
            untracked.mkdirs()
            File(untracked, "some-binary").writeText("fake content")

            val statusRes = host.store.status(ToolGroupCatalog)
            assertTrue(statusRes.isSuccess, "store.status() must succeed")
            val inventory = statusRes.getOrThrow()
            val apktoolGroup = inventory.groups[group]
            assertNotNull(apktoolGroup, "apktool group state must exist")

            val foundIds = apktoolGroup.installed.map { it.id }
            assertFalse(
                foundIds.contains("untracked-folder"),
                "Folder without version.json must be ignored by status",
            )
        }
    }

    @Test
    fun truncatedOutputMarksVersionNotIntact() = runTest {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val repoPath = host.hostPath("truncate-tool-repo")
            val commit =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "truncate-tool",
                    probeExitCode = 0,
                    tag = "v1.0.0",
                ).getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "truncate-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit,
                )

            ToolGroupCatalog.withTestGroup(group) {
                val req =
                    InstallRequest(
                        opId = "op-truncate-test",
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
                assertNotNull(finish)
                assertIs<OperationResult.Installed>(finish.result)

                // Verify status reports intact initially
                val status1 = host.store.status(ToolGroupCatalog).getOrThrow()
                val ver1 = status1.groups[group.id]?.installed?.firstOrNull()
                assertNotNull(ver1)
                assertTrue(ver1.intact, "Version must be intact initially")

                // Truncate the installed executable to 0 bytes
                val binFile =
                    File(
                        host.tempHome,
                        "LtiRomTools/bin/${group.id.value}/${ver1.id}/${group.outputs.first().file}",
                    )
                assertTrue(binFile.exists())
                binFile.writeText("") // truncate to 0 bytes

                // Verify status now reports intact = false
                val status2 = host.store.status(ToolGroupCatalog).getOrThrow()
                val ver2 = status2.groups[group.id]?.installed?.firstOrNull()
                assertNotNull(ver2)
                assertFalse(ver2.intact, "Truncated output must mark version not intact")
            }
        }
    }

    @Test
    fun reinstallingForkTagReusesExistingDisambiguatedVersion() = runTest(timeout = kotlin.time.Duration.parse("5m")) {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val upstreamPath = host.hostPath("reuse-upstream-repo")
            val forkPath = host.hostPath("reuse-fork-repo")

            // 1. Setup upstream with tag v1.0.0
            val upstreamCommit =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = upstreamPath,
                    toolName = "fork-reuse-tool",
                    probeExitCode = 0,
                    tag = "v1.0.0",
                ).getOrThrow()

            // 2. Clone fork from upstream and add a commit with the same tag name v1.0.0 (pointing to fork commit)
            val forkSetupScript =
                """
                    git clone '$upstreamPath' '$forkPath'
                    cd '$forkPath'
                    git config user.name 'Fork Developer'
                    git config user.email 'fork@example.com'
                    echo '# fork modification' >> CMakeLists.txt
                    git commit -am 'Fork commit for v1.0.0'
                    git tag -f v1.0.0
                    git rev-parse 'v1.0.0^{commit}'
                """.trimIndent()
            val forkSetupRes =
                host.transport.execute(
                    io.ltirom.tooling.core.remote.ToolExecutionRequest(
                        toolId = "sh",
                        arguments = listOf("-c", forkSetupScript),
                        workingDirectory = "/tmp",
                    ),
                )
            assertTrue(forkSetupRes.exitCode == 0, "Fork setup failed: ${forkSetupRes.stderr}")
            val forkCommit = forkSetupRes.stdout.trim().lines().last().trim()
            assertTrue(forkCommit != upstreamCommit, "Fork commit must differ from upstream commit")

            val group =
                Fixtures.createTestToolGroup(
                    id = "fork-reuse-tool",
                    repoUrl = upstreamPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = upstreamCommit,
                )

            ToolGroupCatalog.withTestGroup(group) {
                // 3. Install upstream v1.0.0 -> installs to bin/fork-reuse-tool/v1.0.0
                val reqUpstream =
                    InstallRequest(
                        opId = "op-upstream-install",
                        group = group.id,
                        label = "v1.0.0",
                        source =
                        ResolvedSource.Git(
                            repoUrl = upstreamPath,
                            ref = ToolRef.Tag("v1.0.0"),
                            commit = upstreamCommit,
                        ),
                        recipe = group.recipe,
                        outputs = group.outputs,
                    )
                val evUpstream = host.store.install(reqUpstream).toList()
                val finishUpstream = evUpstream.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(finishUpstream)
                assertIs<OperationResult.Installed>(finishUpstream.result)
                assertEquals("v1.0.0", (finishUpstream.result as OperationResult.Installed).versionId)

                // 4. Install fork v1.0.0 -> disambiguates to v1.0.0-<forkCommit.take(7)>
                val reqFork1 =
                    InstallRequest(
                        opId = "op-fork-install-1",
                        group = group.id,
                        label = "v1.0.0",
                        source =
                        ResolvedSource.Git(
                            repoUrl = forkPath,
                            ref = ToolRef.Tag("v1.0.0"),
                            commit = forkCommit,
                        ),
                        recipe = group.recipe,
                        outputs = group.outputs,
                    )
                val evFork1 = host.store.install(reqFork1).toList()
                val finishFork1 = evFork1.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(finishFork1)
                assertIs<OperationResult.Installed>(finishFork1.result)
                val forkVerId = (finishFork1.result as OperationResult.Installed).versionId
                assertEquals("v1.0.0-${forkCommit.take(7)}", forkVerId)

                // 5. Reinstall fork v1.0.0 -> MUST reuse v1.0.0-<forkCommit.take(7)> without rebuilding or failing!
                val reqFork2 =
                    InstallRequest(
                        opId = "op-fork-install-2",
                        group = group.id,
                        label = "v1.0.0",
                        source =
                        ResolvedSource.Git(
                            repoUrl = forkPath,
                            ref = ToolRef.Tag("v1.0.0"),
                            commit = forkCommit,
                        ),
                        recipe = group.recipe,
                        outputs = group.outputs,
                    )
                val evFork2 = host.store.install(reqFork2).toList()
                val finishFork2 = evFork2.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(finishFork2)
                assertIs<OperationResult.Reused>(
                    finishFork2.result,
                    "Reinstalling the same fork tag must reuse the disambiguated version",
                )
                assertEquals(forkVerId, (finishFork2.result as OperationResult.Reused).versionId)
            }
        }
    }
}
