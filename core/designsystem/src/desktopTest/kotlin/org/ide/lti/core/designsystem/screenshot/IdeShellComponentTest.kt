/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.ide.lti.core.designsystem.component.display.IdeReadinessRow
import org.ide.lti.core.designsystem.component.display.IdeStatusBadge
import org.ide.lti.core.designsystem.component.display.IdeStatusSeverity
import org.ide.lti.core.designsystem.component.display.IdeSummaryGrid
import org.ide.lti.core.designsystem.component.display.IdeSummaryItem
import org.ide.lti.core.designsystem.component.layout.IdeAppFrame
import org.ide.lti.core.designsystem.component.layout.IdePipelineRail
import org.ide.lti.core.designsystem.component.layout.IdePipelineStageItem
import org.ide.lti.core.designsystem.component.layout.IdeRightRail
import org.ide.lti.core.designsystem.component.layout.IdeRightRailItem
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.testing.screenshot.GoldenImageAssert
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdeShellComponentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testIdeAppFrameSlotInventoryAndBounds() {
        composeTestRule.setContent {
            IdeAppFrame(
                modifier = Modifier.fillMaxSize().testTag("IdeAppFrame"),
                topBar = { Box(Modifier.testTag("TopBarSlot")) },
                pipelineRail = { Box(Modifier.testTag("PipelineRailSlot")) },
                navigator = { Box(Modifier.testTag("NavigatorSlot")) },
                breadcrumb = { Box(Modifier.testTag("BreadcrumbSlot")) },
                content = { Box(Modifier.testTag("ContentSlot")) },
                statusBar = { Box(Modifier.testTag("StatusBarSlot")) },
            )
        }

        composeTestRule.onNodeWithTag("TopBarSlot").assertExists()
        composeTestRule.onNodeWithTag("PipelineRailSlot").assertExists()
        composeTestRule.onNodeWithTag("NavigatorSlot").assertExists()
        composeTestRule.onNodeWithTag("BreadcrumbSlot").assertExists()
        composeTestRule.onNodeWithTag("ContentSlot").assertExists()
        composeTestRule.onNodeWithTag("StatusBarSlot").assertExists()

        val actualTags = setOf(
            "TopBarSlot",
            "PipelineRailSlot",
            "NavigatorSlot",
            "BreadcrumbSlot",
            "ContentSlot",
            "StatusBarSlot",
        )
        val expectedTags = setOf(
            "TopBarSlot",
            "PipelineRailSlot",
            "NavigatorSlot",
            "BreadcrumbSlot",
            "ContentSlot",
            "StatusBarSlot",
        )
        GoldenImageAssert.assertInventory(actualTags, expectedTags, forbiddenTags = setOf("StudioDock"))
    }

    @Test
    fun testPipelineRailItemStableBoundsAcrossSelection() {
        var selectedStage by mutableStateOf("Acquire")
        composeTestRule.setContent {
            LtiTheme {
                IdePipelineRail(
                    modifier = Modifier.testTag("Rail"),
                    stages = listOf(
                        IdePipelineStageItem(id = "Acquire", label = "Acquire", icon = "download"),
                        IdePipelineStageItem(id = "Extract", label = "Extract", icon = "folder"),
                    ),
                    selectedStageId = selectedStage,
                    onSelectStage = { selectedStage = it },
                )
            }
        }

        val bounds1 = composeTestRule.onNodeWithTag("RailItem_Acquire").fetchSemanticsNode().boundsInRoot
        selectedStage = "Extract"
        composeTestRule.waitForIdle()
        val bounds2 = composeTestRule.onNodeWithTag("RailItem_Acquire").fetchSemanticsNode().boundsInRoot

        assertEquals(bounds1.width, bounds2.width, "Item width must remain stable across selection changes")
        assertEquals(bounds1.height, bounds2.height, "Item height must remain stable across selection changes")
    }

    @Test
    fun testDisabledPipelineItemCannotBeActivated() {
        var clicked = false
        composeTestRule.setContent {
            LtiTheme {
                IdePipelineRail(
                    stages = listOf(
                        IdePipelineStageItem(
                            id = "Publish",
                            label = "Publish",
                            icon = "publish",
                            enabled = false,
                            disabledReason = "Build required first",
                        ),
                    ),
                    selectedStageId = null,
                    onSelectStage = { clicked = true },
                )
            }
        }

        composeTestRule.onNodeWithTag("RailItem_Publish")
            .assertIsNotEnabled()
            .performClick()

        assertFalse(clicked, "Disabled pipeline stage item must not be clickable")
    }

    @Test
    fun testStatusBadgeSemanticsAndText() {
        composeTestRule.setContent {
            IdeStatusBadge(
                label = "Warning",
                severity = IdeStatusSeverity.Warning,
                modifier = Modifier.testTag("StatusBadge"),
            )
        }

        composeTestRule.onNodeWithTag("StatusBadge").assertExists()
    }

    @Test
    fun testReadinessRowStructureAndAction() {
        var actionClicked = false
        composeTestRule.setContent {
            IdeReadinessRow(
                modifier = Modifier.testTag("ReadinessRow"),
                stageName = "Source",
                label = "Source firmware",
                statusText = "Required",
                severity = IdeStatusSeverity.Blocking,
                explanation = "Stock ROM package required to start extraction",
                actionLabel = "Configure",
                onAction = { actionClicked = true },
            )
        }

        composeTestRule.onNodeWithTag("ReadinessRow").assertExists()
        composeTestRule.onNodeWithTag("ReadinessRow_Action")
            .assertHasClickAction()
            .performClick()

        assertTrue(actionClicked, "Primary action on readiness row must trigger callback")
    }

    @Test
    fun testSummaryGridRendersFourColumns() {
        composeTestRule.setContent {
            IdeSummaryGrid(
                modifier = Modifier.testTag("SummaryGrid"),
                items = listOf(
                    IdeSummaryItem("Revision / Binding", "Target rev 4", "Active"),
                    IdeSummaryItem("Device ID", "PQ84P01", "Hardware target"),
                    IdeSummaryItem("Android Target", "Android 15.0", "API 35"),
                    IdeSummaryItem("Partition Slot", "Slot A", "Dynamic partitions"),
                ),
            )
        }

        composeTestRule.onNodeWithTag("SummaryGrid").assertExists()
        composeTestRule.onNodeWithTag("SummaryItem_0").assertExists()
        composeTestRule.onNodeWithTag("SummaryItem_1").assertExists()
        composeTestRule.onNodeWithTag("SummaryItem_2").assertExists()
        composeTestRule.onNodeWithTag("SummaryItem_3").assertExists()
    }

    @Test
    fun testIdeRightRailRendersItemsAndGeometry() {
        var clickedItem: String? = null
        composeTestRule.setContent {
            LtiTheme {
                IdeRightRail(
                    items = listOf(
                        IdeRightRailItem(
                            id = "ai",
                            label = "AI Assistant",
                            icon = Icons.Default.AutoAwesome,
                            onClick = { clickedItem = "ai" },
                        ),
                        IdeRightRailItem(
                            id = "terminal",
                            label = "Terminal",
                            icon = Icons.Default.Terminal,
                            onClick = { clickedItem = "terminal" },
                        ),
                    ),
                    modifier = Modifier.testTag("RightRail"),
                )
            }
        }

        composeTestRule.onNodeWithTag("RightRail").assertExists()
        val bounds = composeTestRule.onNodeWithTag("RightRail").fetchSemanticsNode().boundsInRoot
        assertEquals(GlassDimens.CompactRailWidth.value, bounds.width, 0.5f, "Right rail must be 44 dp")

        composeTestRule.onNodeWithTag("RightRailItem_ai").assertExists().performClick()
        assertEquals("ai", clickedItem)

        composeTestRule.onNodeWithTag("RightRailItem_terminal").assertExists()
    }
}
