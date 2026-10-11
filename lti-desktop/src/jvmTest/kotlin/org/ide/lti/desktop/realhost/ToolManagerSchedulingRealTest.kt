package org.ide.lti.desktop.realhost

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.ide.lti.core.data.tools.KotlinToolStoreAdapter
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.tools.GroupSetupResult
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.SetupToolsSummary
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.domain.tools.ToolManager
import org.ide.lti.core.model.setup.ToolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real-host test verifying ToolManager scheduling rules, continuing after failures,
 * shared blocker handling, summary counts, and retryFailed (T074 / T077).
 */
class ToolManagerSchedulingRealTest {
    private fun hostPath(host: RealToolHost, sub: String = ""): String = host.hostPath(sub)

    private fun killServer(host: RealToolHost) = runBlocking {
        host.allowRelaunch = false
        val info = host.supervisor.getConnectionInfo()
        val pid = info?.pid
        if (host.mode == "linux") {
            if (pid != null && pid > 0) {
                runCatching { Runtime.getRuntime().exec(arrayOf("kill", "-9", pid.toString())).waitFor() }
            }
            host.serverProcess?.destroyForcibly()
        } else {
            val distro = System.getenv("LTI_TEST_DISTRO") ?: System.getProperty("lti.test.distro") ?: "LtiRomTest"
            val killCmd =
                if (pid != null && pid > 0) {
                    listOf(
                        "sh",
                        "-c",
                        "kill -9 $pid 2>/dev/null; pkill -9 -f ltirom 2>/dev/null; pkill -9 -f java 2>/dev/null",
                    )
                } else {
                    listOf("sh", "-c", "pkill -9 -f ltirom 2>/dev/null; pkill -9 -f java 2>/dev/null")
                }
            io.ltirom.tooling.client.wsl.WslCliExecutor().execute(distro, killCmd)
        }
    }

    @Test
    fun syntaxErrorCmakeFixtureFailsNextGroupsStillInstallAndRetryRebuildsOnlyFailedGroup(): Unit = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repo1 = hostPath(host, "broken-cmake-repo")
            val repo2 = hostPath(host, "working-cmake-repo")

