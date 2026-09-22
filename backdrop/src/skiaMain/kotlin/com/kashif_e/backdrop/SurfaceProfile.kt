package com.kashif_e.backdrop

/**
 * Which displacement-vs-depth curve [com.kashif_e.backdrop.effects.lens] uses across the
 * refraction band, giving the bevel a distinct physical character - ported from the surface
 * cross-section functions described at https://kube.io/blog/liquid-glass-css-svg/ (Snell's-law
 * ray-traced displacement per lens profile), approximated here as closed-form height curves
 * evaluated per-pixel in the shader rather than that article's offline-precomputed displacement
 * texture (SKSL has no equivalent to a baked SVG `feDisplacementMap` lookup, and a real-time
 * per-pixel Snell's-law ray trace is not worth its cost for a UI bevel).
 *
 * [Standard] is the engine's original bevel curve (a plain smoothstep ease) and stays the default
 * everywhere - every existing [com.kashif_e.backdrop.ResolvedGlassEffect] omits this field, so no
 * existing caller's rendered output changes. The other four are opt-in.
 */
public enum class SurfaceProfile {
    /** The original smoothstep bevel - default, byte-identical to pre-existing output. */
    Standard,

    /** `y = sqrt(1 - (1-x)^2)` - a spherical dome: steep near the edge, flat at the apex. */
    Circle,

    /** `y = (1 - (1-x)^4)^0.25` - flatter face, sharper corner falloff than [Circle]. */
    Squircle,

    /** `y = 1 - Circle(x)` - an inward-curving dish, heaviest right at the boundary edge. */
    Concave,

    /** A smoothstep blend of [Circle] and [Concave] - a raised rim over a dished center. */
    Lip,
}

/** The float uniform [com.kashif_e.backdrop.effects.lens] passes to the shader for this profile. */
internal val SurfaceProfile.shaderIndex: Float
    get() = ordinal.toFloat()
