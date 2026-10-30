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
    /**
     * Checks if all paths in [paths] (relative to partition root or work tree) are covered in [fsConfigText] and [fileContextsText].
     */
    public fun check(
        partition: String,
        paths: Collection<String>,
        fsConfigText: String,
        fileContextsText: String,
    ): CoverageResult {
        val fsConfigKnown = fsConfigText.lineSequence()
            .map { it.substringBefore(' ').trim() }
            .filter { it.isNotBlank() }
            .toSet()

        val contextsKnown = fileContextsText.lineSequence()
            .map { it.substringBefore(' ').trim() }
            .filter { it.isNotBlank() }
            .toSet()

        val uncoveredFsConfig = mutableListOf<String>()
        val uncoveredContexts = mutableListOf<String>()

        for (rawPath in paths) {
            val clean = rawPath.trim('/')
            if (clean.isBlank()) continue

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
            lines.firstOrNull { (k, _) ->
                k == escaped || k == ctxPath || (subpath.isNotEmpty() && k.contains(subpath))
            }?.second
        }.firstOrNull()
        return ancestor
            ?: lines.firstOrNull { (k, _) -> k.contains(partition) }?.second
            ?: "u:object_r:system_file:s0"
    }

    public fun fixFsConfig(
        fsConfigText: String,
        partition: String,
        uncoveredPaths: Collection<String>,
        isDirectory: (String) -> Boolean = { false },
    ): String {
        var current = fsConfigText
        for (path in uncoveredPaths) {
            current = SidecarEditor.withFsConfig(
                text = current,
                partition = partition,
                path = path,
                isDirectory = isDirectory(path),
            )
        }
        return current
    }

    public fun fixFileContexts(
        fileContextsText: String,
        partition: String,
        uncoveredPaths: Collection<String>,
        defaultLabel: String? = null,
    ): String {
        var current = fileContextsText
        for (path in uncoveredPaths) {
            val label = defaultLabel ?: findNearestLabel(current, partition, path)
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
