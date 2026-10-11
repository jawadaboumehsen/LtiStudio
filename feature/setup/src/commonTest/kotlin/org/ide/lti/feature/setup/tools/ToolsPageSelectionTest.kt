/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.tools

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.repository.setup.ToolchainSelectionRepository
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.GroupLinks
import org.ide.lti.core.domain.tools.GroupState
import org.ide.lti.core.domain.tools.InitInfo
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.InstalledVersion
import org.ide.lti.core.domain.tools.OperationResult
import org.ide.lti.core.domain.tools.PinnedTools
import org.ide.lti.core.domain.tools.RecipeSummary
import org.ide.lti.core.domain.tools.RefListing
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ResolvedSourceCheck
import org.ide.lti.core.domain.tools.TagRefInfo
import org.ide.lti.core.domain.tools.ToolInventory
import org.ide.lti.core.domain.tools.ToolManager
import org.ide.lti.core.domain.tools.ToolStorePort
import org.ide.lti.core.domain.tools.ToolUsageCoordinator
import org.ide.lti.core.model.setup.ToolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What an install is built from, and what happens to answers that arrive late. */
class ToolsPageSelectionTest {
    private val erofs = ToolGroupId("erofs-utils")
    private val android = ToolGroupId("android-tools")
    private val recommendedErofs =
        ToolGroupCatalog.group(erofs)!!.let {
            (it.source as org.ide.lti.core.domain.setup.ToolSource.Git).recommendedUrl
        }

    private class Port : ToolStorePort {
        val gates = mutableMapOf<ToolGroupId, CompletableDeferred<Unit>>()
        val installs = mutableListOf<InstallRequest>()
        var useResult: Result<GroupLinks> = Result.success(GroupLinks("x", null))

        override suspend fun init() = Result.success(InitInfo())

        var inventory = ToolInventory(emptyMap())

        override suspend fun status(catalog: ToolGroupCatalog) = Result.success(inventory)

        val refsAsked = mutableListOf<Pair<ToolGroupId, String>>()
        var refsFailure: Throwable? = null

        override suspend fun refs(group: ToolGroupId, repoUrl: String): Result<RefListing> {
            refsAsked += group to repoUrl
            refsFailure?.let { return Result.failure(it) }
            return Result.success(RefListing(listOf(TagRefInfo("tag-of-$repoUrl", "a".repeat(40))), emptyList()))
        }

        val cancelled = mutableListOf<ToolGroupId>()

        override suspend fun cancel(group: ToolGroupId, budgetMs: Long): Result<Unit> {
            cancelled += group
            return Result.success(Unit)
        }

        override suspend fun resolve(group: ToolGroupId, repoUrl: String, ref: ToolRef): Result<ResolvedSourceCheck> {
            gates[group]?.await()
            return Result.success(ResolvedSourceCheck(commit = (group.value.first().code % 10).toString().repeat(40)))
        }

        var installGate: CompletableDeferred<Unit>? = null

        override fun install(request: InstallRequest): Flow<InstallEvent> = flow {
            installs += request
            emit(InstallEvent.Started(request.opId))
            installGate?.await()
            emit(InstallEvent.Finished(OperationResult.Installed("v")))
        }

        override suspend fun use(group: ToolGroupId, versionId: String, choice: ChoiceUpdate) = useResult

        override suspend fun remove(group: ToolGroupId, versionId: String) = Result.success(Unit)

        override suspend fun paths(catalog: ToolGroupCatalog) = Result.success(PinnedTools(emptyMap(), emptyMap()))
    }

    private fun TestScope.page(
        port: Port,
        repo: ToolchainSelectionRepository? = null,
        usage: ToolUsageCoordinator? = null,
    ): ToolsPageState {
        val manager = ToolManager(port, selectionRepository = repo, usageCoordinator = usage)
        runBlocking { manager.refresh() }
        // A child of the test scheduler that the test waits for (backgroundScope tasks never count as pending).
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        backgroundScope.coroutineContext[Job]!!.invokeOnCompletion { scope.cancel() }
        return ToolsPageState(manager, scope)
    }

    @Test
    fun `a resolve answer that arrives after the group changed does not reach the other group`() = runTest {
        val port = Port().also { it.gates[erofs] = CompletableDeferred() }
        val state = page(port)
        state.selectGroup(erofs)
        state.resolveRef(ToolRef.Tag("v1.8.10"), recommendedErofs)
        runCurrent()

        state.selectGroup(android) // the answer for erofs is still on its way
        port.gates.getValue(erofs).complete(Unit)
        advanceUntilIdle()

        assertNull(state.resolvedSelection.value, "erofs' commit must not become android-tools' selection")
        assertNull(state.resolvedCheck.value)
        assertTrue(!state.isResolving.value)
    }

