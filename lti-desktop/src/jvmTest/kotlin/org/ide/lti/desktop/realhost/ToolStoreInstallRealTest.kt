package org.ide.lti.desktop.realhost

import io.ltirom.tooling.core.remote.ToolListResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.tools.KotlinToolStoreAdapter
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.OperationResult
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.model.setup.ToolRef
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real-host integration test verifying tool store installation, disk space checks,
 * network streaming, probe verification, and server registration (T030).
 */
class ToolStoreInstallRealTest {
    @Test
    fun baseDirectoryIsHostHomeNeverHardcodedLti() = runTest {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val initRes = host.store.init()
            assertTrue(initRes.isSuccess, "store.init() must succeed")

            val toolsBase = File(host.tempHome, "LtiRomTools")
            assertTrue(toolsBase.exists(), "LtiRomTools must exist in host tempHome, not hardcoded /home/lti")
            assertTrue(File(toolsBase, "bin").exists(), "bin must exist under host LtiRomTools")
        }
    }

    @Test
    fun diskSpaceThresholdAboveRealFreeSpaceReturnsDiskFullAndTouchesNothing() = runTest {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val highThresholdStore =
                KotlinToolStoreAdapter(
                    transport = host.transport,
                    serverDirProvider = { "${host.hostPath()}/.ltirom/server/current" },
                    freeSpaceThresholdKbProvider = { Long.MAX_VALUE },
                )

            val request =
                InstallRequest(
                    opId = "diskfull-op-1",
                    group = ToolGroupId("erofs-utils"),
                    label = "v1.8.1",
                    source =
                    ResolvedSource.Git(
                        repoUrl = "https://example.com/repo.git",
                        ref = ToolRef.Tag("v1.8.1"),
                        commit = "abcdef1234567890abcdef1234567890abcdef12",
                    ),
                    recipe = ToolGroupCatalog.group(ToolGroupId("erofs-utils"))!!.recipe,
                    outputs = ToolGroupCatalog.group(ToolGroupId("erofs-utils"))!!.outputs,
                )

            val events = highThresholdStore.install(request).toList()
            val finished = events.filterIsInstance<InstallEvent.Finished>().firstOrNull()
            assertNotNull(finished, "Install must emit Finished event")

            val result = finished.result
            assertIs<OperationResult.Failed>(result)
            assertIs<ToolError.DiskFull>(result.error)

            val workDir = File(host.tempHome, "LtiRomTools/work/${request.opId}")
            assertFalse(workDir.exists(), "Workspace must touch nothing when DiskFull")
        }
    }

    @Test
    fun probeExitingOutsideAcceptedExitCodesResultsInVerifyFailedAndNothingPublished() = runTest {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val repoPath = host.hostPath("broken-tool-repo")
            val commit =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "broken-probe-tool",
                    probeExitCode = 1,
                    tag = "v1.0.0",
                ).getOrThrow()

            val brokenGroup =
                Fixtures.createTestToolGroup(
                    id = "broken-probe-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit,
                )

            ToolGroupCatalog.withTestGroup(brokenGroup) {
                val request =
                    InstallRequest(
                        opId = "probe-fail-op",
                        group = brokenGroup.id,
                        label = "v1.0.0",
                        source =
                        ResolvedSource.Git(
                            repoUrl = repoPath,
                            ref = ToolRef.Tag("v1.0.0"),
                            commit = commit,
                        ),
                        recipe = brokenGroup.recipe,
                        outputs = brokenGroup.outputs,
                    )

                val events = host.store.install(request).toList()
                val finished = events.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(finished, "Install must emit Finished event")

                val result = finished.result
                assertIs<OperationResult.Failed>(result)
                assertIs<ToolError.VerifyFailed>(result.error)
                assertEquals("broken-probe-tool", (result.error as ToolError.VerifyFailed).toolId)

                val publishedVer = File(host.tempHome, "LtiRomTools/bin/${brokenGroup.id.value}/v1.0.0")
                assertFalse(publishedVer.exists(), "Must not publish when probe verification fails")

                val currentLink = File(host.tempHome, "LtiRomTools/bin/${brokenGroup.id.value}/current")
                assertFalse(currentLink.exists(), "Current symlink must not exist when probe fails")
            }
        }
    }

    @Test
    fun installWithNetworkStreamAndReadableIdAndUseRegistersToolsInServer() = runTest {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val repoPath = host.hostPath("working-tool-repo")
            val commit =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "working-tool",
                    probeExitCode = 0,
                    tag = "v2.0.0",
                ).getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "working-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v2.0.0"),
                    commit = commit,
                )

            ToolGroupCatalog.withTestGroup(group) {
                val request =
                    InstallRequest(
                        opId = "install-ok-op",
                        group = group.id,
                        label = "v2.0.0",
                        source =
                        ResolvedSource.Git(
                            repoUrl = repoPath,
                            ref = ToolRef.Tag("v2.0.0"),
                            commit = commit,
                        ),
                        recipe = group.recipe,
                        outputs = group.outputs,
                        select = false,
                    )

                val events = host.store.install(request).toList()
                val finished = events.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(finished, "Install must emit Finished")
                val result = finished.result
                assertIs<OperationResult.Installed>(result)

                // Readable version ID
                assertEquals("v2.0.0", result.versionId)
                val publishedVer = File(host.tempHome, "LtiRomTools/bin/${group.id.value}/v2.0.0")
                assertTrue(publishedVer.exists(), "Published directory must exist with readable id")

                // Call use()
                val useRes = host.store.use(group.id, "v2.0.0", ChoiceUpdate.Record)
                assertTrue(useRes.isSuccess, "store.use() must succeed")

                // Server /tools lists tool at bin/<group>/current/<file>
                val listResult = host.transport.refreshTools()
                assertIs<ToolListResult.Tools>(listResult)
                val serverTool = listResult.list.firstOrNull { it.tool == "working-tool" }
                assertNotNull(serverTool, "working-tool must be registered in server /tools")
                assertTrue(serverTool.path.contains("bin/working-tool/current/working-tool"))
            }
        }
    }
}
