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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.ideStatusErrorOutline
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.PluginThemeColors
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.feature.setup.SetupActivityState
import org.ide.lti.feature.setup.SetupUiActions
import org.ide.lti.feature.setup.SetupUiState

/**
 * Machine Environment Recovery destination screen.
 *
 * Faithfully renders the "Recover interrupted setup" interface (US3, FR-007, FR-008):
 * - Breadcrumbs and recovery title with troubleshooting link
 * - "Setup in progress" status card with receipt & timestamp metadata
 * - "Service state" card with reconnect & reconcile action
 * - Installation and reset block notification banner
 * - Tabbed activity and logs viewer with auto-scroll toggle
 */
@Composable
fun RecoveryStepContent(
    state: SetupUiState,
    actions: SetupUiActions,
    modifier: Modifier = Modifier,
    activityState: SetupActivityState? = null,
    onOpenHelp: () -> Unit = actions.onOpenHelp,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .testTag("RecoveryStepContent")
            .padding(
                start = Spacing.ScreenHorizontal,
                end = Spacing.ScreenHorizontal,
                top = Spacing.Medium,
                bottom = Spacing.ScreenVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        // 1. Breadcrumb and Screen Header
        RecoveryHeader(onOpenHelp = onOpenHelp)

        // 2. Side-by-Side Status & Action Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            SetupInProgressCard(
                state = state,
                modifier = Modifier.weight(0.65f),
            )

            ServiceStateCard(
                onReconnect = actions.onReconnect,
                modifier = Modifier.weight(0.35f),
            )
        }

        // 3. Blocked Reset & Installation Callout Banner
        RecoveryBlockedBanner()

        // 4. Tabbed Terminal & Log Drawer
        RecoveryConsoleDrawer(activityState = activityState)
    }
}

@Composable
private fun RecoveryHeader(onOpenHelp: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        // Breadcrumb
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Text(
                text = "Machine environment",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.BodySmall,
                fontFamily = GlassFontFamily.ide(),
            )
            Text(
                text = "/",
                color = MaterialTheme.colorScheme.outlineVariant,
                fontSize = FontSize.BodySmall,
            )
            Text(
                text = "Recovery",
                color = MaterialTheme.colorScheme.primary,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.Medium,
                fontFamily = GlassFontFamily.ide(),
            )
        }

        // Title and Help Link
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                Text(
                    text = "Recover interrupted setup",
                    fontSize = FontSize.HeadlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Resume and complete machine environment setup after an unexpected interruption.",
                    fontSize = FontSize.BodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                modifier = Modifier
                    .clip(GlassShapes.Small)
                    .clickable(onClick = onOpenHelp)
                    .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Compact),
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = "Help",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.Medium),
                )
                Column {
                    Text(
                        text = "Help",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = FontSize.BodyMedium,
                    )
                    Text(
                        text = "Recovery setup and troubleshooting",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = FontSize.Micro,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupInProgressCard(
    state: SetupUiState,
    modifier: Modifier = Modifier,
) {
    val activeOp = state.activeOperation
    val receiptId = activeOp?.operationId ?: state.toolchainSetupState.pendingAttemptId ?: "setup-042"

    GlassCard(
        modifier = modifier.testTag("SetupInProgressCard"),
        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = Spacing.CardPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            // Card Title Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.MediumSmall),
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.Large),
                )
                Column {
                    Text(
                        text = "Setup in progress",
                        fontWeight = FontWeight.Bold,
                        fontSize = FontSize.BodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Environment setup was interrupted. The current operation has not completed.",
                        fontSize = FontSize.BodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Key-Value Properties Table
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                RecoveryPropertyRow("Operation", "Install pinned tool packages")
                RecoveryPropertyRow("Receipt ID", receiptId, isCode = true)
                RecoveryPropertyRow("Last confirmed step", "Verifying mkfs.erofs")
                RecoveryPropertyRow("Environment", "Development environment")
                RecoveryPropertyRow("Submitted", "2025-03-12 10:14:32", isCode = true)
                RecoveryPropertyRow(
                    label = "Last update",
                    value = "2025-03-12 10:18:05\nWaiting to reconnect to service...",
                    isCode = true,
                )
            }
        }
    }
}

