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
import androidx.compose.material3.Text
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
import dev.chrisbanes.haze.hazeSource
import org.ide.lti.core.designsystem.component.primitives.GlassSurface
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Proves that glass surfaces drive real optical blur when a haze source is present,
 * and provide solid, non-transparent fallback rendering when unbacked.
 */
class ModifierGlassScreenshotTest {

    @OptIn(ExperimentalTestApi::class)
    private fun captureStripes(withSource: Boolean): ImageBitmap {
        lateinit var captured: ImageBitmap
        runDesktopComposeUiTest(width = TEST_WIDTH, height = TEST_HEIGHT) {
            setContent {
                LtiTheme(appTheme = AppTheme.Dark) {
                    Box(Modifier.fillMaxSize()) {
                        if (withSource) {
                            Canvas(Modifier.fillMaxSize().hazeSource(GlassTheme.hazeState)) {
                                drawStripes()
                            }
                        }
                        GlassSurface(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(ComponentSize.MinPanelWidth, ComponentSize.ActionCardHeight),
                            surfaceColor = Color(0xFF1E222B),
                        ) {
                            Text("Fallback")
                        }
                    }
                }
            }
            waitForIdle()
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

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun glassWithSourceVisiblyBlursTheSampledStripes() {
        // Compare sharp stripes directly against glass surface over stripes
        lateinit var sharpStripes: ImageBitmap
        runDesktopComposeUiTest(width = TEST_WIDTH, height = TEST_HEIGHT) {
            setContent {
                Canvas(Modifier.fillMaxSize()) { drawStripes() }
            }
            sharpStripes = onRoot().captureToImage()
        }

        val sharpVariance = stripeVariance(sharpStripes)
        val glassVariance = stripeVariance(captureStripes(withSource = true))
        assertTrue(
            glassVariance < sharpVariance * VARIANCE_REDUCTION_CEILING,
            "GlassSurface did not visibly soften the sampled stripes " +
                "(sharp=$sharpVariance, glass=$glassVariance)",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun unbackedFallbackDrawsAnOpaqueSurfaceNotTransparentOrBlack() {
        val image = captureStripes(withSource = false)
        val pixelMap = image.toPixelMap()
        val centerColor = pixelMap[image.width / 2, image.height / 2]
        assertTrue(
            centerColor.alpha > MIN_OPAQUE_ALPHA,
            "Fallback should draw an opaque surface, was alpha=${centerColor.alpha}",
        )
        assertTrue(
            centerColor.red > 0f || centerColor.green > 0f || centerColor.blue > 0f,
            "Fallback drew pure black - surface color missing",
        )
    }

    private companion object {
        const val TEST_WIDTH = 200
        const val TEST_HEIGHT = 140
        const val STRIPE_COUNT = 10
        const val VARIANCE_REDUCTION_CEILING = 0.7
        const val MIN_OPAQUE_ALPHA = 0.8f
    }
}
