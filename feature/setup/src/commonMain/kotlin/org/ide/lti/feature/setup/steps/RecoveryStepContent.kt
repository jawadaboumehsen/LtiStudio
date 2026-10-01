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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.IdeStatusSeverity
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.ideStatusErrorOutline
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.PluginThemeColors
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.feature.setup.BuildServicePresentation
import org.ide.lti.feature.setup.RecoveryEvidencePresentation
import org.ide.lti.feature.setup.RecoveryOperationPresentation
import org.ide.lti.feature.setup.RecoveryPresentation
import org.ide.lti.feature.setup.RecoveryProblemItem
import org.ide.lti.feature.setup.SetupActivityState
import org.ide.lti.feature.setup.SetupUiActions
import org.ide.lti.feature.setup.SetupUiState
import org.ide.lti.feature.setup.components.SetupActivityPanel

/**
 * Machine Environment Recovery destination screen (US5, contracts/ui-states.md §8).
 *
 * Responsibilities:
 * - Idle state shows ONLY "Nothing to recover".
 * - Shows operation card only when activeOperation or pendingAttemptId is present.
 * - Shows service card only when SERVER_CONNECTIVITY step is known.
 * - Shows blocked banner only when recoveryBlockReason is non-null.
 * - Evidence & Problems tabs are hidden when empty and show real data otherwise.
 * - No mock strings, no "View operation evidence".
 * - 100% design system tokens.
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
    val recovery = state.recoveryPresentation

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

        if (recovery.isEmpty) {
            RecoveryEmptyState()
        } else {
            // 2. Side-by-Side Status & Action Cards
            RecoveryCardsSection(
                recovery = recovery,
                onReconnect = actions.onReconnect,
            )

            // 3. Blocked Banner
            if (recovery.blockedReason != null) {
                RecoveryBlockedBanner(reason = recovery.blockedReason)
            }

            // 4. Activity & Console Drawer
            RecoveryDrawerSection(
                recovery = recovery,
                activityState = activityState,
                onExportLogs = actions.onExportLogs,
            )
        }
    }
}

@Composable
private fun RecoveryHeader(onOpenHelp: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
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
private fun RecoveryEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("NothingToRecover")
            .padding(Spacing.ExtraExtraLarge),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.Large),
            )
            Text(
                text = "Nothing to recover",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "No interrupted setup or service failure was found on this machine.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecoveryCardsSection(
    recovery: RecoveryPresentation,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val operation = recovery.operation
    val service = recovery.service

    if (operation != null && service != null) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            RecoveryOperationCard(
                operation = operation,
                modifier = Modifier.weight(0.65f),
            )
            RecoveryServiceCard(
                service = service,
                onReconnect = onReconnect,
                modifier = Modifier.weight(0.35f),
            )
        }
    } else if (operation != null) {
        RecoveryOperationCard(
            operation = operation,
            modifier = modifier.fillMaxWidth(),
        )
    } else if (service != null) {
        RecoveryServiceCard(
            service = service,
            onReconnect = onReconnect,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RecoveryOperationCard(operation: RecoveryOperationPresentation, modifier: Modifier = Modifier) {
    GlassCard(
        modifier = modifier.testTag("SetupInProgressCard"),
        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = Spacing.CardPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
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

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                RecoveryPropertyRow("Operation", operation.kind)
                RecoveryPropertyRow("Receipt ID", operation.attemptId, isCode = true)
                RecoveryPropertyRow("State", operation.state)
                if (operation.failure != null) {
                    RecoveryPropertyRow("Failure", operation.failure, isCode = true)
                }
                if (operation.issuedAt != null) {
                    RecoveryPropertyRow("Submitted", operation.issuedAt, isCode = true)
                }
            }
        }
    }
}

@Composable
private fun RecoveryServiceCard(
    service: BuildServicePresentation,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier.testTag("ServiceStateCard"),
        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = Spacing.CardPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.MediumSmall)) {
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

            val badgeColor = when (service.severity) {
                IdeStatusSeverity.Failed, IdeStatusSeverity.Blocking -> MaterialTheme.colorScheme.error
                IdeStatusSeverity.Warning -> PluginThemeColors.WarningText
                IdeStatusSeverity.Ready -> MaterialTheme.colorScheme.primary
                IdeStatusSeverity.Running -> MaterialTheme.colorScheme.primary
                IdeStatusSeverity.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
            }

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
                        tint = badgeColor,
                        modifier = Modifier.size(IconSize.Medium),
                    )
                    Text(
                        text = service.chipText,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = FontSize.BodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Text(
                text = service.detailText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.BodySmall,
            )

            Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

            GlassPrimaryButton(
                onClick = onReconnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ReconnectButton"),
            ) {
                Icon(
                    painter = AppIcons.RefreshPainterResource(),
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.Small),
                )
                Spacer(modifier = Modifier.width(Spacing.Small))
                Text(
                    text = "Reconnect and reconcile",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = FontSize.BodyMedium,
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
private fun RecoveryBlockedBanner(reason: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("RecoveryBlockedBanner")
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
                    text = reason,
                    fontSize = FontSize.BodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecoveryDrawerSection(
    recovery: RecoveryPresentation,
    activityState: SetupActivityState?,
    onExportLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasEvidence = recovery.hasEvidence
    val hasProblems = recovery.hasProblems

    if (activityState == null && !hasEvidence && !hasProblems) return

    // If only logs, render directly
    if (!hasEvidence && !hasProblems && activityState != null) {
        SetupActivityPanel(
            activityState = activityState,
            defaultExpanded = true,
            onExport = onExportLogs,
            modifier = modifier,
        )
        return
    }

    var selectedTab by remember { mutableStateOf("logs") }

    GlassCard(
        modifier = modifier.fillMaxWidth().testTag("RecoveryConsoleDrawer"),
        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = Spacing.CardPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            // Tab Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (activityState != null) {
                    ConsoleTabItem(
                        title = "Logs",
                        icon = Icons.Default.Terminal,
                        isSelected = selectedTab == "logs",
                        onClick = { selectedTab = "logs" },
                    )
                }
                if (hasEvidence) {
                    ConsoleTabItem(
                        title = "Evidence",
                        icon = Icons.Default.Description,
                        isSelected = selectedTab == "evidence",
                        onClick = { selectedTab = "evidence" },
                        modifier = Modifier.testTag("EvidenceTab"),
                    )
                }
                if (hasProblems) {
                    ConsoleTabItem(
                        title = "Problems",
                        icon = Icons.Default.Warning,
                        isSelected = selectedTab == "problems",
                        onClick = { selectedTab = "problems" },
                        accentTint = PluginThemeColors.WarningText,
                        modifier = Modifier.testTag("ProblemsTab"),
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                "logs" -> {
                    if (activityState != null) {
                        SetupActivityPanel(
                            activityState = activityState,
                            defaultExpanded = true,
                            onExport = onExportLogs,
                        )
                    }
                }
                "evidence" -> {
                    RecoveryEvidenceTab(evidence = recovery.evidence)
                }
                "problems" -> {
                    RecoveryProblemsTab(problems = recovery.problems)
                }
            }
        }
    }
}

@Composable
private fun RecoveryEvidenceTab(evidence: RecoveryEvidencePresentation, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        if (evidence.intents.isNotEmpty()) {
            Text(
                text = "Child intents (${evidence.intents.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = FontSize.BodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            evidence.intents.forEach { intent ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GlassShapes.Small)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(Spacing.SmallMedium),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                        Text(
                            text = "${intent.stage} · ${intent.actionId}",
                            fontWeight = FontWeight.Medium,
                            fontSize = FontSize.BodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Idempotency: ${intent.idempotencyKey}",
                            fontSize = FontSize.Micro,
                            fontFamily = codeFontFamily(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (intent.runId != null) {
                            Text(
                                text = "Run ID: ${intent.runId} · Status: ${intent.terminalStatus ?: "running"}",
                                fontSize = FontSize.Micro,
                                fontFamily = codeFontFamily(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (evidence.terminalProofs.isNotEmpty()) {
            Text(
                text = "Terminal proofs (${evidence.terminalProofs.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = FontSize.BodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            evidence.terminalProofs.forEach { proof ->
                Text(
                    text = "${proof.runId}: ${proof.authoritativeTerminalStatus}",
                    fontSize = FontSize.BodySmall,
                    fontFamily = codeFontFamily(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (evidence.completedActionIds.isNotEmpty()) {
            Text(
                text = "Completed actions: ${evidence.completedActionIds.joinToString(", ")}",
                fontSize = FontSize.BodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecoveryProblemsTab(problems: List<RecoveryProblemItem>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        problems.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GlassShapes.Small)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(Spacing.SmallMedium),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = PluginThemeColors.WarningText,
                    modifier = Modifier.size(IconSize.Small),
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = FontSize.BodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = item.detail,
                        fontSize = FontSize.Micro,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
    modifier: Modifier = Modifier,
    accentTint: androidx.compose.ui.graphics.Color? = null,
) {
    val activeBorder = MaterialTheme.colorScheme.primary
    val contentTint = when {
        isSelected -> MaterialTheme.colorScheme.primary
        accentTint != null -> accentTint
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val textContentColor = when {
        isSelected -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        modifier = modifier
            .clip(GlassShapes.Small)
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Faded))
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
