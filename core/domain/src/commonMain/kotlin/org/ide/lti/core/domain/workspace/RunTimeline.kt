/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workspace

import org.ide.lti.core.domain.pipeline.PipelineStageSequence
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageOutcome
import org.ide.lti.core.model.run.StageState
import org.ide.lti.core.model.run.StageStep
import org.ide.lti.core.model.run.StepState

/** Execution classification for a node in [TimelineAnalysis]. */
enum class TimelineNodeStatus {
    EXECUTED,
    CACHED,
    RUNNING,
    FAILED,
    CANCELLED,
    INTERRUPTED,
    PENDING,
    SKIPPED_TARGET,
    NOT_RUN,
}

/** Directed dependency between an upstream stage [from] and downstream consumer [to]. */
data class PipelineStageDependencyEdge(val from: StageId, val to: StageId)

/**
 * Canonical DAG dependencies across the 8-stage ROM build pipeline.
 * [StageId.WORK_TREE_ASSEMBLY] fans out into [StageId.DEBLOAT] and [StageId.MODULE_APPLICATION],
 * which both join at [StageId.BUILD_FLASHABLE_ZIP].
 */
object PipelineStageDependencies {
    val EDGES: List<PipelineStageDependencyEdge> = listOf(
        PipelineStageDependencyEdge(StageId.FIRMWARE_ACQUISITION, StageId.FIRMWARE_EXTRACTION),
        PipelineStageDependencyEdge(StageId.FIRMWARE_EXTRACTION, StageId.WORK_TREE_ASSEMBLY),
        PipelineStageDependencyEdge(StageId.WORK_TREE_ASSEMBLY, StageId.DEBLOAT),
        PipelineStageDependencyEdge(StageId.WORK_TREE_ASSEMBLY, StageId.MODULE_APPLICATION),
        PipelineStageDependencyEdge(StageId.DEBLOAT, StageId.BUILD_FLASHABLE_ZIP),
        PipelineStageDependencyEdge(StageId.MODULE_APPLICATION, StageId.BUILD_FLASHABLE_ZIP),
        PipelineStageDependencyEdge(StageId.BUILD_FLASHABLE_ZIP, StageId.GENERATE_OTA_MANIFEST),
        PipelineStageDependencyEdge(StageId.GENERATE_OTA_MANIFEST, StageId.PUBLISH_RELEASE),
    )

    private val upstreamByStage: Map<StageId, List<StageId>> =
        EDGES.groupBy({ it.to }, { it.from })

    private val downstreamByStage: Map<StageId, List<StageId>> =
        EDGES.groupBy({ it.from }, { it.to })

    fun upstreamOf(stageId: StageId): List<StageId> = upstreamByStage[stageId].orEmpty()

    fun downstreamOf(stageId: StageId): List<StageId> = downstreamByStage[stageId].orEmpty()

    /** Topological depth column (0..6) placing parallel branches on the same column. */
    fun columnOf(stageId: StageId): Int = when (stageId) {
        StageId.FIRMWARE_ACQUISITION -> 0
        StageId.FIRMWARE_EXTRACTION -> 1
        StageId.WORK_TREE_ASSEMBLY -> 2
        StageId.DEBLOAT -> 3
        StageId.MODULE_APPLICATION -> 3
        StageId.BUILD_FLASHABLE_ZIP -> 4
        StageId.GENERATE_OTA_MANIFEST -> 5
        StageId.PUBLISH_RELEASE -> 6
    }

    /** Vertical lane inside a DAG column (0 for primary trunk, 1 for parallel mod branch). */
    fun laneOf(stageId: StageId): Int = when (stageId) {
        StageId.MODULE_APPLICATION -> 1
        else -> 0
    }
}

/** Structured timing and outcome model for a single tool invocation inside a stage. */
data class TimelineStepNode(
    val stageId: StageId,
    val index: Int,
    val label: String,
    val commandDisplay: String,
    val durationMs: Long?,
    val sharePercent: Int,
    val startOffsetMs: Long?,
    val state: StepState,
    val exitCode: Int?,
)

/** Structured DAG & waterfall node for one canonical pipeline stage. */
data class TimelineStageNode(
    val stageId: StageId,
    val order: Int,
    val column: Int,
    val lane: Int,
    val label: String,
    val status: TimelineNodeStatus,
    val outcomeState: StageState?,
    val durationMs: Long?,
    val sharePercent: Int,
    val startOffsetMs: Long?,
    val whyRan: String?,
    val cacheKey: String?,
    val message: String?,
    val upstream: List<StageId>,
    val steps: List<TimelineStepNode>,
    val slowestSteps: List<TimelineStepNode>,
    val isBottleneck: Boolean,
)

