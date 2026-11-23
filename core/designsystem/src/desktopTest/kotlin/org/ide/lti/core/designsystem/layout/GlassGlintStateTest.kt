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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.ide.lti.core.designsystem.component.layout.GlassGlintState
import org.ide.lti.core.designsystem.component.layout.GlobalAppDestination
import org.ide.lti.core.designsystem.component.layout.GlobalAppTopBar
import org.ide.lti.core.designsystem.component.layout.glassAmbientGlint
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassGlintStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testGlintStatesInstantiateCorrectly() {
        val idle = GlassGlintState.Idle
        val active = GlassGlintState.ActiveBuild(0.5f)
        val warning = GlassGlintState.Warning("SELinux denial detected")
        val error = GlassGlintState.Error("Zygote SIGABRT")
        val connected = GlassGlintState.DeviceConnected("Pixel 8 Pro")

        assertTrue(idle is GlassGlintState)
        assertEquals(0.5f, active.progress)
        assertEquals("SELinux denial detected", warning.message)
        assertEquals("Zygote SIGABRT", error.message)
        assertEquals("Pixel 8 Pro", connected.deviceName)
    }

    @Test
    fun testGlassAmbientGlintModifierRendersAllStates() {
        composeTestRule.setContent {
            LtiTheme {
                Box(
                    modifier = Modifier
                        .testTag("GlintBoxIdle")
                        .size(ComponentSize.ButtonMinWidth, ComponentSize.ButtonMinHeight)
                        .glassAmbientGlint(GlassGlintState.Idle, GlassShapes.HazeFlat),
                )
                Box(
                    modifier = Modifier
                        .testTag("GlintBoxActive")
                        .size(ComponentSize.ButtonMinWidth, ComponentSize.ButtonMinHeight)
                        .glassAmbientGlint(GlassGlintState.ActiveBuild(), GlassShapes.HazeMedium),
                )
                Box(
                    modifier = Modifier
                        .testTag("GlintBoxWarning")
                        .size(ComponentSize.ButtonMinWidth, ComponentSize.ButtonMinHeight)
                        .glassAmbientGlint(GlassGlintState.Warning("Warn"), GlassShapes.HazeMedium),
                )
                Box(
                    modifier = Modifier
                        .testTag("GlintBoxError")
                        .size(ComponentSize.ButtonMinWidth, ComponentSize.ButtonMinHeight)
                        .glassAmbientGlint(GlassGlintState.Error("Err"), GlassShapes.HazeMedium),
                )
                Box(
                    modifier = Modifier
                        .testTag("GlintBoxConnected")
                        .size(ComponentSize.ButtonMinWidth, ComponentSize.ButtonMinHeight)
                        .glassAmbientGlint(GlassGlintState.DeviceConnected("Device"), GlassShapes.HazeMedium),
                )
            }
        }

        composeTestRule.onNodeWithTag("GlintBoxIdle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("GlintBoxActive").assertIsDisplayed()
        composeTestRule.onNodeWithTag("GlintBoxWarning").assertIsDisplayed()
        composeTestRule.onNodeWithTag("GlintBoxError").assertIsDisplayed()
        composeTestRule.onNodeWithTag("GlintBoxConnected").assertIsDisplayed()
    }

    @Test
    fun testGlobalAppTopBarAcceptsGlassGlintState() {
        composeTestRule.setContent {
            LtiTheme {
                GlobalAppTopBar(
                    selectedDestination = GlobalAppDestination.Workspace,
                    onSelectDestination = {},
                    glintState = GlassGlintState.ActiveBuild(0.75f),
                )
            }
        }

        composeTestRule.onNodeWithTag("GlobalAppTopBar").assertIsDisplayed()
    }
}
