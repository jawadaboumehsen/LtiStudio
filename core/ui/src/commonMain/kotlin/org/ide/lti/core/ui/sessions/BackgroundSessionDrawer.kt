/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.actions.GlassTextButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.navigation.GlassTab
import org.ide.lti.core.designsystem.component.navigation.GlassTabBar
import org.ide.lti.core.designsystem.component.progress.GlassProgressBar
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.desktop.BackgroundAlertItem
import org.ide.lti.core.domain.desktop.BackgroundSessionItem
import org.ide.lti.core.domain.desktop.BackgroundSessionJumpTarget
import org.ide.lti.core.domain.desktop.BackgroundSessionSnapshot
import org.ide.lti.core.domain.desktop.BackgroundSessionState

private const val TAB_SESSIONS = "sessions"
private const val TAB_ALERTS = "alerts"

/**
 * Compact JetBrains-Air-style status pill showing live background task count and unread alerts.
 */
@Composable
fun BackgroundSessionStatusPill(
    snapshot: BackgroundSessionSnapshot,
    isDrawerOpen: Boolean,
    onToggleDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when {
        snapshot.activeCount > 0 && snapshot.unreadAlertCount > 0 ->
            "Tasks: ${snapshot.activeCount} running · ${snapshot.unreadAlertCount} new"
        snapshot.activeCount > 0 ->
            "Tasks: ${snapshot.activeCount} running"
        snapshot.unreadAlertCount > 0 ->
            "Alerts: ${snapshot.unreadAlertCount} unread"
        snapshot.sessions.isNotEmpty() || snapshot.alerts.isNotEmpty() ->
            "Tasks: ${snapshot.sessions.size}"
        else ->
            "Tasks: idle"
    }
    GlassChip(
        label = label,
        selected = isDrawerOpen || snapshot.activeCount > 0 || snapshot.unreadAlertCount > 0,
        onClick = onToggleDrawer,
        modifier = modifier.testTag("BackgroundSessionStatusPill"),
    )
}

/**
 * Persistent background session & notification drawer tracking extractions, repacks, builds,
 * firmware imports, APK audits, and device reconnect alerts across the whole workspace.
 */
