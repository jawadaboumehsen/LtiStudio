/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.repository.filesystem

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.model.filesystem.FileNode

/**
 * Repository for file system operations.
 * Abstract to allow platform-specific implementations.
 */
interface FileSystemRepository {
    /**
     * List files and directories at the given path.
     */
    suspend fun listFiles(path: String): Result<List<FileNode>>

    /**
     * Read file content as text.
     */
    suspend fun readFile(path: String): Result<String>

    /**
     * Write content to file.
     */
    suspend fun writeFile(path: String, content: String): Result<Unit>

    /**
     * Create a new directory.
     */
    suspend fun createDirectory(path: String): Result<Unit>

    /**
     * Delete a file or directory.
     */
    suspend fun delete(path: String): Result<Unit>

    /**
     * Rename/move a file or directory.
     */
    suspend fun rename(oldPath: String, newPath: String): Result<Unit>

    /**
     * Check if path exists.
     */
    suspend fun exists(path: String): Boolean

    /**
     * Watch for file system changes.
     */
    fun watchDirectory(path: String): Flow<FileSystemEvent>
}

sealed class FileSystemEvent {
    data class FileCreated(val path: String) : FileSystemEvent()
    data class FileModified(val path: String) : FileSystemEvent()
    data class FileDeleted(val path: String) : FileSystemEvent()
}
