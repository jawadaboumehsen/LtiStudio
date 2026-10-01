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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.actions.GlassItemCard
import org.ide.lti.core.designsystem.component.actions.GlassTextButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassEmptyState
import org.ide.lti.core.designsystem.component.layout.GlobalAppDestination
import org.ide.lti.core.designsystem.component.layout.GlobalAppTopBar
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing

@Composable
fun NotificationScreen(
    onBackClick: () -> Unit,
    onNavigateToSetup: () -> Unit = {},
    onNavigateToWorkspace: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    NotificationScreenContent(
        onBackClick = onBackClick,
        onNavigateToSetup = onNavigateToSetup,
        onNavigateToWorkspace = onNavigateToWorkspace,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
fun NotificationScreenContent(
    onBackClick: () -> Unit,
    onNavigateToSetup: () -> Unit = {},
    onNavigateToWorkspace: () -> Unit = {},
    modifier: Modifier = Modifier,
    notifications: List<AppNotificationItem> = defaultSampleNotifications,
) {
    var notificationList by remember(notifications) { mutableStateOf(notifications) }
    val isBlueGlass = GlassTheme.appTheme == AppTheme.Blue

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isBlueGlass) Color.Transparent else MaterialTheme.colorScheme.surface),
    ) {
        GlobalAppTopBar(
            selectedDestination = GlobalAppDestination.Settings,
            onSelectDestination = { destination ->
                when (destination) {
                    GlobalAppDestination.Setup -> onNavigateToSetup()
                    GlobalAppDestination.Workspace -> onNavigateToWorkspace()
                    GlobalAppDestination.Settings -> Unit
                }
            },
            contextLabel = "Notifications",
            contextIcon = AppIcons.Notifications,
            actions = {
                GlassIconButton(
                    onClick = onBackClick,
                    size = ComponentSize.TopBarButtonSize,
                ) {
                    Icon(
                        painter = AppIcons.ArrowBackPainterResource(),
                        contentDescription = "Back to settings",
                        modifier = Modifier.size(IconSize.Small),
                    )
                }
            },
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = ComponentSize.SettingsMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.ScreenVertical),
                verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
            ) {
                if (notificationList.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        GlassTextButton(onClick = { notificationList = emptyList() }) {
                            Text("Clear all")
                        }
                    }
                }

                if (notificationList.isEmpty()) {
                    // Empty state
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentPadding = Spacing.ExtraExtraLarge,
                    ) {
                        GlassEmptyState(
                            icon = AppIcon.Painted(AppIcons.NotificationsPainterResource),
                            title = "All Caught Up",
                            description = "No unread notifications at this time.",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(notificationList, key = { it.id }) { item ->
                            GlassItemCard(
                                title = item.title,
                                subtitle = item.description,
                                icon = AppIcon.Painted(AppIcons.SparklesPainterResource),
                                isHighlighted = item.isUnread,
                                modifier = Modifier.fillMaxWidth(),
                                trailingContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                                    ) {
                                        Text(
                                            text = item.time,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (item.isUnread) {
                                            Box(
                                                modifier = Modifier
                                                    .size(ComponentSize.StatusDotSize)
                                                    .clip(GlassShapes.Capsule)
                                                    .background(MaterialTheme.colorScheme.primary),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

data class AppNotificationItem(
    val id: String,
    val title: String,
    val description: String,
    val time: String,
    val isUnread: Boolean = false,
)

private val defaultSampleNotifications = listOf(
    AppNotificationItem(
        id = "1",
        title = "Workspace Indexing Completed",
        description = "All project files, symbols, and dependencies are now indexed and ready.",
        time = "Just now",
        isUnread = true,
    ),
    AppNotificationItem(
        id = "2",
        title = "Theme Updated",
        description = "AMOLED true-black surfaces with Light Blue (#4FA8FF) accent applied.",
        time = "5m ago",
        isUnread = true,
    ),
    AppNotificationItem(
        id = "3",
        title = "Build Succeeded",
        description = "Desktop JVM build completed in 2.4s with 0 errors.",
        time = "1h ago",
        isUnread = false,
    ),
)
