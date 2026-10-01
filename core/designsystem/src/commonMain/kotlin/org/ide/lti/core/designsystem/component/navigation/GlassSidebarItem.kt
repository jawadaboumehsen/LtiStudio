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

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
@Composable
fun GlassSidebarItem(
    label: String,
    icon: AppIcon,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val colors = MaterialTheme.colorScheme
    val effectsEnabled = GlassTheme.effectsEnabled
    val targetBackground = sidebarBackground(selected, isHovered, colors, effectsEnabled)
    val targetBorderColor = sidebarBorderColor(selected, isFocused, isHovered, colors, effectsEnabled)

    val backgroundColor by animateColorAsState(
        targetValue = targetBackground,
        label = "GlassSidebarItem_Background",
    )
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        label = "GlassSidebarItem_Border",
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
            .clickable(
                interactionSource = interactionSource,
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
                targetValue = sidebarIconTint(selected, isHovered, colors),
                label = "GlassSidebarItem_IconTint",
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
                color = when {
                    selected || isHovered -> colors.onSurface
                    else -> colors.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        colors.onSurface
                    } else {
                        colors.onSurfaceVariant
                    },
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Resolves the background color for a sidebar item.
 */
private fun sidebarBackground(
    selected: Boolean,
    isHovered: Boolean,
    colors: ColorScheme,
    effectsEnabled: Boolean,
): Color = when {
    selected && !effectsEnabled -> colors.primaryContainer
    selected -> colors.primary.copy(alpha = AlphaTokens.Glow)
    isHovered -> colors.onSurface.copy(alpha = AlphaTokens.UltraFaint)
    else -> Color.Transparent
}

/**
 * Resolves the outline border color for a sidebar item.
 */
private fun sidebarBorderColor(
    selected: Boolean,
    isFocused: Boolean,
    isHovered: Boolean,
    colors: ColorScheme,
    effectsEnabled: Boolean,
): Color = when {
    selected && !effectsEnabled -> colors.primary
    selected -> colors.primary.copy(alpha = AlphaTokens.Faded)
    isFocused -> colors.primary
    isHovered -> colors.outline.copy(alpha = AlphaTokens.Glow)
    else -> Color.Transparent
}

/**
 * Resolves the icon tint color for a sidebar item.
 */
private fun sidebarIconTint(selected: Boolean, isHovered: Boolean, colors: ColorScheme): Color = when {
    selected -> colors.primary
    isHovered -> colors.onSurface
    else -> colors.onSurfaceVariant
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
