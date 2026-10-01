/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolCategory
import org.ide.lti.core.domain.setup.ToolComponentItem
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.feature.setup.components.SetupActivityPanel
import org.ide.lti.feature.setup.steps.ToolsStepContent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Desktop Compose UI tests for the Tools Destination and Activity Viewport (US3 — Tools and activity).
 *
 * Fully DI-free fixtures validating:
 * - Unsupported repair: displays "Not built by setup, so it can't be rebuilt here. Install or update it yourself; Test still works."
 *   and disables rebuild button (FR-009, SC-002).
 * - Supported repair: rebuild action enabled and invokes repair callback (FR-009).
 * - Missing/Failed state: displays failure status and Test action triggers verification (FR-009).
 * - Repairing/Busy state: disables concurrent mutations (FR-008, FR-009).
 * - Bounded activity buffer: enforces 10,000 line cap, tracks dropped lines, and displays visible dropped count badge (FR-010).
 * - Live activity viewport: pause-follow, Jump to latest, line selection, and copy actions (FR-010).
 */
@OptIn(ExperimentalTestApi::class)
class ToolsScreenTest {

    @Test
    fun testUnsupportedRepairDisplaysReasonAndDisablesRebuild() = runDesktopComposeUiTest(width = 1120, height = 900) {
        val unsupportedTool = ToolComponentItem(
            id = "custom-vendor-tool",
            name = "Custom Vendor Tool",
            binaryName = "custom-vendor-tool",
            category = ToolCategory.PACKAGING_AND_TOOLS,
            isCore = false,
            status = StepStatus.PENDING,
        )

        setContent {
            LtiTheme {
                ToolsStepContent(
                    state = SetupUiState(
                        filteredToolsMatrix = listOf(unsupportedTool),
                    ),
                    actions = SetupUiActions(),
                )
            }
        }

        // Exact specification phrase must be displayed
        onNodeWithText(
            "Not built by setup, so it can't be rebuilt here. Install or update it yourself; Test still works.",
        ).assertIsDisplayed()

        // Rebuild button must be disabled for unsupported recipes
        onNodeWithText("Rebuild").assertIsNotEnabled()
    }

    @Test
    fun testSupportedRecipeShowsEnabledRebuildAndTriggersAction() =
        runDesktopComposeUiTest(width = 1120, height = 900) {
            val supportedTool = ToolComponentItem(
                id = "lpmake",
                name = "Logical Partition Maker",
                binaryName = "lpmake",
                category = ToolCategory.DYNAMIC_PARTITIONS,
                isCore = true,
                status = StepStatus.FAILED,
            )

            var recompileTarget: String? = null

            setContent {
                LtiTheme {
                    ToolsStepContent(
                        state = SetupUiState(
                            filteredToolsMatrix = listOf(supportedTool),
                        ),
                        actions = SetupUiActions(
                            onRecompileTool = { recompileTarget = it },
                        ),
                    )
                }
            }

            // Rebuild button is enabled and triggers callback
            onNodeWithText("Rebuild").assertIsDisplayed().performClick()
            assertEquals("lpmake", recompileTarget)
        }

    @Test
    fun testMissingOrFailedToolDisplaysStatusAndTestRunsVerification() =
        runDesktopComposeUiTest(width = 1120, height = 900) {
            val failedTool = ToolComponentItem(
                id = "mke2fs",
                name = "Ext4 Filesystem Formatter",
                binaryName = "mke2fs",
                category = ToolCategory.FILESYSTEM_AND_IMAGES,
                isCore = true,
                status = StepStatus.FAILED,
                lastTested = "Failed with exit code 127",
            )

            var testTarget: String? = null

            setContent {
                LtiTheme {
                    ToolsStepContent(
                        state = SetupUiState(
                            filteredToolsMatrix = listOf(failedTool),
                        ),
                        actions = SetupUiActions(
                            onTestTool = { testTarget = it },
                        ),
                    )
                }
            }

            onNodeWithText("· Failed with exit code 127").assertIsDisplayed()
            onNodeWithText("Test").assertIsDisplayed().performClick()
            assertEquals("mke2fs", testTarget)
        }

