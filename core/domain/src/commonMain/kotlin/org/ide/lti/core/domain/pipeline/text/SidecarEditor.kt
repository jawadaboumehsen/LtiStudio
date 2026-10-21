/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.text

import org.ide.lti.core.model.plugin.FileMetadata

/**
 * Edits `work/configs/fs_config-<p>` and `file_context-<p>` for removed files, using the path rules of
 * [SidecarNormalizer.fsConfigPath] / [SidecarNormalizer.fileContextPath]. One edit per file handles every
 * path at once — the single-pass rule from LtiRom's debloat.sh, whose per-package `sed -i` raced and lost
 * entries.
 */
public object SidecarEditor {
    /** fs_config [text] without [paths] (inside `work/<partition>/`) and anything below them. */
    public fun withoutFsConfig(text: String, partition: String, paths: Collection<String>): String =
        filterLines(text, paths.map { SidecarNormalizer.fsConfigPath(partition, it) })

    /** file_context [text] without [paths] (inside `work/<partition>/`) and anything below them. */
    public fun withoutFileContext(text: String, partition: String, paths: Collection<String>): String = filterLines(
        text,
        paths.map { SidecarNormalizer.escapeRegex(SidecarNormalizer.fileContextPath(partition, it)) },
    )

    /**
     * fs_config [text] with an entry for [path] and each missing parent folder: root-owned, 0755 for folders,
     * 0644 for a file (as LtiRom's ADD_TO_WORK_DIR defaults). Existing entries are kept, except that [path]'s
     * own entry is replaced when [metadata] says what it must be.
     */
    public fun withFsConfig(
        text: String,
        partition: String,
        path: String,
        isDirectory: Boolean,
        metadata: FileMetadata? = null,
    ): String {
        val leafKey = SidecarNormalizer.fsConfigPath(partition, path)
        val base = if (metadata == null) text else filterExact(text, leafKey)
        val known = base.lineSequence().map { it.substringBefore(' ') }.toSet()
        val added = chain(path).mapNotNull { (rel, isLeaf) ->
            val key = SidecarNormalizer.fsConfigPath(partition, rel)
            val owner = when {
                isLeaf && metadata != null -> "${metadata.uid} ${metadata.gid} ${metadata.mode}"
                isLeaf && !isDirectory -> "0 0 644"
                else -> "0 0 755"
            }
            "$key $owner capabilities=0x0".takeIf { key !in known }
        }
        return append(base, added)
    }

    /**
     * file_context [text] with [path] and each missing parent folder, labelled like their closest listed parent;
     * [label], when given, is [path]'s own label (replacing an existing one).
     */
    public fun withFileContext(text: String, partition: String, path: String, label: String? = null): String {
        val leafKey = SidecarNormalizer.escapeRegex(SidecarNormalizer.fileContextPath(partition, path))
        val base = if (label == null) text else filterExact(text, leafKey)
        val labels = base.lineSequence().filter { it.isNotBlank() }
            .associate { it.substringBefore(' ') to it.substringAfter(' ').trim() }
        var inherited = labels[SidecarNormalizer.escapeRegex(SidecarNormalizer.fileContextPath(partition, ""))]
        val added = chain(path).mapNotNull { (rel, isLeaf) ->
            val key = SidecarNormalizer.escapeRegex(SidecarNormalizer.fileContextPath(partition, rel))
            val existing = labels[key]
            when {
                existing != null -> {
                    inherited = existing
                    null
                }
                isLeaf && label != null -> "$key $label"
                else -> inherited?.let { "$key $it" }
            }
        }
        return append(base, added)
    }

    private fun filterExact(text: String, key: String): String {
        val kept = text.lineSequence().filter { it.substringBefore(' ') != key }.toList().dropLastWhile { it.isBlank() }
        return if (kept.isEmpty()) "" else kept.joinToString("\n", postfix = "\n")
    }

    /** `a`, `a/b`, `a/b/c` for `a/b/c`, the last one flagged as the leaf. */
    private fun chain(path: String): List<Pair<String, Boolean>> {
        val parts = path.trim('/').split('/').filter { it.isNotEmpty() }
        return parts.indices.map { i -> parts.take(i + 1).joinToString("/") to (i == parts.lastIndex) }
    }

    private fun append(text: String, lines: List<String>): String {
        if (lines.isEmpty()) return text
        val base = text.trimEnd('\n')
        return (if (base.isEmpty()) lines else listOf(base) + lines).joinToString("\n", postfix = "\n")
    }

    private fun filterLines(text: String, prefixes: List<String>): String {
        if (prefixes.isEmpty()) return text
        val kept = text.lineSequence().filter { line ->
            val path = line.substringBefore(' ')
            line.isBlank() || prefixes.none { path == it || path.startsWith("$it/") }
        }.toList().dropLastWhile { it.isBlank() }
        return if (kept.isEmpty()) "" else kept.joinToString("\n", postfix = "\n")
    }
}
