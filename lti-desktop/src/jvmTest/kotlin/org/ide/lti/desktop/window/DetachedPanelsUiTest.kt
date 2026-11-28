/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.desktop.window

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.GlassSceneHost
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.domain.copilot.BootloopCopilotAnalyzer
import org.ide.lti.core.domain.copilot.CrashSource
import org.ide.lti.core.domain.diff.ConfigDiffEngine
import org.ide.lti.core.domain.partition.explorer.PartitionTreeParser
import org.ide.lti.core.domain.pipeline.macro.MacroPipelineRunner
import org.ide.lti.core.ui.copilot.BootloopCopilotSheet
import org.ide.lti.core.ui.diff.PropertyDiffViewer
import org.ide.lti.core.ui.partition.PartitionExplorerView
import org.ide.lti.core.ui.pipeline.MacroPipelineStudioSheet
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class DetachedPanelsUiTest {

    @Test
    fun liveDiffInspectorBody_withoutWorkspaceSaysSoInsteadOfSampleData() = runDesktopComposeUiTest(
        width = 870,
        height = 750,
    ) {
        setContent {
            GlassSceneHost(effectsEnabled = false) {
                LtiTheme(appTheme = AppTheme.Dark, effectsEnabled = false, reducedMotion = true) {
                    LiveDiffInspectorBody()
                }
            }
        }
        onNodeWithText("Open a workspace", substring = true).assertIsDisplayed()
    }

    @Test
    fun allSmartPanels_composeSafelyInsideVerticalScrollParent() = runDesktopComposeUiTest(
        width = 870,
        height = 750,
    ) {
        val diff = ConfigDiffEngine.computeDiff("ro.a=1\n", "ro.a=2\nro.b=3\n")
        val partition = PartitionTreeParser.parseManifest(
            "system",
            "EROFS",
            listOf("system/bin/sh 0755 u:object_r:shell_exec:s0 250000"),
        )
        val diagnoses = BootloopCopilotAnalyzer.analyze(
            CrashSource.LOGCAT,
            listOf("03-25 12:00:02.345 1000 1000 F libc : Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR)"),
        )
        val recipe = MacroPipelineRunner.createDefaultFlashRecipe()

        setContent {
            GlassSceneHost(effectsEnabled = false) {
                LtiTheme(appTheme = AppTheme.Dark, effectsEnabled = false, reducedMotion = true) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        PropertyDiffViewer(diffResult = diff, snapshotName = "Slot B", onRollback = {})
                        PartitionExplorerView(metadata = partition, onExtractNode = {})
                        BootloopCopilotSheet(diagnoses = diagnoses)
                        MacroPipelineStudioSheet(
                            recipe = recipe,
                            isRunning = false,
                            onRunRecipe = {},
                            onCancelRecipe = {},
                            onRetryStep = {},
                        )
                    }
                }
            }
        }

        onNodeWithTag("PropertyDiffViewer").assertIsDisplayed()
        onNodeWithTag("PartitionExplorerView").assertExists()
        onNodeWithTag("MacroPipelineStudioSheet").assertExists()
    }
}
