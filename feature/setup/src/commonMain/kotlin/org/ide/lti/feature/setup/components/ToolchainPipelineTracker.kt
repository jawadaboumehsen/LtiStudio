/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.ideStatusError
import org.ide.lti.core.designsystem.icon.ideStatusSuccess
import org.ide.lti.core.designsystem.icon.ideStatusWarning
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.component.display.IdeStatusSeverity
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.feature.setup.SetupOperation
import org.ide.lti.feature.setup.SetupPresentationMapper
import org.ide.lti.feature.setup.TerminalHandoffState

/**
 * 5-stage vertical Toolchain Provisioning Pipeline Tracker (US2).
 *
 * Implements:
 * - 5 vertical checklist stages (WSL, Bridge, Doctor, Submodules, Compilers)
 * - Compact healthy rows showing measured status
 * - Expanded failed rows displaying root-cause descriptions and targeted retry button
 * - Restored/historical indicator without claiming live readiness
 * - Single Responsibility: Visualizes stage-by-stage pipeline progression
 * - 100% design-system token usage (zero raw dp or color literals)
 */
@Composable
fun ToolchainPipelineTracker(
    steps: List<SetupStepDetail>,
    onRetryStep: () -> Unit,
    modifier: Modifier = Modifier,
    onRetryStage: (SetupStepStage) -> Unit = { onRetryStep() },
    isBusy: Boolean = false,
    activeOperation: SetupOperation? = null,
    handoffSheetState: TerminalHandoffState = TerminalHandoffState.Idle,
    onInstallPackages: () -> Unit = {},
    onShowHandoffSheet: () -> Unit = {},
) {
    val stepMap = steps.associateBy { it.stage }
    val orderedStages = if (stepMap.containsKey(SetupStepStage.SYSTEM_PACKAGES)) {
        listOf(
            SetupStepStage.WSL_DETECTION,
            SetupStepStage.SYSTEM_PACKAGES,
            SetupStepStage.SERVER_CONNECTIVITY,
            SetupStepStage.SYSTEM_DIAGNOSTICS,
            SetupStepStage.REPO_SYNCHRONIZATION,
            SetupStepStage.TOOLCHAIN_COMPILATION,
        )
    } else {
        listOf(
            SetupStepStage.WSL_DETECTION,
            SetupStepStage.SERVER_CONNECTIVITY,
            SetupStepStage.SYSTEM_DIAGNOSTICS,
            SetupStepStage.REPO_SYNCHRONIZATION,
            SetupStepStage.TOOLCHAIN_COMPILATION,
        )
    }

    val completedCount = orderedStages.count { stage ->
        stepMap[stage]?.status == StepStatus.SUCCESS
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = Spacing.CardPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Text(
                        text = "This machine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GlassChip(
                    label = "$completedCount of ${orderedStages.size} verified",
                    selected = completedCount == orderedStages.size,
                )
            }

            // Vertical Stages
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                orderedStages.forEach { stage ->
                    val step = stepMap[stage] ?: SetupStepDetail(
                        stage = stage,
                        title = stage.displayName,
                        status = StepStatus.PENDING,
                        description = "Pending verification.",
                    )
                    VerticalStageRow(
                        step = step,
                        stage = stage,
                        isBusy = isBusy,
                        onRetryStage = onRetryStage,
                        activeOperation = activeOperation,
                        handoffSheetState = handoffSheetState,
                        onInstallPackages = onInstallPackages,
                        onShowHandoffSheet = onShowHandoffSheet,
                    )
                }
            }
        }
    }
}

