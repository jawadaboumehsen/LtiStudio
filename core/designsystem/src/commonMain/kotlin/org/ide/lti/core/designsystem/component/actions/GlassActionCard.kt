/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.actions

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.display.GlassCard
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
 * Standardized Liquid Glass Vertical Action Card (e.g. for Setup, Welcome, Dashboard screens).
 *
 * @param title Primary action title.
 * @param subtitle Explanatory secondary text.
 * @param icon The [AppIcon] to display in the elevated icon badge.
 * @param onClick Invoked when the card is clicked.
 * @param modifier Optional modifier.
 * @param isPrimary Whether this is the primary hero action (accent rim and elevated glow).
 * @param shortcut Optional keyboard shortcut hint shown beside the subtitle (e.g. "Ctrl+N").
 * @param backdrop Optional backdrop instance.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GlassActionCard(
    title: String,
    subtitle: String,
    icon: AppIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    shortcut: String? = null,
) {
    var isHovered by remember { mutableStateOf(false) }

    val targetBorder = when {
        isPrimary && isHovered -> MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Prominent)
        isPrimary -> MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Border)
        isHovered -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    val targetSurface = when {
        isHovered -> MaterialTheme.colorScheme.surfaceContainerHigh
        isPrimary -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.Subtle)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    val borderColor by animateColorAsState(targetBorder)
    val surfaceColor by animateColorAsState(targetSurface)

    GlassCard(
        modifier = modifier
            .height(ComponentSize.ActionCardHeight)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .glassOutlineBorder(color = borderColor, width = StrokeWidth.Hairline, shape = GlassShapes.ShellCard)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = GlassShapes.ShellCard,
        surfaceColor = surfaceColor,
        contentPadding = Spacing.Medium,
    ) {
        Column(verticalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier
                    .size(IconSize.ActionCard)
                    .clip(GlassShapes.Capsule)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.Faint))
                    .glassOutlineBorder(
                        color = if (isPrimary || isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        width = StrokeWidth.Hairline,
                        shape = GlassShapes.Capsule,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when (icon) {
                    is AppIcon.Vector -> Icon(
                        imageVector = icon.imageVector,
                        contentDescription = title,
                        modifier = Modifier.size(IconSize.Medium),
                        tint = if (isPrimary || isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    is AppIcon.Painted -> Icon(
                        painter = icon.painter(),
                        contentDescription = title,
                        modifier = Modifier.size(IconSize.Medium),
                        tint = if (isPrimary || isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.Small))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(Spacing.ExtraExtraSmall))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            if (shortcut != null) {
                Spacer(modifier = Modifier.height(Spacing.ExtraExtraSmall))
                Text(
                    text = shortcut,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
fun GlassActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    shortcut: String? = null,
) = GlassActionCard(
    title = title,
    subtitle = subtitle,
    icon = AppIcon.Vector(icon),
    onClick = onClick,
    modifier = modifier,
    isPrimary = isPrimary,
    shortcut = shortcut,
)

/**
 * Standardized Liquid Glass Horizontal Item Card (e.g. for Recent Projects, Notifications, File Cards).
 *
 * @param title Primary text.
 * @param subtitle Optional secondary description or path text.
 * @param icon Optional leading icon.
 * @param onClick Optional click action.
 * @param modifier Optional modifier.
 * @param isHighlighted Whether the card is highlighted (e.g. unread notification or active project).
 * @param trailingContent Optional trailing composable slot (e.g. timestamp, chevron, or action button).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GlassItemCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: AppIcon? = null,
    onClick: (() -> Unit)? = null,
    isHighlighted: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    var isHovered by remember { mutableStateOf(false) }

    val targetBorder = when {
        isHighlighted && isHovered -> MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Prominent)
        isHighlighted -> MaterialTheme.colorScheme.primary
        isHovered -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    val borderColor by animateColorAsState(targetBorder)
    val surfaceColor by animateColorAsState(
        if (isHovered) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
    )

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .glassOutlineBorder(color = borderColor, width = StrokeWidth.Hairline, shape = GlassShapes.ShellCard)
            .then(clickableModifier),
        shape = GlassShapes.ShellCard,
        surfaceColor = surfaceColor,
        contentPadding = Spacing.Medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(ComponentSize.TopBarButtonSize)
                        .clip(GlassShapes.Capsule)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .glassOutlineBorder(
                            color = if (isHighlighted || isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            width = StrokeWidth.Hairline,
                            shape = GlassShapes.Capsule,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    when (icon) {
                        is AppIcon.Vector -> Icon(
                            imageVector = icon.imageVector,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.Medium),
                            tint = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        is AppIcon.Painted -> Icon(
                            painter = icon.painter(),
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.Medium),
                            tint = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(Spacing.SmallMedium))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (trailingContent != null) {
                trailingContent()
            } else if (onClick != null) {
                Icon(
                    painter = AppIcons.ChevronRightPainterResource(),
                    contentDescription = null,
                    tint = if (isHovered) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Medium),
                )
            }
        }
    }
}
