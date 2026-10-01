/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.layout

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.ide.lti.core.designsystem.component.layout.GlobalAppDestination
import org.ide.lti.core.designsystem.component.layout.GlobalAppTopBar
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.colorSchemeFor
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class GlobalAppTopBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testSharedShellRendersOneBarAndAllGlobalDestinations() {
        composeTestRule.setContent {
            LtiTheme {
                GlobalAppTopBar(
                    selectedDestination = GlobalAppDestination.Workspace,
                    onSelectDestination = {},
                    contextLabel = "Pixel 9 Pro",
                    windowControls = {},
                )
            }
        }

        composeTestRule.onAllNodesWithTag("GlobalAppTopBar").assertCountEquals(1)
        composeTestRule.onNodeWithTag("GlobalTopBarBrand").assertExists()
        composeTestRule.onNodeWithText("LtiRom Studio").assertExists()
        GlobalAppDestination.entries.forEach { destination ->
            composeTestRule.onNodeWithTag("GlobalTopBar_${destination.name}").assertExists()
            composeTestRule.onNodeWithText(destination.name).assertExists()
        }
        composeTestRule.onNodeWithText("Pixel 9 Pro").assertExists()
        composeTestRule.onNodeWithTag("GlobalTopBar_Workspace")
            .assertIsSelected()
            .assertIsNotEnabled()
    }

    @Test
    fun testUnselectedDestinationDispatchesExactlyOnce() {
        var selection: GlobalAppDestination? = null
        var selectionCount = 0
        composeTestRule.setContent {
            LtiTheme {
                GlobalAppTopBar(
                    selectedDestination = GlobalAppDestination.Setup,
                    onSelectDestination = {
                        selection = it
                        selectionCount += 1
                    },
                    contextLabel = "Environment",
                    windowControls = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("GlobalTopBar_Settings")
            .assertHasClickAction()
            .performClick()

        assertEquals(GlobalAppDestination.Settings, selection)
        assertEquals(1, selectionCount)
    }

    @Test
    fun testBlueThemeProvidesExpectedColorScheme() {
        var observedTheme: AppTheme? = null
        var observedSurface: Color? = null
        composeTestRule.setContent {
            LtiTheme(appTheme = AppTheme.Blue) {
                observedTheme = GlassTheme.appTheme
                observedSurface = MaterialTheme.colorScheme.surface
            }
        }

        composeTestRule.runOnIdle {
            assertEquals(AppTheme.Blue, observedTheme)
            assertEquals(colorSchemeFor(AppTheme.Blue).surface, observedSurface)
        }
    }
}
