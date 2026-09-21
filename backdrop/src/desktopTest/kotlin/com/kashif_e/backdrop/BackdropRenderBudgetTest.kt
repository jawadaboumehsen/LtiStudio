package com.kashif_e.backdrop

import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackdropRenderBudgetTest {

    @Test
    fun requestedSizeWithinBudgetIsUsedAsIs() {
        val decision = resolveBackdropRenderBudget(1f, IntSize(800, 600))
        val runtime = assertIs<BackdropRenderBudgetDecision.Runtime>(decision)
        assertEquals(1f, runtime.scale)
        assertEquals(IntSize(800, 600), runtime.size)
    }

    @Test
    fun invalidScaleFallsBack() {
        assertIs<BackdropRenderBudgetDecision.Fallback>(resolveBackdropRenderBudget(0f, IntSize(800, 600)))
        assertIs<BackdropRenderBudgetDecision.Fallback>(resolveBackdropRenderBudget(-1f, IntSize(800, 600)))
        assertIs<BackdropRenderBudgetDecision.Fallback>(resolveBackdropRenderBudget(Float.NaN, IntSize(800, 600)))
    }

    @Test
    fun invalidSizeFallsBack() {
        assertIs<BackdropRenderBudgetDecision.Fallback>(resolveBackdropRenderBudget(1f, IntSize(0, 600)))
        assertIs<BackdropRenderBudgetDecision.Fallback>(resolveBackdropRenderBudget(1f, IntSize(800, 0)))
    }

    @Test
    fun oversizedRequestSearchesDownToASafeScale() {
        // 6000x6000 at full scale exceeds both the per-dimension cap (4096) and the pixel cap
        // (16_777_216 == 4096x4096) - the search must land on a scale whose resulting size fits
        // both, without falling all the way back to the 0.25f automatic minimum unnecessarily.
        val decision = resolveBackdropRenderBudget(1f, IntSize(6000, 6000))
        val runtime = assertIs<BackdropRenderBudgetDecision.Runtime>(decision)
        assertTrue(runtime.size.fitsRenderBudget(), "search result ${runtime.size} must itself fit the budget")
        assertTrue(runtime.scale in MIN_AUTOMATIC_INPUT_SCALE..1f, "scale ${runtime.scale} must be in the searched range")
    }

    @Test
    fun oversizedRequestNeverExceedsHardDimensionCap() {
        // Width alone exceeds the cap at full scale but comes back within it at the 0.25f
        // automatic minimum (15_000 * 0.25 == 3_750), so this should resolve to a safe Runtime
        // scale rather than falling all the way back.
        val decision = resolveBackdropRenderBudget(1f, IntSize(15_000, 200))
        val runtime = assertIs<BackdropRenderBudgetDecision.Runtime>(decision)
        assertTrue(runtime.size.width <= MAX_LAYER_DIMENSION_PX)
    }

    @Test
    fun pathologicallyHugeRequestFallsBackEvenAtMinimumScale() {
        // 40_000 x 40_000 at the 0.25f automatic minimum is still 10_000 x 10_000 - past both caps.
        val decision = resolveBackdropRenderBudget(1f, IntSize(40_000, 40_000))
        val fallback = assertIs<BackdropRenderBudgetDecision.Fallback>(decision)
        assertEquals(BackdropRenderBudgetFallbackReason.ExceedsLimits, fallback.reason)
    }

    @Test
    fun smallRequestBelowMinimumScaleIsUnaffected() {
        // A caller-requested inputScale below the automatic minimum (e.g. an aggressive perf
        // preset) should be honored as-is once it already fits, not silently raised.
        val decision = resolveBackdropRenderBudget(0.1f, IntSize(800, 600))
        val runtime = assertIs<BackdropRenderBudgetDecision.Runtime>(decision)
        assertEquals(0.1f, runtime.scale)
    }

    @Test
    fun fitsRenderBudgetRejectsZeroAndNegativeDimensions() {
        assertTrue(!IntSize(0, 100).fitsRenderBudget())
        assertTrue(!IntSize(100, 0).fitsRenderBudget())
    }

    @Test
    fun fitsRenderBudgetRejectsOverDimensionCap() {
        assertTrue(!IntSize(MAX_LAYER_DIMENSION_PX + 1, 100).fitsRenderBudget())
    }

    @Test
    fun fitsRenderBudgetAcceptsExactlyAtBothCaps() {
        // 4096 x 4096 == MAX_LAYER_DIMENSION_PX per side and == MAX_RETAINED_PIXELS in area
        // (4096*4096 == 16_777_216) - both caps are inclusive, so this exact boundary must fit.
        assertTrue(IntSize(MAX_LAYER_DIMENSION_PX, MAX_LAYER_DIMENSION_PX).fitsRenderBudget())
        assertTrue(IntSize(4000, 4000).fitsRenderBudget())
    }
}
