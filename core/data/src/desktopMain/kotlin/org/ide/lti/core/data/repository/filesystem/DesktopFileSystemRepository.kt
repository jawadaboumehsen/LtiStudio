/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.repository.filesystem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import org.ide.lti.core.model.filesystem.FileNode
import org.ide.lti.core.model.filesystem.FileType
import java.nio.file.FileSystems
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds

/**
 * Desktop/JVM implementation of FileSystemRepository using Okio.
 */
class DesktopFileSystemRepository : FileSystemRepository {

    private val fileSystem = FileSystem.SYSTEM

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
                    children = if (metadata.isDirectory) emptyList() else null,
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
        val watchService = try {
            FileSystems.getDefault().newWatchService()
        } catch (_: Exception) {
            null
        }

        val dir = try {
            val p = Paths.get(path)
            if (java.nio.file.Files.exists(p)) {
                p.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE,
                )
            }
            p
        } catch (_: Exception) {
            null
        }

        val job = launch(Dispatchers.IO) {
            while (isActive && watchService != null) {
                val key = try {
                    watchService.take()
                } catch (_: Exception) {
                    break
                }
                for (event in key.pollEvents()) {
                    val kind = event.kind()
                    val filename = event.context()?.toString() ?: ""
                    val fullPath = if (dir != null && filename.isNotEmpty()) dir.resolve(filename).toString() else filename
                    when (kind) {
                        StandardWatchEventKinds.ENTRY_CREATE -> trySend(FileSystemEvent.FileCreated(fullPath))
                        StandardWatchEventKinds.ENTRY_MODIFY -> trySend(FileSystemEvent.FileModified(fullPath))
                        StandardWatchEventKinds.ENTRY_DELETE -> trySend(FileSystemEvent.FileDeleted(fullPath))
                    }
                }
                if (!key.reset()) {
                    break
                }
            }
        }

        awaitClose {
            job.cancel()
            try {
                watchService?.close()
            } catch (_: Exception) {
            }
        }
    }
}
