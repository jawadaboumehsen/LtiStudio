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

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import org.ide.lti.core.designsystem.theme.SettingsThemeColors
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StudioBackdropColors
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.config.AppTheme
import org.ide.lti.feature.settings.model.SettingsCategory

/**
 * Settings navigation sidebar displaying categories with search filtering, selection styling, and version footer.
 */
@Composable
fun SettingsSidebar(
    selectedCategory: SettingsCategory,
    onSelectCategory: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
    appVersion: String = "v0.9.0 (Build 1247)",
) {
    val isBlueGlass = GlassTheme.appTheme == AppTheme.Blue
    var searchQuery by remember { mutableStateOf("") }

    val categories = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            SettingsCategory.sidebarCategories
        } else {
            SettingsCategory.sidebarCategories.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.subtitle.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val backgroundModifier = if (isBlueGlass) {
        Modifier.background(
            Brush.verticalGradient(
                listOf(
                    StudioBackdropColors.SidebarStart,
                    StudioBackdropColors.SidebarEnd,
                ),
            ),
        )
    } else {
        Modifier.background(colors.navigatorBackground)
    }

    Column(
        modifier = modifier
            .width(ComponentSize.SettingsSidebarWidth)
            .fillMaxHeight()
            .then(backgroundModifier)
            .border(
                width = IdeShellDimens.HairlineBorder,
                color = if (isBlueGlass) PluginThemeColors.CardBorder else colors.border,
            )
            .padding(Spacing.Medium),
    ) {
        // Title Header
        Text(
            text = "Settings",
            fontSize = FontSize.TitleLarge,
            fontWeight = FontWeight.Bold,
            color = if (isBlueGlass) BrandColors.OnLogoBadge else colors.textMain,
            modifier = Modifier.padding(bottom = Spacing.SmallMedium),
        )

        // Search Settings Input Field
        SettingsSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            colors = colors,
            isBlueGlass = isBlueGlass,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.Medium),
        )

        // Category Items Scrollable Column
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            categories.forEach { category ->
                val isSelected = category == selectedCategory
                SettingsCategoryRow(
                    category = category,
                    isSelected = isSelected,
                    onClick = { onSelectCategory(category) },
                    colors = colors,
                    isBlueGlass = isBlueGlass,
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.Medium))

        // Bottom Footer: App Mark & Version
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.Small)
                .testTag("SettingsSidebarFooter"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
        ) {
            Box(
                modifier = Modifier
                    .size(IconSize.ExtraLarge)
                    .clip(GlassShapes.Card)
                    .background(
                        if (isBlueGlass) {
                            PluginThemeColors.CardBackground
                        } else {
                            colors.cardBackground
                        },
                    )
                    .border(
                        width = IdeShellDimens.HairlineBorder,
                        color = if (isBlueGlass) PluginThemeColors.CardBorder else colors.border,
                        shape = GlassShapes.Card,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                LtiRomStudioMark(
                    modifier = Modifier.size(IconSize.Medium),
                    tint = if (isBlueGlass) PluginThemeColors.ActiveTabBorder else colors.accentBlue,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
                Text(
                    text = "LtiRom Studio",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = FontSize.BodySmall,
                    color = if (isBlueGlass) BrandColors.OnLogoBadge else colors.textMain,
                )
                Text(
                    text = appVersion,
                    fontFamily = codeFontFamily(),
                    fontSize = FontSize.LabelSmall,
                    color = if (isBlueGlass) BrandColors.OnLogoBadge.copy(alpha = AlphaTokens.High) else colors.textDim,
                )
            }
        }
    }
}

