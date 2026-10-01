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

public class ScriptCopyRecipe : BuildRecipe<RecipeConfig.ScriptCopy> {

    @Suppress("ReturnCount")
    override suspend fun build(ctx: RecipeContext, config: RecipeConfig.ScriptCopy): RecipeResult {
        val runner = RecipeCommandRunner(ctx.cli)
        val src = "${ctx.extDir}/${ctx.group.id.value}"

        val primaryFile = config.files.firstOrNull() ?: ctx.group.id.value
        if (!runner.exists(ctx.distro, "$src/$primaryFile", "-f")) {
            return RecipeResult.Unsupported(listOf("$src/$primaryFile"))
        }

        if (!ctx.forceRebuild &&
            runner.isRecorded(ctx.repository, ctx.group.id.value) &&
            runner.groupComplete(ctx.distro, ctx.binDir, ctx.group)
        ) {
            return RecipeResult.Built(ctx.group.outputs.map { it.file })
        }

        val copySteps = config.files.map { file ->
            suspend { runner.copy(ctx.distro, "$src/$file", "${ctx.binDir}/$file") }
        }

        val executables = ctx.group.outputs
            .filter { it.kind == ToolOutputKind.SCRIPT || it.kind == ToolOutputKind.NATIVE }
            .map { "${ctx.binDir}/${it.file}" }

        val chmodStep = suspend {
            if (executables.isNotEmpty()) {
                runner.checked(ctx.distro, listOf("chmod", "+x") + executables)
            } else {
                null
            }
        }

        val failure = runner.firstFailure(copySteps + chmodStep)
        if (failure != null) {
            return RecipeResult.Failed("installing ${ctx.group.id.value} failed: $failure")
        }

        runner.recordInstalledBinaries(ctx)
        return RecipeResult.Built(ctx.group.outputs.map { it.file })
    }
}
