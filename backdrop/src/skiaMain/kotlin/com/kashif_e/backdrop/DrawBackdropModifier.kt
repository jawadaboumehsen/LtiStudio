package com.kashif_e.backdrop

import androidx.compose.foundation.interaction.InteractionSource
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
import com.kashif_e.backdrop.interaction.BackdropInteractionController
import com.kashif_e.backdrop.interaction.BackdropInteractionRenderState
import com.kashif_e.backdrop.interaction.BackdropInteractionResponse
import com.kashif_e.backdrop.shadow.InnerShadow
import com.kashif_e.backdrop.shadow.InnerShadowElement
import com.kashif_e.backdrop.shadow.Shadow
import com.kashif_e.backdrop.shadow.ShadowElement
import kotlin.math.ceil
import kotlin.math.roundToInt

private val DefaultOnDrawBackdrop: DrawScope.(DrawScope.() -> Unit) -> Unit = { it() }

/**
 * Diagnostic counter for how often [DrawBackdropNode] recomputes its effect graph
 * (`effectScope.applyResolved(resolvedEffect)` + reassigning `renderEffect`) - the expensive part
 * of a glass element's re-render, distinct from the cheap per-frame draw.
 */
internal object DrawBackdropDiagnostics {
    var updateEffectsCallCount: Int = 0
}

/**
 * Apply a plain backdrop effect without shadow/highlight decorations.
 */
fun Modifier.drawPlainBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    resolvedEffect: ResolvedGlassEffect = ResolvedGlassEffect.None,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    onDrawBehind: (DrawScope.() -> Unit)? = null,
    onDrawBackdrop: (DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit)? = null,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    onDrawFront: (DrawScope.() -> Unit)? = null,
    interaction: BackdropInteractionResponse? = null,
    interactionSource: InteractionSource? = null
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
                resolvedEffect = resolvedEffect,
                layerBlock = layerBlock,
                onDrawBehind = onDrawBehind,
                onDrawBackdrop = actualOnDrawBackdrop,
                onDrawSurface = onDrawSurface,
                onDrawFront = onDrawFront,
                inputScale = 1f,
                interaction = interaction,
                interactionSource = interactionSource
            )
        )
}

private val DefaultHighlight = { Highlight.Default }
private val DefaultShadow = { Shadow.Default }

/**
 * Draw a backdrop with effects, shadows, and highlights.
 *
 * This is the main entry point for creating liquid glass and frosted glass effects.
 *
 * @param backdrop The backdrop content to draw (use [CanvasBackdrop] for custom drawing).
 * @param shape The shape of the backdrop area.
 * @param resolvedEffect The effect stack to apply (blur, color controls, lens, grain, ...) as a
 * plain, structurally-comparable value - see [ResolvedGlassEffect].
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
 * element. `1f` (the default) records at full resolution. Values below `1f` cut blur cost roughly
 * with the square of the scale (e.g. `0.5f` blurs a quarter of the pixels) at a cost invisible
 * once blurred; ported from Haze's `GlassInputScalePolicy`.
 * @param interaction Optional declared hover/press/focus optics response - see
 * [BackdropInteractionResponse]. Animated by a node-owned [BackdropInteractionController] that
 * invalidates draw only, never the (expensive) Skia effect-graph rebuild path composition-driven
 * [resolvedEffect] changes go through.
 * @param interactionSource The [InteractionSource] whose press/hover/focus events drive
 * [interaction]. Typically the same source passed to `Modifier.clickable`/`hoverable`.
 */
