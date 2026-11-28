/*
 * Copyright 2026 Mifos Initiative
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
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.Elevation
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassLayoutColors
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.InteractionColors
import org.ide.lti.core.designsystem.theme.LetterSpacing
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

private val LocalMacOsMenuSelectionGutter = compositionLocalOf { true }

@Immutable
private data class MenuChromeColors(val container: Color, val border: Color, val specularTop: Color)

@Immutable
private data class MenuItemVisuals(
    val background: Color,
    val foreground: Color,
    val subtitle: Color,
    val checkmark: Color,
    val border: Color,
)

/**
 * macOS-styled Liquid Glass dropdown / context menu wrapping Popup positioning with frosted
 * popover styling, inset accent-pill row highlights, leading selection checkmark gutter, and
 * subtle horizontal dividers. Use with [GlassDropdownMenuHeader], [GlassDropdownMenuItem],
 * [GlassDropdownSubMenuItem], and [GlassDropdownMenuDivider].
 */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: IntOffset = IntOffset.Zero,
    showSelectionGutter: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val dpOffset = with(density) { DpOffset(offset.x.toDp(), offset.y.toDp()) }
    val shadowPx = with(density) { Elevation.Panel.toPx() }
    val colors = MaterialTheme.colorScheme
    val chrome = resolveMenuChromeColors(
        appTheme = GlassTheme.appTheme,
        effectsEnabled = GlassTheme.effectsEnabled,
        colors = colors,
    )
    val menuShape = GlassShapes.Medium

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = dpOffset,
        containerColor = Color.Transparent,
        shape = menuShape,
        tonalElevation = Elevation.None,
        shadowElevation = Elevation.None,
        border = null,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides colors.onSurface,
            LocalMacOsMenuSelectionGutter provides showSelectionGutter,
        ) {
            Box(
                modifier = Modifier
                    .padding(Spacing.ExtraSmall)
                    .graphicsLayer {
                        shadowElevation = shadowPx
                        shape = menuShape
                        clip = false
                        spotShadowColor = GlassLayoutColors.PanelShadow
                        ambientShadowColor = GlassLayoutColors.PanelShadow
                    }
                    .clip(menuShape)
                    .background(chrome.container, menuShape)
                    .drawWithCache {
                        val brush = Brush.verticalGradient(
                            colors = listOf(chrome.specularTop, Color.Transparent),
                            startY = 0f,
                            endY = size.height.coerceAtLeast(1f),
                        )
                        onDrawWithContent {
                            if (chrome.specularTop.alpha > 0f) drawRect(brush = brush)
                            drawContent()
                        }
                    }
                    .glassOutlineBorder(
                        width = StrokeWidth.Hairline,
                        color = chrome.border,
                        shape = menuShape,
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = ComponentSize.DropdownMenuMinWidth)
                        .width(IntrinsicSize.Max)
                        .padding(vertical = Spacing.ExtraSmall),
                    content = content,
                )
            }
        }
    }
}

/**
 * Compact macOS menu section header row inside a [GlassDropdownMenu].
 */
@Composable
fun GlassDropdownMenuHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AlphaTokens.TextMuted),
        fontSize = FontSize.Micro,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = LetterSpacing.Snug,
        fontFamily = GlassFontFamily.ide(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = Spacing.MediumSmall,
                vertical = Spacing.ExtraSmall,
            ),
    )
}

/**
 * macOS-styled selectable or action menu row inside a [GlassDropdownMenu].
 *
 * Uses a horizontally inset concentric squircle highlight pill in the macOS accent color on hover
 * or keyboard focus, fixed leading checkmark gutter when enabled, and optional shortcut or submenu
 * disclosure chevron on the trailing edge.
 */
