package com.kashif_e.backdrop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Outline
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.highlight.HighlightElement
import com.kashif_e.backdrop.shadow.InnerShadow
import com.kashif_e.backdrop.shadow.InnerShadowElement
import com.kashif_e.backdrop.shadow.Shadow
import com.kashif_e.backdrop.shadow.ShadowElement
import kotlin.math.ceil
import kotlin.math.roundToInt

private val DefaultOnDrawBackdrop: DrawScope.(DrawScope.() -> Unit) -> Unit = { it() }

/**
 * Diagnostic counter for how often [DrawBackdropNode] recomputes its effect graph
 * (`effectScope.apply(effects)` + reassigning `renderEffect`) - the expensive part of a glass
 * element's re-render, distinct from the cheap per-frame draw. Used to verify B2 (rim/interaction
 * stage split, backdrop-refactor-plan-20260920.md): hover/press must not retrigger this.
 */
internal object DrawBackdropDiagnostics {
    var updateEffectsCallCount: Int = 0
}

/**
 * Apply a plain backdrop effect without shadow/highlight decorations.
 */
actual fun Modifier.drawPlainBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    effects: BackdropEffectScope.() -> Unit,
    layerBlock: (GraphicsLayerScope.() -> Unit)?,
    exportedBackdrop: LayerBackdrop?,
    onDrawBehind: (DrawScope.() -> Unit)?,
    onDrawBackdrop: (DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit)?,
    onDrawSurface: (DrawScope.() -> Unit)?,
    onDrawFront: (DrawScope.() -> Unit)?
): Modifier {
    val shapeProvider = ShapeProvider(shape)
    val actualOnDrawBackdrop = onDrawBackdrop ?: DefaultOnDrawBackdrop
    return this
        .then(
            if (layerBlock != null) {
                Modifier.graphicsLayer(layerBlock)
            } else {
                Modifier
            }
        )
        .then(
            DrawBackdropElement(
                backdrop = backdrop,
                shapeProvider = shapeProvider,
                effects = effects,
                layerBlock = layerBlock,
                onDrawBehind = onDrawBehind,
                onDrawBackdrop = actualOnDrawBackdrop,
                onDrawSurface = onDrawSurface,
                onDrawFront = onDrawFront,
                inputScale = 1f
            )
        )
}

private val DefaultHighlight = { Highlight.Default }
private val DefaultShadow = { Shadow.Default }

/**
 * Apply a backdrop effect with optional shadow/highlight.
 */
actual fun Modifier.drawBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    effects: BackdropEffectScope.() -> Unit,
    highlight: (() -> Highlight?)?,
    shadow: (() -> Shadow?)?,
    innerShadow: (() -> InnerShadow?)?,
    layerBlock: (GraphicsLayerScope.() -> Unit)?,
    exportedBackdrop: LayerBackdrop?,
    onDrawBehind: (DrawScope.() -> Unit)?,
    onDrawBackdrop: (DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit)?,
    onDrawSurface: (DrawScope.() -> Unit)?,
    onDrawFront: (DrawScope.() -> Unit)?,
    inputScale: Float
): Modifier {
    requireValidInputScale(inputScale)
    val shapeProvider = ShapeProvider(shape)
    val actualOnDrawBackdrop = onDrawBackdrop ?: DefaultOnDrawBackdrop
    return this
        .then(
            if (layerBlock != null) {
                Modifier.graphicsLayer(layerBlock)
            } else {
                Modifier
            }
        )
        .then(
            if (innerShadow != null) {
                InnerShadowElement(
                    shapeProvider = shapeProvider,
                    shadow = innerShadow
                )
            } else {
                Modifier
            }
        )
        .then(
            if (shadow != null) {
                ShadowElement(
                    shapeProvider = shapeProvider,
                    shadow = shadow
                )
            } else {
                Modifier
            }
        )
        .then(
            if (highlight != null) {
                HighlightElement(
                    shapeProvider = shapeProvider,
                    highlight = highlight
                )
            } else {
                Modifier
            }
        )
        .then(
            DrawBackdropElement(
                backdrop = backdrop,
                shapeProvider = shapeProvider,
                effects = effects,
                layerBlock = layerBlock,
                onDrawBehind = onDrawBehind,
                onDrawBackdrop = actualOnDrawBackdrop,
                onDrawSurface = onDrawSurface,
                onDrawFront = onDrawFront,
                inputScale = inputScale
            )
        )
}

/** Validates the `inputScale` contract shared by [drawBackdrop] and [DrawBackdropNode]. */
private fun requireValidInputScale(inputScale: Float) {
    require(inputScale.isFinite() && inputScale > 0f && inputScale <= 1f) {
        "inputScale must be finite and in 0f < value <= 1f, was $inputScale"
    }
}

