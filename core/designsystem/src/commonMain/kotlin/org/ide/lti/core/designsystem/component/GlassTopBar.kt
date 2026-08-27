/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.MotionDuration
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.WindowControlColors
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * State container for desktop window control actions.
 */
data class WindowControlActions(
    val onMinimize: () -> Unit = {},
    val onMaximize: () -> Unit = {},
    val onClose: () -> Unit = {},
)

val LocalWindowControlActions = staticCompositionLocalOf { WindowControlActions() }

/**
 * Professional IDE Liquid Glass Top App Bar.
 *
 * Implements Apple-inspired Liquid Glass effects using the backdrop engine:
 * - Brand logo badge with glowing accent gradient.
 * - Workspace / file breadcrumb pill indicator.
 * - Interactive Liquid Glass Command Search capsule with shortcut badge.
 * - Segmented Liquid Glass panel layout toggles (Explorer, Terminal, AI Assistant).
 * - Utility actions (Notifications, Settings) with micro-interaction hover states.
 * - Platform window management controls with smooth hover responses.
 */
@Composable
fun GlassTopBar(
    title: String = "LtiRom",
    workspaceName: String? = "Workspace",
    onMenuClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onWindowMinimize: (() -> Unit)? = null,
    onWindowMaximize: (() -> Unit)? = null,
    onWindowClose: (() -> Unit)? = null,
    isLeftPanelVisible: Boolean = true,
    isRightPanelVisible: Boolean = true,
    isMiddleDownPanelVisible: Boolean = true,
    onClickLeftPanelIcon: () -> Unit = {},
    onClickMiddleDownPanelIcon: () -> Unit = {},
    onClickRightPanelIcon: () -> Unit = {},
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier,
) {
    val actualBackdrop = backdrop ?: GlassTheme.backdrop
    val glassColors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    val localControls = LocalWindowControlActions.current

    val effectiveMinimize = onWindowMinimize ?: localControls.onMinimize
    val effectiveMaximize = onWindowMaximize ?: localControls.onMaximize
    val effectiveClose = onWindowClose ?: localControls.onClose

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentSize.TopBarHeight)
            .then(
                if (effectsEnabled && actualBackdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = actualBackdrop,
                        shape = { RoundedCornerShape(CornerRadius.None) },
                        effects = {
                            vibrancy()
                            blur(4.dp.toPx())
                            lens(4.dp.toPx(), 8.dp.toPx())
                        },
                        highlight = { Highlight.Plain },
                        onDrawSurface = {
                            drawRect(glassColors.glassSurface)
                        }
                    )
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surface)
                }
            )
            .border(
                width = StrokeWidth.Hairline,
                color = glassColors.glassBorder.copy(alpha = AlphaTokens.Border),
                shape = RoundedCornerShape(CornerRadius.None)
            )
            .padding(horizontal = Spacing.MediumSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        // ==========================================
        // 1. LEFT: Brand & Navigation Section
        // ==========================================
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Compact)
        ) {
            // Brand Logo with gradient glow
            Box(
                modifier = Modifier
                    .size(ComponentSize.LogoBadgeSize)
                    .clip(RoundedCornerShape(CornerRadius.Small))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                glassColors.glassAccent,
                                BrandColors.LogoGradientEnd
                            )
                        )
                    )
                    .border(
                        StrokeWidth.Hairline,
                        glassColors.glassBorder.copy(alpha = AlphaTokens.Disabled),
                        RoundedCornerShape(CornerRadius.Small)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "L",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = glassColors.onGlass
                )
            }

            // Menu Hamburger
            GlassTopBarIconButton(
                icon = AppIcons.Menu,
                contentDescription = "Main Menu",
                onClick = onMenuClick,
            )

            // App Title & Workspace Breadcrumb
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                modifier = Modifier.padding(start = Spacing.ExtraSmall)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = glassColors.onGlass,
                )

                if (workspaceName != null) {
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.bodySmall,
                        color = glassColors.onGlassMuted,
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(CornerRadius.Compact))
                            .background(glassColors.glassSurfaceElevated.copy(alpha = AlphaTokens.Elevated))
                            .border(StrokeWidth.Hairline, glassColors.glassBorder, RoundedCornerShape(CornerRadius.Compact))
                            .padding(horizontal = Spacing.Compact, vertical = Spacing.ExtraExtraSmall)
                    ) {
                        Text(
                            text = workspaceName,
                            style = MaterialTheme.typography.labelSmall,
                            color = glassColors.onGlassSecondary,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ==========================================
        // 2. CENTER: Liquid Glass Search Capsule
        // ==========================================
        LiquidSearchCapsule(
            onClick = onSearchClick,
            backdrop = actualBackdrop,
            modifier = Modifier.widthIn(min = ComponentSize.SearchCapsuleMinWidth, max = ComponentSize.SearchCapsuleMaxWidth)
        )

        Spacer(modifier = Modifier.weight(1f))

        // ==========================================
        // 3. RIGHT: Panel Toggles, Actions, Window Controls
        // ==========================================
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            modifier = Modifier.padding(end = Spacing.Compact)
        ) {
            // Segmented Layout Mode Toggle
            SegmentedPanelLayoutToggle(
                isLeftPanelVisible = isLeftPanelVisible,
                isMiddleDownPanelVisible = isMiddleDownPanelVisible,
                isRightPanelVisible = isRightPanelVisible,
                onClickLeftPanelIcon = onClickLeftPanelIcon,
                onClickMiddleDownPanelIcon = onClickMiddleDownPanelIcon,
                onClickRightPanelIcon = onClickRightPanelIcon,
            )

            // Notifications button
            GlassTopBarIconButton(
                icon = AppIcons.OutlinedNotifications,
                contentDescription = "Notifications",
                onClick = onNotificationsClick,
            )

            // Settings button
            GlassTopBarIconButton(
                icon = AppIcons.SettingsOutlined,
                contentDescription = "Settings",
                onClick = onSettingsClick,
            )

            // Divider before window controls
            Box(
                modifier = Modifier
                    .padding(horizontal = Spacing.ExtraExtraSmall)
                    .width(StrokeWidth.Standard)
                    .height(ComponentSize.TopBarDividerHeight)
                    .background(glassColors.glassBorder)
            )

            // Desktop Window Controls (Minimize, Maximize, Close)
            DesktopWindowControls(
                onMinimize = effectiveMinimize,
                onMaximize = effectiveMaximize,
                onClose = effectiveClose,
            )
        }
    }
}

