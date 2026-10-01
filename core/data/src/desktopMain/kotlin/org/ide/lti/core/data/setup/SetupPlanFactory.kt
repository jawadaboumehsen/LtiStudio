/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import org.ide.lti.core.data.setup.doctor.WslPackageRemediator
import org.ide.lti.core.data.setup.install.detectHostArch
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolCapabilities
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.ToolRef

/**
 * Factory creating authoritative [SetupPlan]s from actual recipe and diagnostic data.
 *
 * Package remediation is resolved by [WslPackageRemediator.resolve], the same function the remediator
 * executes, so the preview can never list something other than what runs. Submodule and recipe
 * identity come from the provisioner's real catalog ([WslToolchainProvisioner.SUBMODULES] and the
 * tools matrix), never from a parallel list.
 *
 * Enforces:
 * - "Confirmation requires the displayed plan ID and unchanged revision hash." (FR-003, FR-004)
 * - "Auto Doctor policy is immutable for the confirmed attempt." (FR-004)
 * - Package/elevation/publication preview and execution both derive from this one immutable plan.
 * - No global signing action exists; a plan can never provision a machine key.
 */
public object SetupPlanFactory {

    public fun createPlan(
        kind: SetupPlanKind,
        targetId: String? = null,
        autoDoctorEnabled: Boolean = true,
        snapshot: ToolchainSetupState,
        environmentKey: String,
        bootstrapPackages: List<String> = emptyList(),
        toolchainLayoutV2: Boolean = false,
        switchInputs: Map<ToolGroupId, ResolvedInput>? = null,
        selectionRevision: Long? = null,
    ): SetupPlan {
        val actions = deriveActions(
            kind = kind,
            targetId = targetId,
            autoDoctorEnabled = autoDoctorEnabled,
            snapshot = snapshot,
            bootstrapPackages = bootstrapPackages,
            toolchainLayoutV2 = toolchainLayoutV2,
            switchInputs = switchInputs,
            selectionRevision = selectionRevision,
        )
        val switchAction = actions.filterIsInstance<SetupPlanAction.SwitchToolchain>().firstOrNull()
        val revisionHash = computeRevisionHash(
            snapshot = snapshot,
            autoDoctorEnabled = autoDoctorEnabled,
            switchInputs = switchAction?.inputs,
            selectionRevision = switchAction?.selectionRevision,
        )
        return SetupPlan(
            planId = "plan-${kind.name.lowercase()}-${System.currentTimeMillis()}",
            revisionHash = revisionHash,
            environmentKey = environmentKey,
            kind = kind,
            targetStageOrToolId = targetId,
            autoDoctorEnabled = autoDoctorEnabled,
            orderedActions = actions,
            prerequisiteEvidenceRevision = snapshot.lastVerifiedTimestamp?.toString(),
        )
    }

    /**
     * Hash of the evidence a plan was derived from: stage statuses, tool statuses, diagnostic
     * statuses and the Auto Doctor policy. Any change means the previewed plan is stale.
     * When [switchInputs] and [selectionRevision] are present, every frozen input and the revision
     * are included in the hash.
     */
    public fun computeRevisionHash(
        snapshot: ToolchainSetupState,
        autoDoctorEnabled: Boolean,
        switchInputs: Map<ToolGroupId, ResolvedInput>? = null,
        selectionRevision: Long? = null,
    ): String {
        val basis = buildString {
            append(snapshot.steps.joinToString("|") { "${it.stage}=${it.status}" })
            append("|tools=").append(snapshot.toolsMatrix.joinToString(",") { "${it.id}:${it.status}" })
            append("|diag=").append(snapshot.diagnostics.joinToString(",") { "${it.id}:${it.status}" })
            append("|published=").append(snapshot.publishedToolIds.sorted().joinToString(","))
            append("|doctor=").append(autoDoctorEnabled)
            if (switchInputs != null && selectionRevision != null) {
                val sortedEntries = switchInputs.entries.sortedBy { it.key.value }
                val inputsPart = sortedEntries.joinToString(",") { (groupId, input) ->
                    val resolvedStr = when (input) {
                        is ResolvedInput.Git -> "${input.repoUrl ?: "default"}@${input.commit}"
                        is ResolvedInput.Release -> "${input.version}:${input.sha256}"
                    }
                    "${groupId.value}=$resolvedStr"
                }
                append("|switch=").append(inputsPart)
                append("|selectionRevision=").append(selectionRevision)
            }
        }
        return "rev-" + basis.hashCode().toUInt().toString(16)
    }

