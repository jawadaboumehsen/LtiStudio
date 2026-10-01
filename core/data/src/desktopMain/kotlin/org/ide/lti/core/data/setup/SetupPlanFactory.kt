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
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.ToolRef

/**
 * Factory creating authoritative [SetupPlan]s from actual recipe and diagnostic data.
 *
 * Every plan that touches tool installation produces a [SetupPlanAction.SwitchToolchain] — the
 * single content-addressable 008 action. Legacy SyncSources/BuildRecipes/PublishTools actions
 * do not exist in this factory; constructing them is a compile error.
 *
 * Package remediation is resolved by [WslPackageRemediator.resolve], the same function the
 * remediator executes, so the preview can never list something other than what runs. Frozen
 * inputs come from caller-supplied [switchInputs]; when absent, [defaultCatalogInputs] is used
 * and represents the catalog's recommended versions at their pinned commits — not live network
 * resolution. The caller is responsible for live resolution via [SourceResolverPort] before
 * passing inputs to a confirmed plan.
 *
 * Enforces:
 * - "Confirmation requires the displayed plan ID and unchanged revision hash." (FR-003, FR-004)
 * - "Auto Doctor policy is immutable for the confirmed attempt." (FR-004)
 * - No global signing action exists; a plan can never provision a machine key.
 * - [defaultCatalogInputs] throws for any group whose release catalog has no supported version.
 */
public object SetupPlanFactory {

