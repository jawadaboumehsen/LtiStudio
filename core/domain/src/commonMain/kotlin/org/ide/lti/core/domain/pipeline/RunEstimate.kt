/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline

import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageState

/**
 * How long the next run should take, from earlier runs on this machine: for each stage the preview says runs, its
 * last [SAMPLES] real runs (not cache hits); the typical time is the sum of their medians, the range the sum of their
 * shortest and longest. A stage that never ran here is [unknown], not guessed.
 */
data class RunEstimate(
    val typicalMs: Long,
    val shortestMs: Long,
    val longestMs: Long,
    /** The runs each counted stage was measured from (the smallest count is the weakest evidence). */
    val samples: Int,
    val unknown: List<StageId>,
    /**
     * Net bytes the newest earlier run that executed at least these stages took from the disk (free space when it
     * started minus when it succeeded); null when no such run was measured. Peaks during a run are not measured.
     */
    val diskBytes: Long? = null,
) {
    /** "Time: about 18 min (12-25 min), from 5+ earlier runs here; disk: about 3.2 GiB more, ...". */
    fun describe(): String {
        val parts = mutableListOf<String>()
        if (samples > 0) {
            val range = if (shortestMs == longestMs) "" else " (${minutes(shortestMs)}-${minutes(longestMs)})"
            val runs = if (samples == 1) "1 earlier run" else "$samples+ earlier runs"
            parts += "Time: about ${minutes(typicalMs)}$range, from $runs here"
        }
        diskBytes?.let { parts += "disk: about ${gib(it)} more, as the last run like it (net)" }
        if (unknown.isNotEmpty()) {
            parts += unknown.joinToString { PipelineStageSequence.label(it) } + " never ran here: not counted"
        }
        return parts.joinToString("; ").replaceFirstChar { it.uppercase() } + "."
    }

    companion object {
        const val SAMPLES: Int = 5

        private fun minutes(ms: Long): String =
            if (ms < MINUTE_MS) "${ms / SECOND_MS} s" else "${(ms + MINUTE_MS / 2) / MINUTE_MS} min"

        private fun gib(bytes: Long): String =
            if (bytes < GIB) "${bytes / MIB} MiB" else "${(bytes * TENTHS / GIB) / TENTHS.toDouble()} GiB"

        private const val MIB = 1024L * 1024L
        private const val GIB = MIB * 1024L
        private const val TENTHS = 10L
        private const val SECOND_MS = 1000L
        private const val MINUTE_MS = 60_000L

        /** Null when no stage runs (everything comes from cache). */
        fun of(preview: List<StagePreview>, history: List<BuildRun>): RunEstimate? {
            val runs = preview.filter { it.runsBecause != null }.map { it.stageId }
            if (runs.isEmpty()) return null
            val newestFirst = history.sortedByDescending { it.startedAt }
            val measured = runs.associateWith { stage ->
                newestFirst.mapNotNull { run ->
                    run.stages.firstOrNull { it.stageId == stage && it.state == StageState.EXECUTED }
                        ?.durationMs?.takeIf { it > 0 }
                }.take(SAMPLES).sorted()
            }
            val known = measured.filterValues { it.isNotEmpty() }
            return RunEstimate(
                typicalMs = known.values.sumOf { it[it.size / 2] },
                shortestMs = known.values.sumOf { it.first() },
                longestMs = known.values.sumOf { it.last() },
                samples = known.values.minOfOrNull { it.size } ?: 0,
                unknown = runs.filter { measured[it].isNullOrEmpty() },
                diskBytes = newestFirst.firstNotNullOfOrNull { run ->
                    val ran = run.stages.filter { it.state == StageState.EXECUTED }.map { it.stageId }.toSet()
                    val before = run.runtimeValues[RuntimeKeys.DISK_FREE_BEFORE]?.toLongOrNull()
                    val after = run.runtimeValues[RuntimeKeys.DISK_FREE_AFTER]?.toLongOrNull()
                    val used = if (before != null && after != null) before - after else null
                    used?.takeIf { ran.containsAll(runs) }?.coerceAtLeast(0)
                },
            )
        }
    }
}
