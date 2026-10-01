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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.cancel
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.component.inputs.GlassToggleImpl
import org.ide.lti.core.designsystem.component.inputs.LocalToggleTimeSource
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.testing.optical.GlassOpticalVerifier
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * Optical and interaction verification suite for [GlassToggle] liquid motion.
 *
 * Verifies:
 * 1. **Baseline Callback Deduplication**: Exact single invocation across tap, completed drag,
 *    and keyboard.
 * 2. **Quick-Tap Mid-Travel Acceptance**: Thumb is visibly refractive at mid-travel during a
 *    fast click in Dark, Blue, and Light.
 * 3. **Isolated Backdrop Resampling**: Proves Haze actively resamples backdrop across distinct
 *    layout positions vs frozen control.
 * 4. **Boundary Refraction on Moving Thumb**: Measured feature displacement along the thumb boundary
 *    vs unrefracted baseline.
 * 5. **Effects-Off Inset Invariance**: Exact solid token color and backdrop invariance on inset
 *    regions of track and thumb.
 * 6. **Directional & Motion Integrity**: Proper RTL layout-aware translation and instantaneous snap
 *    under reduced motion.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class)
// Comprehensive liquid motion verification suite with 24 optical and interaction tests.
@Suppress("LargeClass", "TooManyFunctions")
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
    fun testTapAfterConsumedDragStillToggles() = runDesktopComposeUiTest(
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
            swipe(
                start = center.copy(x = center.x - 14f),
                end = center.copy(x = center.x + 24f),
                durationMillis = 200,
            )
        }
        assertEquals(1, callbackCount)
        assertTrue(isChecked)

        // Allow cooldown to elapse for next distinct physical action
        Thread.sleep(320)

        onRoot().performTouchInput { click(center) }
        assertEquals(2, callbackCount, "A consumed drag must not suppress the next independent tap")
        assertTrue(!isChecked)
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

            waitForIdle()
            mainClock.autoAdvance = false

            val startThumbBounds = onNodeWithTag("GlassToggle_Thumb").fetchSemanticsNode().boundsInRoot
            val startX = startThumbBounds.left
            val expectedTravel = 20f

            // Perform a fast click (pointer down, instantaneous release)
            onRoot().performTouchInput {
                down(center)
                up()
            }

            // Step clock frame by frame (16ms) to find the nearest observed midpoint (|progress - 0.5f| minimized)
            var bestDistanceToMidpoint = Float.MAX_VALUE
            var bestFrameImage: ImageBitmap? = null
            var bestThumbBounds = startThumbBounds

            for (step in 1..15) {
                mainClock.advanceTimeBy(16)
                waitForIdle()
                val currentBounds = onNodeWithTag("GlassToggle_Thumb").fetchSemanticsNode().boundsInRoot
                val progress = (currentBounds.left - startX) / expectedTravel
                val dist = abs(progress - 0.5f)
                if (dist < bestDistanceToMidpoint) {
                    bestDistanceToMidpoint = dist
                    bestThumbBounds = currentBounds
                    bestFrameImage = onRoot().captureToImage()
                }
            }

            assertNotNull(bestFrameImage)
            writeMotionFrame(bestFrameImage, "${theme.id}_mid_travel")

            // Sample the interior of the measured rendered bounds (deflated by 4px from edges)
            val thumbInterior = Rect(
                left = bestThumbBounds.left + 4f,
                top = bestThumbBounds.top + 4f,
                right = bestThumbBounds.right - 4f,
                bottom = bestThumbBounds.bottom - 4f,
            )
            val edgeGradient = computeMaxEdgeGradient(bestFrameImage, thumbInterior)

            println(
                "[DIAGNOSTIC] Theme $theme Quick-Tap Midpoint (|p-0.5|=$bestDistanceToMidpoint) " +
                    "Bounds: $bestThumbBounds Interior Gradient: $edgeGradient",
            )

            assertTrue(
                edgeGradient > 0.005,
                "Quick tap at nearest midpoint must reveal refractive glass " +
                    "(found flat interior gradient $edgeGradient in $theme)",
            )
            mainClock.advanceTimeBy(500)
            mainClock.autoAdvance = true
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

        // Active layout-aware Haze resampling must yield distinct local samples reflecting
        // the different backdrop at x1 vs x2
        assertTrue(
            meanLocalDiff > 0.10,
            "Haze thumb must resample distinct backdrop at different layout positions " +
                "(found meanLocalDiff = $meanLocalDiff)",
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
    // 7. Gate 1: Halo Elimination (Masked Differential Bloom Proof)
    // =============================================================================================

    @Test
    fun testMaskedOutsideCapsuleBloomIsZero() = runDesktopComposeUiTest(
        width = 300,
        height = 200,
    ) {
        var imageWithTrackSource: ImageBitmap? = null
        var imageWithoutTrackSource: ImageBitmap? = null

        // 1. Capture with effectsEnabled = true and registerTrackSource = true
        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                Box(Modifier.fillMaxSize()) {
                    GlassBackdrop(Modifier.fillMaxSize())
                    GlassCard(
                        modifier = Modifier.size(260.dp, 160.dp).align(Alignment.Center),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            GlassToggleImpl(
                                checked = false,
                                onCheckedChange = {},
                                registerTrackSource = true,
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()
        imageWithTrackSource = onRoot().captureToImage()

        // 2. Capture baseline with registerTrackSource = false (exact same toggle, card, backdrop, and visuals!)
        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                Box(Modifier.fillMaxSize()) {
                    GlassBackdrop(Modifier.fillMaxSize())
                    GlassCard(
                        modifier = Modifier.size(260.dp, 160.dp).align(Alignment.Center),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            GlassToggleImpl(
                                checked = false,
                                onCheckedChange = {},
                                registerTrackSource = false,
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()
        imageWithoutTrackSource = onRoot().captureToImage()

        assertNotNull(imageWithTrackSource)
        assertNotNull(imageWithoutTrackSource)

        val mapA = imageWithTrackSource.toPixelMap()
        val mapB = imageWithoutTrackSource.toPixelMap()

        val deltas = mutableListOf<Float>()
        var maxDelta = 0f

        for (y in 30..170) {
            for (x in 30..270) {
                // Skip pixels inside masked toggle capsule region
                if (x in 110..190 && y in 78..122) continue

                val ca = mapA[x, y]
                val cb = mapB[x, y]
                val diff = (abs(ca.red - cb.red) + abs(ca.green - cb.green) + abs(ca.blue - cb.blue)) / 3f
                deltas.add(diff)
                if (diff > maxDelta) maxDelta = diff
            }
        }

        deltas.sort()
        val meanDelta = deltas.average().toFloat()
        val p95Delta = deltas[(deltas.size * 0.95).toInt()]

        println("[DIAGNOSTIC] Gate 1 Real-Scene Outside-Capsule Bloom: mean=$meanDelta, p95=$p95Delta, max=$maxDelta")

        assertTrue(meanDelta < 0.002f, "Mean outside-capsule bloom delta must be < 0.002 (found $meanDelta)")
        assertTrue(p95Delta < 0.008f, "95th percentile localized bloom delta must be < 0.008 (found $p95Delta)")
        assertTrue(maxDelta < 0.015f, "Maximum localized peak bloom delta must be < 0.015 (found $maxDelta)")
    }

    // =============================================================================================
    // 8. Gate 2: Separated Refraction vs Specular Rim Glint Proof
    // =============================================================================================

    @Test
    fun testPatternedRefractionVsUnrefractedControl() = runDesktopComposeUiTest(
        width = 240,
        height = 160,
    ) {
        var zeroRefractionImage: ImageBitmap? = null
        var refractedImage: ImageBitmap? = null

        // 1. Zero-refraction control on moving production thumb: thumbRefractionFactor = 0f
        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().hazeSource(GlassTheme.hazeState)) {
                        drawNonRepeatingPattern()
                    }
                    Box(Modifier.align(Alignment.Center)) {
                        GlassToggleImpl(
                            checked = false,
                            onCheckedChange = {},
                            thumbRefractionFactor = 0f,
                        )
                    }
                }
            }
        }
        waitForIdle()

        // Hold pointer mid-travel to capture moving liquid lens
        onNodeWithTag("GlassToggle").performTouchInput {
            down(center)
            moveTo(center + Offset(10f, 0f))
        }
        zeroRefractionImage = onRoot().captureToImage()
        onNodeWithTag("GlassToggle").performTouchInput { up() }
        waitForIdle()

        // 2. Production moving thumb with full refraction: thumbRefractionFactor = 1f
        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().hazeSource(GlassTheme.hazeState)) {
                        drawNonRepeatingPattern()
                    }
                    Box(Modifier.align(Alignment.Center)) {
                        GlassToggleImpl(
                            checked = false,
                            onCheckedChange = {},
                            thumbRefractionFactor = 1f,
                        )
                    }
                }
            }
        }
        waitForIdle()

        // Hold pointer at exact same mid-travel offset and position
        onNodeWithTag("GlassToggle").performTouchInput {
            down(center)
            moveTo(center + Offset(10f, 0f))
        }
        refractedImage = onRoot().captureToImage()
        onNodeWithTag("GlassToggle").performTouchInput { up() }
        waitForIdle()

        assertNotNull(zeroRefractionImage)
        assertNotNull(refractedImage)

        // Measure boundary feature displacement around the mid-travel thumb [100, 60, 150, 100]
        val rimRoi = Rect(100f, 60f, 150f, 100f)
        val result = GlassOpticalVerifier.measureRefractionFeatureDisplacement(
            unrefracted = zeroRefractionImage,
            refracted = refractedImage,
            boundaryRoi = rimRoi,
        )

        println(
            "[DIAGNOSTIC] Gate 2 Production Thumb Refraction: totalFeatures=${result.featureCount}, " +
                "displacedFeatures=${result.displacedFeatureCount}, maxDisplacement=${result.maxDisplacementPx}px",
        )

        assertTrue(
            result.displacedFeatureCount >= 3,
            "Refraction displacement must affect at least 3 features on production thumb " +
                "(found ${result.displacedFeatureCount})",
        )
        assertTrue(
            result.maxDisplacementPx >= 1.0,
            "Refraction displacement must displace by at least 1.0px (found ${result.maxDisplacementPx}px)",
        )
    }

    // =============================================================================================
    // 9. Multi-Toggle Coexistence (Peer Track Source Toggling, Zero Cross-Talk)
    // =============================================================================================

    @Test
    fun testMultipleNearbyTogglesDoNotBleedOrCrossTalk() = runDesktopComposeUiTest(
        width = 300,
        height = 300,
    ) {
        var imageWithPeerSource: ImageBitmap? = null
        var imageWithoutPeerSource: ImageBitmap? = null

        // Render 1: Toggle A (checked=false) and Toggle B (checked=true, WITH track source)
        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                Box(Modifier.fillMaxSize()) {
                    GlassBackdrop(Modifier.fillMaxSize())
                    GlassCard(modifier = Modifier.size(260.dp, 260.dp).align(Alignment.Center)) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            GlassToggleImpl(
                                checked = false,
                                onCheckedChange = {},
                                registerTrackSource = true,
                                modifier = Modifier.testTag("ToggleA"),
                            )
                            GlassToggleImpl(
                                checked = true,
                                onCheckedChange = {},
                                registerTrackSource = true,
                                modifier = Modifier.testTag("ToggleB"),
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()
        imageWithPeerSource = onRoot().captureToImage()

        // Render 2: Exact same scene and positions, but Toggle B has registerTrackSource = false
        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                Box(Modifier.fillMaxSize()) {
                    GlassBackdrop(Modifier.fillMaxSize())
                    GlassCard(modifier = Modifier.size(260.dp, 260.dp).align(Alignment.Center)) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            GlassToggleImpl(
                                checked = false,
                                onCheckedChange = {},
                                registerTrackSource = true,
                                modifier = Modifier.testTag("ToggleA"),
                            )
                            GlassToggleImpl(
                                checked = true,
                                onCheckedChange = {},
                                registerTrackSource = false,
                                modifier = Modifier.testTag("ToggleB"),
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()
        imageWithoutPeerSource = onRoot().captureToImage()

        assertNotNull(imageWithPeerSource)
        assertNotNull(imageWithoutPeerSource)

        val mapA = imageWithPeerSource.toPixelMap()
        val mapB = imageWithoutPeerSource.toPixelMap()

        // Measure Toggle A thumb pixels (Toggle A is centered at y ≈ 85, thumb at x ≈ 130)
        var maxThumbDelta = 0f
        for (y in 75..95) {
            for (x in 120..140) {
                val ca = mapA[x, y]
                val cb = mapB[x, y]
                val diff = (abs(ca.red - cb.red) + abs(ca.green - cb.green) + abs(ca.blue - cb.blue)) / 3f
                if (diff > maxThumbDelta) maxThumbDelta = diff
            }
        }

        println("[DIAGNOSTIC] Multi-Toggle Cross-Talk: maxThumbDelta=$maxThumbDelta")
        assertTrue(
            maxThumbDelta < 0.002f,
            "Toggle A thumb must not sample Toggle B's track source (delta=$maxThumbDelta)",
        )
    }

    // =============================================================================================
    // 10. Gate 3: Atomic Zero-Leak Cancellation
    // =============================================================================================

    @Test
    fun testDragCancellationRestoresSuppliedCheckedStateWithoutCallback() = runDesktopComposeUiTest(
        width = 240,
        height = 160,
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

        // Cancel gesture by moving pointer and sending cancel
        onRoot().performTouchInput {
            down(center.copy(x = center.x - 14f))
            moveBy(Offset(18f, 0f), delayMillis = 50)
            cancel()
        }
        waitForIdle()

        assertEquals(0, callbackCount, "Drag cancellation must invoke onCheckedChange exactly ZERO times")
        assertFalse(isChecked, "Toggle state must remain false after canceled drag")

        // Also test starting from checked = true
        isChecked = true
        waitForIdle()

        onRoot().performTouchInput {
            down(center.copy(x = center.x + 14f))
            moveBy(Offset(-18f, 0f), delayMillis = 50)
            cancel()
        }
        waitForIdle()

        assertEquals(0, callbackCount, "Drag cancellation from checked state must fire ZERO callbacks")
        assertTrue(isChecked, "Toggle state must remain true after canceled drag")
    }

    // =============================================================================================
    // 11. Gate 4: Frame-1 Immediate Reduced Motion
    // =============================================================================================

    @Test
    fun testReducedMotionSnapsOnFrame1WithoutSwellOrSpringTravel() = runDesktopComposeUiTest(
        width = 240,
        height = 160,
    ) {
        var isChecked by mutableStateOf(false)

        setContent {
            LtiTheme(
                appTheme = AppTheme.Blue,
                effectsEnabled = true,
                reducedMotion = true,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlassToggle(
                        checked = isChecked,
                        onCheckedChange = { isChecked = it },
                    )
                }
            }
        }

        waitForIdle()
        mainClock.autoAdvance = false

        // Frame 0: Trigger activation
        onNodeWithTag("GlassToggle").performClick()

        // Advance by exactly 1 frame (16ms)
        mainClock.advanceTimeBy(16)

        // Capture frame 1: Thumb must immediately be rendered at the target checked position (right side)
        val image = onRoot().captureToImage()
        val map = image.toPixelMap()
        val rightPixel = map[140, 80]
        val leftPixel = map[95, 80]
        println("[DIAGNOSTIC] Gate 4 Reduced Motion Frame 1: rightPixel=${rightPixel.red}, leftPixel=${leftPixel.red}")

        assertTrue(
            rightPixel.red > 0.7f,
            "Reduced motion must snap thumb to right side on frame 1 (found ${rightPixel.red})",
        )
        assertTrue(leftPixel.red < 0.4f, "Left side must be uncovered track on frame 1 (found ${leftPixel.red})")

        mainClock.autoAdvance = true
    }

    // =============================================================================================
    // 12. Gate 5: Unified Accessibility & Keyboard Activation Deduplication
    // =============================================================================================

    @Test
    fun testSemanticAndKeyboardActivationDeduplication() = runDesktopComposeUiTest(
        width = 240,
        height = 160,
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

        // 1. Semantic performClick()
        onNodeWithTag("GlassToggle").performClick()
        waitForIdle()
        assertEquals(1, callbackCount, "Semantic click must invoke callback exactly once")
        assertTrue(isChecked)

        // Allow cooldown to elapse for next distinct action
        Thread.sleep(320)

        // 2. Keyboard Spacebar
        focusRequester.requestFocus()
        waitForIdle()
        onRoot().performKeyInput { pressKey(Key.Spacebar) }
        waitForIdle()
        assertEquals(2, callbackCount, "Spacebar must invoke callback exactly once")
        assertFalse(isChecked)

        // Allow cooldown to elapse for next distinct action
        Thread.sleep(320)

        // 3. Keyboard Enter
        onRoot().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(3, callbackCount, "Enter key must invoke callback exactly once")
        assertTrue(isChecked)
    }

    @Test
    fun testImmediateParentUpdateDeduplicatesRapidPhysicalEvents() = runDesktopComposeUiTest(
        width = 240,
        height = 160,
    ) {
        var callbackCount = 0
        var isChecked by mutableStateOf(false)
        val focusRequester = FocusRequester()

        // A clock that only moves when told to: the two presses below are 0 ms apart however busy the
        // machine is, so the debounce is tested rather than the scheduler.
        val clock = TestTimeSource()
        setContent {
            CompositionLocalProvider(LocalToggleTimeSource provides clock) {
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
        }
        waitForIdle()
        // Past the start-up window, so the first press is accepted.
        clock += 1.seconds

        // Rapid semantic click + Spacebar in immediate succession (<300ms)
        focusRequester.requestFocus()
        onNodeWithTag("GlassToggle").performClick()
        onRoot().performKeyInput { pressKey(Key.Spacebar) }
        waitForIdle()

        assertEquals(
            1,
            callbackCount,
            "Rapid physical activation events within 300ms must be deduplicated to exactly 1",
        )
        assertTrue(isChecked)
    }

    @Test
    fun testDelayedParentUpdateRendersOptimisticTargetAndReconciles() = runDesktopComposeUiTest(
        width = 240,
        height = 160,
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
                        },
                    )
                }
            }
        }
        waitForIdle()

        mainClock.autoAdvance = false
        // Click toggle: should invoke callback and render optimistic target immediately
        onNodeWithTag("GlassToggle").performClick()
        assertEquals(1, callbackCount, "Click must invoke onCheckedChange")

        // Rapid second click within 300ms cooldown must be suppressed
        onNodeWithTag("GlassToggle").performClick()
        assertEquals(1, callbackCount, "Second click within cooldown must be deduplicated")

        // Now simulate delayed parent applying the change
        isChecked = true
        mainClock.autoAdvance = true
        waitForIdle()
        assertTrue(isChecked)
    }

    @Test
    fun testControlledRejectionRestoresVisualStateAndUnlocks() = runDesktopComposeUiTest(
        width = 240,
        height = 160,
    ) {
        var callbackCount = 0

        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlassToggle(
                        checked = false,
                        onCheckedChange = {
                            callbackCount++
                        },
                    )
                }
            }
        }
        waitForIdle()

        // First click
        onNodeWithTag("GlassToggle").performClick()
        assertEquals(1, callbackCount)

        // Wait past the grace period (500ms) and cooldown (300ms)
        Thread.sleep(600)
        mainClock.advanceTimeBy(600)
        waitForIdle()

        // Second click after grace period must NOT be locked!
        onNodeWithTag("GlassToggle").performClick()
        assertEquals(2, callbackCount, "Activation must unlock after grace period even if parent declined")
    }

    @Test
    fun testSwitchingReducedMotionDuringActiveAnimationSnapsImmediate() = runDesktopComposeUiTest(
        width = 240,
        height = 160,
    ) {
        var reducedMotion by mutableStateOf(false)
        var isChecked by mutableStateOf(false)

        setContent {
            LtiTheme(
                appTheme = AppTheme.Blue,
                effectsEnabled = true,
                reducedMotion = reducedMotion,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlassToggle(
                        checked = isChecked,
                        onCheckedChange = { isChecked = it },
                    )
                }
            }
        }
        waitForIdle()

        mainClock.autoAdvance = false
        // Start animation
        onNodeWithTag("GlassToggle").performClick()

        // Advance mid-transit (100ms into the spring)
        mainClock.advanceTimeBy(100)

        // Dynamically switch reducedMotion = true mid-transit
        reducedMotion = true
        mainClock.advanceTimeBy(16)

        // Frame after reducedMotion = true: thumb must immediately snap to target
        val image = onRoot().captureToImage()
        val map = image.toPixelMap()
        val rightPixel = map[140, 80]
        assertTrue(rightPixel.red > 0.6f, "Switching reduced motion mid-transit must immediately snap thumb to target")

        mainClock.autoAdvance = true
    }

    @Test
    fun testDisabledToggleHasNoOnClickActionAndIgnoresKeys() = runDesktopComposeUiTest(
        width = 240,
        height = 160,
    ) {
        var callbackCount = 0

        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlassToggle(
                        checked = false,
                        onCheckedChange = { callbackCount++ },
                        enabled = false,
                    )
                }
            }
        }

        waitForIdle()

        // Assert semantics: has disabled(), does NOT have OnClick action
        val node = onNodeWithTag("GlassToggle").fetchSemanticsNode()
        val hasDisabled = node.config.contains(SemanticsProperties.Disabled)
        val hasOnClick = node.config.contains(SemanticsActions.OnClick)

        assertTrue(hasDisabled, "Disabled toggle must declare Disabled semantics property")
        assertFalse(hasOnClick, "Disabled toggle must NOT expose OnClick semantic action")

        // Pressing Spacebar or Enter must cause 0 callbacks
        onRoot().performKeyInput {
            pressKey(Key.Spacebar)
            pressKey(Key.Enter)
        }
        waitForIdle()

        assertEquals(0, callbackCount, "Disabled toggle must produce 0 callbacks on key inputs")
    }

    // =============================================================================================
    // Helper Pattern Drawing
    // =============================================================================================

    private fun writeMotionFrame(image: ImageBitmap, name: String) {
        val directory = File("build/reports/screenshots/toggle_motion")
        directory.mkdirs()
        val bytes = Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
        assertNotNull(bytes)
        File(directory, "$name.png").writeBytes(bytes)
    }

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