@Composable
fun BackgroundSessionDrawer(
    snapshot: BackgroundSessionSnapshot,
    onJump: (BackgroundSessionJumpTarget) -> Unit,
    onCancelSession: (String) -> Unit,
    onDismissSession: (String) -> Unit,
    onDismissAlert: (String) -> Unit,
    onClearCompleted: () -> Unit,
    onMarkAllRead: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeTab by rememberSaveable { mutableStateOf(TAB_SESSIONS) }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("BackgroundSessionDrawer"),
        shape = GlassShapes.HazePanel,
        contentPadding = Spacing.Medium,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Background Sessions & Notifications",
                        style = GlassTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${snapshot.activeCount} active · ${snapshot.unreadAlertCount} unread alerts",
                        style = GlassTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                    if (activeTab == TAB_SESSIONS && snapshot.sessions.any { !it.state.isActive }) {
                        GlassTextButton(onClick = onClearCompleted) {
                            Text("Clear Done")
                        }
                    }
                    if (activeTab == TAB_ALERTS && snapshot.unreadAlertCount > 0) {
                        GlassTextButton(onClick = onMarkAllRead) {
                            Text("Mark Read")
                        }
                    }
                    GlassTextButton(onClick = onClose) {
                        Text("Close")
                    }
                }
            }

            GlassTabBar(
                tabs = listOf(
                    GlassTab(id = TAB_SESSIONS, title = "Tasks (${snapshot.sessions.size})"),
                    GlassTab(id = TAB_ALERTS, title = "Notifications (${snapshot.alerts.size})"),
                ),
                selectedId = activeTab,
                onTabSelected = { activeTab = it },
                modifier = Modifier.fillMaxWidth(),
            )

            GlassHorizontalDivider()

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = ComponentSize.DialogDefaultHeight)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                when (activeTab) {
                    TAB_SESSIONS -> {
                        if (snapshot.sessions.isEmpty()) {
                            EmptyDrawerMessage("No background tasks recorded yet. Drop a firmware or start a build.")
                        } else {
                            snapshot.sessions.forEach { session ->
                                BackgroundSessionRow(
                                    item = session,
                                    onJump = { session.jumpTarget?.let(onJump) },
                                    onCancel = { onCancelSession(session.id) },
                                    onDismiss = { onDismissSession(session.id) },
                                )
                            }
                        }
                    }

                    else -> {
                        if (snapshot.alerts.isEmpty()) {
                            EmptyDrawerMessage("No notifications yet.")
                        } else {
                            snapshot.alerts.forEach { alert ->
                                BackgroundAlertRow(
                                    item = alert,
                                    onJump = { alert.jumpTarget?.let(onJump) },
                                    onDismiss = { onDismissAlert(alert.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDrawerMessage(text: String) {
    Text(
        text = text,
        style = GlassTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Spacing.Medium),
    )
}

@Composable
private fun BackgroundSessionRow(
    item: BackgroundSessionItem,
    onJump: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val diagnostics = GlassTheme.diagnosticColors
    val stateColor = when (item.state) {
        BackgroundSessionState.RUNNING -> MaterialTheme.colorScheme.primary
        BackgroundSessionState.QUEUED -> diagnostics.warning
        BackgroundSessionState.SUCCEEDED -> diagnostics.success
        BackgroundSessionState.FAILED -> diagnostics.error
        BackgroundSessionState.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.Small)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AlphaTokens.Heavy))
            .border(
                width = StrokeWidth.Hairline,
                color = stateColor.copy(alpha = AlphaTokens.Border),
                shape = GlassShapes.Small,
            )
            .clickable(enabled = item.jumpTarget != null, onClick = onJump)
            .padding(Spacing.Small)
            .testTag("SessionRow_${item.id}"),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KindBadge(label = item.kind.label, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = item.title,
                    style = GlassTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(Spacing.Small))
            KindBadge(label = item.state.name, color = stateColor)
        }

        if (item.detail.isNotBlank()) {
            Text(
                text = item.detail,
                style = GlassTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (item.state.isActive) {
            GlassProgressBar(
                progress = item.progress,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.jumpTarget != null) {
                GlassTextButton(onClick = onJump) {
                    Text("Open")
                }
            }
            if (item.canCancel && item.state.isActive) {
                GlassTextButton(onClick = onCancel) {
                    Text("Cancel", color = diagnostics.error)
                }
            } else if (!item.state.isActive) {
                GlassTextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun BackgroundAlertRow(item: BackgroundAlertItem, onJump: () -> Unit, onDismiss: () -> Unit) {
    val diagnostics = GlassTheme.diagnosticColors
    val accent = if (item.isError) diagnostics.error else diagnostics.info

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.Small)
            .background(
                if (item.unread) {
                    accent.copy(alpha = AlphaTokens.Glow)
                } else {
                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AlphaTokens.Half)
                },
            )
            .clickable(enabled = item.jumpTarget != null, onClick = onJump)
            .padding(Spacing.Small)
            .testTag("AlertRow_${item.id}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(IconSize.Indicator)
                    .clip(GlassShapes.Circle)
                    .background(if (item.unread) accent else MaterialTheme.colorScheme.outlineVariant),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                Text(
                    text = item.title,
                    style = GlassTheme.typography.labelMedium,
                    fontWeight = if (item.unread) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = item.message,
                    style = GlassTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
            if (item.jumpTarget != null) {
                GlassTextButton(onClick = onJump) {
                    Text("View")
                }
            }
            GlassTextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun KindBadge(label: String, color: Color) {
    Text(
        text = label,
        style = GlassTheme.typography.labelSmall.copy(
            fontFamily = codeFontFamily(),
            fontSize = FontSize.Micro,
            fontWeight = FontWeight.Bold,
            color = color,
        ),
        modifier = Modifier
            .clip(GlassShapes.ExtraSmall)
            .background(color.copy(alpha = AlphaTokens.Glow))
            .padding(horizontal = Spacing.ExtraSmall, vertical = Spacing.ExtraExtraSmall),
    )
}
