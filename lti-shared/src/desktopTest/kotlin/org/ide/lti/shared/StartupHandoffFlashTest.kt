/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.shared

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.ide.lti.shared.di.initKoin
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.stopKoin
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class StartupHandoffFlashTest {

    @Before
    fun setUp() {
        stopKoin()
        initKoin()
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun testFastStartWithoutSplashBypassesSplashImmediately() = runDesktopComposeUiTest(
        width = 1120,
        height = 700,
    ) {
        setContent {
            // Calling LtiSharedApp() directly renders Setup without splash delay
            LtiSharedApp()
        }
        // Assert frame 1 composition directly without waiting for coroutine idle
        onNodeWithTag("SplashCard").assertDoesNotExist()
        onNodeWithTag("GlobalAppTopBar").assertIsDisplayed()
    }

    @Test
    fun testDirectStartupRendersFrame1WithoutBlankPixels() = runDesktopComposeUiTest(
        width = 1120,
        height = 700,
    ) {
        setContent {
            LtiSharedApp()
        }
        // Immediately capture frame 1 without waitForIdle()
        onNodeWithTag("GlobalAppTopBar").assertIsDisplayed()

        val image = onRoot().captureToImage()
        val pixels = image.toPixelMap()

        val samplePoints = listOf(
            Pair(60, 28),
            Pair(560, 28),
            Pair(1050, 28),
            Pair(100, 350),
            Pair(560, 350),
            Pair(560, 550),
            Pair(560, 680),
            Pair(1090, 680),
        )

        for (pt in samplePoints) {
            val (x, y) = pt
            val pixel = pixels[x, y]
            assertTrue(pixel.alpha > 0.8f, "Pixel at ($x, $y) must be fully rendered (alpha=${pixel.alpha})")
            assertTrue(pixel.red + pixel.green + pixel.blue > 0.05f, "Pixel at ($x, $y) must not be blank black")
        }
    }
}
