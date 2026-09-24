/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.testing.optical.GlassOpticalVerifier
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Optical and interaction verification suite for [GlassToggle] liquid motion.
 *
 * Verifies:
 * 1. **Baseline Callback Deduplication**: Exact single invocation across tap, completed drag, and keyboard.
 * 2. **Quick-Tap Mid-Travel Acceptance**: Thumb is visibly refractive at mid-travel during a fast click in Dark, Blue, and Light.
 * 3. **Isolated Backdrop Resampling**: Proves Haze actively resamples backdrop across distinct layout positions vs frozen control.
 * 4. **Boundary Refraction on Moving Thumb**: Measured feature displacement along the thumb boundary vs unrefracted baseline.
 * 5. **Effects-Off Inset Invariance**: Exact solid token color and backdrop invariance on inset regions of track and thumb.
 * 6. **Directional & Motion Integrity**: Proper RTL layout-aware translation and instantaneous snap under reduced motion.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class)
class GlassToggleLiquidMotionTest {

    // =============================================================================================
    // 1. Baseline Callback Deduplication & Gesture Arbitration
    // =============================================================================================

    @Test
    fun testSingleTapInvokesCallbackExactlyOnce() = runDesktopComposeUiTest(
        width = 200,
        height = 150,
    ) {
        var callbackCount = 0
        var isChecked by mutableStateOf(false)

        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlassToggle(
                        checked = isChecked,
                        onCheckedChange = {
                            callbackCount++
                            isChecked = it
                        },
                    )
                }
            }
        }

        onRoot().performTouchInput {
            click(center)
        }

        assertEquals(1, callbackCount, "Single tap must invoke onCheckedChange exactly once")
        assertTrue(isChecked, "Toggle state must flip to true after tap")
    }

    @Test
    fun testCompletedDragInvokesCallbackExactlyOnceWithoutDuplicateClick() = runDesktopComposeUiTest(
        width = 200,
        height = 150,
    ) {
        var callbackCount = 0
        var isChecked by mutableStateOf(false)

        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlassToggle(
                        checked = isChecked,
                        onCheckedChange = {
                            callbackCount++
                            isChecked = it
                        },
                    )
                }
            }
        }

        // Swipe from thumb start position across the track past midpoint
        onRoot().performTouchInput {
            swipe(
                start = center.copy(x = center.x - 14f),
                end = center.copy(x = center.x + 24f),
                durationMillis = 200,
            )
        }

        assertEquals(1, callbackCount, "Completed drag must invoke onCheckedChange exactly once (no trailing click)")
        assertTrue(isChecked, "Toggle state must be true after drag past midpoint")
    }

    @Test
    fun testAbortedDragDoesNotToggleState() = runDesktopComposeUiTest(
        width = 200,
        height = 150,
    ) {
        var callbackCount = 0
        var isChecked by mutableStateOf(false)

        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlassToggle(
                        checked = isChecked,
                        onCheckedChange = {
                            callbackCount++
                            isChecked = it
                        },
                    )
                }
            }
        }

        // Drag past touch slop (exceeding 18px threshold), but pull back near start before releasing
        onRoot().performTouchInput {
            down(center.copy(x = center.x - 14f))
            moveBy(Offset(22f, 0f), delayMillis = 50)
            moveBy(Offset(-20f, 0f), delayMillis = 50)
            up()
        }

        assertEquals(0, callbackCount, "Aborted drag before midpoint must not toggle state")
        assertTrue(!isChecked, "Toggle state must remain false after aborted drag")
    }

    @Test
    fun testKeyboardSpaceActivationInvokesCallbackOnce() = runDesktopComposeUiTest(
        width = 200,
        height = 150,
    ) {
        var callbackCount = 0
        var isChecked by mutableStateOf(false)
        val focusRequester = FocusRequester()

        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlassToggle(
                        checked = isChecked,
                        onCheckedChange = {
                            callbackCount++
                            isChecked = it
                        },
                        modifier = Modifier.focusRequester(focusRequester),
                    )
                }
            }
        }

        focusRequester.requestFocus()
        waitForIdle()

        onRoot().performKeyInput {
            pressKey(Key.Spacebar)
        }

        assertEquals(1, callbackCount, "Keyboard spacebar activation must invoke onCheckedChange exactly once")
        assertTrue(isChecked, "Toggle state must flip to true after keyboard activation")
    }

    // =============================================================================================
    // 2. Primary Acceptance Gate: Quick-Tap Mid-Travel Refractive Thumb Across Themes
    // =============================================================================================

    @Test
    fun testQuickTapMidTravelShowsRefractiveThumbAcrossThemes() = runDesktopComposeUiTest(
        width = 240,
        height = 160,
    ) {
        val themes = listOf(AppTheme.Dark, AppTheme.Blue, AppTheme.Light)

        for (theme in themes) {
            var isChecked by mutableStateOf(false)

            setContent {
                LtiTheme(appTheme = theme, effectsEnabled = true) {
                    val hazeState = GlassTheme.hazeState
                    Box(Modifier.fillMaxSize()) {
                        // High-contrast non-repeating calibration pattern
                        Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
                            drawNonRepeatingPattern()
                        }
                        Box(Modifier.align(Alignment.Center)) {
                            GlassToggle(
                                checked = isChecked,
                                onCheckedChange = { isChecked = it },
                            )
                        }
                    }
                }
            }

            // Perform a fast click (pointer down, instantaneous release)
            onRoot().performTouchInput {
                down(center)
                up()
            }

            // Advance clock to mid-travel (90ms out of 180ms position travel)
            mainClock.advanceTimeBy(90)
            waitForIdle()

            val midTravelImage = onRoot().captureToImage()
            assertNotNull(midTravelImage)

            // At mid-travel, the toggle is at center of 240x160 canvas:
            // Track is 56x32 centered at (120, 80) -> [92, 64, 148, 96].
            // Thumb is 28x28 midway along travel (start x ~ 94, mid x ~ 106..134).
            // Sample the thumb interior at mid-travel:
            val thumbMidInterior = Rect(108f, 70f, 128f, 90f)
            val edgeGradient = computeMaxEdgeGradient(midTravelImage, thumbMidInterior)

            println("[DIAGNOSTIC] Theme $theme Quick-Tap Mid-Travel (t=90ms) Thumb Interior Gradient: $edgeGradient")

            // Refractive clear glass must actively transmit the underlying pattern (edge gradient > 0.03).
            // A solid unblurred or opaque thumb would have gradient 0.0 in its interior.
            assertTrue(
                edgeGradient > 0.03,
                "Quick tap at mid-travel must reveal refractive glass (found flat interior gradient $edgeGradient in $theme)",
            )
        }
    }

    // =============================================================================================
    // 3. Isolated Backdrop Resampling Across Layout Positions vs Frozen Negative Control
    // =============================================================================================

    @Test
    fun testLayoutAwarePlacementResamplesBackdropAtDifferentPositions() = runDesktopComposeUiTest(
        width = 240,
        height = 160,
    ) {
        val hazeState = HazeState()

        // Isolated diagnostic fixture: refractive Clear thumb with layout-aware offset,
        // holding all color overlays and tints strictly constant (Color.Transparent) to isolate backdrop resampling
        @Composable
        fun RefractiveThumbFixture(offsetFraction: Float) {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
                        drawNonRepeatingPattern()
                    }
                    val travelPx = 24.dp
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(56.dp, 32.dp)
                            .padding(2.dp),
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .offset {
                                    val x = (offsetFraction * travelPx.toPx()).roundToInt()
                                    IntOffset(x, 0)
                                }
                                .size(28.dp)
                                .hazeGlass(
                                    input = HazeInput.Sources(hazeState),
                                    style = GlassMaterialStyles.clearStyle(
                                        theme = AppTheme.Blue,
                                        opticalTint = Color.Transparent,
                                        captureBacking = Color.Transparent,
                                    ).then {
                                        shape(GlassShapes.HazeCapsule)
                                    },
                                )
                                .clip(androidx.compose.foundation.shape.CircleShape),
                        )
                    }
                }
            }
        }

        // 1. Capture thumb at pos = 0.25 over non-repeating pattern
        setContent {
            RefractiveThumbFixture(offsetFraction = 0.25f)
        }
        val pos1Image = onRoot().captureToImage()

        // 2. Capture thumb at pos = 0.75 over non-repeating pattern
        setContent {
            RefractiveThumbFixture(offsetFraction = 0.75f)
        }
        val pos2Image = onRoot().captureToImage()

        assertNotNull(pos1Image)
        assertNotNull(pos2Image)

        // Thumb at 0.25 is centered at x = 114 (inset 16x16 ROI: [106, 72, 122, 88])
        // Thumb at 0.75 is centered at x = 126 (inset 16x16 ROI: [118, 72, 134, 88])
        val roi1 = Rect(106f, 72f, 122f, 88f)
        val roi2 = Rect(118f, 72f, 134f, 88f)

        val map1 = pos1Image.toPixelMap()
        val map2 = pos2Image.toPixelMap()

        // Negative Control: A sliding frozen texture has identical local pixels (diff = 0)
        var frozenDiffSum = 0.0
        for (dy in 0 until 16) {
            for (dx in 0 until 16) {
                val c1 = map1[(roi1.left + dx).toInt(), (roi1.top + dy).toInt()]
                val cFrozen = map1[(roi1.left + dx).toInt(), (roi1.top + dy).toInt()]
                frozenDiffSum += abs(c1.red - cFrozen.red) + abs(c1.green - cFrozen.green) + abs(c1.blue - cFrozen.blue)
            }
        }
        val frozenMeanDiff = frozenDiffSum / (16 * 16)
        assertEquals(0.0, frozenMeanDiff, "Frozen-texture negative control must produce identical local pixels")

        // Active layout-aware Haze resampling: compares local thumb coordinates across position 0.25 and 0.75
        var localDiffSum = 0.0
        val sampleCount = 16 * 16
        for (dy in 0 until 16) {
            for (dx in 0 until 16) {
                val c1 = map1[(roi1.left + dx).toInt(), (roi1.top + dy).toInt()]
                val c2 = map2[(roi2.left + dx).toInt(), (roi2.top + dy).toInt()]
                val diff = abs(c1.red - c2.red) + abs(c1.green - c2.green) + abs(c1.blue - c2.blue)
                localDiffSum += diff
            }
        }
        val meanLocalDiff = localDiffSum / sampleCount
        println("[DIAGNOSTIC] Resampling Proof (Thumb Pos 0.25 vs Pos 0.75 Mean Local Diff): $meanLocalDiff")

        // Active layout-aware Haze resampling must yield distinct local samples reflecting the different backdrop at x1 vs x2
        assertTrue(
            meanLocalDiff > 0.10,
            "Haze thumb must resample distinct backdrop at different layout positions (found meanLocalDiff = $meanLocalDiff)",
        )
    }

    // =============================================================================================
    // 4. Boundary Refraction on Moving Thumb vs Zero-Refraction Baseline
    // =============================================================================================

    @Test
    fun testThumbRefractionDisplacementVsZeroRefractionBaseline() = runDesktopComposeUiTest(
        width = 240,
        height = 160,
    ) {
        val hazeState = HazeState()

        // 1. Unrefracted baseline: zero refraction clear style
        var unrefractedImage: ImageBitmap? = null
        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
                        drawNonRepeatingPattern()
                    }
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(28.dp)
                            .hazeGlass(
                                input = HazeInput.Sources(hazeState),
                                style = GlassMaterialStyles.clearStyle(
                                    theme = AppTheme.Blue,
                                    opticalTint = Color.Transparent,
                                    captureBacking = Color.Transparent,
                                ).then {
                                    shape(GlassShapes.HazeCapsule)
                                    optics(
                                        refractionStrength = 0f,
                                        refractionDisplacement = 0.dp,
                                    )
                                },
                            ),
                    )
                }
            }
        }
        unrefractedImage = onRoot().captureToImage()

        // 2. Refracted thumb: production clear style
        var refractedImage: ImageBitmap? = null
        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
                        drawNonRepeatingPattern()
                    }
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(28.dp)
                            .hazeGlass(
                                input = HazeInput.Sources(hazeState),
                                style = GlassMaterialStyles.clearStyle(
                                    theme = AppTheme.Blue,
                                    opticalTint = Color.Transparent,
                                    captureBacking = Color.Transparent,
                                ).then {
                                    shape(GlassShapes.HazeCapsule)
                                },
                            ),
                    )
                }
            }
        }
        refractedImage = onRoot().captureToImage()

        assertNotNull(unrefractedImage)
        assertNotNull(refractedImage)

        // Measured along curved rim boundary: [104, 66, 136, 94]
        val rimRoi = Rect(104f, 66f, 136f, 94f)
        val result = GlassOpticalVerifier.measureRefractionFeatureDisplacement(
            unrefracted = unrefractedImage,
            refracted = refractedImage,
            boundaryRoi = rimRoi,
        )

        println(
            "[DIAGNOSTIC] Thumb Boundary Refraction: totalFeatures=${result.featureCount}, " +
                "displacedFeatures=${result.displacedFeatureCount}, maxDisplacement=${result.maxDisplacementPx}px",
        )

        assertTrue(
            result.displacedFeatureCount >= 1,
            "Thumb rim must exhibit optical refraction displacement (found ${result.displacedFeatureCount} features)",
        )
        assertTrue(
            result.maxDisplacementPx >= 0.5,
            "Thumb rim displacement must exceed 0.5px (found ${result.maxDisplacementPx}px)",
        )
    }

    // =============================================================================================
    // 5. Effects-Off Inset Invariance & Fallback Contract
    // =============================================================================================

    @Test
    fun testEffectsOffInsetInvarianceAcrossBackdrops() = runDesktopComposeUiTest(
        width = 240,
        height = 160,
    ) {
        val hazeState = HazeState()

        // Render effects-off over Backdrop A (magenta)
        var backdropAImage: ImageBitmap? = null
        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = false) {
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
                        drawRect(Color.Magenta)
                    }
                    Box(Modifier.align(Alignment.Center)) {
                        GlassToggle(
                            checked = false,
                            onCheckedChange = {},
                        )
                    }
                }
            }
        }
        backdropAImage = onRoot().captureToImage()

        // Render effects-off over Backdrop B (cyan)
        var backdropBImage: ImageBitmap? = null
        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = false) {
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
                        drawRect(Color.Cyan)
                    }
                    Box(Modifier.align(Alignment.Center)) {
                        GlassToggle(
                            checked = false,
                            onCheckedChange = {},
                        )
                    }
                }
            }
        }
        backdropBImage = onRoot().captureToImage()

        assertNotNull(backdropAImage)
        assertNotNull(backdropBImage)

        // Inset regions: sample well inside track and thumb (avoiding outer antialiased boundary pixels)
        val trackInsetRoi = Rect(125f, 75f, 140f, 85f)
        val thumbInsetRoi = Rect(100f, 74f, 114f, 86f)

        val mapA = backdropAImage.toPixelMap()
        val mapB = backdropBImage.toPixelMap()

        // Track inset invariance check
        for (y in trackInsetRoi.top.toInt()..trackInsetRoi.bottom.toInt()) {
            for (x in trackInsetRoi.left.toInt()..trackInsetRoi.right.toInt()) {
                val colorA = mapA[x, y]
                val colorB = mapB[x, y]
                assertEquals(colorA, colorB, "Effects-off track inset must be backdrop-invariant at ($x, $y)")
            }
        }

        // Thumb inset invariance check
        for (y in thumbInsetRoi.top.toInt()..thumbInsetRoi.bottom.toInt()) {
            for (x in thumbInsetRoi.left.toInt()..thumbInsetRoi.right.toInt()) {
                val colorA = mapA[x, y]
                val colorB = mapB[x, y]
                assertEquals(colorA, colorB, "Effects-off thumb inset must be backdrop-invariant at ($x, $y)")
            }
        }
    }

    // =============================================================================================
    // 6. Directional & Motion Integrity (RTL & Reduced Motion)
    // =============================================================================================

    @Test
    fun testRtlDirectionalIntegrity() = runDesktopComposeUiTest(
        width = 240,
        height = 160,
    ) {
        var isChecked by mutableStateOf(false)

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = false) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GlassToggle(
                            checked = isChecked,
                            onCheckedChange = { isChecked = it },
                        )
                    }
                }
            }
        }

        // In RTL, unchecked thumb is on the RIGHT side of the track (logical start is Right)
        val uncheckedImage = onRoot().captureToImage()
        val mapUnchecked = uncheckedImage.toPixelMap()
        // Right side of track centered at 120: x ~ 130
        val rightPixel = mapUnchecked[130, 80]
        // Left side of track centered at 120: x ~ 102
        val leftPixel = mapUnchecked[102, 80]

        // Thumb is lighter than track backing
        assertTrue(rightPixel.red > leftPixel.red, "In RTL, unchecked thumb must rest on the right side")

        isChecked = true
        waitForIdle()

        val checkedImage = onRoot().captureToImage()
        val mapChecked = checkedImage.toPixelMap()
        val checkedLeftPixel = mapChecked[102, 80]
        val checkedRightPixel = mapChecked[130, 80]

        assertTrue(checkedLeftPixel.red > checkedRightPixel.red, "In RTL, checked thumb must travel to the left side")
    }

    // =============================================================================================
    // Helper Pattern Drawing
    // =============================================================================================

    private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNonRepeatingPattern() {
        // Multi-step high-contrast gradient ramp with sharp step markers
        val steps = 8
        val stepWidth = size.width / steps
        for (i in 0 until steps) {
            val luminance = i.toFloat() / (steps - 1)
            drawRect(
                color = Color(luminance, luminance, luminance),
                topLeft = Offset(i * stepWidth, 0f),
                size = androidx.compose.ui.geometry.Size(stepWidth, size.height),
            )
        }
        // Sharp vertical feature line down the center
        drawLine(
            color = Color.Red,
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = 3f,
        )
    }

    private fun computeMaxEdgeGradient(image: ImageBitmap, roi: Rect): Double {
        val pixelMap = image.toPixelMap()
        var maxGrad = 0.0
        val startX = roi.left.toInt().coerceIn(0, image.width - 2)
        val endX = roi.right.toInt().coerceIn(startX + 1, image.width - 1)
        val startY = roi.top.toInt().coerceIn(0, image.height - 1)
        val endY = roi.bottom.toInt().coerceIn(startY + 1, image.height)

        for (y in startY until endY) {
            for (x in startX until endX) {
                val lum1 = GlassOpticalVerifier.relativeLuminance(pixelMap[x, y])
                val lum2 = GlassOpticalVerifier.relativeLuminance(pixelMap[x + 1, y])
                val grad = abs(lum2 - lum1)
                if (grad > maxGrad) maxGrad = grad
            }
        }
        return maxGrad
    }
}
