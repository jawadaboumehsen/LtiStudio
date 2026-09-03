/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import org.ide.lti.core.designsystem.component.GlassActionCard
import org.ide.lti.core.designsystem.component.GlassButton
import org.ide.lti.core.designsystem.component.GlassButtonVariant
import org.ide.lti.core.designsystem.component.GlassCard
import org.ide.lti.core.designsystem.component.GlassChip
import org.ide.lti.core.designsystem.component.GlassEmptyState
import org.ide.lti.core.designsystem.component.GlassEmptyStateOrientation
import org.ide.lti.core.designsystem.component.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.GlassItemCard
import org.ide.lti.core.designsystem.component.GlassPanel
import org.ide.lti.core.designsystem.component.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.GlassSidebarItem
import org.ide.lti.core.designsystem.component.GlassStatusBar
import org.ide.lti.core.designsystem.component.GlassTopBar
import org.ide.lti.core.designsystem.component.GlassVerticalDivider
import org.ide.lti.core.designsystem.component.LineEnding
import org.ide.lti.core.designsystem.component.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LetterSpacing
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SetupScreen(
    onOpenWorkspace: (Workspace) -> Unit,
    onNavigateToConnections: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    backdrop: Backdrop? = null,
    viewModel: SetupViewModel = koinViewModel(),
) {
    val recentProjects by viewModel.recentProjects.collectAsState()

    SetupScreenContent(
        recentProjects = recentProjects,
        onOpenFolder = { viewModel.openFolder(onOpenWorkspace) },
        onCloneGit = { /* TODO: Clone from Git */ },
        onNewFile = { /* TODO: New File */ },
        onRecentProjectClick = { project -> viewModel.openRecentProject(project, onOpenWorkspace) },
        onNavigateToConnections = onNavigateToConnections,
        onOpenSettings = onOpenSettings,
        backdrop = backdrop,
        modifier = modifier,
    )
}

@Composable
fun SetupScreenContent(
    recentProjects: List<RecentProject>,
    onOpenFolder: () -> Unit,
    onCloneGit: () -> Unit,
    onNewFile: () -> Unit,
    onRecentProjectClick: (RecentProject) -> Unit,
    onNavigateToConnections: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    backdrop: Backdrop? = null,
    onMinimize: (() -> Unit)? = null,
    onMaximize: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onDrag: (() -> Unit)? = null,
    isMaximized: Boolean? = null,
) {
    val localBackdrop = rememberLayerBackdrop()
    val resolvedBackdrop = (backdrop as? LayerBackdrop) ?: (GlassTheme.backdrop as? LayerBackdrop) ?: localBackdrop
    var selectedTab by remember { mutableStateOf("workspaces") }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Standard Unified Liquid Glass Top Bar (identical to workspace screen)
            GlassTopBar(
                title = "LtiRom",
                workspaceName = "Setup",
                showMenuButton = false,
                showPanelToggles = false,
                showCommandPalette = false,
                onSettingsClick = onOpenSettings,
                onWindowMinimize = onMinimize,
                onWindowMaximize = onMaximize,
                onWindowClose = onClose,
                onWindowDrag = onDrag,
                isMaximized = isMaximized,
                backdrop = resolvedBackdrop,
                drawOwnChrome = true,
            )

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Left Sidebar with glass panel
                GlassPanel(
                    width = ComponentSize.SetupSidebarWidth,
                    shape = GlassShapes.None,
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    SetupSidebar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        onNavigateToConnections = onNavigateToConnections,
                        onOpenSettings = onOpenSettings,
                        recentProjectCount = recentProjects.size,
                    )
                }

                // Sleek Specular Hairline Vertical Divider
                GlassVerticalDivider(specular = true)

            // Main Content Area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.ScreenVertical),
            ) {
                // Header with status indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                        Text(
                            text = "Workspaces",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = GlassTheme.colors.onGlass,
                        )
                        Text(
                            text = "Open, clone, or create a project to begin developing",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassTheme.colors.onGlassSecondary,
                        )
                    }

                    GlassChip(
                        label = "Ready",
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

                Spacer(modifier = Modifier.height(Spacing.SectionGap))

                // Action Cards with distinct semantic icons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.CardPadding),
                ) {
                    GlassActionCard(
                        title = "Open Folder...",
                        subtitle = "Open a local workspace directory",
                        icon = AppIcons.FolderOpen,
                        isPrimary = true,
                        onClick = onOpenFolder,
                        modifier = Modifier.weight(1f),
                    )
                    GlassActionCard(
                        title = "Clone from Git...",
                        subtitle = "Clone a repository from GitHub or remote",
                        icon = AppIcon.Painted(AppIcons.CopyPainterResource),
                        isPrimary = false,
                        onClick = onCloneGit,
                        modifier = Modifier.weight(1f),
                    )
                    GlassActionCard(
                        title = "New File...",
                        subtitle = "Create a new scratch file",
                        icon = AppIcon.Painted(AppIcons.FilePainterResource),
                        isPrimary = false,
                        onClick = onNewFile,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.ScreenVertical))

                // Recent Projects Section Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        Text(
                            text = "Recent Projects",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = GlassTheme.colors.onGlass,
                        )
                        if (recentProjects.isNotEmpty()) {
                            GlassChip(
                                label = "${recentProjects.size}",
                                selected = false,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.Medium))

                if (recentProjects.isNotEmpty()) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                    ) {
                        items(recentProjects, key = { it.workspaceId }) { project ->
                            GlassItemCard(
                                title = project.name,
                                subtitle = project.path,
                                icon = AppIcon.Painted(AppIcons.FolderPainterResource),
                                onClick = { onRecentProjectClick(project) },
                            )
                        }
                    }
                } else {
                    // Empty state card
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = Spacing.CardPadding,
                    ) {
                        GlassEmptyState(
                            icon = AppIcons.Folder,
                            title = "No Recent Projects",
                            description = "Projects you open will appear here for quick access.",
                            badgeSize = ComponentSize.EmptyStateBadgeSize,
                            iconSize = IconSize.Large,
                            iconTint = GlassTheme.colors.onGlassMuted,
                            titleStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            orientation = GlassEmptyStateOrientation.Horizontal,
                            action = {
                                GlassSecondaryButton(onClick = onOpenFolder) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                                    ) {
                                        Icon(
                                            painter = AppIcons.FolderOpenPainterResource(),
                                            contentDescription = null,
                                            modifier = Modifier.size(IconSize.Medium),
                                        )
                                        Text("Open Folder")
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        // Bottom Unified Liquid Glass Status Bar
        GlassStatusBar(
            filePath = if (selectedTab == "workspaces") "Workspaces" else "Connections",
            branchName = "main",
            encoding = "UTF-8",
            lineEnding = LineEnding.LF,
            fileType = "LtiRom Desktop",
            cursorPosition = null,
            drawOwnChrome = true,
            backdrop = resolvedBackdrop,
        )
    }
}
}

