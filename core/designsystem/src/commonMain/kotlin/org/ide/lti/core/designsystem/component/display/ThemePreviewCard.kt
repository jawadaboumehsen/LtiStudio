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
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.ThemeColors
import org.ide.lti.core.designsystem.theme.config.AppTheme
import org.ide.lti.core.designsystem.theme.resolveAnimationSpec

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
    val glassColors = GlassTheme.colors

    val targetBorder = when {
        isSelected -> theme.indicatorColor
        isHovered -> glassColors.glassBorderHighlight
        else -> glassColors.glassBorder
    }

    val borderColor by animateColorAsState(
        targetValue = targetBorder,
        animationSpec = resolveAnimationSpec(GlassTheme.reducedMotion),
    )

    val targetSurface = when {
        isSelected -> glassColors.glassSurfaceElevated.copy(alpha = AlphaTokens.Elevated)
        isHovered -> glassColors.glassSurfaceElevated.copy(alpha = AlphaTokens.Subtle)
        else -> glassColors.glassSurface
    }

    val surfaceColor by animateColorAsState(
        targetValue = targetSurface,
        animationSpec = resolveAnimationSpec(GlassTheme.reducedMotion),
    )

    val defaultSubtitle = when (theme) {
        is AppTheme.Blue -> "Signature AMOLED"
        is AppTheme.Dark -> "Fleet Obsidian"
        is AppTheme.Light -> "Frosted Minimal"
        else -> "Custom Theme"
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
                    text = theme.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = glassColors.onGlass,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle ?: defaultSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = glassColors.onGlassSecondary,
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
                        .background(theme.indicatorColor)
                        .glassOutlineBorder(
                            width = StrokeWidth.Hairline,
                            color = theme.indicatorColor,
                            shape = GlassShapes.Capsule,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = AppIcons.CheckPainterResource(),
                        contentDescription = "Selected",
                        modifier = Modifier.size(IconSize.Small),
                        tint = if (theme == AppTheme.Light) ThemeColors.LightLayerText else BrandColors.OnLogoBadge,
                    )
                }
            }
        }
    }
}

/**
 * Miniature IDE preview window mockup demonstrating the theme's colors, sidebar, and syntax.
 */
@Composable
private fun MiniIdePreview(
    theme: AppTheme,
    modifier: Modifier = Modifier,
) {
    val windowCanvasColor = when (theme) {
        is AppTheme.Blue -> ThemeColors.BlueAmoledCanvas
        is AppTheme.Dark -> ThemeColors.DarkLayerBase
        is AppTheme.Light -> ThemeColors.LightLayerBase
        else -> ThemeColors.DarkLayerBase
    }

    val windowBorderColor = when (theme) {
        is AppTheme.Blue -> ThemeColors.BlueAmoledBorder
        is AppTheme.Dark -> ThemeColors.DarkLayerHighlight
        is AppTheme.Light -> ThemeColors.LightLayerBorder
        else -> ThemeColors.DarkLayerHighlight
    }

    val sidebarColor = when (theme) {
        is AppTheme.Blue -> ThemeColors.BlueAmoledRail
        is AppTheme.Dark -> ThemeColors.DarkLayerSurface
        is AppTheme.Light -> ThemeColors.LightLayerElevated
        else -> ThemeColors.DarkLayerSurface
    }

    val editorColor = when (theme) {
        is AppTheme.Blue -> ThemeColors.BlueAmoledCanvas
        is AppTheme.Dark -> ThemeColors.DarkLayerElevated
        is AppTheme.Light -> ThemeColors.LightLayerSurface
        else -> ThemeColors.DarkLayerElevated
    }

    val tokenKeywordColor = when (theme) {
        is AppTheme.Blue -> ThemeColors.FleetCyan
        is AppTheme.Dark -> ThemeColors.FleetBlue
        is AppTheme.Light -> ThemeColors.LightLayerAccent
        else -> ThemeColors.FleetBlue
    }

    val tokenIdentifierColor = when (theme) {
        is AppTheme.Blue -> ThemeColors.FleetCyan
        is AppTheme.Dark -> ThemeColors.FleetFocusGlow
        is AppTheme.Light -> ThemeColors.LightLayerText
        else -> ThemeColors.FleetFocusGlow
    }

    val tokenFadedColor = when (theme) {
        is AppTheme.Blue -> ThemeColors.BlueAmoledBorderMuted
        is AppTheme.Dark -> ThemeColors.DarkLayerControl
        is AppTheme.Light -> ThemeColors.LightLayerBorder
        else -> ThemeColors.DarkLayerControl
    }

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
            // Window Title Bar with macOS Mini Dots
            Row(
                modifier = Modifier
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
                        .background(ThemeColors.WindowControlClose),
                )
                Box(
                    modifier = Modifier
                        .size(IconSize.MiniDot)
                        .clip(GlassShapes.Capsule)
                        .background(ThemeColors.WindowControlMinimize),
                )
                Box(
                    modifier = Modifier
                        .size(IconSize.MiniDot)
                        .clip(GlassShapes.Capsule)
                        .background(ThemeColors.WindowControlMaximize),
                )
            }

            // IDE Panes (Sidebar + Editor)
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Mini Project Tree Sidebar
                Column(
                    modifier = Modifier
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

                // Mini Code Editor
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(editorColor)
                        .padding(Spacing.Small),
                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    // Line 1: Keyword + Identifier
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

                    // Line 2: Indented line
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

                    // Line 3: Indented closing token
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

            // Mini Status Bar
            Box(
                modifier = Modifier
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
    }
}
