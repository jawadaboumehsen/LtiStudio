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
import androidx.compose.runtime.CompositionLocalProvider
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
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.LocalHazePerformanceMode
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.InteractionColors
import org.ide.lti.core.testing.screenshot.GoldenImageAssert
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression coverage for coordinate stability and observable rendering across
 * [HazePerformanceMode] profiles. Proves that lower quality profiles sample the
 * exact same real-world region as high quality rather than a cropped or shifted corner.
 */
class DrawBackdropInputScaleScreenshotTest {

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
    private fun ScaledGlassBox(hazeState: HazeState, modifier: Modifier = Modifier) {
        val tintColor = InteractionColors.PressHighlight.copy(alpha = AlphaTokens.Subtle)
        val style = GlassStyle.regular.then {
            shape(RoundedCornerShape(CornerRadius.Medium))
            tint(tintColor)
        }
        Box(
            modifier = modifier.hazeGlass(
                input = HazeInput.Backdrop(hazeState),
                style = style,
            ),
        )
    }

    @OptIn(ExperimentalTestApi::class)
    private fun captureAtMode(mode: HazePerformanceMode): ImageBitmap {
        lateinit var captured: ImageBitmap
        runDesktopComposeUiTest(width = TEST_WINDOW_WIDTH, height = TEST_WINDOW_HEIGHT) {
            setContent {
                val hazeState = rememberHazeState()
                CompositionLocalProvider(LocalHazePerformanceMode provides mode) {
                    Box(Modifier.fillMaxSize()) {
                        PatternedBackground(Modifier.fillMaxSize().hazeSource(hazeState))
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ScaledGlassBox(
                                hazeState,
                                Modifier.size(ComponentSize.MinPanelWidth, ComponentSize.ActionCardHeight),
                            )
                        }
                    }
                }
            }
            captured = onRoot().captureToImage()
        }
        return captured
    }

    @Test
    fun qualityModeMatchesBaseline() {
        val image = captureAtMode(HazePerformanceMode.Quality)
        GoldenImageAssert.assertMatchesBaseline("draw_backdrop_input_scale_1_0", image)
    }

    @Test
    fun performanceModeSamplesSameRegionAsQuality() {
        val quality = captureAtMode(HazePerformanceMode.Quality)
        val performance = captureAtMode(HazePerformanceMode.Performance)

        val qualityMean = meanColorOfGlassBox(quality)
        val performanceMean = meanColorOfGlassBox(performance)
        assertChannelsClose("mean color", qualityMean, performanceMean, maxDelta = 40f)

        val performanceStdDev = stdDevOfGlassBox(performance)
        assertTrue(
            performanceStdDev > MIN_EXPECTED_STD_DEV,
            "Performance mode glass box has near-zero color variance ($performanceStdDev) - " +
                "looks like it sampled a single flat region instead of the full patchwork background",
        )
    }

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
        const val MIN_EXPECTED_STD_DEV = 0.02f
        const val TEST_WINDOW_WIDTH = 400
        const val TEST_WINDOW_HEIGHT = 240
        const val GLASS_BOX_WIDTH = 180
        const val GLASS_BOX_HEIGHT = 120
        const val EDGE_INSET = 20
    }
}
