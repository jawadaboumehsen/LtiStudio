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

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.ide.lti.core.domain.pipeline.stages.DebloatStage
import org.ide.lti.core.domain.pipeline.text.SidecarEditor
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.pipeline.toolkit.RestoreIndex
import org.ide.lti.core.domain.pipeline.toolkit.RestorePoint
import org.ide.lti.core.domain.pipeline.toolkit.RestorePointState
import org.ide.lti.core.domain.pipeline.toolkit.RestorePointStore

private const val RESTORED_BUFFER = 16

/** [itemPath], as its restore point names it, is back in [workspaceId]. */
public data class RestoredItem(val workspaceId: String, val itemPath: String)

/** The workspace's restore points, and putting a backed-up item back where it was (FR-027, FR-038). */
public class RestoreWorkbench(private val runner: WorkbenchRunner, private val store: RestorePointStore) {
    private val restoredItems = MutableSharedFlow<RestoredItem>(extraBufferCapacity = RESTORED_BUFFER)

    /** Each item put back, so a pane still reporting the change it undid can drop that report. */
    public val restored: SharedFlow<RestoredItem> = restoredItems.asSharedFlow()

    /** Every restore point, with [RestorePointState.BackupMissing] for one whose backup was deleted. */
    public suspend fun list(workspaceId: String): RestoreIndex = runner.read(workspaceId) {
        val index = store.readIndex(ws)
        val checked = index.items.map { point ->
            if (point.state == RestorePointState.Available && !point.added && !exists(point.backupPath)) {
                point.copy(state = RestorePointState.BackupMissing)
            } else {
                point
            }
        }
        RestoreIndex(checked)
    }

    /** Copies [pointId]'s backup over its item and marks it restored. */
    public suspend fun restore(workspaceId: String, pointId: String): RestorePoint =
        runner.job(workspaceId, "Restore") { restoreOne(this, pointId) }.also { announce(workspaceId, it) }

    /** Restores every available point of [tool] (all tools when null); returns the restored points. */
    public suspend fun restoreAll(workspaceId: String, tool: String? = null): List<RestorePoint> =
        runner.job(workspaceId, "Restore") {
            store.readIndex(ws).items
                .filter { it.state == RestorePointState.Available && (tool == null || it.tool == tool) }
                .asReversed()
                .map { restoreOne(this, it.id) }
        }.onEach { announce(workspaceId, it) }

    private fun announce(workspaceId: String, point: RestorePoint) {
        restoredItems.tryEmit(RestoredItem(workspaceId, point.itemPath))
    }

    private suspend fun restoreOne(job: WorkbenchRunner.Job, pointId: String): RestorePoint = with(job) {
        val point = store.readIndex(ws).findById(pointId)
            ?: throw RefusalException(Refusal("No such restore point.", pointId, "Refresh the restore point list."))
        if (!point.added && !exists(point.backupPath)) {
            store.updateRestorePointState(ws, point.id, RestorePointState.BackupMissing)
            throw RefusalException(
                Refusal("The backup file is gone.", point.backupPath, "Nothing can be restored from this point."),
            )
        }
        // A debloat item lives in work/<partition>/…; other tools record the path relative to the workspace.
        val target = if (point.tool == DebloatStage.TOOL) "work/${point.itemPath}" else point.itemPath
        listOf(point.backupPath, target).firstOrNull { !insideWorkspace(it) }?.let {
            throw RefusalException(
                Refusal(
                    "This restore point leaves the workspace.",
                    it,
                    "Check restore/index.json; nothing was changed.",
                ),
            )
        }
        // The record may name no `..` and still pass through a link: what counts is where its folder really is.
        (listOf(target) + listOfNotNull(point.backupPath.takeUnless { point.added })).firstOrNull {
            !folderResolvesInside(it, "")
        }?.let {
            throw RefusalException(
                Refusal(
                    "This restore point leaves the workspace through a link.",
                    it,
                    "Check restore/index.json and the link; nothing was changed.",
                ),
            )
        }
        if (point.added) {
            // Undoing an addition: the item goes (its sidecar lines name a missing path and are harmless).
            run("rm", "-rf", "--", abs(target))
            store.updateRestorePointState(ws, point.id, RestorePointState.Restored)
            return@with point.copy(state = RestorePointState.Restored)
        }
        sh(
            "mkdir -p \"$(dirname \"$2\")\" && rm -rf \"$2.restoring\" && cp -a -T \"$1\" \"$2.restoring\" && " +
                // Swapped, not deleted first: stopped midway, the old item is still there as "$2.old".
                "rm -rf \"$2.old\" && { [ ! -e \"$2\" ] && [ ! -L \"$2\" ] || mv \"$2\" \"$2.old\"; } && " +
                "mv \"$2.restoring\" \"$2\" && rm -rf \"$2.old\"",
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

    /** A workspace-relative path that stays inside it (no absolute path, no `..`). */
    private fun insideWorkspace(path: String): Boolean =
        path.isNotBlank() && !path.startsWith("/") && ".." !in path.split('/')
}
