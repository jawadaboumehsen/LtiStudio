package com.kashif_e.backdrop

import kotlin.test.Test
import kotlin.test.assertEquals

class BackdropInputScalePolicyTest {

    @Test
    fun smallBlurAndAreaStayAtFullScale() {
        val policy = BackdropInputScalePolicy()
        assertEquals(1f, policy.resolve(blurRadiusPx = 8f, areaPx = 10_000f))
    }

    @Test
    fun invalidInputsStayAtFullScale() {
        val policy = BackdropInputScalePolicy()
        assertEquals(1f, policy.resolve(blurRadiusPx = 0f, areaPx = 1_000_000f))
        assertEquals(1f, policy.resolve(blurRadiusPx = -5f, areaPx = 1_000_000f))
        assertEquals(1f, policy.resolve(blurRadiusPx = 40f, areaPx = 0f))
        assertEquals(1f, policy.resolve(blurRadiusPx = Float.NaN, areaPx = 1_000_000f))
    }

    @Test
    fun crossingBothBalancedThresholdsEntersBalancedTier() {
        val policy = BackdropInputScalePolicy()
        val scale = policy.resolve(blurRadiusPx = 40f, areaPx = 400_000f)
        assertEquals(BackdropInputScalePolicy.BALANCED_SCALE, scale)
    }

    @Test
    fun crossingBothAggressiveThresholdsEntersAggressiveTierDirectly() {
        val policy = BackdropInputScalePolicy()
        val scale = policy.resolve(blurRadiusPx = 80f, areaPx = 600_000f)
        assertEquals(BackdropInputScalePolicy.AGGRESSIVE_SCALE, scale)
    }

    @Test
    fun onlyOneThresholdCrossedStaysAtFullScale() {
        val policy = BackdropInputScalePolicy()
        // Radius alone past the balanced threshold, area still small.
        assertEquals(1f, policy.resolve(blurRadiusPx = 40f, areaPx = 10_000f))
    }

    @Test
    fun hysteresisKeepsBalancedTierThroughASmallDipBelowEntryThreshold() {
        val policy = BackdropInputScalePolicy()
        // Enter balanced.
        assertEquals(BackdropInputScalePolicy.BALANCED_SCALE, policy.resolve(40f, 400_000f))
        // Dips just below the *entry* threshold (32f/300_000f) but still above the looser *exit*
        // threshold (28f/262_500f) - a naive re-evaluation-from-scratch policy would drop back to
        // full scale here; hysteresis must hold the tier.
        val held = policy.resolve(blurRadiusPx = 30f, areaPx = 280_000f)
        assertEquals(BackdropInputScalePolicy.BALANCED_SCALE, held)
    }

    @Test
    fun droppingBelowTheExitThresholdLeavesBalancedTier() {
        val policy = BackdropInputScalePolicy()
        assertEquals(BackdropInputScalePolicy.BALANCED_SCALE, policy.resolve(40f, 400_000f))
        val dropped = policy.resolve(blurRadiusPx = 20f, areaPx = 200_000f)
        assertEquals(1f, dropped)
    }

    @Test
    fun hysteresisKeepsAggressiveTierThroughASmallDipBelowEntryThreshold() {
        val policy = BackdropInputScalePolicy()
        assertEquals(BackdropInputScalePolicy.AGGRESSIVE_SCALE, policy.resolve(80f, 600_000f))
        // Between the aggressive exit threshold (52.5f/437_500f) and entry threshold (60f/500_000f).
        val held = policy.resolve(blurRadiusPx = 55f, areaPx = 460_000f)
        assertEquals(BackdropInputScalePolicy.AGGRESSIVE_SCALE, held)
    }

    @Test
    fun droppingFromAggressiveBelowBalancedExitGoesStraightToFullScale() {
        val policy = BackdropInputScalePolicy()
        assertEquals(BackdropInputScalePolicy.AGGRESSIVE_SCALE, policy.resolve(80f, 600_000f))
        val dropped = policy.resolve(blurRadiusPx = 10f, areaPx = 50_000f)
        assertEquals(1f, dropped)
    }

    @Test
    fun droppingFromAggressiveToBetweenExitAndBalancedEntryLandsOnBalanced() {
        val policy = BackdropInputScalePolicy()
        assertEquals(BackdropInputScalePolicy.AGGRESSIVE_SCALE, policy.resolve(80f, 600_000f))
        // Below the aggressive exit threshold, but still above the balanced *exit* threshold -
        // resolveWithoutAggressiveTier should land on BALANCED, not skip straight to full scale.
        val landed = policy.resolve(blurRadiusPx = 40f, areaPx = 400_000f)
        assertEquals(BackdropInputScalePolicy.BALANCED_SCALE, landed)
    }

    @Test
    fun risingFromBalancedIntoAggressiveUsesTheEntryThresholdNotExit() {
        val policy = BackdropInputScalePolicy()
        assertEquals(BackdropInputScalePolicy.BALANCED_SCALE, policy.resolve(40f, 400_000f))
        val escalated = policy.resolve(blurRadiusPx = 65f, areaPx = 550_000f)
        assertEquals(BackdropInputScalePolicy.AGGRESSIVE_SCALE, escalated)
    }

    @Test
    fun resetReturnsToFullScaleWithNoMemoryOfThePreviousTier() {
        val policy = BackdropInputScalePolicy()
        assertEquals(BackdropInputScalePolicy.AGGRESSIVE_SCALE, policy.resolve(80f, 600_000f))
        policy.reset()
        // Immediately after reset, a value that would have *held* the aggressive tier via
        // hysteresis (with memory of the previous call) must instead be evaluated fresh, as if
        // this were the first call ever - which crosses the BALANCED entry thresholds (32px /
        // 300_000px²) but not the AGGRESSIVE ones (60px / 500_000px²), landing on BALANCED, not
        // on AGGRESSIVE (what hysteresis would have produced) or NONE.
        val afterReset = policy.resolve(blurRadiusPx = 55f, areaPx = 460_000f)
        assertEquals(BackdropInputScalePolicy.BALANCED_SCALE, afterReset)
    }
}
