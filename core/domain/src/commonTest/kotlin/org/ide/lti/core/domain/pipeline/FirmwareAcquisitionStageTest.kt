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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.ide.lti.core.domain.pipeline.stages.FirmwareAcquisitionStage
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.AcquisitionSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirmwareAcquisitionStageTest {

    private val fx = PipelineFixture()
    private val baseContext = fx.globalOtaContext()
    private val stage = FirmwareAcquisitionStage()

    private fun unsafeAcquisitionSettings(
        base: AcquisitionSettings,
        mode: AcquisitionMode,
        archiveRef: String?,
    ): AcquisitionSettings {
        val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        val allocateInstanceMethod = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
        val instance = allocateInstanceMethod.invoke(unsafe, AcquisitionSettings::class.java) as AcquisitionSettings

        for (field in AcquisitionSettings::class.java.declaredFields) {
            if (java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
            field.isAccessible = true
            when (field.name) {
                "mode" -> field.set(instance, mode)
                "archiveRef" -> field.set(instance, archiveRef)
                else -> field.set(instance, field.get(base))
            }
        }
        return instance
    }

    @Test
    fun `each of the three modes plans the expected steps`() {
        // 1. DOWNLOAD mode
        val downloadCtx = baseContext
        val downloadOutputs = stage.outputs(downloadCtx)
        assertEquals(listOf("firmware/downloaded/NP05J_GB.zip"), downloadOutputs)

        val downloadSteps = stage.plan(downloadCtx)
        val downloadWriteFile = downloadSteps.filterIsInstance<PipelineStep.WriteFile>().single()
        assertEquals("work/acquire/policy.json", downloadWriteFile.relPath)

        val downloadTools = downloadSteps.filterIsInstance<PipelineStep.Tool>().map { it.command.toolId }
        assertTrue("mkdir" in downloadTools)
        assertTrue("curl" in downloadTools)
        assertTrue("file" in downloadTools)
        assertTrue("mv" in downloadTools)
        assertTrue("sha256sum" in downloadTools)
        assertFalse("test" in downloadTools)

        // 2. IMPORT_ARCHIVE mode
        val importSnap = baseContext.snapshot.copy(
            acquisition = baseContext.snapshot.acquisition.copy(
                mode = AcquisitionMode.IMPORT_ARCHIVE,
                archiveRef = "fw/custom_import.zip",
            ),
        )
        val importCtx = baseContext.copy(snapshot = importSnap)
        val importOutputs = stage.outputs(importCtx)
        assertEquals(listOf("fw/custom_import.zip"), importOutputs)

        val importSteps = stage.plan(importCtx)
        val importWriteFile = importSteps.filterIsInstance<PipelineStep.WriteFile>().single()
        assertEquals("work/acquire/policy.json", importWriteFile.relPath)

        val importTools = importSteps.filterIsInstance<PipelineStep.Tool>().map { it.command.toolId }
        assertTrue("test" in importTools)
        assertTrue("sha256sum" in importTools)
        assertFalse("curl" in importTools)
        assertFalse("mkdir" in importTools)
        assertFalse("mv" in importTools)

        // 3. EXISTING_ARCHIVE mode
        val existingSnap = baseContext.snapshot.copy(
            acquisition = baseContext.snapshot.acquisition.copy(
                mode = AcquisitionMode.EXISTING_ARCHIVE,
                archiveRef = "fw/existing_archive.zip",
            ),
        )
        val existingCtx = baseContext.copy(snapshot = existingSnap)
        val existingOutputs = stage.outputs(existingCtx)
        assertEquals(listOf("fw/existing_archive.zip"), existingOutputs)

        val existingSteps = stage.plan(existingCtx)
        val existingWriteFile = existingSteps.filterIsInstance<PipelineStep.WriteFile>().single()
        assertEquals("work/acquire/policy.json", existingWriteFile.relPath)

        val existingProbeCheck = existingSteps.filterIsInstance<PipelineStep.Check>()
            .single { it.label == "verify existing archive probe" }
        assertNull(existingProbeCheck.verify(mapOf("archive.probe.result" to "ok")))
        assertNotNull(existingProbeCheck.verify(mapOf("archive.probe.result" to "corrupt")))
        assertNotNull(existingProbeCheck.verify(emptyMap()))

        val existingTools = existingSteps.filterIsInstance<PipelineStep.Tool>().map { it.command.toolId }
        assertTrue("sha256sum" in existingTools)
        assertFalse("test" in existingTools, "EXISTING_ARCHIVE must not plan import steps")
        assertFalse("curl" in existingTools, "EXISTING_ARCHIVE must not plan download steps")
        assertFalse("mkdir" in existingTools, "EXISTING_ARCHIVE must not plan download steps")
        assertFalse("mv" in existingTools, "EXISTING_ARCHIVE must not plan download steps")
    }

    @Test
    fun `a blank archiveRef in IMPORT_ARCHIVE and EXISTING_ARCHIVE fails with ARCHIVE_REF_MISSING`() {
        for (mode in listOf(AcquisitionMode.IMPORT_ARCHIVE, AcquisitionMode.EXISTING_ARCHIVE)) {
            val blankAcquisition = unsafeAcquisitionSettings(
                base = baseContext.snapshot.acquisition,
                mode = mode,
                archiveRef = "",
            )
            val blankSnap = baseContext.snapshot.copy(acquisition = blankAcquisition)
            val blankCtx = baseContext.copy(snapshot = blankSnap)

            val steps = stage.plan(blankCtx)
            assertEquals(1, steps.size, "Blank archiveRef must immediately return failure check")
            val check = steps.single() as PipelineStep.Check
            val error = check.verify(emptyMap())
            assertNotNull(error)
            assertTrue(
                error.contains("ARCHIVE_REF_MISSING"),
                "Expected failure naming ARCHIVE_REF_MISSING for mode $mode, got: $error",
            )
        }
    }

    @Test
    fun `an expectedSha256 adds the comparison Check and its absence does not`() {
        val expectedDigest = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
        val ctxWithExpected = baseContext.copy(
            snapshot = baseContext.snapshot.copy(
                acquisition = baseContext.snapshot.acquisition.copy(
                    expectedSha256 = expectedDigest,
                ),
            ),
            runtimeValues = mapOf(RuntimeKeys.ARCHIVE_SHA256 to "$expectedDigest  archive.zip"),
        )
        val stepsWithExpected = stage.plan(ctxWithExpected)
        val checkWithExpected = stepsWithExpected.filterIsInstance<PipelineStep.Check>()
            .firstOrNull { it.label == "verify archive checksum" }
        assertNotNull(checkWithExpected, "Expected comparison check when expectedSha256 is present")
        assertNull(checkWithExpected.verify(ctxWithExpected.runtimeValues))

        val mismatchValues = mapOf(
            RuntimeKeys.ARCHIVE_SHA256 to
                "0000000000000000000000000000000000000000000000000000000000000000  archive.zip",
        )
        val mismatchError = checkWithExpected.verify(mismatchValues)
        assertNotNull(mismatchError)
        assertTrue(
            mismatchError.contains("CHECKSUM_MISMATCH"),
            "Expected error to contain CHECKSUM_MISMATCH, got: $mismatchError",
        )

        // Absence of expected checksum
        val ctxWithoutExpected = baseContext.copy(
            snapshot = baseContext.snapshot.copy(
                acquisition = baseContext.snapshot.acquisition.copy(
                    expectedSha256 = null,
                    firmware = baseContext.snapshot.acquisition.firmware.copy(sha256 = null),
                ),
            ),
            runtimeValues = mapOf(RuntimeKeys.ARCHIVE_SHA256 to "$expectedDigest  archive.zip"),
        )
        val stepsWithoutExpected = stage.plan(ctxWithoutExpected)
        val checkWithoutExpected = stepsWithoutExpected.filterIsInstance<PipelineStep.Check>()
            .firstOrNull { it.label == "verify archive checksum" }
        assertNull(checkWithoutExpected, "Expected NO comparison check when expectedSha256 is absent")
    }

    @Test
    fun `policy json content matches retries resume region`() {
        val writeFile = stage.plan(baseContext).filterIsInstance<PipelineStep.WriteFile>().single()
        assertEquals("work/acquire/policy.json", writeFile.relPath)
        val jsonString = writeFile.content.decodeToString()
        val parsed = Json.parseToJsonElement(jsonString).jsonObject

        assertEquals(3, parsed["retries"]?.jsonPrimitive?.int)
        assertEquals(true, parsed["resume"]?.jsonPrimitive?.boolean)
        assertEquals("GLOBAL", parsed["region"]?.jsonPrimitive?.content)

        val customCtx = baseContext.copy(
            snapshot = baseContext.snapshot.copy(
                acquisition = baseContext.snapshot.acquisition.copy(
                    retries = 5,
                    resume = false,
                    region = TargetRegion.CHINA,
                ),
            ),
        )
        val customWriteFile = stage.plan(customCtx).filterIsInstance<PipelineStep.WriteFile>().single()
        val customParsed = Json.parseToJsonElement(customWriteFile.content.decodeToString()).jsonObject

        assertEquals(5, customParsed["retries"]?.jsonPrimitive?.int)
        assertEquals(false, customParsed["resume"]?.jsonPrimitive?.boolean)
        assertEquals("CHINA", customParsed["region"]?.jsonPrimitive?.content)
    }

    @Test
    fun `the cache key changes for each of region retries resume archiveRef and expectedSha256`() {
        val prevKey = "prev-stage-key"
        val baseKey = stage.computeCacheKey(baseContext, prevKey)

        val mutatedRegion = baseContext.copy(
            snapshot = baseContext.snapshot.copy(
                acquisition = baseContext.snapshot.acquisition.copy(region = TargetRegion.CHINA),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedRegion, prevKey), "region must change cache key")

        val mutatedRetries = baseContext.copy(
            snapshot = baseContext.snapshot.copy(
                acquisition = baseContext.snapshot.acquisition.copy(retries = 5),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedRetries, prevKey), "retries must change cache key")

        val mutatedResume = baseContext.copy(
            snapshot = baseContext.snapshot.copy(
                acquisition = baseContext.snapshot.acquisition.copy(resume = false),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedResume, prevKey), "resume must change cache key")

        val mutatedArchiveRef = baseContext.copy(
            snapshot = baseContext.snapshot.copy(
                acquisition = baseContext.snapshot.acquisition.copy(archiveRef = "fw/different_ref.zip"),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedArchiveRef, prevKey), "archiveRef must change cache key")

        val mutatedSha = baseContext.copy(
            snapshot = baseContext.snapshot.copy(
                acquisition = baseContext.snapshot.acquisition.copy(expectedSha256 = "new-expected-sha"),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedSha, prevKey), "expectedSha256 must change cache key")
    }
}
