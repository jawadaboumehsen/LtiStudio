/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package dev.chrisbanes.haze.blur

import dev.chrisbanes.haze.HazePerformanceMode
import kotlin.math.sqrt

internal const val BLUR_FULL_RESOLUTION_SCALE = 1f

private const val MIN_FIXED_PIXEL_FRACTION = 0.25f
private const val MAX_FIXED_PIXEL_FRACTION = 1f

/** Resolves the built-in Blur input scale from a normalized quality fraction. */
@Suppress("DEPRECATION")
internal fun HazePerformanceMode.resolveBlurInputScale(): Float = when (this) {
    HazePerformanceMode.Adaptive -> HazePerformanceMode.Default.resolveBlurInputScale()
    is HazePerformanceMode.Fixed ->
        sqrt(MIN_FIXED_PIXEL_FRACTION + (MAX_FIXED_PIXEL_FRACTION - MIN_FIXED_PIXEL_FRACTION) * qualityFraction)
}
