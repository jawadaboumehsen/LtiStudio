/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.feature.plugins

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.domain.plugin.InstallState
import org.ide.lti.core.domain.plugin.InstalledPackage
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ImportCompletionTest {
    @Test
    fun installedPackageHasACompletedFlowAndResetsForTheNextArchive() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        val installed = mutableStateOf<InstallState?>(
            InstallState.Installed(InstalledPackage("io.ltirom", "pif", "1.4.1", "digest", emptyList(), emptyList())),
        )
        setContent {
            LtiTheme(appTheme = AppTheme.Dark, effectsEnabled = false) {
                PluginImportTab(
                    importPath = "",
                    inspection = null,
                    isLegacyUnsupported = false,
                    legacyFormatName = null,
                    installState = installed.value,
                    onImportPathChange = {},
                    onInspectPath = {},
                    onDismissLegacyWarning = {},
                    onConfirmInstall = {},
                )
            }
        }
        onNodeWithTag("ImportInstalled").assertIsDisplayed()
        onNodeWithText("io.ltirom:pif 1.4.1").assertIsDisplayed()
        onNodeWithText("Select and inspect a package archive").assertDoesNotExist()
        onAllNodesWithContentDescription("Completed").assertCountEquals(3)
        runOnIdle { installed.value = null }
        onNodeWithTag("ImportInstalled").assertDoesNotExist()
        onNodeWithText("Select and inspect a package archive").assertIsDisplayed()
        onAllNodesWithContentDescription("Completed").assertCountEquals(0)
    }
}
