/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.screenshot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.drawBackdrop
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.glass.GlassSceneState
import org.ide.lti.core.designsystem.theme.glass.GlassStyles
import org.ide.lti.core.designsystem.theme.glass.rememberGlassSceneState
import org.ide.lti.core.designsystem.theme.glass.toEffects
import org.ide.lti.core.testing.screenshot.GoldenImageAssert
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression coverage for `inputScale` (backdrop-refactor-plan-20260920.md B1): records the
 * sampled backdrop at a fraction of the element's pixel size, then scales the layer back up to
 * fill it. At `1f` this must be pixel-identical to the pre-B1 path (already covered by every
 * other screenshot test staying at 0.0000% diff). This test instead proves the *scaled-down*
 * path (`0.5f`) samples the same real-world region as `1f` - not a cropped corner of it, which is
 * the failure mode a naive "just record a smaller canvas" implementation would produce, since the
 * ambient [GlassSceneState] draws its captured content at native resolution and relies on the
 * recording canvas itself being pre-scaled.
 */
class DrawBackdropInputScaleScreenshotTest {

    /** A patterned (non-flat) background: a flat diff on a cropped corner would fail this. */
    @Composable
    private fun PatternedBackground(modifier: Modifier = Modifier) {
        Canvas(modifier) {
            drawPatchwork(this)
        }
    }

    private fun drawPatchwork(scope: DrawScope) = with(scope) {
        val cols = 6
        val rows = 4
        val cellWidth = size.width / cols
        val cellHeight = size.height / rows
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val hue = ((row * cols + col) * 37) % 360
                drawRect(
                    color = Color.hsv(hue.toFloat(), 0.65f, 0.9f),
                    topLeft = Offset(col * cellWidth, row * cellHeight),
                    size = Size(cellWidth, cellHeight),
                )
            }
        }
    }

    @Composable
    private fun ScaledGlassBox(backdrop: GlassSceneState, inputScale: Float, modifier: Modifier = Modifier) {
        val tintColor = GlassTheme.colors.onGlass.copy(alpha = AlphaTokens.Subtle)
        val iconButtonStyle = GlassStyles.iconButton
        Box(
            modifier = modifier.drawBackdrop(
                backdrop = backdrop.engineBackdrop,
                shape = { RoundedCornerShape(CornerRadius.Medium) },
                effects = iconButtonStyle.toEffects(),
                inputScale = inputScale,
                onDrawSurface = { drawRect(tintColor) },
            ),
        )
    }

    @OptIn(ExperimentalTestApi::class)
    private fun captureAtScale(inputScale: Float): ImageBitmap {
        lateinit var captured: ImageBitmap
        runDesktopComposeUiTest(width = TEST_WINDOW_WIDTH, height = TEST_WINDOW_HEIGHT) {
            setContent {
                val backdrop = rememberGlassSceneState()
                Box(Modifier.fillMaxSize()) {
                    PatternedBackground(Modifier.fillMaxSize().layerBackdrop(backdrop.engineBackdrop))
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ScaledGlassBox(
                            backdrop,
                            inputScale,
                            Modifier.size(ComponentSize.MinPanelWidth, ComponentSize.ActionCardHeight),
                        )
                    }
                }
            }
            captured = onRoot().captureToImage()
        }
        return captured
    }

    @Test
    fun inputScaleDefaultMatchesFullResolutionBaseline() {
        val image = captureAtScale(1f)
        GoldenImageAssert.assertMatchesBaseline("draw_backdrop_input_scale_1_0", image)
    }

    @Test
    fun inputScaleHalfSamplesSameRegionAsFullResolution() {
        // Compared directly against the 1f capture, not a saved baseline: a baseline file would
        // just canonicalize whatever this test produces on its first run, silently passing even
        // if the 0.5f path sampled the wrong region entirely.
        val full = captureAtScale(1f)
        val half = captureAtScale(0.5f)

        val fullMean = meanColorOfGlassBox(full)
        val halfMean = meanColorOfGlassBox(half)
        assertChannelsClose("mean color", fullMean, halfMean, maxDelta = 40f)

        // A "sampled only the top-left corner" bug would flatten the box to a near-uniform hue;
        // the real patchwork keeps meaningful color spread even after a heavier, lower-res blur.
        val halfStdDev = stdDevOfGlassBox(half)
        assertTrue(
            halfStdDev > MIN_EXPECTED_STD_DEV,
            "inputScale=0.5f glass box has near-zero color variance ($halfStdDev) - " +
                "looks like it sampled a single flat region instead of the full patchwork background",
        )
    }

    /** Box(140dp, 90dp) centered in a 240x160 test window, minus a margin to avoid edge blur/AA. */
    private fun meanColorOfGlassBox(image: ImageBitmap): FloatArray = sampleGlassBox(image) { r, g, b, count ->
        floatArrayOf(r / count, g / count, b / count)
    }

    private fun stdDevOfGlassBox(image: ImageBitmap): Float {
        val pixelMap = image.toPixelMap()
        val bounds = glassBoxBounds(image)
        val samples = mutableListOf<Float>()
        for (y in bounds.first.second until bounds.second.second) {
            for (x in bounds.first.first until bounds.second.first) {
                val c = pixelMap[x, y]
                samples.add((c.red + c.green + c.blue) / 3f)
            }
        }
        val mean = samples.sum() / samples.size
        val variance = samples.sumOf { ((it - mean) * (it - mean)).toDouble() } / samples.size
        return kotlin.math.sqrt(variance).toFloat()
    }

    private inline fun sampleGlassBox(
        image: ImageBitmap,
        reduce: (Float, Float, Float, Float) -> FloatArray,
    ): FloatArray {
        val pixelMap = image.toPixelMap()
        val bounds = glassBoxBounds(image)
        var r = 0f
        var g = 0f
        var b = 0f
        var count = 0f
        for (y in bounds.first.second until bounds.second.second) {
            for (x in bounds.first.first until bounds.second.first) {
                val c = pixelMap[x, y]
                r += c.red
                g += c.green
                b += c.blue
                count += 1f
            }
        }
        return reduce(r, g, b, count)
    }

    /** Inset rectangle inside the centered glass box, avoiding its blurred/rounded edges. */
    private fun glassBoxBounds(image: ImageBitmap): Pair<Pair<Int, Int>, Pair<Int, Int>> {
        val boxWidth = GLASS_BOX_WIDTH
        val boxHeight = GLASS_BOX_HEIGHT
        val inset = EDGE_INSET
        val left = (image.width - boxWidth) / 2 + inset
        val top = (image.height - boxHeight) / 2 + inset
        val right = (image.width + boxWidth) / 2 - inset
        val bottom = (image.height + boxHeight) / 2 - inset
        return (left to top) to (right to bottom)
    }

    private fun assertChannelsClose(label: String, a: FloatArray, b: FloatArray, maxDelta: Float) {
        for (i in a.indices) {
            val delta = abs(a[i] - b[i]) * 255
            assertTrue(delta <= maxDelta, "$label channel $i differs by $delta (max $maxDelta): ${a[i]} vs ${b[i]}")
        }
    }

    private companion object {
        const val MIN_EXPECTED_STD_DEV = 0.03f
        const val TEST_WINDOW_WIDTH = 400
        const val TEST_WINDOW_HEIGHT = 240

        // Matches ComponentSize.MinPanelWidth / ComponentSize.ActionCardHeight (test density 1:1).
        const val GLASS_BOX_WIDTH = 180
        const val GLASS_BOX_HEIGHT = 120
        const val EDGE_INSET = 20
    }
}
