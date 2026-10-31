/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline

import org.ide.lti.core.domain.pipeline.stages.CoverageFixRequest
import org.ide.lti.core.domain.pipeline.stages.ModuleApplicationStage
import org.ide.lti.core.model.workspace.ProjectScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectScriptsTest {
    private val fx = PipelineFixture()

    @Test
    fun enabledScriptsAreWrittenCheckedAndRunInOrderThenTheirFilesGetSidecars() {
        val ctx = fx.globalOtaContext()
        val scripts = listOf(
            ProjectScript("debrand", "rm -rf \"$1/product/app/Foo\""),
            ProjectScript("draft", "echo later", enabled = false),
            ProjectScript("fonts", "cp /tmp/f.ttf \"$1/system/system/fonts/\""),
        )
        val withScripts = ctx.copy(
            snapshot = ctx.snapshot.copy(customization = ctx.snapshot.customization.copy(scripts = scripts)),
        )
        val steps = ModuleApplicationStage().plan(withScripts)
        val labels = steps.map { it.label }
        // Every script is checked before the first one runs: a syntax error in fonts stops the build before
        // debrand has changed work/.
        assertEquals(
            listOf(
                "write debrand",
                "check debrand",
                "write fonts",
                "check fonts",
                "run debrand",
                "run fonts",
                "add missing sidecar lines",
            ),
            labels.dropWhile { !it.startsWith("write debrand") },
        )
        val run = steps.filterIsInstance<PipelineStep.Tool>().single { it.label == "run fonts" }
        assertEquals(listOf(ctx.abs("scripts/fonts.sh"), ctx.abs("work"), ctx.wsPath), run.command.args)
        assertEquals(ctx.abs("work"), run.command.workingDir)
        val host = steps.filterIsInstance<PipelineStep.Host>().single()
        assertEquals(CoverageFixRequest(ctx.target.dynamicPartitions), host.request)
        assertTrue(ModuleApplicationStage().plan(ctx).none { it.label.startsWith("run ") }, "no scripts, no steps")
    }

    @Test
    fun missingSidecarLinesAreAddedInEveryBuildWhenAskedWithoutAnyScript() {
        val ctx = fx.globalOtaContext()
        val asked = ctx.copy(
            snapshot = ctx.snapshot.copy(customization = ctx.snapshot.customization.copy(fillMissingSidecars = true)),
        )
        assertEquals(1, ModuleApplicationStage().plan(asked).filterIsInstance<PipelineStep.Host>().size)
        assertTrue(ModuleApplicationStage().plan(ctx).none { it is PipelineStep.Host })
    }
}
