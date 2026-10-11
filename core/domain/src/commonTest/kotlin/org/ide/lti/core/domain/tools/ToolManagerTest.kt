/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.tools

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.model.setup.ToolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToolManagerTest {
    private class FakeToolStorePort : ToolStorePort {
        var initCalled = false
        var statusCalls = 0
        var inventory = ToolInventory(emptyMap())
        val installedRequests = mutableListOf<InstallRequest>()
        var installOutcome: (InstallRequest) -> OperationResult = { req ->
            OperationResult.Installed("inst-${req.opId}")
        }

        var initResult: Result<InitInfo> = Result.success(InitInfo())
        var holdInstall: CompletableDeferred<Unit>? = null

        override suspend fun init(): Result<InitInfo> {
            initCalled = true
            return initResult
        }

        /** The next status read waits here (after taking its snapshot), like a slow disk read. */
        var statusGate: CompletableDeferred<Unit>? = null

        override suspend fun status(catalog: ToolGroupCatalog): Result<ToolInventory> {
            statusCalls += 1
            val snapshot = inventory
            statusGate?.also { statusGate = null }?.await()
            return Result.success(snapshot)
        }

        override suspend fun refs(group: ToolGroupId, repoUrl: String): Result<RefListing> =
            Result.success(RefListing(emptyList(), emptyList()))

        override suspend fun resolve(group: ToolGroupId, repoUrl: String, ref: ToolRef): Result<ResolvedSourceCheck> =
            Result.success(ResolvedSourceCheck(commit = "1" * 40))

        override fun install(request: InstallRequest): Flow<InstallEvent> = flow {
            installedRequests.add(request)
            emit(InstallEvent.Started(request.opId))
            holdInstall?.await()
            val outcome = installOutcome(request)
            emit(InstallEvent.Finished(outcome))
        }

        val useCalls = mutableListOf<Pair<ToolGroupId, String>>()

        override suspend fun use(group: ToolGroupId, versionId: String, choice: ChoiceUpdate): Result<GroupLinks> {
            useCalls += group to versionId
            return Result.success(GroupLinks(versionId, null))
        }

        override suspend fun remove(group: ToolGroupId, versionId: String): Result<Unit> = Result.success(Unit)

        override suspend fun paths(catalog: ToolGroupCatalog): Result<PinnedTools> = Result.success(
            PinnedTools(emptyMap(), emptyMap()),
        )
    }

    private fun testCatalog(): ToolGroupCatalog = ToolGroupCatalog

    @Test
    fun `prepareMissing calls init first and installs uninitialized groups with recommended source`() = runTest {
        val port = FakeToolStorePort()
        val manager = ToolManager(port = port, catalog = testCatalog())

        val summary = manager.prepareMissing()
        assertTrue(port.initCalled, "prepareMissing must call port.init() first")
        assertTrue(summary.isComplete, "All groups should be installed successfully")
        assertEquals(7, port.installedRequests.size, "Must install all 7 catalog groups")

        // First group is android-tools
        val firstReq = port.installedRequests.first()
        assertEquals("android-tools", firstReq.group.value)
        val src = firstReq.source as ResolvedSource.Git
        assertEquals("https://github.com/UN1CA/external_android-tools.git", src.repoUrl)
    }

    @Test
    fun `prepareMissing builds the targets the confirmed plan froze, not what the catalog says now`() = runTest {
        val port = FakeToolStorePort()
        val manager = ToolManager(port = port, catalog = testCatalog())
        val group = ToolGroupCatalog.allGroups.first()
        val frozen = ResolvedSource.Git("https://example.com/frozen.git", ToolRef.Commit("a" * 40), "a" * 40)

        manager.prepareMissing(targets = mapOf(group.id to frozen))

        assertEquals(frozen, port.installedRequests.first { it.group == group.id }.source)
        val others = port.installedRequests.filter { it.group != group.id }
        assertEquals(6, others.size, "groups without a frozen target still follow their current target")
    }

    @Test
    fun `a group that is intact on disk is registered with the service again, not just reported reused`() = runTest {
        val port = FakeToolStorePort()
        val group = ToolGroupCatalog.EROFS_UTILS
        val source = ToolManager(port = port, catalog = testCatalog()).defaultSourceForGroup(group)
        val version =
            InstalledVersion(group.id, "v1", "v1", source, RecipeSummary("cmake", 1), emptyList(), "1", true)
        port.inventory = ToolInventory(mapOf(group.id to GroupState(group.id, version, null, listOf(version))))
        val manager = ToolManager(port = port, catalog = testCatalog())

        val summary = manager.prepareMissing()

        assertEquals(GroupSetupResult.Reused("v1"), summary.groupResults[group.id])
        assertTrue(
            (group.id to "v1") in port.useCalls,
            "switching to the version in use is what registers its tools",
        )
        assertTrue(port.installedRequests.none { it.group == group.id }, "nothing is rebuilt")
    }

    @Test
    fun `auto-tools skips a group the user is already installing and leaves that operation alone`() = runTest {
        val port = FakeToolStorePort()
        val manager = ToolManager(port = port, catalog = testCatalog())
        manager.refresh()
        val group = ToolGroupCatalog.allGroups.first()
        port.holdInstall = CompletableDeferred()
        val userInstall = launch { manager.installAndUse(group.id, manager.defaultSourceForGroup(group)) }
        runCurrent()
        port.holdInstall = null

        val summary = manager.prepareMissing()

        val result = summary.groupResults[group.id]
        assertTrue(result is GroupSetupResult.Failed && result.error is ToolError.Busy, "got $result")
        assertTrue(group.id in manager.state.value.activeOperations, "the user's install must still be tracked")
        assertEquals(1, port.installedRequests.count { it.group == group.id })
        userInstall.cancel()
    }

    @Test
    fun `prepareMissing logs deleted old tool folder for each entry in InitInfo deletedPaths`() = runTest {
        val port = FakeToolStorePort()
        port.initResult =
            Result.success(
                InitInfo(deletedPaths = listOf("/home/u/LtiRomTools/bin.old-1", "/home/u/LtiRomTools/bin")),
            )
        val events = mutableListOf<String>()
        val manager = ToolManager(port = port, catalog = testCatalog())
        manager.prepareMissing { _, ev ->
            if (ev is InstallEvent.Output) events.add(ev.text)
        }
        assertTrue(events.contains("[tools] Deleted old tool folder /home/u/LtiRomTools/bin.old-1"))
        assertTrue(events.contains("[tools] Deleted old tool folder /home/u/LtiRomTools/bin"))
    }

    @Test
    fun `prepareMissing repairs damaged current selection from recorded source with forceNew`() = runTest {
        val port = FakeToolStorePort()
        val customSource =
            ResolvedSource.Git(
                repoUrl = "https://github.com/custom/android-tools.git",
                ref = ToolRef.Commit("2222222222222222222222222222222222222222"),
                commit = "2222222222222222222222222222222222222222",
            )
        val damagedVersion =
            InstalledVersion(
                group = ToolGroupId("android-tools"),
                id = "v-damaged",
                label = "custom",
                source = customSource,
                recipe = RecipeSummary("android_tools", 1),
                outputs = emptyList(),
                builtAt = "2026-10-01T00:00:00Z",
                intact = false,
            )

        // Android-tools is damaged; others are not installed
        port.inventory =
            ToolInventory(
                mapOf(
                    ToolGroupId("android-tools") to
                        GroupState(
                            group = ToolGroupId("android-tools"),
                            current = damagedVersion,
                            installed = listOf(damagedVersion),
                        ),
                ),
            )

        val manager = ToolManager(port = port, catalog = testCatalog())
        manager.prepareMissing()

        val androidReq = port.installedRequests.firstOrNull { it.group.value == "android-tools" }
        assertNotNull(androidReq)
        assertTrue(androidReq.forceNew, "Damaged toolchain must be repaired with forceNew=true")
        assertEquals(customSource, androidReq.source, "Must repair from recorded source, not catalog recommended")
    }

    @Test
    fun `prepareMissing halts on shared blocker like MissingPackages`() = runTest {
        val port = FakeToolStorePort()
        port.installOutcome = { _ ->
            OperationResult.Failed(ToolError.MissingPackages(listOf("cmake")), "Missing cmake")
        }

        val manager = ToolManager(port = port, catalog = testCatalog())
        val summary = manager.prepareMissing()

        kotlin.test.assertFalse(summary.isComplete)
        // First group failed with MissingPackages
        assertEquals(1, summary.failedCount)
        // Remaining 6 groups must be NotAttempted
        assertEquals(6, summary.notAttemptedCount)
    }

    @Test
    fun `second prepareMissing with all intact makes zero install calls`() = runTest {
        val port = FakeToolStorePort()
        val manager = ToolManager(port = port, catalog = testCatalog())
        // Setup inventory with all groups intact
        val intactGroups =
            ToolGroupCatalog.allGroups.associate { group ->
                val v =
                    InstalledVersion(
                        group = group.id,
                        id = "v-intact-${group.id.value}",
                        label = "v1.0",
                        source = manager.defaultSourceForGroup(group),
                        recipe = RecipeSummary("cmake", 1),
                        outputs = emptyList(),
                        builtAt = "2026-10-01",
                        intact = true,
                    )
                group.id to
                    GroupState(
                        group = group.id,
                        current = v,
                        installed = listOf(v),
                    )
            }
        port.inventory = ToolInventory(intactGroups)
        val summary = manager.prepareMissing()

        assertTrue(summary.isComplete)
        assertEquals(
            0,
            port.installedRequests.size,
            "Second prepareMissing with all intact must not install anything",
        )
    }

    @Test
    fun `repair sends current version source with forceNew`() = runTest {
        val port = FakeToolStorePort()
        val customSource =
            ResolvedSource.Git(
                repoUrl = "https://github.com/custom/android-tools.git",
                ref = ToolRef.Commit("3333333333333333333333333333333333333333"),
                commit = "3333333333333333333333333333333333333333",
            )
        val currentVersion =
            InstalledVersion(
                group = ToolGroupId("android-tools"),
                id = "v-custom",
                label = "custom",
                source = customSource,
                recipe = RecipeSummary("android_tools", 1),
                outputs = emptyList(),
                builtAt = "2026-10-01",
                intact = false,
            )
        port.inventory =
            ToolInventory(
                mapOf(
                    ToolGroupId("android-tools") to
                        GroupState(
                            group = ToolGroupId("android-tools"),
                            current = currentVersion,
                            installed = listOf(currentVersion),
                        ),
                ),
            )

        val manager = ToolManager(port = port, catalog = testCatalog())
        val outcome = manager.repair(ToolGroupId("android-tools"))

        assertTrue(outcome is OperationResult.Installed)
        val lastReq = port.installedRequests.last()
        assertTrue(lastReq.forceNew, "repair must use forceNew=true")
        assertEquals(customSource, lastReq.source, "repair must use current version source, not recommended")
    }

    @Test
    fun `use of an installed version makes no install call`() = runTest {
        val port = FakeToolStorePort()
        val manager = ToolManager(port = port, catalog = testCatalog())

        val outcome = manager.use(ToolGroupId("erofs-utils"), "v-123")
        assertTrue(outcome is OperationResult.Used)
        assertEquals(0, port.installedRequests.size, "use must not make install calls")
    }

    @Test
    fun `followStudio reuses intact pinned version if already installed`() = runTest {
        val port = FakeToolStorePort()
        val catalog = testCatalog()
        val group = ToolGroupCatalog.EROFS_UTILS
        val pinnedSource =
            ResolvedSource.Git(
                repoUrl = (group.source as ToolSource.Git).recommendedUrl,
                ref = (group.source as ToolSource.Git).pinnedRef,
                commit = (group.source as ToolSource.Git).pinnedCommit,
            )
        val installedPinned =
            InstalledVersion(
                group = group.id,
                id = "v-pinned",
                label = "pinned",
                source = pinnedSource,
                recipe = RecipeSummary("cmake", 1),
                outputs = emptyList(),
                builtAt = "2026-10-01",
                intact = true,
            )
        port.inventory =
            ToolInventory(
                mapOf(
                    group.id to
                        GroupState(
                            group = group.id,
                            current = installedPinned,
                            installed = listOf(installedPinned),
                        ),
                ),
            )
        val manager = ToolManager(port = port, catalog = catalog)
        val outcome = manager.followStudio(group.id)
        assertTrue(outcome is OperationResult.Used)
    }

    @Test
    fun `tool removal is refused while a ROM job owns the tool set`() = runTest {
        val port = FakeToolStorePort()
        val usage = ToolUsageCoordinator()
        usage.admitJob("rom-1") { Unit }
        val manager = ToolManager(port = port, catalog = testCatalog(), usageCoordinator = usage)

        val outcome = manager.remove(ToolGroupId("erofs-utils"), "v-123")

        assertTrue(outcome is OperationResult.Failed)
        assertTrue((outcome as OperationResult.Failed).error is ToolError.InUse)
    }

    @Test
    fun `installAndUse with untrusted custom URL returns TrustRequired`() = runTest {
        val port = FakeToolStorePort()
        val untrustedUrl = "https://github.com/evil/external_android-tools.git"
        val fakeSelectionRepo =
            object : org.ide.lti.core.domain.repository.setup.ToolchainSelectionRepository {
                override suspend fun recordTrust(repoUrl: String): Result<Unit> = Result.success(Unit)

                override suspend fun isTrusted(repoUrl: String): Boolean = false
            }
        val manager = ToolManager(port = port, catalog = testCatalog(), selectionRepository = fakeSelectionRepo)

        val customSource =
            ResolvedSource.Git(
                repoUrl = untrustedUrl,
                ref = ToolRef.Commit("4" * 40),
                commit = "4" * 40,
            )
        val outcome = manager.installAndUse(ToolGroupId("android-tools"), customSource)
        assertTrue(outcome is OperationResult.TrustRequired)
        assertEquals(untrustedUrl, outcome.url)
        assertEquals(0, port.installedRequests.size, "Untrusted source must not trigger install")
    }

    @Test
    fun `setup does not build a saved fork the user has not trusted, and builds it once trusted`() = runTest {
        val group = ToolGroupCatalog.ANDROID_TOOLS
        val fork = ResolvedSource.Git("https://github.com/someone/fork.git", ToolRef.Commit("5" * 40), "5" * 40)
        val trusted = mutableSetOf<String>()
        val repo =
            object : org.ide.lti.core.domain.repository.setup.ToolchainSelectionRepository {
                override suspend fun recordTrust(repoUrl: String): Result<Unit> =
                    Result.success(trusted.add(repoUrl)).map { }

                override suspend fun isTrusted(repoUrl: String): Boolean = repoUrl in trusted
            }
        val port = FakeToolStorePort()
        port.inventory =
            ToolInventory(mapOf(group.id to GroupState(group.id, null, Selection("fork-v", fork), emptyList())))
        val manager = ToolManager(port = port, catalog = testCatalog(), selectionRepository = repo)

        val blocked = manager.prepareMissing().groupResults.getValue(group.id)

        assertTrue(blocked is GroupSetupResult.Failed, "was $blocked")
        assertTrue("Trust required" in (blocked as GroupSetupResult.Failed).error.message, blocked.error.message)
        assertTrue(port.installedRequests.none { it.group == group.id }, "an untrusted source is never built")

        manager.recordTrust(fork.repoUrl)
        manager.prepareMissing()

        assertEquals(fork, port.installedRequests.last { it.group == group.id }.source)
    }

    @Test
    fun `a slow refresh that started before a switch cannot bring the old state back`() = runTest {
        val group = ToolGroupCatalog.EROFS_UTILS
        val port = FakeToolStorePort()
        val source = ResolvedSource.Git("u", ToolRef.Commit("1" * 40), "1" * 40)
        val old =
            InstalledVersion(group.id, "v-old", "v-old", source, RecipeSummary("cmake", 1), emptyList(), "1", true)
        val fresh = old.copy(id = "v-new", label = "v-new")

        fun inventoryWithCurrent(current: InstalledVersion) =
            ToolInventory(mapOf(group.id to GroupState(group.id, current, null, listOf(old, fresh))))
        val manager = ToolManager(port = port, catalog = testCatalog())

        port.inventory = inventoryWithCurrent(old)
        val gate = CompletableDeferred<Unit>().also { port.statusGate = it }
        val slow = launch { manager.refresh() } // reads "v-old", then stalls
        runCurrent()

        port.inventory = inventoryWithCurrent(fresh) // the switch happens
        manager.refresh() // and a refresh that started afterwards finishes first
        assertEquals("v-new", manager.state.value.inventory!!.groups.getValue(group.id).current!!.id)

        gate.complete(Unit)
        slow.join() // the old read arrives late

        assertEquals(
            "v-new",
            manager.state.value.inventory!!.groups.getValue(group.id).current!!.id,
            "the late, older read must not publish",
        )
    }

    @Test
    fun `a saved trust that fails is reported, and tool controls can follow a ROM job as a flow`() = runTest {
        val failing =
            object : org.ide.lti.core.domain.repository.setup.ToolchainSelectionRepository {
                override suspend fun recordTrust(repoUrl: String): Result<Unit> =
                    Result.failure(IllegalStateException("no space"))

                override suspend fun isTrusted(repoUrl: String): Boolean = false
            }
        assertTrue(
            ToolManager(port = FakeToolStorePort(), selectionRepository = failing).recordTrust("u").isFailure,
        )
        assertTrue(
            ToolManager(port = FakeToolStorePort()).recordTrust("u").isSuccess,
            "no repository: nothing to save",
        )

        val usage = ToolUsageCoordinator()
        val manager = ToolManager(port = FakeToolStorePort(), usageCoordinator = usage)
        assertEquals(false, manager.romJobActive.value)
        usage.admitJob("rom-1") { Unit }
        assertEquals(true, manager.romJobActive.value)
        usage.finishJob("rom-1")
        assertEquals(false, manager.romJobActive.value)
    }

    @Test
    fun `a saved pick whose folder name exists but whose source differs is built, not reported ready`() = runTest {
        val group = ToolGroupCatalog.EROFS_UTILS
        val manager0 = ToolManager(port = FakeToolStorePort(), catalog = testCatalog())
        val installedSource = manager0.defaultSourceForGroup(group) as ResolvedSource.Git
        val pickedSource = installedSource.copy(ref = ToolRef.Commit("9" * 40), commit = "9" * 40)
        val version =
            InstalledVersion(
                group.id,
                "v1",
                "v1",
                installedSource,
                RecipeSummary("cmake", 1),
                emptyList(),
                "1",
                true,
            )
        val port = FakeToolStorePort()
        port.inventory =
            ToolInventory(
                mapOf(group.id to GroupState(group.id, version, Selection("v1", pickedSource), listOf(version))),
            )

        val summary = ToolManager(port = port, catalog = testCatalog()).prepareMissing()

        assertTrue(
            summary.groupResults[group.id] !is GroupSetupResult.Reused,
            "same folder name, different commit: the wrong code must not pass as the pick",
        )
        assertEquals(pickedSource, port.installedRequests.single { it.group == group.id }.source)
    }

    @Test
    fun `a release naming another archive member than the curated one is refused`() = runTest {
        val port = FakeToolStorePort()
        val manager = ToolManager(port = port, catalog = testCatalog())
        val curated = (ToolGroupCatalog.GH.recipe as RecipeConfig.Release).versions.first()
        val tampered =
            ResolvedSource.Release(curated.version, curated.url, curated.sha256, member = "some/other/binary")

        val outcome = manager.installAndUse(ToolGroupCatalog.GH.id, tampered)

        assertTrue(outcome is OperationResult.Failed, outcome.toString())
        assertEquals(0, port.installedRequests.size)
    }

    @Test
    fun `a git source that names a branch instead of a commit is refused`() = runTest {
        val port = FakeToolStorePort()
        val repo = "https://github.com/UN1CA/external_android-tools.git"
        val source = ResolvedSource.Git(repo, ToolRef.Branch("main"), "main")
        val manager = ToolManager(port = port, catalog = testCatalog())

        val outcome = manager.installAndUse(ToolGroupId("android-tools"), source)

        assertTrue(outcome is OperationResult.Failed, outcome.toString())
        assertEquals(0, port.installedRequests.size)
    }
}

private operator fun String.times(n: Int): String = repeat(n)
