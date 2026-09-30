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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.domain.setup.SetupLogEvent
import org.ide.lti.core.domain.setup.SetupLogKind
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.feature.setup.components.SetupActivityPanel
import org.ide.lti.feature.setup.components.ToolchainPipelineTracker
import org.ide.lti.feature.setup.steps.EnvironmentStepContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T060: Component tests for row actions and activity panel:
 * - "Check again" calls onCheckEnvironment (not navigation);
 * - "View environment" only navigates;
 * - Cancel on a running row shows the confirmation dialog and then dispatches onCancelOperation;
 * - Log filter (All/Checks/Commands/Errors, driven by SetupLogEvent.kind);
 * - Copy copies visible lines;
 * - "Showing the last N lines — export for the full log" disclosure is displayed when droppedCount > 0;
 * - Auto-scroll pausing on scroll up and "Jump to latest" behavior.
 */
@OptIn(ExperimentalTestApi::class)
class SetupActionsTest {

    @Test
    fun testCheckAgainCallsCheckEnvironmentNotNavigation() {
        runDesktopComposeUiTest(width = 1120, height = 1200) {
            var checkCalled = false
            var navigateCalled = false

            val state = ToolchainSetupState(
                steps = listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "WSL Detection",
                        description = "Ubuntu 22.04 LTS",
                        status = StepStatus.SUCCESS,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                        title = "Toolchain Compilation",
                        description = "Pending compilation",
                        status = StepStatus.PENDING,
                    ),
                ),
                isChecking = false,
            )

            setContent {
                LtiTheme {
                    EnvironmentStepContent(
                        state = SetupUiState(toolchainSetupState = state),
                        actions = SetupUiActions(
                            onCheckEnvironment = { checkCalled = true },
                        ),
                        onNavigateToProjects = { navigateCalled = true },
                    )
                }
            }

