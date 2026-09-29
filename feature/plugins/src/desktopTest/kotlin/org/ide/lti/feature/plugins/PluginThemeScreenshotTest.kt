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
class PluginThemeScreenshotTest {

    @Test
    fun testInstalledDarkGlassScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(AppTheme.Dark, createInstalledUiState(), "installed_dark")
    }

    @Test
    fun testInstalledBlueGlassScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(AppTheme.Blue, createInstalledUiState(), "installed_blue")
    }

    @Test
    fun testInstalledLightGlassScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(AppTheme.Light, createInstalledUiState(), "installed_light")
    }

    @Test
    fun testInstalledEffectsOffScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(AppTheme.Dark, createInstalledUiState(), "installed_effects_off", effectsEnabled = false)
    }

    @Test
    fun testInstalledEffectsOffBlueScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(AppTheme.Blue, createInstalledUiState(), "installed_effects_off_blue", effectsEnabled = false)
    }

    @Test
    fun testInstalledEffectsOffLightScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(
            AppTheme.Light,
            createInstalledUiState(),
            "installed_effects_off_light",
            effectsEnabled = false,
        )
    }

    @Test
    fun testMarketplaceDarkGlassScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(AppTheme.Dark, createMarketplaceUiState(), "marketplace_dark")
    }

    @Test
    fun testMarketplaceBlueGlassScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(AppTheme.Blue, createMarketplaceUiState(), "marketplace_blue")
    }

    @Test
    fun testMarketplaceLightGlassScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(AppTheme.Light, createMarketplaceUiState(), "marketplace_light")
    }

    @Test
    fun testMarketplaceEffectsOffScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(
            AppTheme.Dark,
            createMarketplaceUiState(),
            "marketplace_effects_off",
            effectsEnabled = false,
        )
    }

    @Test
    fun testMarketplaceEffectsOffBlueScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(
            AppTheme.Blue,
            createMarketplaceUiState(),
            "marketplace_effects_off_blue",
            effectsEnabled = false,
        )
    }

    @Test
    fun testMarketplaceEffectsOffLightScreenshot() = runDesktopComposeUiTest(
        width = SCREENSHOT_WIDTH,
        height = SCREENSHOT_HEIGHT,
    ) {
        renderAndCapture(
            AppTheme.Light,
            createMarketplaceUiState(),
            "marketplace_effects_off_light",
            effectsEnabled = false,
        )
    }
}
