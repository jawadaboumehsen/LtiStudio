package com.kashif_e.backdrop

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.shadow.InnerShadow
import com.kashif_e.backdrop.shadow.Shadow

/**
 * Draw a backdrop with effects, shadows, and highlights.
 * 
 * This is the main entry point for creating liquid glass and frosted glass effects.
 * 
 * @param backdrop The backdrop content to draw (use [CanvasBackdrop] for custom drawing).
 * @param shape The shape of the backdrop area.
 * @param effects Effects to apply (blur, color controls, lens, etc.).
 * @param highlight Optional highlight effect for glossy appearance.
 * @param shadow Optional drop shadow.
 * @param innerShadow Optional inner shadow for depth.
 * @param layerBlock Optional graphics layer configuration.
 * @param exportedBackdrop Optional layer backdrop for advanced composition.
 * @param onDrawBehind Optional callback to draw behind the backdrop.
 * @param onDrawBackdrop Optional callback to customize backdrop drawing.
 * @param onDrawSurface Optional callback to draw on the surface.
 * @param onDrawFront Optional callback to draw in front of everything.
 * @param inputScale Fraction of the element's pixel size (`0f` exclusive to `1f` inclusive) at
 * which the sampled backdrop is captured and blurred before being scaled back up to fill the
 * element. `1f` (the default) records at full resolution — byte-identical to the pre-B1 behavior.
 * Values below `1f` cut blur cost roughly with the square of the scale (e.g. `0.5f` blurs a
 * quarter of the pixels) at a cost invisible once blurred; ported from Haze's
 * `GlassInputScalePolicy`.
 */
expect fun Modifier.drawBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    effects: BackdropEffectScope.() -> Unit,
    highlight: (() -> Highlight?)? = null,
    shadow: (() -> Shadow?)? = null,
    innerShadow: (() -> InnerShadow?)? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    onDrawBehind: (DrawScope.() -> Unit)? = null,
    onDrawBackdrop: (DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit)? = null,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    onDrawFront: (DrawScope.() -> Unit)? = null,
    inputScale: Float = 1f
): Modifier

/**
 * Draw a plain backdrop without shadows or highlights.
 * Use this for simpler effects or when you need more control.
 */
expect fun Modifier.drawPlainBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    effects: BackdropEffectScope.() -> Unit,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    onDrawBehind: (DrawScope.() -> Unit)? = null,
    onDrawBackdrop: (DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit)? = null,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    onDrawFront: (DrawScope.() -> Unit)? = null
): Modifier
