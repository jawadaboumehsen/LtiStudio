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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Proves [GlassStyle] actually drives a real Haze glass effect - not just that it type
 * checks - by rendering a style through `Modifier.hazeGlass` over high-frequency stripes.
 */
class GlassStyleToEffectsScreenshotTest {

    @OptIn(ExperimentalTestApi::class)
    private fun captureStripes(applyStyle: Boolean): ImageBitmap {
        lateinit var captured: ImageBitmap
        runDesktopComposeUiTest(width = TEST_WIDTH, height = TEST_HEIGHT) {
            setContent {
                val hazeState = rememberHazeState()
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) { drawStripes() }
                    if (applyStyle) {
                        val style = GlassStyle {
                            shape(RoundedCornerShape(CornerRadius.Medium))
                            optics(blurRadius = BLUR_RADIUS)
                        }
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .size(ComponentSize.MinPanelWidth, ComponentSize.ActionCardHeight)
                                .hazeGlass(
                                    input = HazeInput.Backdrop(hazeState),
                                    style = style,
                                ),
                        )
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

    @Test
    fun toEffectsProducesAVisiblyBlurredSurface() {
        val flat = captureStripes(applyStyle = false)
        val styled = captureStripes(applyStyle = true)

        val flatVariance = stripeVariance(flat)
        val styledVariance = stripeVariance(styled)
        assertTrue(
            styledVariance < flatVariance * VARIANCE_REDUCTION_CEILING,
            "GlassStyle with blur did not visibly soften the sampled stripes " +
                "(flat variance=$flatVariance, styled variance=$styledVariance) - blur may not be applying",
        )
    }

    private fun stripeVariance(image: ImageBitmap): Double {
        val pixelMap = image.toPixelMap()
        val y = image.height / 2
        val values = (0 until image.width).map { x -> pixelMap[x, y].red.toDouble() }
        val mean = values.average()
        return values.sumOf { (it - mean) * (it - mean) } / values.size
    }

    private companion object {
        const val TEST_WIDTH = 200
        const val TEST_HEIGHT = 140
        const val STRIPE_COUNT = 10
        val BLUR_RADIUS: Dp = 16.dp
        const val VARIANCE_REDUCTION_CEILING = 0.6
    }
}
