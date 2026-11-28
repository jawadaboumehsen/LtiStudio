/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.toolkit

import org.ide.lti.core.domain.pipeline.text.SidecarNormalizer

/** What happened to one path of a partition between the stock firmware and the build. */
enum class TreeChangeKind { ADDED, REMOVED, CONTENT, META, TYPE }

/** Structural aspect changed on a path, for deep ROM audit filtering. */
enum class StructuralAspect {
    HASH_CONTENT,
    SYMLINK,
    UID_GID_MODE,
    SELINUX_CONTEXT,
    CAPABILITIES,
    APK_PACKAGE,
}

/** Severity of a structural security audit finding inside a compared partition. */
enum class StructuralRiskSeverity { CRITICAL, WARNING, INFO }

/** A concrete security-relevant structural drift between stock and built partitions. */
data class StructuralSecurityFinding(val path: String, val severity: StructuralRiskSeverity, val summary: String)

/** Breakdown of content hashes, symlinks, UID/GID/permissions, SELinux labels, APKs, and risk findings. */
data class StructuralAuditSummary(
    val hashDiffCount: Int,
    val symlinkDiffCount: Int,
    val ownerOrModeDiffCount: Int,
    val selinuxDiffCount: Int,
    val capabilityDiffCount: Int,
    val apkDiffCount: Int,
    val securityFindings: List<StructuralSecurityFinding>,
)

/** One changed path (relative to the partition's tree) and, for metadata, what changed (`mode 644 -> 755`). */
data class TreeChange(val path: String, val kind: TreeChangeKind, val details: List<String> = emptyList()) {
    fun aspects(): Set<StructuralAspect> = buildSet {
        if (kind == TreeChangeKind.CONTENT) add(StructuralAspect.HASH_CONTENT)
        if (path.endsWith(".apk", ignoreCase = true) || path.endsWith(".apex", ignoreCase = true)) {
            add(StructuralAspect.APK_PACKAGE)
        }
        details.forEach { d ->
            when {
                d.startsWith("link ") -> add(StructuralAspect.SYMLINK)
                d.startsWith("owner ") || d.startsWith("mode ") -> add(StructuralAspect.UID_GID_MODE)
                d.startsWith("label ") -> add(StructuralAspect.SELINUX_CONTEXT)
                d.startsWith("capabilities ") -> add(StructuralAspect.CAPABILITIES)
            }
        }
    }
}

/** Two trees of a partition compared (A, the older or stock side, against B). */
data class TreeDiff(val partition: String, val changes: List<TreeChange>, val unchanged: Int) {
    fun count(kind: TreeChangeKind): Int = changes.count { it.kind == kind }

    fun structuralAudit(): StructuralAuditSummary {
        var hashes = 0
        var links = 0
        var uidMode = 0
        var selinux = 0
        var caps = 0
        var apks = 0
        val findings = mutableListOf<StructuralSecurityFinding>()

        changes.forEach { c ->
            val aspects = c.aspects()
            if (StructuralAspect.HASH_CONTENT in aspects) hashes++
            if (StructuralAspect.SYMLINK in aspects) links++
            if (StructuralAspect.UID_GID_MODE in aspects) uidMode++
            if (StructuralAspect.SELINUX_CONTEXT in aspects) selinux++
            if (StructuralAspect.CAPABILITIES in aspects) caps++
            if (StructuralAspect.APK_PACKAGE in aspects) apks++

            c.details.forEach { d ->
                classifyRisk(c.path, d)?.let(findings::add)
            }
            if (c.kind == TreeChangeKind.ADDED && c.path.endsWith("/su")) {
                findings += StructuralSecurityFinding(
                    c.path,
                    StructuralRiskSeverity.WARNING,
                    "Added su binary to partition image",
                )
            }
        }

        return StructuralAuditSummary(
            hashDiffCount = hashes,
            symlinkDiffCount = links,
            ownerOrModeDiffCount = uidMode,
            selinuxDiffCount = selinux,
            capabilityDiffCount = caps,
            apkDiffCount = apks,
            securityFindings = findings.sortedBy { it.severity.ordinal },
        )
    }

    private fun classifyRisk(path: String, detail: String): StructuralSecurityFinding? = when {
        detail.startsWith("label ") && detail.endsWith("-> none") ->
            StructuralSecurityFinding(path, StructuralRiskSeverity.CRITICAL, "SELinux label removed: $detail")

        detail.startsWith("label ") && (detail.contains("unlabeled") || detail.contains("su_exec")) ->
            StructuralSecurityFinding(path, StructuralRiskSeverity.CRITICAL, "SELinux label risk: $detail")

        detail.startsWith("label ") ->
            StructuralSecurityFinding(path, StructuralRiskSeverity.INFO, "SELinux file_contexts change: $detail")

        detail.startsWith("mode ") && isEscalatedMode(detail.substringAfter("-> ").trim()) ->
            StructuralSecurityFinding(
                path,
                StructuralRiskSeverity.WARNING,
                "Privileged/setuid/world-writable mode: $detail",
            )

        detail.startsWith("capabilities ") && !detail.endsWith("-> 0x0") && !detail.endsWith("-> none") ->
            StructuralSecurityFinding(path, StructuralRiskSeverity.WARNING, "Linux file capabilities changed: $detail")

        else -> null
    }

