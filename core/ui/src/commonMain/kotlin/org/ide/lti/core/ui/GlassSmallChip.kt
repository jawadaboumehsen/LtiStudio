/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LensRefraction
import org.ide.lti.core.designsystem.theme.MotionDuration
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

/**
 * Modern Liquid Glass Action Chip / Tag with subtle hover feedback and backdrop effects.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GlassSmallChip(
    label: String,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    onClick: () -> Unit = {},
) {
    var isHovered by remember { mutableStateOf(false) }
    val glassColors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    val actualBackdrop = backdrop ?: GlassTheme.backdrop
    val chipShape = RoundedCornerShape(CornerRadius.Pill)

    val backgroundColor by animateColorAsState(
        targetValue = if (isHovered) glassColors.glassSurfaceElevated else glassColors.glassSurface,
        animationSpec = tween(MotionDuration.Fast)
    )

    val borderColor by animateColorAsState(
        targetValue = if (isHovered) glassColors.glassBorderHighlight else glassColors.glassBorder,
        animationSpec = tween(MotionDuration.Fast)
    )

    val textColor by animateColorAsState(
        targetValue = if (isHovered) glassColors.onGlass else glassColors.onGlassSecondary,
        animationSpec = tween(MotionDuration.Fast)
    )

    Box(
        modifier = modifier
            .clip(chipShape)
            .then(
                if (effectsEnabled && actualBackdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = actualBackdrop,
                        shape = { chipShape },
                        effects = {
                            vibrancy()
                            blur(BackdropBlur.Subtle.toPx())
                            lens(LensRefraction.Button.height.toPx(), LensRefraction.Button.amount.toPx())
                        },
                        highlight = { if (isHovered) Highlight.Ambient else Highlight.Plain },
                        onDrawSurface = {
                            drawRect(backgroundColor)
                        }
                    )
                } else {
                    Modifier.background(backgroundColor)
                }
            )
            .border(StrokeWidth.Hairline, borderColor, chipShape)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Compact),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.5.sp,
            ),
            color = textColor,
        )
    }
}
