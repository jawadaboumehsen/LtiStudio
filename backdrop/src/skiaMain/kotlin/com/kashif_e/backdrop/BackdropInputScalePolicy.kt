package com.kashif_e.backdrop

/**
 * Resolves an automatic backdrop-capture downscale tier from blur radius x visible area, with
 * hysteresis - ported from Haze's `BlurInputScalePolicy`, narrowed to the pure entry/exit-
 * threshold tier logic. Two pieces of Haze's version don't apply here: its `progressive` branch
 * (`:backdrop` has no progressive/gradient-masked blur concept to switch on), and
 * `HazeInputUpdateCadence`'s burst multiplier (biases the area used toward downscaling sooner
 * during a rapid content-invalidation burst - it needs a "content update key" concept `:backdrop`
 * doesn't have; left for later if real scroll-burst jank shows up in practice, not required for
 * the core hysteresis technique to be real and correct).
 *
 * Distinct from [resolveBackdropRenderBudget] (Phase D): that is a hard safety cap applied
 * *after* a scale is chosen; this is the *default* scale choice itself, opt-in via
 * `Modifier.drawBackdrop(adaptiveInputScale = true)` - the caller's own `inputScale` still wins
 * whenever adaptive scaling isn't requested.
 *
 * Node-owned (mirrors [BackdropInteractionController]/the render-budget pattern): call [resolve]
 * every draw and keep the instance for the node's lifetime, so the previous tier gates the next
 * decision - this is what stops one frame of radius/size jitter from flapping between tiers.
 */
internal class BackdropInputScalePolicy {
    private var previousScale: Float = NONE_SCALE

    fun resolve(blurRadiusPx: Float, areaPx: Float): Float {
        val scale = resolveScale(previousScale, blurRadiusPx, areaPx)
        previousScale = scale
        return scale
    }

    fun reset() {
        previousScale = NONE_SCALE
    }

    companion object {
        const val BALANCED_SCALE = 0.8f
        const val AGGRESSIVE_SCALE = 0.5f
        const val NONE_SCALE = 1f

        // At these boundaries, the downsampled blur kernel remains at least 25.6px / 30px.
        const val BALANCED_RADIUS_PX = 32f
        const val AGGRESSIVE_RADIUS_PX = 60f

        // These boundaries avoid reallocating for savings below roughly 0.1M / 0.375M pixels.
        const val BALANCED_AREA_PX = 300_000f
        const val AGGRESSIVE_AREA_PX = 500_000f

        // A 12.5% exit margin absorbs common one-frame layout and radius animation jitter.
        const val BALANCED_RADIUS_EXIT_PX = 28f
        const val AGGRESSIVE_RADIUS_EXIT_PX = 52.5f
        const val BALANCED_AREA_EXIT_PX = 262_500f
        const val AGGRESSIVE_AREA_EXIT_PX = 437_500f

        /** Pure - directly testable without an instance, mirrors [resolveBackdropRenderBudget]'s shape. */
        internal fun resolveScale(previousScale: Float, blurRadiusPx: Float, areaPx: Float): Float {
            if (!blurRadiusPx.isFinite() || !areaPx.isFinite() || blurRadiusPx <= 0f || areaPx <= 0f) {
                return NONE_SCALE
            }
            return when (previousScale) {
                AGGRESSIVE_SCALE -> when {
                    blurRadiusPx < AGGRESSIVE_RADIUS_EXIT_PX || areaPx < AGGRESSIVE_AREA_EXIT_PX ->
                        resolveWithoutAggressiveTier(blurRadiusPx, areaPx)
                    else -> AGGRESSIVE_SCALE
                }
                BALANCED_SCALE -> when {
                    blurRadiusPx >= AGGRESSIVE_RADIUS_PX && areaPx >= AGGRESSIVE_AREA_PX -> AGGRESSIVE_SCALE
                    blurRadiusPx < BALANCED_RADIUS_EXIT_PX || areaPx < BALANCED_AREA_EXIT_PX -> NONE_SCALE
                    else -> BALANCED_SCALE
                }
                else -> when {
                    blurRadiusPx >= AGGRESSIVE_RADIUS_PX && areaPx >= AGGRESSIVE_AREA_PX -> AGGRESSIVE_SCALE
                    blurRadiusPx >= BALANCED_RADIUS_PX && areaPx >= BALANCED_AREA_PX -> BALANCED_SCALE
                    else -> NONE_SCALE
                }
            }
        }

        private fun resolveWithoutAggressiveTier(blurRadiusPx: Float, areaPx: Float): Float =
            when {
                blurRadiusPx >= BALANCED_RADIUS_EXIT_PX && areaPx >= BALANCED_AREA_EXIT_PX -> BALANCED_SCALE
                else -> NONE_SCALE
            }
    }
}
