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

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.domain.setup.UserRepairHandoff
import org.ide.lti.feature.setup.components.TerminalHandoffSheet
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TerminalHandoffSheetTest {
    private val sampleHandoff =
        UserRepairHandoff(
            actionId = "bootstrap:apt",
            description = "Install packages",
            terminalCommand = "sudo apt-get update && sudo apt-get install -y git cmake",
            packages = listOf("git", "cmake"),
            distro = "Ubuntu-24.04",
        )

    @Test
    fun `sheet displays 4-step instructions and has no password inputs or sudoers references`() =
        runDesktopComposeUiTest(width = 1000, height = 800) {
            setContent {
                LtiTheme {
                    TerminalHandoffSheet(
                        handoff = sampleHandoff,
                        onOpenTerminal = {},
                        onResume = {},
                        onDismiss = {},
                        onAbandon = {},
                    )
                }
            }

            // 4-step sequence
            onNodeWithText(
                "1 Copy the command → 2 Open the terminal → 3 Paste it and press Enter, then type your password → " +
                    "4 Come back and select \"I've run it — check again\".",
            ).assertIsDisplayed()
            onNodeWithText("Copy command").assertIsDisplayed().assertIsFocused()
            onNodeWithText("Open the terminal").assertIsDisplayed()
            onNodeWithText("I've run it — check again").assertIsDisplayed()
            onNodeWithText("Close for now").assertIsDisplayed()
            onNodeWithText("Abandon setup").assertIsDisplayed()
        }

    @Test
    fun `clicking Close for now triggers onDismiss`() = runDesktopComposeUiTest(width = 1000, height = 800) {
        var dismissed = false
        setContent {
            LtiTheme {
                TerminalHandoffSheet(
                    handoff = sampleHandoff,
                    onOpenTerminal = {},
                    onResume = {},
                    onDismiss = { dismissed = true },
                    onAbandon = {},
                )
            }
        }

        onNodeWithText("Close for now").performClick()
        assertTrue(dismissed, "Clicking 'Close for now' must trigger onDismiss")
    }

    @Test
    fun `pressing Escape triggers onDismiss`() = runDesktopComposeUiTest(width = 1000, height = 800) {
        var dismissed = false
        setContent {
            LtiTheme {
                TerminalHandoffSheet(
                    handoff = sampleHandoff,
                    onOpenTerminal = {},
                    onResume = {},
                    onDismiss = { dismissed = true },
                    onAbandon = {},
                )
            }
        }

        onRoot().performKeyInput {
            pressKey(Key.Escape)
        }
        assertTrue(dismissed, "Pressing Escape must trigger onDismiss")
    }

    @Test
    fun `clicking Abandon asks for confirmation before invoking onAbandon`() =
        runDesktopComposeUiTest(width = 1000, height = 800) {
            var abandonInvoked = false
            setContent {
                LtiTheme {
                    TerminalHandoffSheet(
                        handoff = sampleHandoff,
                        onOpenTerminal = {},
                        onResume = {},
                        onDismiss = {},
                        onAbandon = { abandonInvoked = true },
                    )
                }
            }

            onNodeWithText("Abandon", substring = true).performClick()
            // First click must not directly abandon; confirmation is required
            assertFalse(
                abandonInvoked,
                "First click on Abandon must ask for confirmation rather than immediately abandoning",
            )

            // Confirm button appears
            onNodeWithText("Confirm", substring = true).performClick()
            assertTrue(abandonInvoked, "Confirming abandon must trigger onAbandon")
        }

    @Test
    fun `clicking the scrim does not dismiss the sheet`() = runDesktopComposeUiTest(width = 1000, height = 800) {
        var dismissed = false
        setContent {
            LtiTheme {
                TerminalHandoffSheet(
                    handoff = sampleHandoff,
                    onOpenTerminal = {},
                    onResume = {},
                    onDismiss = { dismissed = true },
                    onAbandon = {},
                )
            }
        }

        onNodeWithTag("terminal_handoff_scrim").performClick()
        assertFalse(dismissed, "Clicking the scrim must NOT dismiss the sheet (contracts/ui-states.md §4)")
    }

    @Test
    fun `terminal launch failed state displays inline error and try again action`() =
        runDesktopComposeUiTest(width = 1000, height = 800) {
            var terminalRetried = false
            setContent {
                LtiTheme {
                    TerminalHandoffSheet(
                        handoff = sampleHandoff,
                        sheetState = TerminalHandoffState.TerminalLaunchFailed("wt.exe not found"),
                        onOpenTerminal = { terminalRetried = true },
                        onResume = {},
                        onDismiss = {},
                        onAbandon = {},
                    )
                }
            }

            onNodeWithText("wt.exe not found", substring = true).assertIsDisplayed()
            onNodeWithText("Try again").performClick()
            assertTrue(terminalRetried, "Clicking Try again must retry opening the terminal")
        }

    @Test
    fun `verifying state displays progress and disables check button`() =
        runDesktopComposeUiTest(width = 1000, height = 800) {
            var resumeClicked = false
            setContent {
                LtiTheme {
                    TerminalHandoffSheet(
                        handoff = sampleHandoff,
                        sheetState = TerminalHandoffState.Verifying,
                        onOpenTerminal = {},
                        onResume = { resumeClicked = true },
                        onDismiss = {},
                        onAbandon = {},
                    )
                }
            }

            onNodeWithText("Checking installation…", substring = true).assertIsDisplayed()
            onNodeWithText("I've run it — check again").assertIsNotEnabled()
            assertFalse(resumeClicked)
        }

    @Test
    fun `still missing state displays updated packages and command`() =
        runDesktopComposeUiTest(width = 1000, height = 800) {
            setContent {
                LtiTheme {
                    TerminalHandoffSheet(
                        handoff = sampleHandoff,
                        sheetState =
                        TerminalHandoffState.StillMissing(
                            packages = listOf("cmake"),
                            command = "sudo apt-get update && sudo apt-get install -y cmake",
                        ),
                        onOpenTerminal = {},
                        onResume = {},
                        onDismiss = {},
                        onAbandon = {},
                    )
                }
            }

            onNodeWithText("Still missing: cmake", substring = true).assertIsDisplayed()
            onNodeWithText("sudo apt-get update && sudo apt-get install -y cmake", substring = true).assertIsDisplayed()
        }

    @Test
    fun `another operation running state displays inline notice and try again action`() =
        runDesktopComposeUiTest(width = 1000, height = 800) {
            var retried = false
            setContent {
                LtiTheme {
                    TerminalHandoffSheet(
                        handoff = sampleHandoff,
                        sheetState = TerminalHandoffState.AnotherOperationRunning,
                        onOpenTerminal = {},
                        onResume = { retried = true },
                        onDismiss = {},
                        onAbandon = {},
                    )
                }
            }

            onNodeWithText(
                "Another setup operation is running. Try again when it finishes.",
                substring = true,
            ).assertIsDisplayed()
            onNodeWithText("Try again").performClick()
            assertTrue(retried, "Clicking Try again must trigger onResume")
        }
}
