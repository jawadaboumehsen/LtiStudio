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

import org.ide.lti.core.domain.pipeline.stages.WorkTreeAssemblyStage
import org.ide.lti.core.model.workspace.AssemblySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorkTreeAssemblyStageTest {

    private val fx = PipelineFixture()
    private val stage = WorkTreeAssemblyStage()
    private val prevKey = "prev-assembly-cache-key"

    @Test
    fun `happy default preserves todays plan step labels for global ota`() {
        val ctx = fx.globalOtaContext()
        val defaultSteps = stage.plan(ctx)
        val labels = defaultSteps.map { it.label }

        val expectedLabels = listOf(
            "mkdir work",
            "rsync odm",
            "cp fs_config odm",
            "cp file_context odm",
            "rsync product",
            "cp fs_config product",
            "cp file_context product",
            "rsync system",
            "cp fs_config system",
            "cp file_context system",
            "rsync system_dlkm",
            "cp fs_config system_dlkm",
            "cp file_context system_dlkm",
            "rsync system_ext",
            "cp fs_config system_ext",
            "cp file_context system_ext",
            "rsync vendor",
            "cp fs_config vendor",
            "cp file_context vendor",
            "rsync vendor_dlkm",
            "cp fs_config vendor_dlkm",
            "cp file_context vendor_dlkm",
            "cp kernel boot",
            "avbtool info kernel boot",
            "cp kernel init_boot",
            "avbtool info kernel init_boot",
            "cp kernel vendor_boot",
            "avbtool info kernel vendor_boot",
            "cp kernel dtbo",
            "avbtool info kernel dtbo",
            "purge oat dirs",
            "purge aot files",
            "stamp build.prop",
        )
        assertEquals(expectedLabels, labels)
    }

    @Test
    fun `includedPartitions narrows assembled partitions and changes outputs`() {
        val ctx = fx.globalOtaContext()
        val narrowedSnap = ctx.snapshot.copy(
            assembly = ctx.snapshot.assembly.copy(includedPartitions = listOf("system", "vendor")),
        )
        val narrowedCtx = ctx.copy(snapshot = narrowedSnap)
        val steps = stage.plan(narrowedCtx)
        val labels = steps.map { it.label }

        assertTrue(labels.contains("rsync system"))
        assertTrue(labels.contains("rsync vendor"))
        assertFalse(labels.contains("rsync odm"))
        assertFalse(labels.contains("rsync product"))

        val outputs = stage.outputs(narrowedCtx)
        val expectedOutputs = listOf(
            WorkTreeAssemblyStage.BUILD_PROP,
            "work/configs/fs_config-system",
            "work/configs/file_context-system",
            "work/configs/fs_config-vendor",
            "work/configs/file_context-vendor",
        )
        assertEquals(expectedOutputs, outputs)
    }

    @Test
    fun `includedPartitions with partition not on target plans failing check PARTITION_NOT_ON_TARGET`() {
        val ctx = fx.globalOtaContext()
        val invalidSnap = ctx.snapshot.copy(
            assembly = ctx.snapshot.assembly.copy(includedPartitions = listOf("unknown_partition")),
        )
        val steps = stage.plan(ctx.copy(snapshot = invalidSnap))
        val check = steps.filterIsInstance<PipelineStep.Check>().firstOrNull()
        assertNotNull(check)
        val error = check.verify(emptyMap())
        assertNotNull(error)
        assertTrue(error.contains("PARTITION_NOT_ON_TARGET"))
    }

    @Test
    fun `systemExtMode auto preserves target-conditional fold behaviour`() {
        val globalCtx = fx.globalOtaContext()
        val globalSteps = stage.plan(globalCtx)
        assertFalse(globalSteps.any { it.label.contains("fold") }, "Global target must not fold system_ext in auto")

        val chinaCtx = fx.chinaRawContext()
        val chinaSteps = stage.plan(chinaCtx)
        assertTrue(chinaSteps.any { it.label.contains("system_ext fold") }, "China target must fold system_ext in auto")
    }

    @Test
    fun `systemExtMode fold forces fold and standalone skips fold`() {
        val globalCtx = fx.globalOtaContext()
        val foldSnap = globalCtx.snapshot.copy(
            assembly = globalCtx.snapshot.assembly.copy(systemExtMode = "fold"),
        )
        val foldSteps = stage.plan(globalCtx.copy(snapshot = foldSnap))
        assertTrue(foldSteps.any { it.label.contains("system_ext fold") }, "systemExtMode=fold must force fold")

        val chinaCtx = fx.chinaRawContext()
        val standaloneSnap = chinaCtx.snapshot.copy(
            assembly = chinaCtx.snapshot.assembly.copy(systemExtMode = "standalone"),
        )
        val standaloneSteps = stage.plan(chinaCtx.copy(snapshot = standaloneSnap))
        assertFalse(
            standaloneSteps.any { it.label.contains("system_ext fold") },
            "systemExtMode=standalone must skip fold",
        )
    }

    @Test
    fun `systemExtMode unsupported plans failing check UNSUPPORTED_SYSTEM_EXT_MODE`() {
        val ctx = fx.globalOtaContext()
        val invalidSnap = ctx.snapshot.copy(
            assembly = ctx.snapshot.assembly.copy(systemExtMode = "manual"),
        )
        val steps = stage.plan(ctx.copy(snapshot = invalidSnap))
        val check = steps.filterIsInstance<PipelineStep.Check>().firstOrNull()
        assertNotNull(check)
        val error = check.verify(emptyMap())
        assertNotNull(error)
        assertTrue(error.contains("UNSUPPORTED_SYSTEM_EXT_MODE"))
    }

    @Test
    fun `bootFooterPolicy erase keeps erase steps and preserve skips them`() {
        val ctx = fx.globalOtaContext().copy(
            runtimeValues = mapOf(RuntimeKeys.exitOf(RuntimeKeys.kernelInfo("boot")) to "0"),
        )
        val eraseSteps = stage.plan(ctx)
        assertTrue(eraseSteps.any { it.label == "erase footer boot" })

        val preserveSnap = ctx.snapshot.copy(
            assembly = ctx.snapshot.assembly.copy(bootFooterPolicy = "preserve"),
        )
        val preserveSteps = stage.plan(ctx.copy(snapshot = preserveSnap))
        assertFalse(preserveSteps.any { it.label == "erase footer boot" })

        val invalidSnap = ctx.snapshot.copy(
            assembly = ctx.snapshot.assembly.copy(bootFooterPolicy = "keep"),
        )
        val invalidSteps = stage.plan(ctx.copy(snapshot = invalidSnap))
        val check = invalidSteps.filterIsInstance<PipelineStep.Check>().firstOrNull()
        assertNotNull(check)
        val error = check.verify(emptyMap())
        assertNotNull(error)
        assertTrue(error.contains("UNSUPPORTED_FOOTER_POLICY"))
    }

    @Test
    fun `baselineCleanup false skips oat and aot purge`() {
        val ctx = fx.globalOtaContext()
        val noCleanupSnap = ctx.snapshot.copy(
            assembly = ctx.snapshot.assembly.copy(baselineCleanup = false),
        )
        val steps = stage.plan(ctx.copy(snapshot = noCleanupSnap))
        assertFalse(steps.any { it.label == "purge oat dirs" })
        assertFalse(steps.any { it.label == "purge aot files" })
    }

    @Test
    fun `propertyOverrides reach build prop edit and rejects reserved or invalid property`() {
        val ctx = fx.globalOtaContext().copy(
            runtimeValues = mapOf(RuntimeKeys.RUN_DATE to "20260914"),
        )
        val overridesSnap = ctx.snapshot.copy(
            assembly = ctx.snapshot.assembly.copy(
                propertyOverrides = mapOf("ro.custom.feature" to "enabled"),
            ),
        )
        val steps = stage.plan(ctx.copy(snapshot = overridesSnap))
        val stampStep = steps.filterIsInstance<PipelineStep.EditFile>().first {
            it.relPath == WorkTreeAssemblyStage.BUILD_PROP
        }
        val stamped = stampStep.transform("ro.build.type=user\n")
        assertTrue(stamped.contains("ro.custom.feature=enabled"), "Overrides must reach build.prop: $stamped")

        // Reserved keys
        for (reservedKey in listOf("ro.build.version.release", "ro.build.version.security_patch")) {
            val reservedSnap = ctx.snapshot.copy(
                assembly = ctx.snapshot.assembly.copy(
                    propertyOverrides = mapOf(reservedKey to "15"),
                ),
            )
            val reservedSteps = stage.plan(ctx.copy(snapshot = reservedSnap))
            val check = reservedSteps.filterIsInstance<PipelineStep.Check>().firstOrNull()
            assertNotNull(check)
            val error = check.verify(emptyMap())
            assertNotNull(error)
            assertTrue(error.contains("RESERVED_PROPERTY"), "Expected RESERVED_PROPERTY for $reservedKey: $error")
        }

        // Invalid keys with newline or NUL (constructed through reflection or unsafe allocation if needed)
        val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        val allocateInstance = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
        val rawSettings = allocateInstance.invoke(unsafe, AssemblySettings::class.java) as AssemblySettings

        for (field in AssemblySettings::class.java.declaredFields) {
            if (java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
            field.isAccessible = true
            when (field.name) {
                "propertyOverrides" -> field.set(rawSettings, mapOf("invalid\nkey" to "val"))
                "includedPartitions" -> field.set(rawSettings, emptyList<String>())
                "systemExtMode" -> field.set(rawSettings, "auto")
                "bootFooterPolicy" -> field.set(rawSettings, "erase")
                "baselineCleanup" -> field.set(rawSettings, true)
                "buildType" -> field.set(rawSettings, "user")
                "romVersion" -> field.set(rawSettings, "1.0.0")
            }
        }
        val invalidSnap = ctx.snapshot.copy(assembly = rawSettings)
        val invalidSteps = stage.plan(ctx.copy(snapshot = invalidSnap))
        val invalidCheck = invalidSteps.filterIsInstance<PipelineStep.Check>().firstOrNull()
        assertNotNull(invalidCheck)
        val invalidError = invalidCheck.verify(emptyMap())
        assertNotNull(invalidError)
        assertTrue(invalidError.contains("INVALID_PROPERTY_OVERRIDE"))
    }

    @Test
    fun `each assembly setting field changes the cache key`() {
        val ctx = fx.globalOtaContext()
        val baseKey = stage.computeCacheKey(ctx, prevKey)

        val mutatedPartitions = ctx.copy(
            snapshot = ctx.snapshot.copy(
                assembly = ctx.snapshot.assembly.copy(includedPartitions = listOf("system")),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedPartitions, prevKey))

        val mutatedMode = ctx.copy(
            snapshot = ctx.snapshot.copy(
                assembly = ctx.snapshot.assembly.copy(systemExtMode = "fold"),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedMode, prevKey))

        val mutatedFooter = ctx.copy(
            snapshot = ctx.snapshot.copy(
                assembly = ctx.snapshot.assembly.copy(bootFooterPolicy = "preserve"),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedFooter, prevKey))

        val mutatedCleanup = ctx.copy(
            snapshot = ctx.snapshot.copy(
                assembly = ctx.snapshot.assembly.copy(baselineCleanup = false),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedCleanup, prevKey))

        val mutatedBuildType = ctx.copy(
            snapshot = ctx.snapshot.copy(
                assembly = ctx.snapshot.assembly.copy(buildType = "eng"),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedBuildType, prevKey))

        val mutatedRomVersion = ctx.copy(
            snapshot = ctx.snapshot.copy(
                assembly = ctx.snapshot.assembly.copy(romVersion = "2.0.0"),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedRomVersion, prevKey))

        val mutatedProps = ctx.copy(
            snapshot = ctx.snapshot.copy(
                assembly = ctx.snapshot.assembly.copy(propertyOverrides = mapOf("ro.test" to "1")),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedProps, prevKey))
    }
}
