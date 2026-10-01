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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.model.run.StageId
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudioShellLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testWorkspaceShellThemeBlue() {
        renderAndCaptureShell(AppTheme.Blue, "workspace_shell_blue")
    }

    @Test
    fun testWorkspaceShellThemeDark() {
        renderAndCaptureShell(AppTheme.Dark, "workspace_shell_dark")
    }

    @Test
    fun testWorkspaceShellThemeLight() {
        renderAndCaptureShell(AppTheme.Light, "workspace_shell_light")
    }

    @Test
    fun testWorkspaceShellEffectsOff() {
        renderAndCaptureShell(AppTheme.Dark, "workspace_shell_effects_off", effectsEnabled = false)
    }

    @Test
    fun testWorkspaceShellEffectsOffBlue() {
        renderAndCaptureShell(AppTheme.Blue, "workspace_shell_effects_off_blue", effectsEnabled = false)
    }

    @Test
    fun testWorkspaceShellEffectsOffLight() {
        renderAndCaptureShell(AppTheme.Light, "workspace_shell_effects_off_light", effectsEnabled = false)
    }

    @Test
    fun testWorkspaceShellThemesProduceDistinctVisuals() {
        val blueImage = renderAndCaptureShell(AppTheme.Blue, "workspace_shell_blue")
        val darkImage = renderAndCaptureShell(AppTheme.Dark, "workspace_shell_dark")
        val lightImage = renderAndCaptureShell(AppTheme.Light, "workspace_shell_light")

        assertTrue(pixelDifferencePercent(blueImage, darkImage) > MIN_THEME_DIFF_PERCENT)
        assertTrue(pixelDifferencePercent(darkImage, lightImage) > MIN_THEME_DIFF_PERCENT)
    }

    private fun renderAndCaptureShell(theme: AppTheme, name: String, effectsEnabled: Boolean = true): ImageBitmap {
        var observedTheme: AppTheme? = null
        composeTestRule.setContent {
            LtiTheme(appTheme = theme, effectsEnabled = effectsEnabled) {
                observedTheme = GlassTheme.appTheme
                Box(Modifier.fillMaxSize()) {
                    GlassBackdrop()
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
        }

        composeTestRule.waitForIdle()
        assertEquals(theme, observedTheme)
        val node = composeTestRule.onNodeWithTag("StudioShellLayout")
        val image = node.captureToImage()
        node.captureRoboImage(
            filePath = "src/desktopTest/resources/screenshots/$name.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f),
                recordOptions = RoborazziOptions.RecordOptions(resizeScale = 1.0),
            ),
        )
        return image
    }

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
            LtiTheme {
                Box(Modifier.size(GlassDimens.ReferenceWindowWidth, GlassDimens.ReferenceWindowHeight)) {
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
        }

        // Top App Bar
        composeTestRule.onNodeWithTag("GlobalAppTopBar").assertExists()
        composeTestRule.onNodeWithTag("TopBarBrand").assertExists()
        composeTestRule.onNodeWithTag("TopBar_Setup").assertExists()
        composeTestRule.onNodeWithTag("TopBar_Workspace").assertExists()
        composeTestRule.onNodeWithTag("TopBar_Settings").assertExists()
        // TargetContext/Validate/Save are owned by whichever destination is active (ContentHeader
        // for a stage editor, OverviewValidateAndSave for Overview) rather than by the persistent
        // top bar - this state renders Overview, so they are not asserted here.
        composeTestRule.onNodeWithTag("TopBar_DragRegion").assertExists()

        // Pipeline Rail & exactly 8 stages
        composeTestRule.onNodeWithTag("IdePipelineRail").assertExists()
        for (stage in org.ide.lti.feature.rom.studio.api.CanonicalStageDescriptors.ALL) {
            composeTestRule.onNodeWithTag("RailItem_${stage.stageId.name}").assertExists()
        }

        // Workspace Navigator & 5 sections
        composeTestRule.onNodeWithTag("WorkspaceNavigator").assertExists()
        composeTestRule.onNodeWithTag("NavigatorItem_Overview").assertExists()
        composeTestRule.onNodeWithTag("NavigatorItem_RomConfig").assertExists()
        composeTestRule.onNodeWithTag("NavigatorItem_RunHistory").assertExists()
        composeTestRule.onNodeWithTag("NavigatorItem_Artifacts").assertExists()
        composeTestRule.onNodeWithTag("NavigatorItem_TargetProfile").assertExists()

        // Workspace Right Rail & items
        composeTestRule.onNodeWithTag("WorkspaceRightRail").assertExists()
        composeTestRule.onNodeWithTag("RightRailItem_ai").assertExists()
        composeTestRule.onNodeWithTag("RightRailItem_terminal").assertExists()

        // Content, Status Bar (no separate breadcrumb region - each destination's own header
        // owns its breadcrumb; see StudioScreen.kt's isStageEditor-gated ContentHeader)
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
            LtiTheme {
                Box(Modifier.size(GlassDimens.ReferenceWindowWidth, GlassDimens.ReferenceWindowHeight)) {
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
        }

        val topBarBounds = composeTestRule.onNodeWithTag("GlobalAppTopBar").fetchSemanticsNode().boundsInRoot
        val railBounds = composeTestRule.onNodeWithTag("IdePipelineRail").fetchSemanticsNode().boundsInRoot
        val navBounds = composeTestRule.onNodeWithTag("WorkspaceNavigator").fetchSemanticsNode().boundsInRoot
        val contentBounds = composeTestRule.onNodeWithTag("MainContent").fetchSemanticsNode().boundsInRoot
        val statusBarBounds = composeTestRule.onNodeWithTag("StudioStatusBar").fetchSemanticsNode().boundsInRoot
        val rightRailBounds = composeTestRule.onNodeWithTag("WorkspaceRightRail").fetchSemanticsNode().boundsInRoot

        // Vertical relationships
        assertTrue(topBarBounds.bottom <= railBounds.top, "Rail must be below Top Bar")
        assertTrue(topBarBounds.bottom <= navBounds.top, "Navigator must be below Top Bar")
        assertTrue(topBarBounds.bottom <= rightRailBounds.top, "Right Rail must be below Top Bar")
        assertTrue(navBounds.bottom <= contentBounds.top, "Content must be below Navigator")
        assertTrue(railBounds.bottom <= statusBarBounds.top, "Rail must end above Status Bar")
        assertTrue(contentBounds.bottom <= statusBarBounds.top, "Content must end above Status Bar")
        assertTrue(rightRailBounds.bottom <= statusBarBounds.top, "Right Rail must end above Status Bar")

        // Horizontal relationships
        assertTrue(railBounds.right <= navBounds.left, "Navigator must be to the right of Rail")
        assertTrue(railBounds.right <= contentBounds.left, "Content must be to the right of Rail")
        assertTrue(contentBounds.right <= rightRailBounds.left, "Content must be to the left of Right Rail")
    }

    @Test
    fun testExactlyOneTopBarAndOneStatusBar() {
        composeTestRule.setContent {
            LtiTheme {
                Box(Modifier.size(GlassDimens.ReferenceWindowWidth, GlassDimens.ReferenceWindowHeight)) {
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
        }

        composeTestRule.onAllNodesWithTag("GlobalAppTopBar").assertCountEquals(1)
        composeTestRule.onAllNodesWithTag("StudioStatusBar").assertCountEquals(1)
    }

    @Test
    fun testNonInteractiveDragRegionsDoNotInterceptControls() {
        var clickedDestination: GlobalDestination? = null
        var selectedStage: StageId? = null
        var selectedSection: WorkspaceSection? = null

        composeTestRule.setContent {
            LtiTheme {
                Box(Modifier.size(GlassDimens.ReferenceWindowWidth, GlassDimens.ReferenceWindowHeight)) {
                    StudioShellContent(
                        uiState = WorkspaceShellUiState(
                            pipelineItems = createDefaultPipelineRailItems(StageId.FIRMWARE_ACQUISITION),
                        ),
                        onSelectGlobalDestination = { clickedDestination = it },
                        onSelectWorkspaceSection = { selectedSection = it },
                        onSelectStage = { selectedStage = it },
                        onValidate = {},
                        onSave = {},
                        content = { Box(Modifier.fillMaxSize()) },
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("TopBar_Settings").assertHasClickAction().performClick()
        assertEquals(GlobalDestination.Settings, clickedDestination)

        // Validate/Save live in the active destination's own header (ContentHeader for a stage
        // editor, OverviewValidateAndSave for Overview), not the persistent top bar - their click
        // wiring is covered where they are rendered, not here.

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
            LtiTheme {
                Box(Modifier.size(GlassDimens.ReferenceWindowWidth, GlassDimens.ReferenceWindowHeight)) {
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

    @Test
    fun testStudioShellIconsOnlyMode() {
        val uiState = WorkspaceShellUiState(
            globalDestination = GlobalDestination.Workspace,
            pipelineItems = createDefaultPipelineRailItems(StageId.FIRMWARE_ACQUISITION),
            selectedWorkspaceSection = WorkspaceSection.Overview,
        )

        composeTestRule.setContent {
            LtiTheme {
                Box(Modifier.size(GlassDimens.ReferenceWindowWidth, GlassDimens.ReferenceWindowHeight)) {
                    StudioShellContent(
                        uiState = uiState,
                        onSelectGlobalDestination = {},
                        onSelectWorkspaceSection = {},
                        onSelectStage = {},
                        onValidate = {},
                        onSave = {},
                        iconsOnly = true,
                        content = { Box(Modifier.fillMaxSize()) },
                    )
                }
            }
        }

        val railBounds = composeTestRule.onNodeWithTag("IdePipelineRail").fetchSemanticsNode().boundsInRoot
        val navBounds = composeTestRule.onNodeWithTag("WorkspaceNavigator").fetchSemanticsNode().boundsInRoot
        val rightRailBounds = composeTestRule.onNodeWithTag("WorkspaceRightRail").fetchSemanticsNode().boundsInRoot

        assertEquals(GlassDimens.CompactRailWidth.value, railBounds.width, 0.5f, "Rail width must be 44 dp")
        assertEquals(GlassDimens.CompactRailWidth.value, rightRailBounds.width, 0.5f, "Right rail width must be 44 dp")
        assertTrue(navBounds.width > GlassDimens.CompactRailWidth.value, "Navigator tab bar is horizontal")

        for (stage in org.ide.lti.feature.rom.studio.api.CanonicalStageDescriptors.ALL) {
            composeTestRule.onNodeWithTag("RailItem_${stage.stageId.name}").assertExists()
        }

        composeTestRule.onNodeWithTag("NavigatorItem_Overview").assertExists()
        composeTestRule.onNodeWithTag("NavigatorItem_RomConfig").assertExists()
        composeTestRule.onNodeWithTag("NavigatorItem_RunHistory").assertExists()
        composeTestRule.onNodeWithTag("NavigatorItem_Artifacts").assertExists()
        composeTestRule.onNodeWithTag("NavigatorItem_TargetProfile").assertExists()

        composeTestRule.onNodeWithTag("WorkspaceNavigatorFooter").assertDoesNotExist()
    }

    private fun pixelDifferencePercent(first: ImageBitmap, second: ImageBitmap): Double {
        val firstPixels = first.toPixelMap()
        val secondPixels = second.toPixelMap()
        var differentPixels = 0
        for (y in 0 until first.height) {
            for (x in 0 until first.width) {
                if (firstPixels[x, y] != secondPixels[x, y]) {
                    differentPixels++
                }
            }
        }
        return differentPixels.toDouble() / (first.width * first.height) * 100.0
    }

    private companion object {
        const val MIN_THEME_DIFF_PERCENT = 5.0
    }
}
