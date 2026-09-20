/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.ide.lti.core.domain.ports.InMemoryStudioPresentationStore
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.studio.ScrollAnchor
import org.ide.lti.core.model.studio.StudioPresentation
import org.ide.lti.feature.rom.studio.api.CanonicalStageDescriptors
import org.ide.lti.feature.rom.studio.api.StudioSubobjectId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StudioNavigationTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun catalogHasExactlyEightStagesInContractOrderWithExactSubobjectCounts() {
        val stages = CanonicalStageDescriptors.ALL
        assertEquals(8, stages.size)

        val expectedStages = listOf(
            StageId.FIRMWARE_ACQUISITION to 5,
            StageId.FIRMWARE_EXTRACTION to 5,
            StageId.WORK_TREE_ASSEMBLY to 5,
            StageId.DEBLOAT to 6,
            StageId.MODULE_APPLICATION to 6,
            StageId.BUILD_FLASHABLE_ZIP to 7,
            StageId.GENERATE_OTA_MANIFEST to 5,
            StageId.PUBLISH_RELEASE to 8,
        )

        expectedStages.forEachIndexed { index, (expectedStageId, expectedCount) ->
            val stage = stages[index]
            assertEquals(expectedStageId, stage.stageId)
            assertEquals(
                expectedCount,
                stage.subobjects.size,
                "Stage $expectedStageId expected $expectedCount subobjects but got ${stage.subobjects.size}",
            )
        }
    }

    @Test
    fun catalogHasUniqueObjectIdsAcrossAllStages() {
        val allSubobjects = CanonicalStageDescriptors.ALL.flatMap { it.subobjects }
        val allIds = allSubobjects.map { it.id.value }
        val uniqueIds = allIds.toSet()

        assertEquals(allIds.size, uniqueIds.size, "Duplicate subobject IDs found: ${allIds - uniqueIds}")
        assertEquals(47, allSubobjects.size)
    }

    @Test
    fun overviewIsNotInStages() {
        val stageTitles = CanonicalStageDescriptors.ALL.map { it.title.lowercase() }
        assertTrue(stageTitles.none { it.contains("overview") })

        val subobjectIds = CanonicalStageDescriptors.ALL.flatMap { it.subobjects }.map { it.id.value.lowercase() }
        assertTrue(subobjectIds.none { it.contains("overview") })
    }

    @Test
    fun catalogLookupsReturnExpectedDestinationsAndSubobjects() {
        val firmwareAcquisition = CanonicalStageDescriptors.descriptorFor(StageId.FIRMWARE_ACQUISITION)
        assertNotNull(firmwareAcquisition)
        assertEquals("Acquire", firmwareAcquisition.title)

        val owningStage = CanonicalStageDescriptors.owningStage(StudioSubobjectId("firmware-baseline"))
        assertNotNull(owningStage)
        assertEquals(StageId.FIRMWARE_ACQUISITION, owningStage.stageId)

        val subobject = CanonicalStageDescriptors.subobject(StudioSubobjectId("system-ext-handling"))
        assertNotNull(subobject)
        assertEquals("system_ext handling", subobject.title)

        assertNull(CanonicalStageDescriptors.owningStage(StudioSubobjectId("non-existent-subobject")))
        assertNull(CanonicalStageDescriptors.subobject(StudioSubobjectId("non-existent-subobject")))
    }

    @Test
    fun deepLinkBeatsStoredRestoration() {
        val storedPresentation = StudioPresentation(
            workspaceId = "ws-1",
            hasOpened = true,
            editorStageId = StageId.DEBLOAT,
            editorObjectId = "inventory",
        )

        val resolved = resolveStudioEntry(
            presentation = storedPresentation,
            requestedStageId = StageId.FIRMWARE_ACQUISITION,
            requestedObjectId = "firmware-baseline",
        )

        val expected = StudioEntry.Editor(
            stageId = StageId.FIRMWARE_ACQUISITION,
            objectId = "firmware-baseline",
            reason = StudioEntryReason.DEEP_LINK,
        )
        assertEquals(expected, resolved)
    }

    @Test
    fun storedObjectThatNoLongerExistsFallsBackToOwningStageWithObjectNotFound() {
        val storedPresentation = StudioPresentation(
            workspaceId = "ws-1",
            hasOpened = true,
            editorStageId = StageId.DEBLOAT,
            editorObjectId = "deleted-obsolete-subobject",
        )

        val resolved = resolveStudioEntry(
            presentation = storedPresentation,
            requestedStageId = null,
            requestedObjectId = null,
        )

        val expected = StudioEntry.Editor(
            stageId = StageId.DEBLOAT,
            objectId = "inventory",
            reason = StudioEntryReason.OBJECT_NOT_FOUND,
        )
        assertEquals(expected, resolved)
    }

    @Test
    fun openedWorkspaceWithNoStoredEditorRestoresToOverview() {
        val requestedPresentation = StudioPresentation(
            workspaceId = "ws-1",
            hasOpened = true,
            editorStageId = null,
            editorObjectId = null,
        )

        val resolvedFromEmptyStoredStage = resolveStudioEntry(
            presentation = requestedPresentation,
            requestedStageId = null,
            requestedObjectId = null,
        )
        assertEquals(StudioEntry.Overview(StudioEntryReason.RESTORED), resolvedFromEmptyStoredStage)

        // A requested object that belongs to no stage is an OBJECT miss, not a stage miss.
        assertEquals(
            StudioEntry.Overview(StudioEntryReason.OBJECT_NOT_FOUND),
            resolveStudioEntry(presentation = null, requestedStageId = null, requestedObjectId = "no-such-object"),
        )
    }

    @Test
    fun freshWorkspaceGivesOverviewNewWorkspace() {
        val nullPresentationResolved = resolveStudioEntry(
            presentation = null,
            requestedStageId = null,
            requestedObjectId = null,
        )
        assertEquals(StudioEntry.Overview(StudioEntryReason.NEW_WORKSPACE), nullPresentationResolved)

        val unopenedPresentation = StudioPresentation(
            workspaceId = "ws-1",
            hasOpened = false,
            editorStageId = StageId.FIRMWARE_ACQUISITION,
            editorObjectId = "source",
        )
        val unopenedResolved = resolveStudioEntry(
            presentation = unopenedPresentation,
            requestedStageId = null,
            requestedObjectId = null,
        )
        assertEquals(StudioEntry.Overview(StudioEntryReason.NEW_WORKSPACE), unopenedResolved)
    }

    @Test
    fun resolvingReturnsValueWithoutMutatingInputPresentation() {
        val original = StudioPresentation(
            workspaceId = "ws-1",
            hasOpened = true,
            editorStageId = StageId.WORK_TREE_ASSEMBLY,
            editorObjectId = "work-trees",
            scrollAnchors = mapOf("WORK_TREE_ASSEMBLY/work-trees" to ScrollAnchor(anchorId = "sec-1", offset = 120)),
            expandedSections = mapOf("work-trees" to setOf("sec-1", "sec-2")),
            navigatorWidth = 260,
            lastFocusId = "input-1",
        )

        val clonedOriginal = original.copy()

        val resolved = resolveStudioEntry(
            presentation = original,
            requestedStageId = StageId.DEBLOAT,
            requestedObjectId = "inventory",
        )

        assertEquals(
            StudioEntry.Editor(StageId.DEBLOAT, "inventory", StudioEntryReason.DEEP_LINK),
            resolved,
        )
        assertEquals(clonedOriginal, original, "Input presentation must not be mutated during resolution")
    }

    @Test
    fun pureHelpersWithEditorAndWithNavigatorWidth() {
        val initial = StudioPresentation(workspaceId = "ws-1", navigatorWidth = 240)
        val withEditor = initial.withEditor(StageId.PUBLISH_RELEASE, "provider")
        assertEquals(StageId.PUBLISH_RELEASE, withEditor.editorStageId)
        assertEquals("provider", withEditor.editorObjectId)
        assertTrue(withEditor.hasOpened, "Selecting an editor must mark the workspace opened, or restoration is lost")
        assertEquals(initial.workspaceId, withEditor.workspaceId)
        assertNotEquals(initial, withEditor)

        val clampedLow = initial.withNavigatorWidth(150)
        assertEquals(StudioPresentation.MIN_NAVIGATOR_WIDTH, clampedLow.navigatorWidth)

        val clampedHigh = initial.withNavigatorWidth(500)
        assertEquals(StudioPresentation.MAX_NAVIGATOR_WIDTH, clampedHigh.navigatorWidth)

        val inRange = initial.withNavigatorWidth(300)
        assertEquals(300, inRange.navigatorWidth)
    }

    @Test
    fun viewModelInitialEntryResolutionMatchesContract() = runTest(testDispatcher) {
        val store = InMemoryStudioPresentationStore()
        val viewModel = StudioViewModel(
            studioPresentationStore = store,
            initialWorkspaceId = "ws-new",
        )
        advanceUntilIdle()

        // Unopened workspace defaults to Overview with NEW_WORKSPACE
        val initialState = viewModel.uiState.value
        assertEquals("ws-new", initialState.workspaceId)
        assertEquals(StudioEntry.Overview(StudioEntryReason.NEW_WORKSPACE), initialState.entry)

        // Deep-link into specific stage and object
        viewModel.onEntry("ws-new", StageId.DEBLOAT, "inventory")
        advanceUntilIdle()

        val deepLinkState = viewModel.uiState.value
        assertEquals(
            StudioEntry.Editor(StageId.DEBLOAT, "inventory", StudioEntryReason.DEEP_LINK),
            deepLinkState.entry,
        )

        // Pre-stored presentation restores previous editor
        store.put(
            StudioPresentation(
                workspaceId = "ws-stored",
                hasOpened = true,
                editorStageId = StageId.MODULE_APPLICATION,
                editorObjectId = "enabled-ordered-modules",
            ),
        )
        viewModel.onEntry("ws-stored", null, null)
        advanceUntilIdle()

        val storedState = viewModel.uiState.value
        assertEquals("ws-stored", storedState.workspaceId)
        assertEquals(
            StudioEntry.Editor(StageId.MODULE_APPLICATION, "enabled-ordered-modules", StudioEntryReason.RESTORED),
            storedState.entry,
        )
    }

    @Test
    fun viewModelSelectionAndNavigationStateTransitions() = runTest(testDispatcher) {
        val store = InMemoryStudioPresentationStore()
        val viewModel = StudioViewModel(
            studioPresentationStore = store,
            initialWorkspaceId = "ws-1",
        )
        advanceUntilIdle()

        // Select stage BUILD_FLASHABLE_ZIP
        viewModel.selectStage(StageId.BUILD_FLASHABLE_ZIP)
        advanceUntilIdle()

        val stageState = viewModel.uiState.value
        assertTrue(stageState.entry is StudioEntry.Editor)
        val stageEditor = stageState.entry as StudioEntry.Editor
        assertEquals(StageId.BUILD_FLASHABLE_ZIP, stageEditor.stageId)
        assertEquals("images", stageEditor.objectId, "selectStage falls back to the stage's first subobject")

        // Rail items reflect selection
        val selectedRail = stageState.railItems.firstOrNull { it.stage.stageId == StageId.BUILD_FLASHABLE_ZIP }
        assertNotNull(selectedRail)
        assertTrue(selectedRail.isSelected)
        val unselectedRail = stageState.railItems.firstOrNull { it.stage.stageId == StageId.FIRMWARE_ACQUISITION }
        assertNotNull(unselectedRail)
        assertFalse(unselectedRail.isSelected)

        // Select specific object within stage
        viewModel.selectObject(StageId.BUILD_FLASHABLE_ZIP, "avb-keys")
        advanceUntilIdle()

        val objectState = viewModel.uiState.value
        val objectEditor = objectState.entry as StudioEntry.Editor
        assertEquals(StageId.BUILD_FLASHABLE_ZIP, objectEditor.stageId)
        assertEquals("avb-keys", objectEditor.objectId)

        // An object id the catalog does not recognize for this stage is a no-op, not silently accepted.
        viewModel.selectObject(StageId.BUILD_FLASHABLE_ZIP, "no-such-object")
        advanceUntilIdle()
        assertEquals("avb-keys", (viewModel.uiState.value.entry as StudioEntry.Editor).objectId)

        // Select overview mode
        viewModel.openOverview()
        advanceUntilIdle()

        val overviewState = viewModel.uiState.value
        assertTrue(overviewState.entry is StudioEntry.Overview)

        // Restore editor returns to last selected editor context
        viewModel.returnToEditor()
        advanceUntilIdle()

        val restoredState = viewModel.uiState.value
        assertTrue(restoredState.entry is StudioEntry.Editor)
        val restoredEditor = restoredState.entry as StudioEntry.Editor
        assertEquals(StageId.BUILD_FLASHABLE_ZIP, restoredEditor.stageId)
        assertEquals("avb-keys", restoredEditor.objectId)
    }

    @Test
    fun viewModelDockAndDrawerAndSearchTransitions() = runTest(testDispatcher) {
        val store = InMemoryStudioPresentationStore()
        val viewModel = StudioViewModel(
            studioPresentationStore = store,
            initialWorkspaceId = "ws-1",
        )
        advanceUntilIdle()

        // Dock tabs
        viewModel.setActiveDockTab(StudioDockTab.ACTIVITY)
        assertEquals(StudioDockTab.ACTIVITY, viewModel.uiState.value.activeDockTab)
        viewModel.setActiveDockTab(StudioDockTab.ARTIFACTS)
        assertEquals(StudioDockTab.ARTIFACTS, viewModel.uiState.value.activeDockTab)
        viewModel.setActiveDockTab(StudioDockTab.PROBLEMS)
        assertEquals(StudioDockTab.PROBLEMS, viewModel.uiState.value.activeDockTab)

        // Dock expansion
        val initialExpanded = viewModel.uiState.value.isDockExpanded
        viewModel.toggleDock()
        assertEquals(!initialExpanded, viewModel.uiState.value.isDockExpanded)
        viewModel.toggleDock()
        assertEquals(initialExpanded, viewModel.uiState.value.isDockExpanded)

        // Navigator drawer
        val initialDrawer = viewModel.uiState.value.isNavigatorDrawerOpen
        viewModel.toggleNavigatorDrawer()
        assertEquals(!initialDrawer, viewModel.uiState.value.isNavigatorDrawerOpen)
        viewModel.closeNavigatorDrawer()
        assertFalse(viewModel.uiState.value.isNavigatorDrawerOpen)

        // Search query
        viewModel.setSearchQuery("payload")
        assertEquals("payload", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun viewModelSavingPresentationPersistsWidthStageAndObjectWithoutMutatingConfiguration() = runTest(testDispatcher) {
        val store = InMemoryStudioPresentationStore()
        val viewModel = StudioViewModel(
            studioPresentationStore = store,
            initialWorkspaceId = "ws-persist",
        )
        advanceUntilIdle()

        // Resize navigator to 320
        viewModel.setNavigatorWidth(320)
        advanceUntilIdle()
        assertEquals(320, viewModel.uiState.value.presentation.navigatorWidth)

        // Clamped values
        viewModel.setNavigatorWidth(100)
        advanceUntilIdle()
        assertEquals(StudioPresentation.MIN_NAVIGATOR_WIDTH, viewModel.uiState.value.presentation.navigatorWidth)

        viewModel.setNavigatorWidth(500)
        advanceUntilIdle()
        assertEquals(StudioPresentation.MAX_NAVIGATOR_WIDTH, viewModel.uiState.value.presentation.navigatorWidth)

        // Save presentation persists state to store
        viewModel.selectStage(StageId.MODULE_APPLICATION)
        viewModel.selectObject(StageId.MODULE_APPLICATION, "enabled-ordered-modules")
        advanceUntilIdle()

        viewModel.requestSave()
        // Assert the transient notification before advancing time - advanceUntilIdle() would also run
        // requestSave()'s own 2s auto-dismiss delay to completion, clearing it before we could observe it.
        assertNotNull(viewModel.uiState.value.saveRequestedNotification)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.saveRequestedNotification, "Notification must auto-dismiss")

        val persisted = store.get("ws-persist")
        assertNotNull(persisted)
        assertEquals(StageId.MODULE_APPLICATION, persisted.editorStageId)
        assertEquals("enabled-ordered-modules", persisted.editorObjectId)
        assertTrue(persisted.hasOpened)
    }

    @Test
    fun viewModelNeverEmitsMachineSetupOrToolsAsStagesOrSubobjects() = runTest(testDispatcher) {
        val store = InMemoryStudioPresentationStore()
        val viewModel = StudioViewModel(
            studioPresentationStore = store,
            initialWorkspaceId = "ws-check",
        )
        advanceUntilIdle()

        val railItems = viewModel.uiState.value.railItems
        assertEquals(8, railItems.size, "Must have exactly 8 ROM build stages")

        val stageTitles = railItems.map { it.stage.title }
        val stageLabels = railItems.map { it.stage.accessibleLabel }
        val subobjectTitles = railItems.flatMap { it.stage.subobjects }.map { it.title }
        val subobjectLabels = railItems.flatMap { it.stage.subobjects }.map { it.accessibleLabel }

        val forbiddenTerms = listOf("environment", "tools", "machine setup", "provisioning", "toolchain")

        forbiddenTerms.forEach { term ->
            assertTrue(
                stageTitles.none { it.lowercase().contains(term) },
                "Stage titles must never contain '$term': $stageTitles",
            )
            assertTrue(
                stageLabels.none { it.lowercase().contains(term) },
                "Stage accessible labels must never contain '$term': $stageLabels",
            )
            assertTrue(
                subobjectTitles.none { it.lowercase().contains(term) },
                "Subobject titles must never contain '$term': $subobjectTitles",
            )
            assertTrue(
                subobjectLabels.none { it.lowercase().contains(term) },
                "Subobject accessible labels must never contain '$term': $subobjectLabels",
            )
        }
    }
}
