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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
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
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.testing.optical.GlassOpticalVerifier
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Measurement-first prototype test harness evaluating candidate track/thumb geometry pairs,
 * theme-specific tint variants, controlled blur-spread physics, and keyboard focus accessibility.
 */
// One prototype suite: every test drives the same PrototypeGlassToggle and pixel helpers below.
@Suppress("LargeClass")
@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class)
class GlassTogglePrototypeTest {

    enum class ToggleGeometryPair(
        val label: String,
        val visualTrackWidth: Dp,
        val visualTrackHeight: Dp,
        val trackCornerRadius: Dp,
        val thumbWidth: Dp,
        val thumbHeight: Dp,
        val thumbIsCircle: Boolean,
        val padding: Dp,
        // Decoupled interactive hit area
        val minTouchTarget: Dp = 48.dp,
    ) {
        PairACurrent(
            label = "PairA_64x28_Thumb40x24",
            visualTrackWidth = 64.dp,
            visualTrackHeight = 28.dp,
            trackCornerRadius = 14.dp,
            thumbWidth = 40.dp,
            thumbHeight = 24.dp,
            thumbIsCircle = false,
            padding = 2.dp,
        ),
        PairBErgonomic(
            label = "PairB_56x32_Thumb28x28Circ",
            visualTrackWidth = 56.dp,
            visualTrackHeight = 32.dp,
            trackCornerRadius = 16.dp,
            thumbWidth = 28.dp,
            thumbHeight = 28.dp,
            thumbIsCircle = true,
            padding = 2.dp,
        ),
        PairCHazeReference(
            label = "PairC_64x48_Thumb40x40Circ",
            visualTrackWidth = 64.dp,
            visualTrackHeight = 48.dp,
            trackCornerRadius = 24.dp,
            thumbWidth = 40.dp,
            thumbHeight = 40.dp,
            thumbIsCircle = true,
            padding = 4.dp,
        ),
    }

    data class ThemeTintConfig(val theme: AppTheme, val inactiveAlpha: Float, val activeAlpha: Float)

    private val themeConfigs = listOf(
        ThemeTintConfig(AppTheme.Dark, inactiveAlpha = 0.14f, activeAlpha = 0.32f),
        ThemeTintConfig(AppTheme.Blue, inactiveAlpha = 0.16f, activeAlpha = 0.36f),
        ThemeTintConfig(AppTheme.Light, inactiveAlpha = 0.12f, activeAlpha = 0.28f),
    )

