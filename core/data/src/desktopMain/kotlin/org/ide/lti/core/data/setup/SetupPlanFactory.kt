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
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolCapabilities
import org.ide.lti.core.domain.setup.ToolchainSetupState

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
    ): SetupPlan {
        val actions = deriveActions(kind, targetId, autoDoctorEnabled, snapshot, bootstrapPackages)
        val revisionHash = computeRevisionHash(snapshot, autoDoctorEnabled)
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
     */
    public fun computeRevisionHash(snapshot: ToolchainSetupState, autoDoctorEnabled: Boolean): String {
        val basis = snapshot.steps.joinToString("|") { "${it.stage}=${it.status}" }
            .plus("|tools=" + snapshot.toolsMatrix.joinToString(",") { "${it.id}:${it.status}" })
            .plus("|diag=" + snapshot.diagnostics.joinToString(",") { "${it.id}:${it.status}" })
            .plus("|published=" + snapshot.publishedToolIds.sorted().joinToString(","))
            .plus("|doctor=$autoDoctorEnabled")
        return "rev-" + basis.hashCode().toUInt().toString(16)
    }

    private fun deriveActions(
        kind: SetupPlanKind,
        targetId: String?,
        autoDoctorEnabled: Boolean,
        snapshot: ToolchainSetupState,
        bootstrapPackages: List<String> = emptyList(),
    ): List<SetupPlanAction> = when (kind) {
        SetupPlanKind.FULL_SETUP -> deriveFullSetupActions(snapshot, autoDoctorEnabled)
        SetupPlanKind.STAGE_RETRY -> deriveStageRetryActions(targetId, snapshot, autoDoctorEnabled, bootstrapPackages)
        SetupPlanKind.REPAIR_TOOL -> {
            val group = targetId?.let { ToolCapabilities.recipeGroupFor(it) }
            if (group != null && ToolCapabilities.isRecipeSupported(targetId)) {
                val affectedTools = ToolCapabilities.toolsForRecipeGroup(group).toList()
                listOf(
                    SetupPlanAction.BuildRecipes(listOf(group)),
                    SetupPlanAction.PublishTools(affectedTools),
                )
            } else {
                emptyList()
            }
        }
        // A cache reset clears persisted evidence; it installs, syncs, builds and publishes nothing.
        SetupPlanKind.CACHE_RESET -> emptyList()
        SetupPlanKind.BOOTSTRAP_PACKAGES -> {
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
            add(SetupPlanAction.SyncSources(WslToolchainProvisioner.SUBMODULES.map { it.name }))
        }
        if (snapshot.stageNeedsWork(SetupStepStage.TOOLCHAIN_COMPILATION)) {
            val unbuilt = snapshot.toolsMatrix.filter { it.status != StepStatus.SUCCESS }.map { it.id }
            if (unbuilt.isNotEmpty()) {
                add(SetupPlanAction.BuildRecipes(unbuilt))
            }
        }
        add(SetupPlanAction.PublishTools(ToolCatalog.ALL_TOOL_IDS.toList()))
    }

    private fun deriveStageRetryActions(
        targetId: String?,
        snapshot: ToolchainSetupState,
        autoDoctorEnabled: Boolean,
        bootstrapPackages: List<String> = emptyList(),
    ): List<SetupPlanAction> {
        val stage = SetupStepStage.entries.firstOrNull { it.name == targetId } ?: return emptyList()
        return when (stage) {
            SetupStepStage.SYSTEM_DIAGNOSTICS -> listOfNotNull(remediationAction(snapshot, autoDoctorEnabled))
            // Synced sources are only useful built and registered: tools compiled from other sources are
            // stale, so a source retry rebuilds (changed groups only; the rest are skipped) and publishes.
            SetupStepStage.REPO_SYNCHRONIZATION -> listOf(
                SetupPlanAction.SyncSources(WslToolchainProvisioner.SUBMODULES.map { it.name }),
                SetupPlanAction.BuildRecipes(WslToolchainProvisioner.SUBMODULES.map { it.name }),
                SetupPlanAction.PublishTools(ToolCatalog.ALL_TOOL_IDS.toList()),
            )
            SetupStepStage.TOOLCHAIN_COMPILATION -> buildList {
                val unbuilt = snapshot.toolsMatrix.filter { it.status != StepStatus.SUCCESS }.map { it.id }
                if (unbuilt.isNotEmpty()) add(SetupPlanAction.BuildRecipes(unbuilt))
                val unpublished = ToolCatalog.ALL_TOOL_IDS.filter { it !in snapshot.publishedToolIds }
                if (unpublished.isNotEmpty()) add(SetupPlanAction.PublishTools(unpublished))
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
