/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.toolkit

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.ide.lti.core.domain.pipeline.toolkit.AckStore
import org.ide.lti.core.domain.pipeline.toolkit.Acknowledgements
import org.ide.lti.core.domain.pipeline.toolkit.JobLock
import org.ide.lti.core.domain.pipeline.toolkit.JobLockHandle
import org.ide.lti.core.domain.pipeline.toolkit.LockResult
import org.ide.lti.core.domain.pipeline.toolkit.RestoreIndex
import org.ide.lti.core.domain.pipeline.toolkit.RestorePoint
import org.ide.lti.core.domain.pipeline.toolkit.RestorePointState
import org.ide.lti.core.domain.pipeline.toolkit.RestorePointStore
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.workspace.Workspace
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implements [RestorePointStore], [AckStore] and [JobLock]
 * using the build service [PipelineExecutionPort] when available, or falling back
 * directly to the host filesystem for local execution and real-host testing.
 */
public class WorkspaceToolkitStores(private val execution: PipelineExecutionPort? = null) :
    RestorePointStore,
    AckStore,
    JobLock {

    private val lockMutex = Mutex()
    private val acknowledgementMutex = Mutex()

    /** This app run: a lock written by an earlier run (the app crashed or was killed) is stale, not held. */
    private val session = java.util.UUID.randomUUID().toString()

    public companion object {
        public const val ACKNOWLEDGEMENTS_FILE: String = "facts/acknowledgements.json"
        public const val RESTORE_DIR: String = "restore"
        public const val RESTORE_INDEX_FILE: String = "restore/index.json"
        public const val JOB_LOCK_FILE: String = ".job.lock"
        private const val SEPARATOR = "\t"
    }

    // -------------------------------------------------------------------------
    // RestorePointStore
    // -------------------------------------------------------------------------

    override suspend fun readIndex(workspace: Workspace): RestoreIndex {
        val json = readFile(workspace, RESTORE_INDEX_FILE) ?: return RestoreIndex.Empty
        return try {
            RestoreIndex.fromJson(json)
        } catch (e: kotlinx.serialization.SerializationException) {
            // Writing an empty index over it would lose every backup it points to.
            throw IllegalStateException("$RESTORE_INDEX_FILE is damaged (${e.message}); it is kept as it is.", e)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("$RESTORE_INDEX_FILE is damaged (${e.message}); it is kept as it is.", e)
        }
    }

    override suspend fun writeIndex(workspace: Workspace, index: RestoreIndex) {
        writeFile(workspace, RESTORE_INDEX_FILE, index.toJson())
    }

    override suspend fun addRestorePoint(workspace: Workspace, point: RestorePoint): Unit = indexMutex.withLock {
        writeIndex(workspace, readIndex(workspace).add(point))
    }

    override suspend fun updateRestorePointState(workspace: Workspace, id: String, state: RestorePointState): Unit =
        indexMutex.withLock { writeIndex(workspace, readIndex(workspace).withState(id, state)) }

    private val indexMutex = Mutex()

    // -------------------------------------------------------------------------
    // AckStore
    // -------------------------------------------------------------------------

    override suspend fun readAcknowledgements(workspace: Workspace): Acknowledgements {
        val json = readFile(workspace, ACKNOWLEDGEMENTS_FILE) ?: return Acknowledgements.Empty
        return try {
            Acknowledgements.fromJson(json)
        } catch (_: Throwable) {
            Acknowledgements.Empty
        }
    }

    override suspend fun acknowledge(workspace: Workspace, featureId: String, acceptedAt: String): Acknowledgements =
        acknowledgementMutex.withLock {
            val updated = readAcknowledgements(workspace).accept(featureId, acceptedAt)
            writeFile(workspace, ACKNOWLEDGEMENTS_FILE, updated.toJson())
            updated
        }

    override suspend fun isAcknowledged(workspace: Workspace, featureId: String): Boolean =
        readAcknowledgements(workspace).isAcknowledged(featureId)

    // -------------------------------------------------------------------------
    // JobLock
    // -------------------------------------------------------------------------

    override suspend fun acquire(workspace: Workspace, holderName: String): LockResult {
        require(holderName.isNotBlank()) { "JobLock holderName must not be blank" }
        return lockMutex.withLock {
            val existing = currentHolder(workspace)
            if (!existing.isNullOrBlank()) {
                LockResult.Locked(existing)
            } else {
                writeFile(workspace, JOB_LOCK_FILE, "${holderName.trim()}$SEPARATOR$session")
                val handle = object : JobLockHandle {
                    private val released = AtomicBoolean(false)
                    override val holderName: String = holderName.trim()
                    override val workspace: Workspace = workspace

                    override suspend fun release() {
                        // Marked only once it is gone: a delete that failed can be tried again.
                        if (!released.get()) {
                            this@WorkspaceToolkitStores.release(workspace, holderName)
                            released.set(true)
                        }
                    }

                    override fun close() {
                        runBlocking { release() }
                    }
                }
                LockResult.Acquired(handle)
            }
        }
    }

    override suspend fun currentHolder(workspace: Workspace): String? {
        val (holder, owner) = readFile(workspace, JOB_LOCK_FILE)?.trim()?.split(SEPARATOR, limit = 2)
            ?.takeIf { it.size == 2 && it[0].isNotBlank() } ?: return null
        return holder.takeIf { owner == session }
    }

    override suspend fun release(workspace: Workspace, holderName: String?) {
        lockMutex.withLock {
            val current = currentHolder(workspace)
            if (current != null && (holderName == null || current == holderName.trim())) {
                check(
                    deleteFile(workspace, JOB_LOCK_FILE),
                ) { "The job lock of ${workspace.name} could not be removed." }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal file operations
    // -------------------------------------------------------------------------

    private suspend fun readFile(workspace: Workspace, relPath: String): String? {
        if (execution != null) {
            return execution.readFile(workspace, relPath)
        }
        val local = resolveLocal(workspace, relPath)
        return if (Files.isRegularFile(local)) Files.readString(local) else null
    }

    private suspend fun writeFile(workspace: Workspace, relPath: String, content: String) {
        if (execution != null) {
            check(execution.uploadFile(workspace, relPath, content.encodeToByteArray())) {
                "$relPath could not be written to the workspace"
            }
            return
        }
        val local = resolveLocal(workspace, relPath)
        if (local.parent != null) {
            Files.createDirectories(local.parent)
        }
        Files.writeString(local, content)
    }

    private suspend fun deleteFile(workspace: Workspace, relPath: String): Boolean {
        if (execution != null) {
            val root = checkNotNull(workspace.linuxPath) { "workspace ${workspace.id} has no WSL folder" }
            val result = execution.execute(workspace, StepCommand("rm", listOf("-f", "$root/$relPath"), root))
            return result.exitCode == 0
        }
        val local = resolveLocal(workspace, relPath)
        return Files.deleteIfExists(local)
    }

    private fun resolveLocal(workspace: Workspace, relPath: String): Path = Path.of(workspace.path, relPath)
}