private class DrawBackdropElement(
    val backdrop: Backdrop,
    val shapeProvider: ShapeProvider,
    val effects: BackdropEffectScope.() -> Unit,
    val layerBlock: (GraphicsLayerScope.() -> Unit)?,
    val onDrawBehind: (DrawScope.() -> Unit)?,
    val onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit,
    val onDrawSurface: (DrawScope.() -> Unit)?,
    val onDrawFront: (DrawScope.() -> Unit)?,
    val inputScale: Float
) : ModifierNodeElement<DrawBackdropNode>() {

    override fun create(): DrawBackdropNode {
        return DrawBackdropNode(
            backdrop = backdrop,
            shapeProvider = shapeProvider,
            effects = effects,
            layerBlock = layerBlock,
            onDrawBehind = onDrawBehind,
            onDrawBackdrop = onDrawBackdrop,
            onDrawSurface = onDrawSurface,
            onDrawFront = onDrawFront,
            inputScale = inputScale
        )
    }

    override fun update(node: DrawBackdropNode) {
        node.backdrop = backdrop
        node.shapeProvider = shapeProvider
        node.effects = effects
        node.layerBlock = layerBlock
        node.onDrawBehind = onDrawBehind
        node.onDrawBackdrop = onDrawBackdrop
        node.onDrawSurface = onDrawSurface
        node.onDrawFront = onDrawFront
        node.inputScale = inputScale
        node.invalidateDrawCache()
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "drawBackdrop"
        properties["backdrop"] = backdrop
        properties["shapeProvider"] = shapeProvider
        properties["effects"] = effects
        properties["layerBlock"] = layerBlock
        properties["onDrawBehind"] = onDrawBehind
        properties["onDrawBackdrop"] = onDrawBackdrop
        properties["onDrawSurface"] = onDrawSurface
        properties["onDrawFront"] = onDrawFront
        properties["inputScale"] = inputScale
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DrawBackdropElement) return false

        if (backdrop != other.backdrop) return false
        if (shapeProvider != other.shapeProvider) return false
        if (effects != other.effects) return false
        if (layerBlock != other.layerBlock) return false
        if (onDrawBehind != other.onDrawBehind) return false
        if (onDrawBackdrop != other.onDrawBackdrop) return false
        if (onDrawSurface != other.onDrawSurface) return false
        if (onDrawFront != other.onDrawFront) return false
        if (inputScale != other.inputScale) return false

        return true
    }

    override fun hashCode(): Int {
        var result = backdrop.hashCode()
        result = 31 * result + shapeProvider.hashCode()
        result = 31 * result + effects.hashCode()
        result = 31 * result + (layerBlock?.hashCode() ?: 0)
        result = 31 * result + (onDrawBehind?.hashCode() ?: 0)
        result = 31 * result + onDrawBackdrop.hashCode()
        result = 31 * result + (onDrawSurface?.hashCode() ?: 0)
        result = 31 * result + (onDrawFront?.hashCode() ?: 0)
        result = 31 * result + inputScale.hashCode()
        return result
    }
}

