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
import com.kashif_e.backdrop.OpticalSizePoint
import com.kashif_e.backdrop.OpticalSizeValue
import com.kashif_e.backdrop.ResolvedGlassEffect
import com.kashif_e.backdrop.interaction.BackdropInteractionResponse
import com.kashif_e.backdrop.shadow.InnerShadow
import com.kashif_e.backdrop.shadow.Shadow
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.LensRefraction
import org.ide.lti.core.designsystem.theme.StrokeWidth

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
    /** Declared hover/press/focus optics response - see [BackdropInteractionResponse]. Animated
     * by a node-owned interaction controller, decoupled from this style's own recomposition. */
    public val interaction: BackdropInteractionResponse? = null,
    /** When `true`, ignores [inputScale] and resolves a downscale tier automatically from blur
     * radius x visible area, with hysteresis - see `BackdropInputScalePolicy`. Opt-in per style;
     * `false` preserves [inputScale] exactly as authored. */
    public val adaptiveInputScale: Boolean = false,
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
 * Resolves this style's effect-relevant fields into a backdrop-owned [ResolvedGlassEffect] -
 * replaces the old `toEffects()` (`BackdropEffectScope.() -> Unit` lambda). [blur]/[lens] stay
 * [OpticalSizeValue] rather than resolved px: the shortest-side-dependent resolution now happens
 * inside `:backdrop`'s `DrawBackdropNode` at draw time, against the element's real live size -
 * exactly where the old lambda used to read it from the `BackdropEffectScope` receiver. Since
 * [ResolvedGlassEffect] is a plain `data class`, a fresh call for an unchanged style produces a
 * structurally-`==` value, so no caller needs to `remember()` the result for `:backdrop`'s
 * dirty-bitmask diff to see "no change".
 */
public fun GlassStyle.resolve(): ResolvedGlassEffect = ResolvedGlassEffect(
    blur = blur,
    lensHeight = lens.mapValue { it.height },
    lensAmount = lens.mapValue { it.amount },
    lensDepthEffect = lensDepthEffect,
    lensChromaticAberration = lensChromaticAberration,
    vibrancy = vibrancy,
    brightness = brightness,
    saturation = saturation,
    grain = grain,
)

/** Maps each point's value with [selector], preserving [OpticalSizeValue]'s Fixed/Responsive shape. */
private inline fun OpticalSizeValue<LensRefraction>.mapValue(selector: (LensRefraction) -> Dp): OpticalSizeValue<Dp> =
    when (this) {
        is OpticalSizeValue.Fixed -> OpticalSizeValue.Fixed(selector(value))
        is OpticalSizeValue.Responsive -> OpticalSizeValue.Responsive(
            *points.map { OpticalSizePoint(it.shortestDimension, selector(it.value)) }.toTypedArray(),
        )
    }
