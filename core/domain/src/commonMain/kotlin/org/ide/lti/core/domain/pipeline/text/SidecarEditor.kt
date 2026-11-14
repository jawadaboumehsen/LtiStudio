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

    /** fs_config [text] with the lines of [from] and below it (inside `work/<partition>/`) moved to [to]. */
    public fun withRenamedFsConfig(text: String, partition: String, from: String, to: String): String =
        renameKeys(text, SidecarNormalizer.fsConfigPath(partition, from), SidecarNormalizer.fsConfigPath(partition, to))

    /** file_context [text] with the lines of [from] and below it (inside `work/<partition>/`) moved to [to]. */
    public fun withRenamedFileContext(text: String, partition: String, from: String, to: String): String = renameKeys(
        text,
        SidecarNormalizer.escapeRegex(SidecarNormalizer.fileContextPath(partition, from)),
        SidecarNormalizer.escapeRegex(SidecarNormalizer.fileContextPath(partition, to)),
    )

    private fun renameKeys(text: String, from: String, to: String): String = text.lineSequence().joinToString("\n") {
        val key = keyOf(it)
        when {
            key == from -> to + it.removePrefix(key)
            key.startsWith("$from/") -> to + it.removePrefix(from)
            else -> it
        }
    }

    /** fs_config [text] with [backup]'s lines for [path] (inside `work/<partition>/`) and below it put back. */
    public fun withRestoredFsConfig(text: String, backup: String, partition: String, path: String): String =
        restoreLines(text, backup, SidecarNormalizer.fsConfigPath(partition, path))

    /** file_context [text] with [backup]'s lines for [path] (inside `work/<partition>/`) and below it put back. */
    public fun withRestoredFileContext(text: String, backup: String, partition: String, path: String): String =
        restoreLines(text, backup, SidecarNormalizer.escapeRegex(SidecarNormalizer.fileContextPath(partition, path)))

    /**
     * fs_config [text] with an entry for [path] and each missing parent folder. Existing entries are kept, except
     * that [path]'s own entry is replaced when [metadata] says what it must be. Otherwise owner and mode come from
     * [platform] (the ROM's fs_config tables, then Android's defaults: `bin/` folders `0 2000 751`, their files
     * `0 2000 755`); without it, root-owned `0755` folders and `0644` files.
     */
    public fun withFsConfig(
        text: String,
        partition: String,
        path: String,
        isDirectory: Boolean,
        metadata: FileMetadata? = null,
        platform: PlatformMetadata = PlatformMetadata.NONE,
    ): String {
        val leafKey = SidecarNormalizer.fsConfigPath(partition, path)
        val base = if (metadata == null) text else filterExact(text, leafKey)
        val known = base.lineSequence().map { keyOf(it) }.toSet()
        val added = chain(path).mapNotNull { (rel, isLeaf) ->
            val key = SidecarNormalizer.fsConfigPath(partition, rel)
            val fields = if (isLeaf && metadata != null) {
                "${metadata.uid} ${metadata.gid} ${metadata.mode} capabilities=0x0"
            } else {
                platform.owner(partition, rel, isDirectory = !isLeaf || isDirectory).first.fsConfigFields
            }
            "$key $fields".takeIf { key !in known }
        }
        return append(base, added)
    }

    /**
     * file_context [text] with [path] and each missing parent folder. [label], when given, is [path]'s own label
     * (replacing an existing one); otherwise a new entry gets what [platform]'s rules (the ROM's file_contexts) say,
     * else the label of its closest listed parent, as restorecon would.
     */
    public fun withFileContext(
        text: String,
        partition: String,
        path: String,
        label: String? = null,
        kind: EntryKind = EntryKind.File,
        platform: PlatformMetadata = PlatformMetadata.NONE,
    ): String {
        val leafKey = SidecarNormalizer.escapeRegex(SidecarNormalizer.fileContextPath(partition, path))
        val base = if (label == null) text else filterExact(text, leafKey)
        val labels = base.lineSequence().filter { it.isNotBlank() }
            .associate { keyOf(it) to it.drop(keyOf(it).length).trim() }
        var inherited = labels[SidecarNormalizer.escapeRegex(SidecarNormalizer.fileContextPath(partition, ""))]
        val added = chain(path).mapNotNull { (rel, isLeaf) ->
            val key = SidecarNormalizer.escapeRegex(SidecarNormalizer.fileContextPath(partition, rel))
            val existing = labels[key]
            if (existing != null) {
                inherited = existing
                return@mapNotNull null
            }
            val own = if (isLeaf) {
                label ?: platform.label(partition, rel, kind)
            } else {
                platform.label(partition, rel, EntryKind.Directory)
            }
            val chosen = own ?: inherited
            if (!isLeaf && chosen != null) inherited = chosen
            chosen?.let { "$key $it" }
        }
        return append(base, added)
    }

    private fun filterExact(text: String, key: String): String {
        val kept = text.lineSequence().filter { keyOf(it) != key }.toList().dropLastWhile { it.isBlank() }
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

    private fun restoreLines(text: String, backup: String, prefix: String): String {
        val known = text.lineSequence().map { keyOf(it) }.toSet()
        val lines = backup.lineSequence().filter { line ->
            val path = keyOf(line)
            line.isNotBlank() && (path == prefix || path.startsWith("$prefix/")) && path !in known
        }.toList()
        return append(text, lines)
    }

    private fun filterLines(text: String, prefixes: List<String>): String {
        if (prefixes.isEmpty()) return text
        val kept = text.lineSequence().filter { line ->
            val path = keyOf(line)
            line.isBlank() || prefixes.none { path == it || path.startsWith("$it/") }
        }.toList().dropLastWhile { it.isBlank() }
        return if (kept.isEmpty()) "" else kept.joinToString("\n", postfix = "\n")
    }

    /** A sidecar line's path: up to the first space or tab. */
    private fun keyOf(line: String): String = line.takeWhile { !it.isWhitespace() }
}
