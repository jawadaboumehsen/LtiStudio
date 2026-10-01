/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.settings.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.LocalToggleDimensions
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.ToggleDimensions
import org.ide.lti.core.ui.settings.AppSettingsState
import org.ide.lti.feature.settings.SettingsScreenContent
import org.ide.lti.feature.settings.model.SettingsCategory
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SettingsAppearancePairComparisonScreenshotTest {

    // --- 1. Blue Glass Theme ---
    @Test
    fun testSettingsAppearance_Blue_EffectsOn_PairA() =
        runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
            renderAndSaveSettings(
                AppTheme.Blue,
                effectsOn = true,
                ToggleDimensions.PairA,
                "settings_appearance_blue_effects_on_pairA.png",
                "settings_card_blue_effects_on_pairA.png",
            )
        }

    @Test
    fun testSettingsAppearance_Blue_EffectsOn_PairB() =
        runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
            renderAndSaveSettings(
                AppTheme.Blue,
                effectsOn = true,
                ToggleDimensions.PairB,
                "settings_appearance_blue_effects_on_pairB.png",
                "settings_card_blue_effects_on_pairB.png",
            )
        }

    @Test
    fun testSettingsAppearance_Blue_EffectsOff_PairA() =
        runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
            renderAndSaveSettings(
                AppTheme.Blue,
                effectsOn = false,
                ToggleDimensions.PairA,
                "settings_appearance_blue_effects_off_pairA.png",
                "settings_card_blue_effects_off_pairA.png",
            )
        }

    @Test
    fun testSettingsAppearance_Blue_EffectsOff_PairB() =
        runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
            renderAndSaveSettings(
                AppTheme.Blue,
                effectsOn = false,
                ToggleDimensions.PairB,
                "settings_appearance_blue_effects_off_pairB.png",
                "settings_card_blue_effects_off_pairB.png",
            )
        }

    // --- 2. Dark Theme ---
    @Test
    fun testSettingsAppearance_Dark_EffectsOn_PairA() =
        runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
            renderAndSaveSettings(
                AppTheme.Dark,
                effectsOn = true,
                ToggleDimensions.PairA,
                "settings_appearance_dark_effects_on_pairA.png",
                "settings_card_dark_effects_on_pairA.png",
            )
        }

    @Test
    fun testSettingsAppearance_Dark_EffectsOn_PairB() =
        runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
            renderAndSaveSettings(
                AppTheme.Dark,
                effectsOn = true,
                ToggleDimensions.PairB,
                "settings_appearance_dark_effects_on_pairB.png",
                "settings_card_dark_effects_on_pairB.png",
            )
        }

    @Test
    fun testSettingsAppearance_Dark_EffectsOff_PairA() =
        runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
            renderAndSaveSettings(
                AppTheme.Dark,
                effectsOn = false,
                ToggleDimensions.PairA,
                "settings_appearance_dark_effects_off_pairA.png",
                "settings_card_dark_effects_off_pairA.png",
            )
        }

    @Test
    fun testSettingsAppearance_Dark_EffectsOff_PairB() =
        runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
            renderAndSaveSettings(
                AppTheme.Dark,
                effectsOn = false,
                ToggleDimensions.PairB,
                "settings_appearance_dark_effects_off_pairB.png",
                "settings_card_dark_effects_off_pairB.png",
            )
        }

    // --- 3. Light Theme ---
    @Test
    fun testSettingsAppearance_Light_EffectsOn_PairA() =
        runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
            renderAndSaveSettings(
                AppTheme.Light,
                effectsOn = true,
                ToggleDimensions.PairA,
                "settings_appearance_light_effects_on_pairA.png",
                "settings_card_light_effects_on_pairA.png",
            )
        }

    @Test
    fun testSettingsAppearance_Light_EffectsOn_PairB() =
        runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
            renderAndSaveSettings(
                AppTheme.Light,
                effectsOn = true,
                ToggleDimensions.PairB,
                "settings_appearance_light_effects_on_pairB.png",
                "settings_card_light_effects_on_pairB.png",
            )
        }

    @Test
    fun testSettingsAppearance_Light_EffectsOff_PairA() =
        runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
            renderAndSaveSettings(
                AppTheme.Light,
                effectsOn = false,
                ToggleDimensions.PairA,
                "settings_appearance_light_effects_off_pairA.png",
                "settings_card_light_effects_off_pairA.png",
            )
        }

    @Test
    fun testSettingsAppearance_Light_EffectsOff_PairB() =
        runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
            renderAndSaveSettings(
                AppTheme.Light,
                effectsOn = false,
                ToggleDimensions.PairB,
                "settings_appearance_light_effects_off_pairB.png",
                "settings_card_light_effects_off_pairB.png",
            )
        }

    private fun androidx.compose.ui.test.DesktopComposeUiTest.renderAndSaveSettings(
        theme: AppTheme,
        effectsOn: Boolean,
        dimensions: ToggleDimensions,
        screenFileName: String,
        cardFileName: String,
    ) {
        val themeId = when (theme) {
            AppTheme.Dark -> AppSettingsState.THEME_DARK
            AppTheme.Blue -> AppSettingsState.THEME_BLUE
            AppTheme.Light -> AppSettingsState.THEME_LIGHT
        }
        val settingsState = AppSettingsState(
            initialTheme = themeId,
            initialEffectsEnabled = effectsOn,
        )

        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1.0f, fontScale = 1.0f),
                LocalToggleDimensions provides dimensions,
            ) {
                LtiTheme(appTheme = theme, effectsEnabled = effectsOn) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SettingsScreenContent(
                            onBackClick = {},
                            appSettingsState = settingsState,
                            initialCategory = SettingsCategory.Appearance,
                        )
                    }
                }
            }
        }

        waitForIdle()
        val cardNode = onNodeWithTag("GlassCard_glass_and_motion").performScrollTo().assertIsDisplayed()
        waitForIdle()

        val outputDir = File("build/reports/screenshots")
        outputDir.mkdirs()

        // 1. Full Screen
        val screenImage = onRoot().captureToImage()
        val screenSkia = Image.makeFromBitmap(screenImage.asSkiaBitmap())
        val screenFile = File(outputDir, screenFileName)
        val screenBytes = screenSkia.encodeToData(EncodedImageFormat.PNG)?.bytes
        checkNotNull(screenBytes) { "Failed to encode screen capture to PNG" }
        screenFile.writeBytes(screenBytes)
        assertTrue(
            screenFile.exists() && screenFile.length() > 0,
            "Expected screen capture $screenFileName to be written",
        )

        // 2. Focused Glass & motion Card
        val cardImage = cardNode.captureToImage()
        val cardSkia = Image.makeFromBitmap(cardImage.asSkiaBitmap())
        val cardFile = File(outputDir, cardFileName)
        val cardBytes = cardSkia.encodeToData(EncodedImageFormat.PNG)?.bytes
        checkNotNull(cardBytes) { "Failed to encode card capture to PNG" }
        cardFile.writeBytes(cardBytes)
        assertTrue(cardFile.exists() && cardFile.length() > 0, "Expected card capture $cardFileName to be written")

        println(
            "[SETTINGS SCREENSHOT COMPARISON] Wrote $screenFileName (${screenFile.length()} bytes) and " +
                "$cardFileName (${cardFile.length()} bytes)",
        )
    }
}