/** Interactive waterfall & dependency analysis derived from a [BuildRun]. */
data class TimelineAnalysis(
    val runId: String,
    val totalExecutedDurationMs: Long,
    val wallSpanMs: Long,
    val executedCount: Int,
    val cachedCount: Int,
    val failedCount: Int,
    val stages: List<TimelineStageNode>,
    val edges: List<PipelineStageDependencyEdge>,
    val slowestSteps: List<TimelineStepNode>,
    val bottleneckStage: StageId?,
) {
    fun stageNode(stageId: StageId): TimelineStageNode? = stages.firstOrNull { it.stageId == stageId }
}

/** Filters build console log lines to a clicked [StageId] or [TimelineStepNode]. */
object TimelineLogFilter {
    fun filter(logs: List<String>, stageId: StageId?, step: TimelineStepNode? = null): List<String> {
        if (stageId == null || logs.isEmpty()) return logs
        val stageLabel = PipelineStageSequence.label(stageId)
        val stageTokens = listOf(stageId.name, stageLabel, stageId.name.lowercase().replace('_', '-'))
        val baseMatches = logs
            .filter { line -> stageTokens.any { token -> line.contains(token, ignoreCase = true) } }
            .ifEmpty { logs }
        val stepTokens = step?.let {
            buildList {
                add(it.label)
                it.commandDisplay.split(' ').firstOrNull()?.takeIf { tok -> tok.isNotBlank() }?.let(::add)
            }
        }.orEmpty()
        return if (stepTokens.isEmpty()) {
            baseMatches
        } else {
            baseMatches
                .filter { line -> stepTokens.any { token -> line.contains(token, ignoreCase = true) } }
                .ifEmpty { baseMatches }
        }
    }
}

/**
 * Where a run's time went: produces both Markdown reports ([of]) and structured DAG / waterfall /
 * bottleneck analysis ([analyze]) shared across UI timelines and inspection dialogs.
 */
object RunTimeline {
    fun of(run: BuildRun): String = buildString {
        val analysis = analyze(run)
        append("## Timeline\n\n")
        val ran = run.stages.filter { it.state != StageState.SKIPPED && it.state != StageState.NOT_RUN }
        val total = analysis.totalExecutedDurationMs
        if (ran.isEmpty()) append("Every stage came from cache.\n")
        if (total > 0) append("Stages that ran took ${duration(total)} in all.\n\n")
        run.stages.forEach { outcome ->
            val node = analysis.stageNode(outcome.stageId) ?: return@forEach
            append("- ").append(node.label).append(": ")
            append(formatStageLine(outcome, node, total)).append('\n')
            node.slowestSteps.forEach { step ->
                val ms = step.durationMs ?: return@forEach
                append("  - ").append(step.label).append(": ").append(duration(ms)).append('\n')
            }
        }
    }

    /** Produces structured DAG, waterfall timing, and bottleneck step analysis for [run]. */
    fun analyze(run: BuildRun): TimelineAnalysis {
        val outcomesByStage = run.stages.associateBy { it.stageId }
        val ran = run.stages.filter { it.state != StageState.SKIPPED && it.state != StageState.NOT_RUN }
        val totalExecuted = ran.sumOf { it.durationMs ?: 0L }
        val maxThroughOrder = run.throughStage?.let { PipelineStageSequence.orderOf(it) }
        val bottleneckStageId = ran
            .filter { (it.durationMs ?: 0L) > 0L }
            .maxByOrNull { it.durationMs ?: 0L }
            ?.stageId

        val stepsByStage = run.steps.groupBy { it.stageId }
        val allStepNodes = mutableListOf<TimelineStepNode>()

        val stageNodes = PipelineStageSequence.ORDER.map { stageId ->
            val outcome = outcomesByStage[stageId]
            val order = PipelineStageSequence.orderOf(stageId)
            val beyondTarget = maxThroughOrder != null && order > maxThroughOrder
            val status = resolveStatus(outcome, beyondTarget)
            val stageDuration = outcome?.durationMs
            val sharePercent = computeShare(stageDuration, totalExecuted)
            val startOffsetMs = outcome?.startedAt?.let { (it - run.startedAt).inWholeMilliseconds }
            val stageStepNodes = stepsByStage[stageId].orEmpty().map { step ->
                buildStepNode(
                    step = step,
                    run = run,
                    totalExecuted = totalExecuted,
                ).also { allStepNodes += it }
            }
            val slowestForStage = stageStepNodes
                .filter { it.durationMs != null }
                .sortedByDescending { it.durationMs ?: 0L }
                .take(STEPS_SHOWN)

            TimelineStageNode(
                stageId = stageId,
                order = order,
                column = PipelineStageDependencies.columnOf(stageId),
                lane = PipelineStageDependencies.laneOf(stageId),
                label = PipelineStageSequence.label(stageId),
                status = status,
                outcomeState = outcome?.state,
                durationMs = stageDuration,
                sharePercent = sharePercent,
                startOffsetMs = startOffsetMs,
                whyRan = outcome?.whyRan,
                cacheKey = outcome?.cacheKey,
                message = outcome?.message,
                upstream = PipelineStageDependencies.upstreamOf(stageId),
                steps = stageStepNodes,
                slowestSteps = slowestForStage,
                isBottleneck = stageId == bottleneckStageId,
            )
        }

        val wallSpanMs = computeWallSpanMs(run, stageNodes, totalExecuted)
        val slowestGlobal = allStepNodes
            .filter { it.durationMs != null }
            .sortedByDescending { it.durationMs ?: 0L }

        return TimelineAnalysis(
            runId = run.id,
            totalExecutedDurationMs = totalExecuted,
            wallSpanMs = wallSpanMs,
            executedCount = stageNodes.count { it.status == TimelineNodeStatus.EXECUTED },
            cachedCount = stageNodes.count { it.status == TimelineNodeStatus.CACHED },
            failedCount = stageNodes.count { it.status == TimelineNodeStatus.FAILED },
            stages = stageNodes,
            edges = PipelineStageDependencies.EDGES,
            slowestSteps = slowestGlobal,
            bottleneckStage = bottleneckStageId,
        )
    }

