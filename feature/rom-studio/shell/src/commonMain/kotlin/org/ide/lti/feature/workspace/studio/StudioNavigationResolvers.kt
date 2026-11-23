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

import org.ide.lti.core.designsystem.component.layout.IdePipelineStageItem
import org.ide.lti.core.designsystem.component.layout.RailStageStatus
import org.ide.lti.core.model.run.StageId
import org.ide.lti.feature.rom.studio.api.CanonicalStageDescriptors
import org.ide.lti.feature.rom.studio.api.StageEditorRegistry
import org.ide.lti.feature.rom.studio.api.descriptorFor

internal const val READY = "Ready to build"

/** Rail entry for the device configuration of the workspace (opens the ROM configuration panel). */
internal val DEVICE_RAIL_ITEM = IdePipelineStageItem(id = "device", label = "Device configuration", icon = "device")

/** A locked stage shows no dot (it is greyed); an open one is green when configured, red with errors. */
internal fun railStatus(open: Boolean, hasError: Boolean?): RailStageStatus = when {
    !open || hasError == null -> RailStageStatus.NONE
    hasError -> RailStageStatus.ERROR
    else -> RailStageStatus.DONE
}

internal fun resolveStudioBreadcrumbs(
    uiState: WorkspaceShellUiState,
    selectedObjectId: String?,
    stageEditorRegistry: StageEditorRegistry?,
): List<String> {
    val selectedStage = uiState.pipelineItems.firstOrNull { it.isSelected }
    if (selectedStage != null && uiState.selectedWorkspaceSection == null) {
        val currentStage = stageEditorRegistry?.descriptorFor(selectedStage.stageId)
            ?: CanonicalStageDescriptors.descriptorFor(selectedStage.stageId)
        val activeObjectId = selectedObjectId ?: currentStage?.subobjects?.firstOrNull()?.id?.value
        val currentSubobject = currentStage?.subobjects?.firstOrNull { it.id.value == activeObjectId }
        return if (currentSubobject != null) {
            listOf("ROM Setup", currentStage.title, currentSubobject.title)
        } else {
            listOf("ROM Setup", selectedStage.label)
        }
    }
    val sectionName = when (uiState.selectedWorkspaceSection) {
        WorkspaceSection.Overview -> "Overview"
        WorkspaceSection.RomConfig -> "ROM Configuration"
        WorkspaceSection.RunHistory -> "Run History"
        WorkspaceSection.Artifacts -> "Artifacts"
        WorkspaceSection.TargetProfile -> "Target Profile"
        WorkspaceSection.RestorePoints -> "Restore Points"
        WorkspaceSection.Storage -> "Disk Usage"
        null -> "Overview"
    }
    return listOf("Workspace", sectionName)
}

internal fun resolveStudioPipelineStages(uiState: WorkspaceShellUiState): List<IdePipelineStageItem> {
    val device = DEVICE_RAIL_ITEM.copy(status = railStatus(open = true, hasError = uiState.deviceHasError))
    return listOf(device) + uiState.pipelineItems.map { item ->
        IdePipelineStageItem(
            id = item.stageId.name,
            label = item.label,
            icon = item.iconKey,
            enabled = item.isEnabled,
            disabledReason = item.disabledReason,
            status = railStatus(open = item.isEnabled, hasError = uiState.deviceHasError?.let { item.hasError }),
        )
    }
}

internal fun resolveHeaderTitle(
    selectedSection: WorkspaceSection?,
    stageItem: PipelineRailItemUi?,
    selectedObjectId: String?,
    registry: StageEditorRegistry?,
): String {
    if (stageItem != null && selectedSection == null) {
        val currentStage = registry?.descriptorFor(stageItem.stageId)
            ?: CanonicalStageDescriptors.descriptorFor(stageItem.stageId)
        val activeObjectId = selectedObjectId ?: currentStage?.subobjects?.firstOrNull()?.id?.value
        val currentSubobject = currentStage?.subobjects?.firstOrNull { it.id.value == activeObjectId }
        return currentSubobject?.title ?: currentStage?.title ?: stageItem.label
    }
    return when (selectedSection) {
        WorkspaceSection.Overview -> "Workspace Overview"
        WorkspaceSection.RomConfig -> "ROM Configuration"
        WorkspaceSection.RunHistory -> "Run History"
        WorkspaceSection.Artifacts -> "Generated Artifacts"
        WorkspaceSection.TargetProfile -> "Target Device Profile"
        WorkspaceSection.RestorePoints -> "Restore Points"
        WorkspaceSection.Storage -> "Disk Usage"
        null -> "Workspace Overview"
    }
}

