package com.kashif_e.backdrop.interaction

import androidx.compose.runtime.Immutable

/**
 * Declares the optics response for one interaction state - mirrors Haze's
 * `GlassInteractionScope`, narrowed to the channels `:backdrop`'s resolved-effect model actually
 * has (no separate lighting/white-point shader uniform channel the way Haze's glass material
 * does - `blurMultiplier`/`lensMultiplier`/`brightnessDelta` layer directly onto
 * `ResolvedGlassEffect`'s own fields, and `scale` is a draw-time canvas transform, not a shader
 * parameter).
 */
@BackdropInteractionDsl
public sealed interface BackdropInteractionScope {
    /** Multiplies the resolved blur radius. Finite, `0f..2f`. */
    public fun blurMultiplier(multiplier: Float)

    /** Multiplies the resolved lens height/amount together. Finite, `0f..2f`. */
    public fun lensMultiplier(multiplier: Float)

    /** Additive brightness delta layered onto the style's own `colorControls` brightness. Finite, `-1f..1f`. */
    public fun brightnessDelta(delta: Float)

    /** Uniform draw-time scale (press-squish / hover-lift). Finite, `0f < scale <= 1.5f`. */
    public fun scale(scale: Float)
}

@DslMarker
internal annotation class BackdropInteractionDsl

/** Resolved output of one [BackdropInteractionScope] declaration - `null` per channel means "not declared". */
internal data class BackdropInteractionValues(
    val blurMultiplier: Float? = null,
    val lensMultiplier: Float? = null,
    val brightnessDelta: Float? = null,
    val scale: Float? = null,
)

private class BackdropInteractionScopeImpl : BackdropInteractionScope {
    private var blurMultiplier: Float? = null
    private var lensMultiplier: Float? = null
    private var brightnessDelta: Float? = null
    private var scale: Float? = null

    override fun blurMultiplier(multiplier: Float) {
        require(multiplier.isFinite() && multiplier in 0f..2f) {
            "blurMultiplier must be finite and in 0f..2f, was $multiplier"
        }
        this.blurMultiplier = multiplier
    }

    override fun lensMultiplier(multiplier: Float) {
        require(multiplier.isFinite() && multiplier in 0f..2f) {
            "lensMultiplier must be finite and in 0f..2f, was $multiplier"
        }
        this.lensMultiplier = multiplier
    }

    override fun brightnessDelta(delta: Float) {
        require(delta.isFinite() && delta in -1f..1f) {
            "brightnessDelta must be finite and in -1f..1f, was $delta"
        }
        this.brightnessDelta = delta
    }

    override fun scale(scale: Float) {
        require(scale.isFinite() && scale > 0f && scale <= 1.5f) {
            "scale must be finite and in 0f < scale <= 1.5f, was $scale"
        }
        this.scale = scale
    }

    fun build(): BackdropInteractionValues =
        BackdropInteractionValues(blurMultiplier, lensMultiplier, brightnessDelta, scale)
}

/**
 * A [org.ide.lti.core.designsystem.theme.glass.GlassStyle]'s declared hover/press/focus optics
 * response, resolved into plain values at declaration time - mirrors Haze's
 * `buildGlassInteractionResponse` + `GlassInteractionSlot`. Plain data, not stored lambdas: the
 * owning style stays a real `@Immutable data class` with structural equality all the way through,
 * so there is no second lambda-identity problem for `BackdropInteractionController` to work
 * around (the exact bug [com.kashif_e.backdrop.ResolvedGlassEffect] itself replaced).
 *
 * When more than one state is simultaneously active, pressed wins over hovered wins over focused
 * - matches Haze's `resolveGlassInteractionTargets` last-applied-wins order.
 */
@Immutable
public data class BackdropInteractionResponse internal constructor(
    internal val hovered: BackdropInteractionValues? = null,
    internal val pressed: BackdropInteractionValues? = null,
    internal val focused: BackdropInteractionValues? = null,
)

/** Builds a [BackdropInteractionResponse], evaluating each declared block immediately. */
public fun backdropInteraction(
    hovered: (BackdropInteractionScope.() -> Unit)? = null,
    pressed: (BackdropInteractionScope.() -> Unit)? = null,
    focused: (BackdropInteractionScope.() -> Unit)? = null,
): BackdropInteractionResponse = BackdropInteractionResponse(
    hovered = hovered?.let { block -> BackdropInteractionScopeImpl().apply(block).build() },
    pressed = pressed?.let { block -> BackdropInteractionScopeImpl().apply(block).build() },
    focused = focused?.let { block -> BackdropInteractionScopeImpl().apply(block).build() },
)
