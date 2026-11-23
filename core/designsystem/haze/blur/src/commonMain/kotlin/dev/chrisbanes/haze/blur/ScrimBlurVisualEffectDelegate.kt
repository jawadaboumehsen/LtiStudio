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
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.TrimMemoryLevel

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
internal class ScrimBlurVisualEffectDelegate(
    val blurVisualEffect: BlurVisualEffect,
) : BlurVisualEffect.Delegate {
    // Layers kept across draws; each is released when a draw stops using it, on detach and on trim.
    private val alphaLayer = RetainedGraphicsLayer()
    private val tintLayer = RetainedGraphicsLayer()

    override val drawsContentBehind: Boolean get() = true

    override fun DrawScope.draw(context: HazeEffectRuntimeDrawScope) {
        val scrimTint = blurVisualEffect.fallbackTint
            ?: blurVisualEffect.colorEffects?.firstOrNull()
                ?.boostForFallback(blurVisualEffect.blurRadius.takeOrElse { 0.dp })
            ?: return releaseLayers()

        withAlpha(alpha = blurVisualEffect.alpha, retainedLayer = alphaLayer, context = context) {
            drawScrim(
                colorEffect = scrimTint,
                context = context,
                mask = blurVisualEffect.mask,
                progressiveMask = blurVisualEffect.progressiveBrush,
                maskedLayer = tintLayer,
            )
        }
    }

    override fun detach() = releaseLayers()

    override fun clearRetainedOutput() = releaseLayers()

    // The layers are re-recorded at the current size on every draw, so a size change must not
    // release them (the default would forward to [clearRetainedOutput]).
    override fun invalidateRetainedOutput() = Unit

    override fun onTrimMemory(context: HazeEffectLifecycleScope, level: TrimMemoryLevel) {
        if (level.severity >= TrimMemoryLevel.MODERATE.severity) releaseLayers()
    }

    private fun releaseLayers() {
        alphaLayer.release()
        tintLayer.release()
    }
}
