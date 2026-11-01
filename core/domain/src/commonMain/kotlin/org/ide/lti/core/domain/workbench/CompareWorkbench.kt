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

import org.ide.lti.core.domain.pipeline.stages.FirmwareExtractionStage
import org.ide.lti.core.domain.pipeline.toolkit.ArchiveDiff
import org.ide.lti.core.domain.pipeline.toolkit.CodePackageDiff
import org.ide.lti.core.domain.pipeline.toolkit.FileDiff
import org.ide.lti.core.domain.pipeline.toolkit.LineDiff
import org.ide.lti.core.domain.pipeline.toolkit.MethodChangeKind
import org.ide.lti.core.domain.pipeline.toolkit.MethodDifference
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.pipeline.toolkit.TreeCompare
import org.ide.lti.core.domain.pipeline.toolkit.TreeDiff
import org.ide.lti.core.domain.pipeline.toolkit.WorkspaceDiff

/**
 * Comparisons of workspace files (FR-033): two files, archives, folders or decoded packages, two packages decoded
 * for the purpose, and a partition of the build against the stock firmware. Reads only, except
 * the decoded copies it keeps in `workbench/compare/`.
 */
public class CompareWorkbench(private val runner: WorkbenchRunner) {

    /** Line differences between two files in the workspace (FR-033). */
    public suspend fun compareFiles(workspaceId: String, a: String, b: String): FileDiff = runner.read(workspaceId) {
        val left = readText(a) ?: throw missing(a)
        val right = readText(b) ?: throw missing(b)
        val changes = ContentWorkbench.lineChanges(left, right)
        FileDiff(a, b, identical = changes.isEmpty(), lineDifferences = changes)
    }

    /** Members missing, added or changed (by CRC) between two zip archives (APK, JAR, OTA) (FR-033). */
    public suspend fun compareArchives(workspaceId: String, a: String, b: String): ArchiveDiff = runner.read(
        workspaceId,
    ) {
        val left = zipMembers(this, a)
        val right = zipMembers(this, b)
        ArchiveDiff(
            missingInB = (left.keys - right.keys).sorted(),
            extraInB = (right.keys - left.keys).sorted(),
            modified = left.keys.intersect(right.keys).filter { left[it] != right[it] }.sorted(),
        )
    }

    /** Files missing, added or changed (by SHA-256) between two folders of the workspace (FR-033). */
    public suspend fun compareFolders(workspaceId: String, a: String, b: String): WorkspaceDiff = runner.read(
        workspaceId,
    ) {
        val left = hashes(this, a)
        val right = hashes(this, b)
        WorkspaceDiff(
            missingInB = (left.keys - right.keys).sorted(),
            extraInB = (right.keys - left.keys).sorted(),
            modified = left.keys.intersect(right.keys).filter { left[it] != right[it] }.sorted(),
        )
    }

    /**
     * Classes (smali files) added, removed or changed between two decoded packages, and for a changed class its
     * methods added, removed or changed (FR-033).
     */
    public suspend fun compareCode(workspaceId: String, a: String, b: String): CodePackageDiff = runner.read(
        workspaceId,
    ) { codeDiff(this, a, b) }

    /**
     * Decodes two APKs or JARs of the workspace (the build's `work/` or the stock `firmware/extracted/`) with
     * apktool, code only and without line numbers, and compares them method by method.
     */
    public suspend fun compareCodeFiles(workspaceId: String, a: String, b: String): CodePackageDiff =
        runner.job(workspaceId, "Compare ${b.substringAfterLast('/')}") {
            listOf(a, b).forEach(::requireInWorkspaceTree)
            val (left, right) = listOf(a to "workbench/compare/a", b to "workbench/compare/b").map { (file, out) ->
                if (!exists(file)) throw missing(file)
                val result = tryRun("apktool", "d", "-r", "-f", "--no-debug-info", "-o", abs(out), abs(file))
                if (result.exitCode != 0) {
                    throw RefusalException(
                        Refusal(
                            cause = "apktool cannot decode this package.",
                            item = file,
                            nextStep = "Check the details: newer dex container versions are not supported " +
                                "by apktool yet.",
                            details = result.stderr.ifBlank { result.stdout }.trim().takeLast(DETAILS),
                        ),
                    )
                }
                out
            }
            codeDiff(this, left, right)
        }

    /** Partitions with both a build tree (`work/<p>`) and a stock one to compare it with. */
    public suspend fun comparablePartitions(workspaceId: String): List<String> = runner.read(workspaceId) {
        if (!exists("work")) return@read emptyList()
        run("find", abs("work"), "-mindepth", "1", "-maxdepth", "1", "-type", "d", "-printf", "%f\n")
            .lines().filter { it.isNotBlank() && exists("${FirmwareExtractionStage.EXTRACTED}/$it") }.sorted()
    }

