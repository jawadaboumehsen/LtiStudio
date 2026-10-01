/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.build

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.workspace.BuildSettings
import org.junit.Rule
import org.junit.Test

class BuildEditorUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testBuildEditorRoutesAllSubobjects() {
        var selectedSubobject by mutableStateOf("compression")
        val target = DefaultTargetCatalog.PQ84P01_DEFAULT
        val settings = BuildSettings()

        composeTestRule.setContent {
            LtiTheme {
                BuildEditor(
                    settings = settings,
                    target = target,
                    onChange = {},
                    selectedObjectId = selectedSubobject,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("BuildEditor").assertIsDisplayed()
        composeTestRule.onNodeWithTag("CompressionPane").assertIsDisplayed()

        selectedSubobject = "images"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ImagesPane").assertIsDisplayed()

        selectedSubobject = "partition-layout"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("PartitionLayoutPane").assertIsDisplayed()

        selectedSubobject = "flashable-members"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("FlashableMembersPane").assertIsDisplayed()

        selectedSubobject = "avb-keys"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("AvbKeysPane").assertIsDisplayed()

        selectedSubobject = "output-naming"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("OutputNamingPane").assertIsDisplayed()

        selectedSubobject = "package-signing"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("PackageSigningPane").assertIsDisplayed()
    }
}
