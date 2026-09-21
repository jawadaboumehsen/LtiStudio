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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.kashif_e.backdrop.BackdropEffectScope
import com.kashif_e.backdrop.drawBackdrop
import org.ide.lti.core.designsystem.component.actions.*
import org.ide.lti.core.designsystem.component.devtools.*
import org.ide.lti.core.designsystem.component.display.*
import org.ide.lti.core.designsystem.component.feedback.*
import org.ide.lti.core.designsystem.component.inputs.*
import org.ide.lti.core.designsystem.component.navigation.*
import org.ide.lti.core.designsystem.component.primitives.*
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.glass.GlassSceneState
import org.ide.lti.core.designsystem.theme.glass.GlassStyles
import org.ide.lti.core.designsystem.theme.glass.rememberGlassSceneState
import org.ide.lti.core.designsystem.theme.glass.toEffects
import org.ide.lti.core.designsystem.theme.resolveAnimationSpec
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
 * @param backdrop Shared backdrop for glass effects.
 * @param onDragEnd Callback when drag ends or is cancelled.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun GlassDragDivider(
    orientation: DividerOrientation,
    appearance: GlassLayoutAppearance,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    backdrop: GlassSceneState? = null,
    onDragEnd: () -> Unit,
) {
    var isHovered by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    val splitterGripStyle = GlassStyles.splitterGrip
    val splitterGripEffects = remember(splitterGripStyle) { splitterGripStyle.toEffects() }

    // Instant alpha during drag for 1:1 mouse tracking, smooth fade on hover
    val alpha by animateFloatAsState(
        targetValue = if (isHovered || isDragging) 1f else 0f,
        animationSpec = if (isDragging) {
            snap()
        } else {
            resolveAnimationSpec(GlassTheme.reducedMotion)
        },
    )

    // Get backdrop from theme if not explicitly provided
    val actualBackdrop = backdrop ?: GlassTheme.scene ?: rememberGlassSceneState()

    // Capture colors from theme
    val colors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    val normalColor = if (appearance.dividerColor.isSpecified) {
        appearance.dividerColor
    } else {
        colors.glassBorder.copy(alpha = AlphaTokens.Hover)
    }
    val activeColor = if (appearance.dividerActiveColor.isSpecified) {
        appearance.dividerActiveColor
    } else {
        colors.glassAccent
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
                val stateDesc = if (isDragging) "dragging" else if (isHovered) "hovered" else "idle"
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
                        actualBackdrop = actualBackdrop,
                        orientation = orientation,
                        effects = splitterGripEffects,
                        effectsEnabled = effectsEnabled,
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
    actualBackdrop: GlassSceneState,
    orientation: DividerOrientation,
    effects: BackdropEffectScope.() -> Unit,
    effectsEnabled: Boolean = true,
): Modifier = when {
    isDragging -> Modifier.background(activeColor)
    isHovered -> if (effectsEnabled) {
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
            .drawBackdrop(
                backdrop = actualBackdrop.engineBackdrop,
                shape = { GlassShapes.Capsule },
                effects = effects,
                onDrawSurface = {
                    val gradient = when (orientation) {
                        DividerOrientation.Vertical -> Brush.verticalGradient(
                            colors = listOf(
                                activeColor.copy(alpha = AlphaTokens.Muted),
                                activeColor.copy(alpha = AlphaTokens.Prominent),
                                activeColor.copy(alpha = AlphaTokens.Muted),
                            ),
                        )
                        DividerOrientation.Horizontal -> Brush.horizontalGradient(
                            colors = listOf(
                                activeColor.copy(alpha = AlphaTokens.Muted),
                                activeColor.copy(alpha = AlphaTokens.Prominent),
                                activeColor.copy(alpha = AlphaTokens.Muted),
                            ),
                        )
                    }
                    drawRect(gradient)
                },
            )
    } else {
        Modifier.background(activeColor)
    }
    else -> Modifier.background(normalColor)
}
