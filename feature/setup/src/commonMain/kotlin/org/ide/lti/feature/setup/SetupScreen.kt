/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import org.ide.lti.core.designsystem.component.GlassButton
import org.ide.lti.core.designsystem.component.GlassButtonVariant
import org.ide.lti.core.designsystem.component.GlassCard
import org.ide.lti.core.designsystem.component.GlassPanel
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SetupScreen(
    onOpenWorkspace: (Workspace) -> Unit,
    onNavigateToConnections: () -> Unit,
    modifier: Modifier = Modifier,
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
) {
    var selectedTab by remember { mutableStateOf("workspaces") }

    Row(modifier = modifier.fillMaxSize()) {
        // Left Sidebar with glass
        GlassPanel(
            width = ComponentSize.SetupSidebarWidth,
            shape = RoundedCornerShape(CornerRadius.None),
            modifier = Modifier.fillMaxHeight(),
        ) {
            SetupSidebar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onNavigateToConnections = onNavigateToConnections,
            )
        }

        // Main Content Area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(Spacing.ExtraExtraLarge),
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

            Spacer(modifier = Modifier.height(Spacing.ExtraLarge))

            // Action Cards with distinct semantic icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                ActionCard(
                    title = "Open Folder...",
                    subtitle = "Open a local workspace directory",
                    icon = AppIcons.FolderOpen,
                    isPrimary = true,
                    onClick = onOpenFolder,
                    modifier = Modifier.weight(1f),
                )
                ActionCard(
                    title = "Clone from Git...",
                    subtitle = "Clone a repository from GitHub or remote",
                    icon = AppIcons.Copy,
                    isPrimary = false,
                    onClick = onCloneGit,
                    modifier = Modifier.weight(1f),
                )
                ActionCard(
                    title = "New File...",
                    subtitle = "Create a new scratch file",
                    icon = AppIcons.File,
                    isPrimary = false,
                    onClick = onNewFile,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(Spacing.ExtraExtraLarge))

            // Recent Projects Section
            Text(
                text = "Recent Projects",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = GlassTheme.colors.onGlass,
            )

            Spacer(modifier = Modifier.height(Spacing.Medium))

            if (recentProjects.isNotEmpty()) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                ) {
                    items(recentProjects) { project ->
                        RecentProjectCard(
                            project = project,
                            onClick = { onRecentProjectClick(project) },
                        )
                    }
                }
            } else {
                // Empty state card
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = Spacing.ExtraLarge,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(ComponentSize.IconButtonSize)
                                .clip(CircleShape)
                                .background(GlassTheme.colors.glassSurfaceElevated)
                                .border(StrokeWidth.Hairline, GlassTheme.colors.glassBorder, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = AppIcons.Folder,
                                contentDescription = null,
                                tint = GlassTheme.colors.onGlassMuted,
                                modifier = Modifier.size(IconSize.Large),
                            )
                        }

                        Column {
                            Text(
                                text = "No Recent Projects",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = GlassTheme.colors.onGlass,
                            )
                            Text(
                                text = "Projects you open will appear here for quick access.",
                                style = MaterialTheme.typography.bodySmall,
                                color = GlassTheme.colors.onGlassSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupSidebar(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    onNavigateToConnections: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(Spacing.PanelPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        SidebarItem(
            label = "Workspaces",
            icon = AppIcons.Home,
            isSelected = selectedTab == "workspaces",
            onClick = { onTabSelected("workspaces") },
        )
        SidebarItem(
            label = "Connections",
            icon = AppIcons.Link,
            isSelected = selectedTab == "connections",
            onClick = {
                onTabSelected("connections")
                onNavigateToConnections()
            },
        )

        Spacer(modifier = Modifier.height(Spacing.Small))
        HorizontalDivider(
            color = GlassTheme.colors.glassBorder.copy(alpha = AlphaTokens.Faint),
            thickness = StrokeWidth.Hairline,
        )
        Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

        Text(
            text = "COLLABORATION",
            style = MaterialTheme.typography.labelSmall,
            color = GlassTheme.colors.onGlassMuted,
            modifier = Modifier.padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
        )

        SidebarItem(
            label = "Join Session",
            icon = AppIcons.Person,
            isSelected = selectedTab == "join_session",
            onClick = { onTabSelected("join_session") },
        )
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        variant = if (isSelected) GlassButtonVariant.Primary else GlassButtonVariant.Text,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) GlassTheme.colors.onGlass else GlassTheme.colors.onGlassSecondary,
                modifier = Modifier.size(IconSize.Medium),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) GlassTheme.colors.onGlass else GlassTheme.colors.onGlassSecondary,
            )
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier
            .height(ComponentSize.ActionCardHeight)
            .clickable(onClick = onClick),
        contentPadding = Spacing.Medium,
        surfaceColor = if (isPrimary) GlassTheme.colors.glassSurfaceElevated else GlassTheme.colors.glassSurface,
        borderColor = if (isPrimary) GlassTheme.colors.glassBorderHighlight else GlassTheme.colors.glassBorder,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(IconSize.ActionCard)
                    .clip(CircleShape)
                    .background(GlassTheme.colors.glassSurfaceElevated.copy(alpha = AlphaTokens.Faint))
                    .border(
                        StrokeWidth.Hairline,
                        if (isPrimary) GlassTheme.colors.glassBorderHighlight else GlassTheme.colors.glassBorder,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(IconSize.Medium),
                    tint = GlassTheme.colors.glassAccent,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.Small))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = GlassTheme.colors.onGlass,
            )
            Spacer(modifier = Modifier.height(Spacing.ExtraExtraSmall))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RecentProjectCard(
    project: RecentProject,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = Spacing.Medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(ComponentSize.TopBarButtonSize)
                    .clip(CircleShape)
                    .background(GlassTheme.colors.glassSurfaceElevated)
                    .border(StrokeWidth.Hairline, GlassTheme.colors.glassBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppIcons.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.Medium),
                    tint = GlassTheme.colors.glassAccent,
                )
            }
            Spacer(modifier = Modifier.width(Spacing.SmallMedium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassTheme.colors.onGlass,
                )
                Text(
                    text = project.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTheme.colors.onGlassSecondary,
                )
            }
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = GlassTheme.colors.onGlassMuted,
                modifier = Modifier.size(IconSize.Medium),
            )
        }
    }
}
