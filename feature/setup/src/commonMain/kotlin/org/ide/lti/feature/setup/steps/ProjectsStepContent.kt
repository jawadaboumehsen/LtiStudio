/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.ide.lti.core.designsystem.component.actions.GlassActionCard
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.actions.GlassItemCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.GlassEmptyState
import org.ide.lti.core.designsystem.component.display.GlassEmptyStateOrientation
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.TextFieldLeadingSlot
import org.ide.lti.core.designsystem.component.inputs.TextFieldTrailingSlot
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.ideWelcomeFromVCSTab
import org.ide.lti.core.designsystem.icon.ideWelcomeOpen
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.feature.setup.SetupUiActions
import org.ide.lti.feature.setup.SetupUiState
import org.ide.lti.feature.setup.components.TargetSelectorCard

/**
 * Structured overload for [ProjectsStepContent] utilizing [SetupUiState] and [SetupUiActions]
 * to prevent parameter sprawl and ensure single-responsibility composition.
 */
@Composable
fun ProjectsStepContent(
    state: SetupUiState,
    actions: SetupUiActions,
    onNavigateToEnvironment: () -> Unit,
    resolvedBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    ProjectsStepContent(
        recentProjects = state.recentProjects,
        filteredProjects = state.filteredProjects,
        searchQuery = state.searchQuery,
        onSearchQueryChange = actions.onSearchQueryChange,
        onRemoveRecentProject = actions.onRemoveRecentProject,
        onOpenFolder = actions.onOpenFolder,
        onCloneGit = actions.onCloneGit,
        onOpenDefaultWorkspace = actions.onProceedToWorkspace,
        onCreateWorkspace = actions.onCreateWorkspace,
        onRecentProjectClick = actions.onRecentProjectClick,
        toolchainSetupState = state.toolchainSetupState,
        selectedTarget = state.selectedTarget,
        availableTargets = state.availableTargets,
        onSelectTargetAndLaunch = actions.onSelectTargetAndLaunch,
        onNavigateToEnvironment = onNavigateToEnvironment,
        resolvedBackdrop = resolvedBackdrop,
        modifier = modifier,
    )
}

/**
 * Projects & Launchpad Hub - The premier welcome screen cockpit for LtiRom Studio.
 *
 * Implements modern developer experience benchmarks (JetBrains Fleet/IntelliJ, Zed, Cursor, Linear):
 * - Hero Quick Action Cards with keyboard shortcut hints.
 * - Live Ambient Doctor Health Badge.
 * - Reactive workspace search and filtering with token-compliant glass inputs.
 * - Recent project list with device target metadata chips, relative timestamps, and deletion controls.
 */
