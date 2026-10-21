/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.repository.target

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.repository.target.WorkspaceDeviceRepository
import org.ide.lti.core.domain.repository.target.WorkspaceDeviceRepository.Companion.FILE_NAME
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.Workspace

/** Reads and writes `device.json` in the workspace folder through the build service; nothing is kept in the app. */
public class WorkspaceFolderDeviceRepository(
    private val workspaces: WorkspaceRepository,
    private val files: PipelineExecutionPort,
    private val json: Json = DeviceJson,
) : WorkspaceDeviceRepository {
    private val changes = MutableSharedFlow<Pair<String, TargetDevice?>>(extraBufferCapacity = CHANGE_BUFFER)

    override fun observe(workspaceId: String): Flow<TargetDevice?> = flow {
        emit(read(workspace(workspaceId)))
        emitAll(changes.filter { it.first == workspaceId }.map { it.second })
    }

    // A successful read is shared with every observer, so a retry anywhere refreshes the whole app.
    override suspend fun get(workspaceId: String): TargetDevice? =
        read(workspace(workspaceId)).also { changes.emit(workspaceId to it) }

    override suspend fun save(workspaceId: String, device: TargetDevice): TargetDevice {
        val workspace = workspace(workspaceId)
        val next = nextRevision(read(workspace), device)
        val written = files.uploadFile(workspace, FILE_NAME, json.encodeToString(next).encodeToByteArray())
        check(written) { "Could not write $FILE_NAME into the workspace folder" }
        changes.emit(workspaceId to next)
        return next
    }

    private suspend fun workspace(workspaceId: String): Workspace =
        requireNotNull(workspaces.getWorkspace(workspaceId)) { "Workspace not found: $workspaceId" }

    // Only a definite "no such file" in a reachable folder means "no device". `stat` reports any failure as
    // "missing", so the folder's own workspace.json must be there before an absent device.json is believed.
    private suspend fun read(workspace: Workspace): TargetDevice? {
        if (!files.stat(workspace, FILE_NAME).exists) {
            check(files.stat(workspace, MANIFEST).exists) { "The workspace folder could not be reached" }
            return null
        }
        val text = checkNotNull(files.readFile(workspace, FILE_NAME)) { "Could not read $FILE_NAME" }
        return json.decodeFromString<TargetDevice>(text)
    }

    public companion object {
        private const val CHANGE_BUFFER = 16
        private const val MANIFEST = "workspace.json"

        /** Readable on disk and tolerant of fields added later. */
        public val DeviceJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }

        /** [incoming] with the revision after [stored]: bumped when the content differs, unchanged otherwise. */
        public fun nextRevision(stored: TargetDevice?, incoming: TargetDevice): TargetDevice = when {
            stored == null -> incoming
            stored.copy(revision = incoming.revision) == incoming -> incoming.copy(revision = stored.revision)
            else -> incoming.copy(revision = stored.revision + 1)
        }
    }
}
