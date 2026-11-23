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
    fun parseManifest(
        partitionName: String,
        filesystem: String,
        manifestLines: List<String>,
    ): PartitionImageMetadata {
        val rootChildren = mutableListOf<PartitionNode>()
        var totalSize = 0L
        var totalCount = 0

        for (rawLine in manifestLines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            val tokens = line.split(Regex("""\s+"""))
            if (tokens.isEmpty()) continue

            val fullPath = tokens[0].removePrefix("/")
            if (fullPath.isEmpty()) continue

            val segments = fullPath.split("/")
            val fileName = segments.last()
            val isDir = fullPath.endsWith("/") || (tokens.size > 1 && tokens[1].startsWith("040"))

            val mode = if (tokens.size > 1) tokens[1] else "0644"
            val selinux = if (tokens.size > 2 && tokens[2].contains(":")) tokens[2] else "u:object_r:system_file:s0"
            val size = if (tokens.size > 3) tokens[3].toLongOrNull() ?: 0L else 0L
            val linkTarget = if (tokens.size > 4) tokens[4] else null

            val nodeType = when {
                linkTarget != null -> PartitionNodeType.SYMLINK
                isDir -> PartitionNodeType.DIRECTORY
                else -> PartitionNodeType.REGULAR_FILE
            }

            totalSize += size
            totalCount++

            val node = PartitionNode(
                path = fullPath,
                name = fileName,
                type = nodeType,
                sizeBytes = size,
                permissions = mode,
                selinuxContext = selinux,
                targetLink = linkTarget,
            )
            rootChildren.add(node)
        }

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
            totalFiles = totalCount,
            rootNode = rootNode,
        )
    }
}
