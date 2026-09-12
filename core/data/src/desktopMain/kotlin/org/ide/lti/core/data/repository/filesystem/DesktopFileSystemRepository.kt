/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.repository.filesystem

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import org.ide.lti.core.domain.repository.filesystem.FileSystemEvent
import org.ide.lti.core.domain.repository.filesystem.FileSystemRepository
import org.ide.lti.core.model.filesystem.FileNode
import org.ide.lti.core.model.filesystem.FileType
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService

/**
 * Desktop/JVM implementation of FileSystemRepository using Okio.
 */
class DesktopFileSystemRepository : FileSystemRepository {

    private val fileSystem = FileSystem.SYSTEM
    private val logger = Logger.withTag("FileSystemWatcher")

    override suspend fun listFiles(path: String): Result<List<FileNode>> {
        return try {
            val dirPath = path.toPath()
            val files = fileSystem.list(dirPath).map { filePath ->
                val metadata = fileSystem.metadata(filePath)
                FileNode(
                    path = filePath.toString(),
                    name = filePath.name,
                    type = if (metadata.isDirectory) FileType.DIRECTORY else FileType.FILE,
                    size = metadata.size ?: 0,
                    lastModified = metadata.lastModifiedAtMillis ?: 0,
                    children = null,
                )
            }.sortedWith(compareBy({ it.type != FileType.DIRECTORY }, { it.name.lowercase() }))
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun readFile(path: String): Result<String> {
        return try {
            val content = fileSystem.read(path.toPath()) {
                readUtf8()
            }
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun writeFile(path: String, content: String): Result<Unit> {
        return try {
            fileSystem.write(path.toPath()) {
                writeUtf8(content)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createDirectory(path: String): Result<Unit> {
        return try {
            fileSystem.createDirectory(path.toPath())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun delete(path: String): Result<Unit> {
        return try {
            fileSystem.delete(path.toPath())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun rename(oldPath: String, newPath: String): Result<Unit> {
        return try {
            fileSystem.atomicMove(oldPath.toPath(), newPath.toPath())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exists(path: String): Boolean {
        return fileSystem.exists(path.toPath())
    }

    override fun watchDirectory(path: String): Flow<FileSystemEvent> = callbackFlow {
        val watchService = createWatchService()
        val dir = registerDirectory(path, watchService)

        val job = launch(Dispatchers.IO) {
            if (watchService == null) return@launch
            var keepWatching = true
            while (isActive && keepWatching) {
                val key = try {
                    watchService.take()
                } catch (_: java.nio.file.ClosedWatchServiceException) {
                    null
                } catch (e: Exception) {
                    logger.e(e) { "Error taking watch event key for directory: $path" }
                    null
                } ?: break

                processWatchEvents(key, dir) { trySend(it) }
                keepWatching = key.reset()
            }
        }

        awaitClose {
            job.cancel()
            try {
                watchService?.close()
            } catch (e: Exception) {
                logger.w(e) { "Error closing WatchService for directory: $path" }
            }
        }
    }

    private fun createWatchService(): WatchService? {
        return try {
            FileSystems.getDefault().newWatchService()
        } catch (e: Exception) {
            logger.e(e) { "Failed to create WatchService" }
            null
        }
    }

    private fun registerDirectory(path: String, watchService: WatchService?): Path? {
        if (watchService == null) return null
        return try {
            val p = Paths.get(path)
            if (Files.exists(p)) {
                p.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE,
                )
            }
            p
        } catch (e: Exception) {
            logger.e(e) { "Failed to register directory with WatchService: $path" }
            null
        }
    }

    private fun processWatchEvents(
        key: WatchKey,
        dir: Path?,
        onEvent: (FileSystemEvent) -> Unit,
    ) {
        for (event in key.pollEvents()) {
            toFileSystemEvent(event.kind(), dir, event.context())?.let(onEvent)
        }
    }

    private fun toFileSystemEvent(
        kind: WatchEvent.Kind<*>,
        dir: Path?,
        context: Any?,
    ): FileSystemEvent? {
        val filename = context?.toString() ?: ""
        val fullPath = if (dir != null && filename.isNotEmpty()) {
            dir.resolve(filename).toString()
        } else {
            filename
        }
        return when (kind) {
            StandardWatchEventKinds.ENTRY_CREATE -> FileSystemEvent.FileCreated(fullPath)
            StandardWatchEventKinds.ENTRY_MODIFY -> FileSystemEvent.FileModified(fullPath)
            StandardWatchEventKinds.ENTRY_DELETE -> FileSystemEvent.FileDeleted(fullPath)
            else -> null
        }
    }
}
