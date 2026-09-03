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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.kashif_e.backdrop.Backdrop
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.resolveAnimationSpec

/**
 * Standardized Liquid Glass Vertical Action Card (e.g. for Setup, Welcome, Dashboard screens).
 *
 * @param title Primary action title.
 * @param subtitle Explanatory secondary text.
 * @param icon The [AppIcon] to display in the elevated icon badge.
 * @param onClick Invoked when the card is clicked.
 * @param modifier Optional modifier.
 * @param isPrimary Whether this is the primary hero action (accent rim and elevated glow).
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
    backdrop: Backdrop? = null,
) {
    var isHovered by remember { mutableStateOf(false) }
    val glassColors = GlassTheme.colors

    val targetBorder = when {
        isPrimary && isHovered -> glassColors.glassBorderHighlight.copy(alpha = AlphaTokens.Prominent)
        isPrimary -> glassColors.glassAccent.copy(alpha = AlphaTokens.Border)
        isHovered -> glassColors.glassBorderHighlight
        else -> glassColors.glassBorder
    }

    val targetSurface = when {
        isHovered -> glassColors.glassSurfaceElevated
        isPrimary -> glassColors.glassSurfaceElevated.copy(alpha = AlphaTokens.Subtle)
        else -> glassColors.glassSurface
    }

    val borderColor by animateColorAsState(
        targetValue = targetBorder,
        animationSpec = resolveAnimationSpec(GlassTheme.reducedMotion),
    )

    val surfaceColor by animateColorAsState(
        targetValue = targetSurface,
        animationSpec = resolveAnimationSpec(GlassTheme.reducedMotion),
    )

    GlassCard(
        modifier = modifier
            .height(ComponentSize.ActionCardHeight)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = GlassShapes.Card,
        surfaceColor = surfaceColor,
        borderColor = borderColor,
        contentPadding = Spacing.Medium,
        backdrop = backdrop,
    ) {
        Column(verticalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier
                    .size(IconSize.ActionCard)
                    .clip(GlassShapes.Capsule)
                    .background(glassColors.glassSurfaceElevated.copy(alpha = AlphaTokens.Faint))
                    .glassOutlineBorder(
                        color = if (isPrimary || isHovered) glassColors.glassBorderHighlight else glassColors.glassBorder,
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
                        tint = if (isPrimary || isHovered) glassColors.glassAccent else glassColors.onGlassSecondary,
                    )
                    is AppIcon.Painted -> Icon(
                        painter = icon.painter(),
                        contentDescription = title,
                        modifier = Modifier.size(IconSize.Medium),
                        tint = if (isPrimary || isHovered) glassColors.glassAccent else glassColors.onGlassSecondary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.Small))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = glassColors.onGlass,
            )
            Spacer(modifier = Modifier.height(Spacing.ExtraExtraSmall))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = glassColors.onGlassSecondary,
                maxLines = 1,
            )
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
    backdrop: Backdrop? = null,
) = GlassActionCard(
    title = title,
    subtitle = subtitle,
    icon = AppIcon.Vector(icon),
    onClick = onClick,
    modifier = modifier,
    isPrimary = isPrimary,
    backdrop = backdrop,
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
 * @param backdrop Optional backdrop instance.
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
    backdrop: Backdrop? = null,
) {
    var isHovered by remember { mutableStateOf(false) }
    val glassColors = GlassTheme.colors

    val targetBorder = when {
        isHighlighted && isHovered -> glassColors.glassBorderHighlight.copy(alpha = AlphaTokens.Prominent)
        isHighlighted -> glassColors.glassBorderHighlight
        isHovered -> glassColors.glassBorderHighlight
        else -> glassColors.glassBorder
    }

    val borderColor by animateColorAsState(
        targetValue = targetBorder,
        animationSpec = resolveAnimationSpec(GlassTheme.reducedMotion),
    )

    val surfaceColor by animateColorAsState(
        targetValue = if (isHovered) glassColors.glassSurfaceElevated else glassColors.glassSurface,
        animationSpec = resolveAnimationSpec(GlassTheme.reducedMotion),
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
            .then(clickableModifier),
        shape = GlassShapes.Card,
        surfaceColor = surfaceColor,
        borderColor = borderColor,
        contentPadding = Spacing.Medium,
        backdrop = backdrop,
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
                        .background(glassColors.glassSurfaceElevated)
                        .glassOutlineBorder(
                            color = if (isHighlighted || isHovered) glassColors.glassBorderHighlight else glassColors.glassBorder,
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
                            tint = if (isHighlighted) glassColors.glassAccent else glassColors.onGlassSecondary,
                        )
                        is AppIcon.Painted -> Icon(
                            painter = icon.painter(),
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.Medium),
                            tint = if (isHighlighted) glassColors.glassAccent else glassColors.onGlassSecondary,
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
                    color = glassColors.onGlass,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = glassColors.onGlassSecondary,
                    )
                }
            }

            if (trailingContent != null) {
                trailingContent()
            } else if (onClick != null) {
                Icon(
                    painter = AppIcons.ChevronRightPainterResource(),
                    contentDescription = null,
                    tint = if (isHovered) glassColors.onGlass else glassColors.onGlassMuted,
                    modifier = Modifier.size(IconSize.Medium),
                )
            }
        }
    }
}
