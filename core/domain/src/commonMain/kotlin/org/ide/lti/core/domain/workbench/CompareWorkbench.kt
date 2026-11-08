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
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.pipeline.toolkit.SmaliCompare
import org.ide.lti.core.domain.pipeline.toolkit.SuperTool
import org.ide.lti.core.domain.pipeline.toolkit.Superblock
import org.ide.lti.core.domain.pipeline.toolkit.TreeChange
import org.ide.lti.core.domain.pipeline.toolkit.TreeChangeKind
import org.ide.lti.core.domain.pipeline.toolkit.TreeCompare
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
        archiveDiff(zipMembers(this, a), zipMembers(this, b))
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
                if (result.exitCode != 0) throw undecodable(file, result.stderr.ifBlank { result.stdout })
                out
            }
            codeDiff(this, left, right).copy(
                dexDifferences = dexChanges(zipMembers(this, abs(a)), zipMembers(this, abs(b))),
            )
        }

    /** The partition trees and images of [workspaceId] a side can be. */
    public suspend fun sources(workspaceId: String): CompareSources = runner.read(workspaceId) { compareSources() }

    /**
     * The partitions inside [image] of [workspaceId] when it is a super image (a sparse one is expanded once to tell),
     * else none.
     */
    public suspend fun superPartitions(workspaceId: String, image: String): List<String> =
        runner.job(workspaceId, "Read ${image.substringAfterLast('/')}") {
            val img = readableImage(image, member = null)
            if (!isSuperImage(img)) return@job emptyList()
            SuperTool.parseLpdumpOutput(run("lpdump", abs(img))).partitions.filter { it.size > 0 }.map { it.name }
        }

    /**
     * [a] against [b], path by path: added, removed, content, metadata (each side's own owner, mode, capabilities,
     * label, link target) or kind changed; and both superblocks. Each side is read in its own workspace's job. Files
     * of the same size are hashed on both sides; with [trustTimes] only those whose modification time differs.
     */
    public suspend fun compareSides(a: CompareSide, b: CompareSide, trustTimes: Boolean = false): SideComparison {
        val left = runner.job(a.workspaceId, "Read ${a.partition} (A)") { snapshot(a) }
        val right = runner.job(b.workspaceId, "Read ${b.partition} (B)") { snapshot(b) }
        val candidates = TreeCompare.hashCandidates(left.entries, right.entries, trustTimes)
        val leftHashes = runner.job(a.workspaceId, "Hash ${a.partition} (A)") { hashSide(a, left, candidates) }
        val rightHashes = runner.job(b.workspaceId, "Hash ${b.partition} (B)") { hashSide(b, right, candidates) }
        val diff = TreeCompare.compare(
            a.partition,
            left.entries,
            right.entries,
            leftHashes,
            rightHashes,
            left.sidecars,
            right.sidecars,
        )
        return SideComparison(a, b, diff, Superblock.compare(left.superblock, right.superblock), candidates.size)
    }

    /**
     * [change] of a [compareSides] looked at closely: a text file's line diff, a zip's entries, a JAR's or APK's dex
     * files and methods, else sizes and hashes. Only a content change has more to show than its row.
     */
    public suspend fun compareEntry(a: CompareSide, b: CompareSide, change: TreeChange): EntryComparison {
        val path = change.path
        val name = path.substringAfterLast('/')
        if (change.kind != TreeChangeKind.CONTENT) {
            return EntryComparison.NoContent(path, "Only its ${change.kind.name.lowercase()} change: see the row.")
        }
        val left = runner.job(a.workspaceId, "Read $name (A)") { fileOf(a, path, "side-a") }
        val right = runner.job(b.workspaceId, "Read $name (B)") { fileOf(b, path, "side-b") }
        return if (left == null || right == null) {
            EntryComparison.NoContent(path, "Not a regular file on ${if (left == null) "A" else "B"}.")
        } else {
            runner.job(a.workspaceId, "Compare $name") { entryDiff(path, left, right) }
        }
    }

    private suspend fun WorkbenchRunner.Job.entryDiff(path: String, left: String, right: String): EntryComparison {
        val (l, r) = sh(PROBE_SCRIPT, left, right).lines().filter(String::isNotBlank).map { it.split('\t') }
        val zip = l[1] == ZIP_MAGIC && r[1] == ZIP_MAGIC
        val text = l[2] == "0" && r[2] == "0" && l[0].toLong() <= TEXT_LIMIT && r[0].toLong() <= TEXT_LIMIT
        return when {
            zip && (path.endsWith(".jar") || path.endsWith(".apk")) -> {
                val entries = archiveDiff(zipMembers(this, left), zipMembers(this, right))
                EntryComparison.Code(path, entries, decodedDiff(left, right))
            }

            zip -> EntryComparison.Archive(path, archiveDiff(zipMembers(this, left), zipMembers(this, right)))

            text -> EntryComparison.Text(path, LineDiff.diff(run("cat", left).lines(), run("cat", right).lines()))

            else -> {
                val (ha, hb) = run("sha256sum", "--", left, right).lines()
                    .map { it.substringBefore(' ').removePrefix("\\") }
                EntryComparison.Binary(path, l[0].toLong(), r[0].toLong(), ha, hb)
            }
        }
    }

    /** Two packages (absolute paths) decoded into `workbench/compare/decoded-a|b` and compared: dex files, methods. */
    private suspend fun WorkbenchRunner.Job.decodedDiff(left: String, right: String): CodePackageDiff {
        val pairs = listOf(left to "workbench/compare/decoded-a", right to "workbench/compare/decoded-b")
        val outs = pairs.map { (f, out) ->
            requireFolderInside("$out/x", "workbench")
            val result = tryRun("apktool", "d", "-r", "-f", "--no-debug-info", "-o", abs(out), f)
            if (result.exitCode != 0) throw undecodable(f, result.stderr.ifBlank { result.stdout })
            out
        }
        return codeDiff(this, outs[0], outs[1]).copy(
            dexDifferences = dexChanges(zipMembers(this, left), zipMembers(this, right)),
        )
    }

    private suspend fun codeDiff(job: WorkbenchRunner.Job, a: String, b: String): CodePackageDiff {
        val left = hashes(job, a).filterKeys { it.endsWith(".smali") }
        val right = hashes(job, b).filterKeys { it.endsWith(".smali") }
        // A class keeps its name when a recompile moves it to another dex (smali_classesN) folder.
        val leftByClass = left.keys.associateBy(SmaliCompare::className)
        val rightByClass = right.keys.associateBy(SmaliCompare::className)
        val changed = leftByClass.keys.intersect(rightByClass.keys)
            .filter { left[leftByClass.getValue(it)] != right[rightByClass.getValue(it)] }
        suspend fun texts(dir: String, files: Map<String, String>) = changed.associate { cls ->
            files.getValue(cls) to job.readText("$dir/${files.getValue(cls)}").orEmpty()
        }
        return CodePackageDiff(
            classDifferences = SmaliCompare.classChanges(left.keys, right.keys),
            methodDifferences = SmaliCompare.methodChanges(texts(a, leftByClass), texts(b, rightByClass)),
        )
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
        "cd \"$1\" && find . -type f -exec sha256sum {} + && find . -type l -printf 'link:%l  %p\\n' && " +
            "find . -mindepth 1 -type d -empty -printf 'empty folder  %p\\n'",
        job.abs(dir),
    ).lines().mapNotNull(::checksumLine).toMap()

    private fun requireInWorkspaceTree(path: String) {
        require(
            (path.startsWith("work/") || path.startsWith("${FirmwareExtractionStage.EXTRACTED}/")) &&
                ".." !in path.split('/'),
        ) { "Pick a file of the build (work/…) or of the stock firmware (firmware/extracted/…)." }
    }

    private fun undecodable(file: String, output: String) = RefusalException(
        Refusal(
            cause = "apktool cannot decode this package.",
            item = file.substringAfterLast('/'),
            nextStep = "Check the details: newer dex container versions are not supported by apktool yet.",
            details = output.trim().takeLast(DETAILS),
        ),
    )

    private fun archiveDiff(left: Map<String, String>, right: Map<String, String>) = ArchiveDiff(
        missingInB = (left.keys - right.keys).sorted(),
        extraInB = (right.keys - left.keys).sorted(),
        modified = left.keys.intersect(right.keys).filter { left[it] != right[it] }.sorted(),
    )

    /** The dex files ([zipMembers] of two packages) that differ: `changed classes2.dex`, `added classes4.dex`. */
    private fun dexChanges(left: Map<String, String>, right: Map<String, String>): List<String> {
        val d = archiveDiff(left.filterKeys(DEX::matches), right.filterKeys(DEX::matches))
        return d.modified.map { "changed $it" } + d.extraInB.map { "added $it" } + d.missingInB.map { "removed $it" }
    }

    private fun missing(path: String) = RefusalException(
        Refusal("The file does not exist.", path, "Pick another file."),
    )

    internal companion object {
        private const val DETAILS = 4_000
        private const val HASH_BATCH = 2_000
        private val ESCAPE = Regex("""\\(.)""")
        private val WHITESPACE = Regex("""\s+""")
        private const val ZIP_COLUMNS = 8
        private val DEX = Regex("""classes\d*\.dex""")

        /** Larger files are compared as bytes, not as lines. */
        private const val TEXT_LIMIT = 1L shl 20
        private const val ZIP_MAGIC = "504b0304"

        /** `sh -c PROBE_SCRIPT sh <file> <file>`: per file `size \t first 4 bytes (hex) \t NULs in the first 8000`. */
        private const val PROBE_SCRIPT = "for f; do printf '%s\\t%s\\t%s\\n' \"$(stat -c %s \"\$f\")\" " +
            "\"$(xxd -p -l 4 \"\$f\")\" \"$(head -c 8000 \"\$f\" | tr -dc '\\000' | wc -c)\"; done"

        /** A `sha256sum` line as path -> hash; a leading `\` says the name is escaped (a backslash, a line break). */
        internal fun checksumLine(line: String): Pair<String, String>? {
            val (hash, name) = line.split("  ", limit = 2).takeIf { it.size == 2 } ?: return null
            val path = if (line.startsWith("\\")) {
                ESCAPE.replace(name) { m ->
                    when (val c = m.groupValues[1]) {
                        "n" -> "\n"
                        "r" -> "\r"
                        else -> c
                    }
                }
            } else {
                name
            }
            return path.removePrefix("./") to hash.removePrefix("\\")
        }
    }
}
