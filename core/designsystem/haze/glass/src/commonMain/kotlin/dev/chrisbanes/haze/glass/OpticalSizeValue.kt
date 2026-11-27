/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package dev.chrisbanes.haze.glass

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.jvm.JvmInline

/**
 * An optical parameter value that is either fixed or responsive to a material's shortest
 * dimension.
 *
 * This type does not define interpolation for arbitrary [T]. [GlassOptics] accepts
 * [OpticalSizeValue] only for its `Float` depth and [Dp] blur-radius properties and owns the
 * corresponding interpolation and value validation.
 *
 * Future minor releases may add subtypes. Keep an `else` branch when you match exhaustively with `when`.
 */
sealed interface OpticalSizeValue<T> {
    /** A value which does not change with the material size. */
    @JvmInline
    value class Fixed<T>(
        /** The fixed value. */
        val value: T,
    ) : OpticalSizeValue<T>

    /**
     * A value resolved from two or more shortest-dimension [points].
     *
     * Constructor inputs are snapshotted. Point dimensions must be positive, finite, and strictly
     * increasing. [GlassOptics] clamps outside the authored range and uses smoothstep interpolation
     * between adjacent points.
     */
    @JvmInline
    value class Responsive<T> private constructor(
        /** The read-only snapshot of ordered interpolation points. */
        val points: List<OpticalSizePoint<T>>,
    ) : OpticalSizeValue<T> {
        /**
         * Creates a responsive value from ordered [points].
         *
         * @throws IllegalArgumentException if fewer than two points are provided or their dimensions
         * are not strictly increasing.
         */
        constructor(vararg points: OpticalSizePoint<T>) : this(
            buildList(points.size) { addAll(points) },
        )

        /**
         * Creates a responsive value from a collection of ordered [points].
         *
         * @throws IllegalArgumentException if fewer than two points are provided or their dimensions
         * are not strictly increasing.
         */
        constructor(points: Collection<OpticalSizePoint<T>>) : this(
            buildList(points.size) { addAll(points) },
        )

        init {
            require(points.size >= 2) { "points must contain at least two values" }
            for (index in 1..<points.size) {
                require(points[index].shortestDimension > points[index - 1].shortestDimension) {
                    "points shortest dimensions must be strictly increasing"
                }
            }
        }
    }
}

/**
 * Associates a value with a positive shortest dimension.
 *
 * @param shortestDimension The positive shortest dimension at which [value] applies.
 * @param value The value to resolve at [shortestDimension].
 */
data class OpticalSizePoint<T>(
    val shortestDimension: Dp,
    val value: T,
) {
    init {
        require(shortestDimension.value.isFinite()) { "shortestDimension must be finite" }
        require(shortestDimension > 0.dp) { "shortestDimension must be positive" }
    }
}
