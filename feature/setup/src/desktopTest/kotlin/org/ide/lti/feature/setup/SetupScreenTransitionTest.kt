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

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.feature.setup.api.SetupTab
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Tests verifying the iOS-styled unified transition motion across setup tabs
 * and reduced motion handling.
 */
@OptIn(ExperimentalTestApi::class)
class SetupScreenTransitionTest {

    @Test
    fun testSetupTabTransitionBetweenAllSteps() = runDesktopComposeUiTest {
        var currentTab by mutableStateOf(SetupTab.PROJECTS)
        setContent {
            LtiTheme {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = setupTabTransitionSpec(isReducedMotion = false),
                    label = "TestSetupTransition",
                ) { tab ->
                    Text(text = "Tab: ${tab.id}")
                }
            }
        }

        onNodeWithText("Tab: projects").assertIsDisplayed()

        // Forward transition: PROJECTS -> ENVIRONMENT
        currentTab = SetupTab.ENVIRONMENT
        waitForIdle()
        onNodeWithText("Tab: environment").assertIsDisplayed()

        // Forward transition: ENVIRONMENT -> TOOLS
        currentTab = SetupTab.TOOLS
        waitForIdle()
        onNodeWithText("Tab: tools").assertIsDisplayed()

        // Forward transition: TOOLS -> RECOVERY
        currentTab = SetupTab.RECOVERY
        waitForIdle()
        onNodeWithText("Tab: recovery").assertIsDisplayed()

        // Backward transition: RECOVERY -> PROJECTS
        currentTab = SetupTab.PROJECTS
        waitForIdle()
        onNodeWithText("Tab: projects").assertIsDisplayed()
    }

    @Test
    fun testSetupTabTransitionWithReducedMotion() = runDesktopComposeUiTest {
        var currentTab by mutableStateOf(SetupTab.PROJECTS)
        setContent {
            LtiTheme {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = setupTabTransitionSpec(isReducedMotion = true),
                    label = "TestSetupTransitionReducedMotion",
                ) { tab ->
                    Text(text = "Tab: ${tab.id}")
                }
            }
        }

        onNodeWithText("Tab: projects").assertIsDisplayed()

        currentTab = SetupTab.TOOLS
        waitForIdle()
        onNodeWithText("Tab: tools").assertIsDisplayed()
    }

    @Test
    fun testSetupScreenContentTabNavigation() = runDesktopComposeUiTest(width = 1200, height = 800) {
        var selectedTab by mutableStateOf(SetupTab.PROJECTS)

        setContent {
            LtiTheme {
                SetupScreenContent(
                    state = SetupUiState(
                        selectedTab = selectedTab,
                    ),
                    actions = SetupUiActions(
                        onSelectTab = { selectedTab = it },
                    ),
                )
            }
        }

        // Initially on Workspaces / Projects
        onNodeWithTag("RailItem_projects").assertExists()
        onNodeWithTag("RailItem_environment").assertExists()
        onNodeWithTag("RailItem_tools").assertExists()
        onNodeWithTag("RailItem_recovery").assertExists()

        // Switch to Environment via rail item
        onNodeWithTag("RailItem_environment").performClick()
        waitForIdle()
        assertEquals(SetupTab.ENVIRONMENT, selectedTab)

        // Switch to Tools via rail item
        onNodeWithTag("RailItem_tools").performClick()
        waitForIdle()
        assertEquals(SetupTab.TOOLS, selectedTab)

        // Switch back to Projects
        onNodeWithTag("RailItem_projects").performClick()
        waitForIdle()
        assertEquals(SetupTab.PROJECTS, selectedTab)
    }

    @Test
    fun testSetupTabTransitionWithScrollableContentDoesNotThrowInfiniteConstraints() = runDesktopComposeUiTest {
        var currentTab by mutableStateOf(SetupTab.PROJECTS)
        setContent {
            LtiTheme {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = setupTabTransitionSpec(isReducedMotion = false),
                    label = "TestScrollableTransition",
                ) { tab ->
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(text = "Scrollable Content for ${tab.id}")
                    }
                }
            }
        }

        onNodeWithText("Scrollable Content for projects").assertIsDisplayed()

        currentTab = SetupTab.ENVIRONMENT
        waitForIdle()
        onNodeWithText("Scrollable Content for environment").assertIsDisplayed()

        currentTab = SetupTab.TOOLS
        waitForIdle()
        onNodeWithText("Scrollable Content for tools").assertIsDisplayed()
    }
}
