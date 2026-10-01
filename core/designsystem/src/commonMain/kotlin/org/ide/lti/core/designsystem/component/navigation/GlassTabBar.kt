/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.hazeGlass
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassMaterialStyles
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.HazeShape
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.sceneHazeInput

@OptIn(ExperimentalComposeUiApi::class, ExperimentalHazeApi::class)
@Composable
fun GlassTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.PrimaryButtonTint),
    unselectedColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    var isHovered by remember { mutableStateOf(false) }
    val tabShape = GlassShapes.HazeCapsule
    val effectsEnabled = GlassTheme.effectsEnabled
    val colors = MaterialTheme.colorScheme

    val surfaceColor = tabSurfaceColor(
        selected = selected,
        isHovered = isHovered,
        selectedColor = selectedColor,
        unselectedColor = unselectedColor,
        solidContainer = colors.primaryContainer,
        hoverContainer = colors.surfaceContainerHighest,
        effectsEnabled = effectsEnabled,
    )
    val tintColor = if (selected && effectsEnabled) {
        colors.primary.copy(alpha = AlphaTokens.Hover)
    } else {
        Color.Transparent
    }
    val contentColor = tabTextColor(
        selected = selected,
        onSurface = colors.onSurface,
        onSurfaceVariant = colors.onSurfaceVariant,
        onPrimaryContainer = colors.onPrimaryContainer,
        effectsEnabled = effectsEnabled,
    )
    val defaultBorder = if (effectsEnabled) {
        colors.outline.copy(alpha = AlphaTokens.Disabled)
    } else {
        colors.outlineVariant
    }
    val borderColor = tabBorderColor(
        selected = selected,
        isHovered = isHovered,
        primaryColor = colors.primary,
        outlineColor = colors.outline,
        hoverOutline = colors.outline.copy(alpha = AlphaTokens.Border),
        defaultBorder = defaultBorder,
        effectsEnabled = effectsEnabled,
    )

    Tab(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        },
        selected = selected,
        onClick = onClick,
        selectedContentColor = MaterialTheme.colorScheme.onSurface,
        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .tabGlassSurface(tabShape, surfaceColor, tintColor)
            .clip(tabShape)
            .glassOutlineBorder(
                color = borderColor,
                width = StrokeWidth.Hairline,
                shape = tabShape,
            )
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .padding(horizontal = Spacing.TabHorizontal),
    )
}

/**
 * Tab item for panel tab bars.
 * Displays a single tab with optional close action and real glass backdrop.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalHazeApi::class)
@Composable
fun GlassTabItem(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    var isHovered by remember { mutableStateOf(false) }
    val tabShape = GlassShapes.HazeSmall
    val tabColors = resolveTabItemColors(selected, isHovered)
    val selectedUnderlineColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .height(ComponentSize.TabItemHeight)
            .tabGlassSurface(tabShape, tabColors.surfaceColor, tabColors.tintColor)
            .clip(tabShape)
            .glassOutlineBorder(
                color = tabColors.borderColor,
                width = StrokeWidth.Hairline,
                shape = tabShape,
            )
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onSelect,
            )
            .then(
                if (selected) {
                    Modifier.drawBehind {
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    selectedUnderlineColor.copy(alpha = AlphaTokens.Prominent),
                                    selectedUnderlineColor.copy(alpha = AlphaTokens.Medium),
                                    selectedUnderlineColor.copy(alpha = AlphaTokens.Glow),
                                    Color.Transparent,
                                ),
                                startX = 0f,
                                endX = size.width,
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = StrokeWidth.Hairline.toPx(),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = tabColors.textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        if (onClose != null) {
            GlassIconButton(
                onClick = onClose,
                size = IconSize.Medium,
                shape = GlassShapes.HazeExtraSmall,
            ) {
                Icon(
                    painter = AppIcons.ClosePainterResource(),
                    contentDescription = "Close tab",
                    modifier = Modifier.size(IconSize.Small),
                )
            }
        }
    }
}

private class TabItemColors(
    val surfaceColor: Color,
    val tintColor: Color,
    val textColor: Color,
    val borderColor: Color,
)

@Composable
private fun resolveTabItemColors(selected: Boolean, isHovered: Boolean): TabItemColors {
    val effectsEnabled = GlassTheme.effectsEnabled
    val colors = MaterialTheme.colorScheme

    val surfaceColor = tabSurfaceColor(
        selected = selected,
        isHovered = isHovered,
        selectedColor = colors.surfaceContainerHigh,
        unselectedColor = Color.Transparent,
        solidContainer = colors.primaryContainer,
        hoverContainer = colors.surfaceContainerHigh,
        effectsEnabled = effectsEnabled,
    )
    val tintColor = if (selected && effectsEnabled) {
        colors.primary.copy(alpha = AlphaTokens.Hover)
    } else {
        Color.Transparent
    }
    val textColor = tabTextColor(
        selected = selected,
        onSurface = colors.onSurface,
        onSurfaceVariant = colors.onSurfaceVariant,
        onPrimaryContainer = colors.onPrimaryContainer,
        effectsEnabled = effectsEnabled,
    )
    val borderColor = tabBorderColor(
        selected = selected,
        isHovered = isHovered,
        primaryColor = colors.primary,
        outlineColor = colors.outline,
        hoverOutline = colors.outline.copy(alpha = AlphaTokens.Border),
        defaultBorder = Color.Transparent,
        effectsEnabled = effectsEnabled,
    )
    return TabItemColors(surfaceColor, tintColor, textColor, borderColor)
}

private fun tabSurfaceColor(
    selected: Boolean,
    isHovered: Boolean,
    selectedColor: Color,
    unselectedColor: Color,
    solidContainer: Color,
    hoverContainer: Color,
    effectsEnabled: Boolean,
): Color = when {
    !effectsEnabled && selected -> solidContainer
    !effectsEnabled && isHovered -> hoverContainer
    !effectsEnabled -> unselectedColor
    selected -> selectedColor
    isHovered -> hoverContainer
    else -> unselectedColor
}

private fun tabBorderColor(
    selected: Boolean,
    isHovered: Boolean,
    primaryColor: Color,
    outlineColor: Color,
    hoverOutline: Color,
    defaultBorder: Color,
    effectsEnabled: Boolean,
): Color = when {
    !effectsEnabled && (selected || isHovered) -> primaryColor
    !effectsEnabled -> defaultBorder
    selected && isHovered -> primaryColor
    selected -> outlineColor
    isHovered -> hoverOutline
    else -> defaultBorder
}

private fun tabTextColor(
    selected: Boolean,
    onSurface: Color,
    onSurfaceVariant: Color,
    onPrimaryContainer: Color,
    effectsEnabled: Boolean,
): Color = when {
    selected && !effectsEnabled -> onPrimaryContainer
    selected -> onSurface
    else -> onSurfaceVariant
}

/**
 * Close and modification behavior for an individual [GlassTab].
 */
