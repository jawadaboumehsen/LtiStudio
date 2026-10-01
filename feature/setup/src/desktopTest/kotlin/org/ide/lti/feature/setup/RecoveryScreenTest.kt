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
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.AttemptStatus
import org.ide.lti.core.model.setup.ChildIntentRecord
import org.ide.lti.core.model.setup.PersistedExecutionRequest
import org.ide.lti.core.model.setup.SetupAttemptRecord
import org.ide.lti.feature.setup.steps.RecoveryStepContent
import org.junit.Test
import kotlin.test.assertTrue

/**
 * T069: Verification tests for truthful Recovery screen (US5, contracts/ui-states.md §8):
 * - idle -> only "Nothing to recover";
 * - interrupted attempt -> operation card shows journal's kind, ID, state, failure, and issued time;
 * - service stopped -> service card uses Build service row state;
 * - no sample strings ("Install pinned tool packages", "10:14:32", "Service connection is lost") in any state;
 * - Evidence/Problems tabs hidden when empty and showing real data otherwise;
 * - no "View operation evidence".
 */
@OptIn(ExperimentalTestApi::class)
class RecoveryScreenTest {

    @Test
    fun testIdleStateShowsOnlyNothingToRecover() {
        runDesktopComposeUiTest(width = 1120, height = 1200) {
            val state = SetupUiState(
                toolchainSetupState = ToolchainSetupState(
                    steps = emptyList(),
                    diagnostics = emptyList(),
                ),
            )

            setContent {
                LtiTheme {
                    RecoveryStepContent(
                        state = state,
                        actions = SetupUiActions(),
                    )
                }
            }

            onNodeWithText("Nothing to recover").assertIsDisplayed()
            onNodeWithText(
                "No interrupted setup or service failure was found on this machine.",
            ).assertIsDisplayed()

            onAllNodesWithText("Setup in progress").assertCountEquals(0)
            onAllNodesWithText("Service state").assertCountEquals(0)
            onAllNodesWithText("Problems").assertCountEquals(0)
            onAllNodesWithText("Evidence").assertCountEquals(0)
            onAllNodesWithText("Installation and reset stay blocked").assertCountEquals(0)

            assertForbiddenSampleStringsAbsent()
        }
    }

    @Test
    fun testInterruptedAttemptShowsOperationCardDetails() {
        runDesktopComposeUiTest(width = 1120, height = 1200) {
            val record = SetupAttemptRecord(
                attemptId = "attempt-rec-987",
                environmentKey = "Ubuntu",
                planId = "plan-recovery",
                planRevisionHash = "rev-xyz",
                planKind = "FULL_SETUP",
                status = AttemptStatus.INTERRUPTED,
                terminalReason = "Build daemon closed unexpectedly",
                createdAtEpochMs = 1711620000000L,
            )
            val state = SetupUiState(
                attemptRecord = record,
                toolchainSetupState = ToolchainSetupState(
                    steps = emptyList(),
                    diagnostics = emptyList(),
                ),
            )

            setContent {
                LtiTheme {
                    RecoveryStepContent(
                        state = state,
                        actions = SetupUiActions(),
                    )
                }
            }

            onNodeWithText("Setup in progress").assertIsDisplayed()
            onNodeWithText("Full setup").assertIsDisplayed()
            onNodeWithText("attempt-rec-987").assertIsDisplayed()
            onNodeWithText("Interrupted").assertIsDisplayed()
            onNodeWithText("Build daemon closed unexpectedly").assertIsDisplayed()

            onAllNodesWithText("Nothing to recover").assertCountEquals(0)
            assertForbiddenSampleStringsAbsent()
        }
    }

    @Test
    fun testServiceStoppedShowsBuildServiceStateAndReconnect() {
        runDesktopComposeUiTest(width = 1120, height = 1200) {
            var reconnected = false
            val serverStep = SetupStepDetail(
                stage = SetupStepStage.SERVER_CONNECTIVITY,
                title = "Build Service",
                description = "Build service didn't start: port collision",
                status = StepStatus.FAILED,
                error = "port collision",
            )
            val state = SetupUiState(
                toolchainSetupState = ToolchainSetupState(
                    steps = listOf(serverStep),
                    diagnostics = emptyList(),
                ),
            )

            setContent {
                LtiTheme {
                    RecoveryStepContent(
                        state = state,
                        actions = SetupUiActions(
                            onReconnect = { reconnected = true },
                        ),
                    )
                }
            }

            onNodeWithText("Service state").assertIsDisplayed()
            onNodeWithText("Build service didn't start: port collision").assertIsDisplayed()
            onNodeWithText("Reconnect and reconcile").assertIsDisplayed().performClick()

            assertTrue(reconnected, "Clicking Reconnect and reconcile must invoke onReconnect")
            assertForbiddenSampleStringsAbsent()
        }
    }

