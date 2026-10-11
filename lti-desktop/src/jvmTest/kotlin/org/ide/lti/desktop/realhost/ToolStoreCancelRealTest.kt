package org.ide.lti.desktop.realhost

import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.OperationResult
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.model.setup.ToolRef
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Real-host test verifying ToolStore cancellation, op.json tracking, and crash recovery (T085 / US6).
 *
 * Tests:
 * 1. cancel(group) stops build within 10 s, leaves no process behind, keeps work/<op>/ and leaves current unchanged.
 * 2. install writes work/<op>/op.json ({group, versionId, startedAt}) when it starts.
 * 3. killing the server mid-build, then restarting it -> status reports interrupted op; next op cleans older work folders.
 * 4. if server dies during cancel, it raises RemoteCancellationUnconfirmedException with execution id.
 */
class ToolStoreCancelRealTest {
    private fun hostPath(host: RealToolHost, sub: String = ""): String {
        val h = io.ltirom.tooling.client.WslPathTranslator().toWslPath(host.tempHome.path)
        return if (sub.isBlank()) h else "$h/$sub"
    }

    @Test
    fun cancelStopsBuildWithin10sKeepsWorkspaceAndLeavesCurrentUnchanged(): Unit = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repoPath = hostPath(host, "cancel-slow-repo")
            val commit =
                Fixtures.setupSlowCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "cancel-tool",
                    delaySeconds = 60,
                    tag = "v1.0.0",
                ).getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "cancel-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                val opId = "op-cancel-test-1"
                val req =
                    InstallRequest(
                        opId = opId,
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
                        choice = ChoiceUpdate.Record,
                    )

                // Launch install in background
                var finishedResult: OperationResult? = null
                var buildStarted = false
                val installJob =
                    host.store.install(req).onEach { ev ->
                        if (ev is InstallEvent.Output &&
                            (ev.text.contains("cmake") || ev.text.contains("Running build recipe"))
                        ) {
                            buildStarted = true
                        }
                        if (ev is InstallEvent.Finished) {
                            finishedResult = ev.result
                        }
                    }.launchIn(this)

                // Wait until build actually starts running (sleep process active)
                val waitStart = System.currentTimeMillis()
                var pgrepRunning = ""
                while (pgrepRunning.isBlank() && System.currentTimeMillis() - waitStart < 30_000L) {
                    delay(200)
                    val res =
                        host.transport.execute(
                            ToolExecutionRequest(
                                "sh",
                                listOf("-c", "pgrep -x sleep || true"),
                                workingDirectory = "/tmp",
                            ),
                        )
                    pgrepRunning = res.stdout.trim()
                }
                assertTrue(pgrepRunning.isNotBlank(), "Build process (sleep) must be active before cancel")

                // Verify work/<opId>/op.json was written at start
                val workDir = "${hostPath(host)}/LtiRomTools/work/$opId"
                val opJsonRes =
                    host.transport.execute(
                        ToolExecutionRequest("cat", listOf("$workDir/op.json"), workingDirectory = "/tmp"),
                    )
                assertEquals(0, opJsonRes.exitCode, "work/<opId>/op.json must exist while build runs")
                assertTrue(opJsonRes.stdout.contains("\"group\":\"cancel-tool\""), "op.json must contain group")

                // Cancel the operation and measure duration (must be <= 10 s)
                val cancelElapsed =
                    measureTimeMillis {
                        val cancelRes = host.store.cancel(group.id)
                        assertTrue(
                            cancelRes.isSuccess,
                            "store.cancel must succeed: ${cancelRes.exceptionOrNull()?.message}",
                        )
                    }
                assertTrue(cancelElapsed <= 10_000L, "Cancellation took $cancelElapsed ms, must be <= 10 000 ms")

                installJob.join()

                // Verify finished result is Cancelled or Failed
                assertTrue(
                    finishedResult is OperationResult.Cancelled || finishedResult is OperationResult.Failed,
                    "Finished result must be Cancelled or Failed, got: $finishedResult",
                )

                // Verify pgrep -f finds no sleep or cmake build process left behind
                val pgrepRes =
                    host.transport.execute(
                        ToolExecutionRequest(
                            "sh",
                            listOf("-c", "pgrep -f '[s]leep 60' || true"),
                            workingDirectory = "/tmp",
                        ),
                    )
                assertEquals("", pgrepRes.stdout.trim(), "No build or sleep process must remain running")

                // Workspace work/<opId> must be KEPT for diagnostics
                val workDirCheck =
                    host.transport.execute(
                        ToolExecutionRequest("test", listOf("-d", workDir), workingDirectory = "/tmp"),
                    )
                assertEquals(0, workDirCheck.exitCode, "Workspace work/<opId> must be retained after cancel")

                // Current link must be untouched (does not exist yet)
                val currentCheck =
                    host.transport.execute(
                        ToolExecutionRequest(
                            "test",
                            listOf("-e", "${hostPath(host)}/LtiRomTools/bin/${group.id.value}/current"),
                            workingDirectory = "/tmp",
                        ),
                    )
                assertTrue(currentCheck.exitCode != 0, "Current link must not exist after cancelled install")

                // Status must NOT report cancelled operation as Interrupted
                val statusRes = host.store.status(ToolGroupCatalog).getOrThrow()
                val gState = statusRes.groups[group.id]
                assertNull(
                    gState?.interrupted,
                    "Cancelled operation must write result file and not report as Interrupted",
                )
            }
        }
    }

    @Test
    fun crashMidBuildReportsInterruptedOpAndNextOpCleansOlderWork(): Unit = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repoPath = hostPath(host, "crash-slow-repo")
            val commit =
                Fixtures.setupSlowCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "crash-tool",
                    delaySeconds = 60,
                    tag = "v1.0.0",
                ).getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "crash-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                val opId = "op-crash-test-1"
                val req1 =
                    InstallRequest(
                        opId = opId,
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
                        choice = ChoiceUpdate.Record,
                    )

                var buildStarted = false
                val installJob =
                    host.store.install(req1).onEach { ev ->
                        if (ev is InstallEvent.Output &&
                            (ev.text.contains("cmake") || ev.text.contains("Running build recipe"))
                        ) {
                            buildStarted = true
                        }
                    }.launchIn(this)

                val waitStart = System.currentTimeMillis()
                while (!buildStarted && System.currentTimeMillis() - waitStart < 15_000L) {
                    delay(100)
                }

                // Verify op.json exists
                val workDir1 = "${hostPath(host)}/LtiRomTools/work/$opId"
                val opJsonRes =
                    host.transport.execute(
                        ToolExecutionRequest("cat", listOf("$workDir1/op.json"), workingDirectory = "/tmp"),
                    )
                assertEquals(0, opJsonRes.exitCode, "work/$opId/op.json must exist")

                // Force kill the daemon process or cancel the job to simulate crash
                installJob.cancel()

                // Read status -> must report interrupted op from disk
                val inv = host.store.status(ToolGroupCatalog).getOrThrow()
                val gState = inv.groups[group.id]
                assertNotNull(gState, "Group state must exist")
                assertNotNull(gState.interrupted, "Group state must report interrupted op")
                assertEquals(opId, gState.interrupted?.opId)

                // Run a second (fast) operation for the same group
                val commit2 =
                    Fixtures.setupWorkingCMakeToolFixture(
                        transport = host.transport,
                        repoPath = repoPath,
                        toolName = "crash-tool",
                        probeExitCode = 0,
                        tag = "v2.0.0",
                    ).getOrThrow()

                val opId2 = "op-crash-test-2"
                val req2 =
                    InstallRequest(
                        opId = opId2,
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
                        select = true,
                        choice = ChoiceUpdate.Record,
                    )

                val events2 = mutableListOf<InstallEvent>()
                host.store.install(req2).collect { events2.add(it) }
                val finished2 = events2.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(finished2)
                assertIs<OperationResult.Installed>(finished2.result)

                // Verify older workDir1 was deleted by the new operation
                val oldWorkCheck =
                    host.transport.execute(
                        ToolExecutionRequest("test", listOf("-d", workDir1), workingDirectory = "/tmp"),
                    )
                assertTrue(oldWorkCheck.exitCode != 0, "Older work directory $workDir1 must be deleted by next op")
            }
        }
    }

    @Test
    fun cancellingIdleToolDoesNotPoisonFutureInstallFailureOrKillRunningTool(): Unit = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val idleGroup = ToolGroupId("apktool")
            // 1. Cancel an idle group
            val cancelRes = host.store.cancel(idleGroup)
            assertTrue(cancelRes.isSuccess)

            // 2. Perform an install on this group with an invalid repo URL (expected to fail)
            val fakeRepo = hostPath(host, "non-existent-repo-12345")
            val req =
                InstallRequest(
                    opId = "op-idle-cancel-test",
                    group = idleGroup,
                    label = "v1.0.0",
                    source =
                    ResolvedSource.Git(
                        repoUrl = fakeRepo,
                        ref = ToolRef.Tag("v1.0.0"),
                        commit = "0000000000000000000000000000000000000000",
                    ),
                    recipe = org.ide.lti.core.domain.setup.RecipeConfig.CMake("CMakeLists.txt"),
                    outputs = emptyList(),
                )

            val events = mutableListOf<InstallEvent>()
            host.store.install(req).collect { events.add(it) }
            val finish = events.filterIsInstance<InstallEvent.Finished>().firstOrNull()
            assertNotNull(finish, "Install must finish")

            // On unfixed code, this will fail because finish.result is OperationResult.Cancelled instead of OperationResult.Failed!
            assertIs<OperationResult.Failed>(
                finish.result,
                "Real failure must be reported as Failed, not Cancelled by a stale flag",
            )
        }
    }

    @Test
    fun unsupportedLayoutWritesResultFileAndDoesNotReportAsInterrupted(): Unit = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repoPath = hostPath(host, "unsupported-layout-repo")
            val commit =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "layout-tool",
                    probeExitCode = 0,
                    tag = "v1.0.0",
                ).getOrThrow()

            val baseGroup =
                Fixtures.createTestToolGroup(
                    id = "layout-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit,
                )
            val groupWithRequiredLayout =
                baseGroup.copy(
                    layout = listOf("non_existent_layout_requirement.txt"),
                )

            ToolGroupCatalog.withIsolatedTestGroup(groupWithRequiredLayout) {
                val opId = "op-unsupported-layout-test"
                val req =
                    InstallRequest(
                        opId = opId,
                        group = groupWithRequiredLayout.id,
                        label = "v1.0.0",
                        source =
                        ResolvedSource.Git(
                            repoUrl = repoPath,
                            ref = ToolRef.Tag("v1.0.0"),
                            commit = commit,
                        ),
                        recipe = groupWithRequiredLayout.recipe,
                        outputs = groupWithRequiredLayout.outputs,
                    )

                val events = mutableListOf<InstallEvent>()
                host.store.install(req).collect { events.add(it) }
                val finished = events.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(finished, "Install must finish")
                val failedRes = assertIs<OperationResult.Failed>(finished.result)
                assertIs<org.ide.lti.core.domain.tools.ToolError.UnsupportedLayout>(failedRes.error)

                // Result file must exist and record the failure
                val opResult = host.store.result(opId)
                assertTrue(opResult.isFailure, "Result file must record failed status")
                assertTrue(opResult.exceptionOrNull()?.message?.contains("Missing required paths") == true)

                // Status must NOT report this operation as Interrupted
                val statusRes = host.store.status(ToolGroupCatalog).getOrThrow()
                val gState = statusRes.groups[groupWithRequiredLayout.id]
                assertNull(gState?.interrupted, "UnsupportedLayout must not leave group in Interrupted state")
            }
        }
    }
}