@Composable
private fun SetupSidebar(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    onNavigateToConnections: () -> Unit,
    onOpenSettings: () -> Unit,
    recentProjectCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(Spacing.PanelPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        // IDE Brand Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Compact),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Small),
        ) {
            Box(
                modifier = Modifier
                    .size(IconSize.Indicator)
                    .clip(GlassShapes.Capsule)
                    .background(GlassTheme.colors.glassAccent),
            )
            Text(
                text = "LTIROM",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = codeFontFamily(),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = LetterSpacing.Widest,
                ),
                color = GlassTheme.colors.onGlass,
            )
            Spacer(Modifier.weight(1f))
            GlassChip(
                label = "v0.0.1",
                selected = false,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.ExtraSmall))
        GlassHorizontalDivider()
        Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

        Text(
            text = "WORKSPACE",
            style = MaterialTheme.typography.labelSmall,
            color = GlassTheme.colors.onGlassMuted,
            modifier = Modifier.padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
        )

        GlassSidebarItem(
            label = "Workspaces",
            icon = AppIcons.FolderOpen,
            selected = selectedTab == "workspaces",
            badge = if (recentProjectCount > 0) "$recentProjectCount" else null,
            onClick = { onTabSelected("workspaces") },
        )
        GlassSidebarItem(
            label = "Connections",
            icon = AppIcons.Link,
            selected = selectedTab == "connections",
            onClick = {
                onTabSelected("connections")
                onNavigateToConnections()
            },
        )

        Spacer(modifier = Modifier.height(Spacing.Small))
        GlassHorizontalDivider()
        Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

        Text(
            text = "COLLABORATION",
            style = MaterialTheme.typography.labelSmall,
            color = GlassTheme.colors.onGlassMuted,
            modifier = Modifier.padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
        )

        GlassSidebarItem(
            label = "Join Session",
            icon = AppIcons.Person,
            selected = selectedTab == "join_session",
            onClick = { onTabSelected("join_session") },
        )

        Spacer(modifier = Modifier.weight(1f))

        GlassHorizontalDivider()
        Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

        GlassSidebarItem(
            label = "Settings",
            icon = AppIcon.Painted(AppIcons.SettingsPainterResource),
            selected = false,
            onClick = onOpenSettings,
        )
    }
}
