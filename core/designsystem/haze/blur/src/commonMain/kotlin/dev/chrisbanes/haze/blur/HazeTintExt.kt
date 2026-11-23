/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.takeOrElse

internal fun HazeColorEffect.boostForFallback(blurRadius: Dp): HazeColorEffect = when (this) {
    is TintColorHazeColorEffect -> {
        // For color, we can boost the alpha
        val resolved = blurRadius.takeOrElse { HazeBlurDefaults.blurRadius }
        val boosted = color.boostAlphaForBlurRadius(resolved)
        HazeColorEffect.tint(color = boosted, blendMode = blendMode)
    }
    else -> this
}

/**
 * In this implementation, the only tool we have is translucency.
 */
private fun Color.boostAlphaForBlurRadius(blurRadius: Dp): Color {
    // We treat a blur radius of 72.dp as near 'opaque', and linearly boost using that
    val factor = 1 + (blurRadius.value / 72)
    return copy(alpha = (alpha * factor).coerceAtMost(1f))
}