            // Click "Check again" in bottom action bar or header
            onNodeWithText("Check again").performClick()
            assertTrue(checkCalled, "Clicking 'Check again' must invoke onCheckEnvironment")
            assertFalse(navigateCalled, "Clicking 'Check again' must not navigate to projects")
        }
    }

    @Test
    fun testViewEnvironmentOnlyNavigates() {
        runDesktopComposeUiTest(width = 1120, height = 1200) {
            var checkCalled = false
            var navigateCalled = false

            val state = SetupUiState(
                environmentNotice = "Environment verification is required before proceeding.",
            )

            setContent {
                LtiTheme {
                    SetupScreenContent(
                        state = state,
                        actions = SetupUiActions(
                            onCheckEnvironment = { checkCalled = true },
                            onNavigateToEnvironment = { navigateCalled = true },
                        ),
                    )
                }
            }

            // Click "View environment" in the environment notice banner
            onNodeWithText("View environment").performClick()
            assertTrue(navigateCalled, "Clicking 'View environment' must navigate to environment")
            assertFalse(checkCalled, "Clicking 'View environment' must not trigger an environment check")
        }
    }

    @Test
    fun testCancelOnRunningRowShowsConfirmationAndDispatchesCancel() {
        runDesktopComposeUiTest(width = 1120, height = 1200) {
            var cancelDispatched = false
            val runningStep = SetupStepDetail(
                stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                title = "Toolchain Binaries",
                status = StepStatus.RUNNING,
                description = "Building llvm (1 of 4)",
            )

            setContent {
                LtiTheme {
                    ToolchainPipelineTracker(
                        steps = listOf(runningStep),
                        onRetryStep = {},
                        onCancel = { cancelDispatched = true },
                        isBusy = true,
                        // A build is an owned operation: that is what makes it cancellable.
                        activeOperation = SetupOperation(executionState = SetupOperationState.RUNNING),
                    )
                }
            }

            // Click "Cancel" on the running toolchain row
            onNodeWithText("Cancel").performClick()

            // Dialog must appear with required text
            onNodeWithText(
                "The current build of llvm stops; tools already built are kept. You can resume setup at any time.",
            ).assertIsDisplayed()

            // Confirm cancellation inside dialog
            onNodeWithText("Cancel operation").performClick()
            assertTrue(cancelDispatched, "Confirming cancel dialog must dispatch onCancel")
        }
    }

    @Test
    fun testCheckingToolchainRowOffersNoCancelItCannotHonour() {
        runDesktopComposeUiTest(width = 1120, height = 1200) {
            val checkingStep = SetupStepDetail(
                stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                title = "Toolchain Binaries",
                status = StepStatus.RUNNING,
                description = "Checking tools…",
            )

            setContent {
                LtiTheme {
                    // A check marks the step RUNNING but owns no operation: cancel() would find nothing to stop.
                    ToolchainPipelineTracker(steps = listOf(checkingStep), onRetryStep = {
                    }, onCancel = {}, isBusy = true)
                }
            }

            onNodeWithText("Checking tools…", substring = true).assertIsDisplayed()
            onAllNodesWithText("Cancel").assertCountEquals(0)
        }
    }

    private fun logEvent(seq: Long, text: String, kind: SetupLogKind = SetupLogKind.RUN) = SetupLogEvent(
        attemptId = "test-attempt",
        childRunId = "test-run",
        sequence = seq,
        text = text,
        kind = kind,
    )

    @Test
    fun testLogFiltersFilterEventsByKind() {
        runDesktopComposeUiTest(width = 1000, height = 800) {
            val scope = CoroutineScope(Dispatchers.Unconfined)
            val activityState = SetupActivityState(scope)
            activityState.appendEvents(
                listOf(
                    logEvent(1L, "Checking WSL distro", SetupLogKind.CHECK),
                    logEvent(2L, "Compiling gcc 13.2", SetupLogKind.RUN),
                    logEvent(3L, "Compilation error: missing header", SetupLogKind.ERROR),
                ),
            )

            setContent {
                LtiTheme {
                    SetupActivityPanel(
                        activityState = activityState,
                        defaultExpanded = true,
                    )
                }
            }

            // Initially All: all lines visible
            onNodeWithText("Checking WSL distro").assertIsDisplayed()
            onNodeWithText("Compiling gcc 13.2").assertIsDisplayed()
            onNodeWithText("Compilation error: missing header").assertIsDisplayed()

            // Filter to Checks
            onNodeWithText("Checks").performClick()
            onNodeWithText("Checking WSL distro").assertIsDisplayed()
            onNodeWithText("Compiling gcc 13.2").assertDoesNotExist()
            onNodeWithText("Compilation error: missing header").assertDoesNotExist()

            // Filter to Commands
            onNodeWithText("Commands").performClick()
            onNodeWithText("Compiling gcc 13.2").assertIsDisplayed()
            onNodeWithText("Checking WSL distro").assertDoesNotExist()
            onNodeWithText("Compilation error: missing header").assertDoesNotExist()

            // Filter to Errors
            onNodeWithText("Errors").performClick()
            onNodeWithText("Compilation error: missing header").assertIsDisplayed()
            onNodeWithText("Checking WSL distro").assertDoesNotExist()
            onNodeWithText("Compiling gcc 13.2").assertDoesNotExist()

            // Filter to All
            onNodeWithText("All").performClick()
            onNodeWithText("Checking WSL distro").assertIsDisplayed()
            onNodeWithText("Compiling gcc 13.2").assertIsDisplayed()
            onNodeWithText("Compilation error: missing header").assertIsDisplayed()
        }
    }

    @Test
    fun testCopyCopiesVisibleLines() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val activityState = SetupActivityState(scope)
        activityState.appendEvents(
            listOf(
                logEvent(1L, "Line 1", SetupLogKind.CHECK),
                logEvent(2L, "Line 2", SetupLogKind.RUN),
            ),
        )

        assertEquals("Line 1\nLine 2", activityState.copyAll())

        // Filter to CHECKS -> copyAll returns only Line 1
        activityState.setFilter(LogFilter.CHECKS)
        assertEquals("Line 1", activityState.copyAll())

        // Filter to COMMANDS -> copyAll returns only Line 2
        activityState.setFilter(LogFilter.COMMANDS)
        assertEquals("Line 2", activityState.copyAll())
    }

    @Test
    fun testTruncationDisclosureDisplayedWhenDroppedCountGreaterThanZero() {
        runDesktopComposeUiTest(width = 1000, height = 800) {
            val scope = CoroutineScope(Dispatchers.Unconfined)
            val activityState = SetupActivityState(scope, maxBufferSize = 2)
            activityState.appendEvents(
                listOf(
                    logEvent(1L, "Line 1"),
                    logEvent(2L, "Line 2"),
                    logEvent(3L, "Line 3"),
                ),
            )

            assertTrue(activityState.droppedLineCount.value > 0)

            setContent {
                LtiTheme {
                    SetupActivityPanel(
                        activityState = activityState,
                        defaultExpanded = true,
                    )
                }
            }

            onNodeWithText("Showing the last 2 lines — export for the full log").assertIsDisplayed()
        }
    }

    @Test
    fun testAutoScrollPauseAndJumpToLatest() {
        runDesktopComposeUiTest(width = 1000, height = 800) {
            val scope = CoroutineScope(Dispatchers.Unconfined)
            val activityState = SetupActivityState(scope)
            activityState.appendEvents(
                listOf(
                    logEvent(1L, "Line 1"),
                ),
            )

            setContent {
                LtiTheme {
                    SetupActivityPanel(
                        activityState = activityState,
                        defaultExpanded = true,
                    )
                }
            }

            // Initially following latest -> shows "Pause follow"
            onNodeWithText("Pause follow").assertIsDisplayed()
            onNodeWithText("Jump to latest").assertDoesNotExist()

            // Pause follow
            onNodeWithText("Pause follow").performClick()
            assertFalse(activityState.followingLatest.value)

            // Now shows "Jump to latest"
            onNodeWithText("Jump to latest").assertIsDisplayed()
            onNodeWithText("Pause follow").assertDoesNotExist()

            // Click "Jump to latest"
            onNodeWithText("Jump to latest").performClick()
            assertTrue(activityState.followingLatest.value)
            onNodeWithText("Pause follow").assertIsDisplayed()
        }
    }

    @Test
    fun testSetupScreenIconsOnlyMode() = runDesktopComposeUiTest(width = 1200, height = 800) {
        var selectedTab: SetupTab? = null

        setContent {
            LtiTheme {
                SetupScreenContent(
                    state = SetupUiState(
                        selectedTab = SetupTab.TOOLS,
                    ),
                    actions = SetupUiActions(
                        onSelectTab = { selectedTab = it },
                    ),
                    iconsOnly = true,
                )
            }
        }

        val railBounds = onNodeWithTag("IdePipelineRail").fetchSemanticsNode().boundsInRoot
        assertEquals(GlassDimens.CompactRailWidth.value, railBounds.width, 0.5f)

        onNodeWithTag("RailItem_projects").assertExists()
        onNodeWithTag("RailItem_environment").assertExists()
        onNodeWithTag("RailItem_tools").assertExists()
        onNodeWithTag("RailItem_recovery").assertExists()

        onNodeWithTag("NavigatorItem_all_tools").assertExists()
        onNodeWithTag("NavigatorItem_firmware").assertExists()
        onNodeWithTag("NavigatorItem_filesystems").assertExists()
        onNodeWithTag("NavigatorItem_signing").assertExists()

        onNodeWithTag("RailItem_environment").performClick()
        assertEquals(SetupTab.ENVIRONMENT, selectedTab)
    }
}
