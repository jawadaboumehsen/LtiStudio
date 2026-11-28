/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.actions.GlassTextButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.navigation.GlassTab
import org.ide.lti.core.designsystem.component.navigation.GlassTabBar
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.workspace.RunTimeline
import org.ide.lti.core.domain.workspace.TimelineAnalysis
import org.ide.lti.core.domain.workspace.TimelineNodeStatus
import org.ide.lti.core.domain.workspace.TimelineStageNode
import org.ide.lti.core.domain.workspace.TimelineStepNode
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StepState

private const val TAB_DAG = "dag"
private const val TAB_WATERFALL = "waterfall"
private const val TAB_BOTTLENECKS = "bottlenecks"
private const val MAX_BOTTLENECK_ROWS = 8
private const val MIN_BAR_FRACTION = 0.04f
private const val MAX_OFFSET_FRACTION = 0.96f

/**
 * Interactive build visualization combining a dependency Task DAG, execution Waterfall, and
 * Bottleneck Step profiler with click-to-jump navigation to build logs or diffs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractiveBuildTimeline(
    run: BuildRun,
    selectedStageId: StageId?,
    onSelectStage: (StageId?) -> Unit,
    onJumpToLogs: (StageId, TimelineStepNode?) -> Unit,
    modifier: Modifier = Modifier,
    onInspectDiff: ((StageId) -> Unit)? = null,
) {
    val analysis = remember(run) { RunTimeline.analyze(run) }
    var activeTab by rememberSaveable(run.id) { mutableStateOf(TAB_DAG) }
    val selectedNode = analysis.stages.firstOrNull { it.stageId == selectedStageId }
        ?: analysis.bottleneckStage?.let { analysis.stageNode(it) }
        ?: analysis.stages.firstOrNull { it.status != TimelineNodeStatus.NOT_RUN }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("InteractiveBuildTimeline"),
        contentPadding = Spacing.Medium,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium)) {
            TimelineSummaryHeader(
                analysis = analysis,
                selectedStageId = selectedStageId,
                onClearFilter = { onSelectStage(null) },
            )

            GlassTabBar(
                tabs = listOf(
                    GlassTab(id = TAB_DAG, title = "Task DAG"),
                    GlassTab(id = TAB_WATERFALL, title = "Waterfall"),
                    GlassTab(id = TAB_BOTTLENECKS, title = "Bottlenecks (${analysis.slowestSteps.size})"),
                ),
                selectedId = activeTab,
                onTabSelected = { activeTab = it },
                modifier = Modifier.fillMaxWidth(),
            )

            when (activeTab) {
                TAB_DAG -> TaskDagCanvasView(
                    analysis = analysis,
                    selectedStageId = selectedNode?.stageId,
                    onSelectStage = onSelectStage,
                )

                TAB_WATERFALL -> WaterfallView(
                    analysis = analysis,
                    selectedStageId = selectedNode?.stageId,
                    onSelectStage = onSelectStage,
                    onJumpToStepLogs = { step ->
                        onSelectStage(step.stageId)
                        onJumpToLogs(step.stageId, step)
                    },
                )

                else -> BottleneckStepsView(
                    analysis = analysis,
                    onJumpToStepLogs = { step ->
                        onSelectStage(step.stageId)
                        onJumpToLogs(step.stageId, step)
                    },
                )
            }

            if (selectedNode != null) {
                GlassHorizontalDivider()
                SelectedStageInspector(
                    node = selectedNode,
                    onJumpToLogs = { onJumpToLogs(selectedNode.stageId, null) },
                    onJumpToStepLogs = { step -> onJumpToLogs(selectedNode.stageId, step) },
                    onInspectDiff = onInspectDiff?.let { cb -> { cb(selectedNode.stageId) } },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineSummaryHeader(analysis: TimelineAnalysis, selectedStageId: StageId?, onClearFilter: () -> Unit) {
    val typography = GlassTheme.typography
    val diagnostics = GlassTheme.diagnosticColors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Execution Graph & Build Profiler",
                style = typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val totalLabel = if (analysis.totalExecutedDurationMs > 0L) {
                RunTimeline.duration(analysis.totalExecutedDurationMs)
            } else {
                "cached"
            }
            Text(
                text = "${analysis.executedCount} executed · ${analysis.cachedCount} cached · $totalLabel active",
                style = typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
            analysis.bottleneckStage?.let { bottleneck ->
                val node = analysis.stageNode(bottleneck)
                if (node != null) {
                    GlassChip(
                        label = "Slowest: ${node.label} (${node.sharePercent}%)",
                        selected = true,
                    )
                }
            }
            if (analysis.failedCount > 0) {
                GlassChip(
                    label = "${analysis.failedCount} failed",
                    selected = true,
                )
            }
            if (selectedStageId != null) {
                GlassTextButton(onClick = onClearFilter) {
                    Text(
                        text = "Show all logs",
                        color = diagnostics.info,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskDagCanvasView(
    analysis: TimelineAnalysis,
    selectedStageId: StageId?,
    onSelectStage: (StageId?) -> Unit,
) {
    val nodeWidth = ComponentSize.PluginPipelineBoxWidth
    val nodeHeight = ComponentSize.ActionCardHeight
    val columnGap = Spacing.ExtraExtraLarge
    val laneGap = Spacing.Medium
    val colPitch = nodeWidth + columnGap
    val lanePitch = nodeHeight + laneGap
    val columns = (analysis.stages.maxOfOrNull { it.column } ?: 0) + 1
    val lanes = (analysis.stages.maxOfOrNull { it.lane } ?: 0) + 1
    val canvasWidth = (colPitch * columns) - columnGap
    val canvasHeight = (lanePitch * lanes) - laneGap

    val density = LocalDensity.current
    val nodeWidthPx = with(density) { nodeWidth.toPx() }
    val nodeHeightPx = with(density) { nodeHeight.toPx() }
    val colPitchPx = with(density) { colPitch.toPx() }
    val lanePitchPx = with(density) { lanePitch.toPx() }
    val connectorStrokePx = with(density) { StrokeWidth.Focused.toPx() }
    val diagnostics = GlassTheme.diagnosticColors
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        Box(
            modifier = Modifier
                .width(canvasWidth)
                .height(canvasHeight),
        ) {
            Canvas(
                modifier = Modifier
                    .width(canvasWidth)
                    .height(canvasHeight),
            ) {
                analysis.edges.forEach { edge ->
                    drawDagEdge(
                        analysis = analysis,
                        edge = edge,
                        metrics = DagGeometryMetrics(
                            nodeWidthPx = nodeWidthPx,
                            nodeHeightPx = nodeHeightPx,
                            colPitchPx = colPitchPx,
                            lanePitchPx = lanePitchPx,
                            strokeWidthPx = connectorStrokePx,
                        ),
                        successColor = diagnostics.success,
                        infoColor = diagnostics.info,
                        errorColor = diagnostics.error,
                        outlineColor = outlineColor,
                    )
                }
            }

            analysis.stages.forEach { node ->
                DagStageCard(
                    node = node,
                    isSelected = node.stageId == selectedStageId,
                    onClick = { onSelectStage(node.stageId) },
                    modifier = Modifier
                        .offset(x = colPitch * node.column, y = lanePitch * node.lane)
                        .width(nodeWidth)
                        .height(nodeHeight),
                )
            }
        }
    }
}

private data class DagGeometryMetrics(
    val nodeWidthPx: Float,
    val nodeHeightPx: Float,
    val colPitchPx: Float,
    val lanePitchPx: Float,
    val strokeWidthPx: Float,
)

@Suppress("LongParameterList")
private fun DrawScope.drawDagEdge(
    analysis: TimelineAnalysis,
    edge: org.ide.lti.core.domain.workspace.PipelineStageDependencyEdge,
    metrics: DagGeometryMetrics,
    successColor: Color,
    infoColor: Color,
    errorColor: Color,
    outlineColor: Color,
) {
    val from = analysis.stageNode(edge.from) ?: return
    val to = analysis.stageNode(edge.to) ?: return
    val startX = (from.column * metrics.colPitchPx) + metrics.nodeWidthPx
    val startY = (from.lane * metrics.lanePitchPx) + (metrics.nodeHeightPx / 2f)
    val endX = to.column * metrics.colPitchPx
    val endY = (to.lane * metrics.lanePitchPx) + (metrics.nodeHeightPx / 2f)
    val midX = (startX + endX) / 2f
    val edgeColor = when {
        to.status == TimelineNodeStatus.FAILED -> errorColor
        from.status == TimelineNodeStatus.CACHED && to.status == TimelineNodeStatus.CACHED ->
            infoColor.copy(alpha = AlphaTokens.Prominent)
        from.status == TimelineNodeStatus.EXECUTED || to.status == TimelineNodeStatus.EXECUTED ->
            successColor.copy(alpha = AlphaTokens.Prominent)
        else -> outlineColor.copy(alpha = AlphaTokens.Border)
    }
    val path = Path().apply {
        moveTo(startX, startY)
        cubicTo(midX, startY, midX, endY, endX, endY)
    }
    drawPath(
        path = path,
        color = edgeColor,
        style = Stroke(width = metrics.strokeWidthPx, cap = StrokeCap.Round),
    )
}

@Composable
private fun DagStageCard(
    node: TimelineStageNode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = statusColor(node.status)
    val colors = MaterialTheme.colorScheme
    val background = when {
        isSelected -> accent.copy(alpha = AlphaTokens.Hover)
        else -> colors.surfaceContainer.copy(alpha = AlphaTokens.Heavy)
    }
    val borderColor = if (isSelected) accent else accent.copy(alpha = AlphaTokens.Border)

    Column(
        modifier = modifier
            .clip(GlassShapes.Small)
            .background(background)
            .border(
                width = if (isSelected) StrokeWidth.Focused else StrokeWidth.Hairline,
                color = borderColor,
                shape = GlassShapes.Small,
            )
            .clickable(onClick = onClick)
            .padding(Spacing.Small)
            .testTag("DagNode_${node.stageId.name}"),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${node.order}. ${node.label}",
                style = GlassTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(Spacing.ExtraSmall))
            StatusBadgePill(label = statusShortLabel(node.status), color = accent)
        }

        val durationMs = node.durationMs
        val timingText = when {
            node.status == TimelineNodeStatus.CACHED -> "Reused cached outputs"
            durationMs != null -> "${RunTimeline.duration(durationMs)} · ${node.sharePercent}%"
            node.status == TimelineNodeStatus.RUNNING -> "Executing…"
            node.status == TimelineNodeStatus.FAILED -> node.message ?: "Stage failed"
            else -> statusShortLabel(node.status)
        }
        Text(
            text = timingText,
            style = GlassTheme.typography.bodySmall,
            fontFamily = codeFontFamily(),
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${node.steps.size} step(s)",
                style = GlassTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
            val whyRan = node.whyRan
            if (node.isBottleneck) {
                StatusBadgePill(label = "BOTTLENECK", color = GlassTheme.diagnosticColors.warning)
            } else if (!whyRan.isNullOrBlank()) {
                Text(
                    text = whyRan,
                    style = GlassTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun WaterfallView(
    analysis: TimelineAnalysis,
    selectedStageId: StageId?,
    onSelectStage: (StageId?) -> Unit,
    onJumpToStepLogs: (TimelineStepNode) -> Unit,
) {
    val span = analysis.wallSpanMs.coerceAtLeast(1L)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        analysis.stages.forEach { node ->
            val accent = statusColor(node.status)
            val isSelected = node.stageId == selectedStageId
            val startFraction = ((node.startOffsetMs ?: 0L).toFloat() / span.toFloat()).coerceIn(0f, 0.96f)
            val durationMs = node.durationMs
            val widthFraction = if (durationMs != null && durationMs > 0L) {
                (durationMs.toFloat() / span.toFloat()).coerceIn(0.04f, 1f - startFraction)
            } else {
                0.04f
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GlassShapes.Small)
                    .background(
                        if (isSelected) {
                            accent.copy(alpha = AlphaTokens.Glow)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AlphaTokens.Half)
                        },
                    )
                    .clickable { onSelectStage(node.stageId) }
                    .padding(Spacing.Small),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${node.order}. ${node.label}",
                            style = GlassTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        StatusBadgePill(label = statusShortLabel(node.status), color = accent)
                        if (node.isBottleneck) {
                            StatusBadgePill(label = "SLOWEST", color = GlassTheme.diagnosticColors.warning)
                        }
                    }
                    val offsetText = node.startOffsetMs?.let { "+${RunTimeline.duration(it)} · " }.orEmpty()
                    val durText = node.durationMs?.let { RunTimeline.duration(it) } ?: statusShortLabel(node.status)
                    Text(
                        text = "$offsetText$durText (${node.sharePercent}%)",
                        style = GlassTheme.typography.bodySmall,
                        fontFamily = codeFontFamily(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Waterfall proportional track
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Spacing.SmallMedium)
                        .clip(GlassShapes.ExtraSmall)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = AlphaTokens.Half)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (startFraction > 0f) {
                        Spacer(modifier = Modifier.weight(startFraction))
                    }
                    Box(
                        modifier = Modifier
                            .weight(widthFraction)
                            .height(Spacing.Small)
                            .clip(GlassShapes.ExtraSmall)
                            .background(accent),
                    )
                    val trailing = (1f - startFraction - widthFraction).coerceAtLeast(0f)
                    if (trailing > 0f) {
                        Spacer(modifier = Modifier.weight(trailing))
                    }
                }

                if (isSelected && node.steps.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = Spacing.Medium, top = Spacing.ExtraSmall),
                        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                    ) {
                        node.steps.forEach { step ->
                            StepTimingRow(
                                step = step,
                                onClick = { onJumpToStepLogs(step) },
                                tagPrefix = "WaterfallStep",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottleneckStepsView(analysis: TimelineAnalysis, onJumpToStepLogs: (TimelineStepNode) -> Unit) {
    if (analysis.slowestSteps.isEmpty()) {
        Text(
            text = "No per-step tool timings were recorded for this run.",
            style = GlassTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
        analysis.slowestSteps.take(MAX_BOTTLENECK_ROWS).forEach { step ->
            StepTimingRow(
                step = step,
                showStageName = true,
                tagPrefix = "BottleneckStep",
                onClick = { onJumpToStepLogs(step) },
            )
        }
    }
}

@Composable
private fun StepTimingRow(
    step: TimelineStepNode,
    onClick: () -> Unit,
    showStageName: Boolean = false,
    tagPrefix: String = "TimelineStep",
) {
    val diagnostics = GlassTheme.diagnosticColors
    val stepColor = when (step.state) {
        StepState.FAILED -> diagnostics.error
        StepState.COMPLETED -> diagnostics.success
        StepState.CANCELLED -> diagnostics.warning
        StepState.INTERRUPTED -> diagnostics.error
        StepState.RUNNING -> diagnostics.info
        StepState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.Small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.Half))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall)
            .testTag("${tagPrefix}_${step.stageId.name}_${step.index}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = step.label,
                    style = GlassTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (showStageName) {
                    StatusBadgePill(label = step.stageId.name, color = MaterialTheme.colorScheme.primary)
                }
                if (step.exitCode != null && step.exitCode != 0) {
                    StatusBadgePill(label = "exit ${step.exitCode}", color = diagnostics.error)
                }
            }
            Text(
                text = step.commandDisplay,
                style = GlassTheme.typography.labelSmall,
                fontFamily = codeFontFamily(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(Spacing.Small))
        Column(horizontalAlignment = Alignment.End) {
            val durLabel = step.durationMs?.let { RunTimeline.duration(it) } ?: step.state.name.lowercase()
            Text(
                text = "$durLabel (${step.sharePercent}%)",
                style = GlassTheme.typography.labelSmall,
                fontFamily = codeFontFamily(),
                color = stepColor,
            )
            Text(
                text = "Jump to log →",
                style = GlassTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedStageInspector(
    node: TimelineStageNode,
    onJumpToLogs: () -> Unit,
    onJumpToStepLogs: (TimelineStepNode) -> Unit,
    onInspectDiff: (() -> Unit)?,
) {
    val typography = GlassTheme.typography
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("SelectedStageInspector"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Stage ${node.order}: ${node.label}",
                    style = typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val why = node.whyRan?.let { "Reason: $it" }
                    ?: node.message
                    ?: "Upstream dependencies: ${node.upstream.joinToString { it.name }.ifBlank { "root" }}"
                Text(
                    text = why,
                    style = typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                GlassButton(
                    onClick = onJumpToLogs,
                    variant = GlassButtonVariant.Standard,
                    modifier = Modifier.testTag("JumpToStageLogsButton"),
                ) { Text("Jump to Logs") }
                if (onInspectDiff != null) {
                    GlassButton(
                        onClick = onInspectDiff,
                        variant = GlassButtonVariant.Standard,
                        modifier = Modifier.testTag("InspectStageDiffButton"),
                    ) { Text("Inspect Diff") }
                }
            }
        }

        if (!node.cacheKey.isNullOrBlank()) {
            Text(
                text = "Cache key: ${node.cacheKey}",
                style = typography.labelSmall,
                fontFamily = codeFontFamily(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (node.steps.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                node.steps.forEach { step ->
                    StepTimingRow(
                        step = step,
                        onClick = { onJumpToStepLogs(step) },
                        tagPrefix = "InspectorStep",
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadgePill(label: String, color: Color) {
    Text(
        text = label,
        style = GlassTheme.typography.labelSmall.copy(
            fontFamily = codeFontFamily(),
            fontSize = FontSize.Micro,
            fontWeight = FontWeight.Bold,
            color = color,
        ),
        modifier = Modifier
            .clip(GlassShapes.ExtraSmall)
            .background(color.copy(alpha = AlphaTokens.Glow))
            .padding(horizontal = Spacing.ExtraSmall, vertical = Spacing.ExtraExtraSmall),
    )
}

@Composable
private fun statusColor(status: TimelineNodeStatus): Color {
    val diagnostics = GlassTheme.diagnosticColors
    return when (status) {
        TimelineNodeStatus.EXECUTED -> diagnostics.success
        TimelineNodeStatus.CACHED -> diagnostics.info
        TimelineNodeStatus.RUNNING -> MaterialTheme.colorScheme.primary
        TimelineNodeStatus.FAILED -> diagnostics.error
        TimelineNodeStatus.CANCELLED -> diagnostics.warning
        TimelineNodeStatus.INTERRUPTED -> diagnostics.error
        TimelineNodeStatus.PENDING -> diagnostics.warning
        TimelineNodeStatus.SKIPPED_TARGET -> MaterialTheme.colorScheme.onSurfaceVariant
        TimelineNodeStatus.NOT_RUN -> MaterialTheme.colorScheme.outline
    }
}

private fun statusShortLabel(status: TimelineNodeStatus): String = when (status) {
    TimelineNodeStatus.EXECUTED -> "EXECUTED"
    TimelineNodeStatus.CACHED -> "CACHED"
    TimelineNodeStatus.RUNNING -> "RUNNING"
    TimelineNodeStatus.FAILED -> "FAILED"
    TimelineNodeStatus.CANCELLED -> "CANCELLED"
    TimelineNodeStatus.INTERRUPTED -> "INTERRUPTED"
    TimelineNodeStatus.PENDING -> "QUEUED"
    TimelineNodeStatus.SKIPPED_TARGET -> "TARGET"
    TimelineNodeStatus.NOT_RUN -> "NOT RUN"
}
