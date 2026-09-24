/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.testing.screenshot.GoldenImageAssert
import kotlin.test.Test
import kotlin.test.assertTrue

class GlassStylesDialogScreenshotTest {

    @OptIn(ExperimentalTestApi::class)
    private fun captureStripes(applyStyle: Boolean): ImageBitmap {
        lateinit var captured: ImageBitmap
        runDesktopComposeUiTest(width = TEST_WIDTH, height = TEST_HEIGHT) {
            setContent {
                LtiTheme(appTheme = AppTheme.Dark) {
                    val colors = MaterialTheme.colorScheme
                    val shape = GlassShapes.HazePanel
                    Box(Modifier.fillMaxSize()) {
                        Canvas(Modifier.fillMaxSize().hazeSource(GlassTheme.hazeState)) { drawStripes() }
                        if (applyStyle) {
                            Box(
                                Modifier
                                    .align(Alignment.Center)
                                    .size(ComponentSize.MinPanelWidth, ComponentSize.ActionCardHeight)
                                    .hazeGlass(
                                        input = HazeInput.Backdrop(GlassTheme.hazeState),
                                        style = remember(shape, colors.surfaceContainerHighest) {
                                            GlassStyle.regular.then {
                                                this.shape(shape)
                                                backgroundColor(colors.surfaceContainerHighest)
                                            }
                                        },
                                    )
                                    .clip(shape),
                            )
                        }
                    }
                }
            }
            captured = onRoot().captureToImage()
        }
        return captured
    }

    private fun DrawScope.drawStripes() {
        val stripeWidth = size.width / STRIPE_COUNT
        for (i in 0 until STRIPE_COUNT) {
            val color = if (i % 2 == 0) Color.Black else Color.White
            drawRect(color, topLeft = Offset(i * stripeWidth, 0f), size = size.copy(width = stripeWidth))
        }
    }

    private fun stripeVariance(image: ImageBitmap): Double {
        val pixelMap = image.toPixelMap()
        val y = image.height / 2
        val values = (0 until image.width).map { x -> pixelMap[x, y].red.toDouble() }
        val mean = values.average()
        return values.sumOf { (it - mean) * (it - mean) } / values.size
    }

    @Test
    fun dialogStyleVisiblyBlursAndDoesNotCrashWithColorControls() {
        val flatVariance = stripeVariance(captureStripes(applyStyle = false))
        val styledVariance = stripeVariance(captureStripes(applyStyle = true))
        assertTrue(
            styledVariance < flatVariance * VARIANCE_REDUCTION_CEILING,
            "Dialog glass style did not visibly soften the sampled stripes " +
                "(flat=$flatVariance, styled=$styledVariance)",
        )
    }

    @Test
    fun dialogStyleRenders() {
        val image = captureStripes(applyStyle = true)
        GoldenImageAssert.assertMatchesBaseline("glass_styles_dialog", image)
    }

    private companion object {
        const val TEST_WIDTH = 240
        const val TEST_HEIGHT = 160
        const val STRIPE_COUNT = 10
        const val VARIANCE_REDUCTION_CEILING = 0.7
    }
}
