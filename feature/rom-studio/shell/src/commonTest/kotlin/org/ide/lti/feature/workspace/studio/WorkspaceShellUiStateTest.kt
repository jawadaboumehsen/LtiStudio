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

import org.ide.lti.core.model.run.StageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceShellUiStateTest {

    @Test
    fun testDefaultWorkspaceStateHasWorkspaceAsActiveGlobalDestination() {
        val state = WorkspaceShellUiState()
        assertEquals(GlobalDestination.Workspace, state.globalDestination)
        assertEquals(WorkspaceSection.Overview, state.selectedWorkspaceSection)
        assertFalse(state.hasUnsavedChanges)
        assertEquals(0, state.problemCount)
        assertEquals(OperationUiState.Idle, state.validateOperation)
        assertEquals(OperationUiState.Idle, state.saveOperation)
    }

    @Test
    fun testPipelineListContainsExactlyEightCatalogOrderedItems() {
        val state = WorkspaceShellUiState()
        assertEquals(8, state.pipelineItems.size, "Pipeline rail must expose exactly eight stages")

        val expectedStages = listOf(
            StageId.FIRMWARE_ACQUISITION,
            StageId.FIRMWARE_EXTRACTION,
            StageId.WORK_TREE_ASSEMBLY,
            StageId.DEBLOAT,
            StageId.MODULE_APPLICATION,
            StageId.BUILD_FLASHABLE_ZIP,
            StageId.GENERATE_OTA_MANIFEST,
            StageId.PUBLISH_RELEASE,
        )

        expectedStages.forEachIndexed { index, expectedStageId ->
            val item = state.pipelineItems[index]
            assertEquals(expectedStageId, item.stageId)
            assertEquals(index + 1, item.position)
            assertNotNull(item.label)
            assertNotNull(item.iconKey)
            assertTrue(item.accessibleDescription.isNotBlank())
        }
    }

    @Test
    fun testNegativeProblemCountThrowsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> {
            WorkspaceShellUiState(problemCount = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            WorkspaceShellUiState(problemCount = -42)
        }
    }

    @Test
    fun testNonEightPipelineItemsThrowsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> {
            WorkspaceShellUiState(pipelineItems = emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            WorkspaceShellUiState(pipelineItems = createDefaultPipelineRailItems().take(7))
        }
        assertFailsWith<IllegalArgumentException> {
            val nineItems = createDefaultPipelineRailItems() + createDefaultPipelineRailItems().take(1)
            WorkspaceShellUiState(pipelineItems = nineItems)
        }
    }

    @Test
    fun testSelectedWorkspaceSectionNullOnlyForNonWorkspaceDestinations() {
        // Non-workspace global destinations must have null workspace section
        assertFailsWith<IllegalArgumentException> {
            WorkspaceShellUiState(
                globalDestination = GlobalDestination.Setup,
                selectedWorkspaceSection = WorkspaceSection.Overview,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WorkspaceShellUiState(
                globalDestination = GlobalDestination.Settings,
                selectedWorkspaceSection = WorkspaceSection.RomConfig,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WorkspaceShellUiState(
                globalDestination = GlobalDestination.PluginManager,
                selectedWorkspaceSection = WorkspaceSection.RunHistory,
            )
        }

        val setupState = WorkspaceShellUiState(
            globalDestination = GlobalDestination.Setup,
            selectedWorkspaceSection = null,
        )
        assertNull(setupState.selectedWorkspaceSection)
        assertEquals(GlobalDestination.Setup, setupState.globalDestination)

        val settingsState = WorkspaceShellUiState(
            globalDestination = GlobalDestination.Settings,
            selectedWorkspaceSection = null,
        )
        assertNull(settingsState.selectedWorkspaceSection)
        assertEquals(GlobalDestination.Settings, settingsState.globalDestination)
    }

    @Test
    fun testTargetContextExplicitAvailableAndUnavailableValues() {
        val targetContext = TargetContextUi(
            targetId = DisplayValue.Available("Pixel 8 Pro (shiba)"),
            profile = DisplayValue.Available("default-aosp"),
            revision = DisplayValue.Available("rev-104"),
            draftBadge = "Unsaved draft (2 files modified)",
        )

        assertTrue(targetContext.targetId is DisplayValue.Available)
        assertEquals("Pixel 8 Pro (shiba)", (targetContext.targetId as DisplayValue.Available).text)
        assertEquals("Unsaved draft (2 files modified)", targetContext.draftBadge)

        val unavailableTarget = TargetContextUi(
            targetId = DisplayValue.Unavailable("No active ROM profile selected"),
            profile = DisplayValue.Unavailable("Environment unconfigured"),
            revision = DisplayValue.Unavailable("Workspace not initialized"),
            draftBadge = null,
        )

        assertTrue(unavailableTarget.targetId is DisplayValue.Unavailable)
        assertEquals(
            "No active ROM profile selected",
            (unavailableTarget.targetId as DisplayValue.Unavailable).reason,
        )
        assertNull(unavailableTarget.draftBadge)
    }

    @Test
    fun testOperationUiStateTransitions() {
        val idle: OperationUiState = OperationUiState.Idle
        assertEquals(OperationUiState.Idle, idle)

        val running: OperationUiState = OperationUiState.Running(generation = 3L, startedFromRevision = 10L)
        assertTrue(running is OperationUiState.Running)
        assertEquals(3L, running.generation)
        assertEquals(10L, running.startedFromRevision)

        val succeeded: OperationUiState = OperationUiState.Succeeded(
            generation = 3L,
            completedRevision = 11L,
            message = "Validation passed without errors",
        )
        assertTrue(succeeded is OperationUiState.Succeeded)
        assertEquals(11L, succeeded.completedRevision)

        val failed: OperationUiState = OperationUiState.Failed(
            generation = 3L,
            startedFromRevision = 10L,
            userMessage = "Payload integrity checksum mismatch",
        )
        assertTrue(failed is OperationUiState.Failed)
        assertEquals("Payload integrity checksum mismatch", failed.userMessage)
    }
}
