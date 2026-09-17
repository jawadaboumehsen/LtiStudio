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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import org.ide.lti.core.data.settings.AppSettingsState
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.display.GlassToggleTile
import org.ide.lti.core.designsystem.component.display.GlassValueTile
import org.ide.lti.core.designsystem.component.display.GlassVerticalDivider
import org.ide.lti.core.designsystem.component.display.ThemePreviewCard
import org.ide.lti.core.designsystem.component.layout.GlobalAppDestination
import org.ide.lti.core.designsystem.component.layout.GlobalAppTopBar
import org.ide.lti.core.designsystem.component.navigation.GlassSidebarItem
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.ideGeneralLanguage
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.config.AppTheme
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
    val localBackdrop = rememberLayerBackdrop()
    val resolvedBackdrop = (backdrop as? LayerBackdrop) ?: (GlassTheme.backdrop as? LayerBackdrop) ?: localBackdrop
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    var pushEnabled by remember { mutableStateOf(true) }
    var soundEnabled by remember { mutableStateOf(false) }
    var autoSaveEnabled by remember { mutableStateOf(true) }
    var lineNumbersEnabled by remember { mutableStateOf(true) }
    var bracketMatchingEnabled by remember { mutableStateOf(true) }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
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
                    contextLabel = "Preferences",
                )
            }

            // Master-Detail Two-Pane Preferences Layout
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Left Categories Navigation Sidebar
                Column(
                    modifier = Modifier
                        .width(ComponentSize.SettingsSidebarWidth)
                        .fillMaxHeight()
                        .background(GlassTheme.colors.glassSurface.copy(alpha = AlphaTokens.Hover))
                        .padding(Spacing.PanelPadding),
                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Text(
                        text = "PREFERENCES",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassTheme.colors.onGlassMuted,
                        modifier = Modifier.padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
                    )

                    GlassSidebarItem(
                        label = "Appearance",
                        icon = AppIcons.Sparkles,
                        selected = selectedCategoryIndex == 0,
                        onClick = { selectedCategoryIndex = 0 },
                    )

                    GlassSidebarItem(
                        label = "Editor",
                        icon = AppIcons.Edit,
                        selected = selectedCategoryIndex == 1,
                        onClick = { selectedCategoryIndex = 1 },
                    )

                    GlassSidebarItem(
                        label = "Notifications",
                        icon = AppIcon.Painted(AppIcons.NotificationsPainterResource),
                        selected = selectedCategoryIndex == 2,
                        onClick = { selectedCategoryIndex = 2 },
                    )

                    GlassSidebarItem(
                        label = "About",
                        icon = AppIcons.Info,
                        selected = selectedCategoryIndex == 3,
                        onClick = { selectedCategoryIndex = 3 },
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    GlassHorizontalDivider()
                    Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

                    Column(
                        modifier = Modifier.padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
                        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
                    ) {
                        Text(
                            text = "LtiRom Studio",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = GlassTheme.colors.onGlass,
                        )
                        Text(
                            text = "v0.0.1-beta.0.118",
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassTheme.colors.onGlassMuted,
                        )
                    }
                }

                // Sleek Specular Hairline Vertical Divider
                GlassVerticalDivider(specular = true)

                // Right Detail Content Area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.ScreenVertical)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
                ) {
                    when (selectedCategoryIndex) {
                        0 -> {
                            // Appearance & Interface
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                                    Text(
                                        text = "Appearance & Interface",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GlassTheme.colors.onGlass,
                                    )
                                    Text(
                                        text = "Customize the visual theme, glass refraction, and motion ergonomics",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = GlassTheme.colors.onGlassSecondary,
                                    )
                                }

                                GlassChip(
                                    label = "Synced",
                                    selected = false,
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(IconSize.Indicator)
                                                .clip(GlassShapes.Capsule)
                                                .background(GlassTheme.colors.glassAccent),
                                        )
                                    },
                                )
                            }

                            // Theme Selection: Visual Theme Cards Grid
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium)) {
                                Text(
                                    text = "Workspace Theme",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = GlassTheme.colors.onGlass,
                                )
                                Text(
                                    text = "Select a bespoke visual palette. Blue features signature cosmic liquid glass, Dark provides high-contrast obsidian matte focus, and Light delivers clean frosted clarity.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GlassTheme.colors.onGlassSecondary,
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                                ) {
                                    AppTheme.allThemes.forEach { theme ->
                                        ThemePreviewCard(
                                            theme = theme,
                                            isSelected = appSettingsState.theme == theme.id,
                                            onClick = { appSettingsState.theme = theme.id },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }

                            // Display & Glass Rendering Group
                            GlassCard(
                                contentPadding = Spacing.CardPadding,
                                modifier = Modifier.fillMaxWidth(),
                                backdrop = resolvedBackdrop,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.CardPadding)) {
                                    Text(
                                        text = "Display & Glass Rendering",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GlassTheme.colors.onGlass,
                                    )

                                    GlassToggleTile(
                                        icon = AppIcons.LayoutSplit,
                                        title = "Fluid Glass Effects",
                                        description = "GPU-accelerated lens refraction, vibrancy, and specular hairlines",
                                        checked = appSettingsState.effectsEnabled,
                                        onCheckedChange = { appSettingsState.effectsEnabled = it },
                                    )

                                    GlassHorizontalDivider()

                                    GlassToggleTile(
                                        icon = AppIcons.Refresh,
                                        title = "Reduced Motion",
                                        description = "Disable non-essential spring and fluid animations for accessibility",
                                        checked = appSettingsState.reducedMotion,
                                        onCheckedChange = { appSettingsState.reducedMotion = it },
                                    )
                                }
                            }

                            // Localization & Environment Group
                            GlassCard(
                                contentPadding = Spacing.CardPadding,
                                modifier = Modifier.fillMaxWidth(),
                                backdrop = resolvedBackdrop,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.CardPadding)) {
                                    Text(
                                        text = "Localization & Language",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GlassTheme.colors.onGlass,
                                    )

                                    GlassValueTile(
                                        icon = AppIcon.Painted(AppIcons.ideGeneralLanguage),
                                        title = "Interface Language",
                                        description = "Display language for IDE menus, dialogs, and workspace panes",
                                        value = "English (US)",
                                    )
                                }
                            }
                        }

                        1 -> {
                            // Editor Preferences
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                                    Text(
                                        text = "Editor Preferences",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GlassTheme.colors.onGlass,
                                    )
                                    Text(
                                        text = "Configure code typography, gutter elements, and bracket colorization",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = GlassTheme.colors.onGlassSecondary,
                                    )
                                }
                            }

                            // Typography & Formatting Group
                            GlassCard(
                                contentPadding = Spacing.CardPadding,
                                modifier = Modifier.fillMaxWidth(),
                                backdrop = resolvedBackdrop,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.CardPadding)) {
                                    Text(
                                        text = "Typography & Spacing",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GlassTheme.colors.onGlass,
                                    )

                                    GlassValueTile(
                                        icon = AppIcon.Painted(AppIcons.FilePainterResource),
                                        title = "Font Family",
                                        description = "Monospace coding font used in editor panes",
                                        value = "JetBrains Mono",
                                    )

                                    GlassHorizontalDivider()

                                    GlassValueTile(
                                        icon = AppIcons.Edit,
                                        title = "Font Size",
                                        description = "Base font size for code tokens",
                                        value = "13 sp",
                                    )

                                    GlassHorizontalDivider()

                                    GlassValueTile(
                                        icon = AppIcons.LayoutSplit,
                                        title = "Tab Size",
                                        description = "Number of spaces per indentation level",
                                        value = "4 spaces",
                                    )
                                }
                            }

                            // Gutter & Delimiters Group
                            GlassCard(
                                contentPadding = Spacing.CardPadding,
                                modifier = Modifier.fillMaxWidth(),
                                backdrop = resolvedBackdrop,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.CardPadding)) {
                                    Text(
                                        text = "Gutter & Syntax Display",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GlassTheme.colors.onGlass,
                                    )

                                    GlassToggleTile(
                                        icon = AppIcons.Show,
                                        title = "Line Numbers",
                                        description = "Display line numbers in editor gutter",
                                        checked = lineNumbersEnabled,
                                        onCheckedChange = { lineNumbersEnabled = it },
                                    )

                                    GlassHorizontalDivider()

                                    GlassToggleTile(
                                        icon = AppIcons.Check,
                                        title = "Bracket Pair Colorization",
                                        description = "Highlight matching delimiters with distinct tints",
                                        checked = bracketMatchingEnabled,
                                        onCheckedChange = { bracketMatchingEnabled = it },
                                    )
                                }
                            }
                        }

                        2 -> {
                            // Notifications & Persistence
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                                    Text(
                                        text = "Alerts & Notifications",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GlassTheme.colors.onGlass,
                                    )
                                    Text(
                                        text = "Manage system notifications, sound feedback, and file persistence",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = GlassTheme.colors.onGlassSecondary,
                                    )
                                }
                            }

                            GlassCard(
                                contentPadding = Spacing.CardPadding,
                                modifier = Modifier.fillMaxWidth(),
                                backdrop = resolvedBackdrop,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.CardPadding)) {
                                    Text(
                                        text = "System Alerts & Audio",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GlassTheme.colors.onGlass,
                                    )

                                    GlassToggleTile(
                                        icon = AppIcon.Painted(AppIcons.NotificationsPainterResource),
                                        title = "Push Notifications",
                                        description = "Receive workspace alerts and background task notifications",
                                        checked = pushEnabled,
                                        onCheckedChange = { pushEnabled = it },
                                    )

                                    GlassHorizontalDivider()

                                    GlassToggleTile(
                                        icon = AppIcons.Sparkles,
                                        title = "Sound Effects",
                                        description = "Play subtle auditory cues on build completion and diagnostics",
                                        checked = soundEnabled,
                                        onCheckedChange = { soundEnabled = it },
                                    )
                                }
                            }

                            GlassCard(
                                contentPadding = Spacing.CardPadding,
                                modifier = Modifier.fillMaxWidth(),
                                backdrop = resolvedBackdrop,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.CardPadding)) {
                                    Text(
                                        text = "Persistence & Window Focus",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GlassTheme.colors.onGlass,
                                    )

                                    GlassToggleTile(
                                        icon = AppIcons.Save,
                                        title = "Auto-Save on Focus Lost",
                                        description = "Automatically save modified files when switching windows",
                                        checked = autoSaveEnabled,
                                        onCheckedChange = { autoSaveEnabled = it },
                                    )
                                }
                            }
                        }

                        3 -> {
                            // System & About
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                                    Text(
                                        text = "System & Architecture",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GlassTheme.colors.onGlass,
                                    )
                                    Text(
                                        text = "Distribution build, graphics acceleration, and accessibility standard",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = GlassTheme.colors.onGlassSecondary,
                                    )
                                }
                            }

                            GlassCard(
                                contentPadding = Spacing.CardPadding,
                                modifier = Modifier.fillMaxWidth(),
                                backdrop = resolvedBackdrop,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.CardPadding)) {
                                    Text(
                                        text = "Distribution",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GlassTheme.colors.onGlass,
                                    )

                                    GlassValueTile(
                                        icon = AppIcons.Sparkles,
                                        title = "Application",
                                        description = "Liquid Glass IDE Studio",
                                        value = "LtiRom Desktop",
                                    )

                                    GlassHorizontalDivider()

                                    GlassValueTile(
                                        icon = AppIcons.Info,
                                        title = "Build Version",
                                        description = "Current software distribution",
                                        value = "0.0.1-beta.0.118",
                                    )
                                }
                            }

                            GlassCard(
                                contentPadding = Spacing.CardPadding,
                                modifier = Modifier.fillMaxWidth(),
                                backdrop = resolvedBackdrop,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.CardPadding)) {
                                    Text(
                                        text = "Runtime Stack & Graphics",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GlassTheme.colors.onGlass,
                                    )

                                    GlassValueTile(
                                        icon = AppIcons.Settings,
                                        title = "Framework Stack",
                                        description = "Core compiler and declarative UI runtime",
                                        value = "Kotlin 2.1.0 · Compose 1.9.0",
                                    )

                                    GlassHorizontalDivider()

                                    GlassValueTile(
                                        icon = AppIcons.LayoutSplit,
                                        title = "Graphics Engine",
                                        description = "Skiko Skia GPU Acceleration",
                                        value = "DirectX 12 / Metal / Vulkan",
                                    )

                                    GlassHorizontalDivider()

                                    GlassValueTile(
                                        icon = AppIcons.Check,
                                        title = "Accessibility Standard",
                                        description = "Contrast ratio & readability compliance",
                                        value = "WCAG 2.1 AA Compliant",
                                    )
                                }
                            }
                        }
                    }

                    // Generous bottom breathing room so cards never truncate against window bottom edge
                    Spacer(modifier = Modifier.height(Spacing.ExtraExtraLarge))
                }
            }
        }
    }
}
