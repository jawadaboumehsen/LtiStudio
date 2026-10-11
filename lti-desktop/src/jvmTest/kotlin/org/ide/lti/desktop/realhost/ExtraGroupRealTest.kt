package org.ide.lti.desktop.realhost

import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.tools.GroupSetupResult
import org.ide.lti.core.model.setup.ToolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real-host integration test verifying dynamic tool group registration (SC-009, T033).
 * When ToolGroupCatalog.withTestGroup is used with a new CMake fixture group:
 * - prepareMissing really builds and installs it
 * - refresh() reports it, with no code change outside the catalog entry
 */
class ExtraGroupRealTest {
    @Test
    fun extraGroupBuiltAndInstalledAndReportedByRefresh() = runTest {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val repoPath = host.hostPath("extra-tool-repo")
            val setupRes =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "extra-tool",
                    tag = "v1.0.0",
                )
            assertTrue(setupRes.isSuccess, "Must setup fixture repo")
            val commit = setupRes.getOrThrow()

            val extraGroup =
                Fixtures.createTestToolGroup(
                    id = "extra-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit,
                )

            ToolGroupCatalog.withIsolatedTestGroup(extraGroup) {
                // 1. prepareMissing builds and installs the extra group
                val summary = host.manager.prepareMissing()
                val groupResult = summary.groupResults[extraGroup.id]
                assertIs<GroupSetupResult.Installed>(
                    groupResult,
                    "Extra group must be built and installed by prepareMissing",
                )
                assertEquals("v1.0.0", groupResult.versionId)

                // Verify binary exists on disk
                val binaryPath = host.hostPath("LtiRomTools/bin/${extraGroup.id.value}/current/extra-tool")
                val binCheck =
                    host.transport.execute(
                        ToolExecutionRequest(toolId = "test", arguments = listOf("-x", binaryPath)),
                    )
                assertEquals(0, binCheck.exitCode, "Binary must exist and be executable under current link")

                // 2. refresh() reports the extra group in inventory
                val refreshResult = host.manager.refresh()
                assertTrue(refreshResult.isSuccess, "refresh() must succeed")
                val inventory = refreshResult.getOrThrow()

                val reportedGroup = inventory.groups[extraGroup.id]
                assertNotNull(reportedGroup, "refresh() must report extraGroup in inventory")
                assertEquals("v1.0.0", reportedGroup.current?.id)
                assertTrue(reportedGroup.current?.intact == true, "current version must be intact")
            }
        }
    }
}
