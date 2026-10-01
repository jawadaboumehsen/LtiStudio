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

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp

// No Modifier.glass() facade here - Haze doesn't have one either. Call
// `Modifier.hazeGlass(input = HazeInput.Backdrop(GlassTheme.hazeState), style = GlassStyle.regular.then { ... })`
// (dev.chrisbanes.haze.glass) directly at each call site.

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
fun Modifier.glassOutlineBorder(color: Color, width: Dp, shape: Shape): Modifier = this.drawWithCache {
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
