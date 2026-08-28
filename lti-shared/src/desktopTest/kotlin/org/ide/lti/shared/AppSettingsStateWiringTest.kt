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

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.TimeZone
import org.ide.lti.core.data.settings.AppSettingsState
import org.ide.lti.core.data.utils.NetworkMonitor
import org.ide.lti.core.data.utils.TimeZoneMonitor
import org.ide.lti.feature.settings.SettingsScreenContent
import org.ide.lti.shared.di.initKoin
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.stopKoin
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppSettingsStateWiringTest {

    private class FakeNetworkMonitor : NetworkMonitor {
        override val isOnline: Flow<Boolean> = flowOf(true)
    }

    private class FakeTimeZoneMonitor : TimeZoneMonitor {
        override val currentTimeZone: Flow<TimeZone> = flowOf(TimeZone.currentSystemDefault())
    }

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
    fun testFluidEffectsToggleChangesRendering() = runDesktopComposeUiTest(width = 800, height = 600) {
        val settingsState = AppSettingsState(
            initialDarkTheme = true,
            initialEffectsEnabled = true,
        )

        setContent {
            LtiSharedApp(
                networkMonitor = FakeNetworkMonitor(),
                timeZoneMonitor = FakeTimeZoneMonitor(),
                appSettingsState = settingsState,
            )
        }

        waitForIdle()
        val imageEffectsOn = onRoot().captureToImage()

        // Toggle fluid glass effects OFF
        settingsState.effectsEnabled = false
        waitForIdle()
        val imageEffectsOff = onRoot().captureToImage()

        val (diffPixels, diffPercent) = computePixelDiffPercent(imageEffectsOn, imageEffectsOff)
        val total = imageEffectsOn.width * imageEffectsOn.height
        val formattedEffects = "%.4f".format(diffPercent)
        println(
            "[VERIFICATION] Fluid Glass Effects toggle diff: $diffPixels / $total pixels ($formattedEffects%)"
        )

        assertTrue(
            diffPercent > 0.0,
            "Toggling effectsEnabled must cause a visual rendering difference (found $diffPercent% diff)",
        )
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
                networkMonitor = FakeNetworkMonitor(),
                timeZoneMonitor = FakeTimeZoneMonitor(),
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
            "[VERIFICATION] Dark Mode toggle diff: $diffPixels / $total pixels ($formattedDark%)"
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
}