@Composable
private fun SettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    colors: IdeShellColorScheme,
    isBlueGlass: Boolean,
    modifier: Modifier = Modifier,
) {
    val searchBg = if (isBlueGlass) {
        PluginThemeColors.CardBackground.copy(alpha = AlphaTokens.Half)
    } else {
        colors.contentBackground
    }
    val searchBorder = if (isBlueGlass) {
        PluginThemeColors.CardBorder
    } else {
        colors.border
    }
    val textColor = if (isBlueGlass) BrandColors.OnLogoBadge else colors.textMain
    val placeholderColor = if (isBlueGlass) BrandColors.OnLogoBadge.copy(alpha = AlphaTokens.Half) else colors.textMuted

    Row(
        modifier = modifier
            .height(ComponentSize.SettingsSearchFieldHeight)
            .clip(GlassShapes.Small)
            .background(searchBg)
            .border(
                width = IdeShellDimens.HairlineBorder,
                color = searchBorder,
                shape = GlassShapes.Small,
            )
            .padding(horizontal = Spacing.SmallMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = placeholderColor,
            modifier = Modifier.size(IconSize.Search),
        )

        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Search settings",
                    fontSize = FontSize.BodySmall,
                    color = placeholderColor,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = textColor,
                    fontSize = FontSize.BodySmall,
                ),
                cursorBrush = SolidColor(if (isBlueGlass) PluginThemeColors.ActiveTabBorder else colors.accentBlue),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear",
                tint = placeholderColor,
                modifier = Modifier
                    .size(IconSize.Search)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onQueryChange("") },
                    ),
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SettingsCategoryRow(
    category: SettingsCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    colors: IdeShellColorScheme,
    isBlueGlass: Boolean,
    modifier: Modifier = Modifier,
) {
    var isHovered by remember { mutableStateOf(false) }

    val activeBg = if (isBlueGlass) {
        SettingsThemeColors.ActivePillBackground
    } else {
        colors.controlSelected
    }

    val hoverBg = if (isBlueGlass) {
        PluginThemeColors.CardBackground.copy(alpha = AlphaTokens.Half)
    } else {
        colors.controlHover
    }

    val targetBg = when {
        isSelected -> activeBg
        isHovered -> hoverBg
        else -> Color.Transparent
    }

    val animatedBg by animateColorAsState(targetValue = targetBg)

    val activeBorder = if (isBlueGlass) {
        SettingsThemeColors.ActivePillBorder
    } else {
        colors.accentBlue
    }

    val borderModifier = if (isSelected) {
        Modifier.border(
            width = IdeShellDimens.HairlineBorder,
            color = activeBorder,
            shape = GlassShapes.MediumSmall,
        )
    } else {
        Modifier
    }

    val iconColor = when {
        isSelected && isBlueGlass -> BrandColors.OnLogoBadge
        isSelected -> colors.accentBlue
        isHovered -> if (isBlueGlass) BrandColors.OnLogoBadge else colors.textMain
        else -> if (isBlueGlass) BrandColors.OnLogoBadge.copy(alpha = AlphaTokens.High) else colors.textDim
    }

    val labelColor = when {
        isSelected && isBlueGlass -> BrandColors.OnLogoBadge
        isSelected -> colors.textMain
        isHovered -> if (isBlueGlass) BrandColors.OnLogoBadge else colors.textMain
        else -> if (isBlueGlass) BrandColors.OnLogoBadge.copy(alpha = AlphaTokens.Vibrant) else colors.textMuted
    }

    val icon = resolveCategoryIcon(category)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentSize.SettingsCategoryRowHeight)
            .clip(GlassShapes.MediumSmall)
            .then(borderModifier)
            .background(animatedBg)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.SmallMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = category.title,
            tint = iconColor,
            modifier = Modifier.size(IconSize.SidePanelRail),
        )
        Text(
            text = category.title,
            fontSize = FontSize.BodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Suppress("CyclomaticComplexMethod")
private fun resolveCategoryIcon(category: SettingsCategory): ImageVector = when (category) {
    SettingsCategory.EditorPreferences -> Icons.Default.Description
    SettingsCategory.Appearance -> Icons.Default.Palette
    SettingsCategory.Workspace -> Icons.Default.Folder
    SettingsCategory.BuildAndRun -> Icons.Default.PlayArrow
    SettingsCategory.Network -> Icons.Default.Public
    SettingsCategory.PrivacyAndData -> Icons.Default.Security
    SettingsCategory.Updates -> Icons.Default.Refresh
    SettingsCategory.About -> Icons.Default.Info
    SettingsCategory.General -> Icons.Default.Tune
    SettingsCategory.Editor -> Icons.Default.Code
    SettingsCategory.BuildEnvironment -> Icons.Default.Build
    SettingsCategory.PluginSources -> Icons.Default.Widgets
    SettingsCategory.Credentials -> Icons.Default.Key
    SettingsCategory.Notifications -> Icons.Default.Notifications
    SettingsCategory.Paths -> Icons.Default.Folder
    SettingsCategory.Diagnostics -> Icons.Default.Speed
}
