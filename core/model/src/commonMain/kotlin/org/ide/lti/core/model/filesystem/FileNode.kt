/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.filesystem

import kotlinx.serialization.Serializable

/**
 * Represents a file or directory node in the file tree.
 */
@Serializable
data class FileNode(
    val path: String,
    val name: String,
    val type: FileType,
    val size: Long = 0,
    val lastModified: Long = 0,
    // null for files, list for directories
    val children: List<FileNode>? = null,
    val isExpanded: Boolean = false,
)

@Serializable
enum class FileType {
    FILE,
    DIRECTORY,
    SYMLINK,
}
