/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.display

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.colorSchemeFor

/**
 * Standardized Liquid Glass Theme Preview Card displaying an interactive miniature IDE mockup.
 *
 * Adheres to Clean Architecture and Design System Tokens:
 * - Blue Theme: AMOLED black canvas with glowing sky-blue accents and vivid syntax highlights
 * - Dark Theme: Linear/Fleet obsidian matte black canvas with charcoal surfaces and electric blue accent
 * - Light Theme: Frosted luminous white canvas with crisp slate borders
 *
 * @param theme The [AppTheme] this card represents.
 * @param isSelected Whether this theme is currently active.
 * @param onClick Invoked when the user clicks this theme card.
 * @param modifier Optional modifier.
 * @param subtitle Optional subtitle tag (e.g. "Signature AMOLED", "Fleet Obsidian").
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ThemePreviewCard(
    theme: AppTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    var isHovered by remember { mutableStateOf(false) }
    val previewScheme = colorSchemeFor(theme)

    val targetBorder = when {
        isSelected -> previewScheme.primary
        isHovered -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    val borderColor by animateColorAsState(targetValue = targetBorder, label = "ThemePreviewCard_Border")

    val targetSurface = when {
        isSelected -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.Elevated)
        isHovered -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.Subtle)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    val surfaceColor by animateColorAsState(targetValue = targetSurface, label = "ThemePreviewCard_Surface")

    val defaultSubtitle = when (theme) {
        AppTheme.Blue -> "Signature AMOLED"
        AppTheme.Dark -> "Fleet Obsidian"
        AppTheme.Light -> "Frosted Minimal"
    }

    Column(
        modifier = modifier
            .widthIn(min = ComponentSize.ThemeCardMinWidth)
            .clip(GlassShapes.Medium)
            .background(surfaceColor)
            .glassOutlineBorder(
                width = if (isSelected) StrokeWidth.Focused else StrokeWidth.Hairline,
                color = borderColor,
                shape = GlassShapes.Medium,
            )
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(Spacing.SmallMedium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        // Miniature IDE Window Preview Mockup
        MiniIdePreview(theme = theme)

        // Label & Selection Indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
            ) {
                Text(
                    text = themeDisplayName(theme),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle ?: defaultSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Selection Check Badge (Apple/Linear elevated pill)
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(Spacing.Large)
                        .clip(GlassShapes.Capsule)
                        .background(previewScheme.primary)
                        .glassOutlineBorder(
                            width = StrokeWidth.Hairline,
                            color = previewScheme.primary,
                            shape = GlassShapes.Capsule,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = AppIcons.CheckPainterResource(),
                        contentDescription = "Selected",
                        modifier = Modifier.size(IconSize.Small),
                        tint = previewScheme.onPrimary,
                    )
                }
            }
        }
    }
}

private fun themeDisplayName(theme: AppTheme): String = when (theme) {
    AppTheme.Blue -> "Blue Glass"
    AppTheme.Dark -> "Dark"
    AppTheme.Light -> "Light"
}

/**
 * Miniature IDE preview window mockup demonstrating the theme's colors, sidebar, and syntax.
 */
@Composable
private fun MiniIdePreview(theme: AppTheme, modifier: Modifier = Modifier) {
    val previewScheme = colorSchemeFor(theme)
    val windowCanvasColor = previewScheme.surface
    val windowBorderColor = previewScheme.outline
    val sidebarColor = previewScheme.surfaceContainer
    val editorColor = previewScheme.surface
    val tokenKeywordColor = previewScheme.primary
    val tokenIdentifierColor = previewScheme.secondary
    val tokenFadedColor = previewScheme.outlineVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentSize.ThemePreviewHeight)
            .clip(GlassShapes.Small)
            .background(windowCanvasColor)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = windowBorderColor,
                shape = GlassShapes.Small,
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            MiniIdeTitleBar(sidebarColor = sidebarColor)
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                MiniIdeSidebar(
                    sidebarColor = sidebarColor,
                    tokenKeywordColor = tokenKeywordColor,
                    tokenFadedColor = tokenFadedColor,
                )
                MiniIdeEditor(
                    editorColor = editorColor,
                    tokenKeywordColor = tokenKeywordColor,
                    tokenIdentifierColor = tokenIdentifierColor,
                    tokenFadedColor = tokenFadedColor,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            MiniIdeStatusBar(
                sidebarColor = sidebarColor,
                tokenKeywordColor = tokenKeywordColor,
            )
        }
    }
}