@Composable
fun ProjectsStepContent(
    recentProjects: List<RecentProject>,
    filteredProjects: List<RecentProject>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onRemoveRecentProject: (String) -> Unit,
    onOpenFolder: () -> Unit,
    onCloneGit: () -> Unit,
    onOpenDefaultWorkspace: () -> Unit,
    onRecentProjectClick: (RecentProject) -> Unit,
    toolchainSetupState: ToolchainSetupState,
    onNavigateToEnvironment: () -> Unit,
    resolvedBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    selectedTarget: TargetDevice? = null,
    availableTargets: List<TargetDevice> = emptyList(),
    onSelectTargetAndLaunch: (TargetDevice) -> Unit = {},
    onCreateWorkspace: () -> Unit = {},
) {
    val glassColors = GlassTheme.colors
    val diagnostics = glassColors.diagnostics

    val isToolchainReady = toolchainSetupState.isAllReady
    val totalSteps = toolchainSetupState.steps.size
    val readySteps = toolchainSetupState.steps.count { it.status == StepStatus.SUCCESS }

    LazyColumn(
        modifier = modifier
            .widthIn(max = ComponentSize.SetupCockpitMaxWidth)
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(
                start = Spacing.ScreenHorizontal,
                end = Spacing.ScreenHorizontal,
                top = Spacing.ScreenVertical,
                bottom = Spacing.ScreenVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
    ) {
        // Hero Header Row with Ambient Doctor Badge (FrameLayout / Box Anchored Layout)
        item(key = "hero_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Text(
                        text = "LtiRom Studio",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = glassColors.onGlass,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Ultra-clean Android ROM engineering, partition disassembly, and signed flashing cockpit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = glassColors.onGlassSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Live Ambient Toolchain & Doctor Health Badge
                val badgeStatusColor = when {
                    toolchainSetupState.isBusy -> glassColors.glassAccent
                    isToolchainReady -> diagnostics.success
                    else -> diagnostics.warning
                }
                val badgeText = when {
                    toolchainSetupState.isFixing -> "Doctor Auto-Fixing Packages..."
                    toolchainSetupState.isRunning -> "Auto-Provisioning Tools ($readySteps/$totalSteps)..."
                    toolchainSetupState.isChecking -> "Doctor Checking Health..."
                    isToolchainReady -> "Doctor Verified & Ready ($readySteps/$totalSteps)"
                    else -> "Doctor Setup & Auto-Fix ($readySteps/$totalSteps)"
                }

                Box(
                    modifier = Modifier
                        .clip(GlassShapes.Capsule)
                        .background(badgeStatusColor.copy(alpha = AlphaTokens.Glow))
                        .border(
                            width = StrokeWidth.Hairline,
                            color = badgeStatusColor.copy(alpha = AlphaTokens.Border),
                            shape = GlassShapes.Capsule,
                        )
                        .clickable(onClick = onNavigateToEnvironment)
                        .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Small),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        if (toolchainSetupState.isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(IconSize.Small),
                                strokeWidth = StrokeWidth.Hairline,
                                color = badgeStatusColor,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(IconSize.Indicator)
                                    .clip(GlassShapes.Capsule)
                                    .background(badgeStatusColor),
                            )
                        }
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = codeFontFamily(),
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = badgeStatusColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Target Hardware Selector Card
        item(key = "target_selector") {
            TargetSelectorCard(
                selectedTarget = selectedTarget,
                availableTargets = availableTargets,
                onSelectTargetAndLaunch = onSelectTargetAndLaunch,
                backdrop = resolvedBackdrop,
            )
        }

        // Hero Quick Action Cards Symmetrically Balanced Across the Row
        item(key = "quick_actions") {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                GlassActionCard(
                    title = "New Workspace",
                    subtitle = "Create independent workspace for target",
                    icon = AppIcons.Add,
                    isPrimary = true,
                    shortcut = "Ctrl+N",
                    onClick = onCreateWorkspace,
                    backdrop = resolvedBackdrop,
                    modifier = Modifier.weight(1f, fill = false).widthIn(min = ComponentSize.MinPanelWidth),
                )
                GlassActionCard(
                    title = "Open Workspace",
                    subtitle = "Browse local directory or mount",
                    icon = AppIcon.Painted(AppIcons.ideWelcomeOpen),
                    isPrimary = false,
                    shortcut = "Ctrl+O",
                    onClick = onOpenFolder,
                    backdrop = resolvedBackdrop,
                    modifier = Modifier.weight(1f, fill = false).widthIn(min = ComponentSize.MinPanelWidth),
                )
                GlassActionCard(
                    title = "Clone Git",
                    subtitle = "Pull device manifests or trees",
                    icon = AppIcon.Painted(AppIcons.ideWelcomeFromVCSTab),
                    isPrimary = false,
                    shortcut = "Ctrl+Shift+G",
                    onClick = onCloneGit,
                    backdrop = resolvedBackdrop,
                    modifier = Modifier.weight(1f, fill = false).widthIn(min = ComponentSize.MinPanelWidth),
                )
                GlassActionCard(
                    title = "Staging WorkDir",
                    subtitle = "Default ~/LtiRomWorkDir WSL setup",
                    icon = AppIcons.Home,
                    isPrimary = false,
                    shortcut = "Ctrl+Shift+W",
                    onClick = onOpenDefaultWorkspace,
                    backdrop = resolvedBackdrop,
                    modifier = Modifier.weight(1f, fill = false).widthIn(min = ComponentSize.MinPanelWidth),
                )
            }
        }

        item(key = "divider") {
            GlassHorizontalDivider()
        }

        // Recent Workspaces Section Header & Search Input (FrameLayout / Box Anchored Layout)
        item(key = "recent_header") {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Row(
                    modifier = Modifier.padding(vertical = Spacing.ExtraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    Text(
                        text = "Recent Workspaces",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = glassColors.onGlass,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    GlassChip(
                        label = "${recentProjects.size}",
                        selected = false,
                    )
                }

                Box(
                    modifier = Modifier.widthIn(max = ComponentSize.CommandPaletteMaxWidth),
                ) {
                    GlassTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = "Filter projects... (Ctrl+F)",
                        leading = TextFieldLeadingSlot.Icon(AppIcons.Search),
                        trailing = if (searchQuery.isNotBlank()) {
                            TextFieldTrailingSlot.Clear(onClear = { onSearchQueryChange("") })
                        } else {
                            TextFieldTrailingSlot.None
                        },
                        backdrop = resolvedBackdrop,
                    )
                }
            }
        }

        // Recent Projects List or Empty State
        if (filteredProjects.isEmpty()) {
            item(key = "empty_state") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.Large),
                    contentAlignment = Alignment.Center,
                ) {
                    GlassEmptyState(
                        icon = AppIcons.Folder,
                        title = if (searchQuery.isNotBlank()) "No Matching Workspaces" else "No Recent Workspaces",
                        description = if (searchQuery.isNotBlank()) "No workspace matches '$searchQuery'. Try refining your filter."
                        else "Get started by scaffolding a new ROM or opening an existing directory.",
                        orientation = GlassEmptyStateOrientation.Vertical,
                    )
                }
            }
        } else {
            items(
                items = filteredProjects,
                key = { it.workspaceId },
                contentType = { "recent_project" },
            ) { project ->
                GlassItemCard(
                    title = project.name,
                    subtitle = project.path,
                    icon = AppIcons.Folder,
                    onClick = { onRecentProjectClick(project) },
                    backdrop = resolvedBackdrop,
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        ) {
                            project.targetDisplayName?.takeIf { it.isNotBlank() }?.let { targetName ->
                                GlassChip(
                                    label = targetName,
                                    selected = false,
                                )
                            }
                            if (project.targetBinding == null) {
                                GlassChip(
                                    label = "Needs target",
                                    selected = false,
                                )
                            }
                            GlassChip(
                                label = formatRelativeTime(project.lastOpened),
                                selected = false,
                            )
                            GlassIconButton(
                                icon = AppIcons.Close,
                                onClick = { onRemoveRecentProject(project.workspaceId) },
                            )
                        }
                    },
                )
            }
        }
    }
}

private fun formatRelativeTime(instant: Instant): String {
    val now = Clock.System.now()
    val duration = now - instant
    return when {
        duration.inWholeMinutes < 1 -> "Just now"
        duration.inWholeMinutes < 60 -> "${duration.inWholeMinutes}m ago"
        duration.inWholeHours < 24 -> "${duration.inWholeHours}h ago"
        duration.inWholeDays < 7 -> "${duration.inWholeDays}d ago"
        else -> "${duration.inWholeDays / 7}w ago"
    }
}
