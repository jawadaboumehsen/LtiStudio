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

public class GradleJarRecipe : BuildRecipe<RecipeConfig.GradleJar> {

    companion object {
        private const val GRADLE_TIMEOUT_SECONDS: Long = 1_200L
    }

    @Suppress("ReturnCount")
    override suspend fun build(ctx: RecipeContext, config: RecipeConfig.GradleJar): RecipeResult {
        val runner = RecipeCommandRunner(ctx.cli)
        val tool = ctx.group.outputs.firstOrNull()?.toolId ?: ctx.group.id.value
        val projectDir = "${ctx.extDir}/${ctx.group.id.value}"
        val wrapper = "$projectDir/gradlew"

        if (!runner.exists(ctx.distro, wrapper, "-f")) {
            return RecipeResult.Unsupported(listOf(wrapper))
        }

        val output = "$projectDir/${config.outputPath}"
        val jarDest = "${ctx.binDir}/$tool.jar"

        val needsBuild = ctx.forceRebuild ||
            !runner.isRecorded(ctx.repository, tool) ||
            !runner.exists(ctx.distro, output, "-f")

        if (needsBuild) {
            ctx.log("[Step 5/5] Building $tool with Gradle (${config.task})...")
            val failure = runner.executeBuildCommand(
                ctx = ctx,
                workingDir = projectDir,
                toolId = "sh",
                arguments = listOf(wrapper, "-p", projectDir, "--no-daemon", config.task),
                timeoutSeconds = GRADLE_TIMEOUT_SECONDS,
                actionName = "$tool:gradle",
            ).getOrElse { return RecipeResult.Failed(it.message ?: "$tool Gradle build failed") }

            if (failure != null) {
                return RecipeResult.Failed("$tool Gradle build failed (${config.task})", failure)
            }

            if (!runner.exists(ctx.distro, output, "-f")) {
                return RecipeResult.Failed("$tool build finished without $output")
            }
        }

        val copyFailure = runner.copy(ctx.distro, output, jarDest)
        if (copyFailure != null) {
            return RecipeResult.Failed("could not copy $output to $jarDest: $copyFailure")
        }

        runner.recordInstalledBinaries(ctx)
        return RecipeResult.Built(listOf("$tool.jar"))
    }
}
