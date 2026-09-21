package com.kashif_e.backdrop

import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

internal const val MAX_LAYER_DIMENSION_PX: Int = 4096
internal const val MAX_RETAINED_PIXELS: Long = 16_777_216L
internal const val MIN_AUTOMATIC_INPUT_SCALE: Float = 0.25f
private const val BUDGET_SEARCH_ITERATIONS: Int = 16

/** Hard caps a single retained backdrop layer must stay within - mirrors Haze's `GlassRenderBudget`. */
internal fun IntSize.fitsRenderBudget(): Boolean =
    width in 1..MAX_LAYER_DIMENSION_PX &&
        height in 1..MAX_LAYER_DIMENSION_PX &&
        width.toLong() * height.toLong() <= MAX_RETAINED_PIXELS

internal enum class BackdropRenderBudgetFallbackReason {
    InvalidGeometry,
    ExceedsLimits,
}

internal sealed interface BackdropRenderBudgetDecision {
    /** Record the backdrop layer at [scale] × [fullSize], yielding [size]; both are within budget. */
    data class Runtime(val scale: Float, val size: IntSize) : BackdropRenderBudgetDecision

    data class Fallback(val reason: BackdropRenderBudgetFallbackReason) : BackdropRenderBudgetDecision
}

/**
 * Decides the actual scale/size to record `DrawBackdropNode`'s single retained `GraphicsLayer`
 * at, given the caller's [requestedScale] (`inputScale`) and [fullSize] the element would record
 * at `requestedScale = 1f` - mirrors Haze's `resolveGlassRenderBudget`, narrowed to `:backdrop`'s
 * single-retained-layer model. Haze's own version additionally bisection-searches for a "blur
 * topology transition" boundary, because its multi-pass compositing graph's *layer count* can
 * itself change with scale (full vs native blur topology); `:backdrop` always retains exactly one
 * layer regardless of scale, so that half of Haze's search doesn't apply - only the plain
 * safe-scale bisection does.
 */
internal fun resolveBackdropRenderBudget(
    requestedScale: Float,
    fullSize: IntSize,
): BackdropRenderBudgetDecision {
    if (!requestedScale.isFinite() || requestedScale <= 0f || fullSize.width <= 0 || fullSize.height <= 0) {
        return BackdropRenderBudgetDecision.Fallback(BackdropRenderBudgetFallbackReason.InvalidGeometry)
    }

    fun sizeAt(scale: Float): IntSize = IntSize(
        width = (fullSize.width * scale).roundToInt().coerceAtLeast(1),
        height = (fullSize.height * scale).roundToInt().coerceAtLeast(1),
    )

    val requestedSize = sizeAt(requestedScale)
    if (requestedSize.fitsRenderBudget()) {
        return BackdropRenderBudgetDecision.Runtime(requestedScale, requestedSize)
    }

    val minimumScale = minOf(requestedScale, MIN_AUTOMATIC_INPUT_SCALE)
    val minimumSize = sizeAt(minimumScale)
    if (!minimumSize.fitsRenderBudget()) {
        return BackdropRenderBudgetDecision.Fallback(BackdropRenderBudgetFallbackReason.ExceedsLimits)
    }

    var selectedScale = minimumScale
    var selectedSize = minimumSize
    var rejectedScale = requestedScale
    repeat(BUDGET_SEARCH_ITERATIONS) {
        val candidateScale = (selectedScale + rejectedScale) / 2f
        val candidateSize = sizeAt(candidateScale)
        if (candidateSize.fitsRenderBudget()) {
            selectedScale = candidateScale
            selectedSize = candidateSize
        } else {
            rejectedScale = candidateScale
        }
    }
    return BackdropRenderBudgetDecision.Runtime(selectedScale, selectedSize)
}
