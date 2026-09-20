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
import com.kashif_e.backdrop.OpticalSizeValue
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.drawBackdrop
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Proves [GlassStyle.toEffects] actually drives a real backdrop draw - not just that it type
 * checks - by rendering a style through the existing `Modifier.drawBackdrop` directly (the
 * `Modifier.glass(style)` resolution function from backdrop-refactor-plan-20260920.md §1.2 that
 * will normally call this doesn't exist yet).
 */
class GlassStyleToEffectsScreenshotTest {

    @OptIn(ExperimentalTestApi::class)
    private fun captureStripes(applyStyle: Boolean): ImageBitmap {
        lateinit var captured: ImageBitmap
        runDesktopComposeUiTest(width = TEST_WIDTH, height = TEST_HEIGHT) {
            setContent {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) { drawStripes() }
                    if (applyStyle) {
                        val style = GlassStyle(
                            surface = Color.Transparent,
                            shape = RoundedCornerShape(CornerRadius.Medium),
                            blur = OpticalSizeValue.Fixed(BLUR_RADIUS),
                            vibrancy = false,
                        )
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .size(ComponentSize.MinPanelWidth, ComponentSize.ActionCardHeight)
                                .drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { style.shape },
                                    effects = style.toEffects(),
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

        // A blurred region has much lower local variance than the sharp stripes it sampled -
        // compare the same central row on both captures.
        val flatVariance = stripeVariance(flat)
        val styledVariance = stripeVariance(styled)
        assertTrue(
            styledVariance < flatVariance * VARIANCE_REDUCTION_CEILING,
            "GlassStyle.toEffects() did not visibly soften the sampled stripes " +
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
        val BLUR_RADIUS: Dp = Dp(16f)
        const val VARIANCE_REDUCTION_CEILING = 0.6
    }
}
