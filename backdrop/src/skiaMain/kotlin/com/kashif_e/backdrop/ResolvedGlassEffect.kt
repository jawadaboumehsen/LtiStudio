package com.kashif_e.backdrop

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A progressive (fade-direction) blur band, resolved to plain values - backs
 * [ResolvedGlassEffect.progressiveBlur], the value-typed replacement for the raw
 * `effects = { progressiveBlur(...) }` lambda [GlassScrollEdgeFade] used to build by hand.
 */
@Immutable
public data class ResolvedProgressiveBlur(
    public val blurRadiusPx: Float,
    public val tintColor: Color,
    public val tintIntensity: Float,
    public val fadeStart: Float,
    public val fadeEnd: Float,
)

/**
 * A style's effect stack as backdrop-owned data instead of an imperative [BackdropEffectScope]
 * lambda - the resolved-value half of the dirty-tracking pipeline ([GlassEffectDirtyFields])
 * ported from Haze's `ResolvedHazeBlurStyle`/`ResolvedGlassStyle`. [DrawBackdropNode] diffs the
 * previous [ResolvedGlassEffect] against the new one field-by-field and only rebuilds the Skia
 * effect graph when a bit that matters actually flipped - no caller has to `remember()` anything,
 * because a data class already carries structural equality; a fresh instance describing the same
 * values is indistinguishable to the diff.
 *
 * [blur]/[lensHeight]/[lensAmount] stay [OpticalSizeValue] rather than plain px floats: their real
 * pixel value depends on the element's own shortest side, which is only known inside
 * [DrawBackdropNode] at draw time (the same place the old `GlassStyle.toEffects()` lambda used to
 * read it from the [BackdropEffectScope] receiver) - resolving them any earlier, at the caller's
 * composable call site, would silently break `OpticalSizeValue.Responsive` for any future style
 * that uses it.
 */
@Immutable
public data class ResolvedGlassEffect(
    public val blur: OpticalSizeValue<Dp> = OpticalSizeValue.Fixed(0.dp),
    public val lensHeight: OpticalSizeValue<Dp> = OpticalSizeValue.Fixed(0.dp),
    public val lensAmount: OpticalSizeValue<Dp> = OpticalSizeValue.Fixed(0.dp),
    public val lensDepthEffect: Boolean = false,
    public val lensChromaticAberration: Boolean = false,
    public val vibrancy: Boolean = false,
    public val brightness: Float = 0f,
    public val saturation: Float = 1f,
    public val grain: Float = 0f,
    public val progressiveBlur: ResolvedProgressiveBlur? = null,
) {
    public companion object {
        /** No effects at all - the identity value for a plain, unstyled backdrop sample. */
        public val None: ResolvedGlassEffect = ResolvedGlassEffect()
    }
}
