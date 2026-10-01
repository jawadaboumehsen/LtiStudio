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

import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.core.ports.RemoteTransportPort
import org.ide.lti.core.data.setup.recipe.BuildRecipeDispatcher
import org.ide.lti.core.data.setup.recipe.RecipeContext
import org.ide.lti.core.data.setup.recipe.RecipeResult
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.ToolCapabilities
import org.ide.lti.core.domain.setup.ToolGroupCatalog

/**
 * Dedicated engine for compiling and verifying native toolchain binaries.
 *
 * Adheres to:
 * - Single Responsibility Principle: Exclusively orchestrates recipe dispatch and binary verification.
 * - Dispatches building and installation through [BuildRecipeDispatcher] over typed [RecipeConfig]s.
 * - Discrete Commands: All execution flows through discrete runner commands.
 */
public class ToolchainBuildEngine(
    private val cli: WslCliExecutor = WslCliExecutor(),
    private val repository: ToolchainSetupRepository? = null,
    private val transport: RemoteTransportPort? = null,
    private val dispatcher: BuildRecipeDispatcher = BuildRecipeDispatcher(),
) {
    private val commands = DistroCommands(cli)

    public companion object {
        public val CORE_BINARIES: List<String> = listOf(
            "adb",
            "fastboot",
            "mke2fs",
            "dump.erofs",
            "mkfs.erofs",
            "img2sdat",
        )

        /** The file a tool is installed as: JARs as `<id>.jar`, everything else under its id. */
        internal fun installedName(toolId: String): String = ToolCatalog.entryFor(toolId)?.binaryName ?: toolId
    }

    /**
     * Compiles and installs toolchain binaries into [binDir] by dispatching each targeted group
     * to its configured [org.ide.lti.core.data.setup.recipe.BuildRecipe].
     */
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    public suspend fun buildAndDistributeAll(
        distro: String,
        extDir: String,
        binDir: String,
        journal: SetupJournalContext? = null,
        targetRecipeGroups: Set<String>? = null,
        forceRebuild: Boolean = false,
        onRunStarted: (SetupStepStage, String) -> Unit = { _, _ -> },
        onProgress: (currentItem: String, index: Int, total: Int) -> Unit = { _, _, _ -> },
        log: (String) -> Unit = {},
    ): Result<Unit> {
        commands.run(distro, listOf("mkdir", "-p", binDir))
        val groupsToBuild = ToolGroupCatalog.allGroups.filter {
            targetRecipeGroups == null || it.id.value in targetRecipeGroups
        }
        val totalGroups = groupsToBuild.size.coerceAtLeast(1)

        val releaseFailures = mutableListOf<String>()
        for ((index, group) in groupsToBuild.withIndex()) {
            onProgress(group.id.value, index + 1, totalGroups)
            val ctx = RecipeContext(
                distro = distro,
                extDir = extDir,
                binDir = binDir,
                group = group,
                cli = cli,
                repository = repository,
                transport = transport,
                journal = journal,
                forceRebuild = forceRebuild,
                onRunStarted = onRunStarted,
                log = log,
            )
            val result = dispatcher.dispatch(ctx, group.recipe)
            when (result) {
                is RecipeResult.Built -> Unit
                is RecipeResult.Failed -> {
                    val detail = if (result.stderrTail.isNotBlank() && !result.reason.contains(result.stderrTail)) {
                        "${result.reason}:\n${result.stderrTail}"
                    } else {
                        result.reason
                    }
                    if (group.recipe is org.ide.lti.core.domain.setup.RecipeConfig.Release) {
                        releaseFailures += detail
                    } else {
                        return Result.failure(IllegalStateException(detail))
                    }
                }
                is RecipeResult.Unsupported -> {
                    return Result.failure(
                        IllegalStateException("${group.id.value} missing: ${result.missing.joinToString()}"),
                    )
                }
            }
        }

        if (releaseFailures.isNotEmpty()) {
            return Result.failure(IllegalStateException(releaseFailures.joinToString("; ")))
        }

        val targetedTools = targetRecipeGroups?.flatMap { ToolCapabilities.toolsForRecipeGroup(it) }?.toSet()
        val missing = CORE_BINARIES.filter { targetedTools == null || it in targetedTools }
            .filterNot { commands.exists(distro, "$binDir/$it", "-x") }
        if (missing.isNotEmpty()) {
            return Result.failure(
                IllegalStateException("Missing core binaries in $binDir: ${missing.joinToString(", ")}"),
            )
        }
        if (targetRecipeGroups == null) {
            repository?.markStageCompleted(SetupStepStage.TOOLCHAIN_COMPILATION.name, true)
        }
        return Result.success(Unit)
    }
}

/** Discrete, cancellable distro commands with checked results, shared by the build engine's steps. */
internal class DistroCommands(private val cli: WslCliExecutor) {
    suspend fun run(distro: String, command: List<String>, timeoutSeconds: Long = 10) =
        cli.run(distro, command, timeoutSeconds)

    suspend fun exists(distro: String, path: String, flag: String): Boolean =
        run(distro, listOf("test", flag, path)).exitCode == 0

    suspend fun checked(distro: String, command: List<String>): String? {
        val res = run(distro, command)
        return if (res.exitCode == 0) {
            null
        } else {
            "${command.joinToString(" ")}: " + res.error.ifBlank { res.output }.ifBlank { "exit ${res.exitCode}" }
        }
    }

    suspend fun copy(distro: String, from: String, to: String): String? = checked(distro, listOf("cp", "-a", from, to))

    suspend fun copyIfPresent(distro: String, from: String, to: String): String? =
        if (exists(distro, from, "-e")) copy(distro, from, to) else null

    suspend fun linkIfPresent(distro: String, target: String, linkValue: String, link: String): String? =
        if (exists(distro, target, "-f")) checked(distro, listOf("ln", "-sf", linkValue, link)) else null

    suspend fun firstFailure(vararg steps: suspend () -> String?): String? {
        for (step in steps) step()?.let { return it }
        return null
    }
}
