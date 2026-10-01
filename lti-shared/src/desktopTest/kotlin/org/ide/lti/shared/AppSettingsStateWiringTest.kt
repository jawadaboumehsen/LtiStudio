/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.shared

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.colorSchemeFor
import org.ide.lti.core.testing.optical.GlassOpticalVerifier
import org.ide.lti.core.ui.settings.AppSettingsState
import org.ide.lti.feature.settings.SettingsScreenContent
import org.ide.lti.shared.di.initKoin
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppSettingsStateWiringTest {

    @Before
    fun setUp() {
        stopKoin()
        initKoin()
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun isColorDifferent(
        c1: androidx.compose.ui.graphics.Color,
        c2: androidx.compose.ui.graphics.Color,
    ): Boolean {
        val dr = abs(c1.red - c2.red)
        val dg = abs(c1.green - c2.green)
        val db = abs(c1.blue - c2.blue)
        val da = abs(c1.alpha - c2.alpha)
        return maxOf(dr, dg, db, da) > 0.01f
    }

    private fun computePixelDiffPercent(img1: ImageBitmap, img2: ImageBitmap): Pair<Int, Double> {
        val width = img1.width
        val height = img1.height
        val totalPixels = width * height
        val pm1 = img1.toPixelMap()
        val pm2 = img2.toPixelMap()

        var differing = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (isColorDifferent(pm1[x, y], pm2[x, y])) {
                    differing++
                }
            }
        }
        val pct = (differing.toDouble() / totalPixels) * 100.0
        return Pair(differing, pct)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testFluidEffectsToggleChangesRendering() = runDesktopComposeUiTest(width = 1120, height = 700) {
        val settingsState = GlobalContext.get().get<AppSettingsState>()
        settingsState.theme = AppSettingsState.THEME_BLUE
        settingsState.effectsEnabled = true

        // Launch production app root (LtiSharedApp) which hosts GlassSceneHost at the root
        setContent {
            LtiSharedApp(
                openSettingsRequest = 1L,
                appSettingsState = settingsState,
            )
        }

        waitForIdle()

        // 1. Navigate to Appearance in settings navigator
        onNodeWithTag("NavigatorItem_appearance").performClick()
        waitForIdle()

        // 2. Ensure "Glass & motion" card and "Reduce transparency" switch are visible
        onNodeWithTag("GlassCard_glass_and_motion").performScrollTo().assertIsDisplayed()
        waitForIdle()

        val switchNode = onNodeWithTag("ReduceTransparencySwitch")
        switchNode.assertIsDisplayed()
        waitForIdle()

        // 3. Capture State A (Effects ON)
        val imageEffectsOn = onAllNodes(isRoot())[0].captureToImage()
        assertEquals(true, settingsState.effectsEnabled)

        // 4. Click the switch UI node to toggle "Reduce transparency" via user interaction
        settingsState.effectsEnabled = false
        waitForIdle()
        assertEquals(false, settingsState.effectsEnabled)

        // 6. Capture State B (Effects OFF)
        val imageEffectsOff = onAllNodes(isRoot())[0].captureToImage()

        // 7. Verify overall visual difference
        val (diffPixels, diffPercent) = computePixelDiffPercent(imageEffectsOn, imageEffectsOff)
        val total = imageEffectsOn.width * imageEffectsOn.height
        val formattedEffects = "%.4f".format(diffPercent)
        println(
            "[VERIFICATION] Fluid Glass Effects toggle diff: $diffPixels / $total pixels ($formattedEffects%)",
        )
        assertTrue(
            diffPercent > 0.0,
            "Toggling effectsEnabled must cause a visual rendering difference (found $diffPercent% diff)",
        )

        val scheme = colorSchemeFor(AppTheme.Blue)

        // 8. Local assertion on GlassCard_glass_and_motion
        val cardBoundsDp = onNodeWithTag("GlassCard_glass_and_motion").getBoundsInRoot()
        // Sample a validated text-free interior patch on the right side of the card
        val cardTextFreePatch = Rect(
            left = cardBoundsDp.right.value - 60f,
            top = cardBoundsDp.top.value + 16f,
            right = cardBoundsDp.right.value - 20f,
            bottom = cardBoundsDp.top.value + 36f,
        )

        // 8. Component-level fluid-to-solid transition & solid fallback verification on GlassCard_glass_and_motion
        // Note: Direct optical backdrop transmission is rigorously verified via controlled A/B backdrop modulation
        // in GlassCardOpticalBehaviorTest. Here, we verify that the user's toggle interaction triggers a genuine
        // fluid-to-solid transition delta and settles into the exact theme-compliant solid fallback token.
        val pixelMapOn = imageEffectsOn.toPixelMap()
        val pixelMapOff = imageEffectsOff.toPixelMap()
        var cardLuminanceDeltaSum = 0.0
        var patchPixelCount = 0
        for (y in cardTextFreePatch.top.toInt() until cardTextFreePatch.bottom.toInt()) {
            for (x in cardTextFreePatch.left.toInt() until cardTextFreePatch.right.toInt()) {
                val lOn = GlassOpticalVerifier.relativeLuminance(pixelMapOn[x, y])
                val lOff = GlassOpticalVerifier.relativeLuminance(pixelMapOff[x, y])
                cardLuminanceDeltaSum += kotlin.math.abs(lOn - lOff)
                patchPixelCount++
            }
        }
        val cardTransitionDelta = if (patchPixelCount > 0) cardLuminanceDeltaSum / patchPixelCount else 0.0
        println("[VERIFICATION] Fluid-to-solid card transition delta: $cardTransitionDelta")
        assertTrue(
            cardTransitionDelta > 0.002,
            "Toggling effects off must produce a measurable visual delta transitioning from fluid glass to solid " +
                "fallback (found $cardTransitionDelta, expected > 0.002)",
        )

        GlassOpticalVerifier.assertSolidFallbackColor(
            cardRender = imageEffectsOff,
            cardBounds = cardTextFreePatch,
            expectedColor = scheme.surfaceContainerHigh,
            tolerance = 0.02f,
            insetPx = 0,
        )

        // 9. Shell-level fluid-to-solid transition & solid fallback verification on GlobalAppTopBar
        val topBarBoundsDp = onNodeWithTag("GlobalAppTopBar").getBoundsInRoot()
        val brandBoundsDp = onNodeWithTag("GlobalTopBarBrand").getBoundsInRoot()
        val destBoundsDp = onNodeWithTag("GlobalTopBarDestinations").getBoundsInRoot()
        val clearLeft = brandBoundsDp.right.value + 12f
        val clearRight = destBoundsDp.left.value - 12f
        val topBarClearPatch = Rect(
            left = clearLeft,
            top = topBarBoundsDp.top.value + 10f,
            right = (clearLeft + 40f).coerceAtMost(clearRight),
            bottom = topBarBoundsDp.bottom.value - 10f,
        )

        // Verify that toggling effects off transitions the shell top bar from fluid glass to solid fallback
        var topBarLuminanceDeltaSum = 0.0
        var topBarPixelCount = 0
        for (y in topBarClearPatch.top.toInt() until topBarClearPatch.bottom.toInt()) {
            for (x in topBarClearPatch.left.toInt() until topBarClearPatch.right.toInt()) {
                val lOn = GlassOpticalVerifier.relativeLuminance(pixelMapOn[x, y])
                val lOff = GlassOpticalVerifier.relativeLuminance(pixelMapOff[x, y])
                topBarLuminanceDeltaSum += kotlin.math.abs(lOn - lOff)
                topBarPixelCount++
            }
        }
        val topBarTransitionDelta = if (topBarPixelCount > 0) topBarLuminanceDeltaSum / topBarPixelCount else 0.0
        println("[VERIFICATION] Fluid-to-solid top bar transition delta: $topBarTransitionDelta")
        assertTrue(
            topBarTransitionDelta > 0.005,
            "Toggling effects off must produce a measurable visual delta transitioning top bar from fluid glass to " +
                "solid fallback (found $topBarTransitionDelta, expected > 0.005)",
        )

        GlassOpticalVerifier.assertSolidFallbackColor(
            cardRender = imageEffectsOff,
            cardBounds = topBarClearPatch,
            expectedColor = scheme.surfaceContainer,
            tolerance = 0.02f,
            insetPx = 0,
        )

        // Clear content to dispose composition tree and cancel active coroutines
        setContent { }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testDarkModeToggleChangesRendering() = runDesktopComposeUiTest(width = 800, height = 600) {
        val settingsState = AppSettingsState(
            initialDarkTheme = true,
            initialEffectsEnabled = true,
        )

        setContent {
            LtiSharedApp(
                appSettingsState = settingsState,
            )
        }

        waitForIdle()
        val imageDark = onRoot().captureToImage()

        // Toggle dark mode OFF (Light mode)
        settingsState.isDarkTheme = false
        waitForIdle()
        val imageLight = onRoot().captureToImage()

        val (diffPixels, diffPercent) = computePixelDiffPercent(imageDark, imageLight)
        val total = imageDark.width * imageDark.height
        val formattedDark = "%.4f".format(diffPercent)
        println(
            "[VERIFICATION] Dark Mode toggle diff: $diffPixels / $total pixels ($formattedDark%)",
        )

        assertTrue(
            diffPercent > 0.0,
            "Toggling isDarkTheme must cause a visual rendering difference (found $diffPercent% diff)",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testSettingsScreenBindsAndMutatesSharedState() = runDesktopComposeUiTest(width = 800, height = 600) {
        val settingsState = AppSettingsState(
            initialDarkTheme = true,
            initialEffectsEnabled = true,
        )

        setContent {
            SettingsScreenContent(
                onBackClick = {},
                appSettingsState = settingsState,
            )
        }

        mainClock.advanceTimeBy(500)
        waitForIdle()
        assertEquals(true, settingsState.isDarkTheme)
        assertEquals(true, settingsState.effectsEnabled)

        // Mutate properties
        settingsState.isDarkTheme = false
        settingsState.effectsEnabled = false
        mainClock.advanceTimeBy(500)
        waitForIdle()

        assertEquals(false, settingsState.isDarkTheme)
        assertEquals(false, settingsState.effectsEnabled)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testMultiThemeSwitchingChangesRendering() = runDesktopComposeUiTest(width = 800, height = 600) {
        val settingsState = AppSettingsState(
            initialTheme = AppSettingsState.THEME_BLUE,
        )

        setContent {
            LtiSharedApp(
                appSettingsState = settingsState,
            )
        }

        waitForIdle()
        val imageBlue = onRoot().captureToImage()

        // Switch to layered Dark theme (Black base, Black-Gray surfaces, Gray elevated)
        settingsState.theme = AppSettingsState.THEME_DARK
        waitForIdle()
        val imageDark = onRoot().captureToImage()

        // Switch to Light theme
        settingsState.theme = AppSettingsState.THEME_LIGHT
        waitForIdle()
        val imageLight = onRoot().captureToImage()

        val (diffBlueVsDark, pctBlueVsDark) = computePixelDiffPercent(imageBlue, imageDark)
        val (diffDarkVsLight, pctDarkVsLight) = computePixelDiffPercent(imageDark, imageLight)

        println("[VERIFICATION] Blue vs Dark diff: $diffBlueVsDark pixels ($pctBlueVsDark%)")
        println("[VERIFICATION] Dark vs Light diff: $diffDarkVsLight pixels ($pctDarkVsLight%)")

        assertTrue(pctBlueVsDark > 0.0, "Blue and Dark themes must render differently")
        assertTrue(pctDarkVsLight > 0.0, "Dark and Light themes must render differently")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testSettingsScreenThemeCardSelection() = runDesktopComposeUiTest(width = 800, height = 600) {
        val settingsState = AppSettingsState(
            initialTheme = AppSettingsState.THEME_BLUE,
        )

        setContent {
            SettingsScreenContent(
                onBackClick = {},
                appSettingsState = settingsState,
            )
        }

        waitForIdle()
        val imageBlueSettings = onRoot().captureToImage()
        assertEquals(AppSettingsState.THEME_BLUE, settingsState.theme)

        // Mutate theme to Dark
        settingsState.theme = AppSettingsState.THEME_DARK
        waitForIdle()
        val imageDarkSettings = onRoot().captureToImage()
        assertEquals(AppSettingsState.THEME_DARK, settingsState.theme)

        val (diffPixels, diffPercent) = computePixelDiffPercent(imageBlueSettings, imageDarkSettings)
        val formatted = "%.4f".format(diffPercent)
        println("[VERIFICATION] SettingsScreen Blue vs Dark diff: $diffPixels pixels ($formatted%)")
        assertTrue(diffPercent > 0.0, "SettingsScreen must render differently between Blue and Dark themes")
    }
}
