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

import org.ide.lti.core.domain.pipeline.toolkit.BuildFilesystems
import org.ide.lti.core.model.workspace.BuildSettings

/** One way to build an EROFS image: what `mkfs.erofs` is given. */
public data class TrialOption(val label: String, val compression: String, val dedupe: Boolean, val clusterKib: Int?) {
    /** [settings] with this option as [p]'s own choice. */
    public fun applyTo(settings: BuildSettings, p: String): BuildSettings = settings.copy(
        partitionCompression = settings.partitionCompression + (p to compression),
        partitionDedupe = settings.partitionDedupe + (p to dedupe),
        partitionClusterKib = if (clusterKib == null) {
            settings.partitionClusterKib - p
        } else {
            settings.partitionClusterKib + (p to clusterKib)
        },
    )
}

/** A trial build's image size and how long it took; [error] when mkfs.erofs failed. */
public data class TrialResult(
    val option: TrialOption,
    val bytes: Long?,
    val seconds: Double?,
    val error: String? = null,
)

/**
 * Size advisor: builds one partition's EROFS image a few ways into `workbench/trial` (and deletes it), measures each,
 * and says which to use: the fastest that fits the room left in the super group, else the smallest.
 */
public class SizeAdvisor(private val runner: WorkbenchRunner) {
    public suspend fun trial(workspaceId: String, p: String, options: List<TrialOption>): List<TrialResult> =
        runner.job(workspaceId, "Size trial $p") {
            val out = "workbench/trial/$p.img"
            run("mkdir", "-p", abs("workbench/trial"))
            options.map { option ->
                val compress = option.compression.takeIf { it != BuildFilesystems.NO_COMPRESSION }
                val args = listOf("--quiet") +
                    compress?.let { listOf("-z", it) }.orEmpty() +
                    listOf("-Ededupe,fragments").takeIf { option.dedupe }.orEmpty() +
                    option.clusterKib?.let { listOf("-C", (it * KIB).toString()) }.orEmpty() +
                    listOf(
                        "-b", BLOCK.toString(),
                        "--mount-point", if (p == "system") "/" else p,
                        "--fs-config-file", abs("work/configs/fs_config-$p"),
                        "--file-contexts", abs("work/configs/file_context-$p"),
                        abs(out), abs("work/$p"),
                    )
                val printed = tryRun(
                    "sh",
                    "-c",
                    TIMED_SCRIPT,
                    "sh",
                    toolPath("mkfs.erofs"),
                    abs(out),
                    *args.toTypedArray(),
                )
                val (size, seconds) = printed.stdout.trim().lines().lastOrNull().orEmpty().split(' ') + listOf("", "")
                val result = TrialResult(
                    option,
                    size.toLongOrNull()?.takeIf { printed.exitCode == 0 },
                    seconds.toDoubleOrNull(),
                    printed.stderr.trim().takeIf { printed.exitCode != 0 }?.takeLast(ERROR_SHOWN),
                )
                report(
                    if (result.error == null) LogLevel.INF else LogLevel.WRN,
                    "Size trial $p ${option.label}: " +
                        (result.bytes?.let { "${it / MIB} MiB in ${seconds}s" } ?: "failed: ${result.error}"),
                )
                result
            }
        }

    public companion object {
        private const val KIB = 1024L
        private const val MIB = KIB * KIB
        private const val BLOCK = 4096
        private const val ERROR_SHOWN = 400
        private const val CLUSTER = 64

        /**
         * The ways worth trying for [p]: as set now, a 64 KiB cluster, deduplicated (when the boot kernel at
         * [kernelVersion] reads it), both, and lzma (when [decoders] has it). Ways equal to another are tried once.
         */
        public fun options(
            settings: BuildSettings,
            p: String,
            kernelVersion: String?,
            decoders: List<String>,
        ): List<TrialOption> {
            val now = TrialOption(
                "as set now",
                BuildFilesystems.erofsCompression(settings, p),
                BuildFilesystems.erofsDedupe(settings, p, kernelVersion),
                settings.partitionClusterKib[p],
            )
            val dedupes = BuildFilesystems.kernelDedupes(kernelVersion)
            return listOfNotNull(
                now,
                now.copy(label = "64 KiB cluster", clusterKib = CLUSTER),
                now.copy(label = "deduplicated", dedupe = true).takeIf { dedupes },
                now.copy(
                    label = "64 KiB cluster, deduplicated",
                    clusterKib = CLUSTER,
                    dedupe = true,
                ).takeIf { dedupes },
                now.copy(label = "lzma", compression = "lzma").takeIf { "lzma" in decoders },
            ).distinctBy { Triple(it.compression, it.dedupe, it.clusterKib) }
        }

        /** The fastest result that fits in [room] bytes, else the smallest; null when none built. */
        public fun recommend(results: List<TrialResult>, room: Long?): TrialResult? {
            val built = results.filter { it.bytes != null }
            val fitting = built.filter { room == null || it.bytes!! <= room }
            return fitting.minByOrNull { it.seconds ?: Double.MAX_VALUE } ?: built.minByOrNull { it.bytes!! }
        }

        /** `$1` mkfs.erofs, `$2` the image, then its arguments: prints the image size and the seconds it took. */
        private const val TIMED_SCRIPT = """
tool=$1; img=$2; shift 2
rm -f "${'$'}img"
start=${'$'}(date +%s.%N)
"${'$'}tool" "${'$'}@" || exit ${'$'}?
end=${'$'}(date +%s.%N)
printf '%s %s\n' "${'$'}(stat -c %s "${'$'}img")" "${'$'}(echo "${'$'}end - ${'$'}start" | bc 2>/dev/null || awk "BEGIN{print ${'$'}end-${'$'}start}")"
rm -f "${'$'}img""""
    }
}
