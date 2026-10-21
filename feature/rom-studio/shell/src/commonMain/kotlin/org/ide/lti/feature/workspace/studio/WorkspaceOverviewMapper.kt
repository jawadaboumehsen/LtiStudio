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

import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.target.TargetBinding
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.PublishProvider
import org.ide.lti.core.model.workspace.Workspace

private const val PRIORITY_SOURCE_BLOCKING = 10
private const val PRIORITY_SOURCE_ERROR = 15
private const val PRIORITY_DEBLOAT_ERROR = 20
private const val PRIORITY_PATCHES_ERROR = 25
private const val PRIORITY_SIGNING_ERROR = 30
private const val PRIORITY_PUBLICATION_ERROR = 35
private const val PRIORITY_SIGNING_WARNING = 50
private const val PRIORITY_SOURCE_WARNING = 60
private const val PRIORITY_DEBLOAT_WARNING = 65
private const val PRIORITY_PATCHES_WARNING = 70
private const val PRIORITY_PUBLICATION_WARNING = 75
private const val PRIORITY_DEFAULT_READY = 100

private const val OWN_DEVICE = "This workspace"
private const val RECENT_RUNS = 5

/**
 * Pure mapper transforming domain entities and validation reports into [WorkspaceOverviewUiState].
 */
public object WorkspaceOverviewMapper {

    @Suppress("LongParameterList")
    public fun map(
        workspace: Workspace? = null,
        targetDevice: TargetDevice? = null,
        draft: WorkspacePipelineDraft? = null,
        validationReport: ValidationReport = ValidationReport(),
        runs: List<BuildRun> = emptyList(),
        signingVerified: Boolean = false,
        hasExplicitValidSource: Boolean = false,
        loadState: ContentLoadState = ContentLoadState.Content,
    ): WorkspaceOverviewUiState {
        val title = workspace?.name?.takeIf { it.isNotBlank() }
            ?: workspace?.id?.takeIf { it.isNotBlank() }
            ?: ""

        val subtitle = resolveSubtitle(targetDevice, workspace)
        val targetSummary = resolveTargetSummary(workspace, targetDevice, draft)

        val readinessItems = listOf(
            resolveSourceItem(hasExplicitValidSource, validationReport),
            resolveDebloatItem(draft, validationReport),
            resolvePatchesItem(draft, validationReport),
            resolveSigningItem(signingVerified, validationReport),
            resolvePublicationItem(draft, validationReport),
        )

        val reviewCount = readinessItems.count { it.reviewRequired }
        val buildState = resolveBuildState(runs)
        val nextAction = resolveNextAction(
            readinessItems = readinessItems,
            hasTargetBinding = workspace?.targetBinding != null || targetDevice != null,
            hasExplicitValidSource = hasExplicitValidSource,
            built = buildState is BuildStateUi.Succeeded,
        )

        val recentActivity = resolveRecentActivity(runs)

        return WorkspaceOverviewUiState(
            workspaceTitle = title,
            subtitle = subtitle,
            targetSummary = targetSummary,
            readinessItems = readinessItems,
            reviewCount = reviewCount,
            nextAction = nextAction,
            buildState = buildState,
            recentActivity = recentActivity,
            loadState = loadState,
        )
    }

    private fun resolveSubtitle(targetDevice: TargetDevice?, workspace: Workspace?): String {
        val targetBinding = workspace?.targetBinding
        return when {
            targetDevice != null -> "Target: ${targetDevice.name} (${targetDevice.id})"
            targetBinding != null -> "Device configuration of this workspace"
            else -> "No target profile bound"
        }
    }

