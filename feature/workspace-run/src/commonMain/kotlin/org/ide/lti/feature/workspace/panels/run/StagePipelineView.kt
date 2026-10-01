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
    val stageDefs = listOf(
        StageId.FIRMWARE_ACQUISITION to "Acquire",
        StageId.FIRMWARE_EXTRACTION to "Extract",
        StageId.WORK_TREE_ASSEMBLY to "Assemble",
        StageId.MODULE_APPLICATION to "Modules",
        StageId.BUILD_FLASHABLE_ZIP to "Package",
        StageId.GENERATE_OTA_MANIFEST to "Manifest",
    )

    val stepInfos = stageDefs.map { (stageId, title) ->
        val outcome = latestRun?.stages?.find { it.stageId == stageId }
        GlassStepInfo(
            id = stageId.name,
            shortTitle = title,
            status = resolveStepStatus(outcome?.state),
            statusLabel = resolveStepLabel(outcome),
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
