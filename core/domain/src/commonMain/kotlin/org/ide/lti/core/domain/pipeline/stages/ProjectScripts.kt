/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.stages

import org.ide.lti.core.domain.pipeline.HostRequest
import org.ide.lti.core.domain.pipeline.PipelineStep
import org.ide.lti.core.domain.pipeline.StageContext
import org.ide.lti.core.domain.pipeline.stepCommand

/** Adds the missing fs_config / file_context lines of [partitions] in work/, labelled like their nearest parent. */
public data class CoverageFixRequest(val partitions: List<String>) : HostRequest

/**
 * The user's shell scripts ([org.ide.lti.core.model.workspace.ProjectScript]), run by the Patches stage after the
 * modules. Each is written to `scripts/<name>.sh`, checked with `sh -n`, then
 * run from inside work/; files it adds get sidecar lines like their parent folder, so the Build stage's coverage
 * gate passes.
 */
public object ProjectScripts {
    public const val DIR: String = "scripts"
    public const val SIDECARS_KEY: String = "scripts.sidecars"
    public val NAME: Regex = Regex("[A-Za-z0-9_-]{1,64}")

    public fun steps(ctx: StageContext): List<PipelineStep> {
        val scripts = ctx.snapshot.customization.scripts.filter { it.enabled }
        val work = ctx.abs("work")
        val runs = scripts.flatMap { script ->
            val file = ctx.abs("$DIR/${script.name}.sh")
            listOf(
                PipelineStep.WriteFile(
                    "$DIR/${script.name}.sh",
                    script.content.encodeToByteArray(),
                    "write ${script.name}",
                ),
                PipelineStep.Tool(stepCommand("sh", listOf("-n", file), ctx.wsPath), "check ${script.name}"),
                PipelineStep.Tool(stepCommand("sh", listOf(file, work, ctx.wsPath), work), "run ${script.name}"),
            )
        }
        // Also on its own: a sidecar fix made in work/configs between builds is lost when Assemble runs again.
        val fill = scripts.isNotEmpty() || ctx.snapshot.customization.fillMissingSidecars
        val sidecars = PipelineStep.Host(
            "add missing sidecar lines",
            SIDECARS_KEY,
            CoverageFixRequest(ctx.target.dynamicPartitions),
        )
        return runs + listOfNotNull(sidecars.takeIf { fill })
    }
}
