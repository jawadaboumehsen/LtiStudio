/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import org.ide.lti.core.ui.settings.AppSettingsState
import org.ide.lti.core.designsystem.component.layout.GlobalAppDestination
import org.ide.lti.core.designsystem.component.layout.GlobalAppTopBar
import org.ide.lti.core.designsystem.component.layout.IdePipelineRail
import org.ide.lti.core.designsystem.component.layout.IdePipelineStageItem
import org.ide.lti.core.designsystem.component.layout.StudioGlassBackdrop
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.PluginThemeColors
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.config.AppTheme
import org.ide.lti.feature.settings.components.AboutSettingsPane
import org.ide.lti.feature.settings.components.AppearanceSettingsPane
import org.ide.lti.feature.settings.components.EditorSettingsPane
import org.ide.lti.feature.settings.components.GenericCategoryPane
import org.ide.lti.feature.settings.components.NotificationsSettingsPane
import org.ide.lti.feature.settings.components.SettingsSidebar
import org.ide.lti.feature.settings.model.SettingsCategory
import org.koin.compose.koinInject

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToSetup: () -> Unit = onBackClick,
    onNavigateToWorkspace: () -> Unit = onBackClick,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    backdrop: Backdrop? = null,
    appSettingsState: AppSettingsState = koinInject(),
) {
    SettingsScreenContent(
        modifier = modifier.fillMaxSize(),
        onBackClick = onBackClick,
        onNavigateToSetup = onNavigateToSetup,
        onNavigateToWorkspace = onNavigateToWorkspace,
        showHeader = showHeader,
        backdrop = backdrop,
        appSettingsState = appSettingsState,
    )
}