    private fun deriveActions(
        kind: SetupPlanKind,
        targetId: String?,
        autoDoctorEnabled: Boolean,
        snapshot: ToolchainSetupState,
        bootstrapPackages: List<String> = emptyList(),
        toolchainLayoutV2: Boolean = false,
        switchInputs: Map<ToolGroupId, ResolvedInput>? = null,
        selectionRevision: Long? = null,
    ): List<SetupPlanAction> = when (kind) {
        SetupPlanKind.FULL_SETUP ->
            deriveFullSetup(snapshot, autoDoctorEnabled, toolchainLayoutV2, switchInputs, selectionRevision)
        SetupPlanKind.STAGE_RETRY ->
            deriveStageRetry(
                targetId,
                snapshot,
                autoDoctorEnabled,
                bootstrapPackages,
                toolchainLayoutV2,
                switchInputs,
                selectionRevision,
            )
        SetupPlanKind.REPAIR_TOOL ->
            deriveRepairTool(targetId, toolchainLayoutV2, switchInputs, selectionRevision)
        SetupPlanKind.CACHE_RESET -> emptyList()
        SetupPlanKind.BOOTSTRAP_PACKAGES -> deriveBootstrap(bootstrapPackages)
    }

    private fun deriveFullSetup(
        snapshot: ToolchainSetupState,
        autoDoctorEnabled: Boolean,
        toolchainLayoutV2: Boolean,
        switchInputs: Map<ToolGroupId, ResolvedInput>?,
        selectionRevision: Long?,
    ): List<SetupPlanAction> {
        if (!toolchainLayoutV2) return deriveFullSetupActions(snapshot, autoDoctorEnabled)
        val remediation = remediationAction(snapshot, autoDoctorEnabled)
        val inputs = switchInputs ?: defaultCatalogInputs()
        val switch = SetupPlanAction.SwitchToolchain(inputs, selectionRevision ?: 0L)
        return listOfNotNull(remediation, switch)
    }

    private fun deriveStageRetry(
        targetId: String?,
        snapshot: ToolchainSetupState,
        autoDoctorEnabled: Boolean,
        bootstrapPackages: List<String>,
        toolchainLayoutV2: Boolean,
        switchInputs: Map<ToolGroupId, ResolvedInput>?,
        selectionRevision: Long?,
    ): List<SetupPlanAction> {
        val isCompilationRetry = targetId == SetupStepStage.TOOLCHAIN_COMPILATION.name ||
            targetId == SetupStepStage.REPO_SYNCHRONIZATION.name
        if (!toolchainLayoutV2 || !isCompilationRetry) {
            return deriveStageRetryActions(targetId, snapshot, autoDoctorEnabled, bootstrapPackages)
        }
        val remediation = remediationAction(snapshot, autoDoctorEnabled)
        val inputs = switchInputs ?: defaultCatalogInputs()
        val switch = SetupPlanAction.SwitchToolchain(inputs, selectionRevision ?: 0L)
        return listOfNotNull(remediation, switch)
    }

    private fun deriveRepairTool(
        targetId: String?,
        toolchainLayoutV2: Boolean,
        switchInputs: Map<ToolGroupId, ResolvedInput>?,
        selectionRevision: Long?,
    ): List<SetupPlanAction> {
        val group = targetId?.let { ToolCapabilities.recipeGroupFor(it) } ?: targetId
        val isSupported = targetId == null || ToolCapabilities.isRecipeSupported(targetId)
        if (group == null || !isSupported) return emptyList()
        return if (toolchainLayoutV2) {
            val inputs = switchInputs ?: defaultCatalogInputs()
            listOf(
                SetupPlanAction.SwitchToolchain(
                    inputs = inputs,
                    selectionRevision = selectionRevision ?: 0L,
                    forceRebuildGroups = setOf(ToolGroupId(group)),
                ),
            )
        } else {
            val affectedTools = ToolCapabilities.toolsForRecipeGroup(group).toList()
            listOf(
                SetupPlanAction.BuildRecipes(listOf(group)),
                SetupPlanAction.PublishTools(affectedTools),
            )
        }
    }

    private fun deriveBootstrap(bootstrapPackages: List<String>): List<SetupPlanAction> =
        if (bootstrapPackages.isNotEmpty()) {
            listOf(
                SetupPlanAction.InstallPackages(
                    packages = bootstrapPackages,
                    pipPackages = emptyList(),
                    configureLoopMountElevation = false,
                ),
            )
        } else {
            emptyList()
        }

    public fun defaultCatalogInputs(): Map<ToolGroupId, ResolvedInput> =
        ToolGroupCatalog.DEFAULT_GROUPS.associate { group ->
            val input: ResolvedInput = when (val source = group.source) {
                is ToolSource.Git -> ResolvedInput.Git(
                    group = group.id,
                    repoUrl = null,
                    ref = ToolRef.Tag(source.recommendedLabel),
                    commit = source.recommendedCommit,
                    resolvedAt = System.currentTimeMillis(),
                )
                is ToolSource.Release -> {
                    val releaseConfig = group.recipe as? RecipeConfig.Release
                    val version = releaseConfig?.versions?.firstOrNull()
                    ResolvedInput.Release(
                        group = group.id,
                        version = version?.version ?: "default",
                        url = version?.url ?: "",
                        archiveMember = version?.archiveMember ?: "",
                        sha256 = version?.sha256 ?: "",
                        platform = detectHostArch(),
                    )
                }
            }
            group.id to input
        }