    private fun resolveTargetSummary(
        workspace: Workspace?,
        targetDevice: TargetDevice?,
        draft: WorkspacePipelineDraft?,
    ): TargetSummaryUi {
        // A binding that points at the workspace itself is its own device, not a shared profile id.
        val targetBinding = workspace?.targetBinding
        val ownDevice = targetBinding != null && targetBinding.profileId == workspace.id
        return TargetSummaryUi(
            revision = if (ownDevice && targetDevice != null) {
                DisplayValue.Available("rev ${targetDevice.revision}")
            } else {
                resolveRevision(targetBinding, targetDevice)
            },
            binding = if (ownDevice) {
                DisplayValue.Available(OWN_DEVICE)
            } else {
                resolveBinding(targetBinding, targetDevice)
            },
            deviceId = if (ownDevice) ownDeviceId(targetDevice) else resolveDeviceId(targetBinding, targetDevice),
            androidTarget = resolveAndroidTarget(draft, targetDevice),
            partitionSlot = resolvePartitionSlot(draft, targetDevice),
            buildFlavor = resolveBuildFlavor(draft),
            channel = draft?.release?.channel?.takeIf { it.isNotBlank() }
                ?.let { DisplayValue.Available(it.replaceFirstChar(Char::uppercase)) }
                ?: DisplayValue.Unavailable("Not set"),
        )
    }

    private fun ownDeviceId(targetDevice: TargetDevice?): DisplayValue =
        targetDevice?.let { DisplayValue.Available(it.id) } ?: DisplayValue.Unavailable("No device ID")

    private fun resolveRevision(targetBinding: TargetBinding?, targetDevice: TargetDevice?): DisplayValue = when {
        targetBinding != null -> DisplayValue.Available("rev ${targetBinding.profileRevision}")
        targetDevice != null -> DisplayValue.Available("rev ${targetDevice.revision}")
        else -> DisplayValue.Unavailable("No revision")
    }

    private fun resolveBinding(targetBinding: TargetBinding?, targetDevice: TargetDevice?): DisplayValue = when {
        targetBinding != null -> DisplayValue.Available(targetBinding.profileId)
        targetDevice != null -> DisplayValue.Available(targetDevice.id)
        else -> DisplayValue.Unavailable("No target bound")
    }

    private fun resolveDeviceId(targetBinding: TargetBinding?, targetDevice: TargetDevice?): DisplayValue = when {
        targetDevice != null -> DisplayValue.Available(targetDevice.id)
        targetBinding != null -> DisplayValue.Available(targetBinding.profileId)
        else -> DisplayValue.Unavailable("No device ID")
    }

    private fun resolveAndroidTarget(draft: WorkspacePipelineDraft?, targetDevice: TargetDevice?): DisplayValue {
        val draftVer = draft?.acquisition?.firmware?.androidVersion?.takeIf { it.isNotBlank() }
        val deviceVer = targetDevice?.availableFirmwares?.values?.firstOrNull()?.firstOrNull()?.androidVersion
        val version = draftVer ?: deviceVer
        return if (!version.isNullOrBlank()) {
            DisplayValue.Available("Android $version")
        } else {
            DisplayValue.Unavailable("No Android target")
        }
    }

    private fun resolvePartitionSlot(draft: WorkspacePipelineDraft?, targetDevice: TargetDevice?): DisplayValue {
        val slot = draft?.extraction?.slot?.takeIf { it.isNotBlank() }
            ?: targetDevice?.activeSlotSuffix?.takeIf { it.isNotBlank() }
        return if (slot != null) {
            DisplayValue.Available(slot)
        } else {
            DisplayValue.Unavailable("No partition slot")
        }
    }

    private fun resolveBuildFlavor(draft: WorkspacePipelineDraft?): DisplayValue {
        val flavor = draft?.assembly?.buildType?.takeIf { it.isNotBlank() }
        return if (flavor != null) {
            DisplayValue.Available(flavor)
        } else {
            DisplayValue.Unavailable("No build flavor")
        }
    }

