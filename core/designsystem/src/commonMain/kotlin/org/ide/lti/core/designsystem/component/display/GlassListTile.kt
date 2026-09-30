/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.display

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

/**
 * Leading accessory slot for [GlassListTile].
 */
sealed interface ListTileLeading {
    data object None : ListTileLeading
    data class Icon(val icon: AppIcon) : ListTileLeading
    data class Custom(val content: @Composable () -> Unit) : ListTileLeading
}

/**
 * Trailing accessory slot for [GlassListTile].
 */
sealed interface ListTileTrailing {
    data object None : ListTileTrailing
    data class Chevron(val onClick: () -> Unit) : ListTileTrailing
    data class Toggle(val checked: Boolean, val onCheckedChange: (Boolean) -> Unit) : ListTileTrailing
    data class ValueText(val text: String, val onClick: (() -> Unit)? = null) : ListTileTrailing
    data class Custom(val content: @Composable () -> Unit) : ListTileTrailing
}

/**
 * Standardized Liquid Glass List Tile component using strongly typed slots.
 */
@Composable
fun GlassListTile(
    headline: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leading: ListTileLeading = ListTileLeading.None,
    trailing: ListTileTrailing = ListTileTrailing.None,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) = GlassListTile(
    headline = headline,
    modifier = modifier,
    supportingText = supportingText,
    leadingIcon = when (leading) {
        is ListTileLeading.Icon -> leading.icon
        else -> null
    },
    trailingContent = when (trailing) {
        is ListTileTrailing.None -> null
        is ListTileTrailing.Chevron -> {
            { GlassIconButton(onClick = trailing.onClick, icon = AppIcons.ChevronRight, size = IconSize.Medium) }
        }
        is ListTileTrailing.Toggle -> {
            { GlassToggle(checked = trailing.checked, onCheckedChange = trailing.onCheckedChange, enabled = enabled) }
        }
        is ListTileTrailing.ValueText -> {
            {
                Text(
                    text = trailing.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        is ListTileTrailing.Custom -> trailing.content
    },
    onClick = onClick,
    enabled = enabled,
)

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
                val effectsEnabled = GlassTheme.effectsEnabled
                val badgeBg = if (effectsEnabled) {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.Elevated)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
                val badgeBorder = if (effectsEnabled) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
                Box(
                    modifier = Modifier
                        .size(ComponentSize.ListTileBadgeSize)
                        .clip(GlassShapes.Small)
                        .background(badgeBg)
                        .glassOutlineBorder(
                            width = StrokeWidth.Hairline,
                            color = badgeBorder,
                            shape = GlassShapes.Small,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    when (leadingIcon) {
                        is AppIcon.Vector -> Icon(
                            imageVector = leadingIcon.imageVector,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(IconSize.Medium),
                        )
                        is AppIcon.Painted -> Icon(
                            painter = leadingIcon.painter(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(IconSize.Medium),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onClick != null) {
                    Icon(
                        painter = AppIcons.ChevronRightPainterResource(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
