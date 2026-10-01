/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.versions

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.repository.setup.ToolchainSelectionRepository
import org.ide.lti.core.domain.setup.CompatibilityResult
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.UpdateAvailability
import org.ide.lti.core.domain.setup.ports.ActivationOutcome
import org.ide.lti.core.domain.setup.ports.ActivationRequest
import org.ide.lti.core.domain.setup.ports.ArtifactId
import org.ide.lti.core.domain.setup.ports.CleanupReport
import org.ide.lti.core.domain.setup.ports.InstallId
import org.ide.lti.core.domain.setup.ports.InstalledToolchain
import org.ide.lti.core.domain.setup.ports.RefListing
import org.ide.lti.core.domain.setup.ports.ResolveOutcome
import org.ide.lti.core.domain.setup.ports.SourceResolverPort
import org.ide.lti.core.domain.setup.ports.ToolchainInstallationPort
import org.ide.lti.core.model.setup.DistroToolSelections
import org.ide.lti.core.model.setup.ToolRef
import org.ide.lti.core.model.setup.ToolSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ToolVersionsStateTest {

    private class FakeSelectionRepository : ToolchainSelectionRepository {
        val distroSelections = mutableMapOf<String, DistroToolSelections>()
        val trustedRepos = mutableSetOf<String>()

        override suspend fun desiredSelections(distro: String): DistroToolSelections =
            distroSelections[distro] ?: DistroToolSelections()

        override suspend fun saveSelections(
            distro: String,
            expectedRevision: Long,
            desired: Map<ToolGroupId, ToolSelection>,
        ): Result<Long> {
            val current = distroSelections[distro] ?: DistroToolSelections()
            if (current.revision != expectedRevision) {
                return Result.failure(
                    IllegalStateException("CAS mismatch: expected $expectedRevision but was ${current.revision}"),
                )
            }
            val newRevision = expectedRevision + 1L
            distroSelections[distro] = DistroToolSelections(
                desired = desired.mapKeys { it.key.value },
                revision = newRevision,
            )
            return Result.success(newRevision)
        }

        override suspend fun recordTrust(repoUrl: String): Result<Unit> {
            trustedRepos.add(repoUrl)
            return Result.success(Unit)
        }

        override suspend fun isTrusted(repoUrl: String): Boolean = trustedRepos.contains(repoUrl)
    }

    private class FakeSourceResolver : SourceResolverPort {
        var refListingDelayMs: Long = 0L
        var resolveDelayMs: Long = 0L
        var resolveOutcomeOverride: ResolveOutcome? = null
        var compatibilityOverride: CompatibilityResult = CompatibilityResult.LayoutCompatible
        var updateAvailabilityOverride: UpdateAvailability = UpdateAvailability.UpToDate

        val requestedRefListings = mutableListOf<Pair<ToolGroupId, String?>>()
        val requestedResolutions = mutableListOf<ToolSelection>()

        override suspend fun listRefs(group: ToolGroupId, repoUrl: String?): RefListing {
            requestedRefListings.add(group to repoUrl)
            if (refListingDelayMs > 0) delay(refListingDelayMs)
            return RefListing(tags = listOf("v1.0", "v1.1", "v2.0"), branches = listOf("main", "dev"))
        }

        override suspend fun resolve(selection: ToolSelection): ResolveOutcome {
            requestedResolutions.add(selection)
            if (resolveDelayMs > 0) delay(resolveDelayMs)
            resolveOutcomeOverride?.let { return it }
            val commit = when (val ref = selection.ref) {
                is ToolRef.Commit -> ref.sha
                is ToolRef.Tag -> "1111111111111111111111111111111111111111"
                is ToolRef.Branch -> "2222222222222222222222222222222222222222"
                is ToolRef.ReleaseVersion -> "3333333333333333333333333333333333333333"
            }
            return ResolveOutcome.Resolved(
                ResolvedInput.Git(
                    group = ToolGroupId(selection.group),
                    repoUrl = selection.repoUrl,
                    ref = selection.ref,
                    commit = commit,
                    resolvedAt = 1000L,
                ),
            )
        }

        override suspend fun checkLayout(input: ResolvedInput.Git): CompatibilityResult = compatibilityOverride

        override suspend fun updateAvailability(frozen: ResolvedInput.Git): UpdateAvailability =
            updateAvailabilityOverride
    }

    private class FakeInstallationPort(var activeId: String? = "install-initial") : ToolchainInstallationPort {
        override suspend fun state(): InstalledToolchain = InstalledToolchain(
            activeInstallId = activeId?.let { InstallId(it) },
            previousInstallId = null,
        )

        override suspend fun assembleCandidate(artifacts: Map<ToolGroupId, ArtifactId>): InstallId = InstallId("cand-1")

        override suspend fun activate(request: ActivationRequest): ActivationOutcome {
            activeId = request.targetInstallId.value
            return ActivationOutcome.Committed(request.targetInstallId)
        }

        override suspend fun activationOutcome(requestId: String): ActivationOutcome? = null
        override suspend fun cleanup(references: Set<InstallId>): CleanupReport = CleanupReport()
    }

    @Test
    fun `draft vs desired vs installed isolation with CAS and UI draft`() = runTest {
        val repo = FakeSelectionRepository()
        val resolver = FakeSourceResolver()
        val installPort = FakeInstallationPort(activeId = "install-1")
        val state = ToolVersionsState(
            scope = this,
            repository = repo,
            sourceResolver = resolver,
            installationPort = installPort,
            activeDistroProvider = { "Ubuntu" },
        )

        state.refresh("Ubuntu")
        advanceUntilIdle()

        val erofsGroup = ToolGroupId("erofs-utils")
        val initialRows = state.rows.value
        val initialErofsRow = initialRows.firstOrNull { it.groupId == erofsGroup.value }
        assertNotNull(initialErofsRow)
        assertEquals(ToolGroupStatus.UP_TO_DATE, initialErofsRow.status)

        state.startEdit(erofsGroup)
        advanceUntilIdle()

        assertNotNull(state.draft.value)
        assertEquals(erofsGroup, state.draft.value?.group)

        state.updateDraftRef(ToolRef.Tag("v2.0"))
        advanceUntilIdle()

        val rowsWhileDrafting = state.rows.value
        val erofsRowWhileDrafting = rowsWhileDrafting.first { it.groupId == erofsGroup.value }
        assertEquals(
            ToolGroupStatus.UP_TO_DATE,
            erofsRowWhileDrafting.status,
            "Draft edits must not mutate rows before save",
        )

        val saveResult = state.saveDraft("Ubuntu")
        assertTrue(saveResult.isSuccess, "Save must succeed with valid revision")
        advanceUntilIdle()

        val rowsAfterSave = state.rows.value
        val erofsRowAfterSave = rowsAfterSave.first { it.groupId == erofsGroup.value }
        assertEquals(ToolGroupStatus.CHANGE_PENDING, erofsRowAfterSave.status)
        assertEquals("v2.0", erofsRowAfterSave.selectedLabel)
        assertNull(state.selectedGroupId.value, "Edit panel must close after save")
        assertNull(state.draft.value, "Draft must clear after save")
    }

    @Test
    fun `resolve and check states - transitions from Resolving to Resolved with compatibility`() = runTest {
        val repo = FakeSelectionRepository()
        val resolver = FakeSourceResolver()
        val state = ToolVersionsState(
            scope = this,
            repository = repo,
            sourceResolver = resolver,
            installationPort = FakeInstallationPort(),
            activeDistroProvider = { "Ubuntu" },
        )

        val group = ToolGroupId("erofs-utils")
        state.startEdit(group)
        advanceUntilIdle()

        assertTrue(state.resolutionState.value is ResolutionState.Resolved)
        val resolved = state.resolutionState.value as ResolutionState.Resolved
        assertTrue(resolved.compatibility is CompatibilityResult.LayoutCompatible)
        assertEquals("1111111111111111111111111111111111111111", (resolved.input as ResolvedInput.Git).commit)
    }

    @Test
    fun `errors keep input - resolve failure retains user input in draft`() = runTest {
        val repo = FakeSelectionRepository()
        val resolver = FakeSourceResolver().apply {
            resolveOutcomeOverride = ResolveOutcome.NotFound
        }
        val state = ToolVersionsState(
            scope = this,
            repository = repo,
            sourceResolver = resolver,
            installationPort = FakeInstallationPort(),
            activeDistroProvider = { "Ubuntu" },
        )

        val group = ToolGroupId("erofs-utils")
        state.startEdit(group)
        state.updateDraftRepoUrl("https://github.com/my-fork/erofs-tools.git")
        state.updateDraftRef(ToolRef.Tag("v9.9.9"))
        advanceUntilIdle()

        val resState = state.resolutionState.value
        assertTrue(resState is ResolutionState.Error, "Resolution state must be Error when ref not found")
        val draft = state.draft.value
        assertNotNull(draft)
        assertEquals("https://github.com/my-fork/erofs-tools.git", draft.repoUrl)
        assertEquals(ToolRef.Tag("v9.9.9"), draft.ref)
    }

    @Test
    fun `update available - branch head moved updates status to UPDATE_AVAILABLE`() = runTest {
        val repo = FakeSelectionRepository()
        val resolver = FakeSourceResolver().apply {
            updateAvailabilityOverride = UpdateAvailability.UpdateAvailable("9999999999999999999999999999999999999999")
        }
        val state = ToolVersionsState(
            scope = this,
            repository = repo,
            sourceResolver = resolver,
            installationPort = FakeInstallationPort(),
            activeDistroProvider = { "Ubuntu" },
        )

        val group = ToolGroupId("android-tools")
        repo.saveSelections(
            "Ubuntu",
            0L,
            mapOf(group to ToolSelection(group.value, null, ToolRef.Branch("main"))),
        )

        state.refresh("Ubuntu")
        advanceUntilIdle()
        state.checkForUpdates("Ubuntu", group)
        advanceUntilIdle()

        val row = state.rows.value.first { it.groupId == group.value }
        assertEquals(ToolGroupStatus.UPDATE_AVAILABLE, row.status)
    }

    @Test
    fun `slow resolve or ref listing never blocks editing, cancel or escape`() = runTest {
        val repo = FakeSelectionRepository()
        val resolver = FakeSourceResolver().apply {
            refListingDelayMs = 10_000L
            resolveDelayMs = 10_000L
        }
        val state = ToolVersionsState(
            scope = this,
            repository = repo,
            sourceResolver = resolver,
            installationPort = FakeInstallationPort(),
            activeDistroProvider = { "Ubuntu" },
        )

        val group = ToolGroupId("erofs-utils")
        state.startEdit(group)
        advanceTimeBy(100L)

        assertTrue(state.isLoadingRefs.value)
        assertEquals(ResolutionState.Resolving, state.resolutionState.value)

        state.cancelEdit()
        advanceTimeBy(50L)

        assertNull(state.selectedGroupId.value, "Panel must close immediately on Cancel/Escape")
        assertNull(state.draft.value)
        assertFalse(state.isLoadingRefs.value)
        assertEquals(ResolutionState.Idle, state.resolutionState.value)
    }

    @Test
    fun `superseded resolve result is discarded`() = runTest {
        val repo = FakeSelectionRepository()
        val resolver = FakeSourceResolver()
        val state = ToolVersionsState(
            scope = this,
            repository = repo,
            sourceResolver = resolver,
            installationPort = FakeInstallationPort(),
            activeDistroProvider = { "Ubuntu" },
        )

        val group = ToolGroupId("erofs-utils")
        state.startEdit(group)
        advanceUntilIdle()

        resolver.resolveDelayMs = 1000L
        state.updateDraftRef(ToolRef.Tag("v1.0"))
        advanceTimeBy(200L)

        resolver.resolveDelayMs = 100L
        state.updateDraftRef(ToolRef.Tag("v2.0"))
        advanceTimeBy(150L)

        val resState = state.resolutionState.value
        assertTrue(resState is ResolutionState.Resolved)
        assertEquals(ToolRef.Tag("v2.0"), (resState.input as ResolvedInput.Git).ref)
    }
}
