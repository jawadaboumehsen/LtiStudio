/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.setup

/**
 * Execution-verification contract for one tool binary: which recipe group owns it, how it is
 * probed and which exit codes prove that the binary actually executes.
 *
 * [probeArguments] excludes the binary itself so the same record serves an adapter that runs a
 * resolved path (`/home/x/LtiRomTools/bin/adb version`) and one that asks the daemon to run a
 * published tool id (`adb` + `version`).
 */
data class ToolCapability(
    val toolId: String,
    val recipeGroup: String,
    val probeArguments: List<String> = ToolCapabilities.DEFAULT_PROBE_ARGUMENTS,
    val acceptedExitCodes: Set<Int> = ToolCapabilities.DEFAULT_ACCEPTED_EXIT_CODES,
) {
    /** Full argv for a resolved binary path. */
    fun probeArgv(binaryPath: String): List<String> = listOf(binaryPath) + probeArguments
}

/**
 * Authoritative capability and recipe inventory for native toolchain binaries (US4, FR-009, FR-010).
 *
 * Rewritten as a derived view of [ToolGroupCatalog] (FR-001, US7).
 */
object ToolCapabilities {

    const val GROUP_ANDROID_TOOLS: String = "android-tools"
    const val GROUP_EROFS_UTILS: String = "erofs-utils"
    const val GROUP_IMG2SDAT: String = "img2sdat"
    const val GROUP_APKTOOL: String = "apktool"
    const val GROUP_SIGNAPK: String = "signapk"
    const val GROUP_PAYLOAD_DUMPER_GO: String = "payload-dumper-go"

    /** GitHub CLI, installed from a pinned, checksum-verified release (used by the publishing adapter). */
    const val GROUP_GH: String = "gh"

    /** Shown on a tool that no setup recipe builds (e.g. `gh`): it can be tested but not rebuilt here. */
    const val REPAIR_UNAVAILABLE_REASON: String =
        "Not built by setup, so it can't be rebuilt here. Install or update it yourself; Test still works."

    val DEFAULT_PROBE_ARGUMENTS: List<String> = listOf("--help")
    val DEFAULT_ACCEPTED_EXIT_CODES: Set<Int> = setOf(0)

    /**
     * The inventory derived from [ToolGroupCatalog]. Order within a group is the verification order after a repair.
     */
    val INVENTORY: List<ToolCapability>
        get() = ToolGroupCatalog.allGroups.flatMap { group ->
            group.outputs.map { output ->
                ToolCapability(
                    toolId = output.toolId,
                    recipeGroup = group.id.value,
                    probeArguments = output.probe.args,
                    acceptedExitCodes = output.probe.acceptedExitCodes,
                )
            }
        }

    /**
     * Set of tool IDs with an automated, supported rebuild/acquisition recipe.
     */
    val SUPPORTED_TOOL_IDS: Set<String> get() = ToolGroupCatalog.allToolIds

    fun capabilityFor(toolId: String): ToolCapability? = ToolGroupCatalog.outputFor(toolId)?.let { output ->
        val group = ToolGroupCatalog.groupByToolId(toolId)
        ToolCapability(
            toolId = output.toolId,
            recipeGroup = group?.id?.value ?: "",
            probeArguments = output.probe.args,
            acceptedExitCodes = output.probe.acceptedExitCodes,
        )
    }

    fun isRecipeSupported(toolId: String): Boolean = ToolGroupCatalog.outputFor(toolId) != null

    fun unavailableReasonFor(toolId: String): String? =
        if (isRecipeSupported(toolId)) null else REPAIR_UNAVAILABLE_REASON

    fun recipeGroupFor(toolId: String): String? = ToolGroupCatalog.groupByToolId(toolId)?.id?.value

    fun toolsForRecipeGroup(recipeGroup: String): Set<String> =
        ToolGroupCatalog.group(ToolGroupId(recipeGroup))?.outputs?.map { it.toolId }?.toSet() ?: emptySet()

    fun allRecipeGroups(): Set<String> = ToolGroupCatalog.allGroups.map { it.id.value }.toSet()

    fun allRecipes(): List<RecipeCapability> = ToolGroupCatalog.allGroups.map { group ->
        RecipeCapability(
            recipeGroup = group.id.value,
            toolIds = group.outputs.map { it.toolId }.toSet(),
            isSupported = true,
        )
    }

    /** Probe arguments (without the binary) for [toolId]; the conservative default for unknown tools. */
    fun probeArgumentsFor(toolId: String): List<String> =
        ToolGroupCatalog.outputFor(toolId)?.probe?.args ?: DEFAULT_PROBE_ARGUMENTS

    /** Full probe argv for a resolved binary path. */
    fun probeArgumentsFor(toolId: String, binaryPath: String): List<String> =
        listOf(binaryPath) + probeArgumentsFor(toolId)

    fun acceptedExitCodesFor(toolId: String): Set<Int> =
        ToolGroupCatalog.outputFor(toolId)?.probe?.acceptedExitCodes ?: DEFAULT_ACCEPTED_EXIT_CODES

    /** True when [exitCode] proves that [toolId] executed. */
    fun isAcceptedExitCode(toolId: String, exitCode: Int): Boolean =
        exitCode in acceptedExitCodesFor(toolId)

    /**
     * Enforces: "Repair invalidates the owning recipe group only and verifies all affected required tools."
     */
    fun invalidateOwningGroupForRepair(
        targetToolId: String,
        allTools: Map<String, ToolEvidence>,
    ): Map<String, ToolEvidence> = invalidateForRepair(
        targetToolId = targetToolId,
        allTools = allTools,
        recipes = allRecipes(),
    )
}
