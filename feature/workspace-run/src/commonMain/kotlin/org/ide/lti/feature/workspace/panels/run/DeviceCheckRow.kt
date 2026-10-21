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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.pipeline.PipelineStageSequence
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.model.run.StageId

/** One line of the checklist: a stage (or the device itself) and its first blocking problem, if any. */
internal data class DeviceCheckRow(val label: String, val problem: String?)

/** Device identity first, then the eight stages in pipeline order, each with its first problem. */
internal fun deviceChecklist(issues: List<ValidationError>): List<DeviceCheckRow> {
    val device = DeviceCheckRow("Device", issues.firstOrNull { it.stageId == null }?.message)
    val stages = PipelineStageSequence.ORDER.map { stage ->
        DeviceCheckRow(stageLabel(stage), issues.firstOrNull { it.stageId == stage }?.message)
    }
    return listOf(device) + stages
}

internal fun stageLabel(stage: StageId): String = when (stage) {
    StageId.FIRMWARE_ACQUISITION -> "Acquire firmware"
    StageId.FIRMWARE_EXTRACTION -> "Extract"
    StageId.WORK_TREE_ASSEMBLY -> "Assemble"
    StageId.DEBLOAT -> "Debloat"
    StageId.MODULE_APPLICATION -> "Patches & mods"
    StageId.BUILD_FLASHABLE_ZIP -> "Build & sign"
    StageId.GENERATE_OTA_MANIFEST -> "Release metadata"
    StageId.PUBLISH_RELEASE -> "Publish"
}

/** Why Start is disabled: what is missing in the device configuration, stage by stage. */
@Composable
internal fun DeviceReadinessChecklist(issues: List<ValidationError>, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth(), contentPadding = Spacing.Medium) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
            Text(
                text = "Device configuration needs attention before a build can start",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            deviceChecklist(issues).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                    Text(
                        text = if (row.problem == null) "✓" else "✕",
                        color = if (row.problem == null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = row.problem?.let { "${row.label}: $it" } ?: row.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