    private fun resolveSourceItem(
        hasExplicitValidSource: Boolean,
        validationReport: ValidationReport,
    ): ReadinessItemUi {
        val errors = validationReport.errors.filter {
            it.stageId == StageId.FIRMWARE_ACQUISITION || it.fieldPath.startsWith("acquisition")
        }
        val firstError = errors.firstOrNull { it.severity == Severity.ERROR }
        val firstWarning = errors.firstOrNull { it.severity == Severity.WARNING }

        return when {
            !hasExplicitValidSource -> ReadinessItemUi(
                id = ReadinessId.SOURCE,
                title = "Source firmware",
                contextLabel = "Pipeline step 01",
                severity = ReadinessSeverity.Blocking,
                statusLabel = "Needs selection",
                description = "No source package or baseline zip assigned.",
                action = WorkspaceActionUi.OpenStage(StageId.FIRMWARE_ACQUISITION),
                reviewRequired = true,
                priority = PRIORITY_SOURCE_BLOCKING,
            )
            firstError != null -> ReadinessItemUi(
                id = ReadinessId.SOURCE,
                title = "Source firmware",
                contextLabel = "Pipeline step 01",
                severity = ReadinessSeverity.Blocking,
                statusLabel = "Source error",
                description = firstError.message,
                action = WorkspaceActionUi.OpenStage(StageId.FIRMWARE_ACQUISITION),
                reviewRequired = true,
                priority = PRIORITY_SOURCE_ERROR,
            )
            firstWarning != null -> ReadinessItemUi(
                id = ReadinessId.SOURCE,
                title = "Source firmware",
                contextLabel = "Pipeline step 01",
                severity = ReadinessSeverity.Warning,
                statusLabel = "Review source",
                description = firstWarning.message,
                action = WorkspaceActionUi.OpenStage(StageId.FIRMWARE_ACQUISITION),
                reviewRequired = true,
                priority = PRIORITY_SOURCE_WARNING,
            )
            else -> ReadinessItemUi(
                id = ReadinessId.SOURCE,
                title = "Source firmware",
                contextLabel = "Pipeline step 01",
                severity = ReadinessSeverity.Ready,
                statusLabel = "Configured",
                description = "Firmware source baseline is configured and valid.",
                action = WorkspaceActionUi.OpenStage(StageId.FIRMWARE_ACQUISITION),
                reviewRequired = false,
                priority = PRIORITY_DEFAULT_READY,
            )
        }
    }

    private fun resolveDebloatItem(
        draft: WorkspacePipelineDraft?,
        validationReport: ValidationReport,
    ): ReadinessItemUi {
        val errors = validationReport.errors.filter {
            it.stageId == StageId.DEBLOAT || it.fieldPath.startsWith("debloat")
        }
        val firstError = errors.firstOrNull { it.severity == Severity.ERROR }
        val firstWarning = errors.firstOrNull { it.severity == Severity.WARNING }
        val debloat = draft?.debloat
        val isConfigured = debloat != null && (debloat.enabled || debloat.removeSelectors.isNotEmpty())

        return when {
            firstError != null -> ReadinessItemUi(
                id = ReadinessId.DEBLOAT,
                title = "Debloat",
                contextLabel = "Pipeline step 04",
                severity = ReadinessSeverity.Blocking,
                statusLabel = "Policy error",
                description = firstError.message,
                action = WorkspaceActionUi.OpenStage(StageId.DEBLOAT),
                reviewRequired = true,
                priority = PRIORITY_DEBLOAT_ERROR,
            )
            firstWarning != null -> ReadinessItemUi(
                id = ReadinessId.DEBLOAT,
                title = "Debloat",
                contextLabel = "Pipeline step 04",
                severity = ReadinessSeverity.Warning,
                statusLabel = "Review policy",
                description = firstWarning.message,
                action = WorkspaceActionUi.OpenStage(StageId.DEBLOAT),
                reviewRequired = true,
                priority = PRIORITY_DEBLOAT_WARNING,
            )
            isConfigured && debloat != null -> {
                val label = if (debloat.removeSelectors.isNotEmpty()) {
                    "${debloat.removeSelectors.size} rules active"
                } else {
                    "Debloat enabled"
                }
                ReadinessItemUi(
                    id = ReadinessId.DEBLOAT,
                    title = "Debloat",
                    contextLabel = "Pipeline step 04",
                    severity = ReadinessSeverity.Ready,
                    statusLabel = label,
                    description = "Debloat rules and package selections are configured.",
                    action = WorkspaceActionUi.OpenStage(StageId.DEBLOAT),
                    reviewRequired = false,
                    priority = PRIORITY_DEFAULT_READY,
                )
            }
            else -> ReadinessItemUi(
                id = ReadinessId.DEBLOAT,
                title = "Debloat",
                contextLabel = "Pipeline step 04",
                severity = ReadinessSeverity.Neutral,
                statusLabel = "No removals configured",
                description = "Using stock package list with default keep rules.",
                action = WorkspaceActionUi.OpenStage(StageId.DEBLOAT),
                reviewRequired = true,
                priority = PRIORITY_DEFAULT_READY,
            )
        }
    }

