/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workbench

import org.ide.lti.core.common.utils.runCatchingCancellable
import org.ide.lti.core.domain.pipeline.toolkit.BuildFilesystems
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore

/** One partition built a smaller way than now: measured by a trial build ([SizeAdvisor]), both sizes and times. */
data class CompressionStep(
    val partition: String,
    val option: TrialOption,
    val nowBytes: Long,
    val bytes: Long,
    val nowSeconds: Double?,
    val seconds: Double?,
) {
    val savingBytes: Long get() = nowBytes - bytes
}

/**
 * The size goal's second half: what compression would save once the apps are picked. Every EROFS dynamic partition
 * that is built (not kept as shipped) is tried the advisor's ways (as set now, a 64 KiB cluster, deduplicated when the
 * boot kernel reads it, lzma when it decodes it); per partition the smallest result that is smaller than now. Trials
 * build into `workbench/trial` and delete it: nothing of the build changes until a step is used.
 */
class CompressionGoal(
    private val advisor: SizeAdvisor,
    private val boot: BootWorkbench,
    private val settings: WorkspaceSettingsStore,
    private val runner: WorkbenchRunner,
) {
    /**
     * Measures the partitions largest first (by their stock image) and stops once the savings found cover
     * [shortBytes]: each trial is a full build of a partition, minutes for system with deduplication.
     * [progress] hears which partition is being tried, as "system (1 of 7)".
     */
    suspend fun measure(
        workspaceId: String,
        shortBytes: Long,
        progress: (String) -> Unit = {},
    ): List<CompressionStep> {
        val build = checkNotNull(settings.current(workspaceId)) { "The workspace has no configuration yet" }.build
        val device = checkNotNull(settings.device(workspaceId)) { "The workspace has no device configuration" }
        val kernel = runCatchingCancellable { boot.kernelFacts(workspaceId) }.getOrNull()
        val candidates = device.dynamicPartitions
            .filter { BuildFilesystems.of(build, it) == BuildFilesystems.EROFS && it !in build.keepStock }
        val sizes = runner.read(workspaceId) {
            candidates.associateWith { p ->
                tryRun("stat", "-c", "%s", abs("firmware/extracted/$p.img")).stdout.trim().toLongOrNull() ?: 0L
            }
        }
        val ordered = candidates.sortedByDescending { sizes[it] ?: 0L }
        val steps = mutableListOf<CompressionStep>()
        for ((i, p) in ordered.withIndex()) {
            if (pick(steps, shortBytes).sumOf { it.savingBytes } >= shortBytes) break
            progress("$p (${i + 1} of ${ordered.size})")
            val options = SizeAdvisor.options(build, p, kernel?.version, kernel?.erofsCompressions.orEmpty())
            stepOf(p, advisor.trial(workspaceId, p, options, build.blockSize))?.let { steps += it }
        }
        return steps
    }

    /** The first result is the partition as set now: the baseline; the step is the smallest that beats it. */
    private fun stepOf(p: String, results: List<TrialResult>): CompressionStep? {
        val now = results.firstOrNull()
        val best = results.filter { it.bytes != null }.minByOrNull { it.bytes ?: Long.MAX_VALUE }
        val nowBytes = now?.bytes
        val bestBytes = best?.bytes
        return if (nowBytes != null && bestBytes != null && bestBytes < nowBytes) {
            CompressionStep(p, best.option, nowBytes, bestBytes, now.seconds, best.seconds)
        } else {
            null
        }
    }

    companion object {
        /** The largest savings first until [shortBytes] are covered; all of them when they cannot be. */
        fun pick(steps: List<CompressionStep>, shortBytes: Long): List<CompressionStep> {
            val picked = mutableListOf<CompressionStep>()
            var saved = 0L
            for (step in steps.sortedByDescending { it.savingBytes }) {
                if (saved >= shortBytes) break
                picked += step
                saved += step.savingBytes
            }
            return picked
        }
    }
}
