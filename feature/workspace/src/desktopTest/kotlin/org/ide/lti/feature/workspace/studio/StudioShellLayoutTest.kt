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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.model.run.StageId
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudioShellLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testRequiredSemanticInventory() {
        val uiState = WorkspaceShellUiState(
            globalDestination = GlobalDestination.Workspace,
            targetContext = TargetContextUi(
                targetId = DisplayValue.Available("Pixel 8 Pro (shiba)"),
                profile = DisplayValue.Available("default-aosp"),
                revision = DisplayValue.Available("rev-104"),
                draftBadge = null,
            ),
            problemCount = 2,
            activity = ActivityUiState.Idle,
            connection = ConnectionUiState.Connected("device-001"),
            pipelineItems = createDefaultPipelineRailItems(StageId.FIRMWARE_ACQUISITION),
            selectedWorkspaceSection = WorkspaceSection.Overview,
        )

        composeTestRule.setContent {
            Box(Modifier.size(IdeShellDimens.ReferenceWindowWidth, IdeShellDimens.ReferenceWindowHeight)) {
                StudioShellContent(
                    uiState = uiState,
                    onSelectGlobalDestination = {},
                    onSelectWorkspaceSection = {},
                    onSelectStage = {},
                    onValidate = {},
                    onSave = {},
                    content = { Box(Modifier.fillMaxSize().testTag("MainContent")) },
                )
            }
        }

        // Top App Bar
        composeTestRule.onNodeWithTag("GlobalAppTopBar").assertExists()
        composeTestRule.onNodeWithTag("TopBarBrand").assertExists()
        composeTestRule.onNodeWithTag("TopBar_Setup").assertExists()
        composeTestRule.onNodeWithTag("TopBar_Workspace").assertExists()
        composeTestRule.onNodeWithTag("TopBar_Settings").assertExists()
        composeTestRule.onNodeWithTag("TopBarTargetContext").assertExists()
        composeTestRule.onNodeWithTag("TopBar_Validate").assertExists()
        composeTestRule.onNodeWithTag("TopBar_Save").assertExists()
        composeTestRule.onNodeWithTag("TopBar_DragRegion").assertExists()

        // Pipeline Rail & exactly 8 stages
        composeTestRule.onNodeWithTag("IdePipelineRail").assertExists()
        for (stage in StudioDestinationCatalog.stages) {
            composeTestRule.onNodeWithTag("RailItem_${stage.stageId.name}").assertExists()
        }

        // Workspace Navigator & 5 sections
        composeTestRule.onNodeWithTag("WorkspaceNavigator").assertExists()
        composeTestRule.onNodeWithTag("NavigatorItem_Overview").assertExists()
        composeTestRule.onNodeWithTag("NavigatorItem_RomConfig").assertExists()
        composeTestRule.onNodeWithTag("NavigatorItem_RunHistory").assertExists()
        composeTestRule.onNodeWithTag("NavigatorItem_Artifacts").assertExists()
        composeTestRule.onNodeWithTag("NavigatorItem_TargetProfile").assertExists()
        composeTestRule.onNodeWithTag("WorkspaceNavigatorFooter").assertExists()

        // Breadcrumb, Content, Status Bar
        composeTestRule.onNodeWithTag("IdeBreadcrumbBar").assertExists()
        composeTestRule.onNodeWithTag("MainContent").assertExists()
        composeTestRule.onNodeWithTag("StudioStatusBar").assertExists()
        composeTestRule.onNodeWithTag("StatusBar_Problems").assertExists()
        composeTestRule.onNodeWithTag("StatusBar_Activity").assertExists()
        composeTestRule.onNodeWithTag("StatusBar_Connection").assertExists()
    }

    @Test
    fun testFixedRegionNonOverlap() {
        val uiState = WorkspaceShellUiState(
            globalDestination = GlobalDestination.Workspace,
            targetContext = TargetContextUi(
                targetId = DisplayValue.Available("Pixel 8 Pro (shiba)"),
                profile = DisplayValue.Available("default-aosp"),
                revision = DisplayValue.Available("rev-104"),
            ),
            pipelineItems = createDefaultPipelineRailItems(StageId.FIRMWARE_ACQUISITION),
            selectedWorkspaceSection = WorkspaceSection.Overview,
        )

        composeTestRule.setContent {
            Box(Modifier.size(IdeShellDimens.ReferenceWindowWidth, IdeShellDimens.ReferenceWindowHeight)) {
                StudioShellContent(
                    uiState = uiState,
                    onSelectGlobalDestination = {},
                    onSelectWorkspaceSection = {},
                    onSelectStage = {},
                    onValidate = {},
                    onSave = {},
                    content = { Box(Modifier.fillMaxSize().testTag("MainContent")) },
                )
            }
        }

        val topBarBounds = composeTestRule.onNodeWithTag("GlobalAppTopBar").fetchSemanticsNode().boundsInRoot
        val railBounds = composeTestRule.onNodeWithTag("IdePipelineRail").fetchSemanticsNode().boundsInRoot
        val navBounds = composeTestRule.onNodeWithTag("WorkspaceNavigator").fetchSemanticsNode().boundsInRoot
        val breadcrumbBounds = composeTestRule.onNodeWithTag("IdeBreadcrumbBar").fetchSemanticsNode().boundsInRoot
        val contentBounds = composeTestRule.onNodeWithTag("MainContent").fetchSemanticsNode().boundsInRoot
        val statusBarBounds = composeTestRule.onNodeWithTag("StudioStatusBar").fetchSemanticsNode().boundsInRoot

        // Vertical relationships
        assertTrue(topBarBounds.bottom <= railBounds.top, "Rail must be below Top Bar")
        assertTrue(topBarBounds.bottom <= navBounds.top, "Navigator must be below Top Bar")
        assertTrue(topBarBounds.bottom <= breadcrumbBounds.top, "Breadcrumb must be below Top Bar")
        assertTrue(breadcrumbBounds.bottom <= contentBounds.top, "Content must be below Breadcrumb")
        assertTrue(railBounds.bottom <= statusBarBounds.top, "Rail must end above Status Bar")
        assertTrue(navBounds.bottom <= statusBarBounds.top, "Navigator must end above Status Bar")
        assertTrue(contentBounds.bottom <= statusBarBounds.top, "Content must end above Status Bar")

        // Horizontal relationships
        assertTrue(railBounds.right <= navBounds.left, "Navigator must be to the right of Rail")
        assertTrue(navBounds.right <= contentBounds.left, "Content must be to the right of Navigator")
    }

    @Test
    fun testExactlyOneTopBarAndOneStatusBar() {
        composeTestRule.setContent {
            Box(Modifier.size(IdeShellDimens.ReferenceWindowWidth, IdeShellDimens.ReferenceWindowHeight)) {
                StudioShellContent(
                    uiState = WorkspaceShellUiState(),
                    onSelectGlobalDestination = {},
                    onSelectWorkspaceSection = {},
                    onSelectStage = {},
                    onValidate = {},
                    onSave = {},
                    content = { Box(Modifier.fillMaxSize()) },
                )
            }
        }

        composeTestRule.onAllNodesWithTag("GlobalAppTopBar").assertCountEquals(1)
        composeTestRule.onAllNodesWithTag("StudioStatusBar").assertCountEquals(1)
    }

    @Test
    fun testNonInteractiveDragRegionsDoNotInterceptControls() {
        var clickedDestination: GlobalDestination? = null
        var validated = false
        var selectedStage: StageId? = null
        var selectedSection: WorkspaceSection? = null

        composeTestRule.setContent {
            Box(Modifier.size(IdeShellDimens.ReferenceWindowWidth, IdeShellDimens.ReferenceWindowHeight)) {
                StudioShellContent(
                    uiState = WorkspaceShellUiState(
                        pipelineItems = createDefaultPipelineRailItems(StageId.FIRMWARE_ACQUISITION),
                    ),
                    onSelectGlobalDestination = { clickedDestination = it },
                    onSelectWorkspaceSection = { selectedSection = it },
                    onSelectStage = { selectedStage = it },
                    onValidate = { validated = true },
                    onSave = {},
                    content = { Box(Modifier.fillMaxSize()) },
                )
            }
        }

        composeTestRule.onNodeWithTag("TopBar_Settings").assertHasClickAction().performClick()
        assertEquals(GlobalDestination.Settings, clickedDestination)

        composeTestRule.onNodeWithTag("TopBar_Validate").assertHasClickAction().performClick()
        assertTrue(validated)

        composeTestRule.onNodeWithTag("RailItem_DEBLOAT").assertHasClickAction().performClick()
        assertEquals(StageId.DEBLOAT, selectedStage)

        composeTestRule.onNodeWithTag("NavigatorItem_RomConfig").assertHasClickAction().performClick()
        assertEquals(WorkspaceSection.RomConfig, selectedSection)
    }

    @Test
    fun testStableBoundsDuringSelectionChanges() {
        var uiState by mutableStateOf(
            WorkspaceShellUiState(
                pipelineItems = createDefaultPipelineRailItems(StageId.FIRMWARE_ACQUISITION),
                selectedWorkspaceSection = WorkspaceSection.Overview,
            ),
        )

        composeTestRule.setContent {
            Box(Modifier.size(IdeShellDimens.ReferenceWindowWidth, IdeShellDimens.ReferenceWindowHeight)) {
                StudioShellContent(
                    uiState = uiState,
                    onSelectGlobalDestination = {},
                    onSelectWorkspaceSection = {},
                    onSelectStage = {},
                    onValidate = {},
                    onSave = {},
                    content = { Box(Modifier.fillMaxSize()) },
                )
            }
        }

        val top1 = composeTestRule.onNodeWithTag("GlobalAppTopBar").fetchSemanticsNode().boundsInRoot
        val rail1 = composeTestRule.onNodeWithTag("IdePipelineRail").fetchSemanticsNode().boundsInRoot
        val nav1 = composeTestRule.onNodeWithTag("WorkspaceNavigator").fetchSemanticsNode().boundsInRoot
        val status1 = composeTestRule.onNodeWithTag("StudioStatusBar").fetchSemanticsNode().boundsInRoot

        // Change selection
        uiState = uiState.copy(
            pipelineItems = createDefaultPipelineRailItems(StageId.BUILD_FLASHABLE_ZIP),
            selectedWorkspaceSection = WorkspaceSection.Artifacts,
        )
        composeTestRule.waitForIdle()

        val top2 = composeTestRule.onNodeWithTag("GlobalAppTopBar").fetchSemanticsNode().boundsInRoot
        val rail2 = composeTestRule.onNodeWithTag("IdePipelineRail").fetchSemanticsNode().boundsInRoot
        val nav2 = composeTestRule.onNodeWithTag("WorkspaceNavigator").fetchSemanticsNode().boundsInRoot
        val status2 = composeTestRule.onNodeWithTag("StudioStatusBar").fetchSemanticsNode().boundsInRoot

        assertEquals(top1.width, top2.width, "Top Bar width must remain stable")
        assertEquals(top1.height, top2.height, "Top Bar height must remain stable")
        assertEquals(rail1.width, rail2.width, "Rail width must remain stable")
        assertEquals(rail1.height, rail2.height, "Rail height must remain stable")
        assertEquals(nav1.width, nav2.width, "Navigator width must remain stable")
        assertEquals(nav1.height, nav2.height, "Navigator height must remain stable")
        assertEquals(status1.width, status2.width, "Status Bar width must remain stable")
        assertEquals(status1.height, status2.height, "Status Bar height must remain stable")
    }
}
