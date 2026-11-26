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

import org.ide.lti.core.domain.pipeline.configuration.DeviceConfigReadiness
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.StageSettingsValidator
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageState
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.feature.rom.studio.api.StageDescriptor

internal fun recomputeOverviewState(uiState: StudioUiState, device: TargetDevice?): WorkspaceOverviewUiState {
    val workspace = uiState.availableWorkspaces.firstOrNull { it.id == uiState.workspaceId }
    val snapshot = uiState.snapshot?.takeIf { it.workspaceId == uiState.workspaceId }
    val acquisitionErrors = uiState.validationReport.errors.any {
        it.severity == Severity.ERROR &&
            (it.stageId == StageId.FIRMWARE_ACQUISITION || it.fieldPath.startsWith("acquisition"))
    }
    val signed = snapshot?.build?.signing?.signPackage == true
    return WorkspaceOverviewMapper.map(
        workspace = workspace,
        targetDevice = device,
        draft = snapshot?.let { WorkspacePipelineDraft.create(uiState.workspaceId, it, device) },
        validationReport = uiState.validationReport,
        runs = uiState.runs,
        signingVerified = signed && signedBuildOf(uiState.runs, snapshot.id),
        hasExplicitValidSource = snapshot != null && !acquisitionErrors,
    )
}

/** The editor header's target: this workspace, its own device, and the device file's live revision. */
internal fun computeTargetContext(uiState: StudioUiState, device: TargetDevice?): TargetContextUi {
    if (uiState.workspaceId.isBlank()) return uninitializedTargetContext()
    val ws = uiState.availableWorkspaces.firstOrNull { it.id == uiState.workspaceId }
    val tb = ws?.targetBinding
    // A binding to the workspace itself is its own device: show the device, never the workspace id.
    val sharedProfile = tb?.profileId?.takeIf { it != ws.id }
    val noDevice = DisplayValue.Unavailable("No device")
    val revisionValue = (device?.revision ?: tb?.profileRevision)?.let {
        DisplayValue.Available("rev $it")
    } ?: noDevice
    return TargetContextUi(
        targetId = DisplayValue.Available(ws?.name ?: uiState.workspaceId),
        profile = (sharedProfile ?: device?.name)?.let { DisplayValue.Available(it) } ?: noDevice,
        // The device file's own edit counter is the live revision; the binding keeps the one it was made with.
        revision = revisionValue,
        draftBadge = null,
    )
}

internal fun buildWorkspaceShellState(
    currentShell: WorkspaceShellUiState,
    uiState: StudioUiState,
    device: TargetDevice?,
    descriptors: List<StageDescriptor>,
): WorkspaceShellUiState {
    val selectedStageId = (uiState.entry as? StudioEntry.Editor)?.stageId
    val items = createDefaultPipelineRailItems(selectedStageId, descriptors).map { item ->
        item.copy(hasError = uiState.validationReport.blocks(item.stageId))
    }
    val pipelineItems = lockStages(items, uiState.validationReport)
    val problemCount = uiState.validationReport.errors.size
    val targetContext = computeTargetContext(uiState, device)

    val effectiveSection = if (currentShell.globalDestination == GlobalDestination.Workspace) {
        when (uiState.entry) {
            is StudioEntry.Overview -> currentShell.selectedWorkspaceSection ?: WorkspaceSection.Overview
            is StudioEntry.Editor -> null
        }
    } else {
        null
    }

    return currentShell.copy(
        targetContext = targetContext,
        problemCount = problemCount,
        pipelineItems = pipelineItems,
        deviceHasError = uiState.validationReport.blocks(null),
        selectedWorkspaceSection = effectiveSection,
    )
}

internal fun computeRailItems(
    entry: StudioEntry? = null,
    report: ValidationReport = ValidationReport(),
    descriptors: List<StageDescriptor>,
): List<StudioRailItem> {
    val selectedStageId = (entry as? StudioEntry.Editor)?.stageId
    return descriptors.map { stage ->
        val isSelected = stage.stageId == selectedStageId
        val errors = report.errors.filter { it.stageId == stage.stageId }
        val errorCount = errors.count { it.severity == Severity.ERROR }
        val warningCount = errors.count { it.severity == Severity.WARNING }
        val statusLabel = when {
            errorCount > 0 -> "$errorCount error${if (errorCount > 1) "s" else ""}"
            warningCount > 0 -> "$warningCount warning${if (warningCount > 1) "s" else ""}"
            else -> "Valid"
        }
        StudioRailItem(
            stage = stage,
            isSelected = isSelected,
            errorCount = errorCount,
            warningCount = warningCount,
            statusLabel = statusLabel,
        )
    }
}

internal fun buildInitialShellState(workspaceId: String, descriptors: List<StageDescriptor>): WorkspaceShellUiState {
    val targetContext = if (workspaceId.isNotBlank()) {
        TargetContextUi(
            targetId = DisplayValue.Available(workspaceId),
            profile = DisplayValue.Available("Default"),
            revision = DisplayValue.Available("rev-1"),
            draftBadge = null,
        )
    } else {
        uninitializedTargetContext()
    }
    return WorkspaceShellUiState(
        globalDestination = GlobalDestination.Workspace,
        targetContext = targetContext,
        hasUnsavedChanges = false,
        validateOperation = OperationUiState.Idle,
        saveOperation = OperationUiState.Idle,
        connection = ConnectionUiState.Connected("Local Engine"),
        problemCount = 0,
        activity = ActivityUiState.Idle,
        pipelineItems = createDefaultPipelineRailItems(descriptors = descriptors),
        selectedWorkspaceSection = WorkspaceSection.Overview,
    )
}

/** The device and every stage's saved settings checked, or why they cannot be ([missing]). */
internal fun buildValidation(
    missing: String?,
    snapshot: ConfigurationSnapshot?,
    device: TargetDevice?,
): ValidationReport = if (missing != null || snapshot == null || device == null) {
    ValidationReport(
        errors = listOf(
            ValidationError(
                stageId = null,
                objectId = null,
                fieldPath = "workspace",
                code = "WORKSPACE_INCOMPLETE",
                severity = Severity.ERROR,
                message = missing ?: "Incomplete workspace",
            ),
        ),
    )
} else {
    // Publish is not part of a build (the build check leaves it out), but its stage still shows its errors.
    val publish = StageSettingsValidator.validatePublish(snapshot.publish).errors.filter {
        it.severity == Severity.ERROR
    }
    ValidationReport(errors = DeviceConfigReadiness.check(device, snapshot) + publish)
}

private fun uninitializedTargetContext(): TargetContextUi = TargetContextUi(
    targetId = DisplayValue.Unavailable("No active ROM profile selected"),
    profile = DisplayValue.Unavailable("Environment unconfigured"),
    revision = DisplayValue.Unavailable("Workspace not initialized"),
    draftBadge = null,
)

/**
 * Whether a build of these settings ([snapshotId]) signed: its Build & Sign ran (or was its cached result). A run
 * stopped earlier, or one of older settings, proves nothing about the keys.
 */
private fun signedBuildOf(runs: List<BuildRun>, snapshotId: String?): Boolean = runs.any { run ->
    run.state == RunState.SUCCEEDED && run.snapshotId == snapshotId &&
        run.stages.any {
            it.stageId == StageId.BUILD_FLASHABLE_ZIP &&
                (it.state == StageState.EXECUTED || it.state == StageState.SKIPPED)
        }
}
