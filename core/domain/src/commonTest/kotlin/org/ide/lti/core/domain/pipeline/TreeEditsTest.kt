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

import org.ide.lti.core.domain.pipeline.configuration.StageSettingsValidator
import org.ide.lti.core.domain.pipeline.stages.ModuleApplicationStage
import org.ide.lti.core.domain.pipeline.stages.TreeEditSidecars
import org.ide.lti.core.domain.pipeline.stages.TreeEdits
import org.ide.lti.core.model.workspace.AssemblySettings
import org.ide.lti.core.model.workspace.TreeEdit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TreeEditsTest {
    private val fx = PipelineFixture()
    private val partitions = listOf("system", "vendor", "product")

    @Test
    fun anEditMustNameAPathInsideAPartitionAndAPlainNewName() {
        assertNull(TreeEdits.problem(TreeEdit("product", "app/YouTube"), partitions))
        assertNull(TreeEdits.problem(TreeEdit("product", "media/My Song.ogg", "My_Song.ogg"), partitions))
        assertNotNull(TreeEdits.problem(TreeEdit("cust", "app/Foo"), partitions))
        assertNotNull(TreeEdits.problem(TreeEdit("product", "app/../../etc"), partitions))
        assertNotNull(TreeEdits.problem(TreeEdit("product", ""), partitions))
        assertNotNull(TreeEdits.problem(TreeEdit("product", "app/Foo", "a/b"), partitions))
        assertNotNull(TreeEdits.problem(TreeEdit("product", "app/Foo", "my app"), partitions))
        assertNotNull(TreeEdits.problem(TreeEdit("product", "app/Foo", "Foo"), partitions))
        assertEquals("app/Bar", TreeEdits.result(TreeEdit("product", "app/Foo", "Bar")))
        assertEquals("Bar", TreeEdits.result(TreeEdit("product", "Foo", "Bar")))
        assertNull(TreeEdits.result(TreeEdit("product", "app/Foo")))
    }

    @Test
    fun theValidatorRefusesABadEditAndTwoEditsOfOnePath() {
        val bad = AssemblySettings(treeEdits = listOf(TreeEdit("product", "app/..")))
        assertTrue(StageSettingsValidator.validateAssembly(bad).errors.any { it.code == TreeEdits.INVALID_TREE_EDIT })
        val twice = AssemblySettings(
            treeEdits = listOf(TreeEdit("product", "app/Foo"), TreeEdit("product", "app/Foo/", "Bar")),
        )
        assertTrue(StageSettingsValidator.validateAssembly(twice).errors.any { it.code == "INVALID_TREE_EDIT" })
    }

    @Test
    fun renamedItemsKeepTheirLinesAndDeletedOnesLoseThem() {
        val fs = "product/app 0 0 755 capabilities=0x0\nproduct/app/Foo 0 0 755 capabilities=0x0\n" +
            "product/app/Foo/Foo.apk 0 0 644 capabilities=0x0\nproduct/app/Foobar 0 0 755 capabilities=0x0\n"
        val ctx = "/product/app u:object_r:system_file:s0\n/product/app/Foo u:object_r:system_file:s0\n" +
            "/product/app/Foo/Foo\\.apk u:object_r:system_file:s0\n/product/app/Foobar u:object_r:system_file:s0\n"
        val (renamedFs, renamedCtx) = TreeEdits.sidecars(
            "product",
            listOf(TreeEdit("product", "app/Foo", "Bar")),
            fs,
            ctx,
        )
        assertEquals(
            "product/app 0 0 755 capabilities=0x0\nproduct/app/Bar 0 0 755 capabilities=0x0\n" +
                "product/app/Bar/Foo.apk 0 0 644 capabilities=0x0\nproduct/app/Foobar 0 0 755 capabilities=0x0\n",
            renamedFs,
        )
        assertTrue("/product/app/Bar/Foo\\.apk u:object_r:system_file:s0" in renamedCtx, renamedCtx)
        // A name that merely starts with the old one (Foobar) is another item.
        assertTrue("/product/app/Foobar u:object_r:system_file:s0" in renamedCtx, renamedCtx)
        val (deletedFs, _) = TreeEdits.sidecars("product", listOf(TreeEdit("product", "app/Foo")), fs, ctx)
        assertEquals("product/app 0 0 755 capabilities=0x0\nproduct/app/Foobar 0 0 755 capabilities=0x0\n", deletedFs)
        // Another partition's edit leaves these lines alone.
        assertEquals(fs to ctx, TreeEdits.sidecars("product", listOf(TreeEdit("vendor", "app/Foo")), fs, ctx))
    }

    @Test
    fun thePatchesStageAppliesEditsLastAndMovesTheirLines() {
        val ctx = fx.globalOtaContext()
        val edits = listOf(
            TreeEdit("product", "app/YouTube"),
            TreeEdit("system", "system/fonts/My Font.ttf", "MyFont.ttf"),
        )
        val withEdits = ctx.copy(snapshot = ctx.snapshot.copy(assembly = ctx.snapshot.assembly.copy(treeEdits = edits)))
        val steps = ModuleApplicationStage().plan(withEdits)
        val labels = steps.map { it.label }
        assertEquals(
            listOf(
                "delete product/app/YouTube (1)",
                "rename system/system/fonts/My Font.ttf (2)",
                "move the sidecar lines of tree edits",
                "add missing sidecar lines",
            ),
            labels.takeLast(4),
        )
        val rename = steps.filterIsInstance<PipelineStep.Tool>().single { it.label.startsWith("rename") }
        assertEquals(
            listOf(
                "-c",
                TreeEdits.APPLY_SCRIPT,
                "sh",
                ctx.abs("work/system"),
                "system/fonts/My Font.ttf",
                "MyFont.ttf",
            ),
            rename.command.args,
        )
        assertEquals(TreeEditSidecars(edits), steps.filterIsInstance<PipelineStep.Host>().first().request)
    }
}
