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

import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ToolOutputKind

public class CMakeRecipe : BuildRecipe<RecipeConfig.CMake> {

    companion object {
        private const val CONFIGURE_TIMEOUT_SECONDS = 300L
        private const val MAKE_TIMEOUT_SECONDS = 300L
    }

    override suspend fun build(ctx: RecipeContext, config: RecipeConfig.CMake): RecipeResult {
        val runner = RecipeCommandRunner(ctx.cli)
        val projectDir = "${ctx.extDir}/${ctx.group.id.value}"

        val unsupported = validateSource(ctx, runner, config, projectDir)
        return unsupported ?: if (isAlreadyBuilt(ctx, runner)) {
            ctx.log("[Step 5/5] ${ctx.group.id.value}: every tool is present in ${ctx.binDir}. Skipping CMake/Make.")
            RecipeResult.Built(ctx.group.outputs.map { it.file })
        } else {
            executeBuildSteps(ctx, runner, config, projectDir)
        }
    }

    private suspend fun validateSource(
        ctx: RecipeContext,
        runner: RecipeCommandRunner,
        config: RecipeConfig.CMake,
        projectDir: String,
    ): RecipeResult.Unsupported? {
        val sourceDir = config.sourceSubdir?.let { "$projectDir/$it" } ?: projectDir
        val checkFile = if (config.sourceSubdir != null) "$sourceDir/CMakeLists.txt" else "$projectDir/CMakeLists.txt"
        return if (!runner.exists(ctx.distro, projectDir, "-d") || !runner.exists(ctx.distro, checkFile, "-f")) {
            RecipeResult.Unsupported(listOf(checkFile))
        } else {
            null
        }
    }

    private suspend fun isAlreadyBuilt(ctx: RecipeContext, runner: RecipeCommandRunner): Boolean {
        if (ctx.forceRebuild) return false
        val primaryTool = ctx.group.outputs.firstOrNull()?.toolId ?: ctx.group.id.value
        return runner.isRecorded(ctx.repository, primaryTool) &&
            runner.groupComplete(ctx.distro, ctx.binDir, ctx.group)
    }

    private suspend fun executeBuildSteps(
        ctx: RecipeContext,
        runner: RecipeCommandRunner,
        config: RecipeConfig.CMake,
        projectDir: String,
    ): RecipeResult {
        ctx.log("[Step 5/5] Compiling ${ctx.group.id.value}...")
        val buildDir = "$projectDir/${config.buildSubdir}"
        runner.run(ctx.distro, listOf("mkdir", "-p", buildDir))
        if (ctx.forceRebuild) {
            runner.run(ctx.distro, listOf("rm", "-rf", "$buildDir/CMakeCache.txt", "$buildDir/CMakeFiles"))
            ctx.log("[Step 5/5] Rebuild: discarded the previous CMake configuration in $buildDir.")
        }

        val compileFailure = runConfigureAndCompile(ctx, runner, config, projectDir, buildDir)
        if (compileFailure != null) return compileFailure

        val installFailure = installOutputs(ctx, runner, buildDir)
        return if (installFailure != null) {
            RecipeResult.Failed("${ctx.group.id.value} built, but installing its outputs failed: $installFailure")
        } else {
            runner.recordInstalledBinaries(ctx)
            RecipeResult.Built(ctx.group.outputs.map { it.file })
        }
    }

    private suspend fun runConfigureAndCompile(
        ctx: RecipeContext,
        runner: RecipeCommandRunner,
        config: RecipeConfig.CMake,
        projectDir: String,
        buildDir: String,
    ): RecipeResult.Failed? {
        val configureArgs = buildConfigureArgs(config)
        val configureResult = runner.executeBuildCommand(
            ctx = ctx,
            workingDir = projectDir,
            toolId = "cmake",
            arguments = configureArgs,
            timeoutSeconds = CONFIGURE_TIMEOUT_SECONDS,
            actionName = "${ctx.group.id.value}:cmake",
        )
        val cmakeError = configureResult.fold(
            onSuccess = { err ->
                if (err != null) RecipeResult.Failed("${ctx.group.id.value} cmake configuration failed", err) else null
            },
            onFailure = { ex ->
                RecipeResult.Failed(ex.message ?: "${ctx.group.id.value} cmake failed")
            },
        )
        return cmakeError ?: runMakeCommand(ctx, runner, buildDir)
    }

    private suspend fun runMakeCommand(
        ctx: RecipeContext,
        runner: RecipeCommandRunner,
        buildDir: String,
    ): RecipeResult.Failed? {
        val makeRes = runner.executeBuildCommand(
            ctx = ctx,
            workingDir = buildDir,
            toolId = "make",
            arguments = listOf("-C", buildDir, "-j4"),
            timeoutSeconds = MAKE_TIMEOUT_SECONDS,
            actionName = "${ctx.group.id.value}:make",
        ).getOrElse { return RecipeResult.Failed(it.message ?: "${ctx.group.id.value} make failed") }
        return if (makeRes != null) {
            RecipeResult.Failed("${ctx.group.id.value} make build failed", makeRes)
        } else {
            null
        }
    }

    private fun buildConfigureArgs(config: RecipeConfig.CMake): List<String> = buildList {
        add("-S")
        add(config.sourceSubdir ?: ".")
        add("-B")
        add(config.buildSubdir)
        if (config.cmakeArgs.none { it.startsWith("-DCMAKE_BUILD_TYPE=") }) {
            add("-DCMAKE_BUILD_TYPE=Release")
        }
        if (config.cmakeArgs.none { it.startsWith("-DCMAKE_C_COMPILER=") }) {
            add("-DCMAKE_C_COMPILER=clang")
        }
        if (config.cmakeArgs.none { it.startsWith("-DCMAKE_CXX_COMPILER=") }) {
            add("-DCMAKE_CXX_COMPILER=clang++")
        }
        addAll(config.cmakeArgs)
    }

    private suspend fun installOutputs(ctx: RecipeContext, runner: RecipeCommandRunner, buildDir: String): String? {
        val distro = ctx.distro
        val binDir = ctx.binDir
        return runner.firstFailure(
            { copyOutputs(runner, distro, buildDir, binDir) },
            { runner.linkIfPresent(distro, "$binDir/fuse.erofs", "fuse.erofs", "$binDir/erofsfuse") },
            { makeOutputsExecutable(ctx, runner) },
        )
    }

    private suspend fun copyOutputs(
        runner: RecipeCommandRunner,
        distro: String,
        buildDir: String,
        binDir: String,
    ): String? = when {
        runner.exists(distro, "$buildDir/erofs-tools", "-d") -> {
            runner.copy(distro, "$buildDir/erofs-tools/.", "$binDir/")
        }
        runner.exists(distro, buildDir, "-d") -> {
            runner.copy(distro, "$buildDir/.", "$binDir/")
        }
        else -> "the build produced no outputs in $buildDir"
    }

    private suspend fun makeOutputsExecutable(ctx: RecipeContext, runner: RecipeCommandRunner): String? {
        val executables = ctx.group.outputs
            .filter { it.kind == ToolOutputKind.NATIVE }
            .map { "${ctx.binDir}/${it.file}" }
        return if (executables.isNotEmpty()) {
            runner.checked(ctx.distro, listOf("chmod", "+x") + executables)
        } else {
            null
        }
    }
}
