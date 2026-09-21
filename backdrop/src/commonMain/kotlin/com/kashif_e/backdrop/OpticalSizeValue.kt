package com.kashif_e.backdrop

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.jvm.JvmInline

/**
 * An optical parameter value that is either fixed, or responsive to a component's shortest
 * dimension (so a 44dp pill and a 1400dp bar can share one named style while resolving to
 * different blur/lens numbers).
 *
 * Ported from Haze's `OpticalSizeValue` (`haze-glass`, Apache-2.0) — narrowed to the two cases
 * `backdrop/` actually needs: a constant, or a small ordered set of size breakpoints.
 */
@Immutable
public sealed interface OpticalSizeValue<T> {

    @JvmInline
    public value class Fixed<T>(public val value: T) : OpticalSizeValue<T>

    @Immutable
    public class Responsive<T> private constructor(
        public val points: List<OpticalSizePoint<T>>,
    ) : OpticalSizeValue<T> {

        public constructor(vararg points: OpticalSizePoint<T>) : this(points.toList())

        init {
            require(points.size >= 2) { "Responsive requires at least two points" }
            for (index in 1 until points.size) {
                require(points[index].shortestDimension > points[index - 1].shortestDimension) {
                    "points must have strictly increasing shortestDimension"
                }
            }
        }

        override fun equals(other: Any?): Boolean = other is Responsive<*> && points == other.points
        override fun hashCode(): Int = points.hashCode()
    }
}

/** Associates [value] with the shortest-side [shortestDimension] at which it applies. */
@Immutable
public data class OpticalSizePoint<T>(
    public val shortestDimension: Dp,
    public val value: T,
) {
    init {
        require(shortestDimension.value.isFinite() && shortestDimension > 0.dp) {
            "shortestDimension must be finite and positive"
        }
    }
}

/** GLSL-style smoothstep: 0 at/before [edge0], 1 at/after [edge1], cubic ease between. */
@PublishedApi
internal fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Resolves an [OpticalSizeValue] at [shortestSide] using [lerp] to interpolate between adjacent
 * points, clamping outside the authored range and smoothstep-easing between them otherwise.
 *
 * Generic in `T` so theme code can resolve domain types (e.g. a paired blur-height/displacement
 * refraction token) without `backdrop/` knowing what those types are — this engine module has no
 * theme knowledge, per the glass material plan.
 */
public inline fun <T> OpticalSizeValue<T>.resolve(shortestSide: Dp, lerp: (a: T, b: T, t: Float) -> T): T =
    when (this) {
        is OpticalSizeValue.Fixed -> value
        is OpticalSizeValue.Responsive -> {
            val first = points.first()
            val last = points.last()
            when {
                shortestSide <= first.shortestDimension -> first.value
                shortestSide >= last.shortestDimension -> last.value
                else -> {
                    var result = last.value
                    for (index in 1 until points.size) {
                        val lo = points[index - 1]
                        val hi = points[index]
                        if (shortestSide <= hi.shortestDimension) {
                            val t = smoothstep(lo.shortestDimension.value, hi.shortestDimension.value, shortestSide.value)
                            result = lerp(lo.value, hi.value, t)
                            break
                        }
                    }
                    result
                }
            }
        }
    }

/** [resolve] convenience for the common case of a single interpolated [Dp]. */
public fun OpticalSizeValue<Dp>.resolve(shortestSide: Dp): Dp =
    resolve(shortestSide) { a, b, t -> Dp(a.value + (b.value - a.value) * t) }

/**
 * Scales every value carried by this [OpticalSizeValue] by [factor], preserving the Fixed/
 * Responsive shape - used by accessibility's reduce-transparency to shrink a style's authored
 * blur radius without needing to know the element's shortest side (resolution still happens at
 * draw time, against the already-scaled value).
 */
public fun OpticalSizeValue<Dp>.scale(factor: Float): OpticalSizeValue<Dp> = when (this) {
    is OpticalSizeValue.Fixed -> OpticalSizeValue.Fixed(value * factor)
    is OpticalSizeValue.Responsive -> OpticalSizeValue.Responsive(
        *points.map { OpticalSizePoint(it.shortestDimension, it.value * factor) }.toTypedArray(),
    )
}
