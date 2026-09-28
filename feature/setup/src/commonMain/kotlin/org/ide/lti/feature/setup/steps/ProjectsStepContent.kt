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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.actions.GlassTextButton
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.GlassEmptyState
import org.ide.lti.core.designsystem.component.display.GlassEmptyStateOrientation
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerAction
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.TextFieldLeadingSlot
import org.ide.lti.core.designsystem.component.inputs.TextFieldTrailingSlot
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenu
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenuItem
import org.ide.lti.core.designsystem.component.tooltip.GlassTooltipArea
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.SetupTokens
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.feature.setup.PendingWorkspaceRequest
import org.ide.lti.feature.setup.ProjectsHeaderStatusPresentation
import org.ide.lti.feature.setup.SetupPresentationMapper
import org.ide.lti.feature.setup.SetupUiActions
import org.ide.lti.feature.setup.SetupUiState
import org.ide.lti.feature.setup.WorkspaceAction

/**
 * Workspaces destination content for Setup (US1 - Workspaces, P1 MVP).
 *
 * Implements Apple-inspired clean developer experience:
 * - Title + New/Open toolbar with shortcut hints (Ctrl+N, Ctrl+O).
 * - Labeled search field with clear action and shortcut hint (Ctrl+F).
 * - Virtualized recents list with target device badge, relative timestamps, selection, and context menu.
 * - Distinct empty state and no-match state.
 * - Strict design-system token discipline using SetupTokens (zero raw dp/Color/alpha literals).
 * - No global target switch, fake clone, or staging actions (FR-001, FR-002, FR-003, FR-011).
 */
