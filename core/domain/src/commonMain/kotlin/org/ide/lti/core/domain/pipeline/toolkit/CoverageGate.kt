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
import org.ide.lti.core.domain.pipeline.text.SidecarEditor
import org.ide.lti.core.domain.pipeline.text.SidecarNormalizer
import org.ide.lti.core.model.plugin.FileMetadata

/**
 * Result of checking permission (fs_config) and SELinux label (file_contexts) coverage.
 *
 * Key: `build.coverage`
 */
@Serializable
public data class CoverageResult(
    val uncoveredFsConfig: List<String> = emptyList(),
    val uncoveredContexts: List<String> = emptyList(),
) {
    val isClean: Boolean get() = uncoveredFsConfig.isEmpty() && uncoveredContexts.isEmpty()

    public companion object {
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        public fun encode(result: CoverageResult): String = json.encodeToString(serializer(), result)
        public fun decode(string: String): CoverageResult = json.decodeFromString(serializer(), string)
    }
}

/**
 * Host request for checking coverage across dynamic partitions.
 */
public data class CoverageCheckRequest(val partitions: List<String>) : org.ide.lti.core.domain.pipeline.HostRequest

/**
 * Gate verifying that every file to be packaged has an entry in fs_config and file_contexts (FR-015).
 */
public object CoverageGate {
    /** How a missing partition root is reported. */
    public const val ROOT: String = "/"

    /**
     * Checks if all paths in [paths] (relative to partition root or work tree) are covered in [fsConfigText] and [fileContextsText].
     */
    public fun check(
        partition: String,
        paths: Collection<String>,
        fsConfigText: String,
        fileContextsText: String,
    ): CoverageResult {
        // Keys as written: system's root line has an empty path (" 0 0 755").
        val fsConfigKnown = fsConfigText.lineSequence()
            .filter { it.isNotBlank() }
            .map { it.substringBefore(' ').trim() }
            .toSet()

        val contextsKnown = fileContextsText.lineSequence()
            .map { it.substringBefore(' ').trim() }
            .filter { it.isNotBlank() }
            .toSet()

        val uncoveredFsConfig = mutableListOf<String>()
        val uncoveredContexts = mutableListOf<String>()

        // The partition root is never in a tree listing, yet mkfs needs its lines: it is always checked.
        for (rawPath in paths + ROOT) {
            val clean = rawPath.trim('/').ifEmpty { ROOT }

            // Only the forms SidecarNormalizer writes count; a looser match would let a missing entry pass.
            if (SidecarNormalizer.fsConfigPath(partition, clean) !in fsConfigKnown) {
                uncoveredFsConfig.add(clean)
            }

            val ctxPath = SidecarNormalizer.fileContextPath(partition, clean)
            if (SidecarNormalizer.escapeRegex(ctxPath) !in contextsKnown && ctxPath !in contextsKnown) {
                uncoveredContexts.add(clean)
            }
        }

        return CoverageResult(
            uncoveredFsConfig = uncoveredFsConfig.distinct().sorted(),
            uncoveredContexts = uncoveredContexts.distinct().sorted(),
        )
    }
}

/**
 * The user's own owner, mode and label for files without sidecar lines: the first rule
 * whose [pattern] matches the whole device path wins; files no rule matches get their folder's lines.
 */
public data class CoverageRule(val pattern: Regex, val uid: Int, val gid: Int, val mode: String, val label: String) {
    public companion object {
        private const val FIELDS = 5
        private val MODE = Regex("[0-7]{3,4}")

        /** [text]'s rules; a line that is not `<regex> <uid> <gid> <mode> <label>` (or a bad regex) is an error. */
        public fun parse(text: String): Pair<List<CoverageRule>, List<String>> {
            val rules = mutableListOf<CoverageRule>()
            val errors = mutableListOf<String>()
            text.lines().forEachIndexed { index, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
                val f = line.split(Regex("\\s+"))
                val uid = f.getOrNull(1)?.toIntOrNull()?.takeIf { it >= 0 }
                val gid = f.getOrNull(2)?.toIntOrNull()?.takeIf { it >= 0 }
                val regex = runCatching { Regex(f[0]) }.getOrNull()
                val wellFormed = f.size == FIELDS && MODE.matches(f[3])
                if (!wellFormed || uid == null || gid == null || regex == null) {
                    errors += "line ${index + 1}: '$line' is not <regex> <uid> <gid> <mode> <label>"
                } else {
                    rules += CoverageRule(regex, uid, gid, f[3], f[4])
                }
            }
            return rules to errors
        }

        public fun match(rules: List<CoverageRule>, devicePath: String): CoverageRule? =
            rules.firstOrNull { it.pattern.matches(devicePath) }
    }
}

/**
 * Proposes and applies missing coverage entries through [SidecarEditor] (FR-016).
 */
public object CoverageFix {
    /**
     * Finds the nearest covered parent label in [fileContextsText] for [path].
     */
    public fun findNearestLabel(fileContextsText: String, partition: String, path: String): String {
        val lines = fileContextsText.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val key = line.substringBefore(' ').trim()
                val label = line.substringAfter(' ').trim()
                key to label
            }
            .toList()

        val parts = path.trim('/').split('/').filter { it.isNotEmpty() }
        val ancestor = (parts.size downTo 0).asSequence().mapNotNull { i ->
            val subpath = parts.take(i).joinToString("/")
            val ctxPath = SidecarNormalizer.fileContextPath(partition, subpath)
            val escaped = SidecarNormalizer.escapeRegex(ctxPath)
            // The folder itself, never a name that merely contains it (bin-extra is not bin's ancestor).
            lines.firstOrNull { (k, _) -> k == escaped || k == ctxPath }?.second
        }.firstOrNull()
        return ancestor ?: "u:object_r:system_file:s0"
    }

    public fun fixFsConfig(
        fsConfigText: String,
        partition: String,
        uncoveredPaths: Collection<String>,
        isDirectory: (String) -> Boolean = { false },
        rules: List<CoverageRule> = emptyList(),
    ): String {
        var current = fsConfigText
        // A root's owner and mode cannot be taken from a folder above it: a missing root stays refused.
        for (path in uncoveredPaths - CoverageGate.ROOT) {
            val rule = CoverageRule.match(rules, SidecarNormalizer.fileContextPath(partition, path))
            current = SidecarEditor.withFsConfig(
                text = current,
                partition = partition,
                path = path,
                isDirectory = isDirectory(path),
                metadata = rule?.let { FileMetadata(it.uid, it.gid, it.mode) },
            )
        }
        return current
    }

    public fun fixFileContexts(
        fileContextsText: String,
        partition: String,
        uncoveredPaths: Collection<String>,
        defaultLabel: String? = null,
        rules: List<CoverageRule> = emptyList(),
    ): String {
        var current = fileContextsText
        for (path in uncoveredPaths - CoverageGate.ROOT) {
            val rule = CoverageRule.match(rules, SidecarNormalizer.fileContextPath(partition, path))
            val label = rule?.label ?: defaultLabel ?: findNearestLabel(current, partition, path)
            current = SidecarEditor.withFileContext(
                text = current,
                partition = partition,
                path = path,
                label = label,
            )
        }
        return current
    }
}
