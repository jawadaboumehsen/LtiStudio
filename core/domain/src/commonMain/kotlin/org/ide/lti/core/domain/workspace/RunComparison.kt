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

import kotlinx.serialization.json.Json
import org.ide.lti.core.domain.pipeline.StageInputs
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.workbench.WorkbenchRunner
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageOutcome
import org.ide.lti.core.model.run.StageState
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

/** Two runs side by side as Markdown; [differences] counts the lines that differ (0: the runs did the same). */
public data class RunComparisonText(val markdown: String, val differences: Int)

/**
 * What differs between two runs of a workspace, from what each run kept: its settings snapshot (every stage's
 * fields, so the firmware, scripts and mod options too), its mods' versions, its record ([RunRecord]: the files the
 * mods changed, the apps removed, the keys it signed with), its tool versions, its packages and how each stage ran.
 * Nothing is read from today's settings or work/; a run without a snapshot or a record says so. Writes nothing.
 */
public class RunComparison(private val runner: WorkbenchRunner, private val snapshots: SnapshotRepository) {
    /** [older] against [newer] (the order only decides which side is "before"). */
    public suspend fun compare(older: BuildRun, newer: BuildRun): RunComparisonText {
        val a = Side(older, snapshot(older), RunRecord.read(runner, older.workspaceId, older.id))
        val b = Side(newer, snapshot(newer), RunRecord.read(runner, newer.workspaceId, newer.id))
        return of(a, b)
    }

    private suspend fun snapshot(run: BuildRun): ConfigurationSnapshot? =
        snapshots.getSnapshot(run.snapshotId)?.takeIf { it.workspaceId == run.workspaceId }

    /** One run and what it kept. */
    public data class Side(val run: BuildRun, val snapshot: ConfigurationSnapshot?, val record: RunRecord)