@Composable
fun SettingsScreenContent(
    onBackClick: () -> Unit,
    onNavigateToSetup: () -> Unit = onBackClick,
    onNavigateToWorkspace: () -> Unit = onBackClick,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    backdrop: Backdrop? = null,
    appSettingsState: AppSettingsState = koinInject(),
) {
    val currentTheme = AppTheme.fromId(appSettingsState.theme)
    val isBlueGlass = currentTheme == AppTheme.Blue && appSettingsState.effectsEnabled
    val ambientBackdrop = (backdrop as? LayerBackdrop) ?: (GlassTheme.backdrop as? LayerBackdrop)
    val localBackdrop = rememberLayerBackdrop()
    val resolvedBackdrop = if (isBlueGlass) {
        localBackdrop
    } else {
        ambientBackdrop ?: localBackdrop
    }

    LtiTheme(
        appTheme = currentTheme,
        effectsEnabled = appSettingsState.effectsEnabled,
        reducedMotion = appSettingsState.reducedMotion,
        sharedBackdrop = resolvedBackdrop,
    ) {
        val colors = GlassTheme.ideShellColors

        var selectedCategory by remember { mutableStateOf(SettingsCategory.EditorPreferences) }

        val pipelineStages = remember {
            listOf(
                IdePipelineStageItem(id = "acquire", label = "Acquire", icon = "Download"),
                IdePipelineStageItem(id = "extract", label = "Extract", icon = "Folder"),
                IdePipelineStageItem(id = "assemble", label = "Assemble", icon = "Inventory2"),
                IdePipelineStageItem(id = "debloat", label = "Debloat", icon = "Delete"),
                IdePipelineStageItem(id = "patches", label = "Patches", icon = "Edit"),
                IdePipelineStageItem(id = "build", label = "Build", icon = "Bolt"),
                IdePipelineStageItem(id = "metadata", label = "Metadata", icon = "Description"),
                IdePipelineStageItem(id = "publish", label = "Publish", icon = "Public"),
            )
        }

        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .background(colors.appBackground)
                .testTag("SettingsScreen"),
        ) {
            if (isBlueGlass) {
                StudioGlassBackdrop(backdrop = resolvedBackdrop)
            }

            val showPipelineRail = maxWidth >= IdeShellDimens.CompactBreakpoint

            Column(modifier = Modifier.fillMaxSize()) {
                if (showHeader) {
                    GlobalAppTopBar(
                        selectedDestination = GlobalAppDestination.Settings,
                        onSelectDestination = { destination ->
                            when (destination) {
                                GlobalAppDestination.Setup -> onNavigateToSetup()
                                GlobalAppDestination.Workspace -> onNavigateToWorkspace()
                                GlobalAppDestination.Settings -> Unit
                            }
                        },
                        colors = colors,
                        actions = {
                            // Top Bar Global Search Box
                            Row(
                                modifier = Modifier
                                    .height(ComponentSize.TopBarButtonSize)
                                    .clip(GlassShapes.Small)
                                    .background(
                                        if (isBlueGlass) {
                                            PluginThemeColors.CardBackground.copy(alpha = AlphaTokens.Half)
                                        } else {
                                            colors.cardBackground
                                        },
                                    )
                                    .border(
                                        width = IdeShellDimens.HairlineBorder,
                                        color = if (isBlueGlass) PluginThemeColors.CardBorder else colors.border,
                                        shape = GlassShapes.Small,
                                    )
                                    .padding(horizontal = Spacing.SmallMedium),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = if (isBlueGlass) BrandColors.OnLogoBadge.copy(alpha = AlphaTokens.Half) else colors.textMuted,
                                    modifier = Modifier.size(IconSize.Search),
                                 )
                                Text(
                                    text = "Search files, actions, ...",
                                    fontSize = FontSize.LabelSmall,
                                    color = if (isBlueGlass) BrandColors.OnLogoBadge.copy(alpha = AlphaTokens.Half) else colors.textMuted,
                                )
                                Spacer(modifier = Modifier.width(Spacing.Small))
                                Box(
                                    modifier = Modifier
                                        .clip(GlassShapes.ExtraSmall)
                                        .background(if (isBlueGlass) PluginThemeColors.CardBackground else colors.controlSelected)
                                        .padding(horizontal = Spacing.ExtraSmall, vertical = Spacing.Hairline),
                                ) {
                                    Text(
                                        text = "Ctrl+K",
                                        fontFamily = codeFontFamily(),
                                        fontSize = FontSize.LabelSmall,
                                        color = if (isBlueGlass) BrandColors.OnLogoBadge.copy(alpha = AlphaTokens.High) else colors.textDim,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(Spacing.SmallMedium))

                            // User Profile Avatar Badge
                            Box(
                                modifier = Modifier
                                    .size(ComponentSize.TopBarButtonSize)
                                    .clip(CircleShape)
                                    .background(
                                        if (isBlueGlass) {
                                            PluginThemeColors.ActiveTabBackground
                                        } else {
                                            colors.controlSelected
                                        },
                                    )
                                    .border(
                                        width = IdeShellDimens.HairlineBorder,
                                        color = if (isBlueGlass) PluginThemeColors.ActiveTabBorder else colors.accentBlue,
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "JD",
                                    fontSize = FontSize.LabelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBlueGlass) BrandColors.OnLogoBadge else colors.textMain,
                                )
                            }
                        },
                    )
                    HorizontalDivider(
                        thickness = IdeShellDimens.HairlineBorder,
                        color = if (isBlueGlass) PluginThemeColors.CardBorder else colors.border,
                    )
                }

                // Middle Body: Pipeline Rail (Optional) + Category Sidebar + Detail Content
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    if (showPipelineRail) {
                        Column(
                            modifier = Modifier
                                .width(IdeShellDimens.PipelineRailWidth)
                                .fillMaxHeight()
                                .background(colors.railBackground),
                        ) {
                            IdePipelineRail(
                                stages = pipelineStages,
                                selectedStageId = null,
                                onSelectStage = { _ -> onNavigateToWorkspace() },
                                colors = colors,
                                modifier = Modifier.weight(1f),
                            )

                            // Bottom Settings Button in Pipeline Rail (Active)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(ComponentSize.SidePanelRailButtonSize)
                                        .clip(GlassShapes.Small)
                                        .background(
                                            if (isBlueGlass) {
                                                PluginThemeColors.ActiveTabBackground
                                            } else {
                                                colors.accentBlue
                                            },
                                        )
                                        .border(
                                            width = IdeShellDimens.HairlineBorder,
                                            color = if (isBlueGlass) PluginThemeColors.ActiveTabBorder else colors.accentBlue,
                                            shape = GlassShapes.Small,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = BrandColors.OnLogoBadge,
                                        modifier = Modifier.size(IconSize.Medium),
                                    )
                                }
                            }

                            // Help & Support
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = Spacing.Small)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { /* help action */ },
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = "Help & Support",
                                    tint = if (isBlueGlass) {
                                        BrandColors.OnLogoBadge.copy(alpha = AlphaTokens.High)
                                    } else {
                                        colors.textMuted
                                    },
                                    modifier = Modifier.size(IconSize.SidePanelRail),
                                )
                                Text(
                                    text = "Help",
                                    fontSize = FontSize.Micro,
                                    color = if (isBlueGlass) {
                                        BrandColors.OnLogoBadge.copy(alpha = AlphaTokens.High)
                                    } else {
                                        colors.textMuted
                                    },
                                    maxLines = 1,
                                )
                            }
                        }

                        VerticalDivider(
                            thickness = IdeShellDimens.HairlineBorder,
                            color = if (isBlueGlass) PluginThemeColors.CardBorder else colors.border,
                        )
                    }

                    // Settings Category Navigation Sidebar
                    SettingsSidebar(
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it },
                        colors = colors,
                    )

                    VerticalDivider(
                        thickness = IdeShellDimens.HairlineBorder,
                        color = if (isBlueGlass) PluginThemeColors.CardBorder else colors.border,
                    )

                    // Right Detail Content Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                if (isBlueGlass) Color.Transparent else colors.contentBackground,
                            )
                            .padding(
                                horizontal = Spacing.ScreenHorizontal,
                                vertical = Spacing.ScreenVertical,
                            )
                            .verticalScroll(rememberScrollState()),
                    ) {
                        when (selectedCategory) {
                            SettingsCategory.EditorPreferences, SettingsCategory.Editor -> {
                                EditorSettingsPane(colors = colors)
                            }
                            SettingsCategory.Appearance -> {
                                AppearanceSettingsPane(
                                    appSettingsState = appSettingsState,
                                    onRestoreDefaults = {
                                        appSettingsState.theme = AppSettingsState.THEME_BLUE
                                        appSettingsState.effectsEnabled = true
                                        appSettingsState.reducedMotion = false
                                    },
                                    onApplyChanges = {
                                        // Persistence is automatic via AppSettingsState
                                    },
                                    colors = colors,
                                )
                            }
                            SettingsCategory.About -> {
                                AboutSettingsPane(colors = colors)
                            }
                            SettingsCategory.Notifications -> {
                                NotificationsSettingsPane(colors = colors)
                            }
                            else -> {
                                GenericCategoryPane(
                                    category = selectedCategory,
                                    colors = colors,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