@Composable
private fun RecoveryPropertyRow(label: String, value: String, isCode: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.BodySmall,
            modifier = Modifier.width(GlassDimens.ReadinessLabelColumnWidth),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontFamily = if (isCode) codeFontFamily() else GlassFontFamily.ide(),
            fontWeight = if (isCode) FontWeight.Normal else FontWeight.Medium,
        )
    }
}

@Composable
private fun ServiceStateCard(
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier.testTag("ServiceStateCard"),
        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = Spacing.CardPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.MediumSmall)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Service state",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = FontSize.BodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = "Service status info",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Small),
                )
            }

            // Unknown Badge Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GlassShapes.Small)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(GlassDimens.HairlineBorder, MaterialTheme.colorScheme.outlineVariant, GlassShapes.Small)
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(IconSize.Medium),
                    )
                    Text(
                        text = "Unknown",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = FontSize.BodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Explanatory Caption
            Text(
                text = "Service connection is lost.\nThis may take a few minutes.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.BodySmall,
            )

            Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

            val isBlue = GlassTheme.appTheme == AppTheme.Blue
            // Primary Reconnect Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ReconnectButton")
                    .clip(GlassShapes.Small)
                    .border(
                        width = GlassDimens.HairlineBorder,
                        color = if (isBlue) PluginThemeColors.ActiveTabBorder else MaterialTheme.colorScheme.primary,
                        shape = GlassShapes.Small,
                    )
                    .background(if (isBlue) PluginThemeColors.ActiveTabBackground else MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onReconnect)
                    .padding(vertical = Spacing.SmallMedium, horizontal = Spacing.Medium),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = AppIcons.RefreshPainterResource(),
                    contentDescription = null,
                    tint = BrandColors.OnLogoBadge,
                    modifier = Modifier.size(IconSize.Small),
                )
                Spacer(modifier = Modifier.width(Spacing.Small))
                Text(
                    text = "Reconnect and reconcile",
                    color = BrandColors.OnLogoBadge,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = FontSize.BodyMedium,
                )
            }

            // Secondary View Evidence Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ViewEvidenceButton")
                    .clip(GlassShapes.Small)
                    .border(
                        width = GlassDimens.HairlineBorder,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = GlassShapes.Small,
                    )
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(onClick = {})
                    .padding(vertical = Spacing.SmallMedium, horizontal = Spacing.Medium),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(IconSize.Small),
                )
                Spacer(modifier = Modifier.width(Spacing.Small))
                Text(
                    text = "View operation evidence",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.BodyMedium,
                )
            }
        }
    }
}

