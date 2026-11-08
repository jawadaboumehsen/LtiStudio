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
import androidx.compose.ui.test.performClick
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.domain.pipeline.toolkit.ImageTree
import org.ide.lti.core.domain.pipeline.toolkit.ImageTreeEntry
import org.ide.lti.core.domain.workbench.PartitionTree
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.workspace.AssemblySettings
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class AssembleEditorUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testAssembleEditorRoutesAllSubobjects() {
        var selectedSubobject by mutableStateOf("work-trees")
        val target = DefaultTargetCatalog.PQ84P01_DEFAULT
        val settings = AssemblySettings()
        var extractedPath: String? = null

        val sampleEntries = listOf(
            ImageTreeEntry("system/bin/sh", 12345L, 493, 0, 0, "u:object_r:shell_exec:s0"),
            ImageTreeEntry("system/app/TestApp", 0L, 16877, 0, 0, "u:object_r:system_file:s0"),
        )

        composeTestRule.setContent {
            LtiTheme {
                AssembleEditor(
                    settings = settings,
                    target = target,
                    onChange = {},
                    selectedObjectId = selectedSubobject,
                    // The row whose details are open: Copy out sits in its details panel.
                    imageState = ImagePaneState(
                        tree = PartitionTree(ImageTree(sampleEntries), "work/system", null),
                        focused = sampleEntries.first(),
                    ),
                    imageActions = ImagePaneActions(extract = { extractedPath = it.path }),
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("AssembleEditor").assertIsDisplayed()
        composeTestRule.onNodeWithTag("WorkTreesPane").assertIsDisplayed()

        selectedSubobject = "image-browser"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ImageBrowserPane").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ImageTreeRow_system/bin/sh").assertIsDisplayed()

        composeTestRule.onNodeWithTag("ImageItemDetails").assertIsDisplayed()
        composeTestRule.onNodeWithTag("CopyOutButton").performClick()
        assertEquals("system/bin/sh", extractedPath)

        selectedSubobject = "image-conversion"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ImageToolsPane").assertIsDisplayed()

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

        selectedSubobject = "mount-table"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("MountTablePane").assertIsDisplayed()
        composeTestRule.onNodeWithTag("DisableDataEncryptionToggle").assertIsDisplayed()
    }
}
