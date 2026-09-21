/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.kashif_e.backdrop.ResolvedGlassEffect
import com.kashif_e.backdrop.scale

/**
 * Semantic accessibility preferences that change how glass separates itself from its content -
 * ported from Haze's `GlassAccessibilitySettings`/`LocalGlassAccessibilitySettings`. This module
 * deliberately does not read platform accessibility services itself: hosts (`lti-desktop`, etc.)
 * map their platform's setting and pass it to [LtiTheme]'s `accessibility` parameter, exactly
 * like [LocalReducedMotion] already works for reduced motion.
 *
 * Consumed only by [org.ide.lti.core.designsystem.component.primitives.glass] - the same single
 * facade that already reads [GlassTheme.effectsEnabled]/[GlassTheme.scene] on a style's behalf.
 * [reduceTransparency] scales the resolved blur radius by the same 0.65x Haze uses;
 * [increaseContrast]/[showBorders] both force a visible border, since `:backdrop`'s effect model
 * has no separate contrast/specular/edge-softness fields the way Haze's `ResolvedGlassStyle`
 * does - kept as two distinct flags (not folded into one) only because a host may map them from
 * two distinct platform preferences, matching Haze's own settings shape.
 */
@Immutable
data class GlassAccessibilitySettings(
    val reduceTransparency: Boolean = false,
    val increaseContrast: Boolean = false,
    val showBorders: Boolean = false,
)

/** Accessibility preferences inherited by glass components in this composition subtree. */
val LocalGlassAccessibilitySettings: ProvidableCompositionLocal<GlassAccessibilitySettings> =
    staticCompositionLocalOf { GlassAccessibilitySettings() }

/** Haze's own reduce-transparency blur multiplier (`GlassRenderParams.withAccessibility`). */
private const val REDUCE_TRANSPARENCY_BLUR_MULTIPLIER = 0.65f

/**
 * Applies [settings] to this resolved effect - ported from Haze's
 * `ResolvedGlassStyle.withAccessibility()`. `reduceTransparency` shrinks [ResolvedGlassEffect.blur]
 * by the same 0.65x Haze uses, letting more of the real background show through; `increaseContrast`
 * and `showBorders` have no optics-level equivalent here (Haze's `contrast`/`specularIntensity`/
 * `edgeSoftnessPx` fields don't exist on [ResolvedGlassEffect]) and are instead applied by
 * [org.ide.lti.core.designsystem.component.primitives.glass] forcing a visible border, the closest
 * existing lever with the same practical effect.
 */
internal fun ResolvedGlassEffect.withAccessibility(settings: GlassAccessibilitySettings): ResolvedGlassEffect =
    if (settings.reduceTransparency) {
        copy(blur = blur.scale(REDUCE_TRANSPARENCY_BLUR_MULTIPLIER))
    } else {
        this
    }
