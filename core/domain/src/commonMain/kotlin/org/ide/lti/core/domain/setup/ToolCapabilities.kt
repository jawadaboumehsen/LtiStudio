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
public data class ToolCapability(
    val toolId: String,
    val recipeGroup: String,
    val probeArguments: List<String> = ToolCapabilities.DEFAULT_PROBE_ARGUMENTS,
    val acceptedExitCodes: Set<Int> = ToolCapabilities.DEFAULT_ACCEPTED_EXIT_CODES,
) {
    /** Full argv for a resolved binary path. */
    public fun probeArgv(binaryPath: String): List<String> = listOf(binaryPath) + probeArguments
}

/**
 * Authoritative capability and recipe inventory for native toolchain binaries (US4, FR-009, FR-010).
 *
 * Consumed by UI availability (ToolBrowserState), adapter execution (ToolchainBuildEngine,
 * WslToolchainProvisioner) and readiness probing (EnvironmentReadinessAdapter) — one source, no
 * duplicated tables.
 *
 * Enforces:
 * - "Repair invalidates the owning recipe group only and verifies all affected required tools."
 * - "Repair requires a supported recipe; otherwise the action is unavailable with a reason and performs no mutation."
 * - "Domain tool capabilities define probes and acceptable exit codes; a file existence scan never
 *    overwrites a failed execution test or labels it Just tested."
 *
 * Probe defaults are the conservative `--help` / exit 0 contract the adapters already used; tools
 * whose CLI is known to behave differently carry an explicit override below.
 */
public object ToolCapabilities {

    public const val GROUP_ANDROID_TOOLS: String = "android-tools"
    public const val GROUP_EROFS_UTILS: String = "erofs-utils"
    public const val GROUP_IMG2SDAT: String = "img2sdat"
    public const val GROUP_APKTOOL: String = "apktool"
    public const val GROUP_SIGNAPK: String = "signapk"
    public const val GROUP_PAYLOAD_DUMPER_GO: String = "payload-dumper-go"

    /** GitHub CLI, installed from a pinned, checksum-verified release (used by the publishing adapter). */
    public const val GROUP_GH: String = "gh"

    /** Shown on a tool that no setup recipe builds (e.g. `gh`): it can be tested but not rebuilt here. */
    public const val REPAIR_UNAVAILABLE_REASON: String =
        "Not built by setup, so it can't be rebuilt here. Install or update it yourself; Test still works."

    public val DEFAULT_PROBE_ARGUMENTS: List<String> = listOf("--help")
    public val DEFAULT_ACCEPTED_EXIT_CODES: Set<Int> = setOf(0)

    /** Exit status a C program produces from `exit(-1)` on Linux. */
    private const val EXIT_MINUS_ONE: Int = 255

    /** sysexits.h EX_USAGE: what the liblp tools exit with after printing usage. */
    private const val EXIT_USAGE: Int = 64

    private fun group(recipeGroup: String, vararg tools: ToolCapability): List<ToolCapability> =
        tools.map { it.copy(recipeGroup = recipeGroup) }

    private fun tool(
        toolId: String,
        probeArguments: List<String> = DEFAULT_PROBE_ARGUMENTS,
        acceptedExitCodes: Set<Int> = DEFAULT_ACCEPTED_EXIT_CODES,
    ): ToolCapability = ToolCapability(
        toolId = toolId,
        recipeGroup = "",
        probeArguments = probeArguments,
        acceptedExitCodes = acceptedExitCodes,
    )

