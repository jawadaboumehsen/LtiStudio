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

import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.layer.GraphicsLayer

internal class RetainedGlassGroupAlphaLayer {
    var layer: GraphicsLayer? = null
        private set

    /** The context that created [layer], which alone can release it. */
    private var owner: GraphicsContext? = null

    val isAvailable: Boolean get() = layer?.isReleased == false

    fun prepare(required: Boolean, graphicsContext: GraphicsContext) {
        if (required) {
            if (layer?.isReleased != false) {
                layer = graphicsContext.createGraphicsLayer()
                owner = graphicsContext
            }
        } else {
            release(graphicsContext)
        }
    }

    fun release(graphicsContext: GraphicsContext?) {
        releaseLayer(layer, owner ?: graphicsContext)
        layer = null
        owner = null
    }
}

internal fun requiresGlassGroupAlpha(alpha: Float): Boolean = alpha > 0f && alpha < 1f
