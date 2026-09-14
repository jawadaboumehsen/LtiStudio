/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.navigation

import org.ide.lti.core.designsystem.component.primitives.*
import org.ide.lti.core.designsystem.component.actions.*
import org.ide.lti.core.designsystem.component.inputs.*
import org.ide.lti.core.designsystem.component.display.*
import org.ide.lti.core.designsystem.component.navigation.*
import org.ide.lti.core.designsystem.component.layout.*
import org.ide.lti.core.designsystem.component.feedback.*
import org.ide.lti.core.designsystem.component.devtools.*

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.resolveAnimationSpec

/**
 * Standardized Liquid Glass Sidebar Navigation Item (e.g. for Setup, Drawer, Sidebar panels).
 *
 * Adheres to Apple / Linear design standards:
 * - Selected: Subtle frosted glass sheen, delicate specular hairline border, vibrant accent icon tint, bold label
 * - Hovered: Ultra-faint ambient highlight
 * - Normal: Clean transparent background with secondary muted typography
 *
 * @param label Human-readable navigation title.
 * @param icon The [AppIcon] to display.
 * @param selected Whether this destination is currently selected.
 * @param onClick Invoked when clicked.
 * @param modifier Optional modifier.
 * @param badge Optional trailing badge or counter string.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GlassSidebarItem(
    label: String,
    icon: AppIcon,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    val glassColors = GlassTheme.colors
    var isHovered by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    val targetBackground = when {
        selected -> glassColors.glassAccent.copy(alpha = AlphaTokens.Glow)
        isHovered -> glassColors.onGlass.copy(alpha = AlphaTokens.UltraFaint)
        else -> Color.Transparent
    }
    val targetBorderColor = when {
        selected -> glassColors.glassAccent.copy(alpha = AlphaTokens.Faded)
        isFocused -> glassColors.focusRing
        isHovered -> glassColors.glassBorder.copy(alpha = AlphaTokens.Glow)
        else -> Color.Transparent
    }

    val backgroundColor by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = resolveAnimationSpec(GlassTheme.reducedMotion),
    )
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = resolveAnimationSpec(GlassTheme.reducedMotion),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.Medium)
            .background(backgroundColor)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = borderColor,
                shape = GlassShapes.Medium,
            )
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
        ) {
            val iconTint by animateColorAsState(
                targetValue = when {
                    selected -> glassColors.glassAccent
                    isHovered -> glassColors.onGlass
                    else -> glassColors.onGlassSecondary
                },
                animationSpec = resolveAnimationSpec(GlassTheme.reducedMotion),
            )
            when (icon) {
                is AppIcon.Vector -> Icon(
                    imageVector = icon.imageVector,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(IconSize.Medium),
                )
                is AppIcon.Painted -> Icon(
                    painter = icon.painter(),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(IconSize.Medium),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) glassColors.onGlass else if (isHovered) glassColors.onGlass else glassColors.onGlassSecondary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) glassColors.onGlass else glassColors.onGlassMuted,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Convenience overload accepting an [ImageVector] icon directly.
 */
@Composable
fun GlassSidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) = GlassSidebarItem(
    label = label,
    icon = AppIcon.Vector(icon),
    selected = selected,
    onClick = onClick,
    modifier = modifier,
    badge = badge,
)