    private fun resolvePatchesItem(
        draft: WorkspacePipelineDraft?,
        validationReport: ValidationReport,
    ): ReadinessItemUi {
        val errors = validationReport.errors.filter {
            it.stageId == StageId.MODULE_APPLICATION || it.fieldPath.startsWith("customization")
        }
        val firstError = errors.firstOrNull { it.severity == Severity.ERROR }
        val firstWarning = errors.firstOrNull { it.severity == Severity.WARNING }
        val customization = draft?.customization
        val hasCustomPackages = customization?.enabledPackages?.isNotEmpty() == true
        val hasLockfileEntries = customization?.lockfile?.entries?.isNotEmpty() == true
        val hasPatches = hasCustomPackages || hasLockfileEntries

        return when {
            firstError != null -> ReadinessItemUi(
                id = ReadinessId.PATCHES,
                title = "Patches",
                contextLabel = "Pipeline step 05",
                severity = ReadinessSeverity.Blocking,
                statusLabel = "Patch error",
                description = firstError.message,
                action = WorkspaceActionUi.OpenStage(StageId.MODULE_APPLICATION),
                reviewRequired = true,
                priority = PRIORITY_PATCHES_ERROR,
            )
            firstWarning != null -> ReadinessItemUi(
                id = ReadinessId.PATCHES,
                title = "Patches",
                contextLabel = "Pipeline step 05",
                severity = ReadinessSeverity.Warning,
                statusLabel = "Review patches",
                description = firstWarning.message,
                action = WorkspaceActionUi.OpenStage(StageId.MODULE_APPLICATION),
                reviewRequired = true,
                priority = PRIORITY_PATCHES_WARNING,
            )
            hasPatches && customization != null -> {
                val count = customization.enabledPackages.size.coerceAtLeast(customization.lockfile.entries.size)
                ReadinessItemUi(
                    id = ReadinessId.PATCHES,
                    title = "Patches",
                    contextLabel = "Pipeline step 05",
                    severity = ReadinessSeverity.Ready,
                    statusLabel = "$count patches enabled",
                    description = "Customization modules and patches are resolved and enabled.",
                    action = WorkspaceActionUi.OpenStage(StageId.MODULE_APPLICATION),
                    reviewRequired = false,
                    priority = PRIORITY_DEFAULT_READY,
                )
            }
            else -> ReadinessItemUi(
                id = ReadinessId.PATCHES,
                title = "Patches",
                contextLabel = "Pipeline step 05",
                severity = ReadinessSeverity.Neutral,
                statusLabel = "No modules enabled",
                description = "No plugins are enabled for this build.",
                action = WorkspaceActionUi.OpenStage(StageId.MODULE_APPLICATION),
                reviewRequired = false,
                priority = PRIORITY_DEFAULT_READY,
            )
        }
    }

