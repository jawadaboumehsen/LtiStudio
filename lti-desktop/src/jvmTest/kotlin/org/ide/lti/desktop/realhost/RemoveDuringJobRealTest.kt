package org.ide.lti.desktop.realhost

import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.OperationResult
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.domain.tools.ToolManager
import org.ide.lti.core.domain.tools.ToolUsageCoordinator
import org.ide.lti.core.model.setup.ToolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real-host test verifying running jobs keep their tools (T082 / US5):
 * - With a job admitted through ToolUsageCoordinator, ToolManager.remove is refused (InUse)
 *   and the directory still exists. After the job ends, remove succeeds.
 * - Paths pinned at admission still point to the old folder after a `use` switch,
 *   and the old binary still executes.
 */
class RemoveDuringJobRealTest {
    private fun hostPath(host: RealToolHost, sub: String = ""): String = host.hostPath(sub)

    @Test
    fun removeRefusedWhileJobActiveAndSucceedsAfterwards(): Unit = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repoPath = hostPath(host, "rm-job-repo")
            val commit1 =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "rm-job-tool",
                    probeExitCode = 0,
                    tag = "v1.0.0",
                ).getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "rm-job-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit1,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                // Install v1.0.0 (selected)
                val req1 =
                    InstallRequest(
                        opId = "op-rm-v1",
                        group = group.id,
                        label = "v1.0.0",
                        source =
                        ResolvedSource.Git(
                            repoUrl = repoPath,
                            ref = ToolRef.Tag("v1.0.0"),
                            commit = commit1,
                        ),
                        recipe = group.recipe,
                        outputs = group.outputs,
                        select = true,
                        choice = ChoiceUpdate.Keep,
                    )
                val events1 = host.store.install(req1).toList()
                val fin1 = events1.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(fin1, "Install v1 must finish")
                assertIs<OperationResult.Installed>(fin1.result, "Install v1 must succeed")

                // Install v2.0.0 (not selected)
                val commit2 =
                    Fixtures.setupWorkingCMakeToolFixture(
                        transport = host.transport,
                        repoPath = repoPath,
                        toolName = "rm-job-tool",
                        probeExitCode = 0,
                        tag = "v2.0.0",
                    ).getOrThrow()

                val req2 =
                    InstallRequest(
                        opId = "op-rm-v2",
                        group = group.id,
                        label = "v2.0.0",
                        source =
                        ResolvedSource.Git(
                            repoUrl = repoPath,
                            ref = ToolRef.Tag("v2.0.0"),
                            commit = commit2,
                        ),
                        recipe = group.recipe,
                        outputs = group.outputs,
                        select = false,
                        choice = ChoiceUpdate.Keep,
                    )
                val events2 = host.store.install(req2).toList()
                val fin2 = events2.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(fin2, "Install v2 must finish")
                assertIs<OperationResult.Installed>(fin2.result, "Install v2 must succeed")

                val coordinator = ToolUsageCoordinator()
                val manager =
                    ToolManager(
                        port = host.store,
                        catalog = ToolGroupCatalog,
                        usageCoordinator = coordinator,
                    )

                val v2Dir = "${hostPath(host)}/LtiRomTools/bin/${group.id.value}/v2.0.0"

                // 1. Admit a job through coordinator
                val pins =
                    coordinator.admitJob("job-active-work") {
                        host.store.paths(ToolGroupCatalog).getOrThrow()
                    }
                assertTrue(coordinator.isJobActive, "Job must be active")

                // 2. Attempt to remove unselected version v2.0.0 while job is active
                val removeAttempt = manager.remove(group.id, "v2.0.0")
                assertIs<OperationResult.Failed>(removeAttempt, "Remove must fail while a job is active")
                assertIs<ToolError.InUse>(
                    removeAttempt.error,
                    "Error must be ToolError.InUse: ${removeAttempt.error}",
                )

                // Verify folder STILL exists on host
                val checkExistsBefore =
                    host.transport.execute(
                        ToolExecutionRequest("test", listOf("-d", v2Dir), workingDirectory = "/tmp"),
                    )
                assertEquals(0, checkExistsBefore.exitCode, "Folder v2.0.0 must still exist")

                // 3. Finish the job
                coordinator.finishJob("job-active-work")
                assertTrue(!coordinator.isJobActive, "Job must not be active after finish")

                // 4. Remove should now succeed
                val removeAfter = manager.remove(group.id, "v2.0.0")
                assertTrue(
                    removeAfter !is OperationResult.Failed,
                    "Remove must succeed after job ends: $removeAfter",
                )

                // Verify folder is gone
                val checkExistsAfter =
                    host.transport.execute(
                        ToolExecutionRequest("test", listOf("-d", v2Dir), workingDirectory = "/tmp"),
                    )
                assertNotEquals(0, checkExistsAfter.exitCode, "Folder v2.0.0 must be deleted")
            }
        }
    }

    @Test
    fun pinnedPathsPersistAcrossVersionSwitchAndExecuteOldBinary(): Unit = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repoPath = hostPath(host, "pin-persist-repo")
            val commit1 =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "pin-tool",
                    probeExitCode = 0,
                    tag = "v1.0.0",
                ).getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "pin-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit1,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                // Install v1.0.0 (selected)
                val req1 =
                    InstallRequest(
                        opId = "op-pin-v1",
                        group = group.id,
                        label = "v1.0.0",
                        source =
                        ResolvedSource.Git(
                            repoUrl = repoPath,
                            ref = ToolRef.Tag("v1.0.0"),
                            commit = commit1,
                        ),
                        recipe = group.recipe,
                        outputs = group.outputs,
                        select = true,
                        choice = ChoiceUpdate.Keep,
                    )
                host.store.install(req1).toList()

                // Install v2.0.0 (not selected)
                val commit2 =
                    Fixtures.setupWorkingCMakeToolFixture(
                        transport = host.transport,
                        repoPath = repoPath,
                        toolName = "pin-tool",
                        probeExitCode = 0,
                        tag = "v2.0.0",
                    ).getOrThrow()

                val req2 =
                    InstallRequest(
                        opId = "op-pin-v2",
                        group = group.id,
                        label = "v2.0.0",
                        source =
                        ResolvedSource.Git(
                            repoUrl = repoPath,
                            ref = ToolRef.Tag("v2.0.0"),
                            commit = commit2,
                        ),
                        recipe = group.recipe,
                        outputs = group.outputs,
                        select = false,
                        choice = ChoiceUpdate.Keep,
                    )
                host.store.install(req2).toList()

                val coordinator = ToolUsageCoordinator()
                val manager =
                    ToolManager(
                        port = host.store,
                        catalog = ToolGroupCatalog,
                        usageCoordinator = coordinator,
                    )

                // Admit Job 1 and capture its pins
                val job1Pins =
                    coordinator.admitJob("job-pinned-session") {
                        host.store.paths(ToolGroupCatalog).getOrThrow()
                    }

                val oldPinnedPath = job1Pins.paths.getValue("pin-tool")
                assertTrue(oldPinnedPath.contains("v1.0.0"), "Pinned path must be v1.0.0: $oldPinnedPath")
                assertEquals("v1.0.0", job1Pins.versions["pin-tool"])

                // Execute the pinned binary before the switch
                val exec1 =
                    host.transport.execute(
                        ToolExecutionRequest(
                            toolId = oldPinnedPath,
                            arguments = listOf("--version"),
                            workingDirectory = "/tmp",
                        ),
                    )
                assertEquals(0, exec1.exitCode, "Old binary must execute successfully")

                // Switch to v2.0.0 while job is running
                val switchRes = manager.use(group.id, "v2.0.0", ChoiceUpdate.Record)
                assertIs<OperationResult.Used>(switchRes, "Switch to v2.0.0 must succeed")

                // Verify that new jobs query returns v2.0.0
                val newPins = host.store.paths(ToolGroupCatalog).getOrThrow()
                val newPinnedPath = newPins.paths.getValue("pin-tool")
                assertTrue(newPinnedPath.contains("v2.0.0"), "New pins must point to v2.0.0: $newPinnedPath")
                assertEquals("v2.0.0", newPins.versions["pin-tool"])

                // Verify that Job 1's pinned path STILL points to the old binary and still executes
                val exec2 =
                    host.transport.execute(
                        ToolExecutionRequest(
                            toolId = oldPinnedPath,
                            arguments = listOf("--version"),
                            workingDirectory = "/tmp",
                        ),
                    )
                assertEquals(0, exec2.exitCode, "Old binary must still execute after use switch")

                coordinator.finishJob("job-pinned-session")
            }
        }
    }
}
