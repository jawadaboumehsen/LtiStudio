package com.kashif_e.backdrop

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FallbackAlphaBoostTest {

    @Test
    fun zeroOrUnspecifiedBlurProducesNoBoost() {
        assertEquals(0f, resolveFallbackAlphaBoost(OpticalSizeValue.Fixed(0.dp), 200.dp))
        assertEquals(0f, resolveFallbackAlphaBoost(OpticalSizeValue.Fixed((-4).dp), 200.dp))
    }

    @Test
    fun invalidShortestSideProducesNoBoost() {
        assertEquals(0f, resolveFallbackAlphaBoost(OpticalSizeValue.Fixed(16.dp), 0.dp))
        assertEquals(0f, resolveFallbackAlphaBoost(OpticalSizeValue.Fixed(16.dp), Dp(Float.NaN)))
    }

    @Test
    fun boostScalesLinearlyWithBlurRadius() {
        // 36dp is half of the 72dp reference radius Haze's own ScrimBlurVisualEffectDelegate uses.
        val boost = resolveFallbackAlphaBoost(OpticalSizeValue.Fixed(36.dp), 200.dp)
        assertEquals(0.5f, boost, absoluteTolerance = 1e-4f)
    }

    @Test
    fun boostIsClampedAtOneForRadiiAtOrAboveTheReference() {
        assertEquals(1f, resolveFallbackAlphaBoost(OpticalSizeValue.Fixed(72.dp), 200.dp))
        assertEquals(1f, resolveFallbackAlphaBoost(OpticalSizeValue.Fixed(200.dp), 200.dp))
    }

    @Test
    fun boostIsAlwaysWithinUnitInterval() {
        for (radius in listOf(0.1f, 1f, 10f, 36f, 71.9f, 72f, 500f, 10_000f)) {
            val boost = resolveFallbackAlphaBoost(OpticalSizeValue.Fixed(radius.dp), 200.dp)
            assertTrue(boost in 0f..1f, "radius=$radius produced out-of-range boost $boost")
        }
    }

    @Test
    fun respectsResponsiveOpticalSizeValueResolution() {
        // A Responsive blur should resolve against shortestSide exactly like the real render
        // path does, then feed that resolved value into the same boost formula.
        val responsive = OpticalSizeValue.Responsive(
            OpticalSizePoint(44.dp, 8.dp),
            OpticalSizePoint(400.dp, 72.dp),
        )
        val boostAtSmall = resolveFallbackAlphaBoost(responsive, 44.dp)
        val boostAtLarge = resolveFallbackAlphaBoost(responsive, 400.dp)
        assertTrue(boostAtSmall < boostAtLarge, "boost must grow with the resolved blur radius")
        assertEquals(1f, boostAtLarge, absoluteTolerance = 1e-4f)
    }
}

private fun assertEquals(expected: Float, actual: Float, absoluteTolerance: Float) {
    assertTrue(
        kotlin.math.abs(expected - actual) <= absoluteTolerance,
        "expected $expected, got $actual (tolerance $absoluteTolerance)",
    )
}