    private fun isEscalatedMode(mode: String): Boolean {
        if (mode.length >= 4 && (mode.startsWith("4") || mode.startsWith("2") || mode.startsWith("6"))) return true
        return mode.lastOrNull() in setOf('2', '3', '6', '7')
    }
}

/**
 * Compares two trees of a partition (a folder of a workspace or an image read straight from its file): files added,
 * removed, changed in content, or in owner, mode, capabilities, SELinux label or link target (from each side's own
 * sidecars), or changed kind. A different size is a change of content; files of the same size are hashed, all of
 * them, or with [hashCandidates]' `trustTimes` only those whose modification time differs (rsync's quick check).
 */
object TreeCompare {
    /** `sh -c LISTING_SCRIPT sh <tree>`: `<type>\t<size>\t<mtime>\t<path>\t<link target>` per entry. */
    const val LISTING_SCRIPT: String = "find \"\$1\" -mindepth 1 -printf '%y\\t%s\\t%T@\\t%P\\t%l\\n'"

    /** `sh -c HASH_SCRIPT sh <tree> <path>...`: sha256sum's lines for those paths of the tree. */
    const val HASH_SCRIPT: String = "cd \"\$1\" && shift && sha256sum -- \"\$@\""

    private const val FIELDS = 5
    private const val CAPS = "capabilities="

    data class Entry(val type: Char, val size: Long, val mtime: String, val target: String)

    fun parseListing(output: String): Map<String, Entry> = output.lines().mapNotNull { line ->
        val f = line.split('\t', limit = FIELDS).takeIf { it.size == FIELDS && it[0].isNotEmpty() }
            ?: return@mapNotNull null
        f[3] to Entry(f[0][0], f[1].toLongOrNull() ?: 0L, f[2], f[4])
    }.toMap()

    /**
     * Regular files on both sides of the same size: they need hashing. With [trustTimes], only those whose
     * modification time differs (the same size and time count as the same content).
     */
    fun hashCandidates(
        stock: Map<String, Entry>,
        build: Map<String, Entry>,
        trustTimes: Boolean = true,
    ): List<String> = build.filter { (path, b) ->
        val s = stock[path]
        s != null && s.type == 'f' && b.type == 'f' && s.size == b.size && !(trustTimes && s.mtime == b.mtime)
    }.keys.sorted()

    /** sha256sum output as path -> hash. */
    fun parseHashes(output: String): Map<String, String> = output.lines().mapNotNull { line ->
        line.split("  ", limit = 2).takeIf { it.size == 2 }?.let { it[1].removePrefix("./") to it[0] }
    }.toMap()

    /** fs_config text as key -> `uid:gid`, mode, capabilities (the key may be "" for system's root). */
    fun parseFsConfig(text: String): Map<String, List<String>> = text.lines().mapNotNull { line ->
        val parts = line.split(' ')
        val metaCount = if (parts.lastOrNull()?.startsWith(CAPS) == true) 4 else 3
        if (parts.size <= metaCount) return@mapNotNull null
        val meta = parts.takeLast(metaCount)
        parts.dropLast(metaCount).joinToString(" ") to
            listOf("${meta[0]}:${meta[1]}", meta[2], meta.getOrNull(3)?.removePrefix(CAPS) ?: "0x0")
    }.toMap()

    /** file_contexts text as (escaped) path -> label. */
    fun parseFileContexts(text: String): Map<String, String> = text.lines().mapNotNull { line ->
        line.trim().takeIf { ' ' in it }?.let { it.substringBeforeLast(' ') to it.substringAfterLast(' ') }
    }.toMap()

    /** The sidecars of one side, read for [partition]. */
    class Sidecars(val partition: String, fsConfig: String, fileContexts: String) {
        private val fs = parseFsConfig(fsConfig)
        private val labels = parseFileContexts(fileContexts)

        internal fun owner(path: String) = fs[SidecarNormalizer.fsConfigPath(partition, path)]?.get(0)

        internal fun mode(path: String) = fs[SidecarNormalizer.fsConfigPath(partition, path)]?.get(1)

        internal fun caps(path: String) = fs[SidecarNormalizer.fsConfigPath(partition, path)]?.get(2)

        internal fun label(path: String) =
            labels[SidecarNormalizer.escapeRegex(SidecarNormalizer.fileContextPath(partition, path))]
    }

