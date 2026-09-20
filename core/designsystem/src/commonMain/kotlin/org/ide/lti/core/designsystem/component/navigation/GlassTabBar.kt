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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.highlight.Highlight
import org.ide.lti.core.designsystem.component.actions.*
import org.ide.lti.core.designsystem.component.devtools.*
import org.ide.lti.core.designsystem.component.display.*
import org.ide.lti.core.designsystem.component.feedback.*
import org.ide.lti.core.designsystem.component.inputs.*
import org.ide.lti.core.designsystem.component.layout.*
import org.ide.lti.core.designsystem.component.primitives.*
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassColorScheme
import org.ide.lti.core.designsystem.theme.GlassEffects
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GlassTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = GlassTheme.colors.glassAccent.copy(alpha = AlphaTokens.PrimaryButtonTint),
    unselectedColor: Color = GlassTheme.colors.glassSurfaceElevated,
    backdrop: Backdrop? = null,
) {
    var isHovered by remember { mutableStateOf(false) }
    val glassColors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    val actualBackdrop = backdrop ?: GlassTheme.backdrop
    val tabShape = GlassShapes.Capsule

    val surfaceColor = when {
        selected -> selectedColor
        isHovered -> glassColors.glassSurfaceElevated
        else -> unselectedColor
    }
    val tintColor = if (selected) {
        glassColors.glassAccent.copy(alpha = AlphaTokens.Hover)
    } else {
        Color.Transparent
    }
    val contentColor = when {
        selected -> glassColors.onGlass
        isHovered -> glassColors.onGlassSecondary
        else -> glassColors.onGlassMuted
    }

    val borderColor = when {
        selected && isHovered -> glassColors.glassBorderHighlight
        selected -> glassColors.glassBorder
        isHovered -> glassColors.glassBorder.copy(alpha = AlphaTokens.Border)
        else -> glassColors.glassBorder.copy(alpha = AlphaTokens.Disabled)
    }

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
        selectedContentColor = glassColors.onGlass,
        unselectedContentColor = glassColors.onGlassMuted,
        modifier = modifier
            .clip(tabShape)
            .tabGlassSurface(
                isHovered = isHovered,
                effectsEnabled = effectsEnabled,
                backdrop = actualBackdrop,
                shape = tabShape,
                surfaceColor = surfaceColor,
                tintColor = tintColor,
            )
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
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GlassTabItem(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    backdrop: Backdrop? = null,
) {
    var isHovered by remember { mutableStateOf(false) }
    val glassColors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    val actualBackdrop = backdrop ?: GlassTheme.backdrop
    val tabShape = GlassShapes.Small
    val tabColors = resolveTabItemColors(selected, isHovered, glassColors)

    Row(
        modifier = modifier
            .height(ComponentSize.TabItemHeight)
            .clip(tabShape)
            .tabGlassSurface(
                isHovered = isHovered,
                effectsEnabled = effectsEnabled,
                backdrop = actualBackdrop,
                shape = tabShape,
                surfaceColor = tabColors.surfaceColor,
                tintColor = tabColors.tintColor,
            )
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
                                    glassColors.glassBorderHighlight.copy(alpha = AlphaTokens.Prominent),
                                    glassColors.glassHighlight.copy(alpha = AlphaTokens.Medium),
                                    glassColors.glassBorderHighlight.copy(alpha = AlphaTokens.Glow),
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
                shape = GlassShapes.ExtraSmall,
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

private fun resolveTabItemColors(
    selected: Boolean,
    isHovered: Boolean,
    glassColors: GlassColorScheme,
): TabItemColors {
    val surfaceColor = if (selected || isHovered) glassColors.glassSurfaceElevated else Color.Transparent
    val tintColor = if (selected) glassColors.glassAccent.copy(alpha = AlphaTokens.Hover) else Color.Transparent
    val textColor = when {
        selected -> glassColors.onGlass
        isHovered -> glassColors.onGlassSecondary
        else -> glassColors.onGlassMuted
    }
    val borderColor = when {
        selected && isHovered -> glassColors.glassBorderHighlight
        selected -> glassColors.glassBorder
        isHovered -> glassColors.glassBorder.copy(alpha = AlphaTokens.Border)
        else -> Color.Transparent
    }
    return TabItemColors(surfaceColor, tintColor, textColor, borderColor)
}

/**
 * Close and modification behavior for an individual [GlassTab].
 */
sealed interface TabCloseBehavior {
    data object Unclosable : TabCloseBehavior
    data class Closable(
        val isDirty: Boolean = false,
        val onClose: () -> Unit,
    ) : TabCloseBehavior
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
    backdrop: Backdrop? = null,
) {
    val actualBackdrop = backdrop ?: GlassTheme.backdrop
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
                    backdrop = actualBackdrop,
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
    backdrop: Backdrop? = null,
) {
    val actualBackdrop = backdrop ?: GlassTheme.backdrop
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
                    backdrop = actualBackdrop,
                )
            }
        }
        actions?.invoke()
    }
}

private fun Modifier.tabGlassSurface(
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
            effects = GlassEffects.Button,
            highlight = { if (isHovered) Highlight.Ambient else Highlight.Plain },
            onDrawSurface = {
                if (surfaceColor.isSpecified && surfaceColor != Color.Transparent) {
                    drawRect(surfaceColor.copy(alpha = surfaceColor.alpha * AlphaTokens.ButtonFill))
                }
                if (tintColor.isSpecified && tintColor != Color.Transparent) {
                    drawRect(tintColor)
                }
            },
        )
    } else {
        this.drawBehind {
            if (surfaceColor.isSpecified && surfaceColor != Color.Transparent) {
                drawRect(surfaceColor.copy(alpha = surfaceColor.alpha * AlphaTokens.ButtonFill))
            }
            if (tintColor.isSpecified && tintColor != Color.Transparent) {
                drawRect(tintColor)
            }
        }
    }
}
