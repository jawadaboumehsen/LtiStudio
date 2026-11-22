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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isFinite
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.withGraphicsLayer

internal inline fun DrawScope.translate(
    offset: Offset,
    block: DrawScope.() -> Unit,
) {
    if (offset.isFinite && offset != Offset.Zero) {
        translate(offset.x, offset.y, block)
    } else {
        block()
    }
}

@OptIn(ExperimentalHazeApi::class)
internal inline fun DrawScope.withAlpha(
    alpha: Float,
    context: HazeEffectRuntimeDrawScope,
    crossinline block: DrawScope.() -> Unit,
) {
    if (alpha < 1f) {
        context.withGraphicsLayer { layer ->
            layer.alpha = alpha
            layer.record { block() }
            drawLayer(layer)
        }
    } else {
        block()
    }
}
