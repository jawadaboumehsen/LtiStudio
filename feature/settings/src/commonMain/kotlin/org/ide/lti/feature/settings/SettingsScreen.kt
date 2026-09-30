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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import org.ide.lti.core.designsystem.component.layout.GlobalAppDestination
import org.ide.lti.core.designsystem.component.layout.GlobalAppTopBar
import org.ide.lti.core.designsystem.component.layout.IdeAppFrame
import org.ide.lti.core.designsystem.component.layout.IdeNavigatorItem
import org.ide.lti.core.designsystem.component.layout.IdeNavigatorPanel
import org.ide.lti.core.designsystem.component.layout.IdeRightRail
import org.ide.lti.core.designsystem.component.layout.IdeStatusBar
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.Spacing
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
    appSettingsState: AppSettingsState = koinInject(),
) {
    SettingsScreenContent(
        modifier = modifier.fillMaxSize(),
        onBackClick = onBackClick,
        onNavigateToSetup = onNavigateToSetup,
        onNavigateToWorkspace = onNavigateToWorkspace,
        showHeader = showHeader,
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
    appSettingsState: AppSettingsState = koinInject(),
    initialCategory: SettingsCategory = SettingsCategory.EditorPreferences,
) {
    val currentTheme = AppTheme.fromId(appSettingsState.theme)

    LtiTheme(
        appTheme = currentTheme,
        effectsEnabled = appSettingsState.effectsEnabled,
        reducedMotion = appSettingsState.reducedMotion,
    ) {
        val density = LocalDensity.current
        var isCompact by remember { mutableStateOf(false) }

        var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory) }

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
                .onSizeChanged { size ->
                    val widthDp = with(density) { size.width.toDp() }
                    isCompact = widthDp < GlassDimens.CompactBreakpoint
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
                    )
                }
            },
            navigator = {
                IdeNavigatorPanel(
                    headerTitle = "SETTINGS",
                    headerSubtitle = null,
                    items = settingsNavItems,
                    selectedItemId = selectedNavId,
                    onSelectItem = { id -> selectedCategory = navIdToCategory(id) },
                    iconsOnly = true,
                )
            },
            statusBar = {
                IdeStatusBar(
                    modifier = Modifier.fillMaxWidth(),
                    leadingContent = {
                        Text(
                            text = "Settings • ${selectedCategory.title}",
                            fontSize = FontSize.LabelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Text(
                            text = "LtiRom Studio v0.9.0 • Ready",
                            fontSize = FontSize.LabelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            },
            rightPanel = {
                IdeRightRail(
                    items = emptyList(),
                    modifier = Modifier.testTag("SettingsRightRail"),
                )
            },
            content = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = Spacing.ScreenHorizontal,
                            vertical = Spacing.ScreenVertical,
                        ),
                ) {
                    when (selectedCategory) {
                        SettingsCategory.EditorPreferences, SettingsCategory.Editor -> {
                            EditorSettingsPane()
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
                            )
                        }
                        SettingsCategory.About -> {
                            AboutSettingsPane()
                        }
                        SettingsCategory.Notifications -> {
                            NotificationsSettingsPane()
                        }
                        else -> {
                            GenericCategoryPane(
                                category = selectedCategory,
                            )
                        }
                    }
                }
            },
        )
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
