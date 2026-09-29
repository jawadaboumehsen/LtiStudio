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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.ui.settings.AppSettingsState
import org.ide.lti.feature.settings.SettingsScreenContent
import org.ide.lti.feature.settings.model.SettingsCategory
import org.junit.Test

internal const val WIDTH_STANDARD = 1120
internal const val HEIGHT_STANDARD = 700
internal const val WIDTH_COMPACT = 900
internal const val HEIGHT_COMPACT = 600

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
    fun testSettings_Compact_Blue_EffectsOn() =
        runDesktopComposeUiTest(width = WIDTH_COMPACT, height = HEIGHT_COMPACT) {
            verifySettingsAppearance(AppTheme.Blue, effectsOn = true, width = WIDTH_COMPACT, height = HEIGHT_COMPACT)
        }

    @Test
    fun testSettings_Compact_Dark_EffectsOn() =
        runDesktopComposeUiTest(width = WIDTH_COMPACT, height = HEIGHT_COMPACT) {
            verifySettingsAppearance(AppTheme.Dark, effectsOn = true, width = WIDTH_COMPACT, height = HEIGHT_COMPACT)
        }

    @Test
    fun testSettings_Compact_Light_EffectsOn() =
        runDesktopComposeUiTest(width = WIDTH_COMPACT, height = HEIGHT_COMPACT) {
            verifySettingsAppearance(AppTheme.Light, effectsOn = true, width = WIDTH_COMPACT, height = HEIGHT_COMPACT)
        }

    // =============================================================================================
    // Navigation Interaction Verification
    // =============================================================================================

    @Test
    fun testNavigatorClickSelectsAppearance() =
        runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
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
        val name = "settings_appearance_${theme.id}_${effectsTag}_${width}x$height"
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
}
