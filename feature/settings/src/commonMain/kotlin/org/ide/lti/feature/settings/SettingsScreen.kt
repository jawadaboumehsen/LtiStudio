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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.mutableIntStateOf
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import org.ide.lti.core.data.settings.AppSettingsState
import org.ide.lti.core.designsystem.component.GlassCard
import org.ide.lti.core.designsystem.component.GlassChip
import org.ide.lti.core.designsystem.component.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.GlassIconButton
import org.ide.lti.core.designsystem.component.GlassStatusBar
import org.ide.lti.core.designsystem.component.GlassToggle
import org.ide.lti.core.designsystem.component.GlassToggleTile
import org.ide.lti.core.designsystem.component.GlassTopBar
import org.ide.lti.core.designsystem.component.GlassValueTile
import org.ide.lti.core.designsystem.component.LineEnding
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.ideGeneralLanguage
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.koin.compose.koinInject

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    backdrop: Backdrop? = null,
    appSettingsState: AppSettingsState = koinInject(),
) {
    SettingsScreenContent(
        modifier = modifier.fillMaxSize(),
        onBackClick = onBackClick,
        showHeader = showHeader,
        backdrop = backdrop,
        appSettingsState = appSettingsState,
    )
}

@Composable
fun SettingsScreenContent(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    backdrop: Backdrop? = null,
    appSettingsState: AppSettingsState = koinInject(),
) {
    val localBackdrop = rememberLayerBackdrop()
    val resolvedBackdrop = (backdrop as? LayerBackdrop) ?: (GlassTheme.backdrop as? LayerBackdrop) ?: localBackdrop
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    val categories = remember { listOf("Appearance", "Editor", "Notifications", "About") }

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
                GlassTopBar(
                    title = "LtiRom",
                    workspaceName = "Settings",
                    onBackClick = onBackClick,
                    showMenuButton = false,
                    showPanelToggles = false,
                    showCommandPalette = false,
                    showSearchButton = false,
                    backdrop = resolvedBackdrop,
                    drawOwnChrome = true,
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = ComponentSize.SettingsMaxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.ScreenVertical)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
                ) {
                    // Header & Category Chips Bar
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                                Text(
                                    text = "Preferences",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = GlassTheme.colors.onGlass,
                                )
                                Text(
                                    text = "Configure your IDE environment, editor experience, and themes",
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

                        // Category Tabs Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            categories.forEachIndexed { index, name ->
                                GlassChip(
                                    label = name,
                                    selected = selectedCategoryIndex == index,
                                    onClick = { selectedCategoryIndex = index },
                                )
                            }
                        }
                    }

                    // Selected Category Content
                    when (selectedCategoryIndex) {
                        0 -> {
                            // Appearance
                            GlassCard(
                                contentPadding = Spacing.CardPadding,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.CardPadding)) {
                                    Text(
                                        text = "Appearance & Interface",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GlassTheme.colors.onGlass,
                                    )

                                    GlassToggleTile(
                                        icon = AppIcons.Sparkles,
                                        title = "Dark Mode",
                                        description = "AMOLED true-black surfaces with vibrant light-blue accents",
                                        checked = appSettingsState.isDarkTheme,
                                        onCheckedChange = { appSettingsState.isDarkTheme = it },
                                    )

                                    GlassHorizontalDivider()

                                    GlassToggleTile(
                                        icon = AppIcons.LayoutSplit,
                                        title = "Fluid Glass Effects",
                                        description = "GPU-accelerated lens refraction, vibrancy, and specular hairlines",
                                        checked = appSettingsState.effectsEnabled,
                                        onCheckedChange = { appSettingsState.effectsEnabled = it },
                                    )

                                    GlassHorizontalDivider()

                                    GlassToggleTile(
                                        icon = AppIcons.Info,
                                        title = "Reduced Motion",
                                        description = "Disable non-essential spring and fluid animations for accessibility",
                                        checked = appSettingsState.reducedMotion,
                                        onCheckedChange = { appSettingsState.reducedMotion = it },
                                    )

                                    GlassHorizontalDivider()

                                    GlassValueTile(
                                        icon = AppIcon.Painted(AppIcons.ideGeneralLanguage),
                                        title = "Language",
                                        description = "Display language for IDE interface",
                                        value = "English (US)",
                                    )

                                    GlassHorizontalDivider()

                                    GlassValueTile(
                                        icon = AppIcons.Sparkles,
                                        title = "Accent Palette",
                                        description = "Primary liquid glass highlight tone",
                                        value = "Light Blue (#4FA8FF)",
                                    )
                                }
                            }
                        }

                        1 -> {
                            // Editor
                            GlassCard(
                                contentPadding = Spacing.CardPadding,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.CardPadding)) {
                                    Text(
                                        text = "Editor Preferences",
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

                                    GlassHorizontalDivider()

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
                            // Notifications
                            GlassCard(
                                contentPadding = Spacing.CardPadding,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.CardPadding)) {
                                    Text(
                                        text = "Alerts & Notifications",
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

                                    GlassHorizontalDivider()

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
                            // About & Diagnostics
                            GlassCard(
                                contentPadding = Spacing.CardPadding,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.CardPadding)) {
                                    Text(
                                        text = "System & Architecture",
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

                                    GlassHorizontalDivider()

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
                }
            }

            // Bottom Unified Liquid Glass Status Bar
            GlassStatusBar(
                filePath = "Settings",
                branchName = "preferences",
                encoding = "UTF-8",
                lineEnding = LineEnding.LF,
                fileType = "Configuration",
                cursorPosition = null,
                drawOwnChrome = true,
                backdrop = resolvedBackdrop,
            )
        }
    }
}

