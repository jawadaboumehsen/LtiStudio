/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.testing.optical

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GlassOpticalVerifierTest {

    private fun createSolidBitmap(width: Int, height: Int, argbColor: Int): ImageBitmap {
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32(width, height, ColorAlphaType.PREMUL))
        bitmap.erase(argbColor)
        return Image.makeFromBitmap(bitmap).toComposeImageBitmap()
    }

    private fun createStripeBitmap(width: Int, height: Int, stripeWidth: Int, isBlurred: Boolean): ImageBitmap {
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32(width, height, ColorAlphaType.PREMUL))
        val canvas = Canvas(bitmap)

        if (!isBlurred) {
            // Sharp alternating black and white stripes
            val blackPaint = Paint().apply { color = 0xFF000000.toInt() }
            val whitePaint = Paint().apply { color = 0xFFFFFFFF.toInt() }
            for (x in 0 until width step (stripeWidth * 2)) {
                canvas.drawRect(
                    org.jetbrains.skia.Rect.makeXYWH(x.toFloat(), 0f, stripeWidth.toFloat(), height.toFloat()),
                    blackPaint,
                )
                canvas.drawRect(
                    org.jetbrains.skia.Rect.makeXYWH(
                        (x + stripeWidth).toFloat(),
                        0f,
                        stripeWidth.toFloat(),
                        height.toFloat(),
                    ),
                    whitePaint,
                )
            }
        } else {
            // Smoothly diffused gradient transitioning over 16 pixels (simulating blur)
            for (x in 0 until width) {
                val cycle = x % (stripeWidth * 2)
                val factor = if (cycle < stripeWidth) {
                    cycle.toFloat() / stripeWidth
                } else {
                    1f - ((cycle - stripeWidth).toFloat() / stripeWidth)
                }
                val gray = (factor * 255).toInt().coerceIn(0, 255)
                val argb = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                val paint = Paint().apply { color = argb }
                canvas.drawRect(org.jetbrains.skia.Rect.makeXYWH(x.toFloat(), 0f, 1f, height.toFloat()), paint)
            }
        }

        return Image.makeFromBitmap(bitmap).toComposeImageBitmap()
    }

    @Test
    fun testRelativeLuminanceAndContrastCalculations() {
        val black = Color(0f, 0f, 0f, 1f)
        val white = Color(1f, 1f, 1f, 1f)

        val blackLum = GlassOpticalVerifier.relativeLuminance(black)
        val whiteLum = GlassOpticalVerifier.relativeLuminance(white)

        assertEquals(0.0, blackLum, 0.001)
        assertEquals(1.0, whiteLum, 0.001)

        val contrast = GlassOpticalVerifier.contrastRatio(black, white)
        assertEquals(21.0, contrast, 0.01)
    }

    @Test
    fun testOpticalDiffusionPassesOnBlurredPattern() {
        val raw = createStripeBitmap(100, 100, stripeWidth = 16, isBlurred = false)
        val blurred = createStripeBitmap(100, 100, stripeWidth = 16, isBlurred = true)

        // Blurred pattern should easily pass retention ratio <= 0.40
        GlassOpticalVerifier.assertOpticalDiffusion(
            rawPattern = raw,
            glassRender = blurred,
            maxGradientRetentionRatio = 0.40,
        )
    }

    @Test
    fun testOpticalDiffusionFailsOnUnblurredRawPattern() {
        val raw = createStripeBitmap(100, 100, stripeWidth = 16, isBlurred = false)

        val error = assertFailsWith<AssertionError> {
            GlassOpticalVerifier.assertOpticalDiffusion(
                rawPattern = raw,
                glassRender = raw,
                maxGradientRetentionRatio = 0.40,
            )
        }
        assertTrue(error.message!!.contains("Optical diffusion failure"))
    }

    @Test
    fun testBackdropModulationPassesWhenModulatedWithEffectsOn() {
        // Backdrop A: Light Gray, Backdrop B: Dark Gray
        val cardA = createSolidBitmap(100, 100, 0xFFE0E0E0.toInt())
        val cardB = createSolidBitmap(100, 100, 0xFF404040.toInt())
        val bounds = Rect(0f, 0f, 100f, 100f)

        GlassOpticalVerifier.assertBackdropModulation(
            cardOverBackdropA = cardA,
            cardOverBackdropB = cardB,
            cardBounds = bounds,
            insetPx = 8,
            minDeltaModulation = 0.03,
            effectsEnabled = true,
        )
    }

    @Test
    fun testBackdropModulationFailsWhenOpaqueFlatSlabWithEffectsOn() {
        // Negative control: both renders identical (opaque flat card)
        val flatCard = createSolidBitmap(100, 100, 0xFF303030.toInt())
        val bounds = Rect(0f, 0f, 100f, 100f)

        val error = assertFailsWith<AssertionError> {
            GlassOpticalVerifier.assertBackdropModulation(
                cardOverBackdropA = flatCard,
                cardOverBackdropB = flatCard,
                cardBounds = bounds,
                insetPx = 8,
                minDeltaModulation = 0.03,
                effectsEnabled = true,
            )
        }
        assertTrue(error.message!!.contains("Backdrop modulation failure (effects ON)"))
    }

    @Test
    fun testBackdropModulationPassesWhenInvariantWithEffectsOff() {
        val fallbackColor = Color(0xFF333333)
        val flatCard = createSolidBitmap(100, 100, 0xFF333333.toInt())
        val bounds = Rect(0f, 0f, 100f, 100f)

        GlassOpticalVerifier.assertBackdropModulation(
            cardOverBackdropA = flatCard,
            cardOverBackdropB = flatCard,
            cardBounds = bounds,
            insetPx = 8,
            effectsEnabled = false,
            expectedSolidFallback = fallbackColor,
        )
    }

    @Test
    fun testSolidFallbackColorPassesMatchingColor() {
        val color = Color(0.2f, 0.3f, 0.4f, 1f)
        val argb =
            (0xFF shl 24) or ((0.2f * 255).toInt() shl 16) or ((0.3f * 255).toInt() shl 8) or (0.4f * 255).toInt()
        val image = createSolidBitmap(60, 60, argb)
        val bounds = Rect(0f, 0f, 60f, 60f)

        GlassOpticalVerifier.assertSolidFallbackColor(
            cardRender = image,
            cardBounds = bounds,
            expectedColor = color,
            tolerance = 0.02f,
            insetPx = 4,
        )
    }

    @Test
    fun testAdjacentTextContrastPassesHighContrastAndFailsLowContrast() {
        val darkBg = createSolidBitmap(60, 60, 0xFF121212.toInt())
        val whiteText = Color(1f, 1f, 1f, 1f)
        val darkGrayText = Color(0.15f, 0.15f, 0.15f, 1f)
        val bounds = Rect(0f, 0f, 60f, 60f)

        // White text on dark background should pass WCAG AA >= 4.5:1
        GlassOpticalVerifier.assertAdjacentTextContrast(
            textToken = whiteText,
            background = darkBg,
            adjacentBounds = bounds,
            minRatio = 4.5,
        )

        // Dark gray text on dark background should fail
        val error = assertFailsWith<AssertionError> {
            GlassOpticalVerifier.assertAdjacentTextContrast(
                textToken = darkGrayText,
                background = darkBg,
                adjacentBounds = bounds,
                minRatio = 4.5,
            )
        }
        assertTrue(error.message!!.contains("WCAG text contrast failure"))
    }

    @Test
    fun testOpticalBlurSpreadPassesOnBlurredAndFailsOnUnblurredTranslucent() {
        val blurred = createStripeBitmap(100, 100, stripeWidth = 16, isBlurred = true)
        val roi = Rect(16f, 16f, 84f, 84f)

        // 1. Genuine blurred pattern must pass edge spreading check
        GlassOpticalVerifier.assertOpticalBlurSpread(
            glassRender = blurred,
            roi = roi,
            stripePeriodPx = 16,
            minTransitionWidthPx = 3,
            minSpreadFraction = 0.20,
        )

        // 2. Unblurred translucent overlay: sharp stripes tinted with flat 50% white
        val unblurredTranslucent =
            createTranslucentStripeBitmap(100, 100, stripeWidth = 16, alpha = 0.6f, tintArgb = 0xFF888888.toInt())
        val error = assertFailsWith<AssertionError> {
            GlassOpticalVerifier.assertOpticalBlurSpread(
                glassRender = unblurredTranslucent,
                roi = roi,
                stripePeriodPx = 16,
                minTransitionWidthPx = 3,
                minSpreadFraction = 0.20,
            )
        }
        assertTrue(error.message!!.contains("Optical blur failure: edge spreading is insufficient"))
    }

    @Test
    fun testRefractionDisplacementPassesWhenFeaturesDisplaced() {
        val unrefracted = createFeatureBitmap(60, 60, featureX = 20f, isShifted = false)
        val refracted = createFeatureBitmap(60, 60, featureX = 20f, isShifted = true, shiftPx = 2f)
        val roi = Rect(10f, 10f, 50f, 50f)

        GlassOpticalVerifier.assertRefractionDisplacement(
            unrefracted = unrefracted,
            refracted = refracted,
            boundaryRoi = roi,
            minDisplacedFeatures = 1,
            minDisplacementPx = 0.5,
        )
    }

    @Test
    fun testRefractionDisplacementFailsWhenFeaturesOnlyTintedWithoutSpatialShift() {
        val unrefracted = createFeatureBitmap(60, 60, featureX = 20f, isShifted = false)
        // Flat gray tint on unshifted feature
        val tinted = createFeatureBitmap(60, 60, featureX = 20f, isShifted = false, tintAlpha = 0.5f)
        val roi = Rect(10f, 10f, 50f, 50f)

        val error = assertFailsWith<AssertionError> {
            GlassOpticalVerifier.assertRefractionDisplacement(
                unrefracted = unrefracted,
                refracted = tinted,
                boundaryRoi = roi,
                minDisplacedFeatures = 1,
                minDisplacementPx = 0.5,
            )
        }
        assertTrue(error.message!!.contains("Refraction displacement failure: displaced feature count"))
    }

    @Test
    fun testRefractionDisplacementFailsOnFlatSolidFieldsWithoutFeatures() {
        val solid1 = createSolidBitmap(40, 40, 0xFF000000.toInt())
        val solid2 = createSolidBitmap(40, 40, 0xFF444444.toInt())
        val roi = Rect(0f, 0f, 40f, 40f)

        val error = assertFailsWith<AssertionError> {
            GlassOpticalVerifier.assertRefractionDisplacement(
                unrefracted = solid1,
                refracted = solid2,
                boundaryRoi = roi,
                minDisplacedFeatures = 1,
            )
        }
        assertTrue(error.message!!.contains("no distinct visual features found in unrefracted image within ROI"))
    }

    @Test
    fun testEdgeTransitionExpansionPassesOnOpticalBlurAndFailsOnTranslucentOverlay() {
        val raw = createStripeBitmap(100, 100, stripeWidth = 16, isBlurred = false)
        val blurred = createStripeBitmap(100, 100, stripeWidth = 16, isBlurred = true)
        val roi = Rect(16f, 16f, 84f, 84f)

        // 1. Genuine blur must have 10-90% transition expansion >= 1.8x
        GlassOpticalVerifier.assertEdgeTransitionExpansion(
            rawPattern = raw,
            glassRender = blurred,
            roi = roi,
            stripePeriodPx = 16,
            minExpansionRatio = 1.8,
        )

        // 2. Unblurred translucent overlay retains 1px sharp transition and must fail
        val unblurredTranslucent =
            createTranslucentStripeBitmap(100, 100, stripeWidth = 16, alpha = 0.6f, tintArgb = 0xFF888888.toInt())
        val error = assertFailsWith<AssertionError> {
            GlassOpticalVerifier.assertEdgeTransitionExpansion(
                rawPattern = raw,
                glassRender = unblurredTranslucent,
                roi = roi,
                stripePeriodPx = 16,
                minExpansionRatio = 1.8,
            )
        }
        assertTrue(error.message!!.contains("Optical blur failure: 10-90% edge transition expansion is insufficient"))
    }

    private fun createFeatureBitmap(
        width: Int,
        height: Int,
        featureX: Float,
        featureWidth: Float = 2f,
        isShifted: Boolean = false,
        shiftPx: Float = 2f,
        tintAlpha: Float = 1.0f,
    ): ImageBitmap {
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32(width, height, ColorAlphaType.PREMUL))
        val canvas = Canvas(bitmap)
        canvas.drawRect(
            org.jetbrains.skia.Rect.makeXYWH(0f, 0f, width.toFloat(), height.toFloat()),
            Paint().apply {
                color =
                    0xFF000000.toInt()
            },
        )
        val effectiveX = if (isShifted) featureX + shiftPx else featureX
        val colorAlpha = (tintAlpha * 255).toInt().coerceIn(0, 255)
        val paint = Paint().apply {
            color = (colorAlpha shl 24) or 0x00FFFFFF
        }
        canvas.drawRect(org.jetbrains.skia.Rect.makeXYWH(effectiveX, 0f, featureWidth, height.toFloat()), paint)
        return Image.makeFromBitmap(bitmap).toComposeImageBitmap()
    }

    private fun createTranslucentStripeBitmap(
        width: Int,
        height: Int,
        stripeWidth: Int,
        alpha: Float,
        tintArgb: Int,
    ): ImageBitmap {
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32(width, height, ColorAlphaType.PREMUL))
        val canvas = Canvas(bitmap)

        val blackPaint = Paint().apply { color = 0xFF000000.toInt() }
        val whitePaint = Paint().apply { color = 0xFFFFFFFF.toInt() }
        for (x in 0 until width step (stripeWidth * 2)) {
            canvas.drawRect(
                org.jetbrains.skia.Rect.makeXYWH(x.toFloat(), 0f, stripeWidth.toFloat(), height.toFloat()),
                blackPaint,
            )
            canvas.drawRect(
                org.jetbrains.skia.Rect.makeXYWH(
                    (x + stripeWidth).toFloat(),
                    0f,
                    stripeWidth.toFloat(),
                    height.toFloat(),
                ),
                whitePaint,
            )
        }

        val tintPaint = Paint().apply {
            color = tintArgb
            this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        }
        canvas.drawRect(org.jetbrains.skia.Rect.makeXYWH(0f, 0f, width.toFloat(), height.toFloat()), tintPaint)

        return Image.makeFromBitmap(bitmap).toComposeImageBitmap()
    }
}
