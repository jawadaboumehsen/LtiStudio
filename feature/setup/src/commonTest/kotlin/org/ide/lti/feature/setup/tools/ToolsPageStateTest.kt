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

import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.GroupLinks
import org.ide.lti.core.domain.tools.GroupState
import org.ide.lti.core.domain.tools.InitInfo
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.InstalledVersion
import org.ide.lti.core.domain.tools.OperationKind
import org.ide.lti.core.domain.tools.OperationPhase
import org.ide.lti.core.domain.tools.PinnedTools
import org.ide.lti.core.domain.tools.RecipeSummary
import org.ide.lti.core.domain.tools.RefListing
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ResolvedSourceCheck
import org.ide.lti.core.domain.tools.Selection
import org.ide.lti.core.domain.tools.TagRefInfo
import org.ide.lti.core.domain.tools.ToolInventory
import org.ide.lti.core.domain.tools.ToolManager
import org.ide.lti.core.domain.tools.ToolOperation
import org.ide.lti.core.domain.tools.ToolStorePort
import org.ide.lti.core.domain.tools.ToolUsageCoordinator
import org.ide.lti.core.model.setup.ToolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolsPageStateTest {
    private class FakePort : ToolStorePort {
        var inventory = ToolInventory(emptyMap())
        var resolvedCommit = "2" * 40

        override suspend fun init(): Result<InitInfo> = Result.success(InitInfo())

        override suspend fun status(catalog: ToolGroupCatalog): Result<ToolInventory> = Result.success(inventory)

        override suspend fun refs(group: ToolGroupId, repoUrl: String): Result<RefListing> =
            Result.success(RefListing(listOf(TagRefInfo("v1.8.2", "2" * 40)), emptyList()))

        override suspend fun resolve(group: ToolGroupId, repoUrl: String, ref: ToolRef): Result<ResolvedSourceCheck> =
            Result.success(ResolvedSourceCheck(commit = resolvedCommit))

        override fun install(request: InstallRequest): Flow<InstallEvent> = emptyFlow()

        override suspend fun use(group: ToolGroupId, versionId: String, choice: ChoiceUpdate): Result<GroupLinks> =
            Result.success(GroupLinks(versionId, null))

        override suspend fun remove(group: ToolGroupId, versionId: String): Result<Unit> = Result.success(Unit)

        override suspend fun paths(catalog: ToolGroupCatalog): Result<PinnedTools> = Result.success(
            PinnedTools(emptyMap(), emptyMap()),
        )
    }

    @Test
    fun `primary button reflects situation - Use for installed, Repair for damaged, Install for new`() = runTest {
        val port = FakePort()
        val vCurrent =
            InstalledVersion(
                group = ToolGroupId("erofs-utils"),
                id = "v-installed",
                label = "v1.8.1",
                source = ResolvedSource.Git("https://example.com", ToolRef.Commit("1" * 40), "1" * 40),
                recipe = RecipeSummary("cmake", 1),
                outputs = emptyList(),
                builtAt = "2026-10-01",
                intact = true,
            )
        port.inventory =
            ToolInventory(
                mapOf(
                    ToolGroupId("erofs-utils") to
                        GroupState(
                            group = ToolGroupId("erofs-utils"),
                            current = vCurrent,
                            installed = listOf(vCurrent),
                        ),
                ),
            )

        val manager = ToolManager(port)
        manager.refresh()

        val state = ToolsPageState(manager = manager, scope = backgroundScope)
        advanceUntilIdle()

        // Select installed version -> button is disabled or "Use"
        state.selectGroup(ToolGroupId("erofs-utils"))
        state.selectInstalledVersion("v-installed")
        advanceUntilIdle()
        assertEquals(PrimaryButtonAction.Use, state.primaryButtonAction.value)

        // Mark damaged
        val vDamaged = vCurrent.copy(intact = false)
        port.inventory =
            ToolInventory(
                mapOf(
                    ToolGroupId("erofs-utils") to
                        GroupState(
                            group = ToolGroupId("erofs-utils"),
                            current = vDamaged,
                            installed = listOf(vDamaged),
                        ),
                ),
            )
        state.refresh()
        advanceUntilIdle()
        assertEquals(PrimaryButtonAction.Repair, state.primaryButtonAction.value)
    }

    @Test
    fun `remove is disabled for active and selected version`() = runTest {
        val port = FakePort()
        val v1 =
            InstalledVersion(
                group = ToolGroupId("erofs-utils"),
                id = "v1",
                label = "v1.0",
                source = ResolvedSource.Git("https://example.com", ToolRef.Commit("1" * 40), "1" * 40),
                recipe = RecipeSummary("cmake", 1),
                outputs = emptyList(),
                builtAt = "2026-10-01",
                intact = true,
            )
        val v2 = v1.copy(id = "v2", label = "v2.0")
        val v3 = v1.copy(id = "v3", label = "v3.0")

        port.inventory =
            ToolInventory(
                mapOf(
                    ToolGroupId("erofs-utils") to
                        GroupState(
                            group = ToolGroupId("erofs-utils"),
                            current = v2,
                            selection = Selection("v1", v1.source),
                            installed = listOf(v1, v2, v3),
                        ),
                ),
            )
        val manager = ToolManager(port)
        manager.refresh()

        val state = ToolsPageState(manager = manager, scope = backgroundScope)
        advanceUntilIdle()
        state.selectGroup(ToolGroupId("erofs-utils"))
        advanceUntilIdle()

        assertFalse(state.canRemoveVersion("v2"), "Current version cannot be removed")
        assertEquals("Version is currently in use", state.removeDisabledReason("v2"))

        assertFalse(state.canRemoveVersion("v1"), "Selected version cannot be removed")
        assertEquals("Version is selected as your pick", state.removeDisabledReason("v1"))

        assertTrue(state.canRemoveVersion("v3"), "Unreferenced version v3 can be removed")
    }

    @Test
    fun `use refreshes the SDK manager inventory after the switch`() = runTest {
        val group = ToolGroupId("erofs-utils")
        val v1 = installedVersion(group, "v1", "v1.0")
        val v2 = installedVersion(group, "v2", "v2.0")
        val port =
            FakePort().apply {
                inventory = ToolInventory(mapOf(group to GroupState(group, v2, null, listOf(v1, v2))))
            }
        val manager = ToolManager(port)
        manager.refresh()
        val state = ToolsPageState(manager, backgroundScope)
        runCurrent()

        state.selectGroup(group)
        runCurrent()

        port.inventory = ToolInventory(mapOf(group to GroupState(group, v1, null, listOf(v1, v2))))
        state.use("v1")
        runCurrent()

        assertEquals("v1", state.selectedGroupState.value?.current?.id)
    }

    @Test
    fun `rows come from ToolGroupCatalog and version names are readable ids`() = runTest {
        val port = FakePort()
        val vErofs = installedVersion(ToolGroupId("erofs-utils"), "v1.8.10-251217", "v1.8.10")
        val vAndroid = installedVersion(ToolGroupId("android-tools"), "sixteen-f9bef94", "sixteen")
        val vGh = installedVersion(ToolGroupId("gh"), "2.97.0", "v2.97.0")

        port.inventory =
            ToolInventory(
                mapOf(
                    ToolGroupId("erofs-utils") to
                        GroupState(
                            group = ToolGroupId("erofs-utils"),
                            current = vErofs,
                            installed = listOf(vErofs),
                        ),
                    ToolGroupId("android-tools") to
                        GroupState(
                            group = ToolGroupId("android-tools"),
                            current = vAndroid,
                            selection = Selection("sixteen-f9bef94", vAndroid.source),
                            installed = listOf(vAndroid),
                        ),
                    ToolGroupId("gh") to
                        GroupState(
                            group = ToolGroupId("gh"),
                            current = vGh,
                            installed = listOf(vGh),
                        ),
                ),
            )

        val manager = ToolManager(port)
        manager.refresh()

        val state = ToolsPageState(manager, backgroundScope)
        advanceUntilIdle()

        val rows = state.rows.value
        assertEquals(ToolGroupCatalog.allGroups.size, rows.size)
        assertEquals(
            ToolGroupCatalog.allGroups.map { it.id.value },
            rows.map { it.groupId },
        )

        // Version names are readable IDs
        val erofsRow = rows.first { it.groupId == "erofs-utils" }
        assertEquals("v1.8.10-251217", erofsRow.inUseLabel)

        // Group with user pick shows (yours)
        val androidRow = rows.first { it.groupId == "android-tools" }
        assertEquals("sixteen-f9bef94 (yours)", androidRow.inUseLabel)

        val ghRow = rows.first { it.groupId == "gh" }
        assertEquals("2.97.0", ghRow.inUseLabel)

        // Uninstalled group shows em-dash
        val uninstalledRow = rows.first { it.groupId == "apktool" }
        assertEquals("—", uninstalledRow.inUseLabel)
    }

    @Test
    fun `primary action transitions - Use when installed and intact, Install and use otherwise, Repair when damaged`() =
        runTest {
            val port = FakePort()
            val group = ToolGroupId("erofs-utils")
            val v1 = installedVersion(group, "v1.8.9", "v1.8.9")
            val v2 = installedVersion(group, "v1.8.10-251217", "v1.8.10")
            val vDamaged = installedVersion(group, "v1.8.8-damaged", "v1.8.8").copy(intact = false)

            port.inventory =
                ToolInventory(
                    mapOf(
                        group to
                            GroupState(
                                group = group,
                                current = v1,
                                installed = listOf(v1, v2, vDamaged),
                            ),
                    ),
                )

            val manager = ToolManager(port)
            manager.refresh()
            val state = ToolsPageState(manager, backgroundScope)
            advanceUntilIdle()

            state.selectGroup(group)
            advanceUntilIdle()

            // 1. Target version is installed and intact -> Use
            state.selectTargetVersion("v1.8.10-251217")
            advanceUntilIdle()
            assertEquals(PrimaryButtonAction.Use, state.primaryButtonAction.value)

            // 2. Target version is new / not installed -> Install & use
            state.selectTargetVersion("v1.8.11-new")
            advanceUntilIdle()
            assertEquals(PrimaryButtonAction.InstallAndUse, state.primaryButtonAction.value)

            // 3. Target version is installed but damaged -> Install & use
            state.selectTargetVersion("v1.8.8-damaged")
            advanceUntilIdle()
            assertEquals(PrimaryButtonAction.InstallAndUse, state.primaryButtonAction.value)

            // 4. Current version needs repair -> Repair
            val damagedCurrent = v1.copy(intact = false)
            port.inventory =
                ToolInventory(
                    mapOf(
                        group to
                            GroupState(
                                group = group,
                                current = damagedCurrent,
                                installed = listOf(damagedCurrent, v2),
                            ),
                    ),
                )
            state.refresh()
            advanceUntilIdle()
            // Still looking at another version (the damaged v1.8.8): that one is installed, this is not repaired.
            assertEquals(PrimaryButtonAction.InstallAndUse, state.primaryButtonAction.value)

            // Choosing the damaged version in use itself: repair it.
            state.selectTargetVersion("v1.8.9")
            advanceUntilIdle()
            assertEquals(PrimaryButtonAction.Repair, state.primaryButtonAction.value)
        }

    @Test
    fun `remove is disabled for version in use and while ROM job runs with reason text`() = runTest {
        val port = FakePort()
        val coordinator = ToolUsageCoordinator()
        val group = ToolGroupId("erofs-utils")
        val vPick = installedVersion(group, "v1.8.8", "v1.8.8")
        val vCurrent = installedVersion(group, "v1.8.9", "v1.8.9")
        val vOther = installedVersion(group, "v1.8.10", "v1.8.10")

        port.inventory =
            ToolInventory(
                mapOf(
                    group to
                        GroupState(
                            group = group,
                            current = vCurrent,
                            selection = Selection("v1.8.8", vPick.source),
                            installed = listOf(vPick, vCurrent, vOther),
                        ),
                ),
            )

        val manager = ToolManager(port = port, usageCoordinator = coordinator)
        manager.refresh()
        val state = ToolsPageState(manager, backgroundScope)
        advanceUntilIdle()

        state.selectGroup(group)
        advanceUntilIdle()

        // Version in use cannot be removed
        assertFalse(state.canRemoveVersion("v1.8.9"))
        assertEquals("Version is currently in use", state.removeDisabledReason("v1.8.9"))

        // Picked version cannot be removed
        assertFalse(state.canRemoveVersion("v1.8.8"))
        assertEquals("Version is selected as your pick", state.removeDisabledReason("v1.8.8"))

        // Unused version can be removed normally
        assertTrue(state.canRemoveVersion("v1.8.10"))
        assertNull(state.removeDisabledReason("v1.8.10"))

        // While a ROM job runs: removal and repair are disabled ("A ROM job is running"); use stays enabled (US5)
        coordinator.admitJob("job-running-1") {}
        assertFalse(state.canRemoveVersion("v1.8.10"))
        assertEquals("A ROM job is running", state.removeDisabledReason("v1.8.10"))
        assertFalse(state.canRepair())
        assertEquals("A ROM job is running", state.repairDisabledReason())
        assertTrue(state.canUseVersion("v1.8.10"))
        assertNull(state.useDisabledReason("v1.8.10"))

        // Finish ROM job -> removal allowed again
        coordinator.finishJob("job-running-1")
        assertTrue(state.canRemoveVersion("v1.8.10"))
        assertNull(state.removeDisabledReason("v1.8.10"))
    }

    @Test
    fun `use Studio version offered only when group has a pick and disabled when installing`() = runTest {
        val port = FakePort()
        val coordinator = ToolUsageCoordinator()
        val group = ToolGroupId("erofs-utils")
        val vCurrent = installedVersion(group, "v1.8.9", "v1.8.9")

        // 1. Group without pick -> not offered
        port.inventory =
            ToolInventory(
                mapOf(
                    group to
                        GroupState(
                            group = group,
                            current = vCurrent,
                            selection = null,
                            installed = listOf(vCurrent),
                        ),
                ),
            )

        val manager = ToolManager(port = port, usageCoordinator = coordinator)
        manager.refresh()
        val state = ToolsPageState(manager, backgroundScope)
        advanceUntilIdle()

        state.selectGroup(group)
        advanceUntilIdle()
        assertFalse(state.canUseStudioVersion)
        assertNull(state.useStudioVersionDisabledReason())

        // 2. Group with pick -> offered and enabled
        port.inventory =
            ToolInventory(
                mapOf(
                    group to
                        GroupState(
                            group = group,
                            current = vCurrent,
                            selection = Selection("v1.8.9", vCurrent.source),
                            installed = listOf(vCurrent),
                        ),
                ),
            )
        state.refresh()
        advanceUntilIdle()
        assertTrue(state.canUseStudioVersion)
        assertNull(state.useStudioVersionDisabledReason())

        // 3. While ROM job runs -> use Studio version remains enabled (US5)
        coordinator.admitJob("job-rom-2") {}
        assertTrue(state.canUseStudioVersion)
        assertNull(state.useStudioVersionDisabledReason())
        coordinator.finishJob("job-rom-2")
        assertTrue(state.canUseStudioVersion)

        // 4. While group is installing -> disabled with reason
        port.inventory =
            ToolInventory(
                mapOf(
                    group to
                        GroupState(
                            group = group,
                            current = vCurrent,
                            selection = Selection("v1.8.9", vCurrent.source),
                            installed = listOf(vCurrent),
                            operation =
                            ToolOperation(
                                opId = "op-install",
                                group = group,
                                kind = OperationKind.Install,
                                phase = OperationPhase.Building,
                            ),
                        ),
                ),
            )
        state.refresh()
        advanceUntilIdle()
        assertFalse(state.canUseStudioVersion)
        assertEquals("Another change is running for this group", state.useStudioVersionDisabledReason())
    }

    @Test
    fun `while group is installing - Use, Install and use, Remove and Repair are disabled with reason`() = runTest {
        val port = FakePort()
        val group = ToolGroupId("erofs-utils")
        val vCurrent = installedVersion(group, "v1.8.9", "v1.8.9")
        val vOther = installedVersion(group, "v1.8.10", "v1.8.10")

        port.inventory =
            ToolInventory(
                mapOf(
                    group to
                        GroupState(
                            group = group,
                            current = vCurrent,
                            installed = listOf(vCurrent, vOther),
                            operation =
                            ToolOperation(
                                opId = "op-build",
                                group = group,
                                kind = OperationKind.Install,
                                phase = OperationPhase.Building,
                            ),
                        ),
                ),
            )

        val manager = ToolManager(port)
        manager.refresh()
        val state = ToolsPageState(manager, backgroundScope)
        advanceUntilIdle()

        state.selectGroup(group)
        state.selectTargetVersion("v1.8.10")
        advanceUntilIdle()

        assertTrue(state.isGroupInstalling(group))

        // Primary button disabled
        assertEquals(PrimaryButtonAction.Disabled, state.primaryButtonAction.value)
        assertEquals("Another change is running for this group", state.primaryButtonDisabledReason.value)

        // Use disabled
        assertFalse(state.canUseVersion("v1.8.10"))
        assertEquals("Another change is running for this group", state.useDisabledReason("v1.8.10"))

        // Remove disabled
        assertFalse(state.canRemoveVersion("v1.8.10"))
        assertEquals("Another change is running for this group", state.removeDisabledReason("v1.8.10"))

        // Repair disabled
        assertFalse(state.canRepair())
        assertEquals("Another change is running for this group", state.repairDisabledReason())
    }

    @Test
    fun `while a change runs the version and source choices are frozen until it ends`() = runTest {
        val port = FakePort()
        val group = ToolGroupId("erofs-utils")
        val vCurrent = installedVersion(group, "v1.8.9", "v1.8.9")
        val vOther = installedVersion(group, "v1.8.10", "v1.8.10")
        val running =
            ToolOperation(
                opId = "op-build",
                group = group,
                kind = OperationKind.Install,
                phase = OperationPhase.Building,
                percent = 40,
            )
        port.inventory =
            ToolInventory(
                mapOf(group to GroupState(group, vCurrent, null, listOf(vCurrent, vOther), operation = running)),
            )
        val manager = ToolManager(port)
        manager.refresh()
        val state = ToolsPageState(manager, backgroundScope)
        advanceUntilIdle()
        state.selectGroup(group)

        state.selectTargetVersion("v1.8.10")
        state.selectInstalledVersion("v1.8.10")
        state.selectRefType(RefType.Branch)
        state.setCustomRepoUrl("https://fork.example/erofs-utils.git")
        state.resolveRef(ToolRef.Tag("v1.8.10"))
        advanceUntilIdle()

        assertEquals("v1.8.9", state.selectedVersionId.value, "the target does not move")
        assertEquals(RefType.Tag, state.selectedRefType.value)
        assertNull(state.customRepoUrl.value)
        assertFalse(state.isResolving.value)
        assertNull(state.resolvedSelection.value)

        port.inventory = ToolInventory(mapOf(group to GroupState(group, vCurrent, null, listOf(vCurrent, vOther))))
        state.refresh()
        advanceUntilIdle()
        state.selectTargetVersion("v1.8.10")
        assertEquals("v1.8.10", state.selectedVersionId.value, "free again once it has ended")
    }

    @Test
    fun `the running change is shown with its step, percentage and latest line`() {
        val install =
            ToolOperation(
                opId = "o",
                group = erofs,
                kind = OperationKind.Install,
                phase = OperationPhase.Building,
                outputTail = listOf("first", "[ 40%] Building CXX object a.o", ""),
                percent = 46,
            )

        val shown = ToolsPresentationMapper.mapProgress(install)

        assertEquals("Installing", shown.title)
        assertEquals("Step 2 of 5 · Building", shown.stepLabel)
        assertEquals(46, shown.percent)
        assertEquals("46%", shown.percentLabel)
        assertEquals("[ 40%] Building CXX object a.o", shown.latestLine)
        assertTrue(shown.canCancel)

        val switching = ToolsPresentationMapper.mapProgress(
            ToolOperation("u", erofs, OperationKind.Use, null, OperationPhase.Switching),
        )
        assertEquals("Switching version", switching.title)
        assertNull(switching.percent, "a switch has no percentage")
        assertFalse(switching.canCancel, "a switch cannot be stopped half way")
    }

    private val erofs = ToolGroupId("erofs-utils")

    private suspend fun kotlinx.coroutines.test.TestScope.pageWithInstalledV1(port: FakePort): ToolsPageState {
        val v1 = installedVersion(erofs, "v1", "v1")
        port.inventory = ToolInventory(mapOf(erofs to GroupState(group = erofs, current = v1, installed = listOf(v1))))
        val manager = ToolManager(port)
        manager.refresh()
        // A child of the test scheduler that the test waits for (backgroundScope tasks never count as pending).
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        backgroundScope.coroutineContext[kotlinx.coroutines.Job]!!.invokeOnCompletion { scope.cancel() }
        val state = ToolsPageState(manager = manager, scope = scope)
        advanceUntilIdle()
        state.selectGroup(erofs)
        advanceUntilIdle()
        return state
    }

    @Test
    fun `a fork's tag with the name of an installed one is installed, not switched to`() = runTest {
        val port = FakePort()
        val state = pageWithInstalledV1(port)
        assertEquals(PrimaryButtonAction.Use, state.primaryButtonAction.value)

        // The fork's v1 is another commit in another repository.
        state.resolveRef(ToolRef.Tag("v1"), "https://fork.example/erofs-utils.git")
        advanceUntilIdle()

        assertEquals("v1", state.selectedVersionId.value)
        assertEquals(PrimaryButtonAction.InstallAndUse, state.primaryButtonAction.value)
    }

    @Test
    fun `the same tag of the same repository and commit is only switched to`() = runTest {
        val port = FakePort().apply { resolvedCommit = "1" * 40 }
        val state = pageWithInstalledV1(port)

        state.resolveRef(ToolRef.Tag("v1"), "https://example.com")
        advanceUntilIdle()

        assertEquals(PrimaryButtonAction.Use, state.primaryButtonAction.value)
    }

    @Test
    fun `typing another branch or commit, or changing the ref type, drops the resolved commit`() = runTest {
        val port = FakePort()
        val state = pageWithInstalledV1(port)

        state.selectRefType(RefType.Commit)
        state.resolveRef(ToolRef.Commit("2" * 40), "https://example.com")
        advanceUntilIdle()
        assertTrue(state.canInstallSelected())

        state.refInputChanged() // the commit text was edited
        assertNull(state.resolvedSelection.value)
        assertFalse(state.canInstallSelected())

        state.resolveRef(ToolRef.Commit("2" * 40), "https://example.com")
        advanceUntilIdle()
        assertTrue(state.canInstallSelected())
        state.selectRefType(RefType.Branch) // another kind of ref
        assertNull(state.resolvedSelection.value)
        assertFalse(state.canInstallSelected())
    }

    @Test
    fun `a resolution still in flight when the input changes is discarded`() = runTest {
        val port = FakePort()
        val state = pageWithInstalledV1(port)

        state.resolveRef(ToolRef.Commit("2" * 40), "https://example.com")
        state.refInputChanged() // before the answer arrives
        advanceUntilIdle()

        assertNull(state.resolvedSelection.value)
    }

    private fun installedVersion(group: ToolGroupId, id: String, label: String): InstalledVersion = InstalledVersion(
        group = group,
        id = id,
        label = label,
        source = ResolvedSource.Git("https://example.com", ToolRef.Commit("1" * 40), "1" * 40),
        recipe = RecipeSummary("cmake", 1),
        outputs = emptyList(),
        builtAt = "2026-10-01",
        intact = true,
    )
}

private operator fun String.times(n: Int): String = repeat(n)
