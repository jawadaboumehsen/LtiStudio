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

import org.ide.lti.core.data.setup.install.detectHostArch
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.ToolGroup
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ToolManager

/**
 * Factory creating authoritative [SetupPlan]s from actual recipe and package data.
 *
 * Every plan that touches tool installation produces a [SetupPlanAction.SwitchToolchain] — the
 * component-tool action. The component manager owns source preparation, acquisition, and selection.
 *
 * Frozen inputs come from caller-supplied [switchInputs]; when absent, [defaultCatalogInputs] is used
 * and represents the catalog's recommended versions at their pinned commits — not live network
 * resolution.
 */
public object SetupPlanFactory {
    private val planSequence = java.util.concurrent.atomic.AtomicLong(0)

    public fun createPlan(
        kind: SetupPlanKind,
        targetId: String? = null,
        snapshot: ToolchainSetupState,
        environmentKey: String,
        bootstrapPackages: List<String> = emptyList(),
        switchInputs: Map<ToolGroupId, ResolvedInput>? = null,
        selectionRevision: Long? = null,
    ): SetupPlan {
        val actions =
            deriveActions(
                kind = kind,
                targetId = targetId,
                bootstrapPackages = bootstrapPackages,
                switchInputs = switchInputs,
                selectionRevision = selectionRevision,
            )
        val switchAction = actions.filterIsInstance<SetupPlanAction.SwitchToolchain>().firstOrNull()
        val revisionHash =
            computeRevisionHash(
                snapshot = snapshot,
                switchInputs = switchAction?.inputs,
                selectionRevision = switchAction?.selectionRevision,
            )
        return SetupPlan(
            planId = "plan-${kind.name.lowercase()}-${System.currentTimeMillis()}-${planSequence.incrementAndGet()}",
            revisionHash = revisionHash,
            environmentKey = environmentKey,
            kind = kind,
            targetStageOrToolId = targetId,
            orderedActions = actions,
            prerequisiteEvidenceRevision = snapshot.lastVerifiedTimestamp?.toString(),
        )
    }

    /**
     * Hash of the evidence a plan was derived from: stage statuses and inputs.
     */
    public fun computeRevisionHash(
        snapshot: ToolchainSetupState,
        switchInputs: Map<ToolGroupId, ResolvedInput>? = null,
        selectionRevision: Long? = null,
    ): String {
        val basis =
            buildString {
                append(snapshot.steps.joinToString("|") { "${it.stage}=${it.status}" })
                if (switchInputs != null && selectionRevision != null) {
                    val sortedEntries = switchInputs.entries.sortedBy { it.key.value }
                    val inputsPart =
                        sortedEntries.joinToString(",") { (groupId, input) ->
                            val resolvedStr =
                                when (input) {
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
        bootstrapPackages: List<String> = emptyList(),
        switchInputs: Map<ToolGroupId, ResolvedInput>? = null,
        selectionRevision: Long? = null,
    ): List<SetupPlanAction> = when (kind) {
        SetupPlanKind.FULL_SETUP ->
            deriveFullSetup(switchInputs, selectionRevision)
        SetupPlanKind.STAGE_RETRY ->
            deriveStageRetry(targetId, bootstrapPackages, switchInputs, selectionRevision)
        SetupPlanKind.REPAIR_TOOL ->
            deriveRepairTool(targetId, switchInputs, selectionRevision)
        SetupPlanKind.CACHE_RESET -> emptyList()
        SetupPlanKind.BOOTSTRAP_PACKAGES -> deriveBootstrap(bootstrapPackages)
    }

    // -----------------------------------------------------------------------
    // Full setup: optional package remediation + a SwitchToolchain
    // -----------------------------------------------------------------------

    private fun deriveFullSetup(
        switchInputs: Map<ToolGroupId, ResolvedInput>?,
        selectionRevision: Long?,
    ): List<SetupPlanAction> {
        val inputs = switchInputs ?: defaultCatalogInputs()
        val switch = SetupPlanAction.SwitchToolchain(inputs, selectionRevision ?: 0L)
        return listOf(switch)
    }

    // -----------------------------------------------------------------------
    // Stage retry
    // -----------------------------------------------------------------------

    private fun deriveStageRetry(
        targetId: String?,
        bootstrapPackages: List<String>,
        switchInputs: Map<ToolGroupId, ResolvedInput>?,
        selectionRevision: Long?,
    ): List<SetupPlanAction> = when (targetId) {
        org.ide.lti.core.domain.setup.SetupStepStage.SYSTEM_PACKAGES.name -> {
            deriveBootstrap(bootstrapPackages)
        }
        org.ide.lti.core.domain.setup.SetupStepStage.TOOLCHAIN_COMPILATION.name -> {
            val inputs = switchInputs ?: defaultCatalogInputs()
            val switch = SetupPlanAction.SwitchToolchain(inputs, selectionRevision ?: 0L)
            listOf(switch)
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
        val group =
            targetId?.let { tid ->
                ToolGroupCatalog.allGroups.firstOrNull { g -> g.outputs.any { it.toolId == tid } }
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
                ),
            )
        } else {
            emptyList()
        }

    // -----------------------------------------------------------------------
    // Default catalog inputs: pinned recommended commits from the catalog.
    // -----------------------------------------------------------------------

    public fun defaultCatalogInputs(): Map<ToolGroupId, ResolvedInput> = ToolGroupCatalog.allGroups.associate { group ->
        val input: ResolvedInput =
            when (val source = group.source) {
                is ToolSource.Git ->
                    ResolvedInput.Git(
                        group = group.id,
                        repoUrl = source.recommendedUrl,
                        ref = source.pinnedRef,
                        commit = source.pinnedCommit,
                        resolvedAt = System.currentTimeMillis(),
                    )
                is ToolSource.Release -> {
                    val releaseConfig =
                        group.recipe as? RecipeConfig.Release
                            ?: error("Release group '${group.id.value}' has no RecipeConfig.Release")
                    val version =
                        releaseConfig.versions.firstOrNull()
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

    public fun ResolvedSource.toResolvedInput(group: ToolGroup): ResolvedInput = when (this) {
        is ResolvedSource.Git ->
            ResolvedInput.Git(
                group = group.id,
                repoUrl = repoUrl,
                ref = ref,
                commit = commit,
                submoduleCommits = submodules,
                resolvedAt = System.currentTimeMillis(),
            )
        is ResolvedSource.Release ->
            ResolvedInput.Release(
                group = group.id,
                version = version,
                url = url,
                archiveMember = member,
                sha256 = sha256,
                platform = "linux-${detectHostArch()}",
            )
    }

    /**
     * Resolve switch inputs from ToolManager's authoritative target selection.
     */
    public fun toolManagerTargets(toolManager: ToolManager?): Map<ToolGroupId, ResolvedInput> {
        if (toolManager == null) return defaultCatalogInputs()
        return ToolGroupCatalog.allGroups.associate { group ->
            val target = toolManager.targetSourceForGroup(group)
            group.id to target.toResolvedInput(group)
        }
    }
}