    private fun resolveSigningItem(signingVerified: Boolean, validationReport: ValidationReport): ReadinessItemUi {
        val errors = validationReport.errors.filter {
            it.stageId == StageId.BUILD_FLASHABLE_ZIP ||
                it.fieldPath.startsWith("build.signing") ||
                it.fieldPath.startsWith("signing")
        }
        val firstError = errors.firstOrNull { it.severity == Severity.ERROR }
        return when {
            firstError != null -> ReadinessItemUi(
                id = ReadinessId.SIGNING,
                title = "Signing key",
                contextLabel = "Security context",
                severity = ReadinessSeverity.Blocking,
                statusLabel = "Key error",
                description = firstError.message,
                action = WorkspaceActionUi.OpenStage(StageId.BUILD_FLASHABLE_ZIP, "package-signing"),
                reviewRequired = true,
                priority = PRIORITY_SIGNING_ERROR,
            )
            !signingVerified -> ReadinessItemUi(
                id = ReadinessId.SIGNING,
                title = "Signing key",
                contextLabel = "Security context",
                severity = ReadinessSeverity.Warning,
                statusLabel = "Key required",
                description = "Release and AVB keypair not verified for production.",
                action = WorkspaceActionUi.OpenStage(StageId.BUILD_FLASHABLE_ZIP, "package-signing"),
                reviewRequired = true,
                priority = PRIORITY_SIGNING_WARNING,
            )
            else -> ReadinessItemUi(
                id = ReadinessId.SIGNING,
                title = "Signing key",
                contextLabel = "Security context",
                severity = ReadinessSeverity.Ready,
                statusLabel = "Key verified",
                description = "AVB and release signing keys are verified and ready for production packaging.",
                action = WorkspaceActionUi.OpenStage(StageId.BUILD_FLASHABLE_ZIP, "package-signing"),
                reviewRequired = false,
                priority = PRIORITY_DEFAULT_READY,
            )
        }
    }

    private fun resolvePublicationItem(
        draft: WorkspacePipelineDraft?,
        validationReport: ValidationReport,
    ): ReadinessItemUi {
        val errors = validationReport.errors.filter {
            it.stageId == StageId.PUBLISH_RELEASE || it.fieldPath.startsWith("publish")
        }
        val firstError = errors.firstOrNull { it.severity == Severity.ERROR }
        val firstWarning = errors.firstOrNull { it.severity == Severity.WARNING }
        val publish = draft?.publish
        return when {
            firstError != null -> ReadinessItemUi(
                id = ReadinessId.PUBLICATION,
                title = "Publication",
                contextLabel = "Pipeline step 08",
                severity = ReadinessSeverity.Blocking,
                statusLabel = "Publish error",
                description = firstError.message,
                action = WorkspaceActionUi.OpenStage(StageId.PUBLISH_RELEASE),
                reviewRequired = true,
                priority = PRIORITY_PUBLICATION_ERROR,
            )
            firstWarning != null -> ReadinessItemUi(
                id = ReadinessId.PUBLICATION,
                title = "Publication",
                contextLabel = "Pipeline step 08",
                severity = ReadinessSeverity.Warning,
                statusLabel = "Review publication",
                description = firstWarning.message,
                action = WorkspaceActionUi.OpenStage(StageId.PUBLISH_RELEASE),
                reviewRequired = true,
                priority = PRIORITY_PUBLICATION_WARNING,
            )
            publish != null && publish.provider != PublishProvider.NONE -> ReadinessItemUi(
                id = ReadinessId.PUBLICATION,
                title = "Publication",
                contextLabel = "Pipeline step 08",
                severity = ReadinessSeverity.Ready,
                statusLabel = if (publish.provider == PublishProvider.GITHUB_RELEASE) {
                    "GitHub release"
                } else {
                    "Local export"
                },
                description = when (publish.provider) {
                    PublishProvider.GITHUB_RELEASE -> "Publishes to github.com/${publish.repository.orEmpty()}."
                    else -> "Exports to ${publish.localExportFolder.orEmpty()}."
                },
                action = WorkspaceActionUi.OpenStage(StageId.PUBLISH_RELEASE),
                reviewRequired = false,
                priority = PRIORITY_DEFAULT_READY,
            )
            else -> ReadinessItemUi(
                id = ReadinessId.PUBLICATION,
                title = "Publication",
                contextLabel = "Pipeline step 08",
                severity = ReadinessSeverity.Neutral,
                statusLabel = "Optional until build completes",
                description = "Choose where to publish once a build is ready.",
                action = WorkspaceActionUi.OpenStage(StageId.PUBLISH_RELEASE),
                reviewRequired = false,
                priority = PRIORITY_DEFAULT_READY,
            )
        }
    }