    @Test
    fun testBusyStateDisablesInteractiveActions() = runDesktopComposeUiTest(width = 1120, height = 900) {
        val tool = ToolComponentItem(
            id = "adb",
            name = "Android Debug Bridge",
            binaryName = "adb",
            category = ToolCategory.BRIDGE_AND_FLASHING,
            isCore = true,
            status = StepStatus.RUNNING,
        )

        setContent {
            LtiTheme {
                ToolsStepContent(
                    state = SetupUiState(
                        toolchainSetupState = ToolchainSetupState(isRunning = true),
                        filteredToolsMatrix = listOf(tool),
                    ),
                    actions = SetupUiActions(),
                )
            }
        }

        onNodeWithText("Test").assertIsNotEnabled()
        onNodeWithText("Rebuild").assertIsNotEnabled()
    }

    @Test
    fun testLogBufferExceeding10000LinesDisplaysDroppedBadgeAndBoundedCount() =
        runDesktopComposeUiTest(width = 1120, height = 900) {
            val testScope = CoroutineScope(Dispatchers.Unconfined)
            val activityState = SetupActivityState(scope = testScope)

            // Append 10,500 lines to exceed the 10,000 line memory buffer cap
            val lines = (1..10_500).map { "Log entry line $it" }
            activityState.appendLines(lines)

            assertEquals(
                10_000,
                activityState.visibleEvents.value.size,
                "Visible lines buffer must be capped at 10,000 lines",
            )
            assertEquals(500, activityState.droppedLineCount.value, "Dropped lines must be exactly 500")

            setContent {
                LtiTheme {
                    ToolsStepContent(
                        state = SetupUiState(
                            filteredToolsMatrix = emptyList(),
                        ),
                        actions = SetupUiActions(),
                        activityState = activityState,
                    )
                }
            }

            // Exact line count and visible dropped line badge must be shown
            onNodeWithText("10000 lines").assertIsDisplayed()
            onNodeWithText("500 dropped").assertIsDisplayed()
        }

    @Test
    fun testActivityPanelPauseFollowAndJumpToLatest() = runDesktopComposeUiTest(width = 1120, height = 900) {
        val testScope = CoroutineScope(Dispatchers.Unconfined)
        val activityState = SetupActivityState(scope = testScope)
        activityState.appendLines(listOf("Line 1", "Line 2", "Line 3"))

        setContent {
            LtiTheme {
                SetupActivityPanel(
                    activityState = activityState,
                    title = "Activity Test",
                )
            }
        }

        // Initially following latest
        assertTrue(activityState.followingLatest.value)
        onNodeWithText("Pause follow").assertIsDisplayed().performClick()

        // After pause
        assertFalse(activityState.followingLatest.value)
        onNodeWithText("Jump to latest").assertIsDisplayed().performClick()

        // After jump to latest
        assertTrue(activityState.followingLatest.value)
        onNodeWithText("Pause follow").assertIsDisplayed()
    }

    @Test
    fun testActivityPanelSelectionAndCopy() = runDesktopComposeUiTest(width = 1120, height = 900) {
        val testScope = CoroutineScope(Dispatchers.Unconfined)
        val activityState = SetupActivityState(scope = testScope)
        activityState.appendLines(listOf("Alpha log entry", "Beta log entry", "Gamma log entry"))

        setContent {
            LtiTheme {
                SetupActivityPanel(
                    activityState = activityState,
                    title = "Activity Test",
                )
            }
        }

        // Initially 0 selected lines
        assertTrue(activityState.selectedEventIds.value.isEmpty())

        // Click on "Alpha log entry": selection is keyed by the event id, not a list index
        onNodeWithText("Alpha log entry").assertIsDisplayed().performClick()
        val alphaId = activityState.visibleEvents.value.first { it.text == "Alpha log entry" }.id
        assertEquals(setOf(alphaId), activityState.selectedEventIds.value)
        onNodeWithText("Copy selection (1)").assertIsDisplayed()

        // Clear selection via Clear button
        onNodeWithContentDescription("Clear selection").assertIsDisplayed().performClick()
        assertTrue(activityState.selectedEventIds.value.isEmpty())
    }
}
