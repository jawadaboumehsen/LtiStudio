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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.asBrush

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
internal class ScrimBlurVisualEffectDelegate(
    val blurVisualEffect: BlurVisualEffect,
) : BlurVisualEffect.Delegate {
    override fun DrawScope.draw(context: HazeEffectRuntimeDrawScope) {
        val scrimTint = blurVisualEffect.fallbackTint
            ?: blurVisualEffect.colorEffects?.firstOrNull()
                ?.boostForFallback(blurVisualEffect.blurRadius.takeOrElse { 0.dp })
            ?: return

        withAlpha(alpha = blurVisualEffect.alpha, context = context) {
            drawScrim(
                colorEffect = scrimTint,
                context = context,
                mask = blurVisualEffect.mask ?: blurVisualEffect.progressive?.asBrush(),
            )
        }
    }
}
