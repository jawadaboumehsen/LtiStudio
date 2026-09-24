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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
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
class SettingsAppearanceScreenshotTest {

    // =============================================================================================
    // Individual Named Test Cases for Standard Viewport (1120x700)
    // =============================================================================================

    @Test
    fun testSettings_Dark_EffectsOn() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        verifySettingsAppearance(AppTheme.Dark, effectsOn = true, width = WIDTH_STANDARD, height = HEIGHT_STANDARD)
    }

    @Test
    fun testSettings_Dark_EffectsOff() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        verifySettingsAppearance(AppTheme.Dark, effectsOn = false, width = WIDTH_STANDARD, height = HEIGHT_STANDARD)
    }

    @Test
    fun testSettings_Blue_EffectsOn() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        verifySettingsAppearance(AppTheme.Blue, effectsOn = true, width = WIDTH_STANDARD, height = HEIGHT_STANDARD)
    }

    @Test
    fun testSettings_Blue_EffectsOff() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        verifySettingsAppearance(AppTheme.Blue, effectsOn = false, width = WIDTH_STANDARD, height = HEIGHT_STANDARD)
    }

    @Test
    fun testSettings_Light_EffectsOn() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        verifySettingsAppearance(AppTheme.Light, effectsOn = true, width = WIDTH_STANDARD, height = HEIGHT_STANDARD)
    }

    @Test
    fun testSettings_Light_EffectsOff() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        verifySettingsAppearance(AppTheme.Light, effectsOn = false, width = WIDTH_STANDARD, height = HEIGHT_STANDARD)
    }

    // =============================================================================================
    // Compact Viewport Smoke Cases (900x600)
    // =============================================================================================

    @Test
    fun testSettings_Compact_Blue_EffectsOn() = runDesktopComposeUiTest(width = WIDTH_COMPACT, height = HEIGHT_COMPACT) {
        verifySettingsAppearance(AppTheme.Blue, effectsOn = true, width = WIDTH_COMPACT, height = HEIGHT_COMPACT)
    }

    @Test
    fun testSettings_Compact_Dark_EffectsOn() = runDesktopComposeUiTest(width = WIDTH_COMPACT, height = HEIGHT_COMPACT) {
        verifySettingsAppearance(AppTheme.Dark, effectsOn = true, width = WIDTH_COMPACT, height = HEIGHT_COMPACT)
    }

    @Test
    fun testSettings_Compact_Light_EffectsOn() = runDesktopComposeUiTest(width = WIDTH_COMPACT, height = HEIGHT_COMPACT) {
        verifySettingsAppearance(AppTheme.Light, effectsOn = true, width = WIDTH_COMPACT, height = HEIGHT_COMPACT)
    }

    // =============================================================================================
    // Navigation Interaction Verification
    // =============================================================================================

    @Test
    fun testNavigatorClickSelectsAppearance() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        val settingsState = AppSettingsState(
            initialTheme = AppSettingsState.THEME_BLUE,
            initialEffectsEnabled = true,
        )

        setContent {
            LtiTheme(appTheme = AppTheme.Blue) {
                Box(Modifier.fillMaxSize()) {
                    GlassBackdrop()
                    SettingsScreenContent(
                        onBackClick = {},
                        appSettingsState = settingsState,
                        initialCategory = SettingsCategory.EditorPreferences,
                    )
                }
            }
        }

        waitForIdle()
        // Verify we started on Editor Preferences in the navigator
        onNodeWithTag("NavigatorItem_editor").assertIsDisplayed()

        // Click the real navigation item in IdeNavigatorPanel
        onNodeWithTag("NavigatorItem_appearance").performClick()
        waitForIdle()

        // Verify that Appearance category is now active and "Glass & motion" card is rendered
        onNodeWithTag("GlassCard_glass_and_motion").performScrollTo().assertIsDisplayed()
    }

    // =============================================================================================
    // =============================================================================================
    // Visual Comparison Test Cases: Pair A (Current) vs Pair B (Compact Ergonomic) Across All Themes
    // =============================================================================================

    // --- 1. Blue Glass Theme ---
    @Test
    fun testSettingsAppearance_Blue_EffectsOn_PairA() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        renderAndSaveSettings(AppTheme.Blue, effectsOn = true, ToggleDimensions.PairA, "settings_appearance_blue_effects_on_pairA.png", "settings_card_blue_effects_on_pairA.png")
    }

    @Test
    fun testSettingsAppearance_Blue_EffectsOn_PairB() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        renderAndSaveSettings(AppTheme.Blue, effectsOn = true, ToggleDimensions.PairB, "settings_appearance_blue_effects_on_pairB.png", "settings_card_blue_effects_on_pairB.png")
    }

    @Test
    fun testSettingsAppearance_Blue_EffectsOff_PairA() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        renderAndSaveSettings(AppTheme.Blue, effectsOn = false, ToggleDimensions.PairA, "settings_appearance_blue_effects_off_pairA.png", "settings_card_blue_effects_off_pairA.png")
    }

    @Test
    fun testSettingsAppearance_Blue_EffectsOff_PairB() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        renderAndSaveSettings(AppTheme.Blue, effectsOn = false, ToggleDimensions.PairB, "settings_appearance_blue_effects_off_pairB.png", "settings_card_blue_effects_off_pairB.png")
    }

    // --- 2. Dark Theme ---
    @Test
    fun testSettingsAppearance_Dark_EffectsOn_PairA() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        renderAndSaveSettings(AppTheme.Dark, effectsOn = true, ToggleDimensions.PairA, "settings_appearance_dark_effects_on_pairA.png", "settings_card_dark_effects_on_pairA.png")
    }

    @Test
    fun testSettingsAppearance_Dark_EffectsOn_PairB() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        renderAndSaveSettings(AppTheme.Dark, effectsOn = true, ToggleDimensions.PairB, "settings_appearance_dark_effects_on_pairB.png", "settings_card_dark_effects_on_pairB.png")
    }

    @Test
    fun testSettingsAppearance_Dark_EffectsOff_PairA() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        renderAndSaveSettings(AppTheme.Dark, effectsOn = false, ToggleDimensions.PairA, "settings_appearance_dark_effects_off_pairA.png", "settings_card_dark_effects_off_pairA.png")
    }

    @Test
    fun testSettingsAppearance_Dark_EffectsOff_PairB() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        renderAndSaveSettings(AppTheme.Dark, effectsOn = false, ToggleDimensions.PairB, "settings_appearance_dark_effects_off_pairB.png", "settings_card_dark_effects_off_pairB.png")
    }

    // --- 3. Light Theme ---
    @Test
    fun testSettingsAppearance_Light_EffectsOn_PairA() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        renderAndSaveSettings(AppTheme.Light, effectsOn = true, ToggleDimensions.PairA, "settings_appearance_light_effects_on_pairA.png", "settings_card_light_effects_on_pairA.png")
    }

    @Test
    fun testSettingsAppearance_Light_EffectsOn_PairB() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        renderAndSaveSettings(AppTheme.Light, effectsOn = true, ToggleDimensions.PairB, "settings_appearance_light_effects_on_pairB.png", "settings_card_light_effects_on_pairB.png")
    }

    @Test
    fun testSettingsAppearance_Light_EffectsOff_PairA() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        renderAndSaveSettings(AppTheme.Light, effectsOn = false, ToggleDimensions.PairA, "settings_appearance_light_effects_off_pairA.png", "settings_card_light_effects_off_pairA.png")
    }

    @Test
    fun testSettingsAppearance_Light_EffectsOff_PairB() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        renderAndSaveSettings(AppTheme.Light, effectsOn = false, ToggleDimensions.PairB, "settings_appearance_light_effects_off_pairB.png", "settings_card_light_effects_off_pairB.png")
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
        assertTrue(screenFile.exists() && screenFile.length() > 0, "Expected screen capture $screenFileName to be written")

        // 2. Focused Glass & motion Card
        val cardImage = cardNode.captureToImage()
        val cardSkia = Image.makeFromBitmap(cardImage.asSkiaBitmap())
        val cardFile = File(outputDir, cardFileName)
        val cardBytes = cardSkia.encodeToData(EncodedImageFormat.PNG)?.bytes
        checkNotNull(cardBytes) { "Failed to encode card capture to PNG" }
        cardFile.writeBytes(cardBytes)
        assertTrue(cardFile.exists() && cardFile.length() > 0, "Expected card capture $cardFileName to be written")

        println("[SETTINGS SCREENSHOT COMPARISON] Wrote $screenFileName (${screenFile.length()} bytes) and $cardFileName (${cardFile.length()} bytes)")
    }

    // =============================================================================================
    // Internal Helper & Roborazzi Capture Engine
    // =============================================================================================

    private fun androidx.compose.ui.test.DesktopComposeUiTest.verifySettingsAppearance(
        theme: AppTheme,
        effectsOn: Boolean,
        width: Int,
        height: Int,
        density: Float = 1.0f,
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
                // fontScale must strictly remain 1.0f; only density varies if testing high-DPI
                LocalDensity provides Density(density = density, fontScale = 1.0f),
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
        // Scroll to target card to ensure the entire card region is in view
        onNodeWithTag("GlassCard_glass_and_motion").performScrollTo().assertIsDisplayed()
        waitForIdle()

        val effectsTag = if (effectsOn) "effects_on" else "effects_off"
        val name = "settings_appearance_${theme.id}_${effectsTag}_${width}x${height}"
        val targetPath = "src/desktopTest/resources/screenshots/$name.png"

        // Capture through Roborazzi Desktop with 0.5% tolerance threshold
        onRoot().captureRoboImage(
            filePath = targetPath,
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(
                    changeThreshold = 0.005f,
                ),
                recordOptions = RoborazziOptions.RecordOptions(
                    resizeScale = 1.0,
                ),
            ),
        )
    }

    private companion object {
        const val WIDTH_STANDARD = 1120
        const val HEIGHT_STANDARD = 700
        const val WIDTH_COMPACT = 900
        const val HEIGHT_COMPACT = 600
    }
}
