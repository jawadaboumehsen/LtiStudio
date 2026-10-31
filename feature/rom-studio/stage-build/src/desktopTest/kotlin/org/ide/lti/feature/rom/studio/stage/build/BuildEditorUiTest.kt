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
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.workspace.BuildSettings
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

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
                    facts = BuildFacts(loaded = true),
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

    @Test
    fun thePartitionsTableChangesEachPartitionsOwnChoices() {
        var settings by mutableStateOf(BuildSettings())
        composeTestRule.setContent {
            LtiTheme {
                BuildEditor(
                    settings = settings,
                    target = DefaultTargetCatalog.PQ84P01_DEFAULT,
                    facts = BuildFacts(loaded = true),
                    onChange = { settings = it },
                )
            }
        }
        fun choose(field: String, option: String) {
            composeTestRule.onNodeWithTag(field).performScrollTo().performClick()
            composeTestRule.onNodeWithTag("$field:$option").performClick()
            composeTestRule.waitForIdle()
        }
        choose("Cluster_vendor", "64 KiB")
        assertEquals(mapOf("vendor" to 64), settings.partitionClusterKib)
        choose("Dedupe_vendor", "Off")
        assertEquals(mapOf("vendor" to false), settings.partitionDedupe)
        choose("Compression_vendor", "None")
        assertEquals("none", settings.partitionCompression["vendor"])
        // Uncompressed: deduplication and cluster no longer apply and cannot be opened.
        composeTestRule.onNodeWithTag("Cluster_vendor").assertHasNoClickAction()
        choose("Filesystem_odm", "ext4")
        assertEquals("ext4", settings.partitionFilesystem["odm"])
        choose("Size_odm", "Automatic + free space…")
        assertEquals(64, settings.partitionFreeMib["odm"])
        choose("Size_odm", "Fixed size…")
        assertEquals(null, settings.partitionFreeMib["odm"])
        assertEquals(64L * 1024 * 1024, settings.partitionSizeBytes["odm"])
        composeTestRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("SizeKib_odm")))
            .performTextReplacement("4096")
        assertEquals(4096L * 1024, settings.partitionSizeBytes["odm"])
    }

    @Test
    fun aStandaloneImageCanBeMadeSparseOnlyOnceItIsInTheZip() {
        var settings by mutableStateOf(BuildSettings(extraPackages = setOf("FullPackage")))
        composeTestRule.setContent {
            LtiTheme {
                BuildEditor(
                    settings = settings,
                    target = DefaultTargetCatalog.PQ84P01_DEFAULT,
                    facts = BuildFacts(loaded = true),
                    onChange = { settings = it },
                    selectedObjectId = "extra-packages",
                )
            }
        }
        composeTestRule.onNodeWithTag("SparseImage_vendor_dlkm").assertDoesNotExist()
        composeTestRule.onNodeWithTag("Standalone_vendor_dlkm").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("SparseImage_vendor_dlkm").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(setOf("vendor_dlkm"), settings.standaloneImages.toSet())
        assertEquals(setOf("vendor_dlkm"), settings.sparseImages.toSet())
        composeTestRule.onNodeWithTag("Standalone_vendor_dlkm").performClick()
        composeTestRule.waitForIdle()
        assertEquals(emptySet<String>(), settings.sparseImages.toSet())
    }
}