@Composable
fun GlassDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    isSelected: Boolean = false,
    enabled: Boolean = true,
    shortcut: String? = null,
    hasSubmenu: Boolean = false,
    destructive: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()

    val isHighlighted = enabled && (hovered || focused || pressed)
    val itemShape = GlassShapes.MediumSmall
    val visuals = resolveMenuItemVisuals(
        enabled = enabled,
        isHighlighted = isHighlighted,
        pressed = pressed,
        focused = focused,
        hovered = hovered,
        destructive = destructive,
    )

    val verticalPad = if (subtitle != null) Spacing.Compact else Spacing.ExtraSmall
    val borderModifier = if (visuals.border.alpha > 0f) {
        Modifier.glassOutlineBorder(
            width = StrokeWidth.Hairline,
            color = visuals.border,
            shape = itemShape,
        )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.ExtraSmall)
            .defaultMinSize(minHeight = ComponentSize.SegmentedButtonSize)
            .clip(itemShape)
            .background(visuals.background, itemShape)
            .then(borderModifier)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.Small, vertical = verticalPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        MenuRowSlots(
            text = text,
            subtitle = subtitle,
            leadingIcon = leadingIcon,
            isSelected = isSelected,
            isHighlighted = isHighlighted,
            shortcut = shortcut,
            hasSubmenu = hasSubmenu,
            visuals = visuals,
        )
    }
}