    @Test
    fun testEvidenceTabHiddenWhenEmptyAndShowsRealDataWhenPresent() {
        runDesktopComposeUiTest(width = 1120, height = 1200) {
            val emptyRecord = SetupAttemptRecord(
                attemptId = "attempt-empty",
                environmentKey = "Ubuntu",
                planId = "plan-1",
                planRevisionHash = "rev-1",
                planKind = "FULL_SETUP",
                status = AttemptStatus.RUNNING,
            )
            val stateWithoutEvidence = SetupUiState(
                attemptRecord = emptyRecord,
                toolchainSetupState = ToolchainSetupState(
                    steps = emptyList(),
                    diagnostics = emptyList(),
                ),
            )

            setContent {
                LtiTheme {
                    RecoveryStepContent(
                        state = stateWithoutEvidence,
                        actions = SetupUiActions(),
                    )
                }
            }

            onAllNodesWithText("Evidence").assertCountEquals(0)

            val childIntent = ChildIntentRecord(
                childIndex = 0,
                stage = "TOOLCHAIN_COMPILATION",
                actionId = "build-llvm",
                request = PersistedExecutionRequest(toolId = "llvm"),
                idempotencyKey = "key-idem-llvm",
                workspaceLock = "lock-llvm",
            )
            val recordWithEvidence = SetupAttemptRecord(
                attemptId = "attempt-with-evidence",
                environmentKey = "Ubuntu",
                planId = "plan-2",
                planRevisionHash = "rev-2",
                planKind = "FULL_SETUP",
                status = AttemptStatus.INTERRUPTED,
                orderedIntents = listOf(childIntent),
            )
            val stateWithEvidence = SetupUiState(
                attemptRecord = recordWithEvidence,
                toolchainSetupState = ToolchainSetupState(
                    steps = emptyList(),
                    diagnostics = emptyList(),
                ),
            )

            setContent {
                LtiTheme {
                    RecoveryStepContent(
                        state = stateWithEvidence,
                        actions = SetupUiActions(),
                    )
                }
            }

            onNodeWithTag("EvidenceTab").assertIsDisplayed().performClick()
            onNodeWithText("Child intents (1)").assertIsDisplayed()
            onNodeWithText("TOOLCHAIN_COMPILATION · build-llvm").assertIsDisplayed()
            onNodeWithText("Idempotency: key-idem-llvm").assertIsDisplayed()

            assertForbiddenSampleStringsAbsent()
        }
    }

    @Test
    fun testProblemsTabHiddenWhenEmptyAndShowsRealDataWhenPresent() {
        runDesktopComposeUiTest(width = 1120, height = 1200) {
            val cleanStep = SetupStepDetail(
                stage = SetupStepStage.WSL_DETECTION,
                title = "WSL Detection",
                description = "Ubuntu 22.04 LTS",
                status = StepStatus.SUCCESS,
            )
            val stateWithoutProblems = SetupUiState(
                toolchainSetupState = ToolchainSetupState(
                    steps = listOf(cleanStep),
                    diagnostics = emptyList(),
                ),
            )

            setContent {
                LtiTheme {
                    RecoveryStepContent(
                        state = stateWithoutProblems,
                        actions = SetupUiActions(),
                    )
                }
            }

            onAllNodesWithText("Problems").assertCountEquals(0)

            val failedStep = SetupStepDetail(
                stage = SetupStepStage.SYSTEM_PACKAGES,
                title = "System Packages",
                description = "Missing packages",
                status = StepStatus.FAILED,
                error = "Missing packages: build-essential, cmake",
            )
            val stateWithProblems = SetupUiState(
                toolchainSetupState = ToolchainSetupState(
                    steps = listOf(failedStep),
                    diagnostics = emptyList(),
                ),
            )

            setContent {
                LtiTheme {
                    RecoveryStepContent(
                        state = stateWithProblems,
                        actions = SetupUiActions(),
                    )
                }
            }

            onNodeWithTag("ProblemsTab").assertIsDisplayed().performClick()
            onNodeWithText("System Packages").assertIsDisplayed()
            onNodeWithText("Missing packages: build-essential, cmake").assertIsDisplayed()

            assertForbiddenSampleStringsAbsent()
        }
    }

    @Test
    fun testBlockedBannerDisplaysRecoveryBlockReason() {
        runDesktopComposeUiTest(width = 1120, height = 1200) {
            val state = SetupUiState(
                toolchainSetupState = ToolchainSetupState(
                    steps = emptyList(),
                    diagnostics = emptyList(),
                    recoveryBlockReason = "A prior installation was interrupted. Recovery is required.",
                ),
            )

            setContent {
                LtiTheme {
                    RecoveryStepContent(
                        state = state,
                        actions = SetupUiActions(),
                    )
                }
            }

            onNodeWithText("Installation and reset stay blocked").assertIsDisplayed()
            onNodeWithText(
                "A prior installation was interrupted. Recovery is required.",
            ).assertIsDisplayed()

            assertForbiddenSampleStringsAbsent()
        }
    }

    private fun androidx.compose.ui.test.DesktopComposeUiTest.assertForbiddenSampleStringsAbsent() {
        onAllNodesWithText("Install pinned tool packages").assertCountEquals(0)
        onAllNodesWithText("10:14:32").assertCountEquals(0)
        onAllNodesWithText("Service connection is lost").assertCountEquals(0)
        onAllNodesWithText("View operation evidence").assertCountEquals(0)
    }
}