    @Test
    fun `changing the repository drops the resolved commit, and install builds only what was resolved`() = runTest {
        val port = Port()
        val state = page(port)
        state.selectGroup(erofs)
        state.resolveRef(ToolRef.Tag("v1.8.10"), recommendedErofs)
        advanceUntilIdle()
        assertNotNull(state.resolvedSelection.value)

        state.installResolved()
        advanceUntilIdle()
        val source = port.installs.single().source as ResolvedSource.Git
        assertEquals(recommendedErofs, source.repoUrl)
        assertEquals(ToolRef.Tag("v1.8.10"), source.ref)
        assertEquals(state.resolvedSelection.value!!.check.commit, source.commit)

        state.setCustomRepoUrl("https://example.com/other.git")
        assertNull(state.resolvedSelection.value, "the commit belonged to the old repository")
        state.installResolved()
        advanceUntilIdle()
        assertEquals(1, port.installs.size, "nothing resolved means nothing is installed")
    }

    @Test
    fun `a failed change is shown for its group`() = runTest {
        val port = Port().also { it.useResult = Result.failure(IllegalStateException("link failed")) }
        val state = page(port)
        state.selectGroup(erofs)

        state.use("v1.8.10")
        advanceUntilIdle()

        val error = assertNotNull(state.actionError.value)
        assertEquals(erofs, error.group)
        state.selectGroup(android)
        assertNull(state.actionError.value, "another group does not inherit the message")
    }

    @Test
    fun `trusting a repository runs the action that asked for it`() = runTest {
        val trusted = mutableSetOf<String>()
        val repo =
            object : ToolchainSelectionRepository {
                override suspend fun recordTrust(repoUrl: String): Result<Unit> {
                    trusted += repoUrl
                    return Result.success(Unit)
                }

                override suspend fun isTrusted(repoUrl: String) = repoUrl in trusted
            }
        val port = Port()
        val state = page(port, repo)
        state.selectGroup(erofs)
        var ran = false

        state.requestWithTrustCheck("https://example.com/fork.git") { ran = true }
        assertTrue(state.showTrustDialog.value)
        assertTrue(!ran, "not before the user agrees")

        state.confirmTrustAndProceed()
        advanceUntilIdle()

        assertTrue(ran, "agreeing must carry on with the original request")
        assertTrue("https://example.com/fork.git" in trusted)
    }

    @Test
    fun `dismissing the trust dialog forgets the action`() = runTest {
        val repo =
            object : ToolchainSelectionRepository {
                override suspend fun recordTrust(repoUrl: String) = Result.success(Unit)

                override suspend fun isTrusted(repoUrl: String) = false
            }
        val state = page(Port(), repo)
        var ran = false

        state.requestWithTrustCheck("https://example.com/fork.git") { ran = true }
        state.dismissTrustDialog()
        state.confirmTrustAndProceed()
        advanceUntilIdle()

        assertTrue(!ran)
    }

    @Test
    fun `a build in progress shows in its row and in the inspector, not only after it is done`() = runTest {
        val port = Port().also { it.installGate = CompletableDeferred() }
        val state = page(port)
        val group = ToolGroupCatalog.allGroups.first()
        state.selectGroup(group.id)
        assertEquals(ToolRowStatusKind.NotInstalled, state.rows.value.first { it.groupId == group.id.value }.statusKind)

        state.installAndUse(state.manager.defaultSourceForGroup(group))
        runCurrent()

        val row = state.rows.value.first { it.groupId == group.id.value }
        assertEquals(ToolRowStatusKind.Installing, row.statusKind)
        assertTrue(row.canCancel, "a running build can be cancelled from its row")
        assertNotNull(state.selectedGroupState.value?.operation, "the inspector sees the phase too")
        assertTrue(state.isGroupInstalling(group.id))

        port.installGate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(state.rows.value.first { it.groupId == group.id.value }.statusKind != ToolRowStatusKind.Installing)
    }

    @Test
    fun `the repository typed for one tool does not follow to the next`() = runTest {
        val state = page(Port())
        state.selectGroup(erofs)
        state.setCustomRepoUrl("https://example.com/erofs-fork.git")
        assertEquals("https://example.com/erofs-fork.git", state.customRepoUrl.value)

        state.selectGroup(android)

        assertNull(state.customRepoUrl.value)
    }

    @Test
    fun `cancel goes to the group that was clicked even if the selection moves before it runs`() = runTest {
        val port = Port()
        val state = page(port)
        state.selectGroup(erofs)

        state.cancelActiveOperation()
        state.selectGroup(android) // before the cancel coroutine gets to run
        advanceUntilIdle()

        assertEquals(listOf(erofs), port.cancelled)
    }

    @Test
    fun `editing the repository reloads its tags after a pause, and the old repository's tags disappear at once`() =
        runTest {
            val port = Port()
            val state = page(port)
            state.selectGroup(erofs)
            state.loadRefs()
            advanceUntilIdle()
            assertEquals("tag-of-$recommendedErofs", state.availableRefs.value!!.tags.single().name)
            port.refsAsked.clear()

            state.setCustomRepoUrl("https://example.com/f")
            state.setCustomRepoUrl("https://example.com/fo")
            state.setCustomRepoUrl("https://example.com/fork.git")
            assertNull(state.availableRefs.value, "upstream's tags must not stay visible for the fork")

            advanceTimeBy(300)
            assertTrue(port.refsAsked.isEmpty(), "no request while the URL is still being typed")
            advanceUntilIdle()
            assertEquals(
                listOf(erofs to "https://example.com/fork.git"),
                port.refsAsked,
                "one request, for the final URL",
            )
            assertEquals("tag-of-https://example.com/fork.git", state.availableRefs.value!!.tags.single().name)
        }

