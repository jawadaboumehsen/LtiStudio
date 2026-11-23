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

enum class PartitionNodeType {
    DIRECTORY,
    REGULAR_FILE,
    SYMLINK,
}

/**
 * Virtual filesystem node representing files and directories inside an EROFS/EXT4 partition image
 * without requiring loop-mounting or root privilege.
 */
data class PartitionNode(
    val path: String,
    val name: String,
    val type: PartitionNodeType,
    val sizeBytes: Long = 0L,
    val compressedSizeBytes: Long? = null,
    val permissions: String = "0644",
    val selinuxContext: String = "u:object_r:system_file:s0",
    val targetLink: String? = null,
    val children: List<PartitionNode> = emptyList(),
) {
    val isDirectory: Boolean get() = type == PartitionNodeType.DIRECTORY

    fun filter(query: String): PartitionNode? {
        if (query.isBlank()) return this
        val matchesSelf = name.contains(query, ignoreCase = true) ||
            path.contains(query, ignoreCase = true) ||
            selinuxContext.contains(query, ignoreCase = true)
        val filteredChildren = children.mapNotNull { it.filter(query) }
        return if (matchesSelf || filteredChildren.isNotEmpty()) {
            copy(children = filteredChildren)
        } else {
            null
        }
    }
}

/**
 * Metadata descriptor for an inspected partition image.
 */
data class PartitionImageMetadata(
    val partitionName: String,
    val filesystem: String,
    val totalSizeBytes: Long,
    val totalFiles: Int,
    val rootNode: PartitionNode,
)
