/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.run

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.component.progress.GlassStepInfo
import org.ide.lti.core.designsystem.component.progress.GlassStepStatus
import org.ide.lti.core.designsystem.component.progress.GlassStepper
import org.ide.lti.core.designsystem.component.progress.GlassStepperOrientation
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.pipeline.PipelineStageSequence
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageOutcome
import org.ide.lti.core.model.run.StageState

@Composable
public fun StagePipelineView(
    latestRun: BuildRun?,
    isBusy: Boolean,
    modifier: Modifier = Modifier,
    orientation: GlassStepperOrientation = GlassStepperOrientation.Horizontal,
) {
    // Every stage the pipeline executes (Publish is not one of them), from the pipeline's own order.
    val stages = PipelineStageSequence.ORDER.filter { it != StageId.PUBLISH_RELEASE }
    val through = latestRun?.throughStage?.let(stages::indexOf)
    val stepInfos = stages.mapIndexed { index, stageId ->
        val outcome = latestRun?.stages?.find { it.stageId == stageId }
        val outsideRun = through != null && index > through
        GlassStepInfo(
            id = stageId.name,
            shortTitle = stageLabel(stageId),
            status = if (outsideRun) GlassStepStatus.SKIPPED else resolveStepStatus(outcome?.state),
            statusLabel = if (outsideRun) "Not in this run" else resolveStepLabel(outcome),
        )
    }

    GlassStepper(
        steps = stepInfos,
        orientation = orientation,
        isBusy = isBusy,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Small),
    )
}

/** Resolves the glass stepper status for a given stage state. */
private fun resolveStepStatus(state: StageState?): GlassStepStatus = when (state) {
    StageState.RUNNING -> GlassStepStatus.RUNNING
    StageState.EXECUTED -> GlassStepStatus.SUCCESS
    StageState.SKIPPED -> GlassStepStatus.SKIPPED
    StageState.FAILED -> GlassStepStatus.FAILED
    StageState.CANCELLED -> GlassStepStatus.CANCELLED
    StageState.INTERRUPTED -> GlassStepStatus.INTERRUPTED
    StageState.PENDING, StageState.NOT_RUN, null -> GlassStepStatus.PENDING
}

/** Resolves the human-readable display label for a stage outcome. */
private fun resolveStepLabel(outcome: StageOutcome?): String = when (outcome?.state) {
    StageState.SKIPPED -> "Skipped"
    StageState.EXECUTED -> outcome.durationMs?.let { "${it / 1000}s" } ?: "Complete"
    StageState.FAILED -> outcome.message ?: "Failed"
    StageState.RUNNING -> "Running..."
    StageState.CANCELLED -> "Cancelled"
    StageState.INTERRUPTED -> "Interrupted"
    else -> "Pending"
}
