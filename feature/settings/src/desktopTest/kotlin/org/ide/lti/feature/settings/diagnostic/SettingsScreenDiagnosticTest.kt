/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.settings.diagnostic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.ColorContrast
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.colorSchemeFor
import org.ide.lti.core.ui.settings.AppSettingsState
import org.ide.lti.feature.settings.SettingsScreenContent
import org.ide.lti.feature.settings.model.SettingsCategory
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SettingsScreenDiagnosticTest {

    @Test
    fun testSettingsAppearanceRenderedPixelContrastMeetsWcagDarkEffectsOn() =
        assertThemeContrastMeetsWcag(AppTheme.Dark, effectsEnabled = true)

    @Test
    fun testSettingsAppearanceRenderedPixelContrastMeetsWcagDarkEffectsOff() =
        assertThemeContrastMeetsWcag(AppTheme.Dark, effectsEnabled = false)

    @Test
    fun testSettingsAppearanceRenderedPixelContrastMeetsWcagBlueEffectsOn() =
        assertThemeContrastMeetsWcag(AppTheme.Blue, effectsEnabled = true)

    @Test
    fun testSettingsAppearanceRenderedPixelContrastMeetsWcagBlueEffectsOff() =
        assertThemeContrastMeetsWcag(AppTheme.Blue, effectsEnabled = false)

    @Test
    fun testSettingsAppearanceRenderedPixelContrastMeetsWcagLightEffectsOn() =
        assertThemeContrastMeetsWcag(AppTheme.Light, effectsEnabled = true)

    @Test
    fun testSettingsAppearanceRenderedPixelContrastMeetsWcagLightEffectsOff() =
        assertThemeContrastMeetsWcag(AppTheme.Light, effectsEnabled = false)

    private fun assertThemeContrastMeetsWcag(theme: AppTheme, effectsEnabled: Boolean = true) = runDesktopComposeUiTest(
        width = 1120,
        height = 700,
    ) {
        val themeId = when (theme) {
            AppTheme.Dark -> AppSettingsState.THEME_DARK
            AppTheme.Blue -> AppSettingsState.THEME_BLUE
            AppTheme.Light -> AppSettingsState.THEME_LIGHT
        }

        val settingsState = AppSettingsState(
            initialTheme = themeId,
            initialEffectsEnabled = effectsEnabled,
        )

        setContent {
            LtiTheme(appTheme = theme, effectsEnabled = effectsEnabled) {
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

        waitForIdle()

        // 1. Measure unboxed header text at the top of the appearance panel
        val appearanceHeaderBounds = onNodeWithText("Make the studio comfortable for long sessions.").getBoundsInRoot()
        val headerX = (appearanceHeaderBounds.left.value + 5f).toInt()
        val headerY = (
            appearanceHeaderBounds.top.value +
                (appearanceHeaderBounds.bottom.value - appearanceHeaderBounds.top.value) / 2f
            ).toInt()

        // 2. Scroll to "Glass & motion" card and the switch inside it
        onNodeWithTag("GlassCard_glass_and_motion").performScrollTo().assertIsDisplayed()
        waitForIdle()

        val glassCardTitleBounds = onNodeWithText("Glass & motion").getBoundsInRoot()

        val image = onRoot().captureToImage()
        assertNotNull(image)
        val pixelMap = image.toPixelMap()
        val scheme = colorSchemeFor(theme)

        // Sample background pixel inside the "Glass & motion" card
        // Sample slightly below the title where card glass is clear of text glyphs
        val cardSampleX = (glassCardTitleBounds.left.value + 40f).toInt().coerceIn(0, image.width - 1)
        val cardSampleY = (glassCardTitleBounds.bottom.value + 4f).toInt().coerceIn(0, image.height - 1)

        val sampledCardBg = pixelMap[cardSampleX, cardSampleY]
        val onSurfaceCardRatio = ColorContrast.contrastRatio(scheme.onSurface, sampledCardBg)
        val onSurfaceVariantCardRatio = ColorContrast.contrastRatio(scheme.onSurfaceVariant, sampledCardBg)

        // Sample unboxed background pixel behind the header text
        // Sample near top of central column
        val unboxedSampleX = headerX.coerceIn(0, image.width - 1)
        val unboxedSampleY = (headerY - 15).coerceIn(0, image.height - 1)
        val sampledUnboxedBg = pixelMap[unboxedSampleX, unboxedSampleY]
        val unboxedHeadlineRatio = ColorContrast.contrastRatio(scheme.onSurface, sampledUnboxedBg)
        val unboxedSubtitleRatio = ColorContrast.contrastRatio(scheme.onSurfaceVariant, sampledUnboxedBg)

        println(
            """
            |---------------------------------------------------------------------------------
            |[SETTINGS DIAGNOSTIC] Theme: ${theme.id} (EffectsEnabled=$effectsEnabled)
            |  Glass & Motion Card:
            |    Sampled Bg Color: $sampledCardBg at ($cardSampleX, $cardSampleY)
            |    Title/OnSurface Contrast: $onSurfaceCardRatio:1 (WCAG AA >= 4.5:1)
            |    Subtitle/OnSurfaceVariant Contrast: $onSurfaceVariantCardRatio:1 (WCAG AA >= 4.5:1)
            |  Unboxed Header:
            |    Sampled Bg Color: $sampledUnboxedBg at ($unboxedSampleX, $unboxedSampleY)
            |    Headline Contrast: $unboxedHeadlineRatio:1 (WCAG AA >= 4.5:1)
            |    Subtitle Contrast: $unboxedSubtitleRatio:1 (WCAG AA >= 4.5:1)
            |---------------------------------------------------------------------------------
            """.trimMargin(),
        )

        // Assert WCAG AA standard (>= 4.5:1 for body and normal text, >= 3.0:1 for large text/headlines)
        assertTrue(
            onSurfaceCardRatio >= 4.5,
            "Theme ${theme.id} card onSurface contrast must meet WCAG AA >= 4.5:1 (found: $onSurfaceCardRatio:1)",
        )
        assertTrue(
            onSurfaceVariantCardRatio >= 4.5,
            "Theme ${theme.id} card onSurfaceVariant contrast must meet WCAG AA >= 4.5:1 " +
                "(found: $onSurfaceVariantCardRatio:1)",
        )
        assertTrue(
            unboxedHeadlineRatio >= 4.5,
            "Theme ${theme.id} unboxed headline contrast must meet WCAG AA >= 4.5:1 (found: $unboxedHeadlineRatio:1)",
        )
        assertTrue(
            unboxedSubtitleRatio >= 4.5,
            "Theme ${theme.id} unboxed subtitle contrast must meet WCAG AA >= 4.5:1 (found: $unboxedSubtitleRatio:1)",
        )
    }

    @Test
    fun testCompactViewportScrollsToGlassAndMotionCard() = runDesktopComposeUiTest(
        width = 900,
        height = 600,
    ) {
        val settingsState = AppSettingsState(
            initialTheme = AppSettingsState.THEME_BLUE,
            initialEffectsEnabled = true,
        )

        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
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

        waitForIdle()
        // At 900x600, "Glass & motion" card is initially below the fold.
        // Verify scrolling reaches and displays the card.
        onNodeWithTag("GlassCard_glass_and_motion").performScrollTo().assertIsDisplayed()
        onNodeWithText("Glass & motion").assertIsDisplayed()
        onNodeWithText("Glass effects").assertIsDisplayed()
    }

    @Test
    fun testSettingsRightRailPresenceAndGeometryAtWideViewport() = runDesktopComposeUiTest(
        width = 1120,
        height = 700,
    ) {
        val settingsState = AppSettingsState(
            initialTheme = AppSettingsState.THEME_BLUE,
            initialEffectsEnabled = true,
        )

        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
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

        waitForIdle()
        onNodeWithTag("SettingsRightRail").assertExists()
        val bounds = onNodeWithTag("SettingsRightRail").getBoundsInRoot()
        val railWidth = (bounds.right - bounds.left).value
        kotlin.test.assertEquals(
            expected = GlassDimens.CompactRailWidth.value,
            actual = railWidth,
            absoluteTolerance = 0.5f,
            message = "Settings right rail must be 44 dp wide",
        )
    }
}