    fun compare(
        partition: String,
        stock: Map<String, Entry>,
        build: Map<String, Entry>,
        stockHashes: Map<String, String>,
        buildHashes: Map<String, String>,
        stockSidecars: Sidecars,
        buildSidecars: Sidecars,
    ): TreeDiff {
        val changes = mutableListOf<TreeChange>()
        var unchanged = 0
        (stock.keys + build.keys).sorted().forEach { path ->
            val s = stock[path]
            val b = build[path]
            val change = when {
                s == null -> TreeChange(path, TreeChangeKind.ADDED)

                b == null -> TreeChange(path, TreeChangeKind.REMOVED)

                s.type != b.type -> TreeChange(path, TreeChangeKind.TYPE, listOf("${kind(s.type)} -> ${kind(b.type)}"))

                else -> {
                    val meta = metaDetails(path, s, b, stockSidecars, buildSidecars)
                    // A file not hashed (same size and time, trusted) has no hash on either side: the same.
                    val content = s.type == 'f' && (s.size != b.size || stockHashes[path] != buildHashes[path])
                    when {
                        content -> TreeChange(
                            path,
                            TreeChangeKind.CONTENT,
                            listOf("${s.size} -> ${b.size} bytes") + meta,
                        )

                        meta.isNotEmpty() -> TreeChange(path, TreeChangeKind.META, meta)

                        else -> null
                    }
                }
            }
            if (change == null) unchanged++ else changes += change
        }
        return TreeDiff(partition, changes, unchanged)
    }

    private fun metaDetails(path: String, s: Entry, b: Entry, a: Sidecars, z: Sidecars): List<String> = listOfNotNull(
        differ("owner", a.owner(path), z.owner(path)),
        differ("mode", a.mode(path), z.mode(path)),
        differ("capabilities", a.caps(path), z.caps(path)),
        differ("label", a.label(path), z.label(path)),
        differ("link", s.target.ifEmpty { null }, b.target.ifEmpty { null }),
    )

    private fun differ(what: String, before: String?, after: String?): String? =
        "$what ${before ?: "none"} -> ${after ?: "none"}".takeIf { before != after }

    private fun kind(type: Char): String = when (type) {
        'f' -> "file"
        'd' -> "folder"
        'l' -> "link"
        else -> "special"
    }
}

/** A line diff (longest common subsequence): `- old` and `+ new` lines, or null past [MAX_LINES] lines a side. */
object LineDiff {
    const val MAX_LINES: Int = 3000
    private const val CONTEXT = 3

    fun diff(old: List<String>, new: List<String>): List<String>? = ops(old, new)?.filterNot { it.startsWith("  ") }

    /**
     * What an edit changed, readably: the common start and end are cut first (an edited smali class can be 20,000
     * lines; the change is a few), then the rest is diffed with unchanged lines kept as `  line`; a run of more than
     * twice [context] unchanged lines folds to `  …`, and [context] unchanged lines stand on each side. Empty when
     * nothing changed; null when the changed part is too long to diff.
     */
    fun hunk(old: List<String>, new: List<String>, context: Int = CONTEXT): List<String>? {
        var start = 0
        while (start < old.size && start < new.size && old[start] == new[start]) start++
        var end = 0
        while (end < old.size - start && end < new.size - start && old[old.size - 1 - end] == new[new.size - 1 - end]) {
            end++
        }
        val middle = ops(old.subList(start, old.size - end), new.subList(start, new.size - end))
        return when {
            middle == null -> null

            middle.all { it.startsWith("  ") } -> emptyList()

            else -> listOf("@@ line ${start + 1} @@") +
                old.subList(maxOf(0, start - context), start).map { "  $it" } +
                fold(middle, context) +
                old.subList(old.size - end, minOf(old.size, old.size - end + context)).map { "  $it" }
        }
    }

    /** [lines] with every run of more than twice [context] unchanged lines cut to its ends and `  …`. */
    private fun fold(lines: List<String>, context: Int): List<String> {
        val out = mutableListOf<String>()
        var run = mutableListOf<String>()
        fun flush() {
            out += if (run.size > 2 * context) run.take(context) + "  …" + run.takeLast(context) else run
            run = mutableListOf()
        }
        for (line in lines) {
            if (line.startsWith("  ")) {
                run += line
            } else {
                flush()
                out += line
            }
        }
        flush()
        return out
    }

    /** `lcs[i][j]`: length of longest common subsequence of old (from i) and new (from j). */
    private fun lcs(old: List<String>, new: List<String>): Array<IntArray> {
        val lcs = Array(old.size + 1) { IntArray(new.size + 1) }
        for (i in old.indices.reversed()) {
            for (j in new.indices.reversed()) {
                lcs[i][j] = if (old[i] == new[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
        return lcs
    }

    /** Every line: `  ` unchanged, `- ` removed, `+ ` added (the removed one first, as diffs show it). */
    private fun ops(old: List<String>, new: List<String>): List<String>? {
        if (old.size > MAX_LINES || new.size > MAX_LINES) return null
        val lcs = lcs(old, new)
        val out = mutableListOf<String>()
        var i = 0
        var j = 0
        while (i < old.size || j < new.size) {
            when {
                i < old.size && j < new.size && old[i] == new[j] -> out += "  ${old[i++].also { j++ }}"
                i < old.size && (j == new.size || lcs[i + 1][j] >= lcs[i][j + 1]) -> out += "- ${old[i++]}"
                else -> out += "+ ${new[j++]}"
            }
        }
        return out
    }
}
