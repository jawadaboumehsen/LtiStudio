/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.primitives

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.BackdropEffectScope
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.GlassEffects
import org.ide.lti.core.designsystem.theme.GlassHighlights
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LocalGlassColors
import org.ide.lti.core.designsystem.theme.StrokeWidth

/**
 * Applies glass morphism effect to any composable.
 *
 * Convenience modifier that wraps [com.kashif_e.backdrop.drawBackdrop] with the
 * standard liquid glass effects, shape, and colors.
 *
 * @param backdrop The backdrop instance to sample from (defaults to [GlassTheme.backdrop])
 * @param shape The shape to clip the glass to (defaults to [GlassShapes.Medium])
 * @param surfaceColor Optional custom surface color (defaults to [GlassColorScheme.glassSurface])
 * @param tint Optional tint color applied over the backdrop (defaults to [GlassColorScheme.glassTint])
 * @param effects Custom backdrop effect block (defaults to [GlassEffects.Standard])
 * @param highlight Custom highlight style (defaults to [GlassHighlights.Plain])
 *
 * Example:
 * ```kotlin
 * Box(
 *     Modifier
 *         .size(200.dp)
 *         .glassEffect()
 * ) {
 *     Text("Liquid Glass")
 * }
 * ```
 */
fun Modifier.glassEffect(
    backdrop: Backdrop? = null,
    shape: Shape = GlassShapes.Medium,
    surfaceColor: Color = Color.Unspecified,
    tint: Color = Color.Unspecified,
    effects: BackdropEffectScope.() -> Unit = GlassEffects.Standard,
    highlight: () -> Highlight? = GlassHighlights.Plain,
): Modifier = composed {
    val glassColors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    val actualBackdrop = backdrop ?: GlassTheme.backdrop

    val finalSurfaceColor = if (surfaceColor.isSpecified) surfaceColor else glassColors.glassSurface
    val finalTint = if (tint.isSpecified) tint else glassColors.glassTint

    if (effectsEnabled && actualBackdrop != null) {
        this.drawBackdrop(
            backdrop = actualBackdrop,
            shape = { shape },
            effects = effects,
            highlight = highlight,
            onDrawSurface = {
                drawRect(finalSurfaceColor.copy(alpha = finalSurfaceColor.alpha * AlphaTokens.ButtonFill))

                if (finalTint.isSpecified && finalTint.alpha > 0f) {
                    drawRect(finalTint, blendMode = BlendMode.SrcOver)
                }
            },
        )
    } else {
        this
            .clip(shape)
            .drawBehind {
                drawRect(finalSurfaceColor.copy(alpha = finalSurfaceColor.alpha * AlphaTokens.ButtonFill))

                if (finalTint.isSpecified && finalTint.alpha > 0f) {
                    drawRect(finalTint, blendMode = BlendMode.SrcOver)
                }
            }
    }
}

/**
 * Applies a simple glass background color without full effects.
 *
 * Useful for performance-critical scenarios where you want the glass aesthetic
 * but don't need the full effect stack.
 *
 * @param color Background color (default: from GlassTheme)
 *
 * Example:
 * ```kotlin
 * Box(
 *     Modifier
 *         .size(100.dp)
 *         .glassBackground()
 * ) {
 *     Text("Simple Glass")
 * }
 * ```
 */
fun Modifier.glassBackground(
    color: Color = Color.Unspecified,
): Modifier = this then GlassBackgroundElement(color)

private data class GlassBackgroundElement(
    val color: Color,
) : ModifierNodeElement<GlassBackgroundNode>() {
    override fun create(): GlassBackgroundNode = GlassBackgroundNode(color)

    override fun update(node: GlassBackgroundNode) {
        node.color = color
    }
}

private class GlassBackgroundNode(
    var color: Color,
) : Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    DrawModifierNode {

    override fun ContentDrawScope.draw() {
        val finalColor = if (color.isSpecified) {
            color
        } else {
            currentValueOf(LocalGlassColors).glassSurface
        }
        drawRect(finalColor)
        drawContent()
    }
}

/**
 * Draws a [width]-thick [color] border along [shape], INSET so the stroke lies entirely
 * inside the shape.
 *
 * Replaces `Modifier.border(...)` for this design system. Compose Foundation's `border()`
 * takes a separate, imprecise codepath for `Outline.Generic` shapes — which is every
 * `ContinuousCapsule` / `ContinuousRoundedRectangle` in this app: it rasterizes the
 * stroke into an offscreen bitmap, clears the outer half through a mask deliberately
 * overscaled by `(size + 1) / size` (see Border.kt's own comment: "Scale the canvas
 * slightly to cover the background that may be visible after clearing the outer stroke"),
 * and composites that bitmap back. That is a second, independent anti-aliasing pass
 * stacked on the glass surface's own anti-aliased clip edge, misaligned with it by
 * roughly half a pixel — visible as rough, doubled, "zigzag" edges on curves.
 *
 * This draws a single inset stroke in the same draw pass, with the same canvas and the
 * same anti-aliasing as everything else. The outer edge of the stroke sits halfStroke
 * INSIDE the glass clip boundary, so it never competes with it.
 *
 * The outline is built at the inset size (`shape.createOutline(innerSize, ...)`) and
 * translated by halfStroke. Do NOT try to shrink the shape's corner sizes via
 * `CornerBasedShape.copy(...)`: `ContinuousCapsule` overrides `createOutline` to call
 * `continuity.createCapsuleOutline(size)` and ignores its corner sizes entirely, and its
 * `copy()` returns a base `ContinuousRoundedRectangle` that loses that override. Insetting
 * by size is exact for capsules and leaves at most a halfStroke corner-fullness residue
 * (erring inward, never outward) for fixed-radius rounded rectangles.
 */