    /** The build's [partition] against the stock firmware's: what debloat, mods and scripts changed. */
    public suspend fun compareWithStock(workspaceId: String, partition: String): TreeDiff = runner.read(workspaceId) {
        val stockDir = "${FirmwareExtractionStage.EXTRACTED}/$partition"
        val buildDir = "work/$partition"
        if (!exists(stockDir) || !exists(buildDir)) {
            throw RefusalException(
                Refusal("$partition has no stock or no build tree yet.", partition, "Run the build through Assemble."),
            )
        }
        val stock = TreeCompare.parseListing(sh(TreeCompare.LISTING_SCRIPT, abs(stockDir)))
        val build = TreeCompare.parseListing(sh(TreeCompare.LISTING_SCRIPT, abs(buildDir)))
        val candidates = TreeCompare.hashCandidates(stock, build)

        // A few thousand paths per call keeps the command line far below the kernel's limit.
        suspend fun hashes(dir: String) = candidates.chunked(HASH_BATCH).fold(emptyMap<String, String>()) { all, c ->
            all + TreeCompare.parseHashes(sh(TreeCompare.HASH_SCRIPT, abs(dir), *c.toTypedArray()))
        }
        suspend fun sidecars(dir: String) = TreeCompare.Sidecars(
            partition,
            readText("$dir/fs_config-$partition").orEmpty(),
            readText("$dir/file_context-$partition").orEmpty(),
        )
        TreeCompare.compare(
            partition,
            stock,
            build,
            hashes(stockDir),
            hashes(buildDir),
            sidecars(FirmwareExtractionStage.EXTRACTED),
            sidecars("work/configs"),
        )
    }

    private suspend fun codeDiff(job: WorkbenchRunner.Job, a: String, b: String): CodePackageDiff {
        val left = hashes(job, a).filterKeys { it.endsWith(".smali") }
        val right = hashes(job, b).filterKeys { it.endsWith(".smali") }
        val classes = (left.keys - right.keys).map { "removed $it" } + (right.keys - left.keys).map { "added $it" }
        val methodChanges = left.keys.intersect(right.keys).filter { left[it] != right[it] }.flatMap { file ->
            val l = methodsOf(job.readText("$a/$file").orEmpty())
            val r = methodsOf(job.readText("$b/$file").orEmpty())
            val cls = file.removeSuffix(".smali").substringAfter('/')
            (l.keys - r.keys).map { MethodDifference(cls, it, MethodChangeKind.Removed) } +
                (r.keys - l.keys).map { MethodDifference(cls, it, MethodChangeKind.Added) } +
                l.keys.intersect(r.keys).filter { l[it] != r[it] }.map { m ->
                    val lines = LineDiff.diff(l.getValue(m).lines(), r.getValue(m).lines()).orEmpty()
                    MethodDifference(cls, m, MethodChangeKind.Modified, lines)
                }
        }
        return CodePackageDiff(classes.sorted(), methodChanges)
    }

    /** member -> CRC-32, from the `unzip -v` table (`Length Method Size Cmpr Date Time CRC-32 Name`). */
    private suspend fun zipMembers(job: WorkbenchRunner.Job, path: String): Map<String, String> =
        job.run("unzip", "-v", job.abs(path)).lines().mapNotNull { line ->
            line.trim().split(
                WHITESPACE,
                limit = ZIP_COLUMNS,
            ).takeIf { it.size == ZIP_COLUMNS && it[0].all(Char::isDigit) }
        }.associate { it[ZIP_COLUMNS - 1] to it[ZIP_COLUMNS - 2] }

    /** path -> sha256 of each file, or `link:<target>` of each symlink (a link changed or added is a difference). */
    private suspend fun hashes(job: WorkbenchRunner.Job, dir: String): Map<String, String> = job.sh(
        "cd \"$1\" && find . -type f -exec sha256sum {} + && find . -type l -printf 'link:%l  %p\\n'",
        job.abs(dir),
    ).lines().mapNotNull { line ->
        line.split("  ", limit = 2).takeIf { it.size == 2 }?.let { it[1].removePrefix("./") to it[0] }
    }.toMap()

    /**
     * method signature -> its `.method` line and body, in one smali file; [CLASS_LEVEL] holds every line outside
     * the methods (class header, super, interfaces, annotations, fields), so a change there is reported too.
     */
    private fun methodsOf(smali: String): Map<String, String> {
        val methods = mutableMapOf<String, String>()
        val classLevel = StringBuilder()
        var signature: String? = null
        val body = StringBuilder()
        for (line in smali.lines()) {
            when {
                line.startsWith(".method") -> {
                    signature = line.substringAfterLast(' ')
                    // The header too: an access flag changed on it (private -> public) is a modified method.
                    body.clear().append(line).append('\n')
                }

                line.startsWith(
                    ".end method",
                ) -> signature?.let { methods[it] = body.toString() }.also { signature = null }

                signature != null -> body.append(line).append('\n')

                else -> classLevel.append(line).append('\n')
            }
        }
        methods[CLASS_LEVEL] = classLevel.toString()
        return methods
    }

    private fun requireInWorkspaceTree(path: String) {
        require(
            (path.startsWith("work/") || path.startsWith("${FirmwareExtractionStage.EXTRACTED}/")) &&
                ".." !in path.split('/'),
        ) { "Pick a file of the build (work/…) or of the stock firmware (firmware/extracted/…)." }
    }

    private fun missing(path: String) = RefusalException(
        Refusal("The file does not exist.", path, "Pick another file."),
    )

    private companion object {
        private const val DETAILS = 4_000
        private const val HASH_BATCH = 2_000
        private val WHITESPACE = Regex("""\s+""")
        private const val ZIP_COLUMNS = 8

        /** The lines of a class outside its methods, compared as one entry. */
        private const val CLASS_LEVEL = "(class header and fields)"
    }
}
