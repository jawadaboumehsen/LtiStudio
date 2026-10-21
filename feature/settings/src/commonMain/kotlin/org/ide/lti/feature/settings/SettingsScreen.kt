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
import org.ide.lti.core.domain.ports.AppEnvironmentPort
import org.ide.lti.core.model.user.AppearanceSettings
import org.ide.lti.core.ui.settings.AppSettingsState
import org.ide.lti.feature.settings.components.AboutSettingsPane
import org.ide.lti.feature.settings.components.AppearanceSettingsPane
import org.ide.lti.feature.settings.model.SettingsCategory
import org.koin.compose.getKoin
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
    val koin = getKoin()
    SettingsScreenContent(
        environmentPort = remember(koin) { koin.getOrNull<AppEnvironmentPort>() },
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
    initialCategory: SettingsCategory = SettingsCategory.Appearance,
    /** What About shows about the running app; null in previews and tests. */
    environmentPort: AppEnvironmentPort? = null,
) {
    val currentTheme = AppTheme.fromId(appSettingsState.theme)
    val environment = remember(environmentPort) { environmentPort?.environment() }

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
                IdeNavigatorItem(id = "appearance", label = "Appearance"),
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
                            text = environment?.version?.let { "LtiRom Studio $it" }
                                ?: "LtiRom Studio · development build",
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
                        SettingsCategory.Appearance -> {
                            AppearanceSettingsPane(
                                appSettingsState = appSettingsState,
                                onRestoreDefaults = {
                                    appSettingsState.theme = AppSettingsState.THEME_BLUE
                                    appSettingsState.effectsEnabled = true
                                    appSettingsState.reducedMotion = false
                                    appSettingsState.appearance = AppearanceSettings()
                                },
                            )
                        }
                        SettingsCategory.About -> {
                            AboutSettingsPane(
                                environment = environment,
                                onOpenFolder = { environmentPort?.openFolder(it) },
                            )
                        }
                    }
                }
            },
        )
    }
}

private fun categoryToNavId(category: SettingsCategory): String = when (category) {
    SettingsCategory.Appearance -> "appearance"
    SettingsCategory.About -> "about"
}

private fun navIdToCategory(id: String): SettingsCategory =
    if (id == "about") SettingsCategory.About else SettingsCategory.Appearance
