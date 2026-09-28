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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.ExperimentalHazeApi
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
import org.ide.lti.core.designsystem.theme.BrandLogoGradientEnd
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.HazeShape
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LetterSpacing
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.awt.Cursor

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
@OptIn(ExperimentalHazeApi::class)
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
    shape: HazeShape = GlassShapes.HazeFlat,
    drawOwnChrome: Boolean = true,
) {
    val localControls = LocalWindowControlActions.current

    val effectiveWindowControls = windowControls ?: localControls
    val effectiveDrag = effectiveWindowControls.onDragWindow
    val effectiveDragDelta = effectiveWindowControls.onDragDelta

    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val chromeModifier = if (drawOwnChrome) {
        Modifier.ideShellSurface(
            shape = shape,
            surfaceColor = surfaceColor,
            drawBorder = false,
        )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentSize.TopBarHeight)
            .windowDragGesture(
                onDrag = effectiveDrag,
                onDragDelta = effectiveDragDelta,
            )
            .then(chromeModifier)
            .padding(horizontal = Spacing.MediumSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            // LEADING SECTION: Navigation & Brand
            GlassTopBarLeftSection(
                navigation = navigation,
            )

            // Panel layout segmented toggles if configured
            if (panelToggles != null) {
                SegmentedPanelLayoutToggle(
                    toggles = panelToggles,
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
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Faint)),
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
private fun GlassTopBarLeftSection(navigation: TopBarNavigation) {
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
                            .background(
                                Brush.linearGradient(
                                    listOf(MaterialTheme.colorScheme.primary, BrandLogoGradientEnd),
                                ),
                            ),
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
                        color = MaterialTheme.colorScheme.onSurface,
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                        Box(
                            modifier = Modifier
                                .clip(GlassShapes.ExtraSmall)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.Elevated),
                                )
                                .glassOutlineBorder(
                                    width = StrokeWidth.Hairline,
                                    color = MaterialTheme.colorScheme.outline,
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun GlassCommandPalettePill(search: TopBarSearch, modifier: Modifier = Modifier) {
    var isHovered by remember { mutableStateOf(false) }

    val borderColor = if (isHovered) {
        MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Medium)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Faint)
    }
    val surfaceColor = if (isHovered) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.Hover)
    } else {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AlphaTokens.Subtle)
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
            tint = if (isHovered) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.Small),
        )

        Text(
            text = search.placeholder,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = FontSize.Chip),
            color = if (isHovered) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )

        Box(
            modifier = Modifier
                .clip(GlassShapes.ExtraSmall)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.Elevated))
                .glassOutlineBorder(
                    width = StrokeWidth.Hairline,
                    color = if (isHovered) {
                        MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Medium)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Hover)
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
                color = if (isHovered) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * Segmented layout mode toggle buttons for IDE panels.
 */
@Composable
private fun SegmentedPanelLayoutToggle(toggles: TopBarPanelToggles, modifier: Modifier = Modifier) {
    val toggleShape = GlassShapes.Small

    Row(
        modifier = modifier
            .height(ComponentSize.SegmentedToggleHeight)
            .clip(toggleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AlphaTokens.Subtle))
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Faint),
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
            shape = GlassShapes.ShellControl,
        ) {
            Icon(
                painter = AppIcons.leftPanelPainterResource(),
                contentDescription = "Toggle Left Panel (Files)",
                modifier = Modifier.size(IconSize.Segmented),
                tint = if (toggles.isLeftVisible) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        // Middle Down Panel Toggle (Terminal/Output)
        GlassIconToggleButton(
            selected = toggles.isBottomVisible,
            onSelectedChange = { toggles.onToggleBottom() },
            size = ComponentSize.SegmentedButtonSize,
            shape = GlassShapes.ShellControl,
        ) {
            Icon(
                painter = AppIcons.bottomPanelPainterResource(),
                contentDescription = "Toggle Bottom Panel (Terminal)",
                modifier = Modifier.size(IconSize.Segmented),
                tint = if (toggles.isBottomVisible) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        // Right Panel Toggle (AI Assistant)
        GlassIconToggleButton(
            selected = toggles.isRightVisible,
            onSelectedChange = { toggles.onToggleRight() },
            size = ComponentSize.SegmentedButtonSize,
            shape = GlassShapes.ShellControl,
        ) {
            Icon(
                painter = AppIcons.rightPanelPainterResource(),
                contentDescription = "Toggle Right Panel (AI Assistant)",
                modifier = Modifier.size(IconSize.Segmented),
                tint = if (toggles.isRightVisible) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
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
