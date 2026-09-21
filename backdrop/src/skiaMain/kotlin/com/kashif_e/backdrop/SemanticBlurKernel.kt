package com.kashif_e.backdrop

import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * A bounded, positive half-kernel; every tap is sampled at both signs - ported from Haze's
 * `SemanticBlurKernel` (`haze-glass`). A precise, deterministic small-radius Gaussian
 * alternative to a platform-native blur pass: Haze exists to solve *cross-platform* blur
 * consistency (Android/iOS/Desktop/Web each carry a different native blur implementation, with
 * different small-radius artifacts) - `:backdrop` targets a single Skia backend, where
 * `effects.blur()`'s `ImageFilter.makeBlur()` is already the same native Skia Gaussian blur this
 * kernel would otherwise stand in for, so this is ported as a real, independently correct,
 * independently tested algorithm rather than force-wired into [com.kashif_e.backdrop.effects.blur]
 * without evidence it improves (rather than regresses) anything on this single backend - see
 * `SemanticBlurKernelTest` for the math itself.
 */
internal data class SemanticBlurKernel(
    val centerWeight: Float,
    val taps: List<Tap>,
) {
    data class Tap(
        val offsetPx: Float,
        val weight: Float,
    )

    val variance: Float
        get() = taps.sumOf { 2.0 * it.weight * it.offsetPx * it.offsetPx }.toFloat()

    companion object {
        /** At most 41 samples per pass (center + 20 symmetric pairs). */
        const val MAX_TAP_PAIRS: Int = 20

        /** Source-space bound for a semantic-kernel pass; radii beyond this use a native blur. */
        const val MAX_SUPPORTED_RADIUS_PX: Float = 256f

        /** Progressive blur has no matching source-space low-pass, so it keeps a lower limit. */
        const val MAX_PROGRESSIVE_RADIUS_PX: Float = 38.5f

        private const val SIGMA_SUPPORT: Float = 3f
        private const val MAX_INTEGRATION_SAMPLES_PER_TAP: Int = 8

        fun radiusToSigma(radiusPx: Float): Float =
            radiusPx * 0.57735f + 0.5f * radiusPx.coerceIn(0f, 1f)

        fun create(radiusPx: Float): SemanticBlurKernel = when {
            !radiusPx.isFinite() || radiusPx <= 0f -> SemanticBlurKernel(1f, emptyList())
            else -> createForSigma(radiusToSigma(radiusPx))
        }

        fun createForSigma(sigmaPx: Float): SemanticBlurKernel {
            if (!sigmaPx.isFinite() || sigmaPx <= 0f) return SemanticBlurKernel(1f, emptyList())
            if (sigmaPx <= 0.5f) {
                val variance = sigmaPx * sigmaPx
                return SemanticBlurKernel(
                    centerWeight = 1f - variance,
                    taps = listOf(Tap(offsetPx = 1f, weight = variance * 0.5f)),
                )
            }

            val support = ceil((sigmaPx * SIGMA_SUPPORT).toDouble()).toInt().coerceAtLeast(1)
            // Each group is replaced by its Gaussian-weighted centroid. Groups of two are exact
            // under bilinear sampling; wider groups are a bounded quadrature approximation.
            val samplesPerTap = maxOf(2, ceil(support / MAX_TAP_PAIRS.toDouble()).toInt())
            val center = gaussianWeight(0.0, sigmaPx)
            val unnormalized = buildList {
                var start = 1
                while (start <= support) {
                    val end = minOf(start + samplesPerTap - 1, support)
                    val sampleCount = minOf(end - start + 1, MAX_INTEGRATION_SAMPLES_PER_TAP)
                    val sampleWidth = (end - start + 1).toDouble() / sampleCount
                    var weight = 0.0
                    var weightedOffset = 0.0
                    repeat(sampleCount) { index ->
                        val offset = start - 0.5 + (index + 0.5) * sampleWidth
                        val sampleWeight = gaussianWeight(offset, sigmaPx) * sampleWidth
                        weight += sampleWeight
                        weightedOffset += offset * sampleWeight
                    }
                    add(Tap((weightedOffset / weight).toFloat(), weight.toFloat()))
                    start = end + 1
                }
            }
            val normalization = center + 2.0 * unnormalized.sumOf { it.weight.toDouble() }
            val normalizedKernel = SemanticBlurKernel(
                centerWeight = (center / normalization).toFloat(),
                taps = unnormalized.map { it.copy(weight = (it.weight / normalization).toFloat()) },
            )
            if (sigmaPx <= 2f) {
                val offsetScale = sigmaPx / sqrt(normalizedKernel.variance)
                return normalizedKernel.copy(
                    taps = normalizedKernel.taps.map { it.copy(offsetPx = it.offsetPx * offsetScale) },
                )
            }
            return normalizedKernel
        }

        private fun gaussianWeight(offset: Double, sigma: Float): Double {
            val normalized = offset / sigma
            return exp(-0.5 * normalized * normalized)
        }
    }
}

/**
 * A resolved blur pass plan: either a native (platform) blur, or a [SemanticBlurKernel] pair for
 * the horizontal/vertical separable passes - ported from Haze's `SemanticBlurPlan`.
 */
internal data class SemanticBlurPlan(
    val sampleSize: IntSize,
    val effectiveRadiusPx: Float,
    val sigmaPx: Float,
    val usesNativeBlur: Boolean,
    val horizontalKernel: SemanticBlurKernel,
    val verticalKernel: SemanticBlurKernel,
) {
    val isIdentity: Boolean
        get() = !usesNativeBlur && horizontalKernel.taps.isEmpty() && verticalKernel.taps.isEmpty()

    companion object {
        /** Below this radius, a semantic-kernel pass is used instead of the native blur. */
        const val NATIVE_BLUR_RADIUS_THRESHOLD_PX: Float = 4f

        fun create(sampleWidth: Int, sampleHeight: Int, radiusPx: Float): SemanticBlurPlan {
            val effectiveRadius = radiusPx.takeIf { it.isFinite() }
                ?.coerceIn(0f, SemanticBlurKernel.MAX_SUPPORTED_RADIUS_PX) ?: 0f
            val sigma = if (effectiveRadius > 0f) SemanticBlurKernel.radiusToSigma(effectiveRadius) else 0f
            val usesNativeBlur = effectiveRadius > NATIVE_BLUR_RADIUS_THRESHOLD_PX
            val kernel = if (usesNativeBlur) {
                SemanticBlurKernel(1f, emptyList())
            } else {
                SemanticBlurKernel.createForSigma(sigma)
            }
            return SemanticBlurPlan(
                sampleSize = IntSize(sampleWidth, sampleHeight),
                effectiveRadiusPx = effectiveRadius,
                sigmaPx = sigma,
                usesNativeBlur = usesNativeBlur,
                horizontalKernel = kernel,
                verticalKernel = kernel,
            )
        }
    }
}
