/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.plugins.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.actions.DesktopWindowControls
import org.ide.lti.core.designsystem.component.actions.LocalWindowControlActions
import org.ide.lti.core.designsystem.component.actions.windowDragGesture
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.PluginThemeColors
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.StudioBackdropColors
import org.ide.lti.core.designsystem.theme.ThemeColors

/**
 * Dense IDE product header matching LtiRom Studio concept mockups.
 */
@Composable
public fun PluginTopBar(
    activeWorkspaceId: String?,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassColors = GlassTheme.colors
    val windowControls = LocalWindowControlActions.current

    val borderModifier = Modifier.drawBehind {
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    glassColors.glassBorderHighlight.copy(alpha = AlphaTokens.Hover),
                    glassColors.glassBorderHighlight.copy(alpha = AlphaTokens.Prominent),
                    glassColors.glassBorderHighlight.copy(alpha = AlphaTokens.Hover),
                    Color.Transparent,
                ),
                startX = 0f,
                endX = size.width,
            ),
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = StrokeWidth.Hairline.toPx(),
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentSize.PluginTopBarHeight)
            .windowDragGesture(
                onDrag = { windowControls.onDragWindow.invoke() },
            )
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        StudioBackdropColors.ChromeStart,
                        StudioBackdropColors.ChromeCenter,
                        StudioBackdropColors.ChromeEnd,
                    ),
                ),
            )
            .then(borderModifier)
            .padding(horizontal = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Leading: Brand logo + Title
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.MediumSmall),
        ) {
            Box(
                modifier = Modifier
                    .size(ComponentSize.PluginLogoBadgeSize)
                    .clip(GlassShapes.MediumSmall)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                ThemeColors.FleetCyan,
                                ThemeColors.FleetBlue,
                            ),
                        ),
                    )
                    .padding(Spacing.Compact),
                contentAlignment = Alignment.Center,
            ) {
                LtiRomStudioMark()
            }

            Text(
                text = "LtiRom Studio",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = glassColors.onGlass,
            )
        }

        // Center: Top Navigation Actions matching Concept mockups
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.MediumSmall),
        ) {
            // Setup Action
            TopBarNavButton(
                label = "Setup",
                icon = AppIcons.SettingsPainterResource,
                isSelected = false,
                onClick = onNavigateBack,
            )

            // Workspace Action (active rectangular card with bright cyan border and luminous blue glass fill)
            TopBarNavButton(
                label = "Workspace",
                icon = AppIcons.FolderPainterResource,
                isSelected = activeWorkspaceId != null,
                onClick = onNavigateBack,
            )

            // Settings Action
            TopBarNavButton(
                label = "Settings",
                icon = AppIcons.SettingsPainterResource,
                isSelected = false,
                onClick = onOpenSettings,
            )
        }

        // Trailing: Window Controls
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            DesktopWindowControls(
                onMinimize = windowControls.onMinimize,
                onMaximize = windowControls.onMaximize,
                onClose = windowControls.onClose,
                isMaximized = windowControls.isMaximized(),
            )
        }
    }
}

@Composable
private fun LtiRomStudioMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(IconSize.Large)) {
        val lineStyle = Stroke(
            width = StrokeWidth.Standard.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        repeat(3) { layer ->
            val offset = size.height * (0.08f + layer * 0.22f)
            val path = Path().apply {
                moveTo(size.width * 0.50f, offset)
                lineTo(size.width * 0.88f, offset + size.height * 0.20f)
                lineTo(size.width * 0.50f, offset + size.height * 0.40f)
                lineTo(size.width * 0.12f, offset + size.height * 0.20f)
                close()
            }
            drawPath(path = path, color = BrandColors.OnLogoBadge, style = lineStyle)
        }
    }
}

@Composable
private fun TopBarNavButton(
    label: String,
    icon: @Composable () -> androidx.compose.ui.graphics.painter.Painter,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val glassColors = GlassTheme.colors
    val shape = GlassShapes.MediumSmall

    val bg = if (isSelected) {
        PluginThemeColors.ActiveTabBackground
    } else {
        Color.Transparent
    }

    val contentColor = if (isSelected) {
        BrandColors.OnLogoBadge
    } else {
        glassColors.onGlassSecondary
    }

    val borderMod = if (isSelected) {
        Modifier.glassOutlineBorder(
            width = StrokeWidth.Hairline,
            color = PluginThemeColors.ActiveTabBorder,
            shape = shape,
        )
    } else {
        Modifier
    }

    val sizeMod = if (isSelected) {
        Modifier
            .width(ComponentSize.PluginWorkspaceTabWidth)
            .height(ComponentSize.PluginWorkspaceTabHeight)
    } else {
        Modifier.height(ComponentSize.PluginWorkspaceTabHeight)
    }

    Row(
        modifier = Modifier
            .then(sizeMod)
            .clip(shape)
            .background(bg)
            .then(borderMod)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.SmallMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Compact, Alignment.CenterHorizontally),
    ) {
        Icon(
            painter = icon(),
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(IconSize.Medium),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
