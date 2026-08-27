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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import com.kyant.capsule.ContinuousCapsule
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassColorScheme
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LensRefraction
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GlassTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = GlassTheme.colors.glassAccent,
    unselectedColor: Color = GlassTheme.colors.glassSurfaceElevated,
    backdrop: Backdrop? = null,
) {
    var isHovered by remember { mutableStateOf(false) }
    val glassColors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    val actualBackdrop = backdrop ?: GlassTheme.backdrop
    val tabShape = ContinuousCapsule

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
            .border(
                width = StrokeWidth.Hairline,
                color = borderColor,
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
    val tabShape = RoundedCornerShape(CornerRadius.Small)
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
            .border(
                width = StrokeWidth.Hairline,
                color = tabColors.borderColor,
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
            GlassTabCloseButton(
                selected = selected,
                onClose = onClose,
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun GlassTabCloseButton(
    selected: Boolean,
    onClose: () -> Unit,
) {
    var isCloseHovered by remember { mutableStateOf(false) }
    val glassColors = GlassTheme.colors
    val closeIconTint = when {
        isCloseHovered -> glassColors.onGlass
        selected -> glassColors.onGlassSecondary
        else -> glassColors.onGlassMuted
    }

    Box(
        modifier = Modifier
            .size(IconSize.Medium)
            .clip(RoundedCornerShape(CornerRadius.ExtraSmall))
            .then(
                if (isCloseHovered) {
                    Modifier.background(glassColors.glassSurfaceElevated)
                } else {
                    Modifier
                }
            )
            .onPointerEvent(PointerEventType.Enter) { isCloseHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isCloseHovered = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close tab",
            modifier = Modifier.size(IconSize.Small),
            tint = closeIconTint,
        )
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
 * Tab bar for panel content with scrollable tabs and optional actions slot.
 */
@Composable
fun GlassTabBar(
    tabs: List<String>,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedIndex: Int = 0,
    selectedTabIndex: Int = selectedIndex,
    onTabClosed: ((Int) -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    backdrop: Backdrop? = null,
) {
    val actualBackdrop = backdrop ?: GlassTheme.backdrop
    val activeIndex = if (selectedIndex != 0) selectedIndex else selectedTabIndex
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
                    selected = index == activeIndex,
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
            effects = {
                vibrancy()
                blur(BackdropBlur.Button.toPx())
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
