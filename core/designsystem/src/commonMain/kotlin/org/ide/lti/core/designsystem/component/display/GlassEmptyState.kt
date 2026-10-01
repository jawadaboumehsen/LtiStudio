/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

enum class GlassEmptyStateOrientation {
    Vertical,
    Horizontal,
}

/**
 * Type-safe action configuration for [GlassEmptyState].
 */
sealed interface EmptyStateAction {
    data object None : EmptyStateAction
    data class Single(val label: String, val onClick: () -> Unit) : EmptyStateAction
    data class Dual(
        val primaryLabel: String,
        val onPrimaryClick: () -> Unit,
        val secondaryLabel: String,
        val onSecondaryClick: () -> Unit,
    ) : EmptyStateAction
    data class Custom(val content: @Composable () -> Unit) : EmptyStateAction
}

object GlassEmptyStateDefaults {
    val BadgeSize: Dp = ComponentSize.EmptyStateBadgeSize
    val IconSize: Dp = org.ide.lti.core.designsystem.theme.IconSize.ExtraLarge
}

/**
 * Reusable Liquid Glass empty state component for placeholder views, empty lists, and onboarding screens.
 */
@Composable
fun GlassEmptyState(
    icon: AppIcon,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    badgeSize: Dp = ComponentSize.EmptyStateBadgeSize,
    iconSize: Dp = IconSize.ExtraLarge,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    orientation: GlassEmptyStateOrientation = GlassEmptyStateOrientation.Vertical,
    onIconClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        when (orientation) {
            GlassEmptyStateOrientation.Vertical -> {
                Column(
                    modifier = modifier,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    GlassEmptyStateBadge(
                        icon = icon,
                        badgeSize = badgeSize,
                        iconSize = iconSize,
                        iconTint = iconTint,
                        onClick = onIconClick,
                    )

                    Spacer(modifier = Modifier.height(Spacing.Medium))

                    Text(
                        text = title,
                        style = titleStyle,
                        fontWeight = titleStyle.fontWeight ?: FontWeight.SemiBold,
                        color = titleColor,
                        textAlign = TextAlign.Center,
                    )

                    if (description != null) {
                        Spacer(modifier = Modifier.height(Spacing.ExtraSmall))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = descriptionColor,
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (action != null) {
                        Spacer(modifier = Modifier.height(Spacing.Medium))
                        action()
                    }
                }
            }

            GlassEmptyStateOrientation.Horizontal -> {
                Row(
                    modifier = modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                ) {
                    GlassEmptyStateBadge(
                        icon = icon,
                        badgeSize = badgeSize,
                        iconSize = iconSize,
                        iconTint = iconTint,
                        onClick = onIconClick,
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = titleStyle,
                            fontWeight = titleStyle.fontWeight ?: FontWeight.Medium,
                            color = titleColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (description != null) {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = descriptionColor,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (action != null) {
                        action()
                    }
                }
            }
        }
    }
}

@Composable
fun GlassEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    badgeSize: Dp = ComponentSize.EmptyStateBadgeSize,
    iconSize: Dp = IconSize.ExtraLarge,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    orientation: GlassEmptyStateOrientation = GlassEmptyStateOrientation.Vertical,
    onIconClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) = GlassEmptyState(
    icon = AppIcon.Vector(icon),
    title = title,
    modifier = modifier,
    description = description,
    badgeSize = badgeSize,
    iconSize = iconSize,
    iconTint = iconTint,
    titleStyle = titleStyle,
    titleColor = titleColor,
    descriptionColor = descriptionColor,
    orientation = orientation,
    onIconClick = onIconClick,
    action = action,
)

/**
 * Circular glass badge container for empty states.
 */
@Composable
fun GlassEmptyStateBadge(
    icon: AppIcon,
    modifier: Modifier = Modifier,
    badgeSize: Dp = ComponentSize.EmptyStateBadgeSize,
    iconSize: Dp = IconSize.ExtraLarge,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
) {
    if (onClick != null) {
        GlassIconButton(
            onClick = onClick,
            modifier = modifier,
            size = badgeSize,
        ) {
            when (icon) {
                is AppIcon.Vector -> Icon(
                    imageVector = icon.imageVector,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(iconSize),
                )
                is AppIcon.Painted -> Icon(
                    painter = icon.painter(),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    } else {
        val effectsEnabled = GlassTheme.effectsEnabled
        val glowColor = MaterialTheme.colorScheme.primary
        val badgeBg = if (effectsEnabled) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.Elevated)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
        val badgeBorder = if (effectsEnabled) {
            MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Border)
        } else {
            MaterialTheme.colorScheme.primary
        }
        val glowModifier = if (effectsEnabled) {
            Modifier.drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = AlphaTokens.Glow),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = size.minDimension * 0.85f,
                    ),
                )
            }
        } else {
            Modifier
        }
        Box(
            modifier = modifier
                .size(badgeSize)
                .then(glowModifier)
                .clip(GlassShapes.Capsule)
                .background(badgeBg)
                .glassOutlineBorder(
                    width = StrokeWidth.Hairline,
                    color = badgeBorder,
                    shape = GlassShapes.Capsule,
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (icon) {
                is AppIcon.Vector -> Icon(
                    imageVector = icon.imageVector,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(iconSize),
                )
                is AppIcon.Painted -> Icon(
                    painter = icon.painter(),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}
