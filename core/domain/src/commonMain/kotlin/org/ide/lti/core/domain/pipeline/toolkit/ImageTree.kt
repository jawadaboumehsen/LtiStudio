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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A file or directory record inside an extracted partition image.
 */
@Serializable
public data class ImageTreeEntry(
    val path: String,
    val size: Long,
    val mode: Int,
    val uid: Int,
    val gid: Int,
    val label: String,
) {
    init {
        require(path.isNotBlank()) { "Entry path must not be blank" }
        require(size >= 0L) { "Entry size must be non-negative" }
        require(uid >= 0) { "UID must be non-negative" }
        require(gid >= 0) { "GID must be non-negative" }
    }
}

/**
 * File tree representation of an EXT4, F2FS, or EROFS image (contracts: `step-results.md`, US2).
 *
 * Key: `image.tree.<name>`
 */
@Serializable
public data class ImageTree(val entries: List<ImageTreeEntry> = emptyList()) {
    public fun find(path: String): ImageTreeEntry? = entries.firstOrNull { it.path == path }

    public companion object {
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        public fun encode(tree: ImageTree): String = json.encodeToString(serializer(), tree)
        public fun decode(string: String): ImageTree = json.decodeFromString(serializer(), string)
    }
}

/**
 * Diagnostic record of a single entry changed between expected and actual image trees.
 */
@Serializable
public data class ImageTreeChange(val path: String, val reason: String)

/**
 * Comparison result between an image tree before edit and after rebuild (contracts: `step-results.md`, US2).
 *
 * Key: `image.verify.<name>`
 */
@Serializable
public data class ImageVerify(
    val ok: Boolean,
    val missing: List<String> = emptyList(),
    val extra: List<String> = emptyList(),
    val changed: List<ImageTreeChange> = emptyList(),
) {
    public companion object {
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        public fun encode(verify: ImageVerify): String = json.encodeToString(serializer(), verify)
        public fun decode(string: String): ImageVerify = json.decodeFromString(serializer(), string)

        /**
         * What a built image's sidecars (read back from the image) get wrong against the ones the build asked
         * for: a path with no expected entry, another owner or mode, or another SELinux label (FR-009). Expected
         * entries for files the tree no longer has are not reported; the image only holds the tree's files.
         */
        public fun sidecarDifferences(
            expectedFsConfig: String,
            expectedFileContexts: String,
            builtFsConfig: String,
            builtFileContexts: String,
        ): List<String> {
            val expectedOwners = owners(expectedFsConfig)
            val expectedLabels = labels(expectedFileContexts)
            val owners = owners(builtFsConfig).mapNotNull { (path, owner) ->
                when (val want = expectedOwners[path]) {
                    null -> "$path has no fs_config entry"
                    owner -> null
                    else -> "$path is $owner, expected $want"
                }
            }
            val contexts = labels(builtFileContexts).mapNotNull { (path, label) ->
                when (val want = expectedLabels[path]) {
                    null -> "$path has no file_contexts entry"
                    label -> null
                    else -> "$path is labelled $label, expected $want"
                }
            }
            return owners + contexts
        }

        /** fs_config lines as path -> "uid gid mode" (mode without leading zeros). */
        private fun owners(text: String): Map<String, String> = text.lineSequence()
            .map { it.trim().split(WHITESPACE) }
            .filter { it.size >= OWNER_FIELDS }
            .associate { t -> t[0] to "${t[1]} ${t[2]} ${t[3].trimStart('0').ifEmpty { "0" }}" }

        /** file_contexts lines as path regex -> label. */
        private fun labels(text: String): Map<String, String> = text.lineSequence()
            .map { it.trim().split(WHITESPACE) }
            .filter { it.size >= 2 && !it[0].startsWith("#") }
            .associate { t -> t[0] to t.last() }

        private val WHITESPACE = Regex("""\s+""")
        private const val OWNER_FIELDS = 4

        public fun compare(expected: ImageTree, actual: ImageTree): ImageVerify {
            val expectedMap = expected.entries.associateBy { it.path }
            val actualMap = actual.entries.associateBy { it.path }

            val missing = expectedMap.keys.filter { it !in actualMap.keys }.sorted()
            val extra = actualMap.keys.filter { it !in expectedMap.keys }.sorted()
            val changed = mutableListOf<ImageTreeChange>()

            for ((path, exp) in expectedMap) {
                val act = actualMap[path] ?: continue
                val diffs = mutableListOf<String>()
                if (exp.size != act.size) diffs += "size(${exp.size} vs ${act.size})"
                if (exp.mode != act.mode) diffs += "mode(${exp.mode} vs ${act.mode})"
                if (exp.uid != act.uid) diffs += "uid(${exp.uid} vs ${act.uid})"
                if (exp.gid != act.gid) diffs += "gid(${exp.gid} vs ${act.gid})"
                if (exp.label != act.label) diffs += "label(${exp.label} vs ${act.label})"

                if (diffs.isNotEmpty()) {
                    changed += ImageTreeChange(path, diffs.joinToString(", "))
                }
            }

            val ok = missing.isEmpty() && extra.isEmpty() && changed.isEmpty()
            return ImageVerify(
                ok = ok,
                missing = missing,
                extra = extra,
                changed = changed.sortedBy { it.path },
            )
        }
    }
}
