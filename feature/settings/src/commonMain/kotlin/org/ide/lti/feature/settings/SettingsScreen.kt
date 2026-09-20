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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import org.ide.lti.core.designsystem.component.layout.GlobalAppDestination
import org.ide.lti.core.designsystem.component.layout.GlobalAppTopBar
import org.ide.lti.core.designsystem.component.layout.IdeAppFrame
import org.ide.lti.core.designsystem.component.layout.IdeNavigatorItem
import org.ide.lti.core.designsystem.component.layout.IdeNavigatorPanel
import org.ide.lti.core.designsystem.component.layout.IdePipelineRail
import org.ide.lti.core.designsystem.component.layout.IdePipelineStageItem
import org.ide.lti.core.designsystem.component.layout.IdeStatusBar
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.PluginThemeColors
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.config.AppTheme
import org.ide.lti.core.ui.settings.AppSettingsState
import org.ide.lti.feature.settings.components.AboutSettingsPane
import org.ide.lti.feature.settings.components.AppearanceSettingsPane
import org.ide.lti.feature.settings.components.EditorSettingsPane
import org.ide.lti.feature.settings.components.GenericCategoryPane
import org.ide.lti.feature.settings.components.NotificationsSettingsPane
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
        val density = LocalDensity.current
        var isCompact by remember { mutableStateOf(false) }

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

        val settingsNavItems = remember {
            listOf(
                IdeNavigatorItem(id = "general", label = "General"),
                IdeNavigatorItem(id = "appearance", label = "Appearance"),
                IdeNavigatorItem(id = "editor", label = "Editor"),
                IdeNavigatorItem(id = "target_profiles", label = "Target profiles"),
                IdeNavigatorItem(id = "build_environment", label = "Build environment"),
                IdeNavigatorItem(id = "plugin_sources", label = "Plugin sources"),
                IdeNavigatorItem(id = "credentials", label = "Credentials"),
                IdeNavigatorItem(id = "notifications", label = "Notifications"),
                IdeNavigatorItem(id = "about", label = "About"),
            )
        }

        val selectedNavId = categoryToNavId(selectedCategory)

        IdeAppFrame(
            modifier = modifier
                .fillMaxSize()
                .background(colors.appBackground)
                .onSizeChanged { size ->
                    val widthDp = with(density) { size.width.toDp() }
                    isCompact = widthDp < IdeShellDimens.CompactBreakpoint
                }
                .testTag("SettingsScreen"),
            topBar = {
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
                    )
                }
            },
            pipelineRail = {
                IdePipelineRail(
                    stages = pipelineStages,
                    selectedStageId = null,
                    onSelectStage = { _ -> onNavigateToWorkspace() },
                    colors = colors,
                )
            },
            navigator = {
                IdeNavigatorPanel(
                    headerTitle = "SETTINGS",
                    headerSubtitle = null,
                    items = settingsNavItems,
                    selectedItemId = selectedNavId,
                    onSelectItem = { id -> selectedCategory = navIdToCategory(id) },
                    colors = colors,
                )
            },
            statusBar = {
                IdeStatusBar(
                    modifier = Modifier.fillMaxWidth(),
                    colors = colors,
                    leadingContent = {
                        Text(
                            text = "Settings • ${selectedCategory.title}",
                            fontSize = FontSize.LabelSmall,
                            color = colors.textDim,
                        )
                    },
                    trailingContent = {
                        Text(
                            text = "LtiRom Studio v0.9.0 • Ready",
                            fontSize = FontSize.LabelSmall,
                            color = colors.textMuted,
                        )
                    },
                )
            },
            content = {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                horizontal = Spacing.ScreenHorizontal,
                                vertical = Spacing.ScreenVertical,
                            ),
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
                                    onApplyChanges = {},
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

                    if (!isCompact) {
                        VerticalDivider(
                            thickness = IdeShellDimens.HairlineBorder,
                            color = if (isBlueGlass) PluginThemeColors.CardBorder else colors.border,
                        )
                        SettingsHelpColumn(
                            category = selectedCategory,
                            colors = colors,
                            modifier = Modifier
                                .width(IdeShellDimens.WorkspaceNavigatorWidth)
                                .fillMaxHeight()
                                .padding(Spacing.Medium),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun SettingsHelpColumn(
    category: SettingsCategory,
    colors: IdeShellColorScheme,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "About these settings",
            fontSize = FontSize.TitleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.textMain,
        )

        Text(
            text = category.subtitle,
            fontSize = FontSize.BodySmall,
            color = colors.textDim,
        )

        HorizontalDivider(
            thickness = IdeShellDimens.HairlineBorder,
            color = colors.border,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Text(
                text = "Preferences storage",
                fontSize = FontSize.LabelSmall,
                fontWeight = FontWeight.Medium,
                color = colors.textMain,
            )
            Text(
                text = "Studio settings are synchronized across workspaces and persisted locally.",
                fontSize = FontSize.LabelSmall,
                color = colors.textMuted,
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Text(
                text = "Quick navigation",
                fontSize = FontSize.LabelSmall,
                fontWeight = FontWeight.Medium,
                color = colors.textMain,
            )
            Text(
                text = "Use Ctrl+, to open settings or Ctrl+K to search files and studio actions.",
                fontSize = FontSize.LabelSmall,
                color = colors.textMuted,
            )
        }
    }
}

private fun categoryToNavId(category: SettingsCategory): String = when (category) {
    SettingsCategory.General,
    SettingsCategory.Workspace,
    SettingsCategory.PrivacyAndData,
    -> "general"
    SettingsCategory.Appearance -> "appearance"
    SettingsCategory.Editor,
    SettingsCategory.EditorPreferences,
    -> "editor"
    SettingsCategory.TargetProfiles -> "target_profiles"
    SettingsCategory.BuildEnvironment,
    SettingsCategory.BuildAndRun,
    SettingsCategory.Paths,
    -> "build_environment"
    SettingsCategory.PluginSources -> "plugin_sources"
    SettingsCategory.Credentials -> "credentials"
    SettingsCategory.Notifications -> "notifications"
    SettingsCategory.About,
    SettingsCategory.Updates,
    SettingsCategory.Diagnostics,
    SettingsCategory.Network,
    -> "about"
}

private fun navIdToCategory(id: String): SettingsCategory = when (id) {
    "general" -> SettingsCategory.General
    "appearance" -> SettingsCategory.Appearance
    "editor" -> SettingsCategory.EditorPreferences
    "target_profiles" -> SettingsCategory.TargetProfiles
    "build_environment" -> SettingsCategory.BuildEnvironment
    "plugin_sources" -> SettingsCategory.PluginSources
    "credentials" -> SettingsCategory.Credentials
    "notifications" -> SettingsCategory.Notifications
    "about" -> SettingsCategory.About
    else -> SettingsCategory.General
}
