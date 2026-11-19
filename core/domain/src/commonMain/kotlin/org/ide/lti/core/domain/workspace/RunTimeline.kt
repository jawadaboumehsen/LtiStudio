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
import org.ide.lti.core.model.run.StageOutcome
import org.ide.lti.core.model.run.StageState
import org.ide.lti.core.model.run.StageStep

/**
 * Where a run's time went, as Markdown: each stage with when it started (from the run's start), how long it took
 * and its share of the stages that ran, why it ran, and its slowest steps. Steps are timed only in runs recorded
 * since steps keep their times; older runs show the stages alone. Reads only the run.
 */
public object RunTimeline {
    public fun of(run: BuildRun): String = buildString {
        append("## Timeline\n\n")
        val ran = run.stages.filter { it.state != StageState.SKIPPED && it.state != StageState.NOT_RUN }
        val total = ran.sumOf { it.durationMs ?: 0L }
        if (ran.isEmpty()) append("Every stage came from cache.\n")
        if (total > 0) append("Stages that ran took ${duration(total)} in all.\n\n")
        run.stages.forEach { stage ->
            append("- ").append(PipelineStageSequence.label(stage.stageId)).append(": ")
            append(how(run, stage, total)).append('\n')
            slowest(run.steps.filter { it.stageId == stage.stageId }).forEach { (step, ms) ->
                append("  - ").append(step.label ?: step.command.toolId).append(": ").append(duration(ms)).append('\n')
            }
        }
    }

    private fun how(run: BuildRun, stage: StageOutcome, total: Long): String = when (stage.state) {
        StageState.SKIPPED -> "from cache"

        StageState.NOT_RUN -> "not run"

        else -> buildString {
            val ms = stage.durationMs
            append(if (ms == null) "no time recorded" else duration(ms))
            if (ms != null && total > 0) append(" (").append(ms * PERCENT / total).append("%)")
            stage.startedAt?.let { append(", from +").append(duration((it - run.startedAt).inWholeMilliseconds)) }
            if (stage.state != StageState.EXECUTED) append(", ").append(stage.state.name.lowercase())
            stage.whyRan?.let { append("; ran because ").append(it) }
        }
    }

    /** The stage's [STEPS_SHOWN] longest timed steps, longest first. */
    private fun slowest(steps: List<StageStep>): List<Pair<StageStep, Long>> = steps.mapNotNull { step ->
        val start = step.startedAt
        val end = step.endedAt
        if (start == null || end == null) null else step to (end - start).inWholeMilliseconds
    }.sortedByDescending { it.second }.take(STEPS_SHOWN)

    /** "850 ms", "42 s", "12 min 5 s", "1 h 3 min". */
    internal fun duration(ms: Long): String {
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