    @Composable
    private fun PrototypeGlassToggle(
        geometry: ToggleGeometryPair,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        activeColor: Color = Color.Unspecified,
        transparentBacking: Boolean = false,
        inactiveAlpha: Float = 0.14f,
        activeAlpha: Float = 0.32f,
        reducedMotion: Boolean = false,
        interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
        isNegativeControlUnblurred: Boolean = false,
        hazeState: HazeState = GlassTheme.hazeState,
    ) {
        val colors = MaterialTheme.colorScheme
        val resolvedActiveColor = if (activeColor.isSpecified) activeColor else colors.primary
        val isFocused by interactionSource.collectIsFocusedAsState()
        val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
        val theme = GlassTheme.appTheme
        val effectsEnabled = GlassTheme.effectsEnabled

        val targetBacking = if (checked) resolvedActiveColor else colors.surfaceContainerHighest
        val targetTint = if (checked) {
            resolvedActiveColor.copy(alpha = activeAlpha)
        } else {
            colors.onSurface.copy(alpha = inactiveAlpha)
        }

        // Motion policy: snap when reduced motion is requested
        val colorSpec = if (reducedMotion) snap() else tween<Color>(durationMillis = 180)
        val dpSpec = if (reducedMotion) snap() else tween<Dp>(durationMillis = 180)

        val animatedTint by animateColorAsState(targetTint, animationSpec = colorSpec, label = "ToggleTint")
        val animatedBacking by animateColorAsState(targetBacking, animationSpec = colorSpec, label = "ToggleBacking")

        val trackShape = RoundedCornerShape(geometry.trackCornerRadius)

        val trackModifier = resolvePrototypeTrackModifier(
            isNegativeControlUnblurred = isNegativeControlUnblurred,
            effectsEnabled = effectsEnabled,
            animatedTint = animatedTint,
            animatedBacking = animatedBacking,
            transparentBacking = transparentBacking,
            trackShape = trackShape,
            hazeState = hazeState,
            theme = theme,
            interactionSource = interactionSource,
            reducedMotion = reducedMotion,
        )

        // Keyboard focus indicator: visible in BOTH effects-on and effects-off modes
        val focusBorderModifier = if (isFocused) {
            Modifier.border(
                width = 2.dp,
                color = colors.primary,
                shape = trackShape,
            )
        } else {
            Modifier
        }

        // Decoupled interactive touch target wrapper (minimum 48 dp high)
        Box(
            modifier = modifier
                .defaultMinSize(minWidth = geometry.visualTrackWidth, minHeight = geometry.minTouchTarget)
                .focusable(enabled = enabled, interactionSource = interactionSource)
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    interactionSource = interactionSource,
                    indication = null,
                    onValueChange = onCheckedChange,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Visual Track
            Box(
                modifier = Modifier
                    .size(width = geometry.visualTrackWidth, height = geometry.visualTrackHeight)
                    .then(trackModifier)
                    .then(focusBorderModifier)
                    .clip(trackShape)
                    .padding(geometry.padding),
            ) {
                val travel = geometry.visualTrackWidth - geometry.thumbWidth - geometry.padding * 2
                val thumbOffset by animateDpAsState(
                    targetValue = if (checked) travel else 0.dp,
                    animationSpec = dpSpec,
                    label = "ToggleThumbOffset",
                )
                val thumbColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.4f)
                val thumbShape = if (geometry.thumbIsCircle) {
                    CircleShape
                } else {
                    RoundedCornerShape(
                        geometry.thumbHeight / 2,
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = thumbOffset)
                        .size(width = geometry.thumbWidth, height = geometry.thumbHeight)
                        .clip(thumbShape)
                        .background(thumbColor),
                )
            }
        }
    }

    @Composable
    private fun resolvePrototypeTrackModifier(
        isNegativeControlUnblurred: Boolean,
        effectsEnabled: Boolean,
        animatedTint: Color,
        animatedBacking: Color,
        transparentBacking: Boolean,
        trackShape: RoundedCornerShape,
        hazeState: HazeState,
        theme: AppTheme,
        interactionSource: MutableInteractionSource,
        reducedMotion: Boolean,
    ): Modifier = when {
        isNegativeControlUnblurred -> {
            Modifier.background(animatedTint, trackShape)
        }
        effectsEnabled -> {
            val backing = if (transparentBacking) Color.Transparent else animatedBacking
            val motionPolicy = if (reducedMotion) {
                GlassReducedMotionPolicy.Reduced
            } else {
                GlassReducedMotionPolicy.System
            }
            Modifier.hazeGlass(
                input = HazeInput.Sources(hazeState),
                style = remember(theme, animatedTint, animatedBacking, transparentBacking) {
                    GlassMaterialStyles.baseStyle(
                        theme = theme,
                        opticalTint = animatedTint,
                        captureBacking = backing,
                    ) then GlassStyle {
                        this.shape(trackShape)
                        focused {
                            lightingIntensity(0.55f)
                            whitePointDelta(0.04f)
                        }
                    }
                },
                interactionSource = interactionSource,
                interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
                interactionReducedMotionPolicy = motionPolicy,
            )
        }
        else -> {
            Modifier.background(animatedBacking, trackShape)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 1. Controlled Optical Edge-Spread & Blur Verification vs Negative Control
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testOpticalEdgeSpreadAndBlurVsNegativeControlAcrossGeometries() = runDesktopComposeUiTest(
        width = 400,
        height = 300,
    ) {
        val hazeState = HazeState()

        for (geometry in ToggleGeometryPair.entries) {
            // Offset the toggle so that the center of its exposed track aligns with stripe boundary at x = 224
            val trackOffsetX = (24f - geometry.thumbWidth.value / 2f).dp

            // A. Test authentic Haze blur over 16px high-frequency stripes
            setContent {
                LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                    Box(Modifier.fillMaxSize()) {
                        // High frequency stripe calibration backdrop
                        Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
                            val stripeWidth = 16f
                            var x = 0f
                            var isWhite = false
                            while (x < size.width) {
                                drawRect(
                                    color = if (isWhite) Color.White else Color.Black,
                                    topLeft = Offset(x, 0f),
                                    size = Size(stripeWidth, size.height),
                                )
                                x += stripeWidth
                                isWhite = !isWhite
                            }
                        }

                        // Toggle placed so exposed track covers boundary x = 224
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            PrototypeGlassToggle(
                                geometry = geometry,
                                checked = false,
                                onCheckedChange = {},
                                hazeState = hazeState,
                                modifier = Modifier.offset(x = trackOffsetX),
                            )
                        }
                    }
                }
            }
            waitForIdle()

            val glassImage = onRoot().captureToImage()
            assertNotNull(glassImage)

            // Measure ROI inside exposed track around boundary x = 224
            val centerY = glassImage.height / 2
            val exposedTrackRoi = Rect(
                left = 217f,
                top = (centerY - 6).toFloat(),
                right = 231f,
                bottom = (centerY + 6).toFloat(),
            )

            val spreadResult =
                GlassOpticalVerifier.measureEdgeSpread(glassImage, exposedTrackRoi, stripePeriodPx = 16)
            println(
                "[OPTICAL EDGE SPREAD] Geometry: ${geometry.label} " +
                    "transitionWidth=${spreadResult.transitionWidthPx}px, " +
                    "spreadFraction=${spreadResult.spreadFraction}, peakGrad=${spreadResult.peakGradient}",
            )

            // Verify spatial diffusion is active (transition spread >= 3px, spread fraction >= 0.20)
            assertTrue(
                spreadResult.transitionWidthPx >= 3,
                "${geometry.label} transition width (${spreadResult.transitionWidthPx}px) must be >= 3px under Haze",
            )
            assertTrue(
                spreadResult.spreadFraction >= 0.20,
                "${geometry.label} gradient spread fraction (${spreadResult.spreadFraction}) " +
                    "must be >= 0.20 under Haze",
            )

            // B. Negative Control: Unblurred translucent overlay MUST FAIL blur verification
            setContent {
                LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                    Box(Modifier.fillMaxSize()) {
                        Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
                            val stripeWidth = 16f
                            var x = 0f
                            var isWhite = false
                            while (x < size.width) {
                                drawRect(
                                    color = if (isWhite) Color.White else Color.Black,
                                    topLeft = Offset(x, 0f),
                                    size = Size(stripeWidth, size.height),
                                )
                                x += stripeWidth
                                isWhite = !isWhite
                            }
                        }

                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            PrototypeGlassToggle(
                                geometry = geometry,
                                checked = false,
                                onCheckedChange = {},
                                isNegativeControlUnblurred = true,
                                hazeState = hazeState,
                                modifier = Modifier.offset(x = trackOffsetX),
                            )
                        }
                    }
                }
            }
            waitForIdle()

            val negativeImage = onRoot().captureToImage()
            val negSpread = GlassOpticalVerifier.measureEdgeSpread(negativeImage, exposedTrackRoi, stripePeriodPx = 16)
            println(
                "[NEGATIVE CONTROL EDGE SPREAD] Geometry: ${geometry.label} " +
                    "transitionWidth=${negSpread.transitionWidthPx}px, spreadFraction=${negSpread.spreadFraction}",
            )
            assertEquals(1, negSpread.transitionWidthPx, "Negative control must produce sharp 1px transition")
            assertTrue(negSpread.spreadFraction < 0.10, "Negative control must not diffuse gradient energy")

            assertFailsWith<AssertionError> {
                GlassOpticalVerifier.assertOpticalBlurSpread(
                    glassRender = negativeImage,
                    roi = exposedTrackRoi,
                    stripePeriodPx = 16,
                    minTransitionWidthPx = 3,
                )
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 2. Full-Region Image-Wide Backing Equality Verification
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testFullRegionBackingEqualityAcrossEntireCanvas() = runDesktopComposeUiTest(
        width = 400,
        height = 300,
    ) {
        // 1. Render with captureBacking = trackSurfaceColor (production default)
        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                Box(Modifier.fillMaxSize()) {
                    GlassBackdrop() // Smooth backdrop with opaque base
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PrototypeGlassToggle(
                            geometry = ToggleGeometryPair.PairBErgonomic,
                            checked = false,
                            onCheckedChange = {},
                            transparentBacking = false,
                        )
                    }
                }
            }
        }
        waitForIdle()
        val imageOpaqueBacking = onRoot().captureToImage()

        // 2. Render with captureBacking = Transparent (diagnostic branch)
        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                Box(Modifier.fillMaxSize()) {
                    GlassBackdrop() // Smooth backdrop with opaque base
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PrototypeGlassToggle(
                            geometry = ToggleGeometryPair.PairBErgonomic,
                            checked = false,
                            onCheckedChange = {},
                            transparentBacking = true,
                        )
                    }
                }
            }
        }
        waitForIdle()
        val imageTransparentBacking = onRoot().captureToImage()

        // 3. Full-region image-wide equality comparison across every single pixel (120,000 pixels)
        val mapOpaque = imageOpaqueBacking.toPixelMap()
        val mapTransparent = imageTransparentBacking.toPixelMap()
        var maxDelta = 0.0f
        var diffPixelCount = 0
        val totalPixels = imageOpaqueBacking.width * imageOpaqueBacking.height

        for (y in 0 until imageOpaqueBacking.height) {
            for (x in 0 until imageOpaqueBacking.width) {
                val c1 = mapOpaque[x, y]
                val c2 = mapTransparent[x, y]
                val dr = abs(c1.red - c2.red)
                val dg = abs(c1.green - c2.green)
                val db = abs(c1.blue - c2.blue)
                val da = abs(c1.alpha - c2.alpha)
                val d = maxOf(dr, dg, db, da)
                if (d > maxDelta) maxDelta = d
                if (d > 0.005f) diffPixelCount++
            }
        }

        println(
            "[FULL-REGION BACKING COMPARISON] Max channel delta: $maxDelta across $totalPixels pixels " +
                "(pixels with delta > 0.005: $diffPixelCount)",
        )

        assertEquals(
            0,
            diffPixelCount,
            "Every pixel across the entire 400x300 canvas must be identical regardless of " +
                "captureBacking transparency over opaque GlassBackdrop",
        )
        assertTrue(
            maxDelta < 0.005f,
            "Max channel delta ($maxDelta) must be below 0.005f across all channels",
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 3. Visible Keyboard Focus via Real FocusRequester & Tab Navigation in BOTH Effects Modes
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testKeyboardFocusIndicatorVisibleInBothEffectsOnAndOff() = runDesktopComposeUiTest(
        width = 200,
        height = 120,
    ) {
        for (effectsEnabled in listOf(true, false)) {
            val focusRequester = FocusRequester()

            setContent {
                LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = effectsEnabled) {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        PrototypeGlassToggle(
                            geometry = ToggleGeometryPair.PairBErgonomic,
                            checked = false,
                            onCheckedChange = {},
                            modifier = Modifier.focusRequester(focusRequester),
                        )
                    }
                }
            }
            waitForIdle()
            val unfocusedImage = onRoot().captureToImage()

            // 1. Verify Focus via Keyboard Tab Key Navigation
            onRoot().performKeyInput {
                pressKey(Key.Tab)
            }
            waitForIdle()
            val tabFocusedImage = onRoot().captureToImage()
            val diffTab = computePixelDiffPercent(unfocusedImage, tabFocusedImage)
            println("[KEYBOARD FOCUS - TAB KEY NAVIGATION] effectsEnabled=$effectsEnabled | Focus diff: $diffTab%")
            assertTrue(
                diffTab > 1.0,
                "Keyboard focus acquired via Tab key press must render a visible focus ring when " +
                    "effectsEnabled=$effectsEnabled (found $diffTab%, expected > 1.0%)",
            )

            // 2. Verify Focus via FocusRequester
            focusRequester.requestFocus()
            waitForIdle()
            val requesterFocusedImage = onRoot().captureToImage()
            val diffRequester = computePixelDiffPercent(unfocusedImage, requesterFocusedImage)
            println("[KEYBOARD FOCUS - FOCUS REQUESTER] effectsEnabled=$effectsEnabled | Focus diff: $diffRequester%")
            assertTrue(
                diffRequester > 1.0,
                "Keyboard focus acquired via FocusRequester must render a visible focus ring when " +
                    "effectsEnabled=$effectsEnabled (found $diffRequester%, expected > 1.0%)",
            )
        }
    }

    private fun computePixelDiffPercent(img1: ImageBitmap, img2: ImageBitmap): Double {
        val map1 = img1.toPixelMap()
        val map2 = img2.toPixelMap()
        var diffPixels = 0
        val total = img1.width * img1.height
        for (y in 0 until img1.height) {
            for (x in 0 until img1.width) {
                val c1 = map1[x, y]
                val c2 = map2[x, y]
                if (abs(c1.red - c2.red) > 0.05f ||
                    abs(c1.green - c2.green) > 0.05f ||
                    abs(c1.blue - c2.blue) > 0.05f
                ) {
                    diffPixels++
                }
            }
        }
        return (diffPixels.toDouble() / total) * 100.0
    }

    // ---------------------------------------------------------------------------------------------
    // 4. Frame-by-Frame Motion Physics & Reduced Motion 0ms Snap Verification
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testNormalMotionAnimatesFrameByFrame() = runDesktopComposeUiTest(
        width = 200,
        height = 120,
    ) {
        mainClock.autoAdvance = false

        var normalChecked by mutableStateOf(false)
        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true, reducedMotion = false) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    PrototypeGlassToggle(
                        geometry = ToggleGeometryPair.PairBErgonomic,
                        checked = normalChecked,
                        onCheckedChange = { normalChecked = it },
                        reducedMotion = false,
                    )
                }
            }
        }
        mainClock.advanceTimeByFrame()
        val normalStartImg = onRoot().captureToImage().toPixelMap()
        // Pair B: track is [72, 128]. Start thumb spans [74, 102], end thumb spans [98, 126].
        // x = 80 is strictly inside the start thumb; x = 120 is strictly inside the end thumb.
        val thumbStartColorInitial = normalStartImg[80, 60]
        val trackExposedInitial = normalStartImg[120, 60] // exposed track at x=120
        println("[NORMAL MOTION TRACK TINT] At t=0: exposed track (x=120) = $trackExposedInitial")

        // Start thumb is light/onSurface, end position is exposed inactive track
        assertTrue(thumbStartColorInitial.red > 0.8f, "Initial thumb start position (x=80) must contain light thumb")
        assertTrue(trackExposedInitial.red < 0.6f, "Initial thumb end position (x=120) must be track color")

        // Trigger switch toggle
        normalChecked = true
        mainClock.advanceTimeByFrame()

        // Advance to mid-flight: 90ms of 180ms duration. Thumb is centered at x=100 (spans [86, 114]).
        mainClock.advanceTimeBy(90)
        val normalMidImg = onRoot().captureToImage().toPixelMap()
        val trackExposedMid = normalMidImg[120, 60]
        val thumbMidCenterColor = normalMidImg[100, 60]
        println("[NORMAL MOTION TRACK TINT] At t=90ms: mid exposed track (x=120) = $trackExposedMid")

        // At mid-flight (90ms), thumb is at x=100 (spans [86, 114]). So x=120 is not yet reached and still exposed.
        assertTrue(
            trackExposedMid.red < 0.6f,
            "At 90ms mid-flight, thumb (spans 86..114) must NOT yet have reached end position x=120",
        )
        assertTrue(
            thumbMidCenterColor.red > 0.8f,
            "At 90ms mid-flight, thumb center (x=100) must be light thumb color",
        )

        // Advance to completion: +120ms (total 210ms > 180ms)
        mainClock.advanceTimeBy(120)
        val normalEndImg = onRoot().captureToImage().toPixelMap()
        val thumbEndColorFinal = normalEndImg[120, 60]
        val trackExposedFinal = normalEndImg[80, 60] // x=80 was uncovered when thumb moved to x=120
        println("[NORMAL MOTION TRACK TINT] At t=210ms: final exposed track (x=80) = $trackExposedFinal")

        assertTrue(
            thumbEndColorFinal.red > 0.8f,
            "After 180ms animation, end position (x=120) must now be occupied by the thumb",
        )
        assertTrue(
            trackExposedFinal.red < 0.6f,
            "After 180ms animation, start position (x=80) must now be exposed track",
        )

        // VERIFY CONTINUOUS TRACK TINT INTERPOLATION:
        val deltaMidFromInitial = abs(trackExposedMid.blue - trackExposedInitial.blue)
        val deltaFinalFromInitial = abs(trackExposedFinal.blue - trackExposedInitial.blue)
        val deltaFinalFromMid = abs(trackExposedFinal.blue - trackExposedMid.blue)
        println(
            "[TINT INTERPOLATION CHECK] deltaInitialToMid=$deltaMidFromInitial, " +
                "deltaInitialToFinal=$deltaFinalFromInitial, deltaMidToFinal=$deltaFinalFromMid",
        )

        assertTrue(
            deltaMidFromInitial > 0.05f,
            "Track tint at t=90ms must interpolate away from initial inactive color (found delta=$deltaMidFromInitial)",
        )
        assertTrue(
            deltaFinalFromMid > 0.05f,
            "Track tint at t=90ms must not yet have reached final active color (found delta=$deltaFinalFromMid)",
        )
        assertTrue(
            deltaFinalFromInitial > deltaMidFromInitial,
            "Final exposed track color delta ($deltaFinalFromInitial) must exceed " +
                "mid-flight delta ($deltaMidFromInitial)",
        )
    }

    @Test
    fun testReducedMotionSnapsThumbAndColorImmediately() = runDesktopComposeUiTest(
        width = 200,
        height = 120,
    ) {
        mainClock.autoAdvance = false

        var reducedChecked by mutableStateOf(false)
        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true, reducedMotion = true) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    PrototypeGlassToggle(
                        geometry = ToggleGeometryPair.PairBErgonomic,
                        checked = reducedChecked,
                        onCheckedChange = { reducedChecked = it },
                        reducedMotion = true,
                    )
                }
            }
        }
        mainClock.advanceTimeByFrame()
        val initialImg = onRoot().captureToImage().toPixelMap()
        val initialTrackColor = initialImg[120, 60] // exposed track at x=120 before flip

        // Flip state to true under reduced motion
        reducedChecked = true
        // Advance clock for recomposition and layout/draw
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()

        val reducedImg = onRoot().captureToImage().toPixelMap()
        val reducedEndColor = reducedImg[120, 60]
        val reducedSnappedTrack = reducedImg[80, 60]

        println(
            "[REDUCED MOTION SNAP] End color immediately after flip: $reducedEndColor, " +
                "Snapped track color (x=80): $reducedSnappedTrack, Initial track: $initialTrackColor",
        )
        assertTrue(
            reducedEndColor.red > 0.8f,
            "Reduced motion must snap thumb to end position (x=120) immediately without delay",
        )
        assertTrue(
            reducedSnappedTrack.red < 0.6f,
            "Reduced motion must snap start position (x=80) to exposed track immediately on snap",
        )

        // VERIFY REDUCED MOTION COLOR SNAP ON FRAME 1:
        val snapColorShift = abs(reducedSnappedTrack.blue - initialTrackColor.blue)
        println(
            "[REDUCED MOTION TINT SNAP] Shift on frame 1: $snapColorShift " +
                "(blue: ${initialTrackColor.blue} -> ${reducedSnappedTrack.blue})",
        )
        assertTrue(
            snapColorShift > 0.20f,
            "Reduced motion must snap exposed track tint to active color on frame 1 " +
                "(shift=$snapColorShift, expected > 0.20f)",
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 5. Visual Artifact Export Across Geometries, Themes, and States
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testExportPrototypeVisualRendersAcrossThemes() = runDesktopComposeUiTest(
        width = 600,
        height = 360,
    ) {
        for (config in themeConfigs) {
            for (effectsEnabled in listOf(true, false)) {
                setContent {
                    LtiTheme(appTheme = config.theme, effectsEnabled = effectsEnabled) {
                        Box(Modifier.fillMaxSize()) {
                            GlassBackdrop()
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "Theme: ${config.theme.id} | Effects: $effectsEnabled",
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                for (geometry in ToggleGeometryPair.entries) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = geometry.name.substringBefore("_"),
                                            modifier = Modifier.size(width = 60.dp, height = 24.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        // Unchecked
                                        PrototypeGlassToggle(
                                            geometry = geometry,
                                            checked = false,
                                            onCheckedChange = {},
                                            inactiveAlpha = config.inactiveAlpha,
                                            activeAlpha = config.activeAlpha,
                                        )
                                        // Checked
                                        PrototypeGlassToggle(
                                            geometry = geometry,
                                            checked = true,
                                            onCheckedChange = {},
                                            inactiveAlpha = config.inactiveAlpha,
                                            activeAlpha = config.activeAlpha,
                                        )
                                        // Focused
                                        val focusedInteractionSource = remember {
                                            MutableInteractionSource().apply {
                                                tryEmit(FocusInteraction.Focus())
                                            }
                                        }
                                        PrototypeGlassToggle(
                                            geometry = geometry,
                                            checked = false,
                                            onCheckedChange = {},
                                            interactionSource = focusedInteractionSource,
                                            inactiveAlpha = config.inactiveAlpha,
                                            activeAlpha = config.activeAlpha,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                waitForIdle()

                val image = onRoot().captureToImage()
                val filename = "toggle_${config.theme.id}_effects_${if (effectsEnabled) "on" else "off"}"
                saveScreenshot(filename, image)
            }
        }
    }

    private fun saveScreenshot(name: String, bitmap: ImageBitmap) {
        val dir = File("build/reports/screenshots/toggle_prototype")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "$name.png")
        val skiaImage = Image.makeFromBitmap(bitmap.asSkiaBitmap())
        val bytes = skiaImage.encodeToData(EncodedImageFormat.PNG)?.bytes
        if (bytes != null) file.writeBytes(bytes)
    }
}
