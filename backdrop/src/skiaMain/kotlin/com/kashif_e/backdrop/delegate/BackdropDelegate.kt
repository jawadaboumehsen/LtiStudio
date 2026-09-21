package com.kashif_e.backdrop.delegate

import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Renders (or skips) one backdrop element's sampled content each draw - mirrors Haze's
 * `GlassRuntimeEffect.Delegate` split between `RuntimeShaderGlassDelegate` and
 * `FallbackGlassDelegate`. `DrawBackdropNode` swaps between [ShaderBackdropDelegate] and
 * [FallbackBackdropDelegate] based on the render budget (`BackdropRenderBudgetDecision`) and a
 * sticky shader-construction-failure flag.
 */
internal interface BackdropDelegate {
    fun DrawScope.draw()
}

/**
 * The real path: records the ambient backdrop into an offscreen layer and composites it back
 * through the Skia `ImageFilter` chain `BackdropEffectScopeImpl.applyResolved` built - today's
 * real rendering, extracted from `DrawBackdropNode` as-is. [recordAndDraw] is injected rather
 * than called directly because the actual recording uses `DrawModifierNode.recordLayer`, an
 * extension only callable on the node itself - this keeps the delegate a plain class instead of
 * requiring it to *be* a `Modifier.Node`.
 */
internal class ShaderBackdropDelegate(
    private val recordAndDraw: DrawScope.() -> Unit,
) : BackdropDelegate {
    override fun DrawScope.draw() {
        recordAndDraw()
    }
}

/**
 * The degraded path: renders nothing. `:backdrop`'s `onDrawSurface` callback already draws the
 * element's flat surface/tint fill unconditionally (every real caller, e.g. `Modifier.glass`,
 * supplies one) - unlike Haze's own fallback, which has to replicate that fill itself because its
 * delegate owns drawing the surface too, `:backdrop`'s surface fill and backdrop sample are
 * already separate draw stages, so degrading is simply *not* drawing the blurred sample: the
 * surface fill alone is already a complete, correct degraded look.
 */
internal object FallbackBackdropDelegate : BackdropDelegate {
    override fun DrawScope.draw() {
        // Intentionally empty - see the class doc.
    }
}
