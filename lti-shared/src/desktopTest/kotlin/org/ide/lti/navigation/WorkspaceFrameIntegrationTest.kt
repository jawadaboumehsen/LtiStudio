/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.ide.lti.core.designsystem.component.layout.GlobalAppDestination
import org.ide.lti.core.designsystem.component.layout.GlobalAppTopBar
import org.ide.lti.feature.workspace.studio.GlobalDestination
import org.ide.lti.feature.workspace.studio.StudioShellContent
import org.ide.lti.feature.workspace.studio.WorkspaceSection
import org.ide.lti.feature.workspace.studio.WorkspaceShellUiState
import org.junit.Rule
import org.junit.Test

class WorkspaceFrameIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testGlobalDestinationsReachableWithoutDuplicateTopBars() {
        var currentDestination by mutableStateOf(GlobalDestination.Workspace)

        composeTestRule.setContent {
            org.ide.lti.core.designsystem.theme.LtiTheme {
                Box(Modifier.fillMaxSize()) {
                    when (currentDestination) {
                        GlobalDestination.Workspace -> {
                            StudioShellContent(
                                uiState = WorkspaceShellUiState(globalDestination = GlobalDestination.Workspace),
                                onSelectGlobalDestination = { currentDestination = it },
                                onSelectWorkspaceSection = {},
                                onSelectStage = {},
                                onValidate = {},
                                onSave = {},
                                content = { Box(Modifier.fillMaxSize().testTag("WorkspaceContent")) },
                            )
                        }
                        GlobalDestination.Setup -> {
                            TestDestinationScreen(
                                destination = GlobalAppDestination.Setup,
                                contentTag = "SetupScreenContent",
                                onSelectDestination = { currentDestination = it.toStudioDestination() },
                            )
                        }
                        GlobalDestination.Settings -> {
                            TestDestinationScreen(
                                destination = GlobalAppDestination.Settings,
                                contentTag = "SettingsScreenContent",
                                onSelectDestination = { currentDestination = it.toStudioDestination() },
                            )
                        }
                        GlobalDestination.PluginManager -> {
                            Box(Modifier.fillMaxSize().testTag("PluginsScreenContent"))
                        }
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("WorkspaceContent").assertExists()
        composeTestRule.onAllNodesWithTag("GlobalAppTopBar").assertCountEquals(1)

        composeTestRule.onNodeWithTag("TopBar_Setup").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("SetupScreenContent").assertExists()
        composeTestRule.onAllNodesWithTag("GlobalAppTopBar").assertCountEquals(1)

        composeTestRule.onNodeWithTag("TopBar_Settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("SettingsScreenContent").assertExists()
        composeTestRule.onAllNodesWithTag("GlobalAppTopBar").assertCountEquals(1)

        composeTestRule.onNodeWithTag("TopBar_Workspace").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("WorkspaceContent").assertExists()
        composeTestRule.onAllNodesWithTag("GlobalAppTopBar").assertCountEquals(1)
    }

    @Test
    fun testStageConfigurationReachableWithoutDuplicateTopBars() {
        var activeSection by mutableStateOf<WorkspaceSection?>(WorkspaceSection.Overview)

        composeTestRule.setContent {
            org.ide.lti.core.designsystem.theme.LtiTheme {
                Box(Modifier.fillMaxSize()) {
                    StudioShellContent(
                        uiState = WorkspaceShellUiState(
                            globalDestination = GlobalDestination.Workspace,
                            selectedWorkspaceSection = activeSection,
                        ),
                        onSelectGlobalDestination = {},
                        onSelectWorkspaceSection = { activeSection = it },
                        onSelectStage = {},
                        onValidate = {},
                        onSave = {},
                        content = {
                            Box(Modifier.fillMaxSize().testTag("Section_${activeSection?.name}"))
                        },
                    )
                }
            }
        }

        // Initially Overview
        composeTestRule.onNodeWithTag("Section_Overview").assertExists()
        composeTestRule.onAllNodesWithTag("GlobalAppTopBar").assertCountEquals(1)

        // Switch to RomConfig
        activeSection = WorkspaceSection.RomConfig
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("Section_RomConfig").assertExists()
        composeTestRule.onAllNodesWithTag("GlobalAppTopBar").assertCountEquals(1)

        // Switch to TargetProfile
        activeSection = WorkspaceSection.TargetProfile
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("Section_TargetProfile").assertExists()
        composeTestRule.onAllNodesWithTag("GlobalAppTopBar").assertCountEquals(1)
    }

    @Composable
    private fun TestDestinationScreen(
        destination: GlobalAppDestination,
        contentTag: String,
        onSelectDestination: (GlobalAppDestination) -> Unit,
    ) {
        Column(Modifier.fillMaxSize()) {
            GlobalAppTopBar(
                selectedDestination = destination,
                onSelectDestination = onSelectDestination,
                contextLabel = destination.name,
                tagPrefix = "TopBar",
                windowControls = {},
            )
            Box(Modifier.fillMaxSize().testTag(contentTag))
        }
    }

    private fun GlobalAppDestination.toStudioDestination(): GlobalDestination = when (this) {
        GlobalAppDestination.Setup -> GlobalDestination.Setup
        GlobalAppDestination.Workspace -> GlobalDestination.Workspace
        GlobalAppDestination.Settings -> GlobalDestination.Settings
    }
}
