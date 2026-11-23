/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.partition.explorer

/**
 * Domain parser converting Android partition manifests, fs_config tables,
 * and EROFS/EXT4 image listings into a virtual [PartitionNode] hierarchy.
 */
object PartitionTreeParser {

    /**
     * Parses lines formatted as:
     * `path mode selinux_context size [symlink_target]`
     * or standard `fs_config` format:
     * `path uid gid mode capabilities`
     */
    fun parseManifest(partitionName: String, filesystem: String, manifestLines: List<String>): PartitionImageMetadata {
        val rootChildren = manifestLines.mapNotNull { parseLine(it) }
        val totalSize = rootChildren.sumOf { it.sizeBytes }

        val rootNode = PartitionNode(
            path = partitionName,
            name = partitionName,
            type = PartitionNodeType.DIRECTORY,
            sizeBytes = totalSize,
            children = rootChildren,
        )

        return PartitionImageMetadata(
            partitionName = partitionName,
            filesystem = filesystem,
            totalSizeBytes = totalSize,
            totalFiles = rootChildren.size,
            rootNode = rootNode,
        )
    }

    private fun parseLine(rawLine: String): PartitionNode? {
        val line = rawLine.trim()
        val tokens = if (line.isEmpty() || line.startsWith("#")) {
            emptyList()
        } else {
            line.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        }
        val fullPath = tokens.firstOrNull()?.removePrefix("/") ?: ""
        if (fullPath.isEmpty()) return null

        val fileName = fullPath.split("/").last()
        val isDir = fullPath.endsWith("/") || (tokens.size > 1 && tokens[1].startsWith("040"))
        val mode = tokens.getOrNull(1) ?: "0644"
        val selinux = tokens.getOrNull(2)?.takeIf { it.contains(":") } ?: "u:object_r:system_file:s0"
        val size = tokens.getOrNull(3)?.toLongOrNull() ?: 0L
        val linkTarget = tokens.getOrNull(4)

        val nodeType = when {
            linkTarget != null -> PartitionNodeType.SYMLINK
            isDir -> PartitionNodeType.DIRECTORY
            else -> PartitionNodeType.REGULAR_FILE
        }

        return PartitionNode(
            path = fullPath,
            name = fileName,
            type = nodeType,
            sizeBytes = size,
            permissions = mode,
            selinuxContext = selinux,
            targetLink = linkTarget,
        )
    }
}
