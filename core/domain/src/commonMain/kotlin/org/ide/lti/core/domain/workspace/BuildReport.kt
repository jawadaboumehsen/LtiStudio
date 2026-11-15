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

import org.ide.lti.core.domain.pipeline.stages.ChangelogDraft
import org.ide.lti.core.domain.pipeline.stages.DebloatStage
import org.ide.lti.core.domain.workbench.WorkbenchRunner
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.StageState
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

/** The post-build report as Markdown, and the [problems] it found (a package changed or gone, super over). */
public data class BuildReportText(val markdown: String, val problems: List<String>)

/**
 * The post-build report: one page of what a finished run left, read again now. Each package is hashed again and
 * compared with the hash the build recorded; the signing keys' fingerprints; the built images against the super
 * group; how each stage ran; the tool versions; and the changes from stock ([ChangelogDraft]). Writes nothing.
 */
public class BuildReport(private val runner: WorkbenchRunner, private val settings: WorkspaceSettingsStore) {
    public suspend fun of(run: BuildRun): BuildReportText {
        val snapshot = settings.current(run.workspaceId)
        val device = settings.device(run.workspaceId)
        val files = run.artifacts.map { it.linuxPath }
        val (facts, changes) = runner.read(run.workspaceId) {
            Facts.parse(sh(SCRIPT, abs("."), *files.toTypedArray())) to
                ChangelogDraft.of(readText(ChangelogDraft.PLAN), readText(DebloatStage.RESULT), snapshot)
        }
        val problems = mutableListOf<String>()
        val text = buildString {
            header(run, device, snapshot)
            packages(run, facts, problems)
            signing(facts)
            device?.let { budget(it, facts, problems) }
            stages(run)
            if (changes.isNotBlank()) append("\n## Changes from stock\n\n").append(changes.trim()).append('\n')
        }
        return BuildReportText(text, problems)
    }

    private fun StringBuilder.header(run: BuildRun, device: TargetDevice?, snapshot: ConfigurationSnapshot?) {
        append("# Build report\n\n")
        append("- Run: ${run.id} (${run.state.name.lowercase()}, ended ${run.endedAt ?: "?"})\n")
        device?.let { append("- Device: ${it.name} (${it.codename})\n") }
        snapshot?.acquisition?.firmware?.let { append("- Firmware: ${it.version} (${it.buildId})\n") }
    }

    private fun StringBuilder.packages(run: BuildRun, facts: Facts, problems: MutableList<String>) {
        append("\n## Packages\n\n")
        if (run.artifacts.isEmpty()) append("No packages recorded.\n")
        run.artifacts.forEach { artifact ->
            val name = artifact.linuxPath.substringAfterLast('/')
            val now = facts.files[artifact.linuxPath]
            // A checksum file is recorded with the hash it names (the package's): its content is checked instead.
            val sums = name.endsWith(".sha256")
            val actual = if (sums) facts.sums[artifact.linuxPath] else now?.second
            val state = when {
                now == null -> "gone from the workspace".also { problems += "$name is gone" }

                artifact.sha256 == null -> "present (the build recorded no hash)"

                actual != artifact.sha256 ->
                    "CHANGED since the build (now ${actual ?: "unreadable"})".also { problems += "$name changed" }

                sums -> "names the package's hash"

                else -> "matches the build"
            }
            append("- $name: ${mib(now?.first ?: artifact.sizeBytes)}, sha256 ${artifact.sha256 ?: "?"}, $state ")
            append("(${artifact.verification.name.lowercase().replace('_', ' ')})\n")
        }
    }

    private fun StringBuilder.signing(facts: Facts) {
        append("\n## Signing keys\n\n")
        append("- Platform certificate (APKs, OTA): SHA-256 ${facts.cert ?: "not found"}\n")
        append("- AVB public key (boot chain): SHA-256 ${facts.avb ?: "not found"}\n")
    }