@Composable
private fun VerticalStageRow(
    step: SetupStepDetail,
    stage: SetupStepStage,
    isBusy: Boolean,
    onRetryStage: (SetupStepStage) -> Unit,
    activeOperation: SetupOperation? = null,
    handoffSheetState: TerminalHandoffState = TerminalHandoffState.Idle,
    onInstallPackages: () -> Unit = {},
    onShowHandoffSheet: () -> Unit = {},
) {
    val diagnostics = GlassTheme.diagnosticColors

    val stageDisplayName = stage.displayName

    val systemPackagesPres = if (stage == SetupStepStage.SYSTEM_PACKAGES) {
        SetupPresentationMapper.mapSystemPackages(
            step = step,
            isChecking = isBusy || step.status == StepStatus.RUNNING,
            activeOperation = activeOperation,
            handoffSheetState = handoffSheetState,
        )
    } else null

    val isRunning = step.status == StepStatus.RUNNING || systemPackagesPres?.isChecking == true
    val isSuccess = (step.status == StepStatus.SUCCESS || systemPackagesPres?.isReady == true) && !isRunning
    val isRestored = step.provenance == StepProvenance.RESTORED
    val isActionRequired = (step.status == StepStatus.FAILED || step.status == StepStatus.WARNING ||
        systemPackagesPres?.primaryActionLabel != null) && !isSuccess

    val isFailed = step.status == StepStatus.FAILED ||
        (systemPackagesPres != null && systemPackagesPres.severity == IdeStatusSeverity.Failed)
    val isWarning = !isFailed && (step.status == StepStatus.WARNING ||
        (systemPackagesPres != null && systemPackagesPres.severity == IdeStatusSeverity.Warning))

    val displayDescription = systemPackagesPres?.detailText ?: step.description
    val actionLabel = systemPackagesPres?.primaryActionLabel ?: "Retry Stage"

    if (isActionRequired) {
        // Expanded Failed / Warning Stage Row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassShapes.Small)
                .background(
                    if (isFailed) {
                        diagnostics.error.copy(alpha = AlphaTokens.UltraFaint)
                    } else {
                        diagnostics.warning.copy(alpha = AlphaTokens.UltraFaint)
                    },
                )
                .border(
                    width = StrokeWidth.Hairline,
                    color = if (isFailed) {
                        diagnostics.error.copy(alpha = AlphaTokens.Border)
                    } else {
                        diagnostics.warning.copy(alpha = AlphaTokens.Border)
                    },
                    shape = GlassShapes.Small,
                )
                .padding(Spacing.MediumSmall),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        Icon(
                            painter = if (isFailed) AppIcons.ideStatusError() else AppIcons.ideStatusWarning(),
                            contentDescription = if (isFailed) "Failed" else "Warning",
                            tint = if (isFailed) diagnostics.error else diagnostics.warning,
                            modifier = Modifier.size(IconSize.Small),
                        )
                        Text(
                            text = stageDisplayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    GlassChip(
                        label = if (isFailed) "Failed" else "Notice",
                        selected = false,
                    )
                }

                Text(
                    text = displayDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFailed) diagnostics.error else diagnostics.warning,
                )

                if (!step.error.isNullOrBlank() && systemPackagesPres == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(GlassShapes.ExtraSmall)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(
                                width = StrokeWidth.Hairline,
                                color = MaterialTheme.colorScheme.outline,
                                shape = GlassShapes.ExtraSmall,
                            )
                            .padding(Spacing.Small),
                    ) {
                        Text(
                            text = step.error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = codeFontFamily(),
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (systemPackagesPres?.primaryActionLabel != null || systemPackagesPres == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        GlassPrimaryButton(
                            onClick = {
                                when (actionLabel) {
                                    "Install packages" -> onInstallPackages()
                                    "Continue setup" -> onShowHandoffSheet()
                                    else -> onRetryStage(stage)
                                }
                            },
                            enabled = !isBusy,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                            ) {
                                if (actionLabel == "Retry" || actionLabel == "Retry Stage") {
                                    Icon(
                                        painter = AppIcons.RefreshPainterResource(),
                                        contentDescription = null,
                                        modifier = Modifier.size(IconSize.Small),
                                    )
                                }
                                Text(actionLabel, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Compact Healthy / Running / Pending Stage Row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassShapes.Small)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    width = StrokeWidth.Hairline,
                    color = MaterialTheme.colorScheme.outline,
                    shape = GlassShapes.Small,
                )
                .padding(horizontal = Spacing.MediumSmall, vertical = Spacing.Small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    when {
                        isRunning -> {
                            val isReducedMotion = GlassTheme.reducedMotionPolicy == GlassReducedMotionPolicy.Reduced
                            if (isReducedMotion) {
                                Text(
                                    text = "Checking…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(IconSize.Small),
                                    strokeWidth = StrokeWidth.Focused,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        isSuccess -> {
                            Icon(
                                painter = AppIcons.ideStatusSuccess(),
                                contentDescription = "Ready",
                                tint = diagnostics.success,
                                modifier = Modifier.size(IconSize.Small),
                            )
                        }
                        else -> {
                            // Pending is neutral, not a warning: a muted dot.
                            Box(
                                modifier = Modifier
                                    .size(IconSize.Small),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(IconSize.Indicator)
                                        .clip(GlassShapes.Capsule)
                                        .background(MaterialTheme.colorScheme.outline),
                                )
                            }
                        }
                    }

                    Text(
                        text = stageDisplayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.size(Spacing.ExtraSmall))

                    Text(
                        text = displayDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                GlassChip(
                    label = when {
                        isRestored -> "Historical"
                        isSuccess -> "Ready"
                        isRunning -> "Checking…"
                        else -> "Pending"
                    },
                    selected = isSuccess && !isRestored,
                )
            }
        }
    }
}
