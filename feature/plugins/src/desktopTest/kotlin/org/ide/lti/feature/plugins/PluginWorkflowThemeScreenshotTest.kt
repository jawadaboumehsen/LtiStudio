/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.plugins

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.ide.lti.core.designsystem.theme.AppTheme
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PluginWorkflowThemeScreenshotTest {

    @Test
    fun testImportDarkGlassScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(AppTheme.Dark, createImportUiState(), "import_dark")
    }

    @Test
    fun testImportBlueGlassScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(AppTheme.Blue, createImportUiState(), "import_blue")
    }

    @Test
    fun testImportLightGlassScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(AppTheme.Light, createImportUiState(), "import_light")
    }

    @Test
    fun testImportEffectsOffScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(AppTheme.Dark, createImportUiState(), "import_effects_off", effectsEnabled = false)
    }

    @Test
    fun testImportEffectsOffBlueScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(AppTheme.Blue, createImportUiState(), "import_effects_off_blue", effectsEnabled = false)
    }

    @Test
    fun testImportEffectsOffLightScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(
            AppTheme.Light,
            createImportUiState(),
            "import_effects_off_light",
            effectsEnabled = false,
        )
    }

    @Test
    fun testAuthorToolsDarkGlassScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(AppTheme.Dark, createAuthorToolsUiState(), "author_tools_dark")
    }

    @Test
    fun testAuthorToolsBlueGlassScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(AppTheme.Blue, createAuthorToolsUiState(), "author_tools_blue")
    }

    @Test
    fun testAuthorToolsLightGlassScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(AppTheme.Light, createAuthorToolsUiState(), "author_tools_light")
    }

    @Test
    fun testAuthorToolsEffectsOffScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(
            AppTheme.Dark,
            createAuthorToolsUiState(),
            "author_tools_effects_off",
            effectsEnabled = false,
        )
    }

    @Test
    fun testAuthorToolsEffectsOffBlueScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(
            AppTheme.Blue,
            createAuthorToolsUiState(),
            "author_tools_effects_off_blue",
            effectsEnabled = false,
        )
    }

    @Test
    fun testAuthorToolsEffectsOffLightScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(
            AppTheme.Light,
            createAuthorToolsUiState(),
            "author_tools_effects_off_light",
            effectsEnabled = false,
        )
    }
}
