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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.progress.GlassStepInfo
import org.ide.lti.core.designsystem.component.progress.GlassStepStatus
import org.ide.lti.core.designsystem.component.progress.GlassStepper
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus

/**
 * Sleek, horizontal 5-stage pipeline progress tracker replacing monolithic vertical boxes.
 *
 * Visualizes the toolchain provisioning lifecycle:
 * 1. WSL 2 Runtime
 * 2. Server Daemon Bridge
 * 3. Pre-Flight System Doctor
 * 4. Pinned Submodules Sync
 * 5. Native Toolchain Compilation
 *
 * Adheres strictly to:
 * - Single Responsibility: Visualizes stage-by-stage pipeline progression.
 * - Design System Guardrails: 100% token usage (zero raw dp or alpha float literals).
 */
@Composable
fun ToolchainPipelineTracker(
    steps: List<SetupStepDetail>,
    onRetryStep: () -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    backdrop: LayerBackdrop? = null,
) {
    val glassColors = GlassTheme.colors

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        backdrop = backdrop,
        contentPadding = Spacing.CardPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            // Header Row
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    Text(
                        text = "Toolchain Provisioning Pipeline",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = glassColors.onGlass,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    val completedCount = steps.count { it.status == StepStatus.SUCCESS }
                    GlassChip(
                        label = "$completedCount/${steps.size} Stages",
                        selected = completedCount == steps.size,
                    )
                }
            }

            // 5 Stages Horizontal Pipeline Row
            GlassStepper(
                steps = steps.map { step ->
                    val isRestored = step.provenance == StepProvenance.RESTORED &&
                        step.status == StepStatus.PENDING
                    GlassStepInfo(
                        id = step.stage.name,
                        shortTitle = when (step.stage) {
                            SetupStepStage.WSL_DETECTION -> "WSL 2"
                            SetupStepStage.SERVER_CONNECTIVITY -> "Bridge"
                            SetupStepStage.SYSTEM_DIAGNOSTICS -> "Doctor"
                            SetupStepStage.REPO_SYNCHRONIZATION -> "Submodules"
                            SetupStepStage.TOOLCHAIN_COMPILATION -> "Compilers"
                        },
                        status = if (isRestored) {
                            GlassStepStatus.RESTORED
                        } else {
                            step.status.toGlassStepStatus()
                        },
                        statusLabel = if (isRestored) "Re-checking…" else null,
                    )
                },
                isBusy = isBusy,
                onRetryStep = { stepId -> onRetryStep() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun StepStatus.toGlassStepStatus(): GlassStepStatus = when (this) {
    StepStatus.PENDING -> GlassStepStatus.PENDING
    StepStatus.RUNNING -> GlassStepStatus.RUNNING
    StepStatus.SUCCESS -> GlassStepStatus.SUCCESS
    StepStatus.WARNING -> GlassStepStatus.WARNING
    StepStatus.FAILED -> GlassStepStatus.FAILED
}