fun Modifier.glassOutlineBorder(
    color: Color,
    width: Dp,
    shape: Shape,
): Modifier = this.drawWithCache {
    val strokePx = (if (width == Dp.Hairline) 1f else width.toPx())
        .coerceAtMost(size.minDimension / 2f)
    val innerWidth = size.width - strokePx
    val innerHeight = size.height - strokePx
    val hasZeroArea = innerWidth <= 0f || innerHeight <= 0f
    val isInvisible = strokePx <= 0f || color.alpha == 0f

    if (isInvisible || hasZeroArea) {
        onDrawWithContent { drawContent() }
    } else {
        val halfStroke = strokePx / 2f
        val outline = shape.createOutline(
            Size(innerWidth, innerHeight),
            layoutDirection,
            this,
        )
        val stroke = Stroke(width = strokePx)
        onDrawWithContent {
            drawContent()
            translate(left = halfStroke, top = halfStroke) {
                drawOutline(outline = outline, color = color, style = stroke)
            }
        }
    }
}

/**
 * Applies a glass border to a composable.
 *
 * @param color Border color (default: from GlassTheme)
 * @param width Border width (default: 1.dp)
 * @param shape Shape of the border (default: [ContinuousRoundedRectangle] with 16dp radius)
 *
 * Example:
 * ```kotlin
 * Box(
 *     Modifier
 *         .size(100.dp)
 *         .glassBackground()
 *         .glassBorder()
 * ) {
 *     Text("Glass with Border")
 * }
 * ```
 */
fun Modifier.glassBorder(
    color: Color? = null,
    width: Dp = StrokeWidth.Standard,
    shape: Shape = GlassShapes.Medium,
): Modifier = if (color != null) {
    this.glassOutlineBorder(color = color, width = width, shape = shape)
} else {
    this then GlassBorderElement(width, shape)
}

private data class GlassBorderElement(
    val width: Dp,
    val shape: Shape,
) : ModifierNodeElement<GlassBorderNode>() {
    override fun create(): GlassBorderNode = GlassBorderNode(width, shape)

    override fun update(node: GlassBorderNode) {
        node.width = width
        node.shape = shape
    }
}

private class GlassBorderNode(
    var width: Dp,
    var shape: Shape,
) : Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    DrawModifierNode {

    override fun ContentDrawScope.draw() {
        drawContent()
        val finalColor = currentValueOf(LocalGlassColors).glassBorder
        val strokePx = (if (width == Dp.Hairline) 1f else width.toPx())
            .coerceAtMost(size.minDimension / 2f)
        val innerWidth = size.width - strokePx
        val innerHeight = size.height - strokePx
        if (strokePx > 0f && finalColor.alpha > 0f && innerWidth > 0f && innerHeight > 0f) {
            val halfStroke = strokePx / 2f
            val outline = shape.createOutline(
                Size(innerWidth, innerHeight),
                layoutDirection,
                this,
            )
            val stroke = Stroke(width = strokePx)
            translate(left = halfStroke, top = halfStroke) {
                drawOutline(outline = outline, color = finalColor, style = stroke)
            }
        }
    }
}

/**
 * Makes a composable clickable with glass-aware styling.
 *
 * Combines clickable behavior with automatic glass color application
 * for content.
 *
 * @param onClick Callback when clicked
 * @param enabled Whether the element is enabled (default: true)
 * @param role Semantic role for accessibility (default: Button)
 *
 * Example:
 * ```kotlin
 * Box(
 *     Modifier
 *         .glassEffect()
 *         .glassInteractive(onClick = { })
 * ) {
 *     Text("Clickable Glass")
 * }
 * ```
 */
fun Modifier.glassInteractive(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = Role.Button,
): Modifier {
    return this.clickable(
        enabled = enabled,
        onClick = onClick,
        role = role,
    )
}

/**
 * Applies a subtle glass effect optimized for overlays and dialogs.
 *
 * Uses enhanced blur and reduced refraction for scrim-like surfaces.
 *
 * @param backdrop Backdrop instance (default: from GlassTheme or create new)
 * @param blurRadius Custom blur radius (default: 16.dp)
 * @param surfaceColor Surface color (default: semi-transparent black)
 *
 * Example:
 * ```kotlin
 * Box(
 *     Modifier
 *         .fillMaxSize()
 *         .glassScrim()
 * ) {
 *     // Dialog content
 * }
 * ```
 */
fun Modifier.glassScrim(
    backdrop: Backdrop? = null,
    blurRadius: Dp = BackdropBlur.Scrim,
    surfaceColor: Color = Color.Unspecified,
): Modifier = composed {
    val actualBackdrop = backdrop ?: GlassTheme.backdrop ?: rememberLayerBackdrop()
    val effectsEnabled = GlassTheme.effectsEnabled
    val resolvedSurfaceColor = if (surfaceColor.isSpecified) {
        surfaceColor
    } else {
        GlassTheme.colors.glassShadow.copy(alpha = AlphaTokens.Scrim)
    }

    if (effectsEnabled) {
        this.drawBackdrop(
            backdrop = actualBackdrop,
            shape = { androidx.compose.ui.graphics.RectangleShape },
            effects = if (blurRadius == BackdropBlur.Scrim) {
                GlassEffects.Scrim
            } else {
                {
                    vibrancy()
                    blur(blurRadius.toPx())
                }
            },
            highlight = GlassHighlights.Plain,
            onDrawSurface = {
                drawRect(resolvedSurfaceColor)
            },
        )
    } else {
        this.drawBehind {
            drawRect(resolvedSurfaceColor)
        }
    }
}