sealed interface TabCloseBehavior {
    data object Unclosable : TabCloseBehavior
    data class Closable(val isDirty: Boolean = false, val onClose: () -> Unit) : TabCloseBehavior
}

/**
 * Specification for an individual tab item in [GlassTabBar].
 */
@Immutable
data class GlassTab(
    val id: String,
    val title: String,
    val icon: AppIcon? = null,
    val closeBehavior: TabCloseBehavior = TabCloseBehavior.Unclosable,
)

/**
 * Tab bar for panel content with strongly typed [GlassTab] models.
 */
@Composable
fun GlassTabBar(
    tabs: List<GlassTab>,
    selectedId: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .height(ComponentSize.TabBarHeight)
            .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            tabs.forEach { tab ->
                val isSelected = tab.id == selectedId
                val closeAction = when (val cb = tab.closeBehavior) {
                    is TabCloseBehavior.Closable -> cb.onClose
                    TabCloseBehavior.Unclosable -> null
                }
                GlassTabItem(
                    title = tab.title,
                    selected = isSelected,
                    onSelect = { onTabSelected(tab.id) },
                    onClose = closeAction,
                )
            }
        }
        actions?.invoke()
    }
}

/**
 * Tab bar for panel content with scrollable tabs and optional actions slot.
 */
@Composable
fun GlassTabBar(
    tabs: List<String>,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedIndex: Int = 0,
    onTabClosed: ((Int) -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .height(ComponentSize.TabBarHeight)
            .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            tabs.forEachIndexed { index, title ->
                GlassTabItem(
                    title = title,
                    selected = index == selectedIndex,
                    onSelect = { onTabSelected(index) },
                    onClose = onTabClosed?.let { { it(index) } },
                )
            }
        }
        actions?.invoke()
    }
}

@OptIn(ExperimentalHazeApi::class)
@Composable
private fun Modifier.tabGlassSurface(shape: HazeShape, surfaceColor: Color, tintColor: Color): Modifier {
    val theme = GlassTheme.appTheme
    return if (GlassTheme.effectsEnabled) {
        this.hazeGlass(
            input = sceneHazeInput(GlassTheme.hazeState),
            style = remember(theme, shape, tintColor) {
                GlassMaterialStyles.baseStyle(
                    theme = theme,
                    opticalTint = tintColor,
                ).then {
                    this.shape(shape)
                }
            },
        )
    } else {
        this.background(surfaceColor, shape = shape)
    }
}