fun Modifier.drawBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    resolvedEffect: ResolvedGlassEffect = ResolvedGlassEffect.None,
    highlight: (() -> Highlight?)? = null,
    shadow: (() -> Shadow?)? = null,
    innerShadow: (() -> InnerShadow?)? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    onDrawBehind: (DrawScope.() -> Unit)? = null,
    onDrawBackdrop: (DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit)? = null,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    onDrawFront: (DrawScope.() -> Unit)? = null,
    inputScale: Float = 1f,
    interaction: BackdropInteractionResponse? = null,
    interactionSource: InteractionSource? = null
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
                resolvedEffect = resolvedEffect,
                layerBlock = layerBlock,
                onDrawBehind = onDrawBehind,
                onDrawBackdrop = actualOnDrawBackdrop,
                onDrawSurface = onDrawSurface,
                onDrawFront = onDrawFront,
                inputScale = inputScale,
                interaction = interaction,
                interactionSource = interactionSource
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
    val resolvedEffect: ResolvedGlassEffect,
    val layerBlock: (GraphicsLayerScope.() -> Unit)?,
    val onDrawBehind: (DrawScope.() -> Unit)?,
    val onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit,
    val onDrawSurface: (DrawScope.() -> Unit)?,
    val onDrawFront: (DrawScope.() -> Unit)?,
    val inputScale: Float,
    val interaction: BackdropInteractionResponse?,
    val interactionSource: InteractionSource?
) : ModifierNodeElement<DrawBackdropNode>() {

    override fun create(): DrawBackdropNode {
        return DrawBackdropNode(
            backdrop = backdrop,
            shapeProvider = shapeProvider,
            resolvedEffect = resolvedEffect,
            layerBlock = layerBlock,
            onDrawBehind = onDrawBehind,
            onDrawBackdrop = onDrawBackdrop,
            onDrawSurface = onDrawSurface,
            onDrawFront = onDrawFront,
            inputScale = inputScale,
            interaction = interaction,
            interactionSource = interactionSource
        )
    }

    override fun update(node: DrawBackdropNode) {
        // ResolvedGlassEffect is a plain data class (structural equality), so this diff is a
        // real per-field comparison, not a reference check - a fresh ResolvedGlassEffect value
        // describing the same style no longer forces a recompute, and no caller needs to
        // remember() it for that to hold. Only invalidate the (expensive) Skia effect graph when
        // a bit that actually renders differently changed - see GlassEffectDirtyFields.
        val dirty = GlassEffectDirtyFields.diff(node.resolvedEffect, resolvedEffect)
        node.backdrop = backdrop
        node.shapeProvider = shapeProvider
        node.resolvedEffect = resolvedEffect
        node.layerBlock = layerBlock
        node.onDrawBehind = onDrawBehind
        node.onDrawBackdrop = onDrawBackdrop
        node.onDrawSurface = onDrawSurface
        node.onDrawFront = onDrawFront
        node.inputScale = inputScale
        node.interaction = interaction
        node.interactionSource = interactionSource
        node.updateInteractionController()
        if (dirty.any(GlassEffectDirtyFields.RenderEffectAffectingFlags)) {
            node.invalidateDrawCache()
        }
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "drawBackdrop"
        properties["backdrop"] = backdrop
        properties["shapeProvider"] = shapeProvider
        properties["resolvedEffect"] = resolvedEffect
        properties["layerBlock"] = layerBlock
        properties["onDrawBehind"] = onDrawBehind
        properties["onDrawBackdrop"] = onDrawBackdrop
        properties["onDrawSurface"] = onDrawSurface
        properties["onDrawFront"] = onDrawFront
        properties["inputScale"] = inputScale
        properties["interaction"] = interaction
        properties["interactionSource"] = interactionSource
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DrawBackdropElement) return false

        if (backdrop != other.backdrop) return false
        if (shapeProvider != other.shapeProvider) return false
        if (resolvedEffect != other.resolvedEffect) return false
        if (layerBlock != other.layerBlock) return false
        if (onDrawBehind != other.onDrawBehind) return false
        if (onDrawBackdrop != other.onDrawBackdrop) return false
        if (onDrawSurface != other.onDrawSurface) return false
        if (onDrawFront != other.onDrawFront) return false
        if (inputScale != other.inputScale) return false
        if (interaction != other.interaction) return false
        if (interactionSource != other.interactionSource) return false

        return true
    }

    override fun hashCode(): Int {
        var result = backdrop.hashCode()
        result = 31 * result + shapeProvider.hashCode()
        result = 31 * result + resolvedEffect.hashCode()
        result = 31 * result + (layerBlock?.hashCode() ?: 0)
        result = 31 * result + (onDrawBehind?.hashCode() ?: 0)
        result = 31 * result + onDrawBackdrop.hashCode()
        result = 31 * result + (onDrawSurface?.hashCode() ?: 0)
        result = 31 * result + (onDrawFront?.hashCode() ?: 0)
        result = 31 * result + inputScale.hashCode()
        result = 31 * result + (interaction?.hashCode() ?: 0)
        result = 31 * result + (interactionSource?.hashCode() ?: 0)
        return result
    }
}

