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
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.ideStatusErrorOutline
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.PluginThemeColors
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.config.AppTheme
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
    resolvedBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    activityState: SetupActivityState? = null,
    onOpenHelp: () -> Unit = actions.onOpenHelp,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
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
        RecoveryHeader(
            onOpenHelp = onOpenHelp,
            colors = colors,
        )

        // 2. Side-by-Side Status & Action Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            SetupInProgressCard(
                state = state,
                resolvedBackdrop = resolvedBackdrop,
                modifier = Modifier.weight(0.65f),
                colors = colors,
            )

            ServiceStateCard(
                onReconnect = actions.onReconnect,
                resolvedBackdrop = resolvedBackdrop,
                modifier = Modifier.weight(0.35f),
                colors = colors,
            )
        }

        // 3. Blocked Reset & Installation Callout Banner
        RecoveryBlockedBanner(colors = colors)

        // 4. Tabbed Terminal & Log Drawer
        RecoveryConsoleDrawer(
            activityState = activityState,
            resolvedBackdrop = resolvedBackdrop,
            colors = colors,
        )
    }
}

@Composable
private fun RecoveryHeader(onOpenHelp: () -> Unit, colors: IdeShellColorScheme, modifier: Modifier = Modifier) {
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
                color = colors.textDim,
                fontSize = FontSize.BodySmall,
                fontFamily = GlassFontFamily.ide(),
            )
            Text(
                text = "/",
                color = colors.borderMuted,
                fontSize = FontSize.BodySmall,
            )
            Text(
                text = "Recovery",
                color = colors.accentBlue,
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
                    color = colors.textMain,
                )
                Text(
                    text = "Resume and complete machine environment setup after an unexpected interruption.",
                    fontSize = FontSize.BodyMedium,
                    color = colors.textMuted,
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
                    tint = colors.accentBlue,
                    modifier = Modifier.size(IconSize.Medium),
                )
                Column {
                    Text(
                        text = "Help",
                        color = colors.accentBlue,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = FontSize.BodyMedium,
                    )
                    Text(
                        text = "Recovery setup and troubleshooting",
                        color = colors.textDim,
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
    resolvedBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
) {
    val activeOp = state.activeOperation
    val receiptId = activeOp?.operationId ?: state.toolchainSetupState.pendingAttemptId ?: "setup-042"

    GlassCard(
        modifier = modifier.testTag("SetupInProgressCard"),
        surfaceColor = colors.cardBackground,
        borderColor = colors.border,
        backdrop = resolvedBackdrop,
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
                    tint = colors.accentBlue,
                    modifier = Modifier.size(IconSize.Large),
                )
                Column {
                    Text(
                        text = "Setup in progress",
                        fontWeight = FontWeight.Bold,
                        fontSize = FontSize.BodyLarge,
                        color = colors.textMain,
                    )
                    Text(
                        text = "Environment setup was interrupted. The current operation has not completed.",
                        fontSize = FontSize.BodySmall,
                        color = colors.textMuted,
                    )
                }
            }

            // Key-Value Properties Table
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                RecoveryPropertyRow("Operation", "Install pinned tool packages", colors)
                RecoveryPropertyRow("Receipt ID", receiptId, colors, isCode = true)
                RecoveryPropertyRow("Last confirmed step", "Verifying mkfs.erofs", colors)
                RecoveryPropertyRow("Environment", "Development environment", colors)
                RecoveryPropertyRow("Submitted", "2025-03-12 10:14:32", colors, isCode = true)
                RecoveryPropertyRow(
                    label = "Last update",
                    value = "2025-03-12 10:18:05\nWaiting to reconnect to service...",
                    colors = colors,
                    isCode = true,
                )
            }
        }
    }
}

@Composable
private fun RecoveryPropertyRow(label: String, value: String, colors: IdeShellColorScheme, isCode: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = colors.textDim,
            fontSize = FontSize.BodySmall,
            modifier = Modifier.width(GlassDimens.ReadinessLabelColumnWidth),
        )
        Text(
            text = value,
            color = colors.textMain,
            fontSize = FontSize.BodySmall,
            fontFamily = if (isCode) codeFontFamily() else GlassFontFamily.ide(),
            fontWeight = if (isCode) FontWeight.Normal else FontWeight.Medium,
        )
    }
}