    private fun resolveNextAction(
        readinessItems: List<ReadinessItemUi>,
        hasTargetBinding: Boolean,
        hasExplicitValidSource: Boolean,
        built: Boolean = false,
    ): WorkspaceActionUi? {
        val unresolvedItem = readinessItems
            .filter { it.reviewRequired && it.action != null }
            .minByOrNull { it.priority }

        return when {
            !hasExplicitValidSource ->
                WorkspaceActionUi.OpenStage(StageId.FIRMWARE_ACQUISITION)
            !hasTargetBinding ->
                WorkspaceActionUi.OpenWorkspaceSection(WorkspaceSection.TargetProfile)
            unresolvedItem != null ->
                unresolvedItem.action
            // Everything is set: build it, or publish what the last build produced.
            built -> WorkspaceActionUi.OpenStage(StageId.PUBLISH_RELEASE, "publish-review-and-recovery")
            else -> WorkspaceActionUi.OpenWorkspaceSection(WorkspaceSection.RunHistory)
        }
    }

    private fun resolveBuildState(runs: List<BuildRun>): BuildStateUi {
        val latestRun = runs.firstOrNull() ?: return BuildStateUi.NotStarted
        return when (latestRun.state) {
            RunState.QUEUED, RunState.RUNNING, RunState.PAUSED_WAITING_FOR_APP ->
                BuildStateUi.InProgress(
                    runId = latestRun.id,
                    stageLabel = latestRun.stages.lastOrNull()?.stageId?.let(::stageTitle) ?: "Building",
                    progress = null,
                )
            RunState.SUCCEEDED ->
                BuildStateUi.Succeeded(
                    runId = latestRun.id,
                    completedAt = latestRun.endedAt?.toString() ?: "",
                    artifactCount = latestRun.artifacts.size,
                )
            RunState.FAILED, RunState.CANCELLED, RunState.INTERRUPTED ->
                BuildStateUi.Failed(
                    runId = latestRun.id,
                    failedAt = latestRun.endedAt?.toString() ?: "",
                    stageLabel = latestRun.stages.lastOrNull()?.stageId?.let(::stageTitle) ?: "Pipeline",
                    errorMessage = latestRun.stages.lastOrNull()?.message ?: "Build failed",
                )
            RunState.CANCELLING ->
                BuildStateUi.InProgress(
                    runId = latestRun.id,
                    stageLabel = "Cancelling",
                    progress = null,
                )
        }
    }

    private fun stageTitle(id: StageId): String =
        org.ide.lti.feature.rom.studio.api.CanonicalStageDescriptors.ALL.firstOrNull { it.stageId == id }?.title
            ?: id.name

    private fun resolveRecentActivity(runs: List<BuildRun>): List<ActivityItemUi> = runs.take(RECENT_RUNS).map { run ->
        ActivityItemUi(
            id = run.id,
            title = "Build ${run.state.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }}",
            timestamp = run.startedAt.toString().substringBefore('.').replace('T', ' ') + " UTC",
            description = run.stages.lastOrNull()?.let { "Last stage: ${stageTitle(it.stageId)}" } ?: "No stage ran",
            action = WorkspaceActionUi.OpenWorkspaceSection(WorkspaceSection.RunHistory),
        )
    }
}
