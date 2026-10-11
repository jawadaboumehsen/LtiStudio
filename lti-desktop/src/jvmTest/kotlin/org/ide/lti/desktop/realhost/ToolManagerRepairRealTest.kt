package org.ide.lti.desktop.realhost

import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.GroupSetupResult
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.OperationResult
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.model.setup.ToolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Real-host test verifying repair behavior of ToolManager (T055).
 *
 * Assertions:
 * - A damaged target is rebuilt from the target's source under the SAME id.
 * - Inode changes while name stays the same (mv -T bin/<tool>/<id> work/<op>/old, then mv -T work/<op>/out bin/<tool>/<id>).
 * - Pick in settings.json is kept, and other groups' folders are untouched (same inodes).
 * - A second prepareMissing with everything intact builds nothing.
 */
class ToolManagerRepairRealTest {
    private suspend fun getDirectoryInode(host: RealToolHost, dirPath: String): String {
        val res =
            host.transport.execute(
                ToolExecutionRequest(toolId = "stat", arguments = listOf("-c", "%i", dirPath)),
            )
        assertTrue(res.exitCode == 0, "stat -c %i must succeed: ${res.stderr}")
        return res.stdout.trim()
    }

    @Test
    fun damagedTargetIsRebuiltUnderSameIdWithChangedInodeAndKeepsPick() = runTest(timeout = 180.seconds) {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val repoPath1 = host.hostPath("tool-repair-repo")
            val setupRes1 =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath1,
                    toolName = "repair-tool",
                    probeExitCode = 0,
                    tag = "v1.0.0",
                )
            assertTrue(setupRes1.isSuccess, "Must setup fixture repo 1")
            val commit1 = setupRes1.getOrThrow()

            val group1 =
                Fixtures.createTestToolGroup(
                    id = "repair-tool",
                    repoUrl = repoPath1,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit1,
                )

            val repoPath2 = host.hostPath("tool-untouched-repo")
            val setupRes2 =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath2,
                    toolName = "untouched-tool",
                    probeExitCode = 0,
                    tag = "v1.0.0",
                )
            assertTrue(setupRes2.isSuccess, "Must setup fixture repo 2")
            val commit2 = setupRes2.getOrThrow()

            val group2 =
                Fixtures.createTestToolGroup(
                    id = "untouched-tool",
                    repoUrl = repoPath2,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit2,
                )

            ToolGroupCatalog.withIsolatedTestGroups(listOf(group1, group2)) {
                // Install group 1
                val req1 =
                    InstallRequest(
                        opId = "op-install-repair-tool",
                        group = group1.id,
                        label = "v1.0.0",
                        source = ResolvedSource.Git(repoPath1, ToolRef.Tag("v1.0.0"), commit1),
                        recipe = group1.recipe,
                        outputs = group1.outputs,
                        select = true,
                        choice = ChoiceUpdate.Record,
                    )
                val events1 = host.store.install(req1).toList()
                val finish1 = events1.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(finish1)
                assertIs<OperationResult.Installed>(finish1.result)
                val version1Id = (finish1.result as OperationResult.Installed).versionId

                // Install group 2
                val req2 =
                    InstallRequest(
                        opId = "op-install-untouched-tool",
                        group = group2.id,
                        label = "v1.0.0",
                        source = ResolvedSource.Git(repoPath2, ToolRef.Tag("v1.0.0"), commit2),
                        recipe = group2.recipe,
                        outputs = group2.outputs,
                        select = true,
                        choice = ChoiceUpdate.Record,
                    )
                val events2 = host.store.install(req2).toList()
                val finish2 = events2.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(finish2)
                assertIs<OperationResult.Installed>(finish2.result)
                val version2Id = (finish2.result as OperationResult.Installed).versionId

                val dir1 = host.hostPath("LtiRomTools/bin/${group1.id.value}/$version1Id")
                val dir2 = host.hostPath("LtiRomTools/bin/${group2.id.value}/$version2Id")

                val inode1Before = getDirectoryInode(host, dir1)
                val inode2Before = getDirectoryInode(host, dir2)

                // Damage group 1 by deleting its executable output via transport
                val delRes =
                    host.transport.execute(
                        ToolExecutionRequest(
                            toolId = "rm",
                            arguments = listOf("-f", "$dir1/${group1.outputs.first().file}"),
                        ),
                    )
                assertEquals(0, delRes.exitCode, "Must delete group 1 binary")

                // Verify status detects damaged target
                val statusBeforeRepair = host.store.status(ToolGroupCatalog).getOrThrow()
                val group1Ver = statusBeforeRepair.groups[group1.id]?.installed?.firstOrNull { it.id == version1Id }
                assertNotNull(group1Ver)
                assertTrue(!group1Ver.intact, "Group 1 must be not intact")

                // Run ToolManager.repair on group 1
                val repairResult = host.manager.repair(group1.id)
                assertIs<OperationResult.Installed>(repairResult, "Repair must result in Installed")
                val repairedVersionId = repairResult.versionId

                // Name stays the same
                assertEquals(version1Id, repairedVersionId, "Repaired version ID must match original ID")

                // Inode of repaired folder must change
                val inode1After = getDirectoryInode(host, dir1)
                assertTrue(
                    inode1Before != inode1After,
                    "Repaired directory inode must change ($inode1Before vs $inode1After)",
                )

                // Inode of untouched group 2 must NOT change
                val inode2After = getDirectoryInode(host, dir2)
                assertEquals(inode2Before, inode2After, "Untouched group directory inode must remain identical")

                // Pick in settings.json must be kept
                val sFile =
                    org.ide.lti.core.data.tools.ToolSettingsFile(
                        org.ide.lti.core.data.tools.RemoteCommandRunner(host.transport),
                        host.hostPath("LtiRomTools"),
                    )
                val selections = sFile.readSelections().getOrNull().orEmpty()
                assertTrue(selections.containsKey(group1.id), "settings.json must retain group 1 pick")

                // Second prepareMissing with everything intact builds nothing
                val summary = host.manager.prepareMissing()
                for ((gid, res) in summary.groupResults) {
                    if (gid == group1.id || gid == group2.id) {
                        assertIs<GroupSetupResult.Reused>(res, "Group ${gid.value} must be Reused, got $res")
                    }
                }
            }
        }
    }
}