@Suppress("CyclomaticComplexMethod")
@Composable
fun ProjectsStepContent(
    state: SetupUiState,
    actions: SetupUiActions,
    modifier: Modifier = Modifier,
    onNavigateToEnvironment: () -> Unit = {},
    searchFocusRequester: FocusRequester = remember { FocusRequester() },
    focusSearchOnMount: Boolean = false,
    lazyListState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val rootFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // Decided once, deterministically, on mount: honor a caller's pending Ctrl+F
        // search-focus intent (set right before switching to this destination) instead
        // of racing it against this composable's own default root-focus grab.
        if (focusSearchOnMount) {
            searchFocusRequester.requestFocus()
        } else {
            rootFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = modifier
            .widthIn(max = SetupTokens.ContentMaxWidth)
            .fillMaxWidth()
            .fillMaxHeight()
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.isCtrlPressed) {
                    when (event.key) {
                        Key.N -> {
                            actions.onCreateWorkspace()
                            true
                        }
                        Key.O -> {
                            actions.onOpenFolder()
                            true
                        }
                        Key.F -> {
                            searchFocusRequester.requestFocus()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .padding(
                start = Spacing.ScreenHorizontal,
                end = Spacing.ScreenHorizontal,
                top = Spacing.ScreenVertical,
                bottom = Spacing.ScreenVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
    ) {
        // 1. Title + New/Open Toolbar Row, with the live environment check summarized in one line so
        // browsing projects never hides that verification is still running or needs attention.
        val headerStatus = SetupPresentationMapper.mapProjectsHeaderStatus(state.toolchainSetupState)
        WorkspaceHeaderSection(
            onCreateWorkspace = actions.onCreateWorkspace,
            onOpenFolder = actions.onOpenFolder,
            headerStatus = headerStatus,
            onNavigateToEnvironment = onNavigateToEnvironment,
        )

        GlassHorizontalDivider()

        state.workspaceError?.let { error ->
            GlassBanner(
                severity = GlassBannerSeverity.ERROR,
                title = workspaceErrorTitle(error.action),
                description = error.reason + (error.workspacePath?.let { " ($it)" } ?: ""),
                primaryAction = error.retryAction?.let { retry ->
                    GlassBannerAction(
                        label = "Retry",
                        onClick = retry,
                    )
                },
                onDismiss = actions.onDismissWorkspaceError,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Pending folder open banner while verification is in progress
        val pendingPath = (state.pendingWorkspaceRequest as? PendingWorkspaceRequest.OpenPath)?.path
        if (pendingPath != null) {
            GlassBanner(
                severity = GlassBannerSeverity.INFO,
                title = "Opening Directory",
                description = "Opening $pendingPath once environment verification completes.",
                primaryAction = GlassBannerAction(
                    label = "Cancel",
                    onClick = actions.onCancelPendingWorkspaceRequest,
                ),
                onDismiss = actions.onCancelPendingWorkspaceRequest,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 2. Recent Workspaces Header & Labeled Search
        WorkspaceSearchSection(
            searchQuery = state.searchQuery,
            onSearchQueryChange = actions.onSearchQueryChange,
            totalCount = state.recentProjects.size,
            searchFocusRequester = searchFocusRequester,
        )

        // 3. Virtualized Recents List / Distinct Empty State
        if (state.filteredProjects.isEmpty()) {
            WorkspaceEmptyStateSection(searchQuery = state.searchQuery)
        } else {
            val pendingRecentId = (state.pendingWorkspaceRequest as? PendingWorkspaceRequest.OpenRecent)
                ?.project?.workspaceId
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                items(
                    items = state.filteredProjects,
                    key = { it.workspaceId },
                    contentType = { "workspace_row" },
                ) { project ->
                    WorkspaceRowItem(
                        project = project,
                        isSelected = state.selectedWorkspaceId == project.workspaceId,
                        isPendingOpen = pendingRecentId == project.workspaceId,
                        onSelect = { actions.onSelectWorkspace(project.workspaceId) },
                        onOpen = { actions.onRecentProjectClick(project) },
                        onRemove = { actions.onRemoveRecentProject(project.workspaceId) },
                        onCopyPath = {
                            clipboardManager.setText(AnnotatedString(project.path))
                        },
                        onCancelPendingOpen = actions.onCancelPendingWorkspaceRequest,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceRowItem(
    project: RecentProject,
    isSelected: Boolean,
    isPendingOpen: Boolean = false,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onCopyPath: () -> Unit,
    onCancelPendingOpen: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.Card)
            .background(backgroundColor)
            .border(
                width = if (isSelected) StrokeWidth.Focused else StrokeWidth.Standard,
                color = borderColor,
                shape = GlassShapes.Card,
            )
            .clickable(onClick = {
                onSelect()
                onOpen()
            })
            .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                Box(
                    modifier = Modifier
                        .size(ComponentSize.SidePanelRailButtonSize)
                        .clip(GlassShapes.Medium)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Glow)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = AppIcons.FolderPainterResource(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(IconSize.Medium),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
                ) {
                    Text(
                        text = project.name,
                        fontSize = SetupTokens.TextSection,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = project.path,
                        fontSize = SetupTokens.TextMetadata,
                        fontFamily = codeFontFamily(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

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
                if (isPendingOpen) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                    ) {
                        GlassChip(
                            label = "Opening after checks…",
                            selected = true,
                        )
                        GlassTextButton(
                            onClick = onCancelPendingOpen,
                        ) {
                            Text(
                                text = "Cancel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                GlassChip(
                    label = formatRelativeTime(project.lastOpened),
                    selected = false,
                )

                Box {
                    GlassTooltipArea(tooltipText = "More actions") {
                        GlassIconButton(
                            icon = AppIcons.MoreVert,
                            onClick = { isMenuExpanded = true },
                            size = ComponentSize.PanelHeaderAction,
                        )
                    }

                    GlassDropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false },
                    ) {
                        GlassDropdownMenuItem(
                            text = "Open Workspace",
                            leadingIcon = {
                                Icon(
                                    painter = AppIcons.FolderOpenPainterResource(),
                                    contentDescription = null,
                                    modifier = Modifier.size(IconSize.Small),
                                )
                            },
                            onClick = {
                                isMenuExpanded = false
                                onOpen()
                            },
                        )
                        GlassDropdownMenuItem(
                            text = "Copy Path",
                            leadingIcon = {
                                Icon(
                                    painter = AppIcons.CopyPainterResource(),
                                    contentDescription = null,
                                    modifier = Modifier.size(IconSize.Small),
                                )
                            },
                            onClick = {
                                isMenuExpanded = false
                                onCopyPath()
                            },
                        )
                        GlassDropdownMenuItem(
                            text = "Remove from Recents",
                            leadingIcon = {
                                Icon(
                                    painter = AppIcons.DeletePainterResource(),
                                    contentDescription = null,
                                    modifier = Modifier.size(IconSize.Small),
                                )
                            },
                            onClick = {
                                isMenuExpanded = false
                                onRemove()
                            },
                        )
                    }
                }

                GlassTooltipArea(tooltipText = "Remove from Recents") {
                    GlassIconButton(
                        icon = AppIcons.Close,
                        onClick = onRemove,
                        size = ComponentSize.PanelHeaderAction,
                    )
                }
            }
        }
    }
}

private fun workspaceErrorTitle(action: WorkspaceAction): String = when (action) {
    WorkspaceAction.OPEN -> "Failed to open workspace"
    WorkspaceAction.CREATE -> "Failed to create workspace"
    WorkspaceAction.REMOVE -> "Failed to remove workspace"
    WorkspaceAction.BROWSE -> "Failed to browse workspaces"
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

@Composable
private fun WorkspaceHeaderSection(
    onCreateWorkspace: () -> Unit,
    onOpenFolder: () -> Unit,
    modifier: Modifier = Modifier,
    headerStatus: ProjectsHeaderStatusPresentation? = null,
    onNavigateToEnvironment: () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f, fill = false).padding(end = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Text(
                text = "Workspaces",
                fontSize = SetupTokens.TextTitle,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Open a workspace or create a new one",
                fontSize = SetupTokens.TextBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (headerStatus?.statusText != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    Text(
                        text = headerStatus.statusText,
                        fontSize = SetupTokens.TextMetadata,
                        color = if (headerStatus.isNeedsSetup) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (headerStatus.actionLabel != null) {
                        GlassPrimaryButton(
                            onClick = onNavigateToEnvironment,
                        ) {
                            Text(
                                text = headerStatus.actionLabel,
                                fontSize = SetupTokens.TextMetadata,
                            )
                        }
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassSecondaryButton(onClick = onOpenFolder) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    Icon(
                        painter = AppIcons.FolderOpenPainterResource(),
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.Small),
                    )
                    Text("Open Workspace", fontSize = SetupTokens.TextBody)
                    Text(
                        text = "Ctrl+O",
                        fontSize = SetupTokens.TextMetadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            GlassPrimaryButton(onClick = onCreateWorkspace) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    Icon(
                        painter = AppIcons.AddPainterResource(),
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.Small),
                    )
                    Text("New Workspace", fontSize = SetupTokens.TextBody)
                    Text(
                        text = "Ctrl+N",
                        fontSize = SetupTokens.TextMetadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceSearchSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    totalCount: Int,
    searchFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.padding(vertical = Spacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Text(
                text = "Recent Workspaces",
                fontSize = SetupTokens.TextSection,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            GlassChip(
                label = "$totalCount",
                selected = false,
            )
        }

        Box(
            modifier = Modifier.widthIn(max = ComponentSize.CommandPaletteMaxWidth),
        ) {
            GlassTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = "Search workspaces... (Ctrl+F)",
                leading = TextFieldLeadingSlot.Icon(AppIcons.Search),
                trailing = if (searchQuery.isNotBlank()) {
                    TextFieldTrailingSlot.Clear(onClear = { onSearchQueryChange("") })
                } else {
                    TextFieldTrailingSlot.None
                },
                focusRequester = searchFocusRequester,
            )
        }
    }
}

@Composable
private fun WorkspaceEmptyStateSection(
    searchQuery: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Large),
        contentAlignment = Alignment.Center,
    ) {
        if (searchQuery.isNotBlank()) {
            GlassEmptyState(
                icon = AppIcons.Folder,
                title = "No Matching Workspaces",
                description = "No workspace matches '$searchQuery'. Try refining your search query.",
                orientation = GlassEmptyStateOrientation.Vertical,
            )
        } else {
            GlassEmptyState(
                icon = AppIcons.Folder,
                title = "No Recent Workspaces",
                description = "Get started by creating a new workspace or opening an existing directory.",
                orientation = GlassEmptyStateOrientation.Vertical,
            )
        }
    }
}