    /**
     * The inventory. Order within a group is the verification order after a repair.
     */
    private val INVENTORY: List<ToolCapability> =
        group(
            GROUP_ANDROID_TOOLS,
            tool("adb", probeArguments = listOf("version")),
            tool("fastboot", probeArguments = listOf("--version")),
            // e2fsprogs: `--help` is an unknown option (usage, exit 1); `-V` prints the version.
            tool("mke2fs", probeArguments = listOf("-V")),
            // Exit codes below were measured on the built binaries: printing usage is how these tools
            // answer `--help` or no arguments, and it proves the binary runs.
            tool("e2fsdroid", acceptedExitCodes = setOf(0, 1)),
            // liblp tools exit 64 (EX_USAGE) after usage; lpdump answers --help with 0.
            tool("lpmake", acceptedExitCodes = setOf(0, EXIT_USAGE)),
            tool("lpunpack", acceptedExitCodes = setOf(0, EXIT_USAGE)),
            tool("lpdump"),
            tool("lpadd", acceptedExitCodes = setOf(0, EXIT_USAGE)),
            tool("lpflash", acceptedExitCodes = setOf(0, EXIT_USAGE)),
            // libsparse tools have no help flag: without arguments they print usage and exit 1 (older
            // builds exit(-1), i.e. 255).
            tool("simg2img", probeArguments = emptyList(), acceptedExitCodes = setOf(1, EXIT_MINUS_ONE)),
            tool("img2simg", probeArguments = emptyList(), acceptedExitCodes = setOf(1, EXIT_MINUS_ONE)),
            tool("ext2simg", acceptedExitCodes = setOf(0, 1)),
            tool("append2simg", acceptedExitCodes = setOf(0, 1)),
            tool("mkbootimg"),
            tool("unpack_bootimg"),
            tool("repack_bootimg"),
            tool("mkbootfs"),
            tool("mkdtboimg"),
            tool("avbtool"),
            tool("fec", acceptedExitCodes = setOf(0, 1)),
            tool("mkuserimg_mke2fs"),
            // f2fs-tools and the SDK build tools come out of the same android-tools build (vendor/).
            // f2fs-tools: `--help` is an unknown option (exit 1); `-V` prints the version.
            tool("make_f2fs", probeArguments = listOf("-V")),
            tool("sload_f2fs", probeArguments = listOf("-V")),
            // A shell script with no help flag: `--help` prints its usage and exits 1.
            tool("mkf2fsuserimg", acceptedExitCodes = setOf(1)),
            tool("aapt2", probeArguments = listOf("version")),
            // No help flag: any argument it doesn't know prints usage and exits 2.
            tool("zipalign", acceptedExitCodes = setOf(2)),
        ) + group(
            GROUP_EROFS_UTILS,
            tool("mkfs.erofs"),
            tool("dump.erofs"),
            tool("fsck.erofs"),
            // FUSE prints its option help and exits 1.
            tool("erofsfuse", acceptedExitCodes = setOf(0, 1)),
        ) + group(
            GROUP_IMG2SDAT,
            tool("img2sdat"),
        ) + group(
            GROUP_APKTOOL,
            tool("apktool", probeArguments = listOf("--version")),
        ) + group(
            GROUP_SIGNAPK,
            // signapk-all.jar prints usage for --help and exits 2.
            tool("signapk", acceptedExitCodes = setOf(0, 2)),
        ) + group(
            GROUP_PAYLOAD_DUMPER_GO,
            tool("payload-dumper-go"),
        ) + group(
            GROUP_GH,
            tool("gh", probeArguments = listOf("version")),
        )

    private val BY_TOOL_ID: Map<String, ToolCapability> = INVENTORY.associateBy { it.toolId }

    private val BY_GROUP: Map<String, Set<String>> =
        INVENTORY.groupBy { it.recipeGroup }.mapValues { (_, tools) -> tools.map { it.toolId }.toSet() }

    /**
     * Set of tool IDs with an automated, supported rebuild/acquisition recipe.
     */
    public val SUPPORTED_TOOL_IDS: Set<String> = BY_TOOL_ID.keys

    public fun capabilityFor(toolId: String): ToolCapability? = BY_TOOL_ID[toolId]

    public fun isRecipeSupported(toolId: String): Boolean = toolId in BY_TOOL_ID

    public fun unavailableReasonFor(toolId: String): String? =
        if (isRecipeSupported(toolId)) null else REPAIR_UNAVAILABLE_REASON

    public fun recipeGroupFor(toolId: String): String? = BY_TOOL_ID[toolId]?.recipeGroup

    public fun toolsForRecipeGroup(recipeGroup: String): Set<String> =
        BY_GROUP[recipeGroup] ?: emptySet()

    public fun allRecipeGroups(): Set<String> = BY_GROUP.keys

    public fun allRecipes(): List<RecipeCapability> = BY_GROUP.map { (group, tools) ->
        RecipeCapability(
            recipeGroup = group,
            toolIds = tools,
            isSupported = true,
        )
    }

    /** Probe arguments (without the binary) for [toolId]; the conservative default for unknown tools. */
    public fun probeArgumentsFor(toolId: String): List<String> =
        BY_TOOL_ID[toolId]?.probeArguments ?: DEFAULT_PROBE_ARGUMENTS

    /** Full probe argv for a resolved binary path. */
    public fun probeArgumentsFor(toolId: String, binaryPath: String): List<String> =
        listOf(binaryPath) + probeArgumentsFor(toolId)

    public fun acceptedExitCodesFor(toolId: String): Set<Int> =
        BY_TOOL_ID[toolId]?.acceptedExitCodes ?: DEFAULT_ACCEPTED_EXIT_CODES

    /** True when [exitCode] proves that [toolId] executed. */
    public fun isAcceptedExitCode(toolId: String, exitCode: Int): Boolean =
        exitCode in acceptedExitCodesFor(toolId)

    /**
     * Enforces: "Repair invalidates the owning recipe group only and verifies all affected required tools."
     */
    public fun invalidateOwningGroupForRepair(
        targetToolId: String,
        allTools: Map<String, ToolEvidence>,
    ): Map<String, ToolEvidence> = invalidateForRepair(
        targetToolId = targetToolId,
        allTools = allTools,
        recipes = allRecipes(),
    )
}
