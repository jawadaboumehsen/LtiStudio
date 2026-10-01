/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.recipe

import io.ltirom.tooling.client.WslPathTranslator
import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import org.ide.lti.core.data.setup.JournaledRunLauncher
import org.ide.lti.core.data.setup.JournaledRunResult
import org.ide.lti.core.data.setup.SetupJournalContext
import org.ide.lti.core.data.setup.ToolCatalog
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.ToolGroup

public data class RecipeContext(
    val distro: String,
    val extDir: String,
    val binDir: String,
    val group: ToolGroup,
    val cli: WslCliExecutor = WslCliExecutor(),
    val repository: ToolchainSetupRepository? = null,
    val transport: RemoteTransportPort? = null,
    val journal: SetupJournalContext? = null,
    val forceRebuild: Boolean = false,
    val onRunStarted: (SetupStepStage, String) -> Unit = { _, _ -> },
    val log: (String) -> Unit = {},
    val resolvedInput: ResolvedInput? = null,
)

public sealed interface RecipeResult {
    public data class Built(val outputs: List<String> = emptyList()) : RecipeResult
    public data class Failed(val reason: String, val stderrTail: String = "") : RecipeResult
    public data class Unsupported(val missing: List<String>) : RecipeResult {
        public constructor(missingItem: String) : this(listOf(missingItem))
    }
}

public interface BuildRecipe<in C : RecipeConfig> {
    public suspend fun build(ctx: RecipeContext, config: C): RecipeResult
}

public class BuildRecipeDispatcher(
    private val androidTools: BuildRecipe<RecipeConfig.AndroidTools> = AndroidToolsRecipe(),
    private val cmake: BuildRecipe<RecipeConfig.CMake> = CMakeRecipe(),
    private val gradleJar: BuildRecipe<RecipeConfig.GradleJar> = GradleJarRecipe(),
    private val scriptCopy: BuildRecipe<RecipeConfig.ScriptCopy> = ScriptCopyRecipe(),
    private val releaseDownload: BuildRecipe<RecipeConfig.Release> = ReleaseDownloadRecipe(),
) {
    public suspend fun dispatch(ctx: RecipeContext, config: RecipeConfig): RecipeResult = when (config) {
        is RecipeConfig.AndroidTools -> androidTools.build(ctx, config)
        is RecipeConfig.CMake -> cmake.build(ctx, config)
        is RecipeConfig.GradleJar -> gradleJar.build(ctx, config)
        is RecipeConfig.ScriptCopy -> scriptCopy.build(ctx, config)
        is RecipeConfig.Release -> releaseDownload.build(ctx, config)
    }
}

internal class RecipeCommandRunner(private val cli: WslCliExecutor) {

    companion object {
        private const val ERROR_DETAIL_CHARS = 600
        private const val SHA256_HEX_LENGTH = 64
        private val pathTranslator = WslPathTranslator()
        private val winDrivePattern = Regex("""^[a-zA-Z]:[/\\]""")
    }

    private fun normalizeArg(arg: String): String =
        if (winDrivePattern.containsMatchIn(arg)) pathTranslator.toWslPath(arg) else arg

    suspend fun run(distro: String, command: List<String>, timeoutSeconds: Long = 10): CliExecutionResult {
        val normalized = command.map { normalizeArg(it) }
        return cli.run(distro, normalized, timeoutSeconds)
    }

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

    suspend fun sha256Of(distro: String, path: String): String? {
        val res = run(distro, listOf("sha256sum", path))
        return res.output.trim().substringBefore(' ').takeIf { res.exitCode == 0 && it.length == SHA256_HEX_LENGTH }
    }

    suspend fun firstFailure(vararg steps: suspend () -> String?): String? = firstFailure(steps.toList())

    suspend fun firstFailure(steps: List<suspend () -> String?>): String? {
        for (step in steps) step()?.let { return it }
        return null
    }

    fun isRecorded(repository: ToolchainSetupRepository?, toolId: String): Boolean =
        repository?.currentState?.tools?.get(toolId)?.isCompiled == true

    suspend fun isUsable(distro: String, binDir: String, toolId: String, fileName: String): Boolean =
        run(distro, ToolCatalog.probePlan(toolId, binDir, fileName).existsCheck).exitCode == 0

    suspend fun groupComplete(distro: String, binDir: String, group: ToolGroup): Boolean =
        group.outputs.all { isUsable(distro, binDir, it.toolId, it.file) }

    suspend fun recordInstalledBinaries(ctx: RecipeContext) {
        for (output in ctx.group.outputs) {
            val path = "${ctx.binDir}/${output.file}"
            if (isUsable(ctx.distro, ctx.binDir, output.toolId, output.file)) {
                ctx.repository?.updateTool(output.toolId) { tool ->
                    tool.copy(isCompiled = true, binaryPath = path)
                }
            }
        }
    }

    @Suppress("ReturnCount")
    suspend fun executeBuildCommand(
        ctx: RecipeContext,
        workingDir: String,
        toolId: String,
        arguments: List<String>,
        timeoutSeconds: Long,
        actionName: String,
        environment: Map<String, String> = emptyMap(),
    ): Result<String?> {
        if (ctx.transport == null) {
            val env = environment.map { "${it.key}=${it.value}" }
            val command = (if (env.isEmpty()) emptyList() else listOf("env") + env) + toolId + arguments
            val res = run(ctx.distro, command, timeoutSeconds = timeoutSeconds)
            ctx.log("[$toolId] ${res.output.takeLast(300)}")
            return Result.success(
                if (res.exitCode == 0) null else res.error.ifBlank { res.output }.takeLast(ERROR_DETAIL_CHARS),
            )
        }
        val repo = ctx.repository
        val journalCtx = ctx.journal
        if (repo == null || journalCtx == null) {
            return Result.failure(
                IllegalStateException(
                    "Durable build step '$actionName' requires a journaled attempt; " +
                        "refusing an unjournaled server run.",
                ),
            )
        }
        val runResult = JournaledRunLauncher(ctx.transport, repo, journalCtx).run(
            stage = SetupStepStage.TOOLCHAIN_COMPILATION,
            actionId = "build:$actionName",
            request = ToolExecutionRequest(
                toolId = toolId,
                arguments = arguments,
                workingDirectory = workingDir,
                environment = environment,
                timeoutMs = timeoutSeconds * 1000L,
            ),
            onRunStarted = { stage, runId ->
                ctx.onRunStarted(stage, runId)
                ctx.log("[Step 5/5] Attached to build run $runId ($actionName).")
            },
            log = ctx.log,
        )
        return when (runResult) {
            is JournaledRunResult.Finished -> Result.success(
                runResult.takeIf { it.exitCode != 0 }?.let { it.errorTail.ifBlank { "exit code ${it.exitCode}" } },
            )
            is JournaledRunResult.JournalRejected -> Result.failure(IllegalStateException(runResult.reason))
            is JournaledRunResult.Interrupted -> Result.failure(IllegalStateException(runResult.reason))
        }
    }
}
