/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.layout

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.text.style.TextOverflow
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.highlight.Highlight
import java.awt.Cursor
import org.ide.lti.core.designsystem.component.actions.DesktopWindowControls
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.actions.GlassIconToggleButton
import org.ide.lti.core.designsystem.component.actions.LocalWindowControlActions
import org.ide.lti.core.designsystem.component.actions.WindowControlActions
import org.ide.lti.core.designsystem.component.actions.windowDragGesture
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassEffects
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LetterSpacing
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.resolveAnimationSpec
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Navigation element configuration for the leading section of [GlassTopBar].
 */
sealed interface TopBarNavigation {
    data object None : TopBarNavigation
    data class Back(val onClick: () -> Unit) : TopBarNavigation
    data class Menu(val onClick: () -> Unit) : TopBarNavigation
    data class Brand(
        val title: String = "LtiRom",
        val workspaceName: String? = null,
        val onClick: (() -> Unit)? = null,
    ) : TopBarNavigation
    data class Custom(val content: @Composable () -> Unit) : TopBarNavigation
}

/**
 * Cohesive panel toggle configuration for the IDE layout controls in [GlassTopBar].
 */
@Immutable
data class TopBarPanelToggles(
    val isLeftVisible: Boolean = true,
    val isRightVisible: Boolean = true,
    val isBottomVisible: Boolean = false,
    val onToggleLeft: () -> Unit = {},
    val onToggleRight: () -> Unit = {},
    val onToggleBottom: () -> Unit = {},
)

/**
 * Command palette search bar configuration for [GlassTopBar].
 */
@Immutable
data class TopBarSearch(
    val placeholder: String = "Search files, symbols...",
    val shortcut: String = "Ctrl P",
    val onClick: () -> Unit = {},
)

/**
 * Professional IDE Liquid Glass Top App Bar.
 *
 * Single, unified canonical Top App Bar across the application adhering to Clean Architecture
 * and SOLID principles with type-safe sealed interfaces and cohesive data classes.
 */
