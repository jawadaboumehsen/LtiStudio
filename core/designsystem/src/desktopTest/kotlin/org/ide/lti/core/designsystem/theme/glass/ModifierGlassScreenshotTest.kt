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
import com.kashif_e.backdrop.backdrops.layerBackdrop
import org.ide.lti.core.designsystem.component.primitives.glass
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.glass.GlassSceneState
import org.ide.lti.core.designsystem.theme.glass.rememberGlassSceneState
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Proves `Modifier.glass(style)` (backdrop-refactor-plan-20260920.md §1.2) drives the real
 * backdrop when effects are enabled, and a real opaque fallback when they are not - through the
 * actual public facade (reading [org.ide.lti.core.designsystem.theme.GlassTheme] internally),
 * not by calling [com.kashif_e.backdrop.drawBackdrop] directly the way
 * `GlassStyleToEffectsScreenshotTest` does.
 */
class ModifierGlassScreenshotTest {

    @OptIn(ExperimentalTestApi::class)
    private fun captureStripes(effectsEnabled: Boolean): ImageBitmap {
        lateinit var captured: ImageBitmap
        runDesktopComposeUiTest(width = TEST_WIDTH, height = TEST_HEIGHT) {
            setContent {
                val backdrop = rememberGlassSceneState()
                LtiTheme(darkTheme = true, effectsEnabled = effectsEnabled, sharedScene = backdrop) {
                    Box(Modifier.fillMaxSize()) {
                        Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop.engineBackdrop)) { drawStripes() }
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .size(ComponentSize.MinPanelWidth, ComponentSize.ActionCardHeight)
                                .glass(GlassStyles.card),
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

    private fun stripeVariance(image: ImageBitmap): Double {
        val pixelMap = image.toPixelMap()
        val y = image.height / 2
        val values = (0 until image.width).map { x -> pixelMap[x, y].red.toDouble() }
        val mean = values.average()
        return values.sumOf { (it - mean) * (it - mean) } / values.size
    }

    @Test
    fun effectsEnabledVisiblyBlursTheSampledStripes() {
        val flat = captureStripes(effectsEnabled = false).let { stripeVariance(it) }
        val glassEnabled = stripeVariance(captureStripes(effectsEnabled = true))
        assertTrue(
            glassEnabled < flat * VARIANCE_REDUCTION_CEILING,
            "Modifier.glass(GlassStyles.card) did not visibly soften the sampled stripes " +
                "(disabled variance=$flat, enabled variance=$glassEnabled) - real backdrop path may not be reached",
        )
    }

    @Test
    fun effectsDisabledDrawsAnOpaqueFallbackNotTransparentOrBlack() {
        val image = captureStripes(effectsEnabled = false)
        val pixelMap = image.toPixelMap()
        val centerColor = pixelMap[image.width / 2, image.height / 2]
        assertTrue(
            centerColor.alpha > MIN_OPAQUE_ALPHA,
            "Modifier.glass(GlassStyles.card) fallback should draw an opaque surface, was alpha=${centerColor.alpha}",
        )
        assertTrue(
            centerColor.red > 0f || centerColor.green > 0f || centerColor.blue > 0f,
            "Modifier.glass(GlassStyles.card) fallback drew pure black - surface color likely missing",
        )
    }

    private companion object {
        const val TEST_WIDTH = 200
        const val TEST_HEIGHT = 140
        const val STRIPE_COUNT = 10

        // GlassStyles.card carries a real translucent glassTint layer (matching what GlassSurface
        // always substituted when a caller left tint unspecified - see GlassStyles.kt's card/
        // navigator/dialog roles), which flattens contrast somewhat even in the flat fallback
        // capture, so the proportional variance drop from blurring is smaller than a bare
        // stripe-on-stripe comparison would show. 0.7 still requires a real, visible softening.
        const val VARIANCE_REDUCTION_CEILING = 0.7
        const val MIN_OPAQUE_ALPHA = 0.9f
    }
}