@Composable
private fun RowScope.MenuRowSlots(
    text: String,
    subtitle: String?,
    leadingIcon: (@Composable () -> Unit)?,
    isSelected: Boolean,
    isHighlighted: Boolean,
    shortcut: String?,
    hasSubmenu: Boolean,
    visuals: MenuItemVisuals,
) {
    if (LocalMacOsMenuSelectionGutter.current) {
        Box(
            modifier = Modifier.width(IconSize.Medium),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(
                    painter = AppIcons.CheckPainterResource(),
                    contentDescription = "Selected",
                    tint = visuals.checkmark,
                    modifier = Modifier.size(IconSize.Small),
                )
            }
        }
    }

    if (leadingIcon != null) {
        Box(
            modifier = Modifier.size(IconSize.Medium),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides visuals.foreground) {
                leadingIcon()
            }
        }
    }

    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(Spacing.Hairline),
    ) {
        CompositionLocalProvider(LocalContentColor provides visuals.foreground) {
            Text(
                text = text,
                color = visuals.foreground,
                fontSize = FontSize.BodySmall,
                fontWeight = if (isSelected || isHighlighted) FontWeight.Medium else FontWeight.Normal,
                fontFamily = GlassFontFamily.ide(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = visuals.subtitle,
                fontSize = FontSize.Micro,
                fontFamily = GlassFontFamily.ide(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (shortcut != null) {
        Text(
            text = shortcut,
            color = visuals.subtitle,
            fontSize = FontSize.CodeCompact,
            fontFamily = GlassFontFamily.ide(),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (hasSubmenu) {
        Icon(
            painter = AppIcons.ChevronRightPainterResource(),
            contentDescription = "Open submenu",
            tint = visuals.subtitle,
            modifier = Modifier.size(IconSize.Small),
        )
    }
}

private fun resolveMenuChromeColors(
    appTheme: AppTheme,
    effectsEnabled: Boolean,
    colors: ColorScheme,
): MenuChromeColors {
    val isLight = appTheme == AppTheme.Light
    val container = when {
        isLight && effectsEnabled -> colors.surfaceContainerLowest.copy(alpha = AlphaTokens.Heavy)
        isLight -> colors.surfaceContainerLowest
        effectsEnabled -> colors.surfaceContainerHigh.copy(alpha = AlphaTokens.Heavy)
        else -> colors.surfaceContainerHigh
    }
    val border = when {
        isLight && effectsEnabled -> colors.outline.copy(alpha = AlphaTokens.Prominent)
        isLight -> colors.outline
        effectsEnabled -> colors.outlineVariant.copy(alpha = AlphaTokens.Prominent)
        else -> colors.outlineVariant
    }
    val specularTop = if (!isLight && effectsEnabled) {
        InteractionColors.PressHighlight.copy(alpha = AlphaTokens.Subtle)
    } else {
        Color.Transparent
    }
    return MenuChromeColors(container = container, border = border, specularTop = specularTop)
}

@Composable
private fun resolveMenuItemVisuals(
    enabled: Boolean,
    isHighlighted: Boolean,
    pressed: Boolean,
    focused: Boolean,
    hovered: Boolean,
    destructive: Boolean,
): MenuItemVisuals {
    val colors = MaterialTheme.colorScheme
    val appTheme = GlassTheme.appTheme
    val effectsEnabled = GlassTheme.effectsEnabled
    val errorTextColor = GlassTheme.diagnosticColors.error

    val background = resolveItemBackground(enabled, isHighlighted, pressed, destructive, colors)
    val foreground = resolveItemForeground(enabled, isHighlighted, destructive, errorTextColor, colors)
    val subtitle = when {
        !enabled -> colors.onSurfaceVariant.copy(alpha = AlphaTokens.Disabled)
        isHighlighted -> foreground.copy(alpha = AlphaTokens.TextMuted)
        else -> colors.onSurfaceVariant
    }
    val checkmark = when {
        !enabled -> colors.onSurfaceVariant.copy(alpha = AlphaTokens.Disabled)
        isHighlighted -> foreground
        else -> colors.primary
    }
    val border = resolveItemBorder(isHighlighted, focused, hovered, effectsEnabled, appTheme, colors)
    return MenuItemVisuals(background, foreground, subtitle, checkmark, border)
}

private fun resolveItemBackground(
    enabled: Boolean,
    isHighlighted: Boolean,
    pressed: Boolean,
    destructive: Boolean,
    colors: ColorScheme,
): Color {
    if (!enabled || !isHighlighted) return Color.Transparent
    val base = if (destructive) colors.error else colors.primary
    return if (pressed) base.copy(alpha = AlphaTokens.Intense) else base
}

private fun resolveItemForeground(
    enabled: Boolean,
    isHighlighted: Boolean,
    destructive: Boolean,
    errorTextColor: Color,
    colors: ColorScheme,
): Color = when {
    !enabled -> colors.onSurfaceVariant.copy(alpha = AlphaTokens.Disabled)
    isHighlighted && destructive -> BrandColors.OnLogoBadge
    isHighlighted -> colors.onPrimary
    destructive -> errorTextColor
    else -> colors.onSurface
}

private fun resolveItemBorder(
    isHighlighted: Boolean,
    focused: Boolean,
    hovered: Boolean,
    effectsEnabled: Boolean,
    appTheme: AppTheme,
    colors: ColorScheme,
): Color = when {
    isHighlighted && effectsEnabled && appTheme != AppTheme.Light -> {
        InteractionColors.PressHighlight.copy(alpha = AlphaTokens.Hover)
    }
    focused && !hovered && appTheme != AppTheme.Light -> {
        colors.outlineVariant.copy(alpha = AlphaTokens.Prominent)
    }
    else -> Color.Transparent
}

/**
 * Cascading macOS submenu trigger item anchored with a trailing disclosure chevron that opens a
 * nested [GlassDropdownMenu] to the side.
 */
@Composable
fun GlassDropdownSubMenuItem(
    text: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    showSelectionGutter: Boolean = true,
    subMenuOffset: IntOffset = IntOffset.Zero,
    content: @Composable ColumnScope.(closeSubmenu: () -> Unit) -> Unit,
) {
    var submenuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        GlassDropdownMenuItem(
            text = text,
            subtitle = subtitle,
            leadingIcon = leadingIcon,
            enabled = enabled,
            hasSubmenu = true,
            onClick = { submenuExpanded = !submenuExpanded },
        )

        GlassDropdownMenu(
            expanded = submenuExpanded,
            onDismissRequest = { submenuExpanded = false },
            offset = subMenuOffset,
            showSelectionGutter = showSelectionGutter,
        ) {
            content { submenuExpanded = false }
        }
    }
}

/**
 * Thin macOS inset horizontal separator between item groups inside a [GlassDropdownMenu].
 */
@Composable
fun GlassDropdownMenuDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall)
            .height(StrokeWidth.Hairline)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Half)),
    )
}