    @Test
    fun `a failed tag listing is shown with its reason and a retry works`() = runTest {
        val port = Port().also { it.refsFailure = IllegalStateException("network unreachable") }
        val state = page(port)
        state.selectGroup(erofs)

        state.loadRefs()
        advanceUntilIdle()
        assertEquals("network unreachable", state.refsError.value)
        assertNull(state.availableRefs.value)

        port.refsFailure = null
        state.loadRefs()
        advanceUntilIdle()
        assertNull(state.refsError.value, "a retry that works clears the message")
        assertNotNull(state.availableRefs.value)
    }

    @Test
    fun `a trust that could not be saved does not carry on with the request`() = runTest {
        val repo =
            object : ToolchainSelectionRepository {
                override suspend fun recordTrust(repoUrl: String) =
                    Result.failure<Unit>(IllegalStateException("disk full"))

                override suspend fun isTrusted(repoUrl: String) = false
            }
        val state = page(Port(), repo)
        state.selectGroup(erofs)
        var ran = false

        state.requestWithTrustCheck("https://example.com/fork.git") { ran = true }
        state.confirmTrustAndProceed()
        advanceUntilIdle()

        assertTrue(!ran, "going on would only return to 'trust required'")
        assertTrue("disk full" in state.actionError.value!!.message, state.actionError.value!!.message)
    }

    private fun version(id: String, intact: Boolean) = InstalledVersion(
        group = erofs,
        id = id,
        label = id,
        source = ResolvedSource.Git("https://example.com/erofs.git", ToolRef.Commit("1".repeat(40)), "1".repeat(40)),
        recipe = RecipeSummary("cmake", 1),
        outputs = emptyList(),
        builtAt = "1",
        intact = intact,
    )

    @Test
    fun `choosing another version while the current one is damaged installs that one instead of repairing`() = runTest {
        val broken = version("v-broken", intact = false)
        val port = Port().also {
            it.inventory = ToolInventory(mapOf(erofs to GroupState(erofs, broken, null, listOf(broken))))
        }
        val state = page(port)
        state.selectGroup(erofs)
        assertEquals(PrimaryButtonAction.Repair, state.primaryButtonAction.value, "nothing else chosen: repair")

        state.selectTargetVersion("v-other")
        advanceUntilIdle()

        assertEquals(PrimaryButtonAction.InstallAndUse, state.primaryButtonAction.value)
        state.selectTargetVersion("v-broken")
        advanceUntilIdle()
        assertEquals(PrimaryButtonAction.Repair, state.primaryButtonAction.value, "the damaged one itself: repair")
    }

    @Test
    fun `a curated release picked in a release group can be installed from the main button`() = runTest {
        val gh = ToolGroupCatalog.GH
        val port = Port().also {
            it.inventory = ToolInventory(mapOf(gh.id to GroupState(gh.id, null, null, emptyList())))
        }
        val state = page(port)
        state.selectGroup(gh.id)
        val release = state.curatedReleases.first()
        assertTrue(!state.canInstallSelected(), "nothing picked yet")

        state.selectTargetVersion(release.version)
        advanceUntilIdle()
        assertEquals(PrimaryButtonAction.InstallAndUse, state.primaryButtonAction.value)
        assertTrue(state.canInstallSelected())

        state.installResolved()
        advanceUntilIdle()

        val source = port.installs.single().source as ResolvedSource.Release
        assertEquals(release.version, source.version)
        assertEquals(release.sha256, source.sha256)
        state.selectTargetVersion("9.9.9-not-curated")
        assertTrue(!state.canInstallSelected(), "only curated releases can be installed")
    }

    @Test
    fun `starting and ending a ROM job changes the controls without any other event`() = runTest {
        val usage = ToolUsageCoordinator()
        val port = Port().also {
            it.inventory = ToolInventory(mapOf(erofs to GroupState(erofs, null, null, emptyList())))
        }
        val state = page(port, usage = usage)
        state.selectGroup(erofs)
        state.selectTargetVersion("v-new")
        advanceUntilIdle()
        assertEquals(PrimaryButtonAction.InstallAndUse, state.primaryButtonAction.value)

        usage.admitJob("rom-1") { Unit }
        advanceUntilIdle()
        assertEquals(PrimaryButtonAction.Disabled, state.primaryButtonAction.value)
        assertEquals("A ROM job is running", state.primaryButtonDisabledReason.value)

        usage.finishJob("rom-1")
        advanceUntilIdle()
        assertEquals(PrimaryButtonAction.InstallAndUse, state.primaryButtonAction.value)
        assertNull(state.primaryButtonDisabledReason.value)
    }
}