private class DrawBackdropNode(
    var backdrop: Backdrop,
    var shapeProvider: ShapeProvider,
    var resolvedEffect: ResolvedGlassEffect,
    var layerBlock: (GraphicsLayerScope.() -> Unit)?,
    var onDrawBehind: (DrawScope.() -> Unit)?,
    var onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit,
    var onDrawSurface: (DrawScope.() -> Unit)?,
    var onDrawFront: (DrawScope.() -> Unit)?,
    var inputScale: Float,
    var interaction: BackdropInteractionResponse?,
    var interactionSource: InteractionSource?
) : LayoutModifierNode, DrawModifierNode, GlobalPositionAwareModifierNode, ObserverModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private val effectScope = object : BackdropEffectScopeImpl() {
        override val shape: Shape get() = shapeProvider.innerShape
    }

    private var graphicsLayer: GraphicsLayer? = null

    // Node-owned, attached in onAttach()/disposed in onDetach() - its invalidateDraw() calls
    // never touch GlassEffectDirtyFields, keeping hover/press animation fully decoupled from the
    // composition-driven resolvedEffect diff above.
    private var interactionController: BackdropInteractionController? = null
    private var lastAppliedInteractionState: BackdropInteractionRenderState? = null

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
        val interactionState = interactionController?.renderState
        val sizeOrDensityChanged = effectScope.update(this)
        val interactionStateChanged = interactionState != null && interactionState != lastAppliedInteractionState
        if (sizeOrDensityChanged || interactionStateChanged) {
            updateEffects()
        }

        val canvas = drawContext.canvas
        val outline = shapeProvider.shape.createOutline(size, layoutDirection, this)
        val path = clipPath ?: Path().also { clipPath = it }
        val scale = interactionState?.scale ?: 1f

        canvas.save()
        if (scale != 1f) {
            canvas.translate(size.width / 2f, size.height / 2f)
            canvas.scale(scale, scale)
            canvas.translate(-size.width / 2f, -size.height / 2f)
        }
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
        val interactionState = interactionController?.renderState ?: BackdropInteractionRenderState()
        effectScope.applyResolved(resolvedEffect, interactionState)
        lastAppliedInteractionState = interactionState
        // On iOS/Skia, convert the ImageFilter to a Compose RenderEffect
        graphicsLayer?.renderEffect = effectScope.imageFilter?.asComposeRenderEffect()
        padding = effectScope.padding
    }

    /** Pushes the current [interaction]/[interactionSource] into [interactionController], if attached. */
    fun updateInteractionController() {
        val controller = interactionController ?: return
        controller.updateResponse(interaction ?: BackdropInteractionResponse())
        controller.updateInteractionSource(interactionSource)
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        graphicsLayer = graphicsContext.createGraphicsLayer()
        interactionController = BackdropInteractionController(
            scope = coroutineScope,
            invalidateDraw = { invalidateDraw() },
        )
        updateInteractionController()

        observeEffects()
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        graphicsLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            graphicsLayer = null
        }

        interactionController?.dispose()
        interactionController = null
        lastAppliedInteractionState = null
        effectScope.reset()
        clipPath = null
    }
}
