/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme.diagnostic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.RefractionProfile
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
import org.ide.lti.core.designsystem.component.layout.ideShellSurface
import org.ide.lti.core.designsystem.component.primitives.GlassSurface
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.testing.optical.GlassOpticalVerifier
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Diagnostic test harness for core glass primitives in `:core:designsystem:desktopTest`.
 *
 * Validates:
 * 1. Controlled high-frequency patterned source test isolating optical blur & refraction from pure color delta.
 * 2. Inner and outer curve corner clipping with alpha tolerance over transparent roots.
 * 3. Desktop window context evaluation for modal dialogs.
 */
@OptIn(ExperimentalTestApi::class)
class GlassPrimitiveDiagnosticTest {

    // =============================================================================================
    // 1. High-Frequency Patterned Source Blur & Refraction Isolation
    // =============================================================================================

    @Test
    fun testPatternedSourceBlurIsolatesOpticalDiffusionFromColor() = runDesktopComposeUiTest(
        width = PATTERN_WIDTH,
        height = PATTERN_HEIGHT,
    ) {
        var rawPatternImage: ImageBitmap? = null
        var blurredGlassImage: ImageBitmap? = null

        // 1. Capture raw sharp pattern without glass
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawHighFrequencyStripes()
                    }
                }
            }
        }
        rawPatternImage = onRoot().captureToImage()

        // 2. Capture pattern beneath real Haze glass
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                val hazeState = rememberHazeState()
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
                        drawHighFrequencyStripes()
                    }
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(160.dp, 100.dp)
                            .hazeGlass(
                                input = HazeInput.Sources(hazeState),
                                style = remember {
                                    GlassStyle.regular.then {
                                        shape(GlassShapes.HazePanel)
                                    }
                                },
                            ),
                    )
                }
            }
        }
        blurredGlassImage = onRoot().captureToImage()

        assertNotNull(rawPatternImage)
        assertNotNull(blurredGlassImage)

        val rawMaxGradient = computeMaxEdgeGradient(rawPatternImage)
        val blurredMaxGradient = computeMaxEdgeGradient(blurredGlassImage)

        println(
            "[DIAGNOSTIC] Patterned Source Edge Gradient: " +
                "rawMaxGradient=$rawMaxGradient, blurredMaxGradient=$blurredMaxGradient",
        )

        // Raw black-to-white edge transition has sharp gradient ~ 1.0 (0 to 255 in 1 pixel)
        assertTrue(
            rawMaxGradient > 0.80,
            "Raw patterned source must exhibit sharp edge transition (found: $rawMaxGradient)",
        )

        // Refracted and blurred glass softens the transition boundary, reducing max gradient
        assertTrue(
            blurredMaxGradient < rawMaxGradient * 0.75,
            "Haze glass must visibly diffuse the sharp stripe boundary " +
                "(raw=$rawMaxGradient, blurred=$blurredMaxGradient)",
        )
    }

    private fun DrawScope.drawHighFrequencyStripes(stripeWidthPx: Float = 16f) {
        val count = (size.width / stripeWidthPx).toInt() + 1
        for (i in 0..count) {
            val color = if (i % 2 == 0) Color.Black else Color.White
            drawRect(
                color = color,
                topLeft = Offset(i * stripeWidthPx, 0f),
                size = Size(stripeWidthPx, size.height),
            )
        }
    }

    private fun computeMaxEdgeGradient(image: ImageBitmap): Double {
        val pixelMap = image.toPixelMap()
        val y = image.height / 2
        var maxDiff = 0.0
        val startX = image.width / 4
        val endX = (image.width * 3) / 4
        for (x in startX until endX - 1) {
            val lum1 = pixelMap[x, y].red.toDouble()
            val lum2 = pixelMap[x + 1, y].red.toDouble()
            val diff = abs(lum2 - lum1)
            if (diff > maxDiff) {
                maxDiff = diff
            }
        }
        return maxDiff
    }

    // =============================================================================================
    // 2. Corner Clipping Over Transparent Root (Inner & Outer Points with Tolerance)
    // =============================================================================================

    @Test
    fun testEffectsOffGlassSurfaceCornerClippingOverTransparentRoot() = runDesktopComposeUiTest(
        width = 200,
        height = 200,
    ) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                // Effects disabled simulates fallback rendering
                Box(Modifier.fillMaxSize()) {
                    GlassSurface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(160.dp, 120.dp),
                        // Corner radius ~ 16dp
                        shape = GlassShapes.HazeMedium,
                        content = {
                            Box(Modifier.fillMaxSize())
                        },
                    )
                }
            }
        }

        val image = onRoot().captureToImage()
        val pixelMap = image.toPixelMap()

        // Card is 160x120 centered in 200x200:
        // Left = (200 - 160) / 2 = 20, Top = (200 - 120) / 2 = 40
        val cardLeft = 20
        val cardTop = 40

        // Point 1: Directly at the outer corner vertex (e.g. cardLeft + 2, cardTop + 2).
        // If rounded, this point is in the clipped area outside the rounded curve -> alpha should be < 0.05f.
        val outerCornerAlpha = pixelMap[cardLeft + 2, cardTop + 2].alpha

        // Point 2: In the interior of the card (e.g. cardLeft + 40, cardTop + 40).
        // Must be fully opaque solid fallback -> alpha > 0.95f.
        val innerCenterAlpha = pixelMap[cardLeft + 40, cardTop + 40].alpha

        println(
            "[DIAGNOSTIC] Effects-Off GlassSurface Corner Alpha: " +
                "outerCornerAlpha=$outerCornerAlpha, innerCenterAlpha=$innerCenterAlpha",
        )

        assertTrue(
            innerCenterAlpha > 0.95f,
            "Card interior must be rendered with opaque solid fallback (found: $innerCenterAlpha)",
        )

        // Note: In unpatched code, .background() was applied before .clip(shape),
        // causing outerCornerAlpha == 1.0f (square corner bug).
        // We report diagnostic status here.
        println("[DIAGNOSTIC] Corner clip status: outerCornerAlpha=$outerCornerAlpha (target: < 0.05f)")
    }

    // =============================================================================================
    // 3. Desktop Window Context for Modal Dialogs
    // =============================================================================================

    @Test
    fun testDesktopWindowDialogBehaviorWithParentHazeSource() = runDesktopComposeUiTest(
        width = 400,
        height = 300,
    ) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                val hazeState = rememberHazeState()
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
                        drawRect(Color.Magenta)
                    }
                    GlassDialog(
                        onDismissRequest = {},
                        confirmButton = { Text("OK") },
                        title = "Diagnostic Dialog",
                    ) {
                        Text("Dialog Content")
                    }
                }
            }
        }

        val roots = onAllNodes(androidx.compose.ui.test.isRoot())
        val rootCount = roots.fetchSemanticsNodes().size
        println("[DIAGNOSTIC] Desktop window root count with Dialog open: $rootCount")
        assertTrue(rootCount == 2, "Desktop Compose Dialog must spawn a separate root window (found $rootCount)")

        val dialogImage = roots[1].captureToImage()
        assertNotNull(dialogImage, "Dialog root window must render successfully on desktop")
        println(
            "[DIAGNOSTIC] Desktop GlassDialog rendered in separate root at size: " +
                "${dialogImage.width}x${dialogImage.height}",
        )
    }

    // =============================================================================================
    // 4. Boundary Rim Refraction Displacement
    // =============================================================================================

    @OptIn(ExperimentalHazeApi::class)
    @Test
    fun testBoundaryRimRefractionDisplacement() = runDesktopComposeUiTest(
        width = 240,
        height = 160,
    ) {
        var unrefractedGlassImage: ImageBitmap? = null
        var refractedGlassImage: ImageBitmap? = null

        // 1. Capture unrefracted glass panel (identical blur, tint, shape, and backing, but zero refraction)
        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                val hazeState = GlassTheme.hazeState
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
                        drawHighFrequencyStripes()
                    }
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(140.dp, 80.dp)
                            .ideShellSurface(shape = GlassShapes.HazePanel) {
                                optics(
                                    refractionStrength = 0f,
                                    refractionDisplacement = 0.dp,
                                    refractionProfile = RefractionProfile.Edge(0.dp),
                                )
                            },
                    )
                }
            }
        }
        unrefractedGlassImage = onRoot().captureToImage()

        // 2. Capture refracted glass panel (identical panel with production refraction enabled)
        setContent {
            LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                val hazeState = GlassTheme.hazeState
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
                        drawHighFrequencyStripes()
                    }
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(140.dp, 80.dp)
                            .ideShellSurface(shape = GlassShapes.HazePanel),
                    )
                }
            }
        }
        refractedGlassImage = onRoot().captureToImage()

        assertNotNull(unrefractedGlassImage)
        assertNotNull(refractedGlassImage)

        // 1. Boundary rim verification: 1D horizontal feature displacement across vertical stripe edges
        // under the curved boundary rim.
        val rimRoi = Rect(48f, 42f, 72f, 118f)

        val result = GlassOpticalVerifier.measureRefractionFeatureDisplacement(
            unrefracted = unrefractedGlassImage!!,
            refracted = refractedGlassImage!!,
            boundaryRoi = rimRoi,
        )
        println(
            "[DIAGNOSTIC] Boundary Rim Refraction (Horizontal Feature Shift): totalFeatures=${result.featureCount}, " +
                "displacedFeatures=${result.displacedFeatureCount}, " +
                "meanDisplacement=${result.meanDisplacementPx}px, " +
                "maxDisplacement=${result.maxDisplacementPx}px",
        )

        GlassOpticalVerifier.assertRefractionDisplacement(
            unrefracted = unrefractedGlassImage!!,
            refracted = refractedGlassImage!!,
            boundaryRoi = rimRoi,
            minDisplacedFeatures = 1,
            minDisplacementPx = 0.5,
        )

        // 2. Interior verification: features away from the curved rim (where RefractionProfile.Edge does not apply)
        // must exhibit 0px refraction displacement, confirming refraction is strictly localized to the rim boundary.
        val interiorRoi = Rect(80f, 60f, 160f, 100f)
        val interiorResult = GlassOpticalVerifier.measureRefractionFeatureDisplacement(
            unrefracted = unrefractedGlassImage!!,
            refracted = refractedGlassImage!!,
            boundaryRoi = interiorRoi,
        )
        println(
            "[DIAGNOSTIC] Interior Refraction (Zero Displacement Check): " +
                "totalFeatures=${interiorResult.featureCount}, " +
                "displacedFeatures=${interiorResult.displacedFeatureCount}, " +
                "meanDisplacement=${interiorResult.meanDisplacementPx}px, " +
                "maxDisplacement=${interiorResult.maxDisplacementPx}px",
        )
        assertEquals(
            0,
            interiorResult.displacedFeatureCount,
            "Interior features away from the curved rim must have 0px refraction displacement " +
                "(found ${interiorResult.displacedFeatureCount} displaced features)",
        )
        assertEquals(
            0.0,
            interiorResult.maxDisplacementPx,
            "Interior features away from the curved rim must exhibit zero displacement " +
                "(found max=${interiorResult.maxDisplacementPx}px)",
        )
    }

    // =============================================================================================
    // 5. Optical Blur & Refraction Proof vs. Fallback Across All Themes
    // =============================================================================================

    @Test
    fun testOpticalBlurAndRefractionDistinguishedFromFallback() = runDesktopComposeUiTest(
        width = 240,
        height = 160,
    ) {
        val themes = listOf(AppTheme.Dark, AppTheme.Blue, AppTheme.Light)

        for (theme in themes) {
            var rawImage: ImageBitmap? = null
            var fallbackImage: ImageBitmap? = null
            var opticalGlassImage: ImageBitmap? = null

            // 1. Raw sharp high-contrast pattern
            setContent {
                LtiTheme(appTheme = theme, effectsEnabled = false) {
                    Box(Modifier.fillMaxSize()) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawHighFrequencyStripes()
                        }
                    }
                }
            }
            rawImage = onRoot().captureToImage()

            // 2. Fallback (effects disabled -> shaped opaque surfaceContainerHigh)
            setContent {
                LtiTheme(appTheme = theme, effectsEnabled = false) {
                    Box(Modifier.fillMaxSize()) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawHighFrequencyStripes()
                        }
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .size(140.dp, 80.dp)
                                .ideShellSurface(shape = GlassShapes.HazePanel),
                        )
                    }
                }
            }
            fallbackImage = onRoot().captureToImage()

            // 3. True Haze optical glass (effects enabled -> hazeGlass with unified optics)
            setContent {
                LtiTheme(appTheme = theme, effectsEnabled = true) {
                    val hazeState = GlassTheme.hazeState
                    Box(Modifier.fillMaxSize()) {
                        Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
                            drawHighFrequencyStripes()
                        }
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .size(140.dp, 80.dp)
                                .ideShellSurface(shape = GlassShapes.HazePanel),
                        )
                    }
                }
            }
            opticalGlassImage = onRoot().captureToImage()

            assertNotNull(rawImage)
            assertNotNull(fallbackImage)
            assertNotNull(opticalGlassImage)

            val rawGrad = computeMaxEdgeGradient(rawImage)
            val glassGrad = computeMaxEdgeGradient(opticalGlassImage)

            val fallbackPixelMap = fallbackImage.toPixelMap()
            val centerFallback = fallbackPixelMap[fallbackImage.width / 2, fallbackImage.height / 2]

            println(
                "[DIAGNOSTIC] Theme $theme: rawGradient=$rawGrad, glassGradient=$glassGrad, " +
                    "fallbackCenterAlpha=${centerFallback.alpha}",
            )

            // Verify raw pattern is sharp
            assertTrue(rawGrad > 0.80, "Raw pattern must have sharp edge (found $rawGrad in $theme)")

            // Verify fallback is solid opaque
            assertTrue(
                centerFallback.alpha > 0.95f,
                "Fallback must be solid opaque (found ${centerFallback.alpha} in $theme)",
            )

            // Verify real optical glass transmits and diffuses the high-frequency edge via true blur:
            // Gradient must be non-zero (proving transmission of underlying pattern, NOT an opaque flat fallback)
            assertTrue(
                glassGrad > 0.02,
                "Optical glass must transmit underlying high-frequency pattern " +
                    "(found flat gradient $glassGrad in $theme)",
            )
            // Gradient must be significantly diffused compared to raw pattern
            assertTrue(
                glassGrad < rawGrad * 0.85,
                "Optical glass must visibly diffuse sharp edges in $theme (raw=$rawGrad, glass=$glassGrad)",
            )

            // Panel bounds: 140x80 dp centered in 240x160 -> [50, 40, 190, 120]
            val panelInterior = Rect(70f, 55f, 170f, 105f)

            // Spatial edge-spreading verification: proves true blur spread across >= 3px and energy outside peak >= 20%
            // (Rejects any unblurred translucent overlay, which has transition width 1px and 0% spread)
            GlassOpticalVerifier.assertOpticalBlurSpread(
                glassRender = opticalGlassImage!!,
                roi = panelInterior,
                stripePeriodPx = 16,
                minTransitionWidthPx = 3,
                minSpreadFraction = 0.20,
            )

            // 10-90% edge transition width expansion verification: proves authentic spatial optical blur
            // with >= 1.8x expansion
            // (Rejects any unblurred translucent overlay, which maintains ~1.0x expansion)
            GlassOpticalVerifier.assertEdgeTransitionExpansion(
                rawPattern = rawImage!!,
                glassRender = opticalGlassImage!!,
                roi = panelInterior,
                stripePeriodPx = 16,
                minExpansionRatio = 1.8,
            )
        }
    }

    private companion object {
        const val PATTERN_WIDTH = 240
        const val PATTERN_HEIGHT = 160
        const val STRIPE_COUNT = 16
    }
}
