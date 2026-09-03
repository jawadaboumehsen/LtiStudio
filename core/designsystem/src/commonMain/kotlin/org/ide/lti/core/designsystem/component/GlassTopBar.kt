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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import java.awt.Cursor
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassEffects
import org.ide.lti.core.designsystem.theme.GlassHighlights
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LensRefraction
import org.ide.lti.core.designsystem.theme.LetterSpacing
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Professional IDE Liquid Glass Top App Bar.
 *
 * Single, unified canonical Top App Bar across the application.
 * Implements Apple-inspired Liquid Glass effects using the backdrop engine:
 * - Brand logo badge or custom navigation icon / back arrow.
 * - Workspace / file breadcrumb pill indicator.
 * - Centered Command Palette or custom center content slot.
 * - Segmented Liquid Glass panel layout toggles (Explorer, Terminal, AI Assistant).
 * - Standard actions (Search, Notifications, Settings) or custom actions slot.
 * - Platform window management controls with smooth hover responses.
 * - Embedded (`drawOwnChrome = false`) or standalone window chrome support.
 */
@Composable
fun GlassTopBar(
    modifier: Modifier = Modifier,
    title: String = "LtiRom",
    workspaceName: String? = "Workspace",
    navigationIcon: (@Composable () -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    showMenuButton: Boolean = (onBackClick == null && onMenuClick != null),
    centerContent: (@Composable () -> Unit)? = null,
    showCommandPalette: Boolean = true,
    onSearchClick: () -> Unit = {},
    showPanelToggles: Boolean = true,
    isLeftPanelVisible: Boolean = true,
    isRightPanelVisible: Boolean = true,
    isMiddleDownPanelVisible: Boolean = true,
    onClickLeftPanelIcon: () -> Unit = {},
    onClickMiddleDownPanelIcon: () -> Unit = {},
    onClickRightPanelIcon: () -> Unit = {},
    actions: (@Composable () -> Unit)? = null,
    showSearchButton: Boolean = false,
    onNotificationsClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    showWindowControls: Boolean = true,
    onWindowMinimize: (() -> Unit)? = null,
    onMinimize: (() -> Unit)? = null,
    onWindowMaximize: (() -> Unit)? = null,
    onMaximize: (() -> Unit)? = null,
    onWindowClose: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onWindowDrag: (() -> Unit)? = null,
    onDrag: (() -> Unit)? = null,
    isMaximized: Boolean? = null,
    shape: Shape = RectangleShape,
    backdrop: Backdrop? = null,
    drawOwnChrome: Boolean = true,
) {
    val actualBackdrop = backdrop ?: GlassTheme.backdrop
    val glassColors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    val localControls = LocalWindowControlActions.current

    val effectiveMinimize = onWindowMinimize ?: onMinimize ?: localControls.onMinimize
    val effectiveMaximize = onWindowMaximize ?: onMaximize ?: localControls.onMaximize
    val effectiveClose = onWindowClose ?: onClose ?: localControls.onClose
    val effectiveDrag = onWindowDrag ?: onDrag ?: localControls.onDragWindow
    val effectiveIsMaximized = isMaximized ?: localControls.isMaximized()

    val backgroundModifier = if (drawOwnChrome) {
        if (effectsEnabled && actualBackdrop != null) {
            Modifier.drawBackdrop(
                backdrop = actualBackdrop,
                shape = { shape },
                effects = GlassEffects.TopBar,
                highlight = GlassHighlights.None,
                onDrawSurface = {
                    drawRect(glassColors.chromeSurface)
                },
            )
        } else {
            Modifier.background(glassColors.chromeSurface, shape)
        }
    } else {
        Modifier
    }

    val borderModifier = Modifier.drawBehind {
        // Uniform bottom specular hairline gradient separating top bar from window content
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
            .height(ComponentSize.TopBarHeight)
            .windowDragGesture(effectiveDrag)
            .then(backgroundModifier)
            .then(borderModifier)
            .padding(horizontal = Spacing.MediumSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        GlassTopBarLeftSection(
            title = title,
            workspaceName = workspaceName,
            navigationIcon = navigationIcon,
            onBackClick = onBackClick,
            onMenuClick = onMenuClick,
            showMenuButton = showMenuButton,
            showPanelToggles = showPanelToggles,
            isLeftPanelVisible = isLeftPanelVisible,
            isMiddleDownPanelVisible = isMiddleDownPanelVisible,
            isRightPanelVisible = isRightPanelVisible,
            onClickLeftPanelIcon = onClickLeftPanelIcon,
            onClickMiddleDownPanelIcon = onClickMiddleDownPanelIcon,
            onClickRightPanelIcon = onClickRightPanelIcon,
            backdrop = actualBackdrop,
        )

        Spacer(modifier = Modifier.weight(1f))

        if (centerContent != null) {
            centerContent()
        } else if (showCommandPalette) {
            GlassCommandPalettePill(
                onClick = onSearchClick,
                backdrop = actualBackdrop,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        GlassTopBarRightSection(
            actions = actions,
            onSearchClick = onSearchClick,
            onNotificationsClick = onNotificationsClick,
            onSettingsClick = onSettingsClick,
            showSearchButton = showSearchButton,
            showWindowControls = showWindowControls,
            effectiveMinimize = effectiveMinimize,
            effectiveMaximize = effectiveMaximize,
            effectiveClose = effectiveClose,
            effectiveIsMaximized = effectiveIsMaximized,
            backdrop = actualBackdrop,
        )
    }
}

/**
 * Modern Command Palette / Quick Search Pill centered in the top bar.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun GlassCommandPalettePill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
) {
    val glassColors = GlassTheme.colors
    var isHovered by remember { mutableStateOf(false) }

    val borderColor = if (isHovered) {
        glassColors.glassBorderHighlight.copy(alpha = AlphaTokens.Medium)
    } else {
        glassColors.glassBorder.copy(alpha = AlphaTokens.Faint)
    }
    val surfaceColor = if (isHovered) {
        glassColors.glassSurfaceElevated.copy(alpha = AlphaTokens.Hover)
    } else {
        glassColors.glassSurface.copy(alpha = AlphaTokens.Subtle)
    }

    Row(
        modifier = modifier
            .widthIn(
                min = ComponentSize.CommandPaletteMinWidth,
                max = ComponentSize.CommandPaletteMaxWidth,
            )
            .height(ComponentSize.CommandPaletteHeight)
            .clip(GlassShapes.Pill)
            .background(surfaceColor)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = borderColor,
                shape = GlassShapes.Pill,
            )
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .padding(horizontal = Spacing.SmallMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Icon(
            painter = AppIcons.SearchPainterResource(),
            contentDescription = null,
            tint = if (isHovered) glassColors.onGlass else glassColors.onGlassMuted,
            modifier = Modifier.size(IconSize.Small),
        )

        Text(
            text = "Search files, symbols...",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = FontSize.Chip),
            color = if (isHovered) glassColors.onGlassSecondary else glassColors.onGlassMuted,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )

        Box(
            modifier = Modifier
                .clip(GlassShapes.ExtraSmall)
                .background(glassColors.glassSurfaceElevated.copy(alpha = AlphaTokens.Elevated))
                .glassOutlineBorder(
                    width = StrokeWidth.Hairline,
                    color = if (isHovered) {
                        glassColors.glassBorderHighlight.copy(alpha = AlphaTokens.Medium)
                    } else {
                        glassColors.glassBorderHighlight.copy(alpha = AlphaTokens.Hover)
                    },
                    shape = GlassShapes.ExtraSmall,
                )
                .padding(
                    horizontal = Spacing.Compact,
                    vertical = Spacing.ExtraExtraSmall,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Ctrl P",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = FontSize.Micro,
                    fontFamily = codeFontFamily(),
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = LetterSpacing.Loose,
                ),
                color = if (isHovered) glassColors.onGlass else glassColors.onGlassSecondary,
            )
        }
    }
}

@Composable
private fun GlassTopBarLeftSection(
    title: String,
    workspaceName: String?,
    navigationIcon: (@Composable () -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    showMenuButton: Boolean = true,
    showPanelToggles: Boolean = true,
    isLeftPanelVisible: Boolean,
    isMiddleDownPanelVisible: Boolean,
    isRightPanelVisible: Boolean,
    onClickLeftPanelIcon: () -> Unit,
    onClickMiddleDownPanelIcon: () -> Unit,
    onClickRightPanelIcon: () -> Unit,
    backdrop: Backdrop? = null,
) {
    val glassColors = GlassTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Compact),
    ) {
        if (navigationIcon != null) {
            navigationIcon()
        } else if (onBackClick != null) {
            GlassIconButton(
                onClick = onBackClick,
                size = ComponentSize.TopBarButtonSize,
                backdrop = backdrop,
            ) {
                Icon(
                    painter = AppIcons.ArrowBackPainterResource(),
                    contentDescription = "Back",
                    modifier = Modifier.size(IconSize.Medium),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(ComponentSize.LogoBadgeSize)
                    .clip(GlassShapes.Small)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                glassColors.glassAccent,
                                BrandColors.LogoGradientEnd,
                            ),
                        ),
                    )
                    .glassOutlineBorder(
                        width = StrokeWidth.Hairline,
                        color = glassColors.glassBorderHighlight,
                        shape = GlassShapes.Small,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "L",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = glassColors.onGlass,
                )
            }
        }

        if (showMenuButton && onMenuClick != null) {
            GlassIconButton(
                onClick = onMenuClick,
                size = ComponentSize.TopBarButtonSize,
                backdrop = backdrop,
            ) {
                Icon(
                    painter = AppIcons.MenuPainterResource(),
                    contentDescription = "Main Menu",
                    modifier = Modifier.size(IconSize.Medium),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            modifier = Modifier.padding(start = Spacing.ExtraSmall),
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
                        .clip(GlassShapes.Compact)
                        .background(glassColors.glassAccent.copy(alpha = AlphaTokens.Hover))
                        .glassOutlineBorder(
                            width = StrokeWidth.Hairline,
                            color = glassColors.glassBorderHighlight.copy(alpha = AlphaTokens.Prominent),
                            shape = GlassShapes.Compact,
                        )
                        .padding(horizontal = Spacing.Compact, vertical = Spacing.ExtraExtraSmall),
                ) {
                    Text(
                        text = workspaceName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = glassColors.onGlass,
                    )
                }
            }
        }

        if (showPanelToggles) {
            Spacer(modifier = Modifier.width(Spacing.SmallMedium))
            SegmentedPanelLayoutToggle(
                isLeftPanelVisible = isLeftPanelVisible,
                isMiddleDownPanelVisible = isMiddleDownPanelVisible,
                isRightPanelVisible = isRightPanelVisible,
                onClickLeftPanelIcon = onClickLeftPanelIcon,
                onClickMiddleDownPanelIcon = onClickMiddleDownPanelIcon,
                onClickRightPanelIcon = onClickRightPanelIcon,
                backdrop = backdrop,
            )
        }
    }
}

@Composable
private fun GlassTopBarRightSection(
    actions: (@Composable () -> Unit)? = null,
    onSearchClick: (() -> Unit)? = null,
    onNotificationsClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    showSearchButton: Boolean = true,
    showWindowControls: Boolean = true,
    effectiveMinimize: () -> Unit,
    effectiveMaximize: () -> Unit,
    effectiveClose: () -> Unit,
    effectiveIsMaximized: Boolean = false,
    backdrop: Backdrop? = null,
) {
    val glassColors = GlassTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        modifier = Modifier.padding(end = Spacing.Compact),
    ) {
        if (actions != null) {
            actions()
        } else {
            if (showSearchButton && onSearchClick != null) {
                GlassIconButton(
                    onClick = onSearchClick,
                    size = ComponentSize.TopBarButtonSize,
                    backdrop = backdrop,
                ) {
                    Icon(
                        painter = AppIcons.SearchPainterResource(),
                        contentDescription = "Search",
                        modifier = Modifier.size(IconSize.Medium),
                    )
                }
            }

            if (onNotificationsClick != null) {
                GlassIconButton(
                    onClick = onNotificationsClick,
                    size = ComponentSize.TopBarButtonSize,
                    backdrop = backdrop,
                ) {
                    Icon(
                        painter = AppIcons.NotificationsPainterResource(),
                        contentDescription = "Notifications",
                        modifier = Modifier.size(IconSize.Medium),
                    )
                }
            }

            if (onSettingsClick != null) {
                GlassIconButton(
                    onClick = onSettingsClick,
                    size = ComponentSize.TopBarButtonSize,
                    backdrop = backdrop,
                ) {
                    Icon(
                        painter = AppIcons.SettingsPainterResource(),
                        contentDescription = "Settings",
                        modifier = Modifier.size(IconSize.Medium),
                    )
                }
            }
        }

        if (showWindowControls) {
            Box(
                modifier = Modifier
                    .padding(horizontal = Spacing.Compact)
                    .width(StrokeWidth.Standard)
                    .height(ComponentSize.TopBarDividerHeight)
                    .background(glassColors.glassBorder.copy(alpha = AlphaTokens.Faint)),
            )

            DesktopWindowControls(
                onMinimize = effectiveMinimize,
                onMaximize = effectiveMaximize,
                onClose = effectiveClose,
                isMaximized = effectiveIsMaximized,
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
    val toggleShape = GlassShapes.Small

    Row(
        modifier = modifier
            .height(ComponentSize.SegmentedToggleHeight)
            .glassEffect(
                backdrop = backdrop,
                shape = toggleShape,
                effects = GlassEffects.Toggle,
            )
            .glassBorder(
                width = StrokeWidth.Hairline,
                shape = toggleShape,
            )
            .padding(Spacing.ExtraExtraSmall),
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left Panel Toggle (File Explorer)
        GlassIconToggleButton(
            selected = isLeftPanelVisible,
            onSelectedChange = { onClickLeftPanelIcon() },
            size = ComponentSize.SegmentedButtonSize,
            shape = GlassShapes.Compact,
        ) {
            Icon(
                painter = AppIcons.leftPanelPainterResource(),
                contentDescription = "Toggle Left Panel (Files)",
                modifier = Modifier.size(IconSize.Segmented),
                tint = if (isLeftPanelVisible) glassColors.glassAccent else glassColors.onGlassMuted,
            )
        }

        // Middle Down Panel Toggle (Terminal/Output)
        GlassIconToggleButton(
            selected = isMiddleDownPanelVisible,
            onSelectedChange = { onClickMiddleDownPanelIcon() },
            size = ComponentSize.SegmentedButtonSize,
            shape = GlassShapes.Compact,
        ) {
            Icon(
                painter = AppIcons.bottomPanelPainterResource(),
                contentDescription = "Toggle Bottom Panel (Terminal)",
                modifier = Modifier.size(IconSize.Segmented),
                tint = if (isMiddleDownPanelVisible) glassColors.glassAccent else glassColors.onGlassMuted,
            )
        }

        // Right Panel Toggle (AI Assistant)
        GlassIconToggleButton(
            selected = isRightPanelVisible,
            onSelectedChange = { onClickRightPanelIcon() },
            size = ComponentSize.SegmentedButtonSize,
            shape = GlassShapes.Compact,
        ) {
            Icon(
                painter = AppIcons.rightPanelPainterResource(),
                contentDescription = "Toggle Right Panel (AI Assistant)",
                modifier = Modifier.size(IconSize.Segmented),
                tint = if (isRightPanelVisible) glassColors.glassAccent else glassColors.onGlassMuted,
            )
        }
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
