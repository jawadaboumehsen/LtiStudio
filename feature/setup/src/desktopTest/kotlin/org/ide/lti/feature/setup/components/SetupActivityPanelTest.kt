/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.feature.setup.SetupActivityState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SetupActivityPanelTest {
    @Test
    fun testActivityPanelPauseFollowAndJumpToLatest() = runDesktopComposeUiTest(width = 1120, height = 900) {
        val testScope = CoroutineScope(Dispatchers.Unconfined)
        val activityState = SetupActivityState(scope = testScope)
        activityState.appendLines(listOf("Line 1", "Line 2", "Line 3"))

        setContent {
            LtiTheme {
                SetupActivityPanel(
                    activityState = activityState,
                    title = "Activity Test",
                )
            }
        }

        // Initially following latest
        assertTrue(activityState.followingLatest.value)
        onNodeWithText("Pause follow").assertIsDisplayed().performClick()

        // After pause
        assertFalse(activityState.followingLatest.value)
        onNodeWithText("Jump to latest").assertIsDisplayed().performClick()

        // After jump to latest
        assertTrue(activityState.followingLatest.value)
        onNodeWithText("Pause follow").assertIsDisplayed()
    }

    @Test
    fun testActivityPanelSelectionAndCopy() = runDesktopComposeUiTest(width = 1120, height = 900) {
        val testScope = CoroutineScope(Dispatchers.Unconfined)
        val activityState = SetupActivityState(scope = testScope)
        activityState.appendLines(listOf("Alpha log entry", "Beta log entry", "Gamma log entry"))

        setContent {
            LtiTheme {
                SetupActivityPanel(
                    activityState = activityState,
                    title = "Activity Test",
                )
            }
        }

        // Initially 0 selected lines
        assertTrue(activityState.selectedEventIds.value.isEmpty())

        // Click on "Alpha log entry": selection is keyed by the event id, not a list index
        onNodeWithText("Alpha log entry").assertIsDisplayed().performClick()
        val alphaId = activityState.visibleEvents.value.first { it.text == "Alpha log entry" }.id
        assertEquals(setOf(alphaId), activityState.selectedEventIds.value)
        onNodeWithText("Copy selection (1)").assertIsDisplayed()

        // Clear selection via Clear button
        onNodeWithContentDescription("Clear selection").assertIsDisplayed().performClick()
        assertTrue(activityState.selectedEventIds.value.isEmpty())
    }
}
