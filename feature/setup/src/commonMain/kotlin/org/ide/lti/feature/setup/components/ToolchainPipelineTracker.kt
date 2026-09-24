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
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus

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
) {
    val orderedStages = listOf(
        SetupStepStage.WSL_DETECTION,
        SetupStepStage.SERVER_CONNECTIVITY,
        SetupStepStage.SYSTEM_DIAGNOSTICS,
        SetupStepStage.REPO_SYNCHRONIZATION,
        SetupStepStage.TOOLCHAIN_COMPILATION,
    )

    val stepMap = steps.associateBy { it.stage }
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
                        text = "Toolchain Provisioning Pipeline",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GlassChip(
                    label = "$completedCount/${orderedStages.size} Stages Verified",
                    selected = completedCount == orderedStages.size,
                )
            }

            // 5 Vertical Stages
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
) {
    val diagnostics = GlassTheme.diagnosticColors

    val stageDisplayName = when (stage) {
        SetupStepStage.WSL_DETECTION -> "WSL 2 Runtime"
        SetupStepStage.SERVER_CONNECTIVITY -> "Daemon Bridge"
        SetupStepStage.SYSTEM_DIAGNOSTICS -> "System Doctor"
        SetupStepStage.REPO_SYNCHRONIZATION -> "Submodules Sync"
        SetupStepStage.TOOLCHAIN_COMPILATION -> "Toolchain Build"
    }

    val isFailed = step.status == StepStatus.FAILED
    val isWarning = step.status == StepStatus.WARNING
    val isRunning = step.status == StepStatus.RUNNING
    val isSuccess = step.status == StepStatus.SUCCESS
    val isRestored = step.provenance == StepProvenance.RESTORED

    if (isFailed || isWarning) {
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
                    text = step.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFailed) diagnostics.error else diagnostics.warning,
                )

                if (!step.error.isNullOrBlank()) {
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    GlassPrimaryButton(
                        onClick = { onRetryStage(stage) },
                        enabled = !isBusy,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        ) {
                            Icon(
                                painter = AppIcons.RefreshPainterResource(),
                                contentDescription = null,
                                modifier = Modifier.size(IconSize.Small),
                            )
                            Text("Retry Stage", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
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
                            CircularProgressIndicator(
                                modifier = Modifier.size(IconSize.Small),
                                strokeWidth = StrokeWidth.Hairline,
                                color = MaterialTheme.colorScheme.primary,
                            )
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
                            Icon(
                                painter = AppIcons.ideStatusWarning(),
                                contentDescription = "Pending",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(IconSize.Small),
                            )
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
                        text = if (isRestored) "Re-checking…" else step.description,
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
                        isRunning -> "Working…"
                        else -> "Pending"
                    },
                    selected = isSuccess && !isRestored,
                )
            }
        }
    }
}