    private fun ToolchainSetupState.stageNeedsWork(stage: SetupStepStage): Boolean =
        steps.firstOrNull { it.stage == stage }?.status != StepStatus.SUCCESS

    /**
     * Remediation action for the current diagnostics, or null when Auto Doctor is disabled or nothing
     * needs remediation. Toolchain binary synchronisation is owned by the build/publish actions, so it
     * is never part of the remediation preview.
     */
    private fun remediationAction(
        snapshot: ToolchainSetupState,
        autoDoctorEnabled: Boolean,
    ): SetupPlanAction.InstallPackages? {
        if (!autoDoctorEnabled) return null
        val remediation = WslPackageRemediator.resolve(snapshot.diagnostics)
        val hasPackageWork = remediation.aptPackages.isNotEmpty() ||
            remediation.pipPackages.isNotEmpty() ||
            remediation.configureLoopMountElevation
        return if (hasPackageWork) {
            SetupPlanAction.InstallPackages(
                packages = remediation.aptPackages,
                pipPackages = remediation.pipPackages,
                configureLoopMountElevation = remediation.configureLoopMountElevation,
            )
        } else {
            null
        }
    }

    private fun deriveFullSetupActions(
        snapshot: ToolchainSetupState,
        autoDoctorEnabled: Boolean,
    ): List<SetupPlanAction> = buildList {
        if (snapshot.stageNeedsWork(SetupStepStage.SYSTEM_DIAGNOSTICS)) {
            remediationAction(snapshot, autoDoctorEnabled)?.let(::add)
        }
        if (snapshot.stageNeedsWork(SetupStepStage.REPO_SYNCHRONIZATION)) {
            // A sync can change sources, so the build follows it whatever the Toolchain looked like before.
            addAll(syncBuildPublish())
            return@buildList
        }
        if (snapshot.stageNeedsWork(SetupStepStage.TOOLCHAIN_COMPILATION)) {
            val unbuilt = snapshot.toolsMatrix.filter { it.status != StepStatus.SUCCESS }.map { it.id }
            if (unbuilt.isNotEmpty()) {
                add(SetupPlanAction.BuildRecipes(unbuilt))
            }
        }
        add(SetupPlanAction.PublishTools(ToolCatalog.ALL_TOOL_IDS.toList()))
    }

    /**
     * Sources, then their build, then registration. Synced sources are only useful built and registered, and
     * a sync invalidates the groups it changed; unchanged groups are skipped by the build.
     */
    private fun syncBuildPublish(): List<SetupPlanAction> = listOf(
        SetupPlanAction.SyncSources(WslToolchainProvisioner.SUBMODULES.map { it.name }),
        SetupPlanAction.BuildRecipes(WslToolchainProvisioner.SUBMODULES.map { it.name }),
        SetupPlanAction.PublishTools(ToolCatalog.ALL_TOOL_IDS.toList()),
    )

    private fun deriveStageRetryActions(
        targetId: String?,
        snapshot: ToolchainSetupState,
        autoDoctorEnabled: Boolean,
        bootstrapPackages: List<String> = emptyList(),
    ): List<SetupPlanAction> {
        val stage = SetupStepStage.entries.firstOrNull { it.name == targetId } ?: return emptyList()
        return when (stage) {
            SetupStepStage.SYSTEM_DIAGNOSTICS -> listOfNotNull(remediationAction(snapshot, autoDoctorEnabled))
            SetupStepStage.REPO_SYNCHRONIZATION -> syncBuildPublish()
            // Nothing can be built from sources that are not there: a clean machine gets the whole chain.
            SetupStepStage.TOOLCHAIN_COMPILATION -> if (snapshot.stageNeedsWork(SetupStepStage.REPO_SYNCHRONIZATION)) {
                syncBuildPublish()
            } else {
                buildList {
                    val unbuilt = snapshot.toolsMatrix.filter { it.status != StepStatus.SUCCESS }.map { it.id }
                    if (unbuilt.isNotEmpty()) add(SetupPlanAction.BuildRecipes(unbuilt))
                    val unpublished = ToolCatalog.ALL_TOOL_IDS.filter { it !in snapshot.publishedToolIds }
                    if (unpublished.isNotEmpty()) add(SetupPlanAction.PublishTools(unpublished))
                }
            }
            SetupStepStage.SERVER_CONNECTIVITY -> listOf(
                SetupPlanAction.PublishTools(emptyList()),
            )
            SetupStepStage.SYSTEM_PACKAGES -> {
                if (bootstrapPackages.isNotEmpty()) {
                    listOf(
                        SetupPlanAction.InstallPackages(
                            packages = bootstrapPackages,
                            pipPackages = emptyList(),
                            configureLoopMountElevation = false,
                        ),
                    )
                } else {
                    emptyList()
                }
            }
            SetupStepStage.WSL_DETECTION -> emptyList()
        }
    }
}