@Composable
fun GlassTopBar(
    modifier: Modifier = Modifier,
    navigation: TopBarNavigation = TopBarNavigation.Brand(),
    centerContent: (@Composable () -> Unit)? = null,
    search: TopBarSearch? = TopBarSearch(),
    panelToggles: TopBarPanelToggles? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    windowControls: WindowControlActions? = null,
    showWindowControls: Boolean = true,
    shape: Shape = RectangleShape,
    backdrop: Backdrop? = null,
    drawOwnChrome: Boolean = true,
) {
    val actualBackdrop = backdrop ?: GlassTheme.backdrop
    val glassColors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    val localControls = LocalWindowControlActions.current

    val effectiveWindowControls = windowControls ?: localControls
    val effectiveDrag = effectiveWindowControls.onDragWindow
    val effectiveDragDelta = effectiveWindowControls.onDragDelta

    val backgroundModifier = if (drawOwnChrome) {
        if (effectsEnabled && actualBackdrop != null) {
            Modifier.drawBackdrop(
                backdrop = actualBackdrop,
                shape = { shape },
                effects = GlassEffects.TopBar,
                highlight = { null },
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
            .windowDragGesture(
                onDrag = effectiveDrag,
                onDragDelta = effectiveDragDelta,
            )
            .then(backgroundModifier)
            .then(borderModifier)
            .padding(horizontal = Spacing.MediumSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        CompositionLocalProvider(LocalContentColor provides glassColors.onGlass) {
            // LEADING SECTION: Navigation & Brand
            GlassTopBarLeftSection(
                navigation = navigation,
                backdrop = actualBackdrop,
            )

            // Panel layout segmented toggles if configured
            if (panelToggles != null) {
                SegmentedPanelLayoutToggle(
                    toggles = panelToggles,
                    backdrop = actualBackdrop,
                )
            }

            // CENTER SECTION: Search or custom content
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (centerContent != null) {
                    centerContent()
                } else if (search != null) {
                    GlassCommandPalettePill(
                        search = search,
                        backdrop = actualBackdrop,
                    )
                }
            }

            // TRAILING SECTION: Actions & Window Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Compact),
            ) {
                actions?.invoke(this)

                if (showWindowControls) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = Spacing.Compact)
                            .width(StrokeWidth.Standard)
                            .height(ComponentSize.TopBarDividerHeight)
                            .background(glassColors.glassBorder.copy(alpha = AlphaTokens.Faint)),
                    )

                    DesktopWindowControls(
                        onMinimize = effectiveWindowControls.onMinimize,
                        onMaximize = effectiveWindowControls.onMaximize,
                        onClose = effectiveWindowControls.onClose,
                        isMaximized = effectiveWindowControls.isMaximized(),
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassTopBarLeftSection(
    navigation: TopBarNavigation,
    backdrop: Backdrop? = null,
) {
    val glassColors = GlassTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Compact),
    ) {
        when (navigation) {
            TopBarNavigation.None -> Unit
            is TopBarNavigation.Back -> {
                GlassIconButton(
                    onClick = navigation.onClick,
                    size = ComponentSize.TopBarButtonSize,
                    backdrop = backdrop,
                ) {
                    Icon(
                        painter = AppIcons.ArrowBackPainterResource(),
                        contentDescription = "Back",
                        modifier = Modifier.size(IconSize.Small),
                    )
                }
            }
            is TopBarNavigation.Menu -> {
                GlassIconButton(
                    onClick = navigation.onClick,
                    size = ComponentSize.TopBarButtonSize,
                    backdrop = backdrop,
                ) {
                    Icon(
                        painter = AppIcons.MenuPainterResource(),
                        contentDescription = "Menu",
                        modifier = Modifier.size(IconSize.Small),
                    )
                }
            }
            is TopBarNavigation.Brand -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Compact),
                    modifier = Modifier.clickable(enabled = navigation.onClick != null) {
                        navigation.onClick?.invoke()
                    },
                ) {
                    // Branded Logo Badge with gradient and "L" glyph
                    Box(
                        modifier = Modifier
                            .size(ComponentSize.LogoBadgeSize)
                            .clip(GlassShapes.ExtraSmall)
                            .background(GlassTheme.branding.logoBadgeBrush),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "L",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = BrandColors.OnLogoBadge,
                            ),
                        )
                    }

                    Text(
                        text = navigation.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = FontSize.TitleSmall,
                            fontFamily = codeFontFamily(),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = LetterSpacing.Widest,
                        ),
                        color = glassColors.onGlass,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // Workspace / File pill indicator
                    navigation.workspaceName?.let { workspace ->
                        Text(
                            text = "/",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = FontSize.TitleSmall,
                                color = glassColors.onGlassMuted,
                            ),
                        )
                        Box(
                            modifier = Modifier
                                .clip(GlassShapes.ExtraSmall)
                                .background(glassColors.glassSurfaceElevated.copy(alpha = AlphaTokens.Elevated))
                                .glassOutlineBorder(
                                    width = StrokeWidth.Hairline,
                                    color = glassColors.glassBorder,
                                    shape = GlassShapes.ExtraSmall,
                                )
                                .padding(
                                    horizontal = Spacing.Compact,
                                    vertical = Spacing.ExtraExtraSmall,
                                ),
                        ) {
                            Text(
                                text = workspace,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = FontSize.Micro,
                                    letterSpacing = LetterSpacing.Snug,
                                ),
                                color = glassColors.onGlassSecondary,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            is TopBarNavigation.Custom -> {
                navigation.content()
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun GlassCommandPalettePill(
    search: TopBarSearch,
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
            .clickable(onClick = search.onClick)
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
            text = search.placeholder,
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
                text = search.shortcut,
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

/**
 * Segmented layout mode toggle buttons for IDE panels.
 */
/**
 * Segmented layout mode toggle buttons for IDE panels.
 */
@Composable
private fun SegmentedPanelLayoutToggle(
    toggles: TopBarPanelToggles,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
) {
    val glassColors = GlassTheme.colors
    val toggleShape = GlassShapes.Small

    Row(
        modifier = modifier
            .height(ComponentSize.SegmentedToggleHeight)
            .clip(toggleShape)
            .background(glassColors.glassSurface.copy(alpha = AlphaTokens.Subtle))
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = glassColors.glassBorder.copy(alpha = AlphaTokens.Faint),
                shape = toggleShape,
            )
            .padding(Spacing.ExtraExtraSmall),
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left Panel Toggle (File Explorer)
        GlassIconToggleButton(
            selected = toggles.isLeftVisible,
            onSelectedChange = { toggles.onToggleLeft() },
            size = ComponentSize.SegmentedButtonSize,
            shape = GlassShapes.Compact,
        ) {
            Icon(
                painter = AppIcons.leftPanelPainterResource(),
                contentDescription = "Toggle Left Panel (Files)",
                modifier = Modifier.size(IconSize.Segmented),
                tint = if (toggles.isLeftVisible) glassColors.onGlass else glassColors.onGlassMuted,
            )
        }

        // Middle Down Panel Toggle (Terminal/Output)
        GlassIconToggleButton(
            selected = toggles.isBottomVisible,
            onSelectedChange = { toggles.onToggleBottom() },
            size = ComponentSize.SegmentedButtonSize,
            shape = GlassShapes.Compact,
        ) {
            Icon(
                painter = AppIcons.bottomPanelPainterResource(),
                contentDescription = "Toggle Bottom Panel (Terminal)",
                modifier = Modifier.size(IconSize.Segmented),
                tint = if (toggles.isBottomVisible) glassColors.onGlass else glassColors.onGlassMuted,
            )
        }

        // Right Panel Toggle (AI Assistant)
        GlassIconToggleButton(
            selected = toggles.isRightVisible,
            onSelectedChange = { toggles.onToggleRight() },
            size = ComponentSize.SegmentedButtonSize,
            shape = GlassShapes.Compact,
        ) {
            Icon(
                painter = AppIcons.rightPanelPainterResource(),
                contentDescription = "Toggle Right Panel (AI Assistant)",
                modifier = Modifier.size(IconSize.Segmented),
                tint = if (toggles.isRightVisible) glassColors.onGlass else glassColors.onGlassMuted,
            )
        }
    }
}

@Preview
@Composable
private fun GlassTopBarPreview() {
    LtiTheme {
        GlassTopBar(
            navigation = TopBarNavigation.Brand(
                title = "LtiRom",
                workspaceName = "Workspace",
            ),
            panelToggles = TopBarPanelToggles(),
        )
    }
}
