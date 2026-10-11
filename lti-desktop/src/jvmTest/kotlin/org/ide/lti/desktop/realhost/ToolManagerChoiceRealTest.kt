package org.ide.lti.desktop.realhost

import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.ide.lti.core.data.tools.RemoteCommandRunner
import org.ide.lti.core.data.tools.ToolSettingsFile
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ReleaseVersion
import org.ide.lti.core.domain.setup.ToolGroup
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolOutput
import org.ide.lti.core.domain.setup.ToolOutputKind
import org.ide.lti.core.domain.setup.ToolProbe
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.OperationResult
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.domain.tools.ToolManager
import org.ide.lti.core.model.setup.ToolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Real-host test verifying ToolManager choice recording, followStudio,
 * trust check, curated-release constraints, and busy gating (T062 / T069).
 */
class ToolManagerChoiceRealTest {
    private fun hostPath(host: RealToolHost, sub: String = ""): String = host.hostPath(sub)

    @Test
    fun installAndUseAndUseSavePickOnlyAfterVerificationFailingBuildLeavesSettingsUnchanged(): Unit = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repoPath = hostPath(host, "choice-save-repo")
            val commit1 =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "choice-tool",
                    probeExitCode = 0,
                    tag = "v1.0.0",
                ).getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "choice-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit1,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                val sFile = ToolSettingsFile(RemoteCommandRunner(host.transport), hostPath(host, "LtiRomTools"))

                // 1. installAndUse valid v1.0.0 -> Saves pick in settings.json
                println("[TEST1] Step 1: installAndUse v1.0.0")
                val res1 =
                    host.manager.installAndUse(
                        group = group.id,
                        source = ResolvedSource.Git(repoPath, ToolRef.Tag("v1.0.0"), commit1),
                    )
                if (res1 is OperationResult.Failed) {
                    error("res1 FAILED: error=${res1.error}, msg=${res1.message}")
                }
                assertIs<OperationResult.Installed>(res1)
                assertEquals("v1.0.0", res1.versionId)

                val picks1 = sFile.readSelections().getOrThrow()
                assertEquals(1, picks1.size)
                assertEquals("v1.0.0", picks1[group.id]?.first)
                println("[TEST1] Step 1 passed")

                // 2. Failing build (probe exit code 42) -> Fails verification, leaves settings.json unchanged
                println("[TEST1] Step 2: setup fixture v2.0.0")
                val commit2 =
                    Fixtures.setupWorkingCMakeToolFixture(
                        transport = host.transport,
                        repoPath = repoPath,
                        toolName = "choice-tool",
                        probeExitCode = 42,
                        tag = "v2.0.0",
                    ).getOrThrow()

                println("[TEST1] Step 2: installAndUse v2.0.0")
                val res2 =
                    host.manager.installAndUse(
                        group = group.id,
                        source = ResolvedSource.Git(repoPath, ToolRef.Tag("v2.0.0"), commit2),
                    )
                if (res2 is OperationResult.Failed) {
                    println("[TEST1] res2.error is: ${res2.error::class.simpleName}: ${res2.error.message}")
                }
                assertIs<OperationResult.Failed>(res2)
                assertTrue(
                    res2.error is ToolError.BuildFailed || res2.error is ToolError.VerifyFailed,
                    "Expected BuildFailed or VerifyFailed but got ${res2.error}",
                )

                val picks2 = sFile.readSelections().getOrThrow()
                assertEquals(1, picks2.size)
                assertEquals(
                    "v1.0.0",
                    picks2[group.id]?.first,
                    "settings.json must stay unchanged after failing verification",
                )
                println("[TEST1] Step 2 passed")

                // 3. Install valid v3.0.0 with choice = Keep (not recorded yet)
                println("[TEST1] Step 3: setup fixture v3.0.0")
                val commit3 =
                    Fixtures.setupWorkingCMakeToolFixture(
                        transport = host.transport,
                        repoPath = repoPath,
                        toolName = "choice-tool",
                        probeExitCode = 0,
                        tag = "v3.0.0",
                    ).getOrThrow()

                println("[TEST1] Step 3: install v3.0.0")
                val req3 =
                    InstallRequest(
                        opId = "op-v3",
                        group = group.id,
                        label = "v3.0.0",
                        source = ResolvedSource.Git(repoPath, ToolRef.Tag("v3.0.0"), commit3),
                        recipe = group.recipe,
                        outputs = group.outputs,
                        select = false,
                        choice = ChoiceUpdate.Keep,
                    )
                val installEvents = host.store.install(req3).toList()
                val installRes3 = installEvents.filterIsInstance<InstallEvent.Finished>().first().result
                assertIs<OperationResult.Installed>(installRes3)
                println("[TEST1] Step 3 passed")

                // Now use v3.0.0 with Record -> updates pick in settings.json
                println("[TEST1] Step 4: use v3.0.0 Record")
                val res3 = host.manager.use(group.id, "v3.0.0", ChoiceUpdate.Record)
                assertIs<OperationResult.Used>(res3)
                assertEquals("v3.0.0", res3.versionId)

                val picks3 = sFile.readSelections().getOrThrow()
                assertEquals("v3.0.0", picks3[group.id]?.first)

                // 4. Failing use (nonexistent version) -> Fails, leaves settings.json unchanged at v3.0.0
                println("[TEST1] Step 5: use nonexistent")
                val res4 = host.manager.use(group.id, "nonexistent-99", ChoiceUpdate.Record)
                assertIs<OperationResult.Failed>(res4)

                val picks4 = sFile.readSelections().getOrThrow()
                assertEquals(
                    "v3.0.0",
                    picks4[group.id]?.first,
                    "settings.json must stay unchanged after failing use",
                )
                println("[TEST1] Step 5 passed")
            }
        }
    }

    @Test
    fun followStudioReusesOrBuildsStudioPinnedVersionThenRemovesPick(): Unit = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repoPath = hostPath(host, "follow-studio-repo")
            val commitPinned =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "follow-tool",
                    probeExitCode = 0,
                    tag = "v1.0.0",
                ).getOrThrow()

            val commitCustom =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "follow-tool",
                    probeExitCode = 0,
                    tag = "v2.0.0",
                ).getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "follow-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commitPinned,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                val sFile = ToolSettingsFile(RemoteCommandRunner(host.transport), hostPath(host, "LtiRomTools"))

                // Step 1: Install and use custom version v2.0.0 (creates a pick)
                val installCustom =
                    host.manager.installAndUse(
                        group = group.id,
                        source = ResolvedSource.Git(repoPath, ToolRef.Tag("v2.0.0"), commitCustom),
                    )
                assertIs<OperationResult.Installed>(installCustom)
                assertEquals("v2.0.0", installCustom.versionId)
                assertEquals("v2.0.0", sFile.readSelections().getOrThrow()[group.id]?.first)

                // Step 2: followStudio when pinned version is NOT yet installed -> Builds pinned version, sets current, removes pick
                val followRes1 = host.manager.followStudio(group.id)
                if (followRes1 is OperationResult.Failed) {
                    error("followRes1 FAILED: error=${followRes1.error}, msg=${followRes1.message}")
                }
                assertIs<OperationResult.Installed>(followRes1)
                assertEquals("v1.0.0", followRes1.versionId)

                // Pick is removed from settings.json!
                val picksAfter1 = sFile.readSelections().getOrThrow()
                assertNull(picksAfter1[group.id], "followStudio must remove pick from settings.json")

                // Current link points to v1.0.0
                val currLink1 =
                    host.transport.execute(
                        ToolExecutionRequest(
                            "readlink",
                            listOf(hostPath(host, "LtiRomTools/bin/${group.id.value}/current")),
                            "/tmp",
                        ),
                    ).stdout.trim()
                assertEquals("v1.0.0", currLink1)

                // Step 3: Switch back to v2.0.0 with Record (recreating pick)
                val useV2 = host.manager.use(group.id, "v2.0.0", ChoiceUpdate.Record)
                assertIs<OperationResult.Used>(useV2)
                assertEquals("v2.0.0", sFile.readSelections().getOrThrow()[group.id]?.first)

                // Step 4: followStudio when pinned version IS already installed -> Reuses intact pinned version, removes pick
                val followRes2 = host.manager.followStudio(group.id)
                assertIs<OperationResult.Used>(followRes2)
                assertEquals("v1.0.0", followRes2.versionId)

                val picksAfter2 = sFile.readSelections().getOrThrow()
                assertNull(picksAfter2[group.id], "followStudio must remove pick from settings.json when reusing")

                val currLink2 =
                    host.transport.execute(
                        ToolExecutionRequest(
                            "readlink",
                            listOf(hostPath(host, "LtiRomTools/bin/${group.id.value}/current")),
                            "/tmp",
                        ),
                    ).stdout.trim()
                assertEquals("v1.0.0", currLink2)
            }
        }
    }

    @Test
    fun repoUrlOtherThanStudioWithoutTrustRequiresTrustAndNothingIsCloned(): Unit = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val studioRepoPath = hostPath(host, "studio-recommended-repo")
            val forkRepoPath = hostPath(host, "user-custom-fork-repo")

            val commitStudio =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = studioRepoPath,
                    toolName = "trust-tool",
                    probeExitCode = 0,
                    tag = "v1.0.0",
                ).getOrThrow()

            val commitFork =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = forkRepoPath,
                    toolName = "trust-tool",
                    probeExitCode = 0,
                    tag = "v1.0.0-fork",
                ).getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "trust-tool",
                    repoUrl = studioRepoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commitStudio,
                )

            val testSelectionRepo =
                object : org.ide.lti.core.domain.repository.setup.ToolchainSelectionRepository {
                    val trustedUrls = mutableSetOf<String>()

                    override suspend fun recordTrust(repoUrl: String): Result<Unit> {
                        trustedUrls.add(repoUrl)
                        return Result.success(Unit)
                    }

                    override suspend fun isTrusted(repoUrl: String): Boolean = trustedUrls.contains(repoUrl)
                }

            val managerWithTrust =
                ToolManager(
                    port = host.store,
                    catalog = ToolGroupCatalog,
                    selectionRepository = testSelectionRepo,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                // 1. Without trust: untrusted fork repo -> TrustRequired, nothing is cloned
                val resUntrusted =
                    managerWithTrust.installAndUse(
                        group = group.id,
                        source = ResolvedSource.Git(forkRepoPath, ToolRef.Tag("v1.0.0-fork"), commitFork),
                    )
                assertIs<OperationResult.TrustRequired>(resUntrusted)
                assertEquals(forkRepoPath, resUntrusted.url)

                // Verify nothing was cloned in LtiRomTools/src/trust-tool
                val mirrorCheck =
                    host.transport.execute(
                        ToolExecutionRequest(
                            "[",
                            listOf("-d", hostPath(host, "LtiRomTools/src/${group.id.value}")),
                            "/tmp",
                        ),
                    )
                assertTrue(mirrorCheck.exitCode != 0, "No mirror or git repo must be cloned when trust is required")

                // 2. Record trust -> Now installAndUse proceeds and installs successfully
                testSelectionRepo.recordTrust(forkRepoPath)

                val resTrusted =
                    managerWithTrust.installAndUse(
                        group = group.id,
                        source = ResolvedSource.Git(forkRepoPath, ToolRef.Tag("v1.0.0-fork"), commitFork),
                    )
                if (resTrusted is OperationResult.Failed) {
                    error("resTrusted FAILED: error=${resTrusted.error}, msg=${resTrusted.message}")
                }
                assertIs<OperationResult.Installed>(resTrusted)
                assertEquals("v1.0.0-fork", resTrusted.versionId)
            }
        }
    }

    @Test
    fun releasesAcceptedOnlyWhenCurated(): Unit = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val curatedVer =
                ReleaseVersion(
                    version = "2.97.0",
                    url = "https://example.com/downloads/v2.97.0/tool.tar.gz",
                    archiveMember = "tool",
                    sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                )
            val releaseGroup =
                ToolGroup(
                    id = ToolGroupId("curated-release-tool"),
                    source = ToolSource.Release,
                    recipe = RecipeConfig.Release(versions = listOf(curatedVer)),
                    layout = emptyList(),
                    outputs =
                    listOf(
                        ToolOutput(
                            toolId = "curated-release-tool",
                            file = "tool",
                            kind = ToolOutputKind.NATIVE,
                            probe = ToolProbe(listOf("--version")),
                        ),
                    ),
                    buildPackages = emptyList(),
                )

            val gitGroup =
                Fixtures.createTestToolGroup(
                    id = "git-only-tool",
                    repoUrl = "https://example.com/repo.git",
                    ref = ToolRef.Tag("v1.0.0"),
                )

            ToolGroupCatalog.withIsolatedTestGroups(listOf(releaseGroup, gitGroup)) {
                // A: Uncurated version on release group -> Failed(NotFound)
                val uncuratedVersion =
                    ResolvedSource.Release(
                        version = "2.98.0",
                        url = curatedVer.url,
                        sha256 = curatedVer.sha256,
                        member = curatedVer.archiveMember,
                    )
                val res1 = host.manager.installAndUse(releaseGroup.id, uncuratedVersion)
                assertIs<OperationResult.Failed>(res1)
                assertIs<ToolError.NotFound>(res1.error)

                // B: Uncurated SHA-256 on release group -> Failed(NotFound)
                val uncuratedSha =
                    ResolvedSource.Release(
                        version = "2.97.0",
                        url = curatedVer.url,
                        sha256 = "different-sha-256",
                        member = curatedVer.archiveMember,
                    )
                val res2 = host.manager.installAndUse(releaseGroup.id, uncuratedSha)
                assertIs<OperationResult.Failed>(res2)
                assertIs<ToolError.NotFound>(res2.error)

                // C: Git source on release group -> Failed(Internal)
                val gitSource =
                    ResolvedSource.Git(
                        repoUrl = "https://example.com/repo.git",
                        ref = ToolRef.Tag("v1.0.0"),
                        commit = "abcdef1234567890abcdef1234567890abcdef12",
                    )
                val res3 = host.manager.installAndUse(releaseGroup.id, gitSource)
                assertIs<OperationResult.Failed>(res3)
                assertIs<ToolError.Internal>(res3.error)

                // D: Release source on Git group -> Failed(Internal)
                val res4 = host.manager.installAndUse(gitGroup.id, uncuratedVersion)
                assertIs<OperationResult.Failed>(res4)
                assertIs<ToolError.Internal>(res4.error)
            }
        }
    }

    @Test
    fun whileSlowFixtureBuildRunsConcurrentMutationsForGroupReturnBusy(): Unit = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repoPath = hostPath(host, "slow-busy-repo")
            val commit =
                Fixtures.setupSlowCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "busy-tool",
                    delaySeconds = 4,
                    tag = "v1.0.0",
                ).getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "busy-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                val source = ResolvedSource.Git(repoPath, ToolRef.Tag("v1.0.0"), commit)

                // Launch slow build in background coroutine
                val slowJob =
                    async(Dispatchers.IO) {
                        host.manager.installAndUse(group.id, source)
                    }

                // Wait until activeOperations has this group
                var attempts = 0
                while (!host.manager.state.value.activeOperations.containsKey(group.id) && attempts < 50) {
                    delay(50)
                    attempts++
                }
                assertTrue(
                    host.manager.state.value.activeOperations.containsKey(group.id),
                    "Operation must be registered as active during slow build",
                )

                // 1. Concurrent installAndUse -> Busy
                val busyInstall = host.manager.installAndUse(group.id, source)
                assertIs<OperationResult.Failed>(busyInstall)
                assertIs<ToolError.Busy>(busyInstall.error)

                // 2. Concurrent use -> Busy
                val busyUse = host.manager.use(group.id, "v1.0.0")
                assertIs<OperationResult.Failed>(busyUse)
                assertIs<ToolError.Busy>(busyUse.error)

                // 3. Concurrent repair -> Busy
                val busyRepair = host.manager.repair(group.id)
                assertIs<OperationResult.Failed>(busyRepair)
                assertIs<ToolError.Busy>(busyRepair.error)

                // 4. Concurrent remove -> Busy
                val busyRemove = host.manager.remove(group.id, "v1.0.0")
                assertIs<OperationResult.Failed>(busyRemove)
                assertIs<ToolError.Busy>(busyRemove.error)

                // Wait for the slow build to finish
                val finishRes = slowJob.await()
                if (finishRes is OperationResult.Failed) {
                    error("finishRes FAILED: error=${finishRes.error}, msg=${finishRes.message}")
                }
                assertIs<OperationResult.Installed>(finishRes)
                assertEquals("v1.0.0", finishRes.versionId)

                // After completion, activeOperations no longer contains group
                assertFalse(host.manager.state.value.activeOperations.containsKey(group.id))
            }
        }
    }
}
