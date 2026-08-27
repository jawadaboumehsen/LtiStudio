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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.GlassButton
import org.ide.lti.core.designsystem.component.GlassCard
import org.ide.lti.core.designsystem.component.GlassPanel
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
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

    Row(modifier = modifier.fillMaxSize()) {
        // Left Sidebar with glass
        GlassPanel(
            width = ComponentSize.SetupSidebarWidth,
            shape = RoundedCornerShape(CornerRadius.None),
            modifier = Modifier.fillMaxHeight(),
        ) {
            SetupSidebar(
                selectedTab = "workspaces",
                onTabSelected = {},
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
            Text(
                text = "Workspaces",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = GlassTheme.colors.onGlass,
            )

            Spacer(modifier = Modifier.height(Spacing.ExtraLarge))

            // Action Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                ActionCard(
                    title = "Open...",
                    icon = Icons.Default.Add,
                    onClick = { viewModel.openFolder(onOpenWorkspace) },
                    modifier = Modifier.weight(1f),
                )
                ActionCard(
                    title = "Clone from Git...",
                    icon = Icons.Default.Add,
                    onClick = { /* TODO: Clone from Git */ },
                    modifier = Modifier.weight(1f),
                )
                ActionCard(
                    title = "New File",
                    icon = Icons.Default.Add,
                    onClick = { /* TODO: New File */ },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(Spacing.ExtraExtraLarge))

            // Recent Projects
            if (recentProjects.isNotEmpty()) {
                Text(
                    text = "Recent Projects",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassTheme.colors.onGlass,
                )

                Spacer(modifier = Modifier.height(Spacing.Medium))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    items(recentProjects) { project ->
                        RecentProjectCard(
                            project = project,
                            onClick = { viewModel.openRecentProject(project, onOpenWorkspace) },
                        )
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
    Column(modifier = modifier.padding(Spacing.PanelPadding)) {
        SidebarItem(
            label = "Workspaces",
            isSelected = selectedTab == "workspaces",
            onClick = { onTabSelected("workspaces") },
        )
        SidebarItem(
            label = "Connections",
            isSelected = selectedTab == "connections",
            onClick = onNavigateToConnections,
        )

        Spacer(modifier = Modifier.height(Spacing.Medium))
        HorizontalDivider(color = GlassTheme.colors.glassBorder)
        Spacer(modifier = Modifier.height(Spacing.Small))

        Text(
            text = "COLLABORATION",
            style = MaterialTheme.typography.labelSmall,
            color = GlassTheme.colors.onGlassSecondary,
            modifier = Modifier.padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Small),
        )

        SidebarItem(
            label = "Join Session",
            isSelected = false,
            onClick = { /* TODO: Join Session */ },
        )
    }
}

@Composable
private fun SidebarItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        variant = if (isSelected) {
            org.ide.lti.core.designsystem.component.GlassButtonVariant.Primary
        } else {
            org.ide.lti.core.designsystem.component.GlassButtonVariant.Text
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier
            .height(ComponentSize.ActionCardHeight)
            .clickable(onClick = onClick),
        contentPadding = Spacing.Medium,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(IconSize.ActionCard),
                tint = GlassTheme.colors.glassAccent,
            )
            Spacer(modifier = Modifier.height(Spacing.Small))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTheme.colors.onGlass,
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
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(IconSize.Large),
                tint = GlassTheme.colors.glassAccent,
            )
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
        }
    }
}
