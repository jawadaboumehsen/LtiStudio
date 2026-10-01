/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.inputs

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter

/**
 * Specification for an inner shadow rendered inside a component shape outline.
 */
@Immutable
data class InnerShadow(
    val radius: Dp = org.ide.lti.core.designsystem.theme.Spacing.ExtraSmall,
    val offset: DpOffset =
        DpOffset(
            org.ide.lti.core.designsystem.theme.Spacing.None,
            org.ide.lti.core.designsystem.theme.Spacing.ExtraSmall,
        ),
    val color: Color = org.ide.lti.core.designsystem.theme.GlassControlColors.InnerShadow,
    val alpha: Float = 1f,
    val blendMode: BlendMode = DrawScope.DefaultBlendMode,
)

internal class InnerShadowElement(val shape: Shape, val shadow: () -> InnerShadow?) :
    ModifierNodeElement<InnerShadowNode>() {
    override fun create(): InnerShadowNode = InnerShadowNode(shape, shadow)

    override fun update(node: InnerShadowNode) {
        node.shape = shape
        node.shadow = shadow
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "innerShadow"
        properties["shape"] = shape
        properties["shadow"] = shadow
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InnerShadowElement) return false
        return shape == other.shape && shadow == other.shadow
    }

    override fun hashCode(): Int = 31 * shape.hashCode() + shadow.hashCode()
}

internal class InnerShadowNode(var shape: Shape, var shadow: () -> InnerShadow?) :
    Modifier.Node(),
    DrawModifierNode {
    override val shouldAutoInvalidate: Boolean = false

    private var shadowLayer: GraphicsLayer? = null
    private val paint = Paint()
    private val maskPaint = Paint().apply {
        blendMode = BlendMode.Clear
    }
    private var clipPath: Path? = null
    private var prevRadius = Float.NaN
    private var currentImageFilter: ImageFilter? = null

    override fun ContentDrawScope.draw() {
        drawContent()

        val shadowValue = shadow()
        val layer = shadowLayer
        if (shadowValue == null ||
            layer == null ||
            shadowValue.alpha <= 0.001f ||
            shadowValue.radius <= org.ide.lti.core.designsystem.theme.Spacing.None
        ) {
            return
        }
        val density: Density = this
        val layoutDir = layoutDirection

        val radius = shadowValue.radius.toPx()
        val offsetX = shadowValue.offset.x.toPx()
        val offsetY = shadowValue.offset.y.toPx()

        val outline = shape.createOutline(size, layoutDir, density)
        val path = if (outline is Outline.Rounded) {
            clipPath ?: Path().also { clipPath = it }
        } else {
            null
        }

        paint.color = shadowValue.color

        if (prevRadius != radius) {
            currentImageFilter = if (radius > 0f) {
                val sigma = radius / 2f
                ImageFilter.makeBlur(sigma, sigma, FilterTileMode.DECAL)
            } else {
                null
            }
            prevRadius = radius
        }

        layer.alpha = shadowValue.alpha
        layer.blendMode = shadowValue.blendMode
        layer.renderEffect = currentImageFilter?.asComposeRenderEffect()

        layer.record {
            val canvas = drawContext.canvas
            canvas.save()
            canvas.clipOutline(outline, path)

            drawOutline(outline, paint, path)

            canvas.translate(offsetX, offsetY)
            drawOutline(outline, maskPaint, path)
            canvas.translate(-offsetX, -offsetY)

            canvas.restore()
        }

        val canvas = drawContext.canvas
        canvas.save()
        canvas.clipOutline(outline, path)
        drawLayer(layer)
        canvas.restore()
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        shadowLayer = graphicsContext.createGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
        }
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        shadowLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            shadowLayer = null
        }
        currentImageFilter = null
    }

    private fun DrawScope.drawOutline(outline: Outline, p: Paint, path: Path?) {
        when (outline) {
            is Outline.Rectangle -> drawContext.canvas.drawRect(outline.rect, p)
            is Outline.Rounded -> {
                val pth = path ?: Path()
                pth.reset()
                pth.addRoundRect(outline.roundRect)
                drawContext.canvas.drawPath(pth, p)
            }
            is Outline.Generic -> drawContext.canvas.drawPath(outline.path, p)
        }
    }

    private fun Canvas.clipOutline(outline: Outline, path: Path?) {
        when (outline) {
            is Outline.Rectangle -> clipRect(outline.rect)
            is Outline.Rounded -> {
                val pth = path ?: Path()
                pth.reset()
                pth.addRoundRect(outline.roundRect)
                clipPath(pth)
            }
            is Outline.Generic -> clipPath(outline.path)
        }
    }
}

/**
 * Modifier that renders an inner shadow clipped to [shape].
 */
fun Modifier.innerShadow(shape: Shape, shadow: () -> InnerShadow?): Modifier =
    this.then(InnerShadowElement(shape, shadow))
