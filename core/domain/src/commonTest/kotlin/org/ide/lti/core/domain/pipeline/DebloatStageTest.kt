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

import kotlinx.datetime.Instant
import org.ide.lti.core.domain.debloat.DebloatInventory
import org.ide.lti.core.domain.pipeline.stages.DebloatStage
import org.ide.lti.core.domain.pipeline.text.SidecarEditor
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.DebloatSettings
import org.ide.lti.core.model.workspace.Workspace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DebloatStageTest {
    private val listing = """
        41000000	system/system/app/Facebook/Facebook.apk
        2000	system/system/app/Facebook/split_config.arm64_v8a.apk
        9000	system/system/priv-app/SystemUI/SystemUI.apk
        5000	product/app/YouTube/YouTube.apk
        100	configs/fs_config-system.apk
    """.trimIndent()

    private fun ctx(settings: DebloatSettings, values: Map<String, String> = emptyMap()) = StageContext(
        workspace = Workspace("ws", "name", "/w", linuxPath = "/w"),
        target = TargetDevice.EMPTY,
        snapshot = ConfigurationSnapshot.create(
            id = "id",
            workspaceId = "ws",
            profileRevision = 1,
            acquisition = AcquisitionSettings(
                region = TargetRegion.GLOBAL,
                firmware = TargetFirmware("v1", "b1"),
            ),
            debloat = settings,
            createdAt = Instant.parse("2023-01-01T00:00:00Z"),
        ),
        runtimeValues = values,
    )

    @Test
    fun `the inventory groups APKs by app folder and skips the config folders`() {
        val inventory = DebloatInventory.parse(listing)
        assertEquals(
            listOf("product:app/YouTube", "system:system/app/Facebook", "system:system/priv-app/SystemUI"),
            inventory.map { "${it.partition}:${it.relativePath}" },
        )
        assertEquals(41_002_000L, inventory.first { it.relativePath.endsWith("Facebook") }.sizeBytes)
        assertTrue(DebloatInventory.isProtected(inventory.first { it.relativePath.endsWith("SystemUI") }))
    }

    @Test
    fun `disabled debloat writes a no-removal result and touches nothing`() {
        val plan = DebloatStage().plan(ctx(DebloatSettings(enabled = false)))
        assertEquals(1, plan.size)
        assertEquals(DebloatStage.RESULT, (plan.single() as PipelineStep.WriteFile).relPath)
    }

    @Test
    fun `picked apps are deleted and their sidecar entries pruned once per partition`() {
        val settings = DebloatSettings(
            enabled = true,
            removeSelectors = listOf("system:system/app/Facebook", "product:app/YouTube"),
        )
        val stage = DebloatStage()

        val first = stage.plan(ctx(settings))
        assertEquals(listOf("list apps"), first.map { it.label }, "nothing is removed before the inventory is read")

        val plan = stage.plan(ctx(settings, mapOf(DebloatStage.INVENTORY_KEY to listing)))
        val rm = plan.filterIsInstance<PipelineStep.Tool>().single { it.command.toolId == "rm" }
        assertEquals(
            listOf("-rf", "--", "/w/work/product/app/YouTube", "/w/work/system/system/app/Facebook"),
            rm.command.args,
        )
        val edits = plan.filterIsInstance<PipelineStep.EditFile>().map { it.relPath }
        assertEquals(
            listOf(
                "work/configs/fs_config-product",
                "work/configs/file_context-product",
                "work/configs/fs_config-system",
                "work/configs/file_context-system",
            ),
            edits,
        )
        val fsConfig = plan.filterIsInstance<PipelineStep.EditFile>()
            .single { it.relPath == "work/configs/fs_config-system" }
        val pruned = fsConfig.transform(
            "system/app/Facebook 0 0 755 capabilities=0x0\n" +
                "system/app/Facebook/Facebook.apk 0 0 644 capabilities=0x0\n" +
                "system/app/FacebookLite 0 0 755 capabilities=0x0\n",
        )
        // system is system-as-root: its fs_config paths are relative to the image root.
        assertEquals("system/app/FacebookLite 0 0 755 capabilities=0x0\n", pruned, "only that folder, not a prefix")
        assertTrue(plan.last() is PipelineStep.WriteFile)
    }

    @Test
    fun `picking a protected app fails the stage instead of removing it`() {
        val settings = DebloatSettings(enabled = true, removeSelectors = listOf("system:system/priv-app/SystemUI"))
        val plan = DebloatStage().plan(ctx(settings, mapOf(DebloatStage.INVENTORY_KEY to listing)))
        val check = plan.filterIsInstance<PipelineStep.Check>().single()
        assertTrue(check.verify(emptyMap())!!.contains("PROTECTED_ENTRY"))
        assertTrue(plan.none { it is PipelineStep.Tool && it.command.toolId == "rm" })
    }

    @Test
    fun `file_context entries are matched as escaped regex paths`() {
        val text = "/system/app/Face\\.book u:object_r:system_file:s0\n/system/app/Other u:object_r:system_file:s0\n"
        assertEquals(
            "/system/app/Other u:object_r:system_file:s0\n",
            SidecarEditor.withoutFileContext(text, "system", listOf("system/app/Face.book")),
        )
        assertEquals(
            "",
            SidecarEditor.withoutFileContext("/product/app/X u:object_r:system_file:s0\n", "product", listOf("app/X")),
        )
    }

    @Test
    fun `changing the picks changes the cache key`() {
        val stage = DebloatStage()
        fun picks(app: String) =
            ctx(DebloatSettings(enabled = true, removeSelectors = listOf("system:system/app/$app")))
        val a = stage.computeCacheKey(picks("A"), "p")
        val b = stage.computeCacheKey(picks("B"), "p")
        assertNotEquals(a, b)
    }

    @Test
    fun vendorRenamedCoreAppsAreProtectedButLookalikesAreNot() {
        fun app(path: String) = org.ide.lti.core.domain.debloat.DebloatCandidate("system", path, 1, null)
        val inventory = org.ide.lti.core.domain.debloat.DebloatInventory
        val core = listOf(
            "priv-app/SystemUI_MFV_tablet_abroad",
            "priv-app/SettingsProvider_MFV",
            "priv-app/Telecom_MFV",
            "app/WebViewGoogle64",
            "priv-app/DocumentsUIGoogle",
            "priv-app/MediaProviderLegacy",
            "priv-app/Shell",
        )
        for (core in core) {
            assertTrue(inventory.isProtected(app(core)), core)
        }
        for (other in listOf("app/ShellExtras", "app/PhoneticKeyboard", "app/YouTube")) {
            assertFalse(inventory.isProtected(app(other)), other)
        }
    }
}
