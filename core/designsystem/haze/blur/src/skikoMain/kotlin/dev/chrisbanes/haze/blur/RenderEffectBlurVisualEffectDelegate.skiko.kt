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
import dev.chrisbanes.haze.asComposeRenderEffect

@OptIn(InternalHazeApi::class)
internal actual fun RenderEffectBlurVisualEffectDelegate.drawProgressiveEffect(
    drawScope: DrawScope,
    progressive: HazeProgressive,
    contentLayer: GraphicsLayer,
    context: HazeEffectRuntimeDrawScope,
    inputScale: Float,
) = with(drawScope) {
    contentLayer.renderEffect = blurVisualEffect
        .getOrCreateRenderEffect(
            context = context,
            inputScale = inputScale,
            progressive = progressive,
        )
        .asComposeRenderEffect()
    contentLayer.alpha = blurVisualEffect.alpha

    // Finally draw the layer
    drawLayer(contentLayer)
}
