/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.check.stages

import org.ide.lti.core.data.setup.check.CheckContext
import org.ide.lti.core.data.setup.check.CheckStage
import org.ide.lti.core.data.setup.check.StageOutcome
import org.ide.lti.core.data.setup.state.CheckScope
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolSource

public class SourcesStage : CheckStage {
    override val stage: SetupStepStage = SetupStepStage.REPO_SYNCHRONIZATION

    override suspend fun run(ctx: CheckContext, scope: CheckScope): StageOutcome {
        val distro = ctx.environment.distro
        scope.setPaths(ctx.userHome, ctx.workDir)

        val findExt = ctx.readOnlyCommands.run(
            distro,
            listOf("find", ctx.extDir, "-maxdepth", "1", "-mindepth", "1", "-type", "d", "-printf", "%f\n"),
        )
        val subdirs = if (findExt.exitCode == 0) {
            findExt.output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        } else {
            emptySet()
        }

        val testWorkDir = ctx.readOnlyCommands.run(distro, listOf("test", "-d", ctx.workDir))
        val workDirExists = testWorkDir.exitCode == 0

        val gitGroups = ToolGroupCatalog.allGroups.mapNotNull { group ->
            (group.source as? ToolSource.Git)?.let { git -> group.id.value to git }
        }

        val missingSubs = gitGroups.filter { it.first !in subdirs }
        val notAtPin = gitGroups.filter { it.first in subdirs }.filterNot { (name, git) ->
            val dir = "${ctx.extDir}/$name"
            val res = ctx.readOnlyCommands.run(
                distro,
                listOf("git", "-C", dir, "rev-parse", "--show-toplevel", "HEAD"),
            )
            val lines = res.output.lines().map { it.trim() }
            res.exitCode == 0 &&
                lines.getOrNull(0)?.trimEnd('/') == dir.trimEnd('/') &&
                lines.getOrNull(1) == git.recommendedCommit
        }

        return when {
            notAtPin.isNotEmpty() -> {
                val subject = if (notAtPin.size == 1) {
                    "1 source repository is"
                } else {
                    "${notAtPin.size} source repositories are"
                }
                val desc = "$subject not at the pinned commit: ${notAtPin.joinToString { it.first }}"
                StageOutcome.Failed(desc, desc)
            }
            missingSubs.isEmpty() && workDirExists -> {
                StageOutcome.Success("${gitGroups.size} submodules · ~/LtiRomWorkDir")
            }
            !workDirExists -> StageOutcome.Pending("Workspace directory ${ctx.workDir} not created yet.")
            else -> StageOutcome.Pending("${missingSubs.size} submodule(s) missing in ${ctx.extDir}.")
        }
    }
}