    public companion object {
        private val json = Json

        /** The comparison of [a] (before) and [b] (after). */
        public fun of(a: Side, b: Side): RunComparisonText {
            var differences = 0
            val text = buildString {
                append("# Compare runs\n\n")
                append("- Before: ${a.run.id} (${a.run.state.name.lowercase()}, ended ${a.run.endedAt ?: "?"})\n")
                append("- After: ${b.run.id} (${b.run.state.name.lowercase()}, ended ${b.run.endedAt ?: "?"})\n")
                val sections = listOf(
                    "Settings" to settings(a, b),
                    "Mods" to mods(a, b),
                    "Files the mods changed" to modFiles(a, b),
                    "Removed apps" to removedApps(a, b),
                    "Tools" to StageInputs.changes(a.run.toolVersions, b.run.toolVersions),
                    "Signing keys" to keys(a.record, b.record),
                    "Packages" to packages(a.run, b.run),
                )
                sections.filter { it.second.isNotEmpty() }.forEach { (title, lines) ->
                    differences += lines.size
                    append("\n## $title\n\n")
                    lines.forEach { append("- ").append(it).append('\n') }
                }
                if (differences == 0) append("\nThe two runs used the same settings, mods and tools.\n")
                missing(a, b).forEach { append("\n").append(it).append('\n') }
                append("\n## Stages\n\n")
                stages(a.run, b.run).forEach { append("- ").append(it).append('\n') }
            }
            return RunComparisonText(text, differences)
        }

        /** Each stage's settings field that differs, as `build.compression: "lz4" -> "lzma"`. */
        public fun settingsChanges(before: ConfigurationSnapshot, after: ConfigurationSnapshot): List<String> =
            StageId.entries.flatMap { stage ->
                StageInputs.changes(StageInputs.settings(stage, before, json), StageInputs.settings(stage, after, json))
            }

        /** Each mod added, removed or moved to another version (the snapshots' lockfiles). */
        public fun modChanges(before: ConfigurationSnapshot, after: ConfigurationSnapshot): List<String> {
            fun versions(s: ConfigurationSnapshot) = s.customization.lockfile.entries.associate { it.id to it.version }
            return StageInputs.changes(versions(before), versions(after))
        }

        private fun settings(a: Side, b: Side): List<String> = withSnapshots(a, b, ::settingsChanges)

        private fun mods(a: Side, b: Side): List<String> = withSnapshots(a, b, ::modChanges)

        /** [compare] of both runs' snapshots; nothing when either is unavailable (said by [missing]). */
        private fun withSnapshots(
            a: Side,
            b: Side,
            compare: (ConfigurationSnapshot, ConfigurationSnapshot) -> List<String>,
        ): List<String> {
            val before = a.snapshot
            val after = b.snapshot
            return if (before == null || after == null) emptyList() else compare(before, after)
        }

        private fun modFiles(a: Side, b: Side): List<String> {
            if (!a.record.present || !b.record.present) return emptyList()
            fun files(r: RunRecord) = r.plan?.operations?.map { it.target }?.toSet().orEmpty()
            return setChanges(files(a.record), files(b.record), "now changed", "no longer changed")
        }

        private fun removedApps(a: Side, b: Side): List<String> {
            if (!a.record.present || !b.record.present) return emptyList()
            fun apps(r: RunRecord) = r.debloat?.removals?.map { it.packageId ?: it.relativePath }?.toSet().orEmpty()
            val freed = { r: RunRecord -> (r.debloat?.logicalBytesFreed ?: 0L) / MIB }
            val total = listOfNotNull(
                "${freed(a.record)} MiB freed → ${freed(b.record)} MiB".takeIf { freed(a.record) != freed(b.record) },
            )
            return setChanges(apps(a.record), apps(b.record), "now removed", "kept again") + total
        }

        private fun keys(a: RunRecord, b: RunRecord): List<String> = listOfNotNull(
            "platform certificate ${a.certificate} → ${b.certificate}"
                .takeIf { a.certificate != null && b.certificate != null && a.certificate != b.certificate },
            "AVB key ${a.avbKey} → ${b.avbKey}"
                .takeIf { a.avbKey != null && b.avbKey != null && a.avbKey != b.avbKey },
        )

        /** Each package by file name: new, gone, or a different hash (with both sizes). */
        private fun packages(a: BuildRun, b: BuildRun): List<String> {
            fun byName(run: BuildRun) = run.artifacts.associateBy { it.linuxPath.substringAfterLast('/') }
            val before = byName(a)
            val after = byName(b)
            return (before.keys + after.keys).sorted().mapNotNull { name ->
                val x = before[name]
                val y = after[name]
                when {
                    x == null -> "$name: new (${mib(y?.sizeBytes)})"
                    y == null -> "$name: not built any more"
                    x.sha256 != y.sha256 -> "$name: different content" + sizes(x.sizeBytes, y.sizeBytes)
                    else -> null
                }
            }
        }

        /** Every stage of either run: cached or how long it ran, before and after, and why it ran after. */
        private fun stages(a: BuildRun, b: BuildRun): List<String> {
            val before = a.stages.associateBy { it.stageId }
            val after = b.stages.associateBy { it.stageId }
            return StageId.entries.filter { it in before || it in after }.map { stage ->
                val why = after[stage]?.whyRan?.let { ": $it" }.orEmpty()
                "${stage.name.lowercase()}: ${how(before[stage])} → ${how(after[stage])}$why"
            }
        }

        private fun how(outcome: StageOutcome?): String = when {
            outcome == null -> "not part of the run"

            outcome.state == StageState.SKIPPED -> "from cache"

            outcome.state == StageState.NOT_RUN -> "not run"

            else -> (if (outcome.state == StageState.EXECUTED) "ran" else outcome.state.name.lowercase()) +
                outcome.durationMs?.let { " in ${it / MS} s" }.orEmpty()
        }

        /** What the comparison could not see: a run without its snapshot or its record. */
        private fun missing(a: Side, b: Side): List<String> = listOf(a, b).flatMap { side ->
            val noRecord = "Run ${side.run.id} has no record (it predates them): mod files, removed apps and keys " +
                "are not compared."
            listOfNotNull(
                "Run ${side.run.id}'s settings snapshot is unavailable: settings and mods are not compared."
                    .takeIf { side.snapshot == null },
                noRecord.takeIf { !side.record.present },
            )
        }

        private fun setChanges(before: Set<String>, after: Set<String>, added: String, removed: String): List<String> =
            (after - before).sorted().map { "$it: $added" } + (before - after).sorted().map { "$it: $removed" }

        /** ", 6482 MiB → 6417 MiB", or nothing when a size was not recorded. */
        private fun sizes(before: Long?, after: Long?): String =
            if (before == null || after == null) "" else ", ${mib(before)} → ${mib(after)}"

        private fun mib(bytes: Long?): String = bytes?.let { "${it / MIB} MiB" } ?: "? MiB"

        private const val MIB = 1024L * 1024L
        private const val MS = 1000L
    }
}