            val commit1 = Fixtures.setupCmakeSyntaxErrorFixture(host.transport, repo1).getOrThrow()
            val commit2 =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repo2,
                    toolName = "good-group",
                    probeExitCode = 0,
                    tag = "v1.0.0",
                ).getOrThrow()

            val group1 =
                Fixtures.createTestToolGroup(
                    id = "broken-group",
                    repoUrl = repo1,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit1,
                )
            val group2 =
                Fixtures.createTestToolGroup(
                    id = "good-group",
                    repoUrl = repo2,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit2,
                )

            ToolGroupCatalog.withIsolatedTestGroups(listOf(group1, group2)) {
                val summary = host.manager.prepareMissing()

                assertFalse(summary.isComplete, "Pipeline must not be complete when a group fails")
                assertEquals(1, summary.readyCount, "Good group must be ready")
                assertEquals(1, summary.failedCount, "Broken group must be failed")
                assertEquals(0, summary.notAttemptedCount, "No group should be not attempted")

                val res1 = summary.groupResults[group1.id]
                assertIs<GroupSetupResult.Failed>(res1)
                assertIs<ToolError.BuildFailed>(res1.error)

                val res2 = summary.groupResults[group2.id]
                assertIs<GroupSetupResult.Installed>(res2)

                // Fix group 1
                val fixedCommit =
                    Fixtures.setupWorkingCMakeToolFixture(
                        transport = host.transport,
                        repoPath = repo1,
                        toolName = "broken-group",
                        probeExitCode = 0,
                        tag = "v1.0.0",
                    ).getOrThrow()

                val fixedGroup1 =
                    Fixtures.createTestToolGroup(
                        id = "broken-group",
                        repoUrl = repo1,
                        ref = ToolRef.Tag("v1.0.0"),
                        commit = fixedCommit,
                    )

                ToolGroupCatalog.withIsolatedTestGroups(listOf(fixedGroup1, group2)) {
                    val retrySummary = host.manager.retryFailed()

                    assertTrue(retrySummary.isComplete, "Pipeline must be complete after retrying fixed group")
                    assertEquals(2, retrySummary.readyCount)
                    assertEquals(0, retrySummary.failedCount)
                    assertEquals(0, retrySummary.notAttemptedCount)

                    val retryRes1 = retrySummary.groupResults[fixedGroup1.id]
                    assertIs<GroupSetupResult.Installed>(retryRes1)

                    val retryRes2 = retrySummary.groupResults[group2.id]
                    assertIs<GroupSetupResult.Reused>(retryRes2, "Intact good group must be reused, not rebuilt")
                }
            }
        }
    }

    @Test
    fun diskFullSharedBlockerLeavesRemainingGroupsNotAttempted(): Unit = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repo1 = hostPath(host, "disk-repo-1")
            val repo2 = hostPath(host, "disk-repo-2")

            val commit1 = Fixtures.setupWorkingCMakeToolFixture(host.transport, repo1, "tool1").getOrThrow()
            val commit2 = Fixtures.setupWorkingCMakeToolFixture(host.transport, repo2, "tool2").getOrThrow()

            val group1 = Fixtures.createTestToolGroup("disk-group-1", repo1, ToolRef.Tag("v1.0.0"), commit1)
            val group2 = Fixtures.createTestToolGroup("disk-group-2", repo2, ToolRef.Tag("v1.0.0"), commit2)

            val highThresholdStore =
                KotlinToolStoreAdapter(
                    transport = host.transport,
                    serverDirProvider = { "${host.hostPath()}/.ltirom/server/current" },
                    freeSpaceThresholdKbProvider = { Long.MAX_VALUE },
                )
            val manager = ToolManager(port = highThresholdStore, catalog = ToolGroupCatalog)

            ToolGroupCatalog.withIsolatedTestGroups(listOf(group1, group2)) {
                val summary = manager.prepareMissing()

                assertFalse(summary.isComplete)
                assertEquals(0, summary.readyCount)
                assertEquals(1, summary.failedCount)
                assertEquals(1, summary.notAttemptedCount)

                val res1 = summary.groupResults[group1.id]
                assertIs<GroupSetupResult.Failed>(res1)
                assertIs<ToolError.DiskFull>(res1.error)

                val res2 = summary.groupResults[group2.id]
                assertIs<GroupSetupResult.NotAttempted>(res2)
                assertIs<ToolError.DiskFull>(res2.blocker)
            }
        }
    }

    @Test
    fun missingPackagesSharedBlockerLeavesRemainingGroupsNotAttempted(): Unit = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repo1 = hostPath(host, "pkg-repo-1")
            val repo2 = hostPath(host, "pkg-repo-2")

            val commit1 = Fixtures.setupWorkingCMakeToolFixture(host.transport, repo1, "tool1").getOrThrow()
            val commit2 = Fixtures.setupWorkingCMakeToolFixture(host.transport, repo2, "tool2").getOrThrow()

            val baseGroup1 = Fixtures.createTestToolGroup("pkg-group-1", repo1, ToolRef.Tag("v1.0.0"), commit1)
            val group1 = baseGroup1.copy(buildPackages = listOf("nonexistent_command_xyz_12345"))
            val group2 = Fixtures.createTestToolGroup("pkg-group-2", repo2, ToolRef.Tag("v1.0.0"), commit2)

            ToolGroupCatalog.withIsolatedTestGroups(listOf(group1, group2)) {
                val summary = host.manager.prepareMissing()

                assertFalse(summary.isComplete)
                assertEquals(0, summary.readyCount)
                assertEquals(1, summary.failedCount)
                assertEquals(1, summary.notAttemptedCount)

                val res1 = summary.groupResults[group1.id]
                assertIs<GroupSetupResult.Failed>(res1)
                assertIs<ToolError.MissingPackages>(res1.error)

                val res2 = summary.groupResults[group2.id]
                assertIs<GroupSetupResult.NotAttempted>(res2)
                assertIs<ToolError.MissingPackages>(res2.blocker)
            }
        }
    }

    @Test
    fun connectionLostLeavesRemainingGroupsNotAttempted(): Unit = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repo1 = hostPath(host, "conn-repo-1")
            val repo2 = hostPath(host, "conn-repo-2")

            val commit1 =
                Fixtures.setupSlowCMakeToolFixture(
                    host.transport,
                    repo1,
                    "conn-group-1",
                    delaySeconds = 10,
                ).getOrThrow()
            val commit2 = Fixtures.setupWorkingCMakeToolFixture(host.transport, repo2, "conn-group-2").getOrThrow()

            val group1 = Fixtures.createTestToolGroup("conn-group-1", repo1, ToolRef.Tag("v1.0.0"), commit1)
            val group2 = Fixtures.createTestToolGroup("conn-group-2", repo2, ToolRef.Tag("v1.0.0"), commit2)

            ToolGroupCatalog.withIsolatedTestGroups(listOf(group1, group2)) {
                var killed = false
                val summary =
                    host.manager.prepareMissing { g, ev ->
                        if (g == group1.id && ev is InstallEvent.Output && !killed) {
                            killed = true
                            killServer(host)
                        }
                    }

                assertFalse(summary.isComplete)
                assertEquals(0, summary.readyCount)
                assertEquals(1, summary.failedCount)
                assertEquals(1, summary.notAttemptedCount)

                val res1 = summary.groupResults[group1.id]
                assertIs<GroupSetupResult.Failed>(res1)
                assertIs<ToolError.ConnectionLost>(res1.error)

                val res2 = summary.groupResults[group2.id]
                assertIs<GroupSetupResult.NotAttempted>(res2)
                assertIs<ToolError.ConnectionLost>(res2.blocker)
            }
        }
    }

    @Test
    fun cancellationLeavesRemainingGroupsCancelled(): Unit = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repo1 = hostPath(host, "cancel-repo-1")
            val repo2 = hostPath(host, "cancel-repo-2")

            val commit1 =
                Fixtures.setupSlowCMakeToolFixture(
                    host.transport,
                    repo1,
                    "cancel-group-1",
                    delaySeconds = 10,
                ).getOrThrow()
            val commit2 =
                Fixtures.setupWorkingCMakeToolFixture(
                    host.transport,
                    repo2,
                    "cancel-group-2",
                ).getOrThrow()

            val group1 = Fixtures.createTestToolGroup("cancel-group-1", repo1, ToolRef.Tag("v1.0.0"), commit1)
            val group2 = Fixtures.createTestToolGroup("cancel-group-2", repo2, ToolRef.Tag("v1.0.0"), commit2)

            ToolGroupCatalog.withIsolatedTestGroups(listOf(group1, group2)) {
                var summaryResult: SetupToolsSummary? = null
                var cancelled = false
                lateinit var job: kotlinx.coroutines.Job
                job =
                    launch(Dispatchers.IO) {
                        summaryResult =
                            host.manager.prepareMissing { g, ev ->
                                if (g == group1.id && ev is InstallEvent.Output && !cancelled) {
                                    cancelled = true
                                    job.cancel()
                                }
                            }
                    }

                job.join()

                val summary = summaryResult
                assertNotNull(summary, "prepareMissing should produce a summary upon cancellation")
                assertFalse(summary.isComplete)
                assertEquals(0, summary.readyCount)

                val res1 = summary.groupResults[group1.id]
                assertTrue(res1 is GroupSetupResult.Cancelled, "Group 1 must be Cancelled")

                val res2 = summary.groupResults[group2.id]
                assertTrue(res2 is GroupSetupResult.Cancelled, "Group 2 must be Cancelled")
            }
        }
    }
}
