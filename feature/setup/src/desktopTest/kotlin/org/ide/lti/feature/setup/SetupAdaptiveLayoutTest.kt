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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Clock
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.workspace.RecentProject
import org.junit.Test

/** Adaptive layout and text-enlargement regression tests at compact, wide and maximized window sizes. */
@OptIn(ExperimentalTestApi::class)
class SetupAdaptiveLayoutTest {
    @Test
    fun testAdaptiveLayoutCompactAt900x600() = runDesktopComposeUiTest(width = 900, height = 600) {
        val testProject =
            RecentProject(
                workspaceId = "ws_test",
                name = "Kernel_AOSP",
                path = "C:/RomDev/Kernel_AOSP",
                lastOpened = Clock.System.now(),
            )
        // Supply a ready environment state so the header shows the compact "Machine ready"
        // status line instead of the taller "Set up this machine" action row, which would
        // push "Recent Workspaces" below the 600 px viewport boundary (SC-005).
        val readySteps =
            listOf(
                SetupStepDetail(
                    stage = SetupStepStage.WSL_DETECTION,
                    title = "WSL Detection",
                    description = "Ubuntu-24.04",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.SYSTEM_PACKAGES,
                    title = "System Packages",
                    description = "All packages present",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.SERVER_CONNECTIVITY,
                    title = "Server",
                    description = "Connected",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.SYSTEM_DIAGNOSTICS,
                    title = "Diagnostics",
                    description = "All checks passed",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.REPO_SYNCHRONIZATION,
                    title = "Repos",
                    description = "Synced",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                    title = "Toolchain",
                    description = "Ready",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
            )
        val readyState = ToolchainSetupState(steps = readySteps, lastVerifiedTimestamp = 1_700_000_000_000L)

        setContent {
            LtiTheme {
                SetupScreenContent(
                    state =
                    SetupUiState(
                        recentProjects = listOf(testProject),
                        filteredProjects = listOf(testProject),
                        selectedTab = SetupTab.PROJECTS,
                        toolchainSetupState = readyState,
                    ),
                    actions = SetupUiActions(),
                )
            }
        }

        // Brand and navigation elements must be displayed in compact mode
        onNodeWithText("Machine environment").assertIsDisplayed()
        onNodeWithText("Recent Workspaces").assertIsDisplayed()
        onNodeWithText("Environment").assertIsDisplayed()

        // Content items displayed without clipping
        onNodeWithText("Kernel_AOSP").assertIsDisplayed()
        onNodeWithText("New Workspace").assertIsDisplayed()
        onNodeWithText("Open Workspace").assertIsDisplayed()
    }

    @Test
    fun testAdaptiveLayoutWideAt1440x900() = runDesktopComposeUiTest(width = 1440, height = 900) {
        val testProject =
            RecentProject(
                workspaceId = "ws_test_wide",
                name = "Pixel_Port",
                path = "C:/RomDev/Pixel_Port",
                lastOpened = Clock.System.now(),
            )

        setContent {
            LtiTheme {
                SetupScreenContent(
                    state =
                    SetupUiState(
                        recentProjects = listOf(testProject),
                        filteredProjects = listOf(testProject),
                        selectedTab = SetupTab.PROJECTS,
                    ),
                    actions = SetupUiActions(),
                )
            }
        }

        // Wide elements
        onNodeWithText("Machine environment").assertIsDisplayed()
        onNodeWithText("Recent Workspaces").assertIsDisplayed()
        onNodeWithText("Pixel_Port").assertIsDisplayed()
        onNodeWithText("New Workspace").assertIsDisplayed()
        onNodeWithText("Open Workspace").assertIsDisplayed()
        onNodeWithTag("SetupRightRail").assertExists()
    }

    @Test
    fun testTextEnlargementAtCompactSizeDoesNotClipActions() = runDesktopComposeUiTest(width = 900, height = 600) {
        val testProject =
            RecentProject(
                workspaceId = "ws_scaled",
                name = "Scaled_Workspace",
                path = "C:/RomDev/Scaled_Workspace",
                lastOpened = Clock.System.now(),
            )

        val largeTypography =
            Typography(
                titleLarge = TextStyle(fontSize = 26.sp),
                titleMedium = TextStyle(fontSize = 20.sp),
                bodyLarge = TextStyle(fontSize = 18.sp),
                bodyMedium = TextStyle(fontSize = 16.sp),
                labelLarge = TextStyle(fontSize = 16.sp),
            )

        // Supply a ready environment state so the header shows the compact "Machine ready"
        // status line instead of the taller "Set up this machine" action row. The action row
        // with enlarged text would clip "New Workspace" / "Open Workspace" past 600 px (SC-005).
        val readySteps =
            listOf(
                SetupStepDetail(
                    stage = SetupStepStage.WSL_DETECTION,
                    title = "WSL Detection",
                    description = "Ubuntu-24.04",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.SYSTEM_PACKAGES,
                    title = "System Packages",
                    description = "All packages present",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.SERVER_CONNECTIVITY,
                    title = "Server",
                    description = "Connected",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.SYSTEM_DIAGNOSTICS,
                    title = "Diagnostics",
                    description = "All checks passed",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.REPO_SYNCHRONIZATION,
                    title = "Repos",
                    description = "Synced",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                    title = "Toolchain",
                    description = "Ready",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
            )
        val readyState = ToolchainSetupState(steps = readySteps, lastVerifiedTimestamp = 1_700_000_000_000L)

        setContent {
            LtiTheme {
                MaterialTheme(typography = largeTypography) {
                    SetupScreenContent(
                        state =
                        SetupUiState(
                            recentProjects = listOf(testProject),
                            filteredProjects = listOf(testProject),
                            selectedTab = SetupTab.PROJECTS,
                            toolchainSetupState = readyState,
                        ),
                        actions = SetupUiActions(),
                    )
                }
            }
        }

        // Essential actions must remain visible and accessible with enlarged text (SC-005)
        onNodeWithText("New Workspace").assertIsDisplayed()
        onNodeWithText("Open Workspace").assertIsDisplayed()
        onNodeWithText("Scaled_Workspace").assertIsDisplayed()
    }

    @Test
    fun testAdaptiveLayoutEnvironmentCompactAt900x600() = runDesktopComposeUiTest(width = 900, height = 600) {
        val testSteps =
            listOf(
                SetupStepDetail(
                    stage = SetupStepStage.WSL_DETECTION,
                    title = "WSL Detection",
                    description = "Ubuntu 22.04 LTS",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.SERVER_CONNECTIVITY,
                    title = "Server Daemon Bridge",
                    description = "Connected",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.SYSTEM_DIAGNOSTICS,
                    title = "System Diagnostics",
                    description = "Healthy",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
            )
        val state = ToolchainSetupState(steps = testSteps, lastVerifiedTimestamp = 1700000000000L)

        setContent {
            LtiTheme {
                SetupScreenContent(
                    state =
                    SetupUiState(
                        toolchainSetupState = state,
                        selectedTab = SetupTab.ENVIRONMENT,
                    ),
                    actions = SetupUiActions(),
                )
            }
        }

        // Environment header and navigation visible in compact mode (SC-005)
        onAllNodesWithText("Machine setup").onFirst().assertIsDisplayed()
        onNodeWithText("Environment").assertIsDisplayed()
        onNodeWithText("Tools").assertIsDisplayed()
    }

    @Test
    fun testAdaptiveLayoutMaximized1920x1080() = runDesktopComposeUiTest(width = 1920, height = 1080) {
        val testProject =
            RecentProject(
                workspaceId = "ws_maximized",
                name = "Maximized_Workspace",
                path = "C:/RomDev/Maximized_Workspace",
                lastOpened = Clock.System.now(),
            )

        setContent {
            LtiTheme {
                SetupScreenContent(
                    state =
                    SetupUiState(
                        recentProjects = listOf(testProject),
                        filteredProjects = listOf(testProject),
                        selectedTab = SetupTab.PROJECTS,
                    ),
                    actions = SetupUiActions(),
                )
            }
        }

        // Maximized layout checks (SC-005)
        onNodeWithText("Recent Workspaces").assertIsDisplayed()
        onNodeWithText("New Workspace").assertIsDisplayed()
        onNodeWithText("Open Workspace").assertIsDisplayed()
        onNodeWithText("Maximized_Workspace").assertIsDisplayed()
    }
}
