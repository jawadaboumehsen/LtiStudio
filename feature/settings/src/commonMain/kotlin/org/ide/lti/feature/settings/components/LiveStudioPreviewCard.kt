/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.settings.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.LtiRomStudioMark
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.designsystem.theme.PluginThemeColors
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StudioBackdropColors
import org.ide.lti.core.designsystem.theme.ThemeColors
import org.ide.lti.core.designsystem.theme.WallpaperColors
import org.ide.lti.core.designsystem.theme.config.AppTheme

/**
 * Live interactive Studio Preview card displaying a miniature project cockpit window over a scenic mountain backdrop.
 */
@Composable
fun LiveStudioPreviewCard(
    theme: AppTheme,
    reduceTransparency: Boolean,
    isComfortable: Boolean,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
) {
    val isBlueGlass = theme == AppTheme.Blue
    val isDark = theme == AppTheme.Dark

    val windowCanvasBg = when {
        reduceTransparency && isBlueGlass -> ThemeColors.BlueAmoledCanvas
        reduceTransparency && isDark -> colors.navigatorBackground
        reduceTransparency -> ThemeColors.LightLayerElevated
        isBlueGlass -> PluginThemeColors.CardBackground.copy(alpha = AlphaTokens.TextMuted)
        isDark -> colors.navigatorBackground.copy(alpha = AlphaTokens.Heavy)
        else -> ThemeColors.LightLayerBase.copy(alpha = AlphaTokens.Heavy)
    }

    val windowBorderColor = when {
        isBlueGlass -> PluginThemeColors.CardBorder
        isDark -> colors.border
        else -> ThemeColors.LightLayerBorder
    }

    val textColor = when {
        isBlueGlass -> BrandColors.OnLogoBadge
        isDark -> colors.textMain
        else -> ThemeColors.LightLayerText
    }

    val textMutedColor = when {
        isBlueGlass -> BrandColors.OnLogoBadge.copy(alpha = AlphaTokens.Prominent)
        isDark -> colors.textMuted
        else -> colors.textMuted
    }

    val activeAccent = when {
        isBlueGlass -> PluginThemeColors.ActiveTabBorder
        isDark -> colors.accentBlue
        else -> ThemeColors.LightLayerAccent
    }

    val contentSpacing = if (isComfortable) Spacing.SmallMedium else Spacing.ExtraSmall

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentSize.AppearanceLivePreviewHeight)
            .clip(GlassShapes.Card)
            .border(
                width = IdeShellDimens.HairlineBorder,
                color = if (isBlueGlass) PluginThemeColors.CardBorder else colors.border,
                shape = GlassShapes.Card,
            )
            .testTag("LiveStudioPreviewCard"),
    ) {
        // Atmospheric Mountain Wallpaper Backdrop
        ScenicBackdropCanvas(
            isBlueGlass = isBlueGlass,
            isDark = isDark,
            modifier = Modifier.fillMaxSize(),
        )

        // Floating Frosted Glass Miniature Studio Window
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.Medium),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(GlassShapes.Medium)
                    .background(windowCanvasBg)
                    .border(
                        width = IdeShellDimens.HairlineBorder,
                        color = windowBorderColor,
                        shape = GlassShapes.Medium,
                    ),
            ) {
                // Window Chrome Titlebar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ComponentSize.TopBarButtonSize)
                        .padding(horizontal = Spacing.SmallMedium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    Box(modifier = Modifier.size(Spacing.Small).clip(CircleShape).background(ThemeColors.WindowControlClose))
                    Box(modifier = Modifier.size(Spacing.Small).clip(CircleShape).background(ThemeColors.WindowControlMinimize))
                    Box(modifier = Modifier.size(Spacing.Small).clip(CircleShape).background(ThemeColors.WindowControlMaximize))

                    Spacer(modifier = Modifier.width(Spacing.Small))

                    LtiRomStudioMark(
                        modifier = Modifier.size(IconSize.Small),
                        tint = activeAccent,
                    )
                    Text(
                        text = "LtiRom Studio",
                        fontSize = FontSize.LabelSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                    )
                }

                // Window Body Split: Left Mini-Nav & Right Form
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Mini Left Sidebar
                    Column(
                        modifier = Modifier
                            .width(ComponentSize.PluginTableStatusColWidth)
                            .fillMaxHeight()
                            .background(
                                if (isBlueGlass) {
                                    PluginThemeColors.CardBackground.copy(alpha = AlphaTokens.Half)
                                } else {
                                    colors.cardBackground.copy(alpha = AlphaTokens.Half)
                                },
                            )
                            .padding(horizontal = Spacing.Small, vertical = Spacing.SmallMedium),
                        verticalArrangement = Arrangement.spacedBy(contentSpacing),
                    ) {
                        // Projects (Active)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(GlassShapes.Small)
                                .background(
                                    if (isBlueGlass) {
                                        PluginThemeColors.ActiveTabBackground
                                    } else {
                                        colors.controlSelected
                                    },
                                )
                                .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = activeAccent,
                                modifier = Modifier.size(IconSize.Small),
                            )
                            Text(
                                text = "Projects",
                                fontSize = FontSize.LabelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor,
                            )
                        }

                        MiniNavItem(icon = Icons.Default.Schedule, label = "Recent", textColor = textMutedColor)
                        MiniNavItem(icon = Icons.Default.StarOutline, label = "Starred", textColor = textMutedColor)
                        MiniNavItem(icon = Icons.Default.Widgets, label = "Templates", textColor = textMutedColor)
                    }

                    // Mini Right Form Cockpit
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(Spacing.SmallMedium),
                        verticalArrangement = Arrangement.spacedBy(contentSpacing),
                    ) {
                        Text(
                            text = "Project",
                            fontSize = FontSize.BodySmall,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                        )

                        // Name Field
                        MiniFormInput(
                            label = "Name",
                            value = "Aurora",
                            textColor = textColor,
                            labelColor = textMutedColor,
                            bgColor = colors.cardBackground,
                            borderColor = windowBorderColor,
                        )

                        // Location Field
                        MiniFormInput(
                            label = "Location",
                            value = "/Users/jordan/Projects/Aurora",
                            trailingIcon = Icons.Default.FolderOpen,
                            textColor = textColor,
                            labelColor = textMutedColor,
                            bgColor = colors.cardBackground,
                            borderColor = windowBorderColor,
                        )

                        // Description Field
                        MiniFormInput(
                            label = "Description",
                            value = "A modern ROM toolkit for creators.",
                            multiline = true,
                            textColor = textColor,
                            labelColor = textMutedColor,
                            bgColor = colors.cardBackground,
                            borderColor = windowBorderColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    textColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(IconSize.Small),
        )
        Text(
            text = label,
            fontSize = FontSize.LabelSmall,
            color = textColor,
        )
    }
}

