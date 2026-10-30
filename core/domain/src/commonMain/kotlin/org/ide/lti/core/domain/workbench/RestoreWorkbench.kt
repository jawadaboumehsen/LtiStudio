/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workbench

import org.ide.lti.core.domain.pipeline.stages.DebloatStage
import org.ide.lti.core.domain.pipeline.text.SidecarEditor
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.pipeline.toolkit.RestoreIndex
import org.ide.lti.core.domain.pipeline.toolkit.RestorePoint
import org.ide.lti.core.domain.pipeline.toolkit.RestorePointState
import org.ide.lti.core.domain.pipeline.toolkit.RestorePointStore

/** The workspace's restore points, and putting a backed-up item back where it was (FR-027, FR-038). */
public class RestoreWorkbench(private val runner: WorkbenchRunner, private val store: RestorePointStore) {
    /** Every restore point, with [RestorePointState.BackupMissing] for one whose backup was deleted. */
    public suspend fun list(workspaceId: String): RestoreIndex = runner.read(workspaceId) {
        val index = store.readIndex(ws)
        val checked = index.items.map { point ->
            if (point.state == RestorePointState.Available && !exists(point.backupPath)) {
                point.copy(state = RestorePointState.BackupMissing)
            } else {
                point
            }
        }
        RestoreIndex(checked).also { if (it != index) store.writeIndex(ws, it) }
    }

    /** Copies [pointId]'s backup over its item and marks it restored. */
    public suspend fun restore(workspaceId: String, pointId: String): RestorePoint =
        runner.job(workspaceId, "Restore") { restoreOne(this, pointId) }

    /** Restores every available point of [tool] (all tools when null); returns the restored points. */
    public suspend fun restoreAll(workspaceId: String, tool: String? = null): List<RestorePoint> =
        runner.job(workspaceId, "Restore") {
            store.readIndex(ws).items
                .filter { it.state == RestorePointState.Available && (tool == null || it.tool == tool) }
                .map { restoreOne(this, it.id) }
        }

    private suspend fun restoreOne(job: WorkbenchRunner.Job, pointId: String): RestorePoint = with(job) {
        val point = store.readIndex(ws).findById(pointId)
            ?: throw RefusalException(Refusal("No such restore point.", pointId, "Refresh the restore point list."))
        if (!exists(point.backupPath)) {
            store.updateRestorePointState(ws, point.id, RestorePointState.BackupMissing)
            throw RefusalException(
                Refusal("The backup file is gone.", point.backupPath, "Nothing can be restored from this point."),
            )
        }
        // A debloat item lives in work/<partition>/…; other tools record the path relative to the workspace.
        val target = if (point.tool == DebloatStage.TOOL) "work/${point.itemPath}" else point.itemPath
        sh(
            "rm -rf \"$2\" && mkdir -p \"$(dirname \"$2\")\" && cp -a -T \"$1\" \"$2\"",
            abs(point.backupPath),
            abs(target),
        )
        if (point.tool == DebloatStage.TOOL) restoreSidecars(job, point.itemPath)
        store.updateRestorePointState(ws, point.id, RestorePointState.Restored)
        point.copy(state = RestorePointState.Restored)
    }

    /** Debloat pruned the item's sidecar lines; they come back from the copies taken before (FR-015). */
    private suspend fun restoreSidecars(job: WorkbenchRunner.Job, itemPath: String) = with(job) {
        val partition = itemPath.substringBefore('/')
        val path = itemPath.substringAfter('/')
        for ((name, merge) in listOf(
            "fs_config-$partition" to SidecarEditor::withRestoredFsConfig,
            "file_context-$partition" to SidecarEditor::withRestoredFileContext,
        )) {
            val backup = readText("${DebloatStage.SIDECAR_BACKUP_DIR}/$name")
            val current = readText("work/configs/$name")
            if (backup != null && current != null) {
                writeText("work/configs/$name", merge(current, backup, partition, path))
            }
        }
    }
}
