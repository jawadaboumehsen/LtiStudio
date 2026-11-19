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

import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
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
 * group; how each stage ran; the tool versions; and what the run did ([RunRecord]). Writes nothing.
 */
public class BuildReport(
    private val runner: WorkbenchRunner,
    private val settings: WorkspaceSettingsStore,
    private val snapshots: SnapshotRepository,
) {
    /** The report of [run], read now from its workspace. */
    public suspend fun of(run: BuildRun): BuildReportText {
        val snapshot = snapshots.getSnapshot(run.snapshotId)?.takeIf { it.workspaceId == run.workspaceId }
        val device = settings.device(run.workspaceId)
        val files = run.artifacts.map { it.linuxPath }
        val signing = snapshot?.build?.signing
        val facts = runner.read(run.workspaceId) {
            Facts.parse(
                sh(
                    SCRIPT,
                    abs("."),
                    signing?.platformKeyRef?.let { abs("$it.x509.pem") }.orEmpty(),
                    signing?.avbKeyRef?.let(::abs).orEmpty(),
                    *files.toTypedArray(),
                ),
            )
        }
        val record = RunRecord.read(runner, run.workspaceId, run.id)
        val changes = record.changes(snapshot)
        val problems = mutableListOf<String>()
        if (snapshot == null) problems += "the run's configuration snapshot is unavailable"
        val text = buildString {
            header(run, device, snapshot)
            packages(run, facts, problems)
            signing(facts, record, problems)
            device?.let { budget(it, facts, problems) }
            stages(run)
            onThePhone(record)
            if (changes.isNotBlank()) append("\n## Build configuration changes\n\n").append(changes.trim()).append('\n')
            if (!record.present) {
                append("\nThis run has no record (it predates them): its mod and removed-app results are ")
                append("unavailable.\n")
            }
        }
        return BuildReportText(text, problems)
    }

    /** Run id, state and end, its snapshot, the device now and the firmware. */
    private fun StringBuilder.header(run: BuildRun, device: TargetDevice?, snapshot: ConfigurationSnapshot?) {
        append("# Build report\n\n")
        append("- Run: ${run.id} (${run.state.name.lowercase()}, ended ${run.endedAt ?: "?"})\n")
        append("- Configuration snapshot: ${run.snapshotId}${if (snapshot == null) " (unavailable)" else ""}\n")
        device?.let { append("- Device configuration now: ${it.name} (${it.codename})\n") }
        snapshot?.acquisition?.firmware?.let { append("- Firmware: ${it.version} (${it.buildId})\n") }
    }

    /** Each package hashed again against the hash the build recorded. */
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

    /** Built, installed on a phone, device-tested: three states, from the newest check of this build. */
    private fun StringBuilder.onThePhone(record: RunRecord) {
        append("\n## On the phone\n\n")
        append(record.deviceCheck?.markdown() ?: "Not checked on a phone yet (Run panel > Check the phone).\n")
    }

    /** The keys configured now, and the ones the run recorded signing with; a key changed since is a problem. */
    private fun StringBuilder.signing(facts: Facts, record: RunRecord, problems: MutableList<String>) {
        append("\n## Signing keys\n\n")
        key("Platform certificate (APKs, OTA)", record.certificate, facts.cert, problems)
        key("AVB public key (DER)", record.avbKey, facts.avb, problems)
    }

    /** One key: its fingerprint now and, when the run recorded one, whether the build signed with the same. */
    private fun StringBuilder.key(name: String, signed: String?, now: String?, problems: MutableList<String>) {
        append("- $name: configured now SHA-256 ${now ?: "not found"}")
        when (signed) {
            null -> append("\n")

            now -> append(", the one this build signed with\n")

            else -> {
                append("; this build signed with SHA-256 $signed\n")
                problems += "the $name changed since this build"
            }
        }
    }

    private fun StringBuilder.budget(device: TargetDevice, facts: Facts, problems: MutableList<String>) {
        val built = facts.images.filterKeys { it in device.dynamicPartitions }
        val used = built.values.sum()
        val free = device.superGroupBytes - used
        append("\n## Super partition now (${device.superGroupName}, ${mib(device.superGroupBytes)})\n\n")
        built.entries.sortedByDescending { it.value }.forEach { (p, bytes) -> append("- $p: ${mib(bytes)}\n") }
        if (free >= 0) {
            append("- Used ${mib(used)}, ${mib(free)} free\n")
        } else {
            append("- Used ${mib(used)}: ${mib(-free)} OVER\n")
            problems += "the images overflow super"
        }
    }

    /** How each stage ran (or came from cache), and the tool versions. */
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

        /** [bytes] in whole MiB, "?" when unknown. */
        fun mib(bytes: Long?) = bytes?.let { "${it / MIB} MiB" } ?: "? MiB"

        /** `$1` workspace, `$2` configured certificate, `$3` AVB private key; then package paths. */
        internal const val SCRIPT = """cd "$1" || exit 1
cert="$2"; avb="$3"; shift 3
for i in out/images/*.img; do
  [ -f "${'$'}i" ] && printf 'img\t%s\t%s\n' "${'$'}{i##*/}" "${'$'}(stat -c %s "${'$'}i")"
done
if [ -f "${'$'}cert" ]; then
  fingerprint=${'$'}(openssl x509 -in "${'$'}cert" -noout -fingerprint -sha256 2>/dev/null) &&
    printf 'cert\t%s\n' "${'$'}{fingerprint#*=}"
fi
t=${'$'}(mktemp -d) || exit 1; trap 'rm -rf "${'$'}t"' EXIT
if [ -f "${'$'}avb" ] && openssl pkey -in "${'$'}avb" -pubout -outform DER -out "${'$'}t/avb.der" 2>/dev/null; then
  printf 'avb\t%s\n' "${'$'}(sha256sum "${'$'}t/avb.der" | cut -d' ' -f1)"
fi
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