    private fun StringBuilder.budget(device: TargetDevice, facts: Facts, problems: MutableList<String>) {
        val built = facts.images.filterKeys { it in device.dynamicPartitions }
        val used = built.values.sum()
        val free = device.superGroupBytes - used
        append("\n## Super partition (${device.superGroupName}, ${mib(device.superGroupBytes)})\n\n")
        built.entries.sortedByDescending { it.value }.forEach { (p, bytes) -> append("- $p: ${mib(bytes)}\n") }
        if (free >= 0) {
            append("- Used ${mib(used)}, ${mib(free)} free\n")
        } else {
            append("- Used ${mib(used)}: ${mib(-free)} OVER\n")
            problems += "the images overflow super"
        }
    }

    private fun StringBuilder.stages(run: BuildRun) {
        append("\n## Stages\n\n")
        run.stages.forEach { s ->
            val how = when (s.state) {
                StageState.SKIPPED -> "from cache"
                else -> "${s.state.name.lowercase()}${s.durationMs?.let { " in ${it / MS} s" }.orEmpty()}"
            }
            append("- ${s.stageId.name.lowercase()}: $how${s.whyRan?.let { " ($it)" }.orEmpty()}\n")
        }
        if (run.toolVersions.isNotEmpty()) {
            append("\n## Tools\n\n")
            run.toolVersions.toSortedMap().forEach { (id, version) -> append("- $id $version\n") }
        }
    }

    /** What [SCRIPT] read: built image sizes, key fingerprints, and each package's size and sha256 now. */
    private class Facts(
        val images: Map<String, Long>,
        val cert: String?,
        val avb: String?,
        val files: Map<String, Pair<Long, String>>,
        /** The hash each checksum file names (its first word). */
        val sums: Map<String, String>,
    ) {
        companion object {
            fun parse(text: String): Facts {
                val rows = text.lines().map { it.split('\t') }
                fun one(tag: String) = rows.firstOrNull { it.size == 2 && it[0] == tag }?.get(
                    1,
                )?.takeIf { it.isNotBlank() }
                return Facts(
                    images = rows.filter { it.size == 3 && it[0] == "img" }
                        .associate { it[1].removeSuffix(".img") to (it[2].toLongOrNull() ?: 0L) },
                    cert = one("cert"),
                    avb = one("avb"),
                    files = rows.filter { it.size == 4 && it[0] == "file" && it[2] != "-" }
                        .associate { it[1] to ((it[2].toLongOrNull() ?: 0L) to it[3]) },
                    sums = rows.filter { it.size == 3 && it[0] == "sum" }.associate { it[1] to it[2] },
                )
            }
        }
    }

    private companion object {
        const val MS = 1000L
        const val MIB = 1024L * 1024L

        fun mib(bytes: Long?) = bytes?.let { "${it / MIB} MiB" } ?: "? MiB"

        /** `$1` the workspace, then the package paths. Reads only; a package's sha256 is computed again. */
        const val SCRIPT = """cd "$1" || exit 1
shift
for i in out/images/*.img; do
  [ -f "${'$'}i" ] && printf 'img\t%s\t%s\n' "${'$'}{i##*/}" "${'$'}(stat -c %s "${'$'}i")"
done
if [ -f keys/platform.x509.pem ]; then
  printf 'cert\t%s\n' "${'$'}(openssl x509 -in keys/platform.x509.pem -noout -fingerprint -sha256 | cut -d= -f2)"
fi
if [ -f keys/avb.avbpubkey ]; then printf 'avb\t%s\n' "${'$'}(sha256sum keys/avb.avbpubkey | cut -d' ' -f1)"; fi
for a in "${'$'}@"; do
  if [ -f "${'$'}a" ]; then
    printf 'file\t%s\t%s\t%s\n' "${'$'}a" "${'$'}(stat -c %s "${'$'}a")" "${'$'}(sha256sum "${'$'}a" | cut -d' ' -f1)"
    case "${'$'}a" in *.sha256) printf 'sum\t%s\t%s\n' "${'$'}a" "${'$'}(cut -d' ' -f1 "${'$'}a" | head -n 1)" ;; esac
  else
    printf 'file\t%s\t-\t-\n' "${'$'}a"
  fi
done
exit 0"""
    }
}