@Composable
private fun ServiceStateCard(
    onReconnect: () -> Unit,
    resolvedBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
) {
    GlassCard(
        modifier = modifier.testTag("ServiceStateCard"),
        surfaceColor = colors.cardBackground,
        borderColor = colors.border,
        backdrop = resolvedBackdrop,
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
                    color = colors.textMain,
                )
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = "Service status info",
                    tint = colors.textDim,
                    modifier = Modifier.size(IconSize.Small),
                )
            }

            // Unknown Badge Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GlassShapes.Small)
                    .background(colors.cardBackgroundMuted)
                    .border(GlassDimens.HairlineBorder, colors.borderMuted, GlassShapes.Small)
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
                        tint = colors.textMain,
                        modifier = Modifier.size(IconSize.Medium),
                    )
                    Text(
                        text = "Unknown",
                        color = colors.textMain,
                        fontSize = FontSize.BodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Explanatory Caption
            Text(
                text = "Service connection is lost.\nThis may take a few minutes.",
                color = colors.textMuted,
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
                        color = if (isBlue) PluginThemeColors.ActiveTabBorder else colors.accentBlue,
                        shape = GlassShapes.Small,
                    )
                    .background(if (isBlue) PluginThemeColors.ActiveTabBackground else colors.accentBlue)
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
                        color = colors.borderMuted,
                        shape = GlassShapes.Small,
                    )
                    .background(colors.cardBackgroundMuted)
                    .clickable(onClick = {})
                    .padding(vertical = Spacing.SmallMedium, horizontal = Spacing.Medium),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = colors.textMain,
                    modifier = Modifier.size(IconSize.Small),
                )
                Spacer(modifier = Modifier.width(Spacing.Small))
                Text(
                    text = "View operation evidence",
                    color = colors.textMain,
                    fontSize = FontSize.BodyMedium,
                )
            }
        }
    }
}

@Composable
private fun RecoveryBlockedBanner(colors: IdeShellColorScheme, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.Card)
            .background(colors.cardBackground)
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
                    color = colors.textMain,
                )
                Text(
                    text = "Installation and reset stay blocked until terminal status is confirmed. " +
                        "This prevents partial installation and keeps the environment consistent.",
                    fontSize = FontSize.BodySmall,
                    color = colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun RecoveryConsoleDrawer(
    activityState: SetupActivityState?,
    resolvedBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
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
        surfaceColor = colors.cardBackground,
        borderColor = colors.border,
        backdrop = resolvedBackdrop,
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
                        colors = colors,
                    )
                    ConsoleTabItem(
                        title = "Evidence",
                        icon = Icons.Default.Description,
                        isSelected = selectedTab == "evidence",
                        onClick = { selectedTab = "evidence" },
                        colors = colors,
                    )
                    ConsoleTabItem(
                        title = "Problems",
                        icon = Icons.Default.Warning,
                        isSelected = selectedTab == "problems",
                        onClick = { selectedTab = "problems" },
                        colors = colors,
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
                        color = colors.textMuted,
                    )
                    val isBlue = GlassTheme.appTheme == AppTheme.Blue
                    Box(
                        modifier = Modifier
                            .width(Spacing.ExtraExtraLarge)
                            .height(Spacing.Medium)
                            .clip(GlassShapes.Capsule)
                            .background(
                                if (autoScroll) {
                                    if (isBlue) PluginThemeColors.ActiveTabBackground else colors.accentBlue
                                } else {
                                    colors.cardBackgroundMuted
                                },
                            )
                            .border(
                                width = GlassDimens.HairlineBorder,
                                color = if (autoScroll) {
                                    if (isBlue) PluginThemeColors.ActiveTabBorder else colors.accentBlue
                                } else {
                                    colors.borderMuted
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
                    .background(colors.appBackground.copy(alpha = AlphaTokens.Elevated))
                    .border(GlassDimens.HairlineBorder, colors.borderMuted, GlassShapes.Small)
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
                                color = colors.textDim.copy(alpha = AlphaTokens.Half),
                                fontSize = FontSize.BodySmall,
                                fontFamily = codeFontFamily(),
                                modifier = Modifier.width(Spacing.Large),
                            )
                            Text(
                                text = "[$time] $log",
                                color = if (index >= 5) colors.textMuted else colors.textMain,
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
    colors: IdeShellColorScheme,
    accentTint: androidx.compose.ui.graphics.Color? = null,
) {
    val isBlue = GlassTheme.appTheme == AppTheme.Blue
    val activeBorder = if (isBlue) PluginThemeColors.SelectedRowBorder else colors.accentBlue
    val contentTint = when {
        isSelected && isBlue -> BrandColors.OnLogoBadge
        isSelected -> colors.accentBlue
        accentTint != null -> accentTint
        else -> colors.textMuted
    }
    val textContentColor = when {
        isSelected && isBlue -> BrandColors.OnLogoBadge
        isSelected -> colors.textMain
        else -> colors.textMuted
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
                        .background(if (isBlue) PluginThemeColors.SelectedRowBackground else colors.controlSelected)
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
