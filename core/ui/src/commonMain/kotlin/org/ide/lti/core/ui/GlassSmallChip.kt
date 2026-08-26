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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ide.lti.core.designsystem.theme.GlassTheme

/**
 * Modern Liquid Glass Action Chip / Tag with subtle hover feedback.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GlassSmallChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    var isHovered by remember { mutableStateOf(false) }
    val glassColors = GlassTheme.colors

    val backgroundColor by animateColorAsState(
        targetValue = if (isHovered) glassColors.glassSurfaceElevated else glassColors.glassSurface,
        animationSpec = tween(150)
    )

    val borderColor by animateColorAsState(
        targetValue = if (isHovered) glassColors.glassBorderHighlight else glassColors.glassBorder,
        animationSpec = tween(150)
    )

    val textColor by animateColorAsState(
        targetValue = if (isHovered) glassColors.onGlass else glassColors.onGlassSecondary,
        animationSpec = tween(150)
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(backgroundColor)
            .border(0.5.dp, borderColor, RoundedCornerShape(100.dp))
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.5.sp
            ),
            color = textColor
        )
    }
}