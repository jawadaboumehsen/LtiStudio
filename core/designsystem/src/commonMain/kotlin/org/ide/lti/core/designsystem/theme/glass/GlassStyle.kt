/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.kashif_e.backdrop.BackdropEffectScope
import com.kashif_e.backdrop.OpticalSizeValue
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.colorControls
import com.kashif_e.backdrop.effects.frostedGrain
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.resolve
import com.kashif_e.backdrop.shadow.InnerShadow
import com.kashif_e.backdrop.shadow.Shadow
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.LensRefraction
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.resolve
import kotlin.math.min

/**
 * The material of one glass role (bar, rail, card, ...), as a value instead of a
 * [BackdropEffectScope] lambda + a handful of loose params passed separately at every call site.
 * Sketch and field set from backdrop-refactor-plan-20260920.md §1.1, adapted to what `backdrop/`
 * actually exposes today (`vibrancy` is a binary toggle in this engine, not a variable strength;
 * [rim] resolves through [Rim] to the existing `GlassBevel` shader rather than a new one).
 *
 * Consumed via [GlassStyles] (the named role values) and
 * [org.ide.lti.core.designsystem.component.primitives.glass] (§1.2's resolution function).
 */
@Immutable
public data class GlassStyle(
    public val surface: Color,
    public val shape: Shape = RectangleShape,
    public val tint: Color = Color.Transparent,
    public val border: Color = Color.Transparent,
    public val borderWidth: Dp = StrokeWidth.Standard,
    public val blur: OpticalSizeValue<Dp> = OpticalSizeValue.Fixed(BackdropBlur.Standard),
    public val lens: OpticalSizeValue<LensRefraction> = OpticalSizeValue.Fixed(LensRefraction.Surface),
    public val lensDepthEffect: Boolean = false,
    public val lensChromaticAberration: Boolean = false,
    public val vibrancy: Boolean = true,
    public val brightness: Float = 0f,
    public val saturation: Float = 1f,
    public val grain: Float = 0f,
    public val rim: Rim? = Rim.None,
    public val shadow: Shadow? = null,
    public val innerShadow: InnerShadow? = null,
    public val inputScale: Float = 1f,
) {
    init {
        require(grain.isFinite() && grain in 0f..1f) { "grain must be finite and in 0f..1f, was $grain" }
        require(borderWidth.isSpecified && borderWidth.value.isFinite() && borderWidth >= 0.dp) {
            "borderWidth must be specified, finite, and non-negative, was $borderWidth"
        }
        require(inputScale.isFinite() && inputScale > 0f && inputScale <= 1f) {
            "inputScale must be finite and in 0f < value <= 1f, was $inputScale"
        }
        require(brightness.isFinite()) { "brightness must be finite, was $brightness" }
        require(saturation.isFinite() && saturation >= 0f) {
            "saturation must be finite and non-negative, was $saturation"
        }
    }

    /** Copy-based style composition - no scope DSL, per the plan's sketch. */
    public fun then(block: GlassStyle.() -> GlassStyle): GlassStyle = block()
}

/**
 * Resolves [GlassStyle.blur]/[GlassStyle.lens] against the backdrop's own size (read from the
 * [BackdropEffectScope] receiver, so a `Responsive` style scales correctly without the caller
 * having to know the element's size up front) and applies the resolved effect stack.
 */
public fun GlassStyle.toEffects(): BackdropEffectScope.() -> Unit = {
    val shortestSide = min(size.width, size.height).toDp()
    if (brightness != 0f || saturation != 1f) {
        colorControls(brightness = brightness, saturation = saturation)
    }
    if (vibrancy) vibrancy()
    val resolvedBlur = blur.resolve(shortestSide)
    if (resolvedBlur > 0.dp) blur(resolvedBlur.toPx())
    val resolvedLens = lens.resolve(shortestSide)
    if (resolvedLens.height > 0.dp || resolvedLens.amount > 0.dp) {
        lens(
            resolvedLens.height.toPx(),
            resolvedLens.amount.toPx(),
            depthEffect = lensDepthEffect,
            chromaticAberration = lensChromaticAberration,
        )
    }
    if (grain > 0f) frostedGrain(grain)
}