/**
 * Window title bar with mock macOS mini indicator dots.
 */
@Composable
private fun MiniIdeTitleBar(sidebarColor: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Spacing.Fourteen)
            .background(sidebarColor)
            .padding(horizontal = Spacing.Compact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        Box(
            modifier = Modifier
                .size(IconSize.MiniDot)
                .clip(GlassShapes.Capsule)
                .background(GlassTheme.diagnosticColors.error),
        )
        Box(
            modifier = Modifier
                .size(IconSize.MiniDot)
                .clip(GlassShapes.Capsule)
                .background(GlassTheme.diagnosticColors.warning),
        )
        Box(
            modifier = Modifier
                .size(IconSize.MiniDot)
                .clip(GlassShapes.Capsule)
                .background(GlassTheme.diagnosticColors.success),
        )
    }
}

/**
 * Miniature project tree sidebar with placeholder item bars.
 */
@Composable
private fun MiniIdeSidebar(
    sidebarColor: Color,
    tokenKeywordColor: Color,
    tokenFadedColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(ComponentSize.PanelHeaderAction)
            .fillMaxHeight()
            .background(sidebarColor)
            .padding(horizontal = Spacing.ExtraExtraSmall, vertical = Spacing.ExtraSmall),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(AlphaTokens.Prominent)
                .height(Spacing.ExtraExtraSmall)
                .clip(GlassShapes.Capsule)
                .background(tokenKeywordColor),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(AlphaTokens.TextMuted)
                .height(Spacing.ExtraExtraSmall)
                .clip(GlassShapes.Capsule)
                .background(tokenFadedColor),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(AlphaTokens.Hover)
                .height(Spacing.ExtraExtraSmall)
                .clip(GlassShapes.Capsule)
                .background(tokenFadedColor),
        )
    }
}

/**
 * Miniature code editor pane with colored syntax bars.
 */
@Composable
private fun MiniIdeEditor(
    editorColor: Color,
    tokenKeywordColor: Color,
    tokenIdentifierColor: Color,
    tokenFadedColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(editorColor)
            .padding(Spacing.Small),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
            Box(
                modifier = Modifier
                    .width(Spacing.CardPadding)
                    .height(Spacing.ExtraSmall)
                    .clip(GlassShapes.Capsule)
                    .background(tokenKeywordColor),
            )
            Box(
                modifier = Modifier
                    .width(Spacing.ExtraLarge)
                    .height(Spacing.ExtraSmall)
                    .clip(GlassShapes.Capsule)
                    .background(tokenIdentifierColor),
            )
        }
        Row(
            modifier = Modifier.padding(start = Spacing.Small),
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Box(
                modifier = Modifier
                    .width(Spacing.Medium)
                    .height(Spacing.ExtraSmall)
                    .clip(GlassShapes.Capsule)
                    .background(tokenIdentifierColor),
            )
            Box(
                modifier = Modifier
                    .width(Spacing.SectionGap)
                    .height(Spacing.ExtraSmall)
                    .clip(GlassShapes.Capsule)
                    .background(tokenFadedColor),
            )
        }
        Box(
            modifier = Modifier
                .padding(start = Spacing.Small)
                .width(Spacing.Medium)
                .height(Spacing.ExtraSmall)
                .clip(GlassShapes.Capsule)
                .background(tokenKeywordColor),
        )
    }
}

/**
 * Miniature status bar at bottom of the preview window.
 */
@Composable
private fun MiniIdeStatusBar(sidebarColor: Color, tokenKeywordColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Spacing.Compact)
            .background(sidebarColor)
            .padding(horizontal = Spacing.Compact),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(Spacing.ExtraExtraSmall)
                .clip(GlassShapes.Capsule)
                .background(tokenKeywordColor),
        )
    }
}
