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
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LensRefraction
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.MotionDuration
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.WindowControlColors
import org.jetbrains.compose.ui.tooling.preview.Preview

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
    modifier: Modifier = Modifier,
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
                            blur(BackdropBlur.TopBar.toPx())
                            lens(LensRefraction.TopBar.height.toPx(), LensRefraction.TopBar.amount.toPx())
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
        GlassTopBarLeftSection(
            title = title,
            workspaceName = workspaceName,
            onMenuClick = onMenuClick,
            backdrop = actualBackdrop,
        )

        Spacer(modifier = Modifier.weight(1f))

        LiquidSearchCapsule(
            onClick = onSearchClick,
            backdrop = actualBackdrop,
            modifier = Modifier.widthIn(
                min = ComponentSize.SearchCapsuleMinWidth,
                max = ComponentSize.SearchCapsuleMaxWidth
            )
        )

        Spacer(modifier = Modifier.weight(1f))

        GlassTopBarRightSection(
            isLeftPanelVisible = isLeftPanelVisible,
            isMiddleDownPanelVisible = isMiddleDownPanelVisible,
            isRightPanelVisible = isRightPanelVisible,
            onClickLeftPanelIcon = onClickLeftPanelIcon,
            onClickMiddleDownPanelIcon = onClickMiddleDownPanelIcon,
            onClickRightPanelIcon = onClickRightPanelIcon,
            onNotificationsClick = onNotificationsClick,
            onSettingsClick = onSettingsClick,
            effectiveMinimize = effectiveMinimize,
            effectiveMaximize = effectiveMaximize,
            effectiveClose = effectiveClose,
            backdrop = actualBackdrop,
        )
    }
}

@Composable
private fun GlassTopBarLeftSection(
    title: String,
    workspaceName: String?,
    onMenuClick: () -> Unit,
    backdrop: Backdrop? = null,
) {
    val glassColors = GlassTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Compact)
    ) {
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

        GlassTopBarIconButton(
            icon = AppIcons.Menu,
            contentDescription = "Main Menu",
            onClick = onMenuClick,
            backdrop = backdrop,
        )

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
                        .border(
                            StrokeWidth.Hairline,
                            glassColors.glassBorder,
                            RoundedCornerShape(CornerRadius.Compact)
                        )
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
}

@Composable
private fun GlassTopBarRightSection(
    isLeftPanelVisible: Boolean,
    isMiddleDownPanelVisible: Boolean,
    isRightPanelVisible: Boolean,
    onClickLeftPanelIcon: () -> Unit,
    onClickMiddleDownPanelIcon: () -> Unit,
    onClickRightPanelIcon: () -> Unit,
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    effectiveMinimize: () -> Unit,
    effectiveMaximize: () -> Unit,
    effectiveClose: () -> Unit,
    backdrop: Backdrop? = null,
) {
    val glassColors = GlassTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        modifier = Modifier.padding(end = Spacing.Compact)
    ) {
        SegmentedPanelLayoutToggle(
            isLeftPanelVisible = isLeftPanelVisible,
            isMiddleDownPanelVisible = isMiddleDownPanelVisible,
            isRightPanelVisible = isRightPanelVisible,
            onClickLeftPanelIcon = onClickLeftPanelIcon,
            onClickMiddleDownPanelIcon = onClickMiddleDownPanelIcon,
            onClickRightPanelIcon = onClickRightPanelIcon,
            backdrop = backdrop,
        )

        GlassTopBarIconButton(
            icon = AppIcons.OutlinedNotifications,
            contentDescription = "Notifications",
            onClick = onNotificationsClick,
            backdrop = backdrop,
        )

        GlassTopBarIconButton(
            icon = AppIcons.SettingsOutlined,
            contentDescription = "Settings",
            onClick = onSettingsClick,
            backdrop = backdrop,
        )

        Box(
            modifier = Modifier
                .padding(horizontal = Spacing.ExtraExtraSmall)
                .width(StrokeWidth.Standard)
                .height(ComponentSize.TopBarDividerHeight)
                .background(glassColors.glassBorder)
        )

        DesktopWindowControls(
            onMinimize = effectiveMinimize,
            onMaximize = effectiveMaximize,
            onClose = effectiveClose,
        )
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
                            blur(BackdropBlur.SearchCapsule.toPx())
                            lens(LensRefraction.TopBar.height.toPx(), LensRefraction.TopBar.amount.toPx())
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
                role = Role.Button,
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
    backdrop: Backdrop? = null,
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
            onClick = onClickLeftPanelIcon,
            backdrop = backdrop,
        )

        // Middle Down Panel Toggle (Terminal/Output)
        SegmentedToggleButton(
            icon = AppIcons.bottomPanelPainterResource(),
            contentDescription = "Toggle Bottom Panel (Terminal)",
            isActive = isMiddleDownPanelVisible,
            onClick = onClickMiddleDownPanelIcon,
            backdrop = backdrop,
        )

        // Right Panel Toggle (AI Assistant)
        SegmentedToggleButton(
            icon = AppIcons.rightPanelPainterResource(),
            contentDescription = "Toggle Right Panel (AI Assistant)",
            isActive = isRightPanelVisible,
            onClick = onClickRightPanelIcon,
            backdrop = backdrop,
        )
    }
}

