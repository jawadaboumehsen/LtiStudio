/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Standardized Liquid Glass List Tile component.
 *
 * @param headline The primary text of the tile.
 * @param modifier Optional modifier.
 * @param supportingText Optional description/secondary text.
 * @param leadingIcon Optional leading icon.
 * @param trailingContent Optional trailing composable slot (toggle, chevron, badge, or text).
 * @param onClick Optional click action.
 * @param enabled Whether the tile is interactive.
 */
@Composable
fun GlassListTile(
    headline: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingIcon: AppIcon? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val glassColors = GlassTheme.colors
    val clickableModifier = if (onClick != null && enabled) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .padding(end = Spacing.Medium),
        ) {
            if (leadingIcon != null) {
                when (leadingIcon) {
                    is AppIcon.Vector -> Icon(
                        imageVector = leadingIcon.imageVector,
                        contentDescription = null,
                        tint = if (enabled) glassColors.glassAccent else glassColors.onGlassMuted,
                        modifier = Modifier.size(IconSize.Large),
                    )
                    is AppIcon.Painted -> Icon(
                        painter = leadingIcon.painter(),
                        contentDescription = null,
                        tint = if (enabled) glassColors.glassAccent else glassColors.onGlassMuted,
                        modifier = Modifier.size(IconSize.Large),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) glassColors.onGlass else glassColors.onGlassMuted,
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = glassColors.onGlassSecondary,
                    )
                }
            }
        }

        trailingContent?.invoke()
    }
}

/**
 * Standardized Liquid Glass Toggle Tile (e.g. for Settings, Preferences).
 */
@Composable
fun GlassToggleTile(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: AppIcon? = null,
    enabled: Boolean = true,
) {
    GlassListTile(
        headline = title,
        supportingText = description,
        leadingIcon = icon,
        enabled = enabled,
        modifier = modifier,
        trailingContent = {
            GlassToggle(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
    )
}

/**
 * Standardized Liquid Glass Value Tile (e.g. for displaying selection values, readouts).
 */
@Composable
fun GlassValueTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: AppIcon? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val glassColors = GlassTheme.colors
    GlassListTile(
        headline = title,
        supportingText = description,
        leadingIcon = icon,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = glassColors.onGlassSecondary,
                )
                if (onClick != null) {
                    Icon(
                        painter = AppIcons.ChevronRightPainterResource(),
                        contentDescription = null,
                        tint = glassColors.onGlassMuted,
                        modifier = Modifier.size(IconSize.Small),
                    )
                }
            }
        },
    )
}

@Composable
fun GlassToggleTile(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) = GlassToggleTile(
    title = title,
    checked = checked,
    onCheckedChange = onCheckedChange,
    icon = AppIcon.Vector(icon),
    modifier = modifier,
    description = description,
    enabled = enabled,
)

@Composable
fun GlassValueTile(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) = GlassValueTile(
    title = title,
    value = value,
    icon = AppIcon.Vector(icon),
    modifier = modifier,
    description = description,
    onClick = onClick,
    enabled = enabled,
)

