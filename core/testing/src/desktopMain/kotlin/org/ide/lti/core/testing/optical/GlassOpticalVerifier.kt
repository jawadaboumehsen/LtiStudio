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
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * High-precision behavioral optical verification engine for Compose glass UI components.
 *
 * Provides algorithmic assertions that evaluate real optical behavior:
 * 1. Spatial High-Frequency Diffusion (∇I): Asserts that sharp backdrop edges are blurred.
 * 2. Backdrop Modulation Response (ΔM): Asserts that the card interior transmits underlying
 *    backdrop changes when effects are active, and remains invariant when effects are disabled.
 * 3. Solid Fallback Color: Asserts that fallback rendering produces uniform token colors.
 * 4. Worst-case WCAG AA Text Contrast: Asserts readability across heterogeneous glass interiors.
 */
object GlassOpticalVerifier {

    /**
     * Linearizes an sRGB channel value in the range [0.0, 1.0] per WCAG 2.x specifications.
     */
    fun linearize(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.03928) {
            c / 12.92
        } else {
            ((c + 0.055) / 1.055).pow(2.4)
        }
    }

    /**
     * Computes the WCAG 2.x relative luminance of a [Color] in range [0.0, 1.0].
     * L = 0.2126 * R_lin + 0.7152 * G_lin + 0.0722 * B_lin
     */
    fun relativeLuminance(color: Color): Double {
        val rLin = linearize(color.red)
        val gLin = linearize(color.green)
        val bLin = linearize(color.blue)
        return 0.2126 * rLin + 0.7152 * gLin + 0.0722 * bLin
    }

    /**
     * Computes the WCAG 2.x contrast ratio between two colors.
     * Contrast Ratio = (L_lighter + 0.05) / (L_darker + 0.05)
     */
    fun contrastRatio(color1: Color, color2: Color): Double {
        val lum1 = relativeLuminance(color1)
        val lum2 = relativeLuminance(color2)
        val lighter = max(lum1, lum2)
        val darker = min(lum1, lum2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * Asserts that high-frequency spatial transitions in [rawPattern] are diffused in [glassRender].
     *
     * Over high-contrast calibration stripes:
     * ∇I(x, y) = |Luminance(x+1, y) - Luminance(x, y)|
     * MaxGradient_glass <= maxGradientRetentionRatio * MaxGradient_raw
     *
     * @param rawPattern The unblurred calibration pattern render.
     * @param glassRender The rendered glass component over the calibration pattern.
     * @param roi Region of interest to sample inside the component (null uses entire image inset by 16px).
     * @param maxGradientRetentionRatio Maximum ratio of retained gradient. Default is 0.40.
     */
    fun assertOpticalDiffusion(
        rawPattern: ImageBitmap,
        glassRender: ImageBitmap,
        roi: Rect? = null,
        maxGradientRetentionRatio: Double = 0.40,
    ) {
        val effectiveRoi = roi ?: Rect(
            left = 16f,
            top = 16f,
            right = (glassRender.width - 16).coerceAtLeast(16).toFloat(),
            bottom = (glassRender.height - 16).coerceAtLeast(16).toFloat(),
        )

        val startX = effectiveRoi.left.toInt().coerceIn(0, glassRender.width - 1)
        val endX = effectiveRoi.right.toInt().coerceIn(startX + 1, glassRender.width)
        val startY = effectiveRoi.top.toInt().coerceIn(0, glassRender.height)
        val endY = effectiveRoi.bottom.toInt().coerceIn(startY + 1, glassRender.height)

        val rawMap = rawPattern.toPixelMap()
        val glassMap = glassRender.toPixelMap()

        var maxRawGrad = 0.0
        var maxGlassGrad = 0.0

        for (y in startY until endY) {
            for (x in startX until endX - 1) {
                val rawL1 = relativeLuminance(rawMap[x, y])
                val rawL2 = relativeLuminance(rawMap[x + 1, y])
                val rawGrad = abs(rawL2 - rawL1)
                if (rawGrad > maxRawGrad) maxRawGrad = rawGrad

                val glassL1 = relativeLuminance(glassMap[x, y])
                val glassL2 = relativeLuminance(glassMap[x + 1, y])
                val glassGrad = abs(glassL2 - glassL1)
                if (glassGrad > maxGlassGrad) maxGlassGrad = glassGrad
            }
        }

        val retentionRatio = if (maxRawGrad > 0.0) maxGlassGrad / maxRawGrad else 0.0
        if (retentionRatio > maxGradientRetentionRatio) {
            throw AssertionError(
                "Optical diffusion failure: high-frequency gradients retained at ratio " +
                    "${"%.4f".format(retentionRatio)} (maxGlassGrad=${"%.4f".format(maxGlassGrad)}, " +
                    "maxRawGrad=${"%.4f".format(maxRawGrad)}), exceeding threshold $maxGradientRetentionRatio. " +
                    "Component is not applying sufficient spatial optical blur.",
            )
        }
    }

    data class EdgeSpreadResult(val transitionWidthPx: Int, val spreadFraction: Double, val peakGradient: Double)

    /**
     * Measures the spatial edge spreading of vertical stripe boundaries in [image] within [roi].
     *
     * Examines transitions across stripe boundaries (every [stripePeriodPx] pixels).
     * Computes:
     * 1. [transitionWidthPx]: average number of contiguous pixels around each boundary where gradient
     *    exceeds 15% of the local peak gradient.
     * 2. [spreadFraction]: ratio of gradient energy outside the single peak transition pixel to total gradient energy
     *    within the window [-windowPx, +windowPx].
     *
     * For unblurred images (including flat unblurred translucent overlays), transitionWidthPx is 1 and
     * spreadFraction is ~0.00.
     * For genuinely blurred optical glass, transitionWidthPx >= 3 (typically 4-10) and spreadFraction >= 0.20.
     */
    fun measureEdgeSpread(
        image: ImageBitmap,
        roi: Rect,
        stripePeriodPx: Int = 16,
        windowPx: Int = 6,
    ): EdgeSpreadResult {
        val pixelMap = image.toPixelMap()
        val startX = roi.left.toInt().coerceIn(0, image.width - 1)
        val endX = roi.right.toInt().coerceIn(startX + 1, image.width)
        val startY = roi.top.toInt().coerceIn(0, image.height)
        val endY = roi.bottom.toInt().coerceIn(startY + 1, image.height)

        var totalWidth = 0.0
        var totalSpreadFraction = 0.0
        var totalPeak = 0.0
        var boundaryCount = 0

        val firstBoundary = ((startX / stripePeriodPx) + 1) * stripePeriodPx
        for (bx in firstBoundary until endX - windowPx step stripePeriodPx) {
            if (bx - windowPx < startX) continue
            var sumWidthForBoundary = 0
            var sumSpreadForBoundary = 0.0
            var sumPeakForBoundary = 0.0
            var sampleRows = 0

            for (y in startY until endY step 4) {
                val rowSpread = measureRowSpread(pixelMap, y, bx, windowPx, image.width) ?: continue
                sumWidthForBoundary += rowSpread.width
                sumSpreadForBoundary += rowSpread.spreadFrac
                sumPeakForBoundary += rowSpread.peak
                sampleRows++
            }

            if (sampleRows > 0) {
                totalWidth += sumWidthForBoundary.toDouble() / sampleRows
                totalSpreadFraction += sumSpreadForBoundary / sampleRows
                totalPeak += sumPeakForBoundary / sampleRows
                boundaryCount++
            }
        }

        return if (boundaryCount > 0) {
            EdgeSpreadResult(
                transitionWidthPx = (totalWidth / boundaryCount + 0.5).toInt(),
                spreadFraction = totalSpreadFraction / boundaryCount,
                peakGradient = totalPeak / boundaryCount,
            )
        } else {
            EdgeSpreadResult(1, 0.0, 0.0)
        }
    }

    /**
     * Asserts that [glassRender] exhibits authentic optical edge spreading (spatial blur),
     * and is NOT an unblurred translucent overlay or raw pattern.
     */
    fun assertOpticalBlurSpread(
        glassRender: ImageBitmap,
        roi: Rect,
        stripePeriodPx: Int = 16,
        minTransitionWidthPx: Int = 3,
        minSpreadFraction: Double = 0.20,
    ) {
        val result = measureEdgeSpread(glassRender, roi, stripePeriodPx)
        if (result.transitionWidthPx < minTransitionWidthPx || result.spreadFraction < minSpreadFraction) {
            throw AssertionError(
                "Optical blur failure: edge spreading is insufficient (transitionWidth=${result.transitionWidthPx}px " +
                    "(min required: ${minTransitionWidthPx}px), spreadFraction=${"%.4f".format(
                        result.spreadFraction,
                    )} " +
                    "(min required: $minSpreadFraction), peakGrad=${"%.4f".format(result.peakGradient)}). " +
                    "Component exhibits sharp unblurred transitions (such as an unblurred translucent fill), " +
                    "not authentic spatial optical blur.",
            )
        }
    }

    data class EdgeTransitionExpansionResult(
        val rawTransitionWidth10to90Px: Double,
        val blurredTransitionWidth10to90Px: Double,
        val expansionRatio: Double,
        val edgeCount: Int,
    )

    /**
     * Measures the 10%-90% rise-distance (transition width) across step transitions between [rawPattern]
     * and [glassRender] within [roi].
     *
     * In optics, the 10%-90% transition width measures the spatial distance over which an edge rises from
     * 10% to 90% of its full luminance step. Authentic optical blur expands this transition by >= 1.8x,
     * whereas unblurred translucent overlays retain a 1-pixel transition (~1.0x expansion).
     */
    fun measureEdgeTransitionExpansion(
        rawPattern: ImageBitmap,
        glassRender: ImageBitmap,
        roi: Rect,
        stripePeriodPx: Int = 16,
        windowPx: Int = 8,
    ): EdgeTransitionExpansionResult {
        val rawMap = rawPattern.toPixelMap()
        val glassMap = glassRender.toPixelMap()

        val startX = roi.left.toInt().coerceIn(0, rawPattern.width - 1)
        val endX = roi.right.toInt().coerceIn(startX + 1, rawPattern.width)
        val startY = roi.top.toInt().coerceIn(0, rawPattern.height)
        val endY = roi.bottom.toInt().coerceIn(startY + 1, rawPattern.height)

        var totalRawWidth = 0.0
        var totalBlurredWidth = 0.0
        var edgeCount = 0

        val firstBoundary = ((startX / stripePeriodPx) + 1) * stripePeriodPx
        for (bx in firstBoundary until endX - windowPx step stripePeriodPx) {
            if (bx - windowPx < startX) continue

            for (y in startY until endY step 4) {
                val rawW = measureSingle10to90Width(rawMap, bx, y, windowPx)
                val glassW = measureSingle10to90Width(glassMap, bx, y, windowPx)

                if (rawW > 0.0 && glassW > 0.0) {
                    totalRawWidth += rawW
                    totalBlurredWidth += glassW
                    edgeCount++
                }
            }
        }

        val avgRaw = if (edgeCount > 0) totalRawWidth / edgeCount else 1.0
        val avgBlurred = if (edgeCount > 0) totalBlurredWidth / edgeCount else 1.0
        val ratio = if (avgRaw > 0.0) avgBlurred / avgRaw else 1.0

        return EdgeTransitionExpansionResult(
            rawTransitionWidth10to90Px = avgRaw,
            blurredTransitionWidth10to90Px = avgBlurred,
            expansionRatio = ratio,
            edgeCount = edgeCount,
        )
    }

    private data class RowSpread(val width: Int, val spreadFrac: Double, val peak: Double)

    /** Measures edge transition width and spread fraction for a single horizontal row across the boundary. */
    private fun measureRowSpread(pixelMap: PixelMap, y: Int, bx: Int, windowPx: Int, imageWidth: Int): RowSpread? {
        val grads = DoubleArray(2 * windowPx + 1)
        var maxG = 0.0
        var maxIdx = windowPx
        var sumG = 0.0

        for (offset in -windowPx..windowPx) {
            val px = bx + offset
            if (px + 1 >= imageWidth) continue
            val l1 = relativeLuminance(pixelMap[px, y])
            val l2 = relativeLuminance(pixelMap[px + 1, y])
            val g = abs(l2 - l1)
            val idx = offset + windowPx
            grads[idx] = g
            sumG += g
            if (g > maxG) {
                maxG = g
                maxIdx = idx
            }
        }

        if (maxG <= 0.008 || sumG <= 0.0) return null

        var width = 1
        var left = maxIdx - 1
        while (left >= 0 && grads[left] >= 0.15 * maxG) {
            width++
            left--
        }
        var right = maxIdx + 1
        while (right < grads.size && grads[right] >= 0.15 * maxG) {
            width++
            right++
        }

        val energyOutsidePeak = (sumG - maxG).coerceAtLeast(0.0)
        val spreadFrac = energyOutsidePeak / sumG

        return RowSpread(width, spreadFrac, maxG)
    }

    /** Finds the minimum and maximum luminance within the specified window. */
    private fun findLuminanceRange(map: PixelMap, bx: Int, y: Int, windowPx: Int): Pair<Double, Double> {
        var minL = 1.0
        var maxL = 0.0
        for (offset in -windowPx..windowPx) {
            val px = bx + offset
            if (px in 0 until map.width) {
                val l = relativeLuminance(map[px, y])
                if (l < minL) minL = l
                if (l > maxL) maxL = l
            }
        }
        return Pair(minL, maxL)
    }

    /** Checks whether the transition between two luminance values crosses the target. */
    private fun crosses(lum1: Double, lum2: Double, target: Double): Boolean =
        (lum1 <= target && lum2 >= target) || (lum1 >= target && lum2 <= target)

    /** Computes the linearly interpolated coordinate where the luminance curve crosses the target. */
    private fun interpolateCrossing(px1: Int, lum1: Double, lum2: Double, target: Double): Double {
        val diff = abs(lum2 - lum1)
        val frac = if (diff > 1e-5) abs(target - lum1) / diff else 0.5
        return px1 + frac
    }

    private fun measureSingle10to90Width(map: PixelMap, bx: Int, y: Int, windowPx: Int): Double {
        val (minL, maxL) = findLuminanceRange(map, bx, y, windowPx)
        val deltaL = maxL - minL
        if (deltaL < 0.05) return 0.0

        val l10 = minL + 0.10 * deltaL
        val l90 = minL + 0.90 * deltaL

        val isRising = relativeLuminance(map[(bx + windowPx).coerceAtMost(map.width - 1), y]) >
            relativeLuminance(map[(bx - windowPx).coerceAtLeast(0), y])

        var x10 = -1.0
        var x90 = -1.0

        for (offset in -windowPx until windowPx) {
            val px1 = bx + offset
            val px2 = px1 + 1
            if (px1 < 0 || px2 >= map.width) continue
            val lum1 = relativeLuminance(map[px1, y])
            val lum2 = relativeLuminance(map[px2, y])

            val (targetA, targetB) = if (isRising) Pair(l10, l90) else Pair(l90, l10)

            if (x10 < 0.0 && crosses(lum1, lum2, targetA)) {
                x10 = interpolateCrossing(px1, lum1, lum2, targetA)
            }
            if (x90 < 0.0 && crosses(lum1, lum2, targetB)) {
                x90 = interpolateCrossing(px1, lum1, lum2, targetB)
            }
        }

        return if (x10 >= 0.0 && x90 >= 0.0) abs(x90 - x10) else 0.0
    }

    /**
     * Asserts that [glassRender] exhibits authentic optical blur expansion over [rawPattern]
     * with 10%-90% transition width expansion ratio >= [minExpansionRatio] (default 1.8x).
     */
    fun assertEdgeTransitionExpansion(
        rawPattern: ImageBitmap,
        glassRender: ImageBitmap,
        roi: Rect,
        stripePeriodPx: Int = 16,
        minExpansionRatio: Double = 1.8,
    ) {
        val result = measureEdgeTransitionExpansion(rawPattern, glassRender, roi, stripePeriodPx)
        if (result.edgeCount == 0 || result.expansionRatio < minExpansionRatio) {
            throw AssertionError(
                "Optical blur failure: 10-90% edge transition expansion is insufficient " +
                    "(raw=${"%.2f".format(result.rawTransitionWidth10to90Px)}px, " +
                    "blurred=${"%.2f".format(result.blurredTransitionWidth10to90Px)}px, " +
                    "expansionRatio=${"%.2f".format(result.expansionRatio)}x, required >= ${minExpansionRatio}x). " +
                    "Component does not exhibit authentic spatial optical blur expansion.",
            )
        }
    }

    data class RefractionFeatureDisplacementResult(
        val featureCount: Int,
        val displacedFeatureCount: Int,
        val meanDisplacementPx: Double,
        val maxDisplacementPx: Double,
    )

    /**
     * Measures the spatial displacement (in pixels) of localized high-gradient visual features
     * (such as grid lines, stripe edges, or points) between [unrefracted] and [refracted] within [boundaryRoi].
     *
     * Identifies feature peak coordinates in [unrefracted] and tracks their displaced positions in [refracted].
     * Unrefracted solid colors or flat unrefracted translucent tints yield 0 displaced features.
     * True optical refraction along glass boundaries or curved lenses bends light, shifting feature positions.
     */
    fun measureRefractionFeatureDisplacement(
        unrefracted: ImageBitmap,
        refracted: ImageBitmap,
        boundaryRoi: Rect,
        minFeatureGradient: Double = 0.03,
        searchRadiusPx: Int = 8,
        displacementThresholdPx: Double = 0.5,
    ): RefractionFeatureDisplacementResult {
        val map1 = unrefracted.toPixelMap()
        val map2 = refracted.toPixelMap()

        val startX = boundaryRoi.left.toInt().coerceIn(0, unrefracted.width - 1)
        val endX = boundaryRoi.right.toInt().coerceIn(startX + 1, unrefracted.width)
        val startY = boundaryRoi.top.toInt().coerceIn(0, unrefracted.height - 1)
        val endY = boundaryRoi.bottom.toInt().coerceIn(startY + 1, unrefracted.height)

        var totalFeatures = 0
        var displacedFeatures = 0
        var sumDisplacement = 0.0
        var maxDisplacement = 0.0

        for (y in startY until endY step 2) {
            for (x in startX + 1 until endX - 2) {
                val signedG = relativeLuminance(map1[x + 1, y]) - relativeLuminance(map1[x, y])
                val gCenter = abs(signedG)
                val gLeft = abs(relativeLuminance(map1[x, y]) - relativeLuminance(map1[x - 1, y]))
                val gRight = abs(relativeLuminance(map1[x + 2, y]) - relativeLuminance(map1[x + 1, y]))

                if (gCenter >= minFeatureGradient && gCenter >= gLeft && gCenter >= gRight) {
                    totalFeatures++
                    val bestRefractedX = findBestRefractedX(map2, x, y, signedG, searchRadiusPx)
                    val shift = abs(bestRefractedX - x).toDouble()
                    sumDisplacement += shift
                    if (shift > maxDisplacement) maxDisplacement = shift
                    if (shift >= displacementThresholdPx) {
                        displacedFeatures++
                    }
                }
            }
        }

        val meanDisplacement = if (totalFeatures > 0) sumDisplacement / totalFeatures else 0.0
        return RefractionFeatureDisplacementResult(
            featureCount = totalFeatures,
            displacedFeatureCount = displacedFeatures,
            meanDisplacementPx = meanDisplacement,
            maxDisplacementPx = maxDisplacement,
        )
    }

    /** Finds the x-coordinate of the best matching feature gradient in the refracted map. */
    private fun findBestRefractedX(map2: PixelMap, x: Int, y: Int, signedG: Double, searchRadiusPx: Int): Int {
        var bestRefractedX = x
        var bestGrad = 0.0
        var minDistance = Int.MAX_VALUE

        for (offset in -searchRadiusPx..searchRadiusPx) {
            val rx = x + offset
            if (rx < 0 || rx >= map2.width - 1) continue
            val srg = relativeLuminance(map2[rx + 1, y]) - relativeLuminance(map2[rx, y])
            if (srg * signedG > 0) {
                val rg = abs(srg)
                val dist = abs(offset)
                if (rg > bestGrad * 1.05 || (abs(rg - bestGrad) <= 0.05 * bestGrad && dist < minDistance)) {
                    bestGrad = rg
                    bestRefractedX = rx
                    minDistance = dist
                }
            }
        }
        return bestRefractedX
    }

    /**
     * Asserts that [refracted] image shows spatial displacement relative to [unrefracted]
     * along the rim / boundary of the component, proving refraction displacement is active.
     */
    fun assertRefractionDisplacement(
        unrefracted: ImageBitmap,
        refracted: ImageBitmap,
        boundaryRoi: Rect,
        minDisplacedFeatures: Int = 1,
        minDisplacementPx: Double = 0.5,
    ) {
        val result = measureRefractionFeatureDisplacement(
            unrefracted = unrefracted,
            refracted = refracted,
            boundaryRoi = boundaryRoi,
            displacementThresholdPx = minDisplacementPx,
        )

        if (result.featureCount == 0) {
            throw AssertionError(
                "Refraction displacement failure: no distinct visual features found in unrefracted image within ROI " +
                    "([${boundaryRoi.left}, ${boundaryRoi.top}, ${boundaryRoi.right}, ${boundaryRoi.bottom}]). " +
                    "Refraction displacement cannot be evaluated on flat solid fields.",
            )
        }

        if (result.displacedFeatureCount < minDisplacedFeatures) {
            throw AssertionError(
                "Refraction displacement failure: displaced feature count (${result.displacedFeatureCount}) " +
                    "is below required minimum ($minDisplacedFeatures) out of ${result.featureCount} total features " +
                    "(mean displacement: ${"%.3f".format(
                        result.meanDisplacementPx,
                    )}px, max: ${result.maxDisplacementPx}px). " +
                    "Image does not exhibit authentic spatial optical feature refraction.",
            )
        }
    }

    /**
     * Asserts that a glass component modulates its interior luminance in response to different backdrops
     * when effects are enabled, or remains completely invariant when effects are disabled.
     *
     * ΔM = (1/N) * Σ |Luminance(A_i) - Luminance(B_i)|
     *
     * @param cardOverBackdropA Component rendered over Backdrop A (e.g. bright backdrop).
     * @param cardOverBackdropB Component rendered over Backdrop B (e.g. dark backdrop).
     * @param cardBounds Bounding box of the card.
     * @param insetPx Pixels to inset from edges to avoid borders and shadows (default 16px).
     * @param minDeltaModulation Minimum mean absolute luminance difference when effects are active (default 0.03).
     * @param effectsEnabled Whether fluid/glass effects are expected to be active.
     * @param expectedSolidFallback Expected color if effectsEnabled is false.
     */
    fun assertBackdropModulation(
        cardOverBackdropA: ImageBitmap,
        cardOverBackdropB: ImageBitmap,
        cardBounds: Rect,
        insetPx: Int = 16,
        minDeltaModulation: Double = 0.03,
        effectsEnabled: Boolean = true,
        expectedSolidFallback: Color? = null,
    ) {
        val innerRect = cardBounds.deflate(insetPx.toFloat())
        val startX = innerRect.left.toInt().coerceIn(0, cardOverBackdropA.width)
        val endX = innerRect.right.toInt().coerceIn(startX, cardOverBackdropA.width)
        val startY = innerRect.top.toInt().coerceIn(0, cardOverBackdropA.height)
        val endY = innerRect.bottom.toInt().coerceIn(startY, cardOverBackdropA.height)

        val mapA = cardOverBackdropA.toPixelMap()
        val mapB = cardOverBackdropB.toPixelMap()

        var sumDeltaLuminance = 0.0
        var pixelCount = 0

        for (y in startY until endY) {
            for (x in startX until endX) {
                val lumA = relativeLuminance(mapA[x, y])
                val lumB = relativeLuminance(mapB[x, y])
                sumDeltaLuminance += abs(lumA - lumB)
                pixelCount++
            }
        }

        val deltaM = if (pixelCount > 0) sumDeltaLuminance / pixelCount else 0.0

        if (effectsEnabled) {
            if (deltaM < minDeltaModulation) {
                throw AssertionError(
                    "Backdrop modulation failure (effects ON): mean luminance modulation ΔM=${"%.5f".format(deltaM)} " +
                        "is below required threshold $minDeltaModulation across $pixelCount sampled pixels. " +
                        "The component appears opaque and disconnected from its backdrop.",
                )
            }
        } else {
            if (deltaM > 0.005) {
                throw AssertionError(
                    "Backdrop modulation failure (effects OFF): mean luminance modulation ΔM=${"%.5f".format(
                        deltaM,
                    )} " +
                        "exceeds solid invariance tolerance 0.005. " +
                        "Fallback component is leaking backdrop changes instead of rendering solid.",
                )
            }
            if (expectedSolidFallback != null) {
                assertSolidFallbackColor(cardOverBackdropA, cardBounds, expectedSolidFallback, insetPx = insetPx)
            }
        }
    }

    /**
     * Asserts that the card interior is uniform and matches [expectedColor] within channel tolerance.
     */
    fun assertSolidFallbackColor(
        cardRender: ImageBitmap,
        cardBounds: Rect,
        expectedColor: Color,
        tolerance: Float = 0.02f,
        insetPx: Int = 16,
    ) {
        val innerRect = cardBounds.deflate(insetPx.toFloat())
        val startX = innerRect.left.toInt().coerceIn(0, cardRender.width)
        val endX = innerRect.right.toInt().coerceIn(startX, cardRender.width)
        val startY = innerRect.top.toInt().coerceIn(0, cardRender.height)
        val endY = innerRect.bottom.toInt().coerceIn(startY, cardRender.height)

        val pixelMap = cardRender.toPixelMap()
        var maxDr = 0f
        var maxDg = 0f
        var maxDb = 0f

        for (y in startY until endY) {
            for (x in startX until endX) {
                val color = pixelMap[x, y]
                val dr = abs(color.red - expectedColor.red)
                val dg = abs(color.green - expectedColor.green)
                val db = abs(color.blue - expectedColor.blue)
                if (dr > maxDr) maxDr = dr
                if (dg > maxDg) maxDg = dg
                if (db > maxDb) maxDb = db

                if (dr > tolerance || dg > tolerance || db > tolerance) {
                    throw AssertionError(
                        "Solid fallback color mismatch at ($x, $y). Expected $expectedColor, actual $color " +
                            "(dr=${"%.4f".format(dr)}, dg=${"%.4f".format(dg)}, db=${"%.4f".format(db)}, " +
                            "tolerance=$tolerance)",
                    )
                }
            }
        }
    }

    /**
     * Evaluates WCAG AA contrast ratio of [textToken] against sampled background pixels within [adjacentBounds].
     *
     * Evaluates against the worst-case (min and max relative luminance) pixels in the region to guarantee
     * readability even over heterogeneous blurred textures.
     */
    fun assertAdjacentTextContrast(
        textToken: Color,
        background: ImageBitmap,
        adjacentBounds: Rect,
        minRatio: Double = 4.5,
    ) {
        val startX = adjacentBounds.left.toInt().coerceIn(0, background.width)
        val endX = adjacentBounds.right.toInt().coerceIn(startX, background.width)
        val startY = adjacentBounds.top.toInt().coerceIn(0, background.height)
        val endY = adjacentBounds.bottom.toInt().coerceIn(startY, background.height)

        val map = background.toPixelMap()
        var minLum = Double.MAX_VALUE
        var maxLum = Double.MIN_VALUE
        var minLumColor = Color.Unspecified
        var maxLumColor = Color.Unspecified
        var pixelCount = 0

        for (y in startY until endY) {
            for (x in startX until endX) {
                val pixel = map[x, y]
                val lum = relativeLuminance(pixel)
                if (lum < minLum) {
                    minLum = lum
                    minLumColor = pixel
                }
                if (lum > maxLum) {
                    maxLum = lum
                    maxLumColor = pixel
                }
                pixelCount++
            }
        }

        if (pixelCount == 0 || minLum == Double.MAX_VALUE) {
            throw AssertionError("No valid pixels sampled within adjacentBounds: $adjacentBounds")
        }

        val textLum = relativeLuminance(textToken)
        val ratioMin = (max(textLum, minLum) + 0.05) / (min(textLum, minLum) + 0.05)
        val ratioMax = (max(textLum, maxLum) + 0.05) / (min(textLum, maxLum) + 0.05)
        val worstRatio = min(ratioMin, ratioMax)

        if (worstRatio < minRatio) {
            throw AssertionError(
                "WCAG text contrast failure: worst-case contrast ratio ${"%.2f".format(worstRatio)}:1 " +
                    "is below required $minRatio:1. " +
                    "(Text token $textToken lum=${"%.4f".format(textLum)}, " +
                    "min BG $minLumColor lum=${"%.4f".format(minLum)} -> ratio=${"%.2f".format(ratioMin)}:1, " +
                    "max BG $maxLumColor lum=${"%.4f".format(maxLum)} -> ratio=${"%.2f".format(ratioMax)}:1)",
            )
        }
    }
}
