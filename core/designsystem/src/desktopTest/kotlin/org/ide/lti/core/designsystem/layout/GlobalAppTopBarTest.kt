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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import org.ide.lti.core.designsystem.component.actions.WindowControlActions
import org.ide.lti.core.designsystem.component.layout.GlobalAppDestination
import org.ide.lti.core.designsystem.component.layout.GlobalAppTopBar
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.colorSchemeFor
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun studioWindowControlsLeadTheToolbarAndInvokeTheirActions() {
        val events = mutableListOf<String>()
        composeTestRule.setContent {
            LtiTheme {
                GlobalAppTopBar(
                    selectedDestination = GlobalAppDestination.Workspace,
                    onSelectDestination = {},
                    windowControlActions = WindowControlActions(
                        onClose = { events.add("close") },
                        onMinimize = { events.add("minimize") },
                        onMaximize = { events.add("restore") },
                        isMaximized = { true },
                    ),
                )
            }
        }

        val controls = listOf("Close", "Minimize", "Restore").map { composeTestRule.onNodeWithContentDescription(it) }
        val positions = controls.map { it.fetchSemanticsNode().positionInRoot.x }
        val brand = composeTestRule.onNodeWithTag("GlobalTopBarBrand").fetchSemanticsNode()
        assertTrue(positions.zipWithNext().all { (left, right) -> left < right })
        assertTrue(positions.last() < brand.positionInRoot.x)
        controls.forEach { it.assertHasClickAction().performClick() }
        assertEquals(listOf("close", "minimize", "restore"), events)
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

    @Test
    fun testTopBarDragDispatchesDragCallbacks() {
        var dragStarted = false
        var dragDeltaX = 0f
        var dragDeltaY = 0f
        var dragEnded = false

        composeTestRule.setContent {
            LtiTheme {
                GlobalAppTopBar(
                    selectedDestination = GlobalAppDestination.Workspace,
                    onSelectDestination = {},
                    windowControlActions = WindowControlActions(
                        onDragWindow = { dragStarted = true },
                        onDragDelta = { dx, dy ->
                            dragDeltaX += dx
                            dragDeltaY += dy
                        },
                        onDragEnd = { dragEnded = true },
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag("GlobalAppTopBar").performTouchInput {
            down(Offset(900f, 20f))
            moveBy(Offset(40f, 10f))
            up()
        }

        assertTrue(dragStarted, "onDragWindow should be called on pointer down")
        assertTrue(dragDeltaX != 0f || dragDeltaY != 0f, "onDragDelta should receive movement delta")
        assertTrue(dragEnded, "onDragEnd should be called on pointer release")
    }

    @Test
    fun testTopBarDoubleClickTriggersMaximize() {
        var maximized = false
        composeTestRule.setContent {
            LtiTheme {
                GlobalAppTopBar(
                    selectedDestination = GlobalAppDestination.Workspace,
                    onSelectDestination = {},
                    windowControlActions = WindowControlActions(
                        onMaximize = { maximized = !maximized },
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag("GlobalAppTopBar").performTouchInput {
            doubleClick(Offset(900f, 20f))
        }

        assertTrue(maximized, "Double clicking empty space in the top app bar should toggle maximize")
    }
}