    private fun resolveStatus(outcome: StageOutcome?, beyondTarget: Boolean): TimelineNodeStatus =
        when (outcome?.state) {
            null ->
                if (beyondTarget) TimelineNodeStatus.SKIPPED_TARGET else TimelineNodeStatus.NOT_RUN
            StageState.EXECUTED -> TimelineNodeStatus.EXECUTED
            StageState.SKIPPED -> TimelineNodeStatus.CACHED
            StageState.RUNNING -> TimelineNodeStatus.RUNNING
            StageState.FAILED -> TimelineNodeStatus.FAILED
            StageState.CANCELLED -> TimelineNodeStatus.CANCELLED
            StageState.INTERRUPTED -> TimelineNodeStatus.INTERRUPTED
            StageState.PENDING -> TimelineNodeStatus.PENDING
            StageState.NOT_RUN ->
                if (beyondTarget) TimelineNodeStatus.SKIPPED_TARGET else TimelineNodeStatus.NOT_RUN
        }

    private fun buildStepNode(step: StageStep, run: BuildRun, totalExecuted: Long): TimelineStepNode {
        val start = step.startedAt
        val end = step.endedAt
        val durationMs = if (start != null && end != null) (end - start).inWholeMilliseconds.coerceAtLeast(0L) else null
        val startOffsetMs = start?.let { (it - run.startedAt).inWholeMilliseconds }
        val label = step.label ?: step.command.toolId
        val cmdParts = listOf(step.command.toolId) + step.command.args
        return TimelineStepNode(
            stageId = step.stageId,
            index = step.index,
            label = label,
            commandDisplay = cmdParts.joinToString(" "),
            durationMs = durationMs,
            sharePercent = computeShare(durationMs, totalExecuted),
            startOffsetMs = startOffsetMs,
            state = step.state,
            exitCode = step.exitCode,
        )
    }

    private fun computeShare(durationMs: Long?, totalMs: Long): Int =
        if (durationMs != null && totalMs > 0L) ((durationMs * PERCENT) / totalMs).toInt() else 0

    private fun computeWallSpanMs(run: BuildRun, stageNodes: List<TimelineStageNode>, totalExecuted: Long): Long {
        val endedMs = run.endedAt?.let { (it - run.startedAt).inWholeMilliseconds } ?: 0L
        val maxStageEnd = stageNodes.maxOfOrNull { node ->
            val offset = node.startOffsetMs ?: 0L
            val dur = node.durationMs ?: 0L
            offset + dur
        } ?: 0L
        return maxOf(endedMs, maxStageEnd, totalExecuted)
    }

    private fun formatStageLine(outcome: StageOutcome, node: TimelineStageNode, total: Long): String =
        when (outcome.state) {
            StageState.SKIPPED -> "from cache"

            StageState.NOT_RUN -> "not run"

            else -> buildString {
                val ms = node.durationMs
                append(if (ms == null) "no time recorded" else duration(ms))
                if (ms != null && total > 0) append(" (").append(node.sharePercent).append("%)")
                node.startOffsetMs?.let { append(", from +").append(duration(it)) }
                if (outcome.state != StageState.EXECUTED) append(", ").append(outcome.state.name.lowercase())
                outcome.whyRan?.let { append("; ran because ").append(it) }
            }
        }

    /** "850 ms", "42 s", "12 min 5 s", "1 h 3 min". */
    fun duration(ms: Long): String {
        val s = ms / MS
        return when {
            ms < MS -> "$ms ms"
            s < MINUTE -> "$s s"
            s < HOUR -> "${s / MINUTE} min ${s % MINUTE} s"
            else -> "${s / HOUR} h ${s % HOUR / MINUTE} min"
        }
    }

    private const val STEPS_SHOWN = 3
    private const val PERCENT = 100L
    private const val MS = 1000L
    private const val MINUTE = 60L
    private const val HOUR = 3600L
}