internal fun resolveHeaderSubtitle(
    selectedSection: WorkspaceSection?,
    stageItem: PipelineRailItemUi?,
    selectedObjectId: String?,
    registry: StageEditorRegistry?,
): String? {
    if (stageItem != null && selectedSection == null) {
        val currentStage = registry?.descriptorFor(stageItem.stageId)
            ?: CanonicalStageDescriptors.descriptorFor(stageItem.stageId)
        val activeObjectId = selectedObjectId ?: currentStage?.subobjects?.firstOrNull()?.id?.value
        return resolveStageSubtitle(stageItem.stageId, activeObjectId)
    }
    return when (selectedSection) {
        WorkspaceSection.RestorePoints -> "Manage backups and restore modified or removed components."
        WorkspaceSection.Storage -> "What the workspace takes in WSL, and freeing what the next build makes again."
        else -> null
    }
}

internal fun resolveStageSubtitle(stageId: StageId, subobjectId: String?): String = when (stageId) {
    StageId.FIRMWARE_ACQUISITION -> resolveAcquireSubtitle(subobjectId)
    StageId.FIRMWARE_EXTRACTION -> resolveExtractSubtitle(subobjectId)
    StageId.WORK_TREE_ASSEMBLY -> resolveAssembleSubtitle(subobjectId)
    StageId.DEBLOAT -> "Configure apps and components to remove."
    StageId.MODULE_APPLICATION -> "Enable and configure patch modules."
    StageId.BUILD_FLASHABLE_ZIP -> "Configure output images and package signing."
    StageId.GENERATE_OTA_MANIFEST -> "Edit release information and review derived details."
    StageId.PUBLISH_RELEASE -> "Configure release export, split archives, and distribution."
}

internal fun resolveAcquireSubtitle(subobjectId: String?): String = when (subobjectId?.lowercase()) {
    "source" -> "Configure acquisition mode and target firmware source."
    "firmware-baseline", "firmware_baseline", "baseline" ->
        "Select the official firmware build to use as the foundation for this workspace build."
    "existing-archives", "existing_archives", "archives" ->
        "Manage and verify previously downloaded or cached firmware archives."
    "download-policy", "download_policy", "download" ->
        "Configure how source files are downloaded, with retry and resume behavior."
    "integrity" -> "Verify downloaded source against expected hash."
    else -> "Acquire and verify official source firmware for this target."
}

internal fun resolveExtractSubtitle(subobjectId: String?): String = when (subobjectId?.lowercase()) {
    "archive-layout", "archive_layout" ->
        "Inspect the archive layout and choose target-supported extraction inputs."
    "dynamic-partitions", "dynamic_partitions" ->
        "Choose what to extract from the verified source."
    "boot-partitions", "boot_partitions" ->
        "Choose which boot images to unpack (boot, init_boot, vendor_boot, dtbo)."
    "slot" -> "Choose which A/B slot's images to unpack."
    "filesystem-handling", "filesystem_handling" -> "How unpacked images are kept between builds."
    else -> "Extract and unpack partition images from verified firmware."
}

internal fun resolveAssembleSubtitle(subobjectId: String?): String = when (subobjectId?.lowercase()) {
    "work-trees", "work_trees" ->
        "Configure source work trees and assembly options."
    "system-ext-handling", "system_ext_handling" ->
        "Configure how system_ext is handled during assembly."
    "boot-preparation", "boot_preparation" ->
        "Configure boot image handling for the assembled build."
    "aot-cleanup", "aot_cleanup" ->
        "Configure ahead-of-time compilation cleanup and odex stripping."
    "build-properties", "build_properties" ->
        "Configure build properties and fingerprint overrides."
    else -> "Assemble work trees and staging structures for customization."
}
