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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolCategory
import org.ide.lti.core.domain.setup.ToolComponentItem
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.feature.setup.steps.EnvironmentStepContent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Desktop Compose UI tests for Environment Setup Cockpit (US2 — Deliberate environment setup).
 *
 * Fully DI-free fixtures validating:
 * - Stage failure: expanded failed row displays error box with targeted retry button (FR-006, FR-007).
 * - Offline state: primary action is 'Retry Connection' with cached projects accessible (FR-007).
 * - Restored provenance: label 'historical' shown alongside previous verification timestamp (FR-004).
 * - Provisioning preview sheet: displays planned changes, elevation requirements, auto doctor toggle (FR-005).
 * - Ready state: primary action is 'Open Workspaces' (FR-002, FR-007).
 */
@OptIn(ExperimentalTestApi::class)
class EnvironmentScreenTest {

    @Test
    fun testStageFailureDisplaysErrorBoxWithTargetedRetryButton() =
        runDesktopComposeUiTest(width = 1120, height = 1800) {
            val errorMessage = "Git submodule sync failed: connection timed out"
            val failedStage = SetupStepDetail(
                stage = SetupStepStage.REPO_SYNCHRONIZATION,
                title = "Repository Synchronization",
                description = "Synchronizing Git submodules",
                status = StepStatus.FAILED,
                error = errorMessage,
            )
            val state = ToolchainSetupState(
                steps = listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "WSL Detection",
                        description = "",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.SERVER_CONNECTIVITY,
                        title = "Server Daemon Bridge",
                        description = "",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.SYSTEM_DIAGNOSTICS,
                        title = "System Diagnostics",
                        description = "",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    failedStage,
                    SetupStepDetail(
                        stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                        title = "Toolchain Compilation",
                        description = "",
                        status = StepStatus.PENDING,
                    ),
                ),
                lastVerifiedTimestamp = 1700000000000L,
            )

            var retriedStage: SetupStepStage? = null

            setContent {
                LtiTheme {
                    EnvironmentStepContent(
                        state = SetupUiState(toolchainSetupState = state),
                        actions = SetupUiActions(
                            onRetryStage = { retriedStage = it },
                        ),
                    )
                }
            }

            // Error message must be displayed in the expanded error box
            onNodeWithText(errorMessage).assertIsDisplayed()

