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
import org.ide.lti.core.domain.pipeline.stages.AppliedModulePlan
import org.ide.lti.core.domain.pipeline.stages.ModuleApplicationStage
import org.ide.lti.core.domain.plugin.EmptyPluginCatalog
import org.ide.lti.core.domain.plugin.PluginCatalogPort
import org.ide.lti.core.domain.plugin.PluginPlanResolver
import org.ide.lti.core.model.plugin.AssetRef
import org.ide.lti.core.model.plugin.Condition
import org.ide.lti.core.model.plugin.Operation
import org.ide.lti.core.model.plugin.PartitionPath
import org.ide.lti.core.model.plugin.PluginPlanTemplate
import org.ide.lti.core.model.plugin.SettingDefinition
import org.ide.lti.core.model.plugin.ValueRef
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.CustomizationSettings
import org.ide.lti.core.model.workspace.PluginLockfile
import org.ide.lti.core.model.workspace.PluginLockfileEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModuleApplicationStageTest {

    private val fx = PipelineFixture()

    private fun createContext(customization: CustomizationSettings): StageContext {
        val snapshot = ConfigurationSnapshot.create(
            id = "test-snap",
            workspaceId = fx.workspace.id,
            profileRevision = 1,
            acquisition = AcquisitionSettings(
                region = TargetRegion.GLOBAL,
                firmware = TargetFirmware(
                    version = "v1.0",
                    buildId = "BID1",
                    androidVersion = "15",
                    securityPatch = "2026-02-01",
                ),
            ),
            customization = customization,
            createdAt = Instant.parse("2026-09-14T00:00:00Z"),
        )
        return StageContext(fx.workspace, snapshot, fx.targetGlobal, emptyMap())
    }

    private class MapPluginCatalog(
        private val templates: Map<String, PluginPlanTemplate> = emptyMap(),
        private val digests: Map<String, String> = emptyMap(),
    ) : PluginCatalogPort {
        override fun templateFor(publisher: String, id: String, version: String): PluginPlanTemplate? =
            templates["$publisher:$id:$version"]

        override fun contentDigestFor(publisher: String, id: String, version: String): String? =
            digests["$publisher:$id:$version"]
    }

    @Test
    fun `no enabled packages results in exactly one WriteFile with empty record and outputs lists applied json`() {
        val stage = ModuleApplicationStage()
        val ctx = createContext(CustomizationSettings(enabledPackages = emptyList()))

        val outputs = stage.outputs(ctx)
        assertEquals(listOf("work/modules/applied.json"), outputs)

        val plan = stage.plan(ctx)
        assertEquals(1, plan.size)
        val step = plan.single() as PipelineStep.WriteFile
        assertEquals("work/modules/applied.json", step.relPath)
        assertEquals("""{"enabled":[],"operations":0}""", step.content.decodeToString())
    }

    @Test
    fun `an enabled package whose lockfile entry is missing produces a Check failing with UNRESOLVED_PLUGIN_CONTENT`() {
        val stage = ModuleApplicationStage()
        val ctx = createContext(
            CustomizationSettings(
                enabledPackages = listOf("org.ide:missing-mod"),
                lockfile = PluginLockfile(entries = emptyList()),
            ),
        )

        val plan = stage.plan(ctx)
        assertEquals(1, plan.size)
        val step = plan.single() as PipelineStep.Check
        val failure = step.verify(emptyMap())
        assertNotNull(failure)
        assertTrue(failure.contains("UNRESOLVED_PLUGIN_CONTENT"))
        assertTrue(failure.contains("org.ide:missing-mod"))
    }

    @Test
    fun `missing catalog template produces Check failing with UNRESOLVED_PLUGIN_CONTENT`() {
        val stage = ModuleApplicationStage(EmptyPluginCatalog)
        val entry = PluginLockfileEntry("org.ide", "mod-no-template", "1.0.0", "sha256:abc")
        val ctx = createContext(
            CustomizationSettings(
                enabledPackages = listOf("org.ide:mod-no-template"),
                lockfile = PluginLockfile(entries = listOf(entry)),
            ),
        )

        val plan = stage.plan(ctx)
        assertEquals(1, plan.size)
        val step = plan.single() as PipelineStep.Check
        val failure = step.verify(emptyMap())
        assertNotNull(failure)
        assertTrue(failure.contains("UNRESOLVED_PLUGIN_CONTENT"))
        assertTrue(failure.contains("org.ide:mod-no-template"))
    }

    @Test
    fun `resolving enabled package produces plan json with ordered operation ids, digest, check, and summary`() {
        val template = PluginPlanTemplate(
            schemaVersion = 1,
            publisher = "org.ide",
            id = "sample-mod",
            version = "1.0.0",
            settings = emptyList(),
            operations = listOf(
                Operation.Delete(
                    id = "op-delete-1",
                    target = PartitionPath("system", "app/Bloatware"),
                ),
                Operation.Copy(
                    id = "op-copy-2",
                    source = AssetRef("assets/app.apk"),
                    destination = PartitionPath("system", "app/App/App.apk"),
                ),
            ),
        )
        val catalog = MapPluginCatalog(
            templates = mapOf("org.ide:sample-mod:1.0.0" to template),
            digests = mapOf("org.ide:sample-mod:1.0.0" to "sha256:pkgdigest"),
        )
        val entry = PluginLockfileEntry("org.ide", "sample-mod", "1.0.0", "sha256:pkgdigest")
        val stage = ModuleApplicationStage(catalog)
        val ctx = createContext(
            CustomizationSettings(
                enabledPackages = listOf("org.ide:sample-mod"),
                lockfile = PluginLockfile(entries = listOf(entry)),
            ),
        )

        val plan = stage.plan(ctx)
        assertEquals(3, plan.size)

        val planFileStep = plan[0] as PipelineStep.WriteFile
        assertEquals("work/modules/plan.json", planFileStep.relPath)
        val planJson = planFileStep.content.decodeToString()
        assertTrue(planJson.contains("op-delete-1"))
        assertTrue(planJson.contains("op-copy-2"))
        assertTrue(planJson.indexOf("op-delete-1") < planJson.indexOf("op-copy-2"))

        val decodedPlan = ConfigurationSnapshot.canonicalJson.decodeFromString<AppliedModulePlan>(planJson)
        assertEquals(listOf("op-delete-1", "op-copy-2"), decodedPlan.operationIds)
        assertEquals(1, decodedPlan.resolvedPlanDigests.size)
        assertTrue(planJson.contains(decodedPlan.resolvedPlanDigests.single()))
        assertEquals(listOf(entry), decodedPlan.lockfileEntries)
        assertEquals("Delete", decodedPlan.operations[0].kind)
        assertEquals("system/app/Bloatware", decodedPlan.operations[0].target)
        assertEquals("Copy", decodedPlan.operations[1].kind)
        assertEquals("system/app/App/App.apk", decodedPlan.operations[1].target)

        val checkStep = plan[1] as PipelineStep.Check
        assertEquals("modules applied", checkStep.label)
        assertNull(checkStep.verify(mapOf("modules.apply.result" to "ok")))
        val failedCheck = checkStep.verify(mapOf("modules.apply.result" to "checksum mismatch"))
        assertNotNull(failedCheck)
        assertTrue(failedCheck.contains("checksum mismatch"))
        assertNotNull(checkStep.verify(emptyMap()))

        val summaryStep = plan[2] as PipelineStep.WriteFile
        assertEquals("work/modules/applied.json", summaryStep.relPath)
        assertEquals("""{"enabled":["org.ide:sample-mod"],"operations":2}""", summaryStep.content.decodeToString())
    }

    @Test
    fun `resolver rejection with illegal setting value produces a failing Check carrying that code`() {
        val template = PluginPlanTemplate(
            schemaVersion = 1,
            publisher = "org.ide",
            id = "setting-mod",
            version = "1.0.0",
            settings = listOf(
                SettingDefinition.BooleanSetting(
                    id = "enable_tweaks",
                    label = "Enable Tweaks",
                    default = false,
                ),
            ),
            operations = listOf(
                Operation.Delete(
                    id = "op-1",
                    target = PartitionPath("system", "app/Bloat"),
                    condition = Condition.Equals(
                        left = ValueRef.Setting("enable_tweaks"),
                        right = ValueRef.Literal("true"),
                    ),
                ),
            ),
        )
        val catalog = MapPluginCatalog(
            templates = mapOf("org.ide:setting-mod:1.0.0" to template),
            digests = mapOf("org.ide:setting-mod:1.0.0" to "sha256:digest"),
        )
        val entry = PluginLockfileEntry("org.ide", "setting-mod", "1.0.0", "sha256:digest")
        val stage = ModuleApplicationStage(catalog)
        val ctx = createContext(
            CustomizationSettings(
                enabledPackages = listOf("org.ide:setting-mod"),
                lockfile = PluginLockfile(entries = listOf(entry)),
                settings = mapOf(
                    "setting-mod" to mapOf("enable_tweaks" to "not-a-boolean"),
                ),
            ),
        )

        val plan = stage.plan(ctx)
        assertEquals(1, plan.size)
        val step = plan.single() as PipelineStep.Check
        val failure = step.verify(emptyMap())
        assertNotNull(failure)
        assertTrue(failure.contains(PluginPlanResolver.ILLEGAL_SETTING_VALUE))
    }

    @Test
    fun `cache key changes when customization settings change and is stable otherwise`() {
        val stage = ModuleApplicationStage()
        val custom1 = CustomizationSettings(enabledPackages = listOf("mod-a"))
        val custom2 = CustomizationSettings(enabledPackages = listOf("mod-b"))
        val custom1Same = CustomizationSettings(enabledPackages = listOf("mod-a"))

        val ctx1 = createContext(custom1)
        val ctx2 = createContext(custom2)
        val ctx1Same = createContext(custom1Same)

        val key1 = stage.computeCacheKey(ctx1, "prev-stage-key")
        val key2 = stage.computeCacheKey(ctx2, "prev-stage-key")
        val key1Same = stage.computeCacheKey(ctx1Same, "prev-stage-key")
        val keyDifferentPrev = stage.computeCacheKey(ctx1, "other-stage-key")

        assertNotEquals(key1, key2)
        assertEquals(key1, key1Same)
        assertNotEquals(key1, keyDifferentPrev)
    }

    @Test
    fun `two identical plan calls produce identical steps`() {
        val template = PluginPlanTemplate(
            schemaVersion = 1,
            publisher = "org.ide",
            id = "stable-mod",
            version = "1.0.0",
            settings = emptyList(),
            operations = listOf(
                Operation.Delete(
                    id = "op-1",
                    target = PartitionPath("system", "app/Test"),
                ),
            ),
        )
        val catalog = MapPluginCatalog(
            templates = mapOf("org.ide:stable-mod:1.0.0" to template),
            digests = mapOf("org.ide:stable-mod:1.0.0" to "sha256:digest"),
        )
        val entry = PluginLockfileEntry("org.ide", "stable-mod", "1.0.0", "sha256:digest")
        val stage = ModuleApplicationStage(catalog)
        val ctx = createContext(
            CustomizationSettings(
                enabledPackages = listOf("org.ide:stable-mod"),
                lockfile = PluginLockfile(entries = listOf(entry)),
            ),
        )

        val plan1 = stage.plan(ctx)
        val plan2 = stage.plan(ctx)

        assertEquals(plan1.size, plan2.size)
        assertEquals(plan1.map { it.label }, plan2.map { it.label })
        val write1 = plan1[0] as PipelineStep.WriteFile
        val write2 = plan2[0] as PipelineStep.WriteFile
        assertEquals(write1.relPath, write2.relPath)
        assertEquals(write1.content.decodeToString(), write2.content.decodeToString())

        val sum1 = plan1[2] as PipelineStep.WriteFile
        val sum2 = plan2[2] as PipelineStep.WriteFile
        assertEquals(sum1.relPath, sum2.relPath)
        assertEquals(sum1.content.decodeToString(), sum2.content.decodeToString())
    }
}
