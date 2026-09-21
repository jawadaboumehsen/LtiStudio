package com.kashif_e.backdrop

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SemanticBlurKernelTest {

    @Test
    fun nonPositiveOrNonFiniteRadiusProducesIdentityKernel() {
        for (radius in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val kernel = SemanticBlurKernel.create(radius)
            assertEquals(1f, kernel.centerWeight, "radius=$radius")
            assertTrue(kernel.taps.isEmpty(), "radius=$radius")
        }
    }

    @Test
    fun kernelWeightsAlwaysNormalizeToOne() {
        for (sigma in listOf(0.1f, 0.5f, 1f, 2f, 5f, 20f, 85.3f)) {
            val kernel = SemanticBlurKernel.createForSigma(sigma)
            val total = kernel.centerWeight + 2f * kernel.taps.sumOf { it.weight.toDouble() }.toFloat()
            assertTrue(
                abs(total - 1f) < 1e-4f,
                "sigma=$sigma: weights must sum to 1 (center + 2x each pair), got $total",
            )
        }
    }

    @Test
    fun tapCountNeverExceedsMaxTapPairs() {
        // A very large sigma pushes `support` far past MAX_TAP_PAIRS - the sample grouping must
        // keep the pass bounded at MAX_TAP_PAIRS regardless.
        val kernel = SemanticBlurKernel.createForSigma(200f)
        assertTrue(
            kernel.taps.size <= SemanticBlurKernel.MAX_TAP_PAIRS,
            "expected at most ${SemanticBlurKernel.MAX_TAP_PAIRS} tap pairs, got ${kernel.taps.size}",
        )
    }

    @Test
    fun tapOffsetsAreOrderedAndPositive() {
        val kernel = SemanticBlurKernel.createForSigma(10f)
        assertTrue(kernel.taps.all { it.offsetPx > 0f })
        for (i in 1 until kernel.taps.size) {
            assertTrue(
                kernel.taps[i].offsetPx > kernel.taps[i - 1].offsetPx,
                "tap offsets must be strictly increasing: ${kernel.taps.map { it.offsetPx }}",
            )
        }
    }

    @Test
    fun smallSigmaVarianceMatchesRequestedSigmaSquared() {
        // Below sigmaPx <= 2f, createForSigma rescales tap offsets so the kernel's own variance
        // matches the requested sigma exactly (up to float precision) - this is the property the
        // Haze source itself calls out (`offsetScale = sigmaPx / sqrt(variance)`).
        for (sigma in listOf(0.6f, 1f, 1.5f, 2f)) {
            val kernel = SemanticBlurKernel.createForSigma(sigma)
            val expectedVariance = sigma * sigma
            assertTrue(
                abs(kernel.variance - expectedVariance) < 0.05f,
                "sigma=$sigma: expected variance~=$expectedVariance, got ${kernel.variance}",
            )
        }
    }

    @Test
    fun ultraNarrowSigmaUsesTheSingleTapClosedForm() {
        val kernel = SemanticBlurKernel.createForSigma(0.25f)
        assertEquals(1, kernel.taps.size)
        val variance = 0.25f * 0.25f
        assertEquals(1f - variance, kernel.centerWeight)
    }

    @Test
    fun radiusToSigmaIsMonotonicallyIncreasing() {
        var previous = SemanticBlurKernel.radiusToSigma(0f)
        for (radius in listOf(1f, 4f, 16f, 64f, 256f)) {
            val sigma = SemanticBlurKernel.radiusToSigma(radius)
            assertTrue(sigma > previous, "radiusToSigma must increase with radius")
            previous = sigma
        }
    }

    @Test
    fun planBelowThresholdUsesSemanticKernel() {
        val plan = SemanticBlurPlan.create(100, 100, radiusPx = 2f)
        assertTrue(!plan.usesNativeBlur)
        assertTrue(plan.horizontalKernel.taps.isNotEmpty() || plan.horizontalKernel.centerWeight < 1f)
    }

    @Test
    fun planAboveThresholdUsesNativeBlur() {
        val plan = SemanticBlurPlan.create(100, 100, radiusPx = 20f)
        assertTrue(plan.usesNativeBlur)
        assertEquals(1f, plan.horizontalKernel.centerWeight)
        assertTrue(plan.horizontalKernel.taps.isEmpty())
    }

    @Test
    fun planClampsRadiusToMaxSupported() {
        val plan = SemanticBlurPlan.create(100, 100, radiusPx = Float.MAX_VALUE)
        assertEquals(SemanticBlurKernel.MAX_SUPPORTED_RADIUS_PX, plan.effectiveRadiusPx)
    }

    @Test
    fun planZeroRadiusIsIdentity() {
        val plan = SemanticBlurPlan.create(100, 100, radiusPx = 0f)
        assertTrue(plan.isIdentity)
    }
}