/**
 * Liquid Glass Search Capsule with keyboard shortcut badge.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LiquidSearchCapsule(
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    var isHovered by remember { mutableStateOf(false) }
    val glassColors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled

    val borderColor by animateColorAsState(
        targetValue = if (isHovered) glassColors.glassBorderHighlight else glassColors.glassBorder,
        animationSpec = tween(MotionDuration.Fast)
    )

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.01f else 1f,
        animationSpec = tween(MotionDuration.Fast)
    )

    Row(
        modifier = modifier
            .height(ComponentSize.SearchCapsuleHeight)
            .scale(scale)
            .clip(RoundedCornerShape(CornerRadius.Pill))
            .then(
                if (effectsEnabled && backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(CornerRadius.Pill) },
                        effects = {
                            vibrancy()
                            blur(2.dp.toPx())
                            lens(4.dp.toPx(), 8.dp.toPx())
                        },
                        highlight = { if (isHovered) Highlight.Ambient else Highlight.Plain },
                        onDrawSurface = {
                            drawRect(
                                if (isHovered) glassColors.glassSurfaceElevated else glassColors.glassSurface
                            )
                        }
                    )
                } else {
                    Modifier.background(glassColors.glassSurface)
                }
            )
            .border(width = StrokeWidth.Hairline, color = borderColor, shape = RoundedCornerShape(CornerRadius.Pill))
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.MediumSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        Icon(
            imageVector = AppIcons.Search,
            contentDescription = "Search",
            modifier = Modifier.size(IconSize.Search),
            tint = if (isHovered) glassColors.glassAccent else glassColors.onGlassMuted
        )

        Text(
            text = "Search files, actions...",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
            color = if (isHovered) glassColors.onGlassSecondary else glassColors.onGlassMuted,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )

        // Shortcut pill badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(CornerRadius.ExtraSmall))
                .background(glassColors.glassSurfaceElevated)
                .border(StrokeWidth.Hairline, glassColors.glassBorder, RoundedCornerShape(CornerRadius.ExtraSmall))
                .padding(horizontal = Spacing.Compact, vertical = Spacing.ExtraExtraSmall)
        ) {
            Text(
                text = "Ctrl+P",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = glassColors.onGlassSecondary
            )
        }
    }
}

/**
 * Segmented layout mode toggle buttons for IDE panels.
 */
