/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.plugins.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.AppIconsRomStages
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.PluginThemeColors
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.domain.plugin.AuthorTaskResult

/**
 * 3-stage author pipeline execution flow matching Concept 15.
 * Truthfully reflects AuthorTaskResult, execution state, and selected task.
 */
@Composable
public fun PluginPipelineFlow(
    taskResult: AuthorTaskResult? = null,
    isRunningTask: Boolean = false,
    selectedTask: String = "",
    modifier: Modifier = Modifier,
) {
    val (dexStatus, dexColor) = resolveStageStatus(
        targetTasks = setOf("compileModPayload"),
        selectedTask = selectedTask,
        isRunning = isRunningTask,
        taskResult = taskResult,
        defaultColor = MaterialTheme.colorScheme.onSurfaceVariant,
        runningColor = MaterialTheme.colorScheme.primary,
        successColor = GlassTheme.diagnosticColors.success,
        errorColor = GlassTheme.diagnosticColors.error,
        warningColor = GlassTheme.diagnosticColors.warning,
    )

    val (planStatus, planColor) = resolveStageStatus(
        targetTasks = setOf("generateModPlan", "validateMod"),
        selectedTask = selectedTask,
        isRunning = isRunningTask,
        taskResult = taskResult,
        defaultColor = MaterialTheme.colorScheme.onSurfaceVariant,
        runningColor = MaterialTheme.colorScheme.primary,
        successColor = GlassTheme.diagnosticColors.success,
        errorColor = GlassTheme.diagnosticColors.error,
        warningColor = GlassTheme.diagnosticColors.warning,
    )

    val (pkgStatus, pkgColor) = resolveStageStatus(
        targetTasks = setOf("packageMod", "testMod", "inspectMod"),
        selectedTask = selectedTask,
        isRunning = isRunningTask,
        taskResult = taskResult,
        defaultColor = MaterialTheme.colorScheme.onSurfaceVariant,
        runningColor = MaterialTheme.colorScheme.primary,
        successColor = GlassTheme.diagnosticColors.success,
        errorColor = GlassTheme.diagnosticColors.error,
        warningColor = GlassTheme.diagnosticColors.warning,
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PipelineStageCard(
            title = "Compiled DEX",
            status = dexStatus,
            statusColor = dexColor,
            icon = AppIcons.FilePainterResource,
            modifier = Modifier.weight(1f),
        )

        Icon(
            painter = AppIcons.ChevronRightPainterResource(),
            contentDescription = "Next stage",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.Small),
        )

        PipelineStageCard(
            title = "Operation plan",
            status = planStatus,
            statusColor = planColor,
            icon = AppIconsRomStages.releaseMetadata,
            modifier = Modifier.weight(1f),
        )

        Icon(
            painter = AppIcons.ChevronRightPainterResource(),
            contentDescription = "Next stage",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.Small),
        )

        PipelineStageCard(
            title = "Tested package",
            status = pkgStatus,
            statusColor = pkgColor,
            icon = AppIconsRomStages.assemble,
            modifier = Modifier.weight(1f),
        )
    }
}

@Suppress("LongParameterList")
private fun resolveStageStatus(
    targetTasks: Set<String>,
    selectedTask: String,
    isRunning: Boolean,
    taskResult: AuthorTaskResult?,
    defaultColor: Color,
    runningColor: Color,
    successColor: Color,
    errorColor: Color,
    warningColor: Color,
): Pair<String, Color> {
    val isTarget = selectedTask in targetTasks
    return when {
        isRunning && isTarget -> "Running..." to runningColor
        isTarget && taskResult is AuthorTaskResult.Success -> "Succeeded" to successColor
        isTarget && taskResult is AuthorTaskResult.Failed -> "Failed (${taskResult.exitCode})" to errorColor
        isTarget && taskResult is AuthorTaskResult.Refused -> "Refused" to warningColor
        isTarget && taskResult is AuthorTaskResult.TimedOut -> "Timed out" to errorColor
        else -> "Not run" to defaultColor
    }
}

@Composable
private fun PipelineStageCard(
    title: String,
    status: String,
    statusColor: Color,
    icon: @Composable () -> Painter,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .widthIn(min = ComponentSize.PluginPipelineBoxWidth)
            .clip(GlassShapes.MediumSmall)
            .background(PluginThemeColors.CardBackground)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = PluginThemeColors.CardBorder,
                shape = GlassShapes.MediumSmall,
            )
            .padding(Spacing.SmallMedium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                painter = icon(),
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.Large),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
