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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import org.ide.lti.core.designsystem.component.display.LtiRomStudioMark
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StudioBackdropColors
import org.ide.lti.core.designsystem.theme.WallpaperColors
import org.ide.lti.core.designsystem.theme.colorSchemeFor

/**
 * Live interactive Studio Preview card displaying a miniature project cockpit window over a scenic mountain backdrop.
 */
@Composable
fun LiveStudioPreviewCard(
    theme: AppTheme,
    reduceTransparency: Boolean,
    isComfortable: Boolean,
    modifier: Modifier = Modifier,
) {
    val isBlueGlass = theme == AppTheme.Blue
    val isDark = theme == AppTheme.Dark
    val previewScheme = colorSchemeFor(theme)

    val windowCanvasBg = when {
        reduceTransparency -> previewScheme.surfaceContainerHigh
        isBlueGlass -> previewScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.TextMuted)
        isDark -> previewScheme.surfaceContainer.copy(alpha = AlphaTokens.Heavy)
        else -> previewScheme.surface.copy(alpha = AlphaTokens.Heavy)
    }

    val windowBorderColor = previewScheme.outline

    val textColor = when {
        isBlueGlass -> BrandColors.OnLogoBadge
        else -> previewScheme.onSurface
    }

    val textMutedColor = when {
        isBlueGlass -> BrandColors.OnLogoBadge.copy(alpha = AlphaTokens.Prominent)
        else -> previewScheme.onSurfaceVariant
    }

    val activeAccent = previewScheme.primary

    val contentSpacing = if (isComfortable) Spacing.SmallMedium else Spacing.ExtraSmall

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentSize.AppearanceLivePreviewHeight)
            .clip(GlassShapes.Card)
            .border(
                width = GlassDimens.HairlineBorder,
                color = MaterialTheme.colorScheme.outline,
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
                        width = GlassDimens.HairlineBorder,
                        color = windowBorderColor,
                        shape = GlassShapes.Medium,
                    ),
            ) {
                // Window Chrome Titlebar
                StudioPreviewTitleBar(activeAccent = activeAccent, textColor = textColor)

                // Window Body Split: Left Mini-Nav & Right Form
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    StudioPreviewSidebar(
                        previewScheme = previewScheme,
                        activeAccent = activeAccent,
                        textColor = textColor,
                        textMutedColor = textMutedColor,
                        contentSpacing = contentSpacing,
                    )
                    StudioPreviewForm(
                        textColor = textColor,
                        textMutedColor = textMutedColor,
                        contentSpacing = contentSpacing,
                    )
                }
            }
        }
    }
}

/** Miniature window titlebar with traffic light buttons and studio logo. */
@Composable
private fun StudioPreviewTitleBar(activeAccent: Color, textColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ComponentSize.TopBarButtonSize)
            .padding(horizontal = Spacing.SmallMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Box(
            modifier = Modifier
                .size(Spacing.Small)
                .clip(GlassShapes.Circle)
                .background(GlassTheme.diagnosticColors.error),
        )
        Box(
            modifier = Modifier
                .size(Spacing.Small)
                .clip(GlassShapes.Circle)
                .background(GlassTheme.diagnosticColors.warning),
        )
        Box(
            modifier = Modifier
                .size(Spacing.Small)
                .clip(GlassShapes.Circle)
                .background(GlassTheme.diagnosticColors.success),
        )

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
}

/** Miniature navigation sidebar inside the studio preview window. */
@Composable
private fun StudioPreviewSidebar(
    previewScheme: ColorScheme,
    activeAccent: Color,
    textColor: Color,
    textMutedColor: Color,
    contentSpacing: Dp,
) {
    Column(
        modifier = Modifier
            .width(ComponentSize.PluginTableStatusColWidth)
            .fillMaxHeight()
            .background(previewScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.Half))
            .padding(horizontal = Spacing.Small, vertical = Spacing.SmallMedium),
        verticalArrangement = Arrangement.spacedBy(contentSpacing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassShapes.Small)
                .background(previewScheme.primary.copy(alpha = AlphaTokens.Faded))
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
}

/** Miniature form inputs inside the studio preview cockpit. */
@Composable
private fun RowScope.StudioPreviewForm(textColor: Color, textMutedColor: Color, contentSpacing: Dp) {
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

        MiniFormInput(
            label = "Name",
            value = "Aurora",
            textColor = textColor,
            labelColor = textMutedColor,
            bgColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )

        MiniFormInput(
            label = "Location",
            value = "/Users/jordan/Projects/Aurora",
            trailingIcon = Icons.Default.FolderOpen,
            textColor = textColor,
            labelColor = textMutedColor,
            bgColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )

        MiniFormInput(
            label = "Description",
            value = "A modern ROM toolkit for creators.",
            multiline = true,
            textColor = textColor,
            labelColor = textMutedColor,
            bgColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun MiniNavItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, textColor: Color) {
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
    borderColor: Color = MaterialTheme.colorScheme.outline,
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
                    width = GlassDimens.HairlineBorder,
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
private fun ScenicBackdropCanvas(isBlueGlass: Boolean, isDark: Boolean, modifier: Modifier = Modifier) {
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
