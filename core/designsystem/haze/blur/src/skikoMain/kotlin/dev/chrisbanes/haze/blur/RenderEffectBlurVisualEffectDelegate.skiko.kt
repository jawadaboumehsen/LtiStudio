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
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.InternalHazeApi

internal actual fun BlurVisualEffect.invalidateRenderEffectOnInputChange() = Unit

@OptIn(InternalHazeApi::class)
internal actual fun RenderEffectBlurVisualEffectDelegate.drawProgressiveEffect(
    drawScope: DrawScope,
    progressive: HazeProgressive,
    contentLayer: GraphicsLayer,
    context: HazeEffectRuntimeDrawScope,
    inputScale: Float,
) = with(drawScope) {
    effectSlot.apply(
        contentLayer,
        blurVisualEffect.getOrCreateRenderEffect(
            context = context,
            inputScale = inputScale,
            progressive = progressive,
            slot = effectSlot,
        ),
    )
    contentLayer.alpha = blurVisualEffect.alpha

    // Finally draw the layer
    drawLayer(contentLayer)
}
