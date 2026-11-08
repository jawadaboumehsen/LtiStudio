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
public enum class TreeChangeKind { ADDED, REMOVED, CONTENT, META, TYPE }

/** One changed path (relative to the partition's tree) and, for metadata, what changed (`mode 644 -> 755`). */
public data class TreeChange(val path: String, val kind: TreeChangeKind, val details: List<String> = emptyList())

/** Two trees of a partition compared (A, the older or stock side, against B). */
public data class TreeDiff(val partition: String, val changes: List<TreeChange>, val unchanged: Int) {
    public fun count(kind: TreeChangeKind): Int = changes.count { it.kind == kind }
}

/**
 * Compares two trees of a partition (a folder of a workspace or an image read straight from its file): files added,
 * removed, changed in content, or in owner, mode, capabilities, SELinux label or link target (from each side's own
 * sidecars), or changed kind. A different size is a change of content; files of the same size are hashed, all of
 * them, or with [hashCandidates]' `trustTimes` only those whose modification time differs (rsync's quick check).
 */
public object TreeCompare {
    /** `sh -c LISTING_SCRIPT sh <tree>`: `<type>\t<size>\t<mtime>\t<path>\t<link target>` per entry. */
    public const val LISTING_SCRIPT: String = "find \"\$1\" -mindepth 1 -printf '%y\\t%s\\t%T@\\t%P\\t%l\\n'"

    /** `sh -c HASH_SCRIPT sh <tree> <path>...`: sha256sum's lines for those paths of the tree. */
    public const val HASH_SCRIPT: String = "cd \"\$1\" && shift && sha256sum -- \"\$@\""

    private const val FIELDS = 5
    private const val CAPS = "capabilities="

    public data class Entry(val type: Char, val size: Long, val mtime: String, val target: String)

    public fun parseListing(output: String): Map<String, Entry> = output.lines().mapNotNull { line ->
        val f = line.split('\t', limit = FIELDS).takeIf { it.size == FIELDS && it[0].isNotEmpty() }
            ?: return@mapNotNull null
        f[3] to Entry(f[0][0], f[1].toLongOrNull() ?: 0L, f[2], f[4])
    }.toMap()

    /**
     * Regular files on both sides of the same size: they need hashing. With [trustTimes], only those whose
     * modification time differs (the same size and time count as the same content).
     */
    public fun hashCandidates(
        stock: Map<String, Entry>,
        build: Map<String, Entry>,
        trustTimes: Boolean = true,
    ): List<String> = build.filter { (path, b) ->
        val s = stock[path]
        s != null && s.type == 'f' && b.type == 'f' && s.size == b.size && !(trustTimes && s.mtime == b.mtime)
    }.keys.sorted()

    /** sha256sum output as path -> hash. */
    public fun parseHashes(output: String): Map<String, String> = output.lines().mapNotNull { line ->
        line.split("  ", limit = 2).takeIf { it.size == 2 }?.let { it[1].removePrefix("./") to it[0] }
    }.toMap()

    /** fs_config text as key -> `uid:gid`, mode, capabilities (the key may be "" for system's root). */
    public fun parseFsConfig(text: String): Map<String, List<String>> = text.lines().mapNotNull { line ->
        val parts = line.split(' ')
        val metaCount = if (parts.lastOrNull()?.startsWith(CAPS) == true) 4 else 3
        if (parts.size <= metaCount) return@mapNotNull null
        val meta = parts.takeLast(metaCount)
        parts.dropLast(metaCount).joinToString(" ") to
            listOf("${meta[0]}:${meta[1]}", meta[2], meta.getOrNull(3)?.removePrefix(CAPS) ?: "0x0")
    }.toMap()

    /** file_contexts text as (escaped) path -> label. */
    public fun parseFileContexts(text: String): Map<String, String> = text.lines().mapNotNull { line ->
        line.trim().takeIf { ' ' in it }?.let { it.substringBeforeLast(' ') to it.substringAfterLast(' ') }
    }.toMap()

    /** The sidecars of one side, read for [partition]. */
    public class Sidecars(public val partition: String, fsConfig: String, fileContexts: String) {
        private val fs = parseFsConfig(fsConfig)
        private val labels = parseFileContexts(fileContexts)

        internal fun owner(path: String) = fs[SidecarNormalizer.fsConfigPath(partition, path)]?.get(0)

        internal fun mode(path: String) = fs[SidecarNormalizer.fsConfigPath(partition, path)]?.get(1)

        internal fun caps(path: String) = fs[SidecarNormalizer.fsConfigPath(partition, path)]?.get(2)

        internal fun label(path: String) =
            labels[SidecarNormalizer.escapeRegex(SidecarNormalizer.fileContextPath(partition, path))]
    }

    public fun compare(
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
public object LineDiff {
    public const val MAX_LINES: Int = 3000

    public fun diff(old: List<String>, new: List<String>): List<String>? {
        if (old.size > MAX_LINES || new.size > MAX_LINES) return null
        // lcs[i][j]: length of the common subsequence of old[i..] and new[j..].
        val lcs = Array(old.size + 1) { IntArray(new.size + 1) }
        for (i in old.indices.reversed()) {
            for (j in new.indices.reversed()) {
                lcs[i][j] = if (old[i] == new[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
        val out = mutableListOf<String>()
        var i = 0
        var j = 0
        while (i < old.size || j < new.size) {
            when {
                i < old.size && j < new.size && old[i] == new[j] -> {
                    i++
                    j++
                }

                // The removed line first, as diffs show it.
                i < old.size && (j == new.size || lcs[i + 1][j] >= lcs[i][j + 1]) -> out += "- ${old[i++]}"

                else -> out += "+ ${new[j++]}"
            }
        }
        return out
    }
}