@Composable
private fun MiniFormInput(
    label: String,
    value: String,
    textColor: Color,
    labelColor: Color,
    bgColor: Color,
    borderColor: Color,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    multiline: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
        Text(
            text = label,
            fontSize = FontSize.LabelSmall,
            color = labelColor,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassShapes.ExtraSmall)
                .background(bgColor)
                .border(
                    width = IdeShellDimens.HairlineBorder,
                    color = borderColor,
                    shape = GlassShapes.ExtraSmall,
                )
                .padding(
                    horizontal = Spacing.Small,
                    vertical = if (multiline) Spacing.SmallMedium else Spacing.ExtraSmall,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = value,
                fontSize = FontSize.LabelSmall,
                color = textColor,
                modifier = Modifier.weight(1f),
            )
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = labelColor,
                    modifier = Modifier.size(IconSize.Small),
                )
            }
        }
    }
}

@Composable
private fun ScenicBackdropCanvas(
    isBlueGlass: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val skyGradient = when {
            isBlueGlass -> Brush.verticalGradient(
                listOf(
                    StudioBackdropColors.Vignette,
                    StudioBackdropColors.Base,
                    StudioBackdropColors.SidebarEnd,
                ),
            )
            isDark -> Brush.verticalGradient(
                listOf(
                    WallpaperColors.DarkSkyTop,
                    WallpaperColors.DarkSkyMiddle,
                    WallpaperColors.DarkSkyHorizon,
                ),
            )
            else -> Brush.verticalGradient(
                listOf(
                    WallpaperColors.LightSkyTop,
                    WallpaperColors.LightSkyMiddle,
                    WallpaperColors.LightSkyHorizon,
                ),
            )
        }
        drawRect(brush = skyGradient)

        // Draw Mountain Silhouette Layers
        val mountainFarColor = if (isDark || isBlueGlass) {
            WallpaperColors.DarkMountainFar
        } else {
            WallpaperColors.LightMountainFar
        }
        val mountainFarPath = Path().apply {
            moveTo(0f, size.height * 0.70f)
            lineTo(size.width * 0.25f, size.height * 0.50f)
            lineTo(size.width * 0.45f, size.height * 0.65f)
            lineTo(size.width * 0.75f, size.height * 0.40f)
            lineTo(size.width, size.height * 0.60f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path = mountainFarPath, color = mountainFarColor)

        val mountainNearColor = if (isDark || isBlueGlass) {
            WallpaperColors.DarkMountainNear
        } else {
            WallpaperColors.LightMountainNear
        }
        val mountainNearPath = Path().apply {
            moveTo(0f, size.height * 0.85f)
            lineTo(size.width * 0.35f, size.height * 0.68f)
            lineTo(size.width * 0.60f, size.height * 0.82f)
            lineTo(size.width * 0.85f, size.height * 0.62f)
            lineTo(size.width, size.height * 0.78f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path = mountainNearPath, color = mountainNearColor)
    }
}