    public fun createPlan(
        kind: SetupPlanKind,
        targetId: String? = null,
        autoDoctorEnabled: Boolean = true,
        snapshot: ToolchainSetupState,
        environmentKey: String,
        bootstrapPackages: List<String> = emptyList(),
        switchInputs: Map<ToolGroupId, ResolvedInput>? = null,
        selectionRevision: Long? = null,
    ): SetupPlan {
        val actions = deriveActions(
            kind = kind,
            targetId = targetId,
            autoDoctorEnabled = autoDoctorEnabled,
            snapshot = snapshot,
            bootstrapPackages = bootstrapPackages,
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
     * Hash of the evidence a plan was derived from: stage statuses, diagnostic statuses and the
     * Auto Doctor policy. Any change means the previewed plan is stale. When [switchInputs] and
     * [selectionRevision] are present, every frozen input and the revision are included in the hash.
     */
    public fun computeRevisionHash(
        snapshot: ToolchainSetupState,
        autoDoctorEnabled: Boolean,
        switchInputs: Map<ToolGroupId, ResolvedInput>? = null,
        selectionRevision: Long? = null,
    ): String {
        val basis = buildString {
            append(snapshot.steps.joinToString("|") { "${it.stage}=${it.status}" })
            append("|diag=").append(snapshot.diagnostics.joinToString(",") { "${it.id}:${it.status}" })
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
        switchInputs: Map<ToolGroupId, ResolvedInput>? = null,
        selectionRevision: Long? = null,
    ): List<SetupPlanAction> = when (kind) {
        SetupPlanKind.FULL_SETUP ->
            deriveFullSetup(snapshot, autoDoctorEnabled, switchInputs, selectionRevision)
        SetupPlanKind.STAGE_RETRY ->
            deriveStageRetry(targetId, snapshot, autoDoctorEnabled, bootstrapPackages, switchInputs, selectionRevision)
        SetupPlanKind.REPAIR_TOOL ->
            deriveRepairTool(targetId, switchInputs, selectionRevision)
        SetupPlanKind.CACHE_RESET -> emptyList()
        SetupPlanKind.BOOTSTRAP_PACKAGES -> deriveBootstrap(bootstrapPackages)
    }

    // -----------------------------------------------------------------------
    // Full setup: optional package remediation + a SwitchToolchain
    // -----------------------------------------------------------------------

    private fun deriveFullSetup(
        snapshot: ToolchainSetupState,
        autoDoctorEnabled: Boolean,
        switchInputs: Map<ToolGroupId, ResolvedInput>?,
        selectionRevision: Long?,
    ): List<SetupPlanAction> {
        val remediation = remediationAction(snapshot, autoDoctorEnabled)
        val inputs = switchInputs ?: defaultCatalogInputs()
        val switch = SetupPlanAction.SwitchToolchain(inputs, selectionRevision ?: 0L)
        return listOfNotNull(remediation, switch)
    }

    // -----------------------------------------------------------------------
    // Stage retry
    // -----------------------------------------------------------------------

    private fun deriveStageRetry(
        targetId: String?,
        snapshot: ToolchainSetupState,
        autoDoctorEnabled: Boolean,
        bootstrapPackages: List<String>,
        switchInputs: Map<ToolGroupId, ResolvedInput>?,
        selectionRevision: Long?,
    ): List<SetupPlanAction> = when (targetId) {
        org.ide.lti.core.domain.setup.SetupStepStage.SYSTEM_PACKAGES.name -> {
            deriveBootstrap(bootstrapPackages)
        }
        org.ide.lti.core.domain.setup.SetupStepStage.SYSTEM_DIAGNOSTICS.name -> {
            listOfNotNull(remediationAction(snapshot, autoDoctorEnabled))
        }
        // REPO_SYNCHRONIZATION and TOOLCHAIN_COMPILATION both resolve to a SwitchToolchain —
        // the 008 handler fetches sources and compiles as needed.
        org.ide.lti.core.domain.setup.SetupStepStage.REPO_SYNCHRONIZATION.name,
        org.ide.lti.core.domain.setup.SetupStepStage.TOOLCHAIN_COMPILATION.name,
        -> {
            val remediation = remediationAction(snapshot, autoDoctorEnabled)
            val inputs = switchInputs ?: defaultCatalogInputs()
            val switch = SetupPlanAction.SwitchToolchain(inputs, selectionRevision ?: 0L)
            listOfNotNull(remediation, switch)
        }
        org.ide.lti.core.domain.setup.SetupStepStage.SERVER_CONNECTIVITY.name -> emptyList()
        org.ide.lti.core.domain.setup.SetupStepStage.WSL_DETECTION.name -> emptyList()
        else -> emptyList()
    }

    // -----------------------------------------------------------------------
    // Repair tool: SwitchToolchain with forceRebuildGroups = {owning group}
    // -----------------------------------------------------------------------

    private fun deriveRepairTool(
        targetId: String?,
        switchInputs: Map<ToolGroupId, ResolvedInput>?,
        selectionRevision: Long?,
    ): List<SetupPlanAction> {
        val group = targetId?.let { tid ->
            ToolGroupCatalog.DEFAULT_GROUPS.firstOrNull { g -> g.outputs.any { it.toolId == tid } }
        } ?: return emptyList()
        val inputs = switchInputs ?: defaultCatalogInputs()
        return listOf(
            SetupPlanAction.SwitchToolchain(
                inputs = inputs,
                selectionRevision = selectionRevision ?: 0L,
                forceRebuildGroups = setOf(group.id),
            ),
        )
    }

    // -----------------------------------------------------------------------
    // Bootstrap packages only (BOOTSTRAP_PACKAGES kind)
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Default catalog inputs: pinned recommended commits from the catalog.
    //
    // These are NOT live-resolved via SourceResolverPort. The caller is
    // responsible for live resolution when presenting the plan to the user for
    // review and confirmation. defaultCatalogInputs() represents the catalog's
    // recommended version at its pinned commit — safe to use as the default
    // for unattended or fallback flows, but the UI must show resolved inputs.
    //
    // Throws IllegalStateException if any Release group has no supported version.
    // -----------------------------------------------------------------------

    public fun defaultCatalogInputs(): Map<ToolGroupId, ResolvedInput> =
        ToolGroupCatalog.DEFAULT_GROUPS.associate { group ->
            val input: ResolvedInput = when (val source = group.source) {
                is ToolSource.Git -> ResolvedInput.Git(
                    group = group.id,
                    repoUrl = source.recommendedUrl,
                    ref = ToolRef.Tag(source.recommendedLabel),
                    commit = source.recommendedCommit,
                    resolvedAt = System.currentTimeMillis(),
                )
                is ToolSource.Release -> {
                    val releaseConfig = group.recipe as? RecipeConfig.Release
                        ?: error("Release group '${group.id.value}' has no RecipeConfig.Release")
                    val version = releaseConfig.versions.firstOrNull()
                        ?: error(
                            "Release group '${group.id.value}' has no supported version in the catalog. " +
                                "Add at least one ReleaseVersion entry before using this group.",
                        )
                    ResolvedInput.Release(
                        group = group.id,
                        version = version.version,
                        url = version.url,
                        archiveMember = version.archiveMember,
                        sha256 = version.sha256,
                        platform = "linux-${detectHostArch()}",
                    )
                }
            }
            group.id to input
        }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Remediation action for the current diagnostics, or null when Auto Doctor is disabled or nothing
     * needs remediation.
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
}
