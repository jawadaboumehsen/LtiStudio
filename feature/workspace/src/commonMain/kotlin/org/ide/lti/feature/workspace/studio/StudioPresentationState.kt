/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio

import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.studio.StudioPresentation

public sealed interface StudioEntry {
    public data class Overview(val reason: StudioEntryReason) : StudioEntry
    public data class Editor(val stageId: StageId, val objectId: String, val reason: StudioEntryReason) : StudioEntry
}

public enum class StudioEntryReason {
    DEEP_LINK,
    RESTORED,
    NEW_WORKSPACE,
    OBJECT_NOT_FOUND,
    STAGE_NOT_FOUND,
}

/**
 * Pure entry resolver determining the initial destination (Overview or Stage Editor)
 * when entering ROM Setup Studio.
 *
 * Precedence rules:
 * 1. An explicit request wins over any stored restoration, always.
 * 2. Requested stage + requested object that the catalog knows and that belong together -> [StudioEntry.Editor]
 *    with [StudioEntryReason.DEEP_LINK].
 * 3. Requested object unknown to the catalog (or not owned by the requested stage) -> fall back to the requested
 *    stage's first subobject with [StudioEntryReason.OBJECT_NOT_FOUND]; an unknown requested stage ->
 *    [StudioEntry.Overview] with [StudioEntryReason.STAGE_NOT_FOUND]; a requested object with no stage at all ->
 *    [StudioEntry.Overview] with [StudioEntryReason.OBJECT_NOT_FOUND].
 * 4. No request, presentation null or hasOpened == false -> [StudioEntry.Overview] with
 *    [StudioEntryReason.NEW_WORKSPACE].
 * 5. No request, hasOpened == true, no editor ever opened -> [StudioEntry.Overview] with
 *    [StudioEntryReason.RESTORED]. Stored stage+object still valid -> [StudioEntry.Editor] with
 *    [StudioEntryReason.RESTORED]; stored object no longer valid -> [StudioEntry.Editor] with the owning stage's
 *    first subobject and [StudioEntryReason.OBJECT_NOT_FOUND]; stored stage no longer valid -> [StudioEntry.Overview]
 *    with [StudioEntryReason.STAGE_NOT_FOUND].
 */
public fun resolveStudioEntry(
    presentation: StudioPresentation?,
    requestedStageId: StageId?,
    requestedObjectId: String?,
): StudioEntry {
    val hasExplicitRequest = requestedStageId != null || requestedObjectId != null

    return if (hasExplicitRequest) {
        resolveExplicitRequest(requestedStageId, requestedObjectId)
    } else {
        resolveRestorationOrFallback(presentation)
    }
}

private fun resolveExplicitRequest(requestedStageId: StageId?, requestedObjectId: String?): StudioEntry {
    val stage = if (requestedStageId != null) {
        StudioDestinationCatalog.stage(requestedStageId)
    } else {
        requestedObjectId?.let(StudioDestinationCatalog::owningStage)
    }
    if (stage == null) {
        val reason = if (requestedStageId != null) {
            StudioEntryReason.STAGE_NOT_FOUND
        } else {
            StudioEntryReason.OBJECT_NOT_FOUND
        }
        return StudioEntry.Overview(reason)
    }
    val matched = requestedObjectId?.let { id -> stage.subobjects.firstOrNull { it.id == id } }
    return StudioEntry.Editor(
        stageId = stage.stageId,
        objectId = (matched ?: stage.subobjects.first()).id,
        reason = if (requestedObjectId != null && matched == null) {
            StudioEntryReason.OBJECT_NOT_FOUND
        } else {
            StudioEntryReason.DEEP_LINK
        },
    )
}

private fun resolveRestorationOrFallback(presentation: StudioPresentation?): StudioEntry {
    val storedStage = presentation?.editorStageId?.let { StudioDestinationCatalog.stage(it) }
    return when {
        presentation == null || !presentation.hasOpened -> {
            StudioEntry.Overview(StudioEntryReason.NEW_WORKSPACE)
        }
        // No editor was ever opened in this workspace: restoring genuinely means the overview.
        presentation.editorStageId == null -> {
            StudioEntry.Overview(StudioEntryReason.RESTORED)
        }
        storedStage == null -> {
            StudioEntry.Overview(StudioEntryReason.STAGE_NOT_FOUND)
        }
        else -> {
            val storedSubobject = storedStage.subobjects.firstOrNull { it.id == presentation.editorObjectId }
            if (storedSubobject != null) {
                StudioEntry.Editor(
                    stageId = storedStage.stageId,
                    objectId = storedSubobject.id,
                    reason = StudioEntryReason.RESTORED,
                )
            } else {
                StudioEntry.Editor(
                    stageId = storedStage.stageId,
                    objectId = storedStage.subobjects.first().id,
                    reason = StudioEntryReason.OBJECT_NOT_FOUND,
                )
            }
        }
    }
}

/**
 * Returns a copy of this presentation state with the selected editor stage and object.
 *
 * Marks the workspace as opened, so a later entry restores this selection instead of the new-workspace overview.
 *
 * Selecting a stage or object is presentation only - it must never start work,
 * mutate a snapshot, or reset a draft.
 */
public fun StudioPresentation.withEditor(stageId: StageId, objectId: String): StudioPresentation = copy(
    hasOpened = true,
    editorStageId = stageId,
    editorObjectId = objectId,
)

/**
 * Returns a copy of this presentation state with the navigator width clamped to 200..360.
 */
public fun StudioPresentation.withNavigatorWidth(width: Int): StudioPresentation = copy(
    navigatorWidth = width.coerceIn(
        StudioPresentation.MIN_NAVIGATOR_WIDTH,
        StudioPresentation.MAX_NAVIGATOR_WIDTH,
    ),
)
