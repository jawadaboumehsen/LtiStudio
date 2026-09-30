/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.layout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import java.awt.Cursor
import org.ide.lti.core.designsystem.theme.CornerRadius as ThemeCornerRadius

/**
 * Unified glass-styled drag divider component.
 *
 * Provides interactive resize functionality with glass morphism effects,
 * hover feedback, and proper cursor indicators.
 *
 * @param orientation Whether this is a vertical or horizontal divider.
 * @param appearance Visual appearance configuration from parent layout.
 * @param onDragStart Callback when drag starts.
 * @param onDrag Callback during drag with delta in pixels.
 * @param onDragEnd Callback when drag ends or is cancelled.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun GlassDragDivider(
    orientation: DividerOrientation,
    appearance: GlassLayoutAppearance,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    var isHovered by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    // Instant alpha during drag for 1:1 mouse tracking, smooth fade on hover
    val alpha by animateFloatAsState(
        targetValue = if (isHovered || isDragging) 1f else 0f,
        animationSpec = if (isDragging) snap() else spring(),
        label = "GlassDragDivider_Alpha",
    )

    val normalColor = if (appearance.dividerColor.isSpecified) {
        appearance.dividerColor
    } else if (GlassTheme.effectsEnabled) {
        MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Hover)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val activeColor = if (appearance.dividerActiveColor.isSpecified) {
        appearance.dividerActiveColor
    } else {
        MaterialTheme.colorScheme.primary
    }

    val (cursor, sizeModifier, lineModifier) = getDividerModifiers(orientation, appearance)

    Box(
        modifier = sizeModifier
            .pointerHoverIcon(cursor)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        onDragStart()
                    },
                    onDragEnd = {
                        isDragging = false
                        onDragEnd()
                    },
                    onDragCancel = {
                        isDragging = false
                        onDragEnd()
                    },
                ) { change, dragAmount ->
                    change.consume()
                    val delta = when (orientation) {
                        DividerOrientation.Vertical -> dragAmount.x
                        DividerOrientation.Horizontal -> dragAmount.y
                    }
                    onDrag(delta)
                }
            }
            .semantics {
                val stateDesc = if (isDragging) {
                    "dragging"
                } else if (isHovered) {
                    "hovered"
                } else {
                    "idle"
                }
                contentDescription = "Resize divider: $stateDesc"
            },
        contentAlignment = Alignment.Center,
    ) {
        // Single shared idle hairline divider between adjacent docked panels
        val idleLineModifier = when (orientation) {
            DividerOrientation.Vertical ->
                Modifier
                    .width(StrokeWidth.Hairline)
                    .fillMaxHeight()
                    .background(normalColor)
            DividerOrientation.Horizontal ->
                Modifier
                    .height(StrokeWidth.Hairline)
                    .fillMaxWidth()
                    .background(normalColor)
        }
        Box(modifier = idleLineModifier)

        // Divider line active state - animated glow during hover/drag
        Box(
            modifier = lineModifier
                .alpha(alpha)
                .clip(GlassShapes.Capsule)
                .then(
                    getDividerBackgroundModifier(
                        isDragging = isDragging,
                        isHovered = isHovered,
                        activeColor = activeColor,
                        normalColor = normalColor,
                        orientation = orientation,
                    ),
                ),
        )
    }
}

private fun getDividerModifiers(
    orientation: DividerOrientation,
    appearance: GlassLayoutAppearance,
): Triple<PointerIcon, Modifier, Modifier> = when (orientation) {
    DividerOrientation.Vertical -> Triple(
        PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)),
        Modifier.width(appearance.dividerWidth).fillMaxHeight(),
        Modifier
            .width(LtiLayoutDefaults.DividerLineWidthActive)
            .fillMaxHeight()
            .padding(vertical = appearance.dividerLinePadding),
    )
    DividerOrientation.Horizontal -> Triple(
        PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)),
        Modifier.height(appearance.dividerWidth).fillMaxWidth(),
        Modifier
            .height(LtiLayoutDefaults.DividerLineWidthActive)
            .fillMaxWidth()
            .padding(horizontal = appearance.dividerLinePadding),
    )
}

private fun getDividerBackgroundModifier(
    isDragging: Boolean,
    isHovered: Boolean,
    activeColor: Color,
    normalColor: Color,
    orientation: DividerOrientation,
): Modifier = when {
    isDragging -> Modifier.background(activeColor)
    isHovered -> {
        val gradientColors = listOf(
            activeColor.copy(alpha = AlphaTokens.Muted),
            activeColor.copy(alpha = AlphaTokens.Prominent),
            activeColor.copy(alpha = AlphaTokens.Muted),
        )
        val gradient = when (orientation) {
            DividerOrientation.Vertical -> Brush.verticalGradient(gradientColors)
            DividerOrientation.Horizontal -> Brush.horizontalGradient(gradientColors)
        }
        Modifier
            .drawBehind {
                drawRoundRect(
                    color = activeColor.copy(alpha = AlphaTokens.Glow),
                    cornerRadius = CornerRadius(ThemeCornerRadius.Pill.toPx()),
                    size = size.copy(
                        width = size.width + Spacing.Small.toPx(),
                        height = size.height + Spacing.Small.toPx(),
                    ),
                    topLeft = Offset(
                        x = -Spacing.ExtraSmall.toPx(),
                        y = -Spacing.ExtraSmall.toPx(),
                    ),
                )
            }
            .background(gradient)
    }
    else -> Modifier.background(normalColor)
}
