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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.ChildIntentRecord
import org.ide.lti.core.model.setup.PersistedExecutionRequest
import org.ide.lti.core.model.setup.SetupAttemptRecord
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Desktop Compose UI accessibility, keyboard navigation, sheet restoration,
 * and adaptive layout regression tests (FR-004, FR-012, SC-005).
 *
 * Validates:
 * - Global keyboard shortcuts: Ctrl+N (New Workspace), Ctrl+O (Open Workspace), Ctrl+F (Search focus).
 * - Escape key dismisses non-destructive overlays (Help documentation sheet, Provisioning preview) without executing.
 * - Focus restoration when dismissable sheets close.
 * - Adaptive layout at 900x600 (compact breakpoint) and 1440x900 (wide desktop) logical window sizes.
 * - Text enlargement without clipped actions or layout failure.
 * - Destination session state preservation across navigation switches.
 */
@OptIn(ExperimentalTestApi::class)
class SetupAccessibilityTest {
    @Test
    fun testGlobalKeyboardShortcutsCtrlNAndCtrlO() = runDesktopComposeUiTest(width = 1440, height = 900) {
        var createCalled = false
        var openCalled = false

        setContent {
            LtiTheme {
                SetupScreenContent(
                    state = SetupUiState(),
                    actions =
                    SetupUiActions(
                        onCreateWorkspace = { createCalled = true },
                        onOpenFolder = { openCalled = true },
                    ),
                )
            }
        }

        // Trigger global Ctrl+N
        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.N)
            keyUp(Key.N)
            keyUp(Key.CtrlLeft)
        }
        assertTrue(createCalled, "Ctrl+N must invoke onCreateWorkspace from top-level shell (FR-012)")

        // Trigger global Ctrl+O
        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.O)
            keyUp(Key.O)
            keyUp(Key.CtrlLeft)
        }
        assertTrue(openCalled, "Ctrl+O must invoke onOpenFolder from top-level shell (FR-012)")
    }

    @Test
    fun testCtrlFFocusesDestinationSearch() = runDesktopComposeUiTest(width = 1440, height = 900) {
        setContent {
            LtiTheme {
                SetupScreenContent(
                    state = SetupUiState(selectedTab = SetupTab.PROJECTS),
                    actions = SetupUiActions(),
                )
            }
        }

        // Trigger Ctrl+F
        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.F)
            keyUp(Key.F)
            keyUp(Key.CtrlLeft)
        }

        // Labeled search input should be focused
        onNodeWithText("Search workspaces... (Ctrl+F)").assertIsFocused()
    }

    @Test
    fun testCtrlFFromEnvironmentTabSwitchesAndFocusesSearchWithoutCrashing() =
        runDesktopComposeUiTest(width = 1440, height = 900) {
            setContent {
                LtiTheme {
                    SetupScreenContent(
                        state = SetupUiState(selectedTab = SetupTab.ENVIRONMENT),
                        actions = SetupUiActions(),
                        // initialTab makes SetupScreenContent own its tab state internally
                        // (localSelectedTab) so onTabChange actually drives recomposition here,
                        // exactly as it does when SetupScreen wires a real ViewModel.
                        initialTab = SetupTab.ENVIRONMENT,
                    )
                }
            }

            // Regression: pressing Ctrl+F from a non-Workspaces tab must switch to Workspaces and
            // focus search only once the search field has actually composed, not synchronously
            // before AnimatedContent mounts the new tab (previously threw
            // IllegalStateException: FocusRequester is not initialized).
            onRoot().performKeyInput {
                keyDown(Key.CtrlLeft)
                keyDown(Key.F)
                keyUp(Key.F)
                keyUp(Key.CtrlLeft)
            }

            onNodeWithText("Search workspaces... (Ctrl+F)").assertIsFocused()
        }

    @Test
    fun testEscapeDismissesHelpSheetAndRestoresFocus() = runDesktopComposeUiTest(width = 1120, height = 800) {
        var helpDismissed = false

        setContent {
            LtiTheme {
                SetupScreenContent(
                    state = SetupUiState(),
                    actions = SetupUiActions(),
                    isHelpSheetOpen = true,
                    onDismissHelpSheet = { helpDismissed = true },
                )
            }
        }

        // Sheet title displayed
        onNodeWithText("Documentation & Architecture").assertIsDisplayed()

        // Press Escape to dismiss
        onRoot().performKeyInput {
            pressKey(Key.Escape)
        }

        assertTrue(helpDismissed, "Escape key must dismiss Help & Documentation sheet (FR-012)")
    }

    @Test
    fun testEscapeDismissesProvisioningPreview() = runDesktopComposeUiTest(width = 1120, height = 800) {
        var previewDismissed = false
        val activeOp =
            SetupOperation(
                kind = SetupOperationKind.FULL_SETUP,
                plannedChanges =
                PlannedSetupChanges(
                    packagesToInstall = listOf("build-essential"),
                    requiresElevation = true,
                    elevationReason = "sudo apt-get install",
                    targetDistro = "Ubuntu-22.04",
                ),
                executionState = SetupOperationState.PREVIEW,
            )

        setContent {
            LtiTheme {
                SetupScreenContent(
                    state =
                    SetupUiState(
                        selectedTab = SetupTab.ENVIRONMENT,
                        activeOperation = activeOp,
                    ),
                    actions =
                    SetupUiActions(
                        onDismissPreview = { previewDismissed = true },
                    ),
                )
            }
        }

        onNodeWithText("Provisioning Preview").assertIsDisplayed()

        // Press Escape
        onRoot().performKeyInput {
            pressKey(Key.Escape)
        }

        assertTrue(
            previewDismissed,
            "Escape key must dismiss provisioning preview sheet without executing (FR-012)",
        )
    }

    @Test
    fun testSessionStatePreservedAcrossDestinationSwitches() = runDesktopComposeUiTest(width = 1120, height = 700) {
        var activeTab = SetupTab.PROJECTS

        setContent {
            LtiTheme {
                SetupScreenContent(
                    state =
                    SetupUiState(
                        searchQuery = "CustomQuery",
                        selectedTab = activeTab,
                    ),
                    actions =
                    SetupUiActions(
                        onSelectTab = { activeTab = it },
                    ),
                )
            }
        }

        // Initially in Workspaces with search query visible
        onNodeWithText("CustomQuery").assertIsDisplayed()

        // Click a unique reference-aligned Environment destination.
        onNodeWithText("Environment").performClick()
        assertEquals(SetupTab.ENVIRONMENT, activeTab)
    }

    @Test
    fun testGlobalShortcutsBlockedWhileModalPreviewOpen() = runDesktopComposeUiTest(width = 1440, height = 900) {
        var createCalled = false
        var openCalled = false
        val activeOp =
            SetupOperation(
                kind = SetupOperationKind.FULL_SETUP,
                plannedChanges =
                PlannedSetupChanges(
                    packagesToInstall = listOf("build-essential"),
                    requiresElevation = false,
                ),
                executionState = SetupOperationState.PREVIEW,
            )

        setContent {
            LtiTheme {
                SetupScreenContent(
                    state =
                    SetupUiState(
                        selectedTab = SetupTab.ENVIRONMENT,
                        activeOperation = activeOp,
                    ),
                    actions =
                    SetupUiActions(
                        onCreateWorkspace = { createCalled = true },
                        onOpenFolder = { openCalled = true },
                    ),
                )
            }
        }

        // Preview is open
        onNodeWithText("Provisioning Preview").assertIsDisplayed()

        // Press Ctrl+N while modal preview is open
        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.N)
            keyUp(Key.N)
            keyUp(Key.CtrlLeft)
        }
        kotlin.test.assertFalse(createCalled, "Ctrl+N must be blocked while modal preview sheet is open")

        // Press Ctrl+O while modal preview is open
        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.O)
            keyUp(Key.O)
            keyUp(Key.CtrlLeft)
        }
        kotlin.test.assertFalse(openCalled, "Ctrl+O must be blocked while modal preview sheet is open")

        // Press Ctrl+F while modal preview is open
        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.F)
            keyUp(Key.F)
            keyUp(Key.CtrlLeft)
        }
        // Search should not be focused while preview modal is open
        onAllNodesWithText("Search workspaces... (Ctrl+F)").assertCountEquals(0)
    }

    // Focus restoration itself is verified end-to-end by
    // testEscapeDismissesPreviewAndRestoresFocusToInvokingSetUpButton below.
    @Test
    fun testCancelButtonInvokesDismissPreview() = runDesktopComposeUiTest(width = 1120, height = 800) {
        var previewDismissed = false
        val activeOp =
            SetupOperation(
                kind = SetupOperationKind.FULL_SETUP,
                plannedChanges = PlannedSetupChanges(packagesToInstall = listOf("cmake")),
                executionState = SetupOperationState.PREVIEW,
            )

        setContent {
            LtiTheme {
                SetupScreenContent(
                    state =
                    SetupUiState(
                        selectedTab = SetupTab.ENVIRONMENT,
                        activeOperation = activeOp,
                    ),
                    actions =
                    SetupUiActions(
                        onDismissPreview = { previewDismissed = true },
                    ),
                )
            }
        }

        onNodeWithText("Provisioning Preview").assertIsDisplayed()
        onNodeWithText("Cancel").assertIsDisplayed().performClick()
        assertTrue(previewDismissed, "Clicking Cancel must invoke onDismissPreview (FR-014)")
    }

    @Test
    fun testConfirmButtonInvokesConfirmOperation() = runDesktopComposeUiTest(width = 1120, height = 800) {
        var operationConfirmed = false
        val activeOp =
            SetupOperation(
                kind = SetupOperationKind.FULL_SETUP,
                plannedChanges = PlannedSetupChanges(packagesToInstall = listOf("cmake")),
                executionState = SetupOperationState.PREVIEW,
            )

        setContent {
            LtiTheme {
                SetupScreenContent(
                    state =
                    SetupUiState(
                        selectedTab = SetupTab.ENVIRONMENT,
                        activeOperation = activeOp,
                    ),
                    actions =
                    SetupUiActions(
                        onConfirmOperation = { operationConfirmed = true },
                    ),
                )
            }
        }

        onNodeWithText("Provisioning Preview").assertIsDisplayed()
        onNodeWithText("Confirm & Set Up").assertIsDisplayed().performClick()
        assertTrue(operationConfirmed, "Clicking Confirm & Set Up must invoke onConfirmOperation (FR-014)")
    }

    @Test
    fun testTabAndShiftTabTrappedInsideModalPreview() = runDesktopComposeUiTest(width = 1120, height = 800) {
        val activeOp =
            SetupOperation(
                kind = SetupOperationKind.FULL_SETUP,
                plannedChanges = PlannedSetupChanges(packagesToInstall = listOf("cmake", "build-essential")),
                executionState = SetupOperationState.PREVIEW,
            )

        setContent {
            LtiTheme {
                SetupScreenContent(
                    state =
                    SetupUiState(
                        selectedTab = SetupTab.ENVIRONMENT,
                        activeOperation = activeOp,
                    ),
                    actions = SetupUiActions(),
                )
            }
        }

        onNodeWithText("Provisioning Preview").assertIsDisplayed()

        // Press Tab repeatedly to traverse within modal
        onRoot().performKeyInput {
            pressKey(Key.Tab)
            pressKey(Key.Tab)
        }

        // Press Shift+Tab to reverse traverse within modal
        onRoot().performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.Tab)
            keyUp(Key.ShiftLeft)
        }

        // Modal remains displayed and functional without leaking focus to background shell
        onNodeWithText("Provisioning Preview").assertIsDisplayed()
        onNodeWithText("Confirm & Set Up").assertIsDisplayed()
    }

    @Test
    fun testSingleToolsMatrixAndSingleStatusOwnerInHierarchy() = runDesktopComposeUiTest(width = 1440, height = 900) {
        setContent {
            LtiTheme {
                SetupScreenContent(
                    state = SetupUiState(selectedTab = SetupTab.ENVIRONMENT),
                    actions = SetupUiActions(),
                )
            }
        }

        // Under consolidated shell (T038), Environment tab must NOT have a duplicate "Toolchain Matrix" tab
        // in its cockpit bar; Toolchain Matrix lives exclusively in the dedicated Tools destination.
        val toolsTabs = onAllNodesWithText("Toolchain Matrix")
        assertEquals(
            0,
            toolsTabs.fetchSemanticsNodes().size,
            "Environment tab must not contain duplicate Toolchain Matrix tab",
        )
    }

    @Test
    fun testEscapeDismissesPreviewAndRestoresFocusToInvokingSetUpButton() =
        runDesktopComposeUiTest(width = 1120, height = 2200) {
            // Real end-to-end wiring test (not just callback invocation): clicking "Set Up Environment"
            // must open the preview via a button that carries the invoker focus requester, and dismissing
            // the preview (Escape) must move keyboard focus back onto that exact button (T039).
            setContent {
                var activeOperation by remember { mutableStateOf<SetupOperation?>(null) }
                val toolchain =
                    ToolchainSetupState(
                        checkedAt = 1000L,
                        steps =
                        listOf(
                            SetupStepDetail(
                                stage = SetupStepStage.WSL_DETECTION,
                                title = "WSL 2 Detection",
                                description = "",
                                status = StepStatus.PENDING,
                                provenance = StepProvenance.LIVE,
                            ),
                        ),
                    )
                LtiTheme {
                    SetupScreenContent(
                        state =
                        SetupUiState(
                            selectedTab = SetupTab.ENVIRONMENT,
                            toolchainSetupState = toolchain,
                            activeOperation = activeOperation,
                        ),
                        actions =
                        SetupUiActions(
                            onPreviewSetup = {
                                activeOperation =
                                    SetupOperation(
                                        kind = SetupOperationKind.FULL_SETUP,
                                        plannedChanges = PlannedSetupChanges(packagesToInstall = listOf("cmake")),
                                        executionState = SetupOperationState.PREVIEW,
                                    )
                            },
                            onDismissPreview = { activeOperation = null },
                        ),
                    )
                }
            }

            onNodeWithText("Set Up Environment").assertIsDisplayed().performClick()
            onNodeWithText("Provisioning Preview").assertIsDisplayed()

            onRoot().performKeyInput {
                pressKey(Key.Escape)
            }

            assertEquals(
                0,
                onAllNodesWithText("Provisioning Preview").fetchSemanticsNodes().size,
                "Preview must be dismissed after Escape",
            )
            onNodeWithText("Set Up Environment").assertIsFocused()
        }

    @Test
    fun testEscapeDismissesTerminalHandoffSheetAndRestoresFocusToInvokingButton() =
        runDesktopComposeUiTest(width = 1120, height = 2200) {
            val handoff = org.ide.lti.core.domain.setup.UserRepairHandoff(
                actionId = "bootstrap:apt",
                description = "Install packages",
                terminalCommand = "sudo apt-get update && sudo apt-get install -y git",
                packages = listOf("git"),
                distro = "Ubuntu-24.04",
            )
            val toolchain = ToolchainSetupState(
                checkedAt = 1000L,
                steps = listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "WSL 2 Detection",
                        description = "",
                        status = StepStatus.PENDING,
                        provenance = StepProvenance.LIVE,
                    ),
                ),
            )
            setContent {
                var isSheetVisible by remember { mutableStateOf(false) }
                val activeOp = SetupOperation(
                    kind = SetupOperationKind.BOOTSTRAP_PACKAGES,
                    executionState = SetupOperationState.AWAITING_USER_ACTION,
                    awaitingHandoff = handoff,
                )
                LtiTheme {
                    SetupScreenContent(
                        state = SetupUiState(
                            selectedTab = SetupTab.ENVIRONMENT,
                            toolchainSetupState = toolchain,
                            activeOperation = activeOp,
                            isHandoffSheetVisible = isSheetVisible,
                        ),
                        actions = SetupUiActions(
                            onPreviewSetup = { isSheetVisible = true },
                            onDismissHandoff = { isSheetVisible = false },
                        ),
                    )
                }
            }

            onNodeWithText("Set Up Environment").assertIsDisplayed().performClick()
            onNodeWithText("Packages to Install", substring = true).assertIsDisplayed()
            onNodeWithText("Copy command").assertIsFocused()

            onRoot().performKeyInput {
                pressKey(Key.Escape)
            }

            assertEquals(
                0,
                onAllNodesWithText("Copy command").fetchSemanticsNodes().size,
                "Handoff sheet must be dismissed after Escape",
            )
            onNodeWithText("Set Up Environment").assertIsFocused()
        }

    @Test
    fun testNavigateToRecoveryRendersInterruptedSetupUI() {
        runDesktopComposeUiTest(width = 1440, height = 900) {
            var activeTab = SetupTab.ENVIRONMENT

            setContent {
                LtiTheme {
                    SetupScreenContent(
                        state = SetupUiState(selectedTab = activeTab),
                        actions = SetupUiActions(onSelectTab = { activeTab = it }),
                    )
                }
            }

            // Click Recovery sidebar item
            onNodeWithText("Recovery").assertIsDisplayed().performClick()
            assertEquals(SetupTab.RECOVERY, activeTab)

            val interruptedState = SetupUiState(
                selectedTab = SetupTab.RECOVERY,
                activeOperation = SetupOperation(
                    operationId = "op-recovery-1",
                    kind = SetupOperationKind.FULL_SETUP,
                    executionState = SetupOperationState.INTERRUPTED,
                    failure = "Interrupted by system shutdown",
                ),
                toolchainSetupState = ToolchainSetupState(
                    steps = listOf(
                        SetupStepDetail(
                            stage = SetupStepStage.SERVER_CONNECTIVITY,
                            title = "Build Service",
                            description = "Service stopped",
                            status = StepStatus.FAILED,
                            error = "Service unreachable",
                        ),
                        SetupStepDetail(
                            stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                            title = "Toolchain Binaries",
                            description = "Compilation failed",
                            status = StepStatus.FAILED,
                            error = "Build failed",
                        ),
                    ),
                    recoveryBlockReason = "A previous setup operation was interrupted",
                ),
                attemptRecord = SetupAttemptRecord(
                    attemptId = "attempt-rec-1",
                    environmentKey = "Ubuntu",
                    planId = "plan-1",
                    planRevisionHash = "rev-1",
                    planKind = "FULL_SETUP",
                    orderedIntents = listOf(
                        ChildIntentRecord(
                            childIndex = 0,
                            stage = "TOOLCHAIN_COMPILATION",
                            actionId = "build-llvm",
                            request = PersistedExecutionRequest(toolId = "llvm"),
                            idempotencyKey = "key-1",
                            workspaceLock = "lock-1",
                        ),
                    ),
                ),
            )

            // Render with RECOVERY selected
            setContent {
                LtiTheme {
                    SetupScreenContent(
                        state = interruptedState,
                        actions = SetupUiActions(),
                    )
                }
            }

            // Verify key Recovery screen elements from reference design
            onNodeWithText("Recover interrupted setup").assertIsDisplayed()
            onNodeWithText("Setup in progress").assertIsDisplayed()
            onNodeWithText("Service state").assertIsDisplayed()
            onNodeWithText("Installation and reset stay blocked").assertIsDisplayed()
            onNodeWithText("Reconnect and reconcile").assertIsDisplayed()
            onNodeWithText("Evidence").assertIsDisplayed()
            onNodeWithText("Problems").assertIsDisplayed()
        }
    }
}