private class DrawBackdropNode(
    var backdrop: Backdrop,
    var shapeProvider: ShapeProvider,
    var effects: BackdropEffectScope.() -> Unit,
    var layerBlock: (GraphicsLayerScope.() -> Unit)?,
    var onDrawBehind: (DrawScope.() -> Unit)?,
    var onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit,
    var onDrawSurface: (DrawScope.() -> Unit)?,
    var onDrawFront: (DrawScope.() -> Unit)?,
    var inputScale: Float
) : LayoutModifierNode, DrawModifierNode, GlobalPositionAwareModifierNode, ObserverModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private val effectScope = object : BackdropEffectScopeImpl() {
        override val shape: Shape get() = shapeProvider.innerShape
    }

    private var graphicsLayer: GraphicsLayer? = null

    private val layoutLayerBlock: GraphicsLayerScope.() -> Unit = {
        // The clip here is redundant: ContentDrawScope.draw() below already wraps
        // everything it draws — including drawContent() — in
        // canvas.clipOutline(outline, path) against the identical outline,
        // same bounds, same coordinate space. Keeping both means the shape's curve gets
        // its anti-aliased coverage computed twice: once when this Offscreen layer is
        // rasterized against its own clip (SkiaGraphicsLayer sets
        // renderNode.setClipPath(..., antiAlias = true)), and again when that layer is
        // composited under the canvas clip. On curved edges that reads as a rough,
        // doubled boundary. The layer itself is kept for invalidation isolation.
        clip = false
        compositingStrategy = CompositingStrategy.Offscreen
    }

    private var layoutCoordinates: LayoutCoordinates? by mutableStateOf(null, neverEqualPolicy())

    private var padding by mutableFloatStateOf(0f)
    
    // Path for clipping the backdrop to shape
    private var clipPath: Path? = null

    private val recordBackdropBlock: (DrawScope.() -> Unit) = {
        val canvas = drawContext.canvas
        // inputScale < 1f: the recording canvas is smaller than the element's real size (see
        // drawBackdropLayer). Scaling the canvas here - before the padding translate and the
        // backdrop sample - makes every subsequent draw in this block (including the ambient
        // layer's 1:1, unscaled drawLayer() inside LayerBackdrop.drawBackdrop) land downsampled
        // onto the smaller canvas, in the same real-world position it would occupy at full
        // resolution. At 1f this is a no-op scale, so behavior is unchanged.
        val needsScale = inputScale != 1f
        if (needsScale) {
            canvas.save()
            canvas.scale(inputScale, inputScale)
        }
        // Rounded up (not truncated) and to the exact same integer drawBackdropLayer() uses for
        // the recorded layer's size/offset below - a raw fractional padding here (e.g. 2.5px at
        // 125% display scaling) would translate the recorded content by a sub-pixel amount that
        // the layer's IntSize/IntOffset math can't represent, misaligning the recorded backdrop
        // from where it gets placed and softening/aliasing every glass element's edges.
        val currentPadding = ceil(padding).toInt().toFloat()

        if (currentPadding != 0f) {
            canvas.translate(currentPadding, currentPadding)
        }
        onDrawBackdrop {
            with(backdrop) {
                drawBackdrop(
                    density = effectScope,
                    coordinates = layoutCoordinates,
                    layerBlock = layerBlock
                )
            }
        }
        if (currentPadding != 0f) {
            canvas.translate(-currentPadding, -currentPadding)
        }
        if (needsScale) {
            canvas.restore()
        }
    }

    private val drawBackdropLayer: DrawScope.() -> Unit = {
        val layer = graphicsLayer
        if (layer != null) {
            // Same rounding as recordBackdropBlock's currentPadding - both must agree exactly.
            val currentPadding = ceil(padding).toInt()
            val currentSize = size
            val fullWidth = ceil(currentSize.width).toInt() + currentPadding * 2
            val fullHeight = ceil(currentSize.height).toInt() + currentPadding * 2

            // inputScale < 1f records fewer pixels (the blur pass is cheaper), then the layer's
            // own scaleX/scaleY compositing transform stretches it back to full size - the same
            // downsample-then-upscale trade Haze's GlassInputScalePolicy makes. At 1f (the
            // default) recordWidth == fullWidth and scaleX/Y == 1f: byte-identical to pre-B1.
            val recordWidth = (fullWidth * inputScale).roundToInt().coerceAtLeast(1)
            val recordHeight = (fullHeight * inputScale).roundToInt().coerceAtLeast(1)

            with(this@DrawBackdropNode) {
                recordLayer(
                    layer,
                    size = IntSize(recordWidth, recordHeight),
                    block = recordBackdropBlock
                )
            }

            layer.pivotOffset = Offset.Zero
            layer.scaleX = fullWidth.toFloat() / recordWidth
            layer.scaleY = fullHeight.toFloat() / recordHeight
            layer.topLeft =
                if (currentPadding != 0) IntOffset(-currentPadding, -currentPadding)
                else IntOffset.Zero
            drawLayer(layer)
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(IntOffset.Zero, layerBlock = layoutLayerBlock)
        }
    }

    override fun ContentDrawScope.draw() {
        if (effectScope.update(this)) {
            updateEffects()
        }

        val canvas = drawContext.canvas
        val outline = shapeProvider.shape.createOutline(size, layoutDirection, this)
        val path = clipPath ?: Path().also { clipPath = it }

        canvas.save()
        canvas.clipOutline(outline, path)

        onDrawBehind?.invoke(this)
        drawBackdropLayer()
        onDrawSurface?.invoke(this)
        drawContent()
        onDrawFront?.invoke(this)

        canvas.restore()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            if (backdrop.isCoordinatesDependent) {
                layoutCoordinates = coordinates
            } else {
                if (layoutCoordinates != null) {
                    layoutCoordinates = null
                }
            }
        }
    }

    override fun onObservedReadsChanged() {
        invalidateDrawCache()
    }

    fun invalidateDrawCache() {
        observeEffects()
    }

    private fun observeEffects() {
        observeReads { updateEffects() }
    }

    private fun updateEffects() {
        DrawBackdropDiagnostics.updateEffectsCallCount++
        effectScope.apply(effects)
        // On iOS/Skia, convert the ImageFilter to a Compose RenderEffect
        graphicsLayer?.renderEffect = effectScope.imageFilter?.asComposeRenderEffect()
        padding = effectScope.padding
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        graphicsLayer = graphicsContext.createGraphicsLayer()

        observeEffects()
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        graphicsLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            graphicsLayer = null
        }

        effectScope.reset()
        clipPath = null
    }
}
