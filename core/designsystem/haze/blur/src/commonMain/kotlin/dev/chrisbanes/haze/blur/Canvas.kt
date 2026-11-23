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

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope

@OptIn(ExperimentalHazeApi::class)
internal inline fun DrawScope.withAlpha(
    alpha: Float,
    retainedLayer: RetainedGraphicsLayer,
    context: HazeEffectRuntimeDrawScope,
    crossinline block: DrawScope.() -> Unit,
) {
    if (alpha < 1f) {
        val layer = retainedLayer.acquire(context.requireGraphicsContext())
        layer.alpha = alpha
        layer.record { block() }
        drawLayer(layer)
    } else {
        retainedLayer.release()
        block()
    }
}