@Composable
private fun SegmentedPanelLayoutToggle(
    isLeftPanelVisible: Boolean,
    isMiddleDownPanelVisible: Boolean,
    isRightPanelVisible: Boolean,
    onClickLeftPanelIcon: () -> Unit,
    onClickMiddleDownPanelIcon: () -> Unit,
    onClickRightPanelIcon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassColors = GlassTheme.colors

    Row(
        modifier = modifier
            .height(ComponentSize.SegmentedToggleHeight)
            .clip(RoundedCornerShape(CornerRadius.Small))
            .background(glassColors.glassSurface)
            .border(StrokeWidth.Hairline, glassColors.glassBorder, RoundedCornerShape(CornerRadius.Small))
            .padding(Spacing.ExtraExtraSmall),
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left Panel Toggle (File Explorer)
        SegmentedToggleButton(
            icon = AppIcons.leftPanelPainterResource(),
            contentDescription = "Toggle Left Panel (Files)",
            isActive = isLeftPanelVisible,
            onClick = onClickLeftPanelIcon
        )

        // Middle Down Panel Toggle (Terminal/Output)
        SegmentedToggleButton(
            icon = AppIcons.bottomPanelPainterResource(),
            contentDescription = "Toggle Bottom Panel (Terminal)",
            isActive = isMiddleDownPanelVisible,
            onClick = onClickMiddleDownPanelIcon
        )

        // Right Panel Toggle (AI Assistant)
        SegmentedToggleButton(
            icon = AppIcons.rightPanelPainterResource(),
            contentDescription = "Toggle Right Panel (AI Assistant)",
            isActive = isRightPanelVisible,
            onClick = onClickRightPanelIcon
        )
    }
}

/**
 * Individual toggle button inside the segmented panel layout control.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SegmentedToggleButton(
    icon: Painter,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isHovered by remember { mutableStateOf(false) }
    val glassColors = GlassTheme.colors

    val bgModifier = when {
        isActive -> Modifier.background(glassColors.glassAccent.copy(alpha = AlphaTokens.Hover))
        isHovered -> Modifier.background(glassColors.glassSurfaceElevated)
        else -> Modifier
    }

    Box(
        modifier = modifier
            .size(ComponentSize.SegmentedButtonSize)
            .clip(RoundedCornerShape(CornerRadius.Compact))
            .then(bgModifier)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(IconSize.Segmented),
            tint = when {
                isActive -> glassColors.glassAccent
                isHovered -> glassColors.onGlass
                else -> glassColors.onGlassMuted
            }
        )
    }
}

/**
 * Clean Liquid Glass Top Bar Icon Button.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun GlassTopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isHovered by remember { mutableStateOf(false) }
    val glassColors = GlassTheme.colors

    Box(
        modifier = modifier
            .size(ComponentSize.TopBarButtonSize)
            .clip(CircleShape)
            .background(if (isHovered) glassColors.glassSurfaceElevated else Color.Transparent)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(IconSize.Medium),
            tint = if (isHovered) glassColors.onGlass else glassColors.onGlassSecondary,
        )
    }
}

/**
 * Desktop Window control buttons (minimize, maximize, close).
 */
@Composable
private fun DesktopWindowControls(
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WindowControlButton(
            icon = Icons.Default.Remove,
            contentDescription = "Minimize",
            onClick = onMinimize,
        )

        WindowControlButton(
            icon = Icons.Default.CropSquare,
            contentDescription = "Maximize",
            onClick = onMaximize,
        )

        WindowControlButton(
            icon = Icons.Default.Close,
            contentDescription = "Close",
            onClick = onClose,
            isCloseButton = true,
        )
    }
}

/**
 * Individual Window Control Button with OS-style close hover.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WindowControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCloseButton: Boolean = false,
) {
    var isHovered by remember { mutableStateOf(false) }
    val glassColors = GlassTheme.colors

    val backgroundColor = when {
        isCloseButton && isHovered -> WindowControlColors.CloseHover
        isHovered -> glassColors.glassSurfaceElevated
        else -> Color.Transparent
    }

    val iconTint = when {
        isCloseButton && isHovered -> glassColors.onGlass
        isHovered -> glassColors.onGlass
        else -> glassColors.onGlassMuted
    }

    Box(
        modifier = modifier
            .size(width = ComponentSize.WindowControlWidth, height = ComponentSize.WindowControlHeight)
            .clip(RoundedCornerShape(CornerRadius.ExtraSmall))
            .background(backgroundColor)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(IconSize.WindowControl),
            tint = iconTint,
        )
    }
}

@Preview
@Composable
private fun GlassTopBar_Preview() {
    LtiTheme {
        GlassTopBar(
            title = "LtiRomGui",
            workspaceName = "LtiRomProject",
            isLeftPanelVisible = true,
            isRightPanelVisible = true,
            isMiddleDownPanelVisible = false,
        )
    }
}