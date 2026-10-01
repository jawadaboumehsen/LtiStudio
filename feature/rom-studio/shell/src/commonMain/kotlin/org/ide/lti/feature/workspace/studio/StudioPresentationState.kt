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

import androidx.compose.runtime.Immutable
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.studio.StudioPresentation
import org.ide.lti.feature.rom.studio.api.CanonicalStageDescriptors
import org.ide.lti.feature.rom.studio.api.StageDescriptor

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
    descriptors: List<StageDescriptor> = CanonicalStageDescriptors.ALL,
): StudioEntry {
    val hasExplicitRequest = requestedStageId != null || requestedObjectId != null

    return if (hasExplicitRequest) {
        resolveExplicitRequest(requestedStageId, requestedObjectId, descriptors)
    } else {
        resolveRestorationOrFallback(presentation, descriptors)
    }
}

private fun resolveExplicitRequest(
    requestedStageId: StageId?,
    requestedObjectId: String?,
    descriptors: List<StageDescriptor>,
): StudioEntry {
    val stage = if (requestedStageId != null) {
        descriptors.firstOrNull { it.stageId == requestedStageId }
    } else {
        requestedObjectId?.let { id ->
            descriptors.firstOrNull { stage -> stage.subobjects.any { it.id.value == id } }
        }
    }
    if (stage == null) {
        val reason = if (requestedStageId != null) {
            StudioEntryReason.STAGE_NOT_FOUND
        } else {
            StudioEntryReason.OBJECT_NOT_FOUND
        }
        return StudioEntry.Overview(reason)
    }
    val matched = requestedObjectId?.let { id -> stage.subobjects.firstOrNull { it.id.value == id } }
    return StudioEntry.Editor(
        stageId = stage.stageId,
        objectId = (matched ?: stage.subobjects.first()).id.value,
        reason = if (requestedObjectId != null && matched == null) {
            StudioEntryReason.OBJECT_NOT_FOUND
        } else {
            StudioEntryReason.DEEP_LINK
        },
    )
}

private fun resolveRestorationOrFallback(
    presentation: StudioPresentation?,
    descriptors: List<StageDescriptor>,
): StudioEntry {
    val storedStage = presentation?.editorStageId?.let { id -> descriptors.firstOrNull { it.stageId == id } }
    return when {
        presentation == null || !presentation.hasOpened -> {
            StudioEntry.Overview(StudioEntryReason.NEW_WORKSPACE)
        }
        presentation.inOverview -> {
            StudioEntry.Overview(StudioEntryReason.RESTORED)
        }
        // No editor was ever opened in this workspace: restoring genuinely means the overview.
        presentation.editorStageId == null -> {
            StudioEntry.Overview(StudioEntryReason.RESTORED)
        }
        storedStage == null -> {
            StudioEntry.Overview(StudioEntryReason.STAGE_NOT_FOUND)
        }
        else -> {
            val storedSubobject = storedStage.subobjects.firstOrNull { it.id.value == presentation.editorObjectId }
            if (storedSubobject != null) {
                StudioEntry.Editor(
                    stageId = storedStage.stageId,
                    objectId = storedSubobject.id.value,
                    reason = StudioEntryReason.RESTORED,
                )
            } else {
                StudioEntry.Editor(
                    stageId = storedStage.stageId,
                    objectId = storedStage.subobjects.first().id.value,
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
    inOverview = false,
)

/**
 * Returns a copy of this presentation state marked as being in Overview mode while preserving the last editor stage.
 */
public fun StudioPresentation.withOverview(): StudioPresentation = copy(
    hasOpened = true,
    inOverview = true,
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

@Immutable
public sealed interface DisplayValue {
    public data class Available(val text: String) : DisplayValue
    public data class Unavailable(val reason: String) : DisplayValue
}

public enum class GlobalDestination {
    Setup,
    Workspace,
    Settings,
    PluginManager,
}

public enum class WorkspaceSection {
    Overview,
    RomConfig,
    RunHistory,
    Artifacts,
    TargetProfile,
}

@Immutable
public sealed interface OperationUiState {
    public data object Idle : OperationUiState

    public data class Running(val generation: Long, val startedFromRevision: Long) : OperationUiState

    public data class Succeeded(val generation: Long, val completedRevision: Long, val message: String? = null) :
        OperationUiState

    public data class Failed(val generation: Long, val startedFromRevision: Long, val userMessage: String) :
        OperationUiState
}

@Immutable
public sealed interface ConnectionUiState {
    public data object Connecting : ConnectionUiState
    public data class Connected(val target: String) : ConnectionUiState
    public data class Disconnected(val reason: String? = null) : ConnectionUiState
}

@Immutable
public sealed interface ActivityUiState {
    public data object Idle : ActivityUiState
    public data class Active(val description: String, val progress: Float? = null) : ActivityUiState
}

@Immutable
public data class TargetContextUi(
    val targetId: DisplayValue,
    val profile: DisplayValue,
    val revision: DisplayValue,
    val draftBadge: String? = null,
)

@Immutable
public data class PipelineRailItemUi(
    val stageId: StageId,
    val position: Int,
    val label: String,
    val iconKey: String,
    val isSelected: Boolean = false,
    val isEnabled: Boolean = true,
    val disabledReason: String? = null,
    val hasError: Boolean = false,
    val accessibleDescription: String = label,
)

@Immutable
public data class IdeStatusUiState(
    val problemCount: Int = 0,
    val activity: ActivityUiState = ActivityUiState.Idle,
    val connection: ConnectionUiState = ConnectionUiState.Disconnected(),
)

@Immutable
public data class WorkspaceShellUiState(
    val globalDestination: GlobalDestination = GlobalDestination.Workspace,
    val targetContext: TargetContextUi? = null,
    val hasUnsavedChanges: Boolean = false,
    val validateOperation: OperationUiState = OperationUiState.Idle,
    val saveOperation: OperationUiState = OperationUiState.Idle,
    val connection: ConnectionUiState = ConnectionUiState.Disconnected(),
    val problemCount: Int = 0,
    val activity: ActivityUiState = ActivityUiState.Idle,
    val pipelineItems: List<PipelineRailItemUi> = createDefaultPipelineRailItems(),
    val selectedWorkspaceSection: WorkspaceSection? = WorkspaceSection.Overview,
) {
    init {
        require(problemCount >= 0) { "problemCount must be non-negative: $problemCount" }
        require(pipelineItems.size == 8) {
            "pipelineItems must contain exactly 8 items, found ${pipelineItems.size}"
        }
        if (globalDestination != GlobalDestination.Workspace) {
            require(selectedWorkspaceSection == null) {
                "selectedWorkspaceSection must be null when globalDestination is $globalDestination"
            }
        }
    }
}

public fun createDefaultPipelineRailItems(
    selectedStageId: StageId? = null,
    descriptors: List<StageDescriptor> = CanonicalStageDescriptors.ALL,
): List<PipelineRailItemUi> = descriptors.mapIndexed { index, stage ->
    val railLabel = when (stage.stageId) {
        StageId.FIRMWARE_ACQUISITION -> "Acquire"
        StageId.FIRMWARE_EXTRACTION -> "Extract"
        StageId.WORK_TREE_ASSEMBLY -> "Assemble"
        StageId.DEBLOAT -> "Debloat"
        StageId.MODULE_APPLICATION -> "Patches"
        StageId.BUILD_FLASHABLE_ZIP -> "Build"
        StageId.GENERATE_OTA_MANIFEST -> "Metadata"
        StageId.PUBLISH_RELEASE -> "Publish"
    }
    PipelineRailItemUi(
        stageId = stage.stageId,
        position = index + 1,
        label = railLabel,
        iconKey = stage.stageId.name,
        isSelected = stage.stageId == selectedStageId,
        isEnabled = true,
        disabledReason = null,
        hasError = false,
        accessibleDescription = stage.accessibleLabel,
    )
}