            // Targeted "Retry Stage" button must be displayed and functional
            onNodeWithText("Retry Stage").assertIsDisplayed().performClick()
            assertEquals(SetupStepStage.REPO_SYNCHRONIZATION, retriedStage)
        }

    @Test
    fun testOfflineStateShowsRetryConnectionAndCachedWorkspacesUsable() =
        runDesktopComposeUiTest(width = 1120, height = 1800) {
            val offlineStep = SetupStepDetail(
                stage = SetupStepStage.SERVER_CONNECTIVITY,
                title = "Server Daemon Bridge",
                description = "Daemon unreachable on port 50051",
                status = StepStatus.FAILED,
            )
            val state = ToolchainSetupState(
                steps = listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "WSL Detection",
                        description = "",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    offlineStep,
                ),
                lastVerifiedTimestamp = 1700000000000L,
            )

            var retriedStage: SetupStepStage? = null
            var navigatedToProjects = false

            setContent {
                LtiTheme {
                    EnvironmentStepContent(
                        state = SetupUiState(toolchainSetupState = state),
                        actions = SetupUiActions(
                            onRetryStage = { retriedStage = it },
                        ),
                        onNavigateToProjects = { navigatedToProjects = true },
                    )
                }
            }

            // Primary action must be "Retry Connection"
            onNodeWithText("Retry Connection").assertIsDisplayed().performClick()
            assertEquals(SetupStepStage.SERVER_CONNECTIVITY, retriedStage)

            // Cached Workspaces must be accessible via "Back to Projects"
            onNodeWithText("Back to Projects").assertIsDisplayed().performClick()
            assertTrue(navigatedToProjects, "User must be able to return to projects while offline")
        }

    @Test
    fun testRestoredProvenanceShowsHistoricalLabel() = runDesktopComposeUiTest(width = 1120, height = 1800) {
        val restoredStep = SetupStepDetail(
            stage = SetupStepStage.WSL_DETECTION,
            title = "WSL Detection",
            description = "Ubuntu 22.04 LTS",
            status = StepStatus.SUCCESS,
            provenance = StepProvenance.RESTORED,
        )
        val state = ToolchainSetupState(
            steps = listOf(restoredStep),
            lastVerifiedTimestamp = 1700000000000L,
        )

        setContent {
            LtiTheme {
                EnvironmentStepContent(
                    state = SetupUiState(toolchainSetupState = state),
                    actions = SetupUiActions(),
                )
            }
        }

        // Historical label / chip must be displayed for restored provenance
        onNodeWithText("Historical").assertIsDisplayed()
    }

    @Test
    fun testProvisioningPreviewSheetDisplaysPlannedChangesAndElevation() =
        runDesktopComposeUiTest(width = 1120, height = 1800) {
            val planned = PlannedSetupChanges(
                packagesToInstall = listOf("build-essential", "cmake", "libssl-dev"),
                submodulesToSync = listOf("core/external/boringssl"),
                toolsToCompile = listOf("avbtool", "fastboot"),
                requiresElevation = true,
                elevationReason = "sudo apt-get install build-essential cmake libssl-dev",
                targetDistro = "Ubuntu-22.04",
            )
            val activeOp = SetupOperation(
                kind = SetupOperationKind.FULL_SETUP,
                plannedChanges = planned,
                executionState = SetupOperationState.PREVIEW,
                autoDoctorEnabled = true,
            )

            var confirmed = false
            var dismissed = false
            var autoDoctorToggled = false

            setContent {
                LtiTheme {
                    SetupScreenContent(
                        state = SetupUiState(
                            toolchainSetupState = ToolchainSetupState(lastVerifiedTimestamp = 1700000000000L),
                            activeOperation = activeOp,
                            isAutoDoctorEnabled = true,
                            selectedTab = SetupTab.ENVIRONMENT,
                        ),
                        actions = SetupUiActions(
                            onConfirmOperation = { confirmed = true },
                            onDismissPreview = { dismissed = true },
                            onSetAutoDoctorEnabled = { autoDoctorToggled = it },
                        ),
                    )
                }
            }

            // Sheet title
            onNodeWithText("Provisioning Preview").assertIsDisplayed()

            // Elevation banner
            onNodeWithText("Root / Sudo Elevation Required").assertIsDisplayed()

            // Planned packages, submodules, and tools
            onNodeWithText("build-essential").assertIsDisplayed()
            onNodeWithText("core/external/boringssl").assertIsDisplayed()
            onNodeWithText("avbtool").assertIsDisplayed()

            // Auto Doctor toggle option
            onNodeWithText("Automated Doctor Remediation").assertIsDisplayed()

            // Confirm & Set Up button
            onNodeWithText("Confirm & Set Up").assertIsDisplayed().performClick()
            assertTrue(confirmed, "Clicking Confirm & Set Up must invoke onConfirmOperation")
        }

    @Test
    fun testReadyStateShowsOpenWorkspacesPrimaryAction() = runDesktopComposeUiTest(width = 1120, height = 1800) {
        val readySteps = listOf(
            SetupStepDetail(
                stage = SetupStepStage.WSL_DETECTION,
                title = "WSL",
                description = "",
                status = StepStatus.SUCCESS,
                provenance = StepProvenance.LIVE,
            ),
            SetupStepDetail(
                stage = SetupStepStage.SERVER_CONNECTIVITY,
                title = "Server",
                description = "",
                status = StepStatus.SUCCESS,
                provenance = StepProvenance.LIVE,
            ),
            SetupStepDetail(
                stage = SetupStepStage.SYSTEM_DIAGNOSTICS,
                title = "Doctor",
                description = "",
                status = StepStatus.SUCCESS,
                provenance = StepProvenance.LIVE,
            ),
            SetupStepDetail(
                stage = SetupStepStage.REPO_SYNCHRONIZATION,
                title = "Repos",
                description = "",
                status = StepStatus.SUCCESS,
                provenance = StepProvenance.LIVE,
            ),
            SetupStepDetail(
                stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                title = "Build",
                description = "",
                status = StepStatus.SUCCESS,
                provenance = StepProvenance.LIVE,
            ),
        )
        val readyTools = listOf(
            ToolComponentItem(
                id = "lpunpack",
                name = "LP Unpack",
                category = ToolCategory.DYNAMIC_PARTITIONS,
                binaryName = "lpunpack",
                status = StepStatus.SUCCESS,
                lastTested = "2026-09-13",
                isCore = true,
            ),
        )
        val state = ToolchainSetupState(
            steps = readySteps,
            toolsMatrix = readyTools,
            publishedToolIds = setOf("lpunpack"),
            checkedAt = 1700000000000L,
            lastReadyAt = 1700000000000L,
            lastVerifiedTimestamp = 1700000000000L,
        )

        var navigatedToProjects = false

        setContent {
            LtiTheme {
                EnvironmentStepContent(
                    state = SetupUiState(toolchainSetupState = state),
                    actions = SetupUiActions(),
                    onNavigateToProjects = { navigatedToProjects = true },
                )
            }
        }

        // Primary action must be "Open Workspaces"
        onNodeWithText("Open Workspaces").assertIsDisplayed().performClick()
        assertTrue(navigatedToProjects, "Clicking Open Workspaces must navigate to Projects/Workspaces")
    }
}