@Composable
private fun RecoveryBlockedBanner(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.Card)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = GlassDimens.HairlineBorder,
                color = PluginThemeColors.WarningBorder,
                shape = GlassShapes.Card,
            )
            .padding(Spacing.Medium),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Icon(
                painter = AppIcons.ideStatusErrorOutline(),
                contentDescription = null,
                tint = PluginThemeColors.WarningText,
                modifier = Modifier.size(IconSize.Large),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
                Text(
                    text = "Installation and reset stay blocked",
                    fontWeight = FontWeight.Bold,
                    fontSize = FontSize.BodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Installation and reset stay blocked until terminal status is confirmed. " +
                        "This prevents partial installation and keeps the environment consistent.",
                    fontSize = FontSize.BodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecoveryConsoleDrawer(
    activityState: SetupActivityState?,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf("logs") }
    var autoScroll by remember { mutableStateOf(true) }

    val sampleLogs = listOf(
        "Starting installation of pinned tool packages...",
        "Resolving package dependencies...",
        "Downloading tool archives...",
        "Extracting packages...",
        "Verifying mkfs.erofs...",
        "Service connection lost during tool verification.",
        "Waiting for service to reconnect...",
    )
    val timestamps = listOf(
        "10:14:32",
        "10:16:11",
        "10:17:03",
        "10:17:26",
        "10:18:04",
        "10:18:05",
        "10:18:05",
    )

    val liveEvents = activityState?.visibleEvents?.collectAsState()?.value.orEmpty()
    val displayLogs = if (liveEvents.isNotEmpty()) {
        liveEvents.map { it.text }
    } else {
        sampleLogs
    }

    GlassCard(
        modifier = modifier.fillMaxWidth().testTag("RecoveryConsoleDrawer"),
        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = Spacing.CardPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium)) {
            // Tab Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Navigation Tabs
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                    ConsoleTabItem(
                        title = "Logs",
                        icon = Icons.Default.Terminal,
                        isSelected = selectedTab == "logs",
                        onClick = { selectedTab = "logs" },
                    )
                    ConsoleTabItem(
                        title = "Evidence",
                        icon = Icons.Default.Description,
                        isSelected = selectedTab == "evidence",
                        onClick = { selectedTab = "evidence" },
                    )
                    ConsoleTabItem(
                        title = "Problems",
                        icon = Icons.Default.Warning,
                        isSelected = selectedTab == "problems",
                        onClick = { selectedTab = "problems" },
                        accentTint = PluginThemeColors.WarningText,
                    )
                }

                // Auto-scroll Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    modifier = Modifier
                        .clip(GlassShapes.Capsule)
                        .clickable { autoScroll = !autoScroll }
                        .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
                ) {
                    Text(
                        text = "Auto-scroll",
                        fontSize = FontSize.BodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val isBlue = GlassTheme.appTheme == AppTheme.Blue
                    Box(
                        modifier = Modifier
                            .width(Spacing.ExtraExtraLarge)
                            .height(Spacing.Medium)
                            .clip(GlassShapes.Capsule)
                            .background(
                                if (autoScroll) {
                                    if (isBlue) PluginThemeColors.ActiveTabBackground else MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                            )
                            .border(
                                width = GlassDimens.HairlineBorder,
                                color = if (autoScroll) {
                                    if (isBlue) PluginThemeColors.ActiveTabBorder else MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = GlassShapes.Capsule,
                            )
                            .padding(Spacing.Hairline),
                        contentAlignment = if (autoScroll) Alignment.CenterEnd else Alignment.CenterStart,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(Spacing.Fourteen)
                                .clip(GlassShapes.Capsule)
                                .background(BrandColors.OnLogoBadge),
                        )
                    }
                }
            }

            // Console Log Lines Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GlassShapes.Small)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = AlphaTokens.Elevated))
                    .border(GlassDimens.HairlineBorder, MaterialTheme.colorScheme.outlineVariant, GlassShapes.Small)
                    .padding(Spacing.Medium),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                    displayLogs.forEachIndexed { index, log ->
                        val time = timestamps.getOrElse(index) { "10:18:05" }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AlphaTokens.Half),
                                fontSize = FontSize.BodySmall,
                                fontFamily = codeFontFamily(),
                                modifier = Modifier.width(Spacing.Large),
                            )
                            Text(
                                text = "[$time] $log",
                                color = if (index >= 5) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                fontSize = FontSize.BodySmall,
                                fontFamily = codeFontFamily(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleTabItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    accentTint: androidx.compose.ui.graphics.Color? = null,
) {
    val isBlue = GlassTheme.appTheme == AppTheme.Blue
    val activeBorder = if (isBlue) PluginThemeColors.SelectedRowBorder else MaterialTheme.colorScheme.primary
    val contentTint = when {
        isSelected && isBlue -> BrandColors.OnLogoBadge
        isSelected -> MaterialTheme.colorScheme.primary
        accentTint != null -> accentTint
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val textContentColor = when {
        isSelected && isBlue -> BrandColors.OnLogoBadge
        isSelected -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        modifier = Modifier
            .clip(GlassShapes.Small)
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier
                        .background(if (isBlue) PluginThemeColors.SelectedRowBackground else MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Faded))
                        .border(
                            width = GlassDimens.HairlineBorder,
                            color = activeBorder,
                            shape = GlassShapes.Small,
                        )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Compact),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentTint,
            modifier = Modifier.size(IconSize.Small),
        )
        Text(
            text = title,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = FontSize.BodySmall,
            color = textContentColor,
        )
    }
}
