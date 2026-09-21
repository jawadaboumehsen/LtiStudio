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
import org.ide.lti.core.testing.screenshot.GoldenImageAssert
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * GlassDialog wraps its content in `androidx.compose.ui.window.Dialog`, which opens a separate
 * platform window - `runDesktopComposeUiTest`'s `onRoot().captureToImage()` only captures the
 * test's own root, not that window's content, so GlassDialog itself can't be golden-tested
 * directly (confirmed: no existing test in this codebase captures a real Dialog-wrapped
 * composable). This tests the actual thing that changed - GlassStyles.dialog resolving through
 * Modifier.glass, including the colorControls brightness/saturation call GlassDialog's old
 * `effects` lambda had that plain GlassStyle.toEffects() didn't reproduce until now - directly,
 * the same way GlassStyleToEffectsScreenshotTest verifies `card` without needing GlassCard itself.
 */
class GlassStylesDialogScreenshotTest {

    @OptIn(ExperimentalTestApi::class)
    private fun captureStripes(applyStyle: Boolean): ImageBitmap {
        lateinit var captured: ImageBitmap
        runDesktopComposeUiTest(width = TEST_WIDTH, height = TEST_HEIGHT) {
            setContent {
                LtiTheme(darkTheme = true) {
                    val backdrop = rememberGlassSceneState()
                    Box(Modifier.fillMaxSize()) {
                        Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop.engineBackdrop)) { drawStripes() }
                        if (applyStyle) {
                            Box(
                                Modifier
                                    .align(Alignment.Center)
                                    .size(ComponentSize.MinPanelWidth, ComponentSize.ActionCardHeight)
                                    .glass(GlassStyles.dialog, backdrop = backdrop),
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
            "GlassStyles.dialog did not visibly soften the sampled stripes " +
                "(flat=$flatVariance, styled=$styledVariance) - colorControls may have broken the effect chain",
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