/**
 * Individual toggle button inside the segmented panel layout control with real glass effect.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SegmentedToggleButton(
    icon: Painter,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
) {
    var isHovered by remember { mutableStateOf(false) }
    val glassColors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    val actualBackdrop = backdrop ?: GlassTheme.backdrop
    val buttonShape = RoundedCornerShape(CornerRadius.Compact)

    val (surfaceColor, tintColor) = resolveSegmentedToggleColors(isActive, isHovered, glassColors)
    val iconTint = resolveSegmentedToggleIconTint(isActive, isHovered, glassColors)
    val borderColor = resolveSegmentedToggleBorderColor(isActive, isHovered, glassColors)

    Box(
        modifier = modifier
            .size(ComponentSize.SegmentedButtonSize)
            .clip(buttonShape)
            .segmentedToggleSurface(
                isHovered = isHovered,
                effectsEnabled = effectsEnabled,
                backdrop = actualBackdrop,
                shape = buttonShape,
                surfaceColor = surfaceColor,
                tintColor = tintColor,
            )
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(width = StrokeWidth.Hairline, color = borderColor, shape = buttonShape)
                } else {
                    Modifier
                }
            )
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(IconSize.Segmented),
            tint = iconTint,
        )
    }
}

private fun resolveSegmentedToggleColors(
    isActive: Boolean,
    isHovered: Boolean,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme,
): Pair<Color, Color> {
    val surfaceColor = if (isActive || isHovered) glassColors.glassSurfaceElevated else glassColors.glassSurface
    val tintColor = if (isActive) glassColors.glassAccent.copy(alpha = AlphaTokens.Hover) else Color.Transparent
    return surfaceColor to tintColor
}

private fun resolveSegmentedToggleBorderColor(
    isActive: Boolean,
    isHovered: Boolean,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme,
): Color = when {
    isActive && isHovered -> glassColors.glassBorderHighlight
    isActive -> glassColors.glassBorder
    isHovered -> glassColors.glassBorder.copy(alpha = AlphaTokens.Border)
    else -> Color.Transparent
}

private fun resolveSegmentedToggleIconTint(
    isActive: Boolean,
    isHovered: Boolean,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme,
): Color = when {
    isActive -> glassColors.glassAccent
    isHovered -> glassColors.onGlass
    else -> glassColors.onGlassMuted
}

private fun Modifier.segmentedToggleSurface(
    isHovered: Boolean,
    effectsEnabled: Boolean,
    backdrop: Backdrop?,
    shape: Shape,
    surfaceColor: Color,
    tintColor: Color,
): Modifier {
    return if (effectsEnabled && backdrop != null) {
        this.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(BackdropBlur.Subtle.toPx())
                lens(LensRefraction.Button.height.toPx(), LensRefraction.Button.amount.toPx())
            },
            highlight = { if (isHovered) Highlight.Ambient else Highlight.Plain },
            onDrawSurface = {
                if (surfaceColor != Color.Transparent) {
                    drawRect(surfaceColor)
                }
                if (tintColor != Color.Transparent) {
                    drawRect(tintColor)
                }
            }
        )
    } else {
        this.background(surfaceColor).then(
            if (tintColor != Color.Transparent) Modifier.background(tintColor) else Modifier
        )
    }
}

/**
 * Clean Liquid Glass Top Bar Icon Button with real backdrop effects on hover.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun GlassTopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
) {
    var isHovered by remember { mutableStateOf(false) }
    val glassColors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    val actualBackdrop = backdrop ?: GlassTheme.backdrop
    val buttonShape = CircleShape

    val borderColor by animateColorAsState(
        targetValue = if (isHovered) glassColors.glassBorderHighlight else glassColors.glassBorder,
        animationSpec = tween(MotionDuration.Fast)
    )

    Box(
        modifier = modifier
            .size(ComponentSize.TopBarButtonSize)
            .clip(buttonShape)
            .segmentedToggleSurface(
                isHovered = isHovered,
                effectsEnabled = effectsEnabled,
                backdrop = actualBackdrop,
                shape = buttonShape,
                surfaceColor = if (isHovered) glassColors.glassSurfaceElevated else glassColors.glassSurface,
                tintColor = Color.Transparent,
            )
            .border(width = StrokeWidth.Hairline, color = borderColor, shape = buttonShape)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
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
                role = Role.Button,
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
