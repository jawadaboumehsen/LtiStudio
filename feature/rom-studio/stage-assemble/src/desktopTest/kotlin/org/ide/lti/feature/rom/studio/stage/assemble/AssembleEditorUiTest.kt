/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.assemble

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.workspace.AssemblySettings
import org.junit.Rule
import org.junit.Test

class AssembleEditorUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testAssembleEditorRoutesAllSubobjects() {
        var selectedSubobject by mutableStateOf("work-trees")
        val target = DefaultTargetCatalog.PQ84P01_DEFAULT
        val settings = AssemblySettings()

        composeTestRule.setContent {
            LtiTheme {
                AssembleEditor(
                    settings = settings,
                    target = target,
                    onChange = {},
                    selectedObjectId = selectedSubobject,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("AssembleEditor").assertIsDisplayed()
        composeTestRule.onNodeWithTag("WorkTreesPane").assertIsDisplayed()

        selectedSubobject = "system-ext-handling"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("SystemExtHandlingPane").assertIsDisplayed()

        selectedSubobject = "boot-preparation"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("BootPreparationPane").assertIsDisplayed()

        selectedSubobject = "aot-cleanup"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("AotCleanupPane").assertIsDisplayed()

        selectedSubobject = "build-properties"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("BuildPropertiesPane").assertIsDisplayed()
    }
}
