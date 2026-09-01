/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.GlassCard
import org.ide.lti.core.designsystem.component.GlassIconButton
import org.ide.lti.core.designsystem.component.GlassTextButton
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

@Composable
fun NotificationScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NotificationScreenContent(
        onBackClick = onBackClick,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
fun NotificationScreenContent(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    notifications: List<AppNotificationItem> = defaultSampleNotifications,
) {
    var notificationList by remember(notifications) { mutableStateOf(notifications) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ComponentSize.SettingsMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.ScreenVertical),
            verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassIconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = AppIcons.ArrowBack,
                            contentDescription = "Back",
                            tint = GlassTheme.colors.onGlass,
                            modifier = Modifier.size(IconSize.Medium),
                        )
                    }

                    Text(
                        text = "Notifications",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = GlassTheme.colors.onGlass,
                    )
                }

                if (notificationList.isNotEmpty()) {
                    GlassTextButton(onClick = { notificationList = emptyList() }) {
                        Text("Clear all")
                    }
                }
            }

            Spacer(Modifier.height(Spacing.ExtraSmall))

            if (notificationList.isEmpty()) {
                // Empty state
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = Spacing.ExtraExtraLarge,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.CardPadding),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(ComponentSize.EmptyStateBadgeSize)
                                .clip(CircleShape)
                                .background(GlassTheme.colors.glassSurfaceElevated.copy(alpha = AlphaTokens.Faint))
                                .border(
                                    StrokeWidth.Hairline,
                                    GlassTheme.colors.glassBorderHighlight.copy(alpha = AlphaTokens.Border),
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = AppIcons.NotificationsPainterResource(),
                                contentDescription = null,
                                tint = GlassTheme.colors.glassAccent,
                                modifier = Modifier.size(IconSize.ExtraLarge),
                            )
                        }

                        Text(
                            text = "All Caught Up",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = GlassTheme.colors.onGlass,
                        )

                        Text(
                            text = "No unread notifications at this time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassTheme.colors.onGlassSecondary,
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(notificationList, key = { it.id }) { item ->
                        NotificationCard(item = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    item: AppNotificationItem,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = Spacing.CardPadding,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
        ) {
            Box(
                modifier = Modifier
                    .size(ComponentSize.TopBarButtonSize)
                    .clip(CircleShape)
                    .background(GlassTheme.colors.glassSurfaceElevated)
                    .border(
                        StrokeWidth.Hairline,
                        if (item.isUnread) GlassTheme.colors.glassBorderHighlight else GlassTheme.colors.glassBorder,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppIcons.Sparkles,
                    contentDescription = null,
                    tint = if (item.isUnread) GlassTheme.colors.glassAccent else GlassTheme.colors.onGlassMuted,
                    modifier = Modifier.size(IconSize.Medium),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (item.isUnread) FontWeight.Bold else FontWeight.Medium,
                        color = GlassTheme.colors.onGlass,
                    )
                    Text(
                        text = item.time,
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassTheme.colors.onGlassMuted,
                    )
                }

                Spacer(Modifier.height(Spacing.ExtraSmall))

                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTheme.colors.onGlassSecondary,
                )
            }

            if (item.isUnread) {
                Spacer(Modifier.width(Spacing.ExtraSmall))
                Box(
                    modifier = Modifier
                        .size(ComponentSize.StatusDotSize)
                        .clip(CircleShape)
                        .background(GlassTheme.colors.glassAccent),
                )
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
