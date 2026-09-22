package com.kashif_e.backdrop

/**
 * Tracks how often a backdrop's render-affecting state has genuinely changed (not every draw
 * frame), so [BackdropInputScalePolicy] can weight its downscale decision by update frequency as
 * well as static radius/area - ported from Haze's `HazeInputUpdateCadence` (ADR-0004/ADR-0005): a
 * large retained result that updates once is cheap to keep at full resolution, but a smaller
 * result recomputed on every animation frame stays fill-rate bound, so its effective area is
 * scaled up to compensate. Node-owned, mirroring [BackdropInputScalePolicy]'s own
 * previous-tier-gated statefulness.
 */
internal class BackdropInputUpdateCadence {
    private var lastUpdateNanos: Long = Long.MIN_VALUE
    private var multiplier: Int = NONE_MULTIPLIER

    /**
     * Call once per genuine render-affecting update (a resolved-effect change or an interaction
     * value that actually moved) - never once per draw frame. Returns the burst multiplier to
     * apply to the area passed into [BackdropInputScalePolicy.resolve].
     */
    fun recordUpdate(nowNanos: Long): Int {
        multiplier = resolveMultiplier(lastUpdateNanos, nowNanos, multiplier)
        lastUpdateNanos = nowNanos
        return multiplier
    }

    fun reset() {
        lastUpdateNanos = Long.MIN_VALUE
        multiplier = NONE_MULTIPLIER
    }

    internal companion object {
        const val NONE_MULTIPLIER = 1
        const val MAX_MULTIPLIER = 3
        const val BURST_WINDOW_NANOS = 100_000_000L

        /**
         * Updates no more than [BURST_WINDOW_NANOS] apart form a burst and increment the
         * multiplier by one, capped at [MAX_MULTIPLIER]; a quiet interval resets it to
         * [NONE_MULTIPLIER]. The very first recorded update (`lastUpdateNanos` unset) is never a
         * burst.
         */
        internal fun resolveMultiplier(lastUpdateNanos: Long, nowNanos: Long, previousMultiplier: Int): Int {
            if (lastUpdateNanos == Long.MIN_VALUE) return NONE_MULTIPLIER
            val elapsedNanos = nowNanos - lastUpdateNanos
            return if (elapsedNanos in 0..BURST_WINDOW_NANOS) {
                (previousMultiplier + 1).coerceAtMost(MAX_MULTIPLIER)
            } else {
                NONE_MULTIPLIER
            }
        }
    }
}
