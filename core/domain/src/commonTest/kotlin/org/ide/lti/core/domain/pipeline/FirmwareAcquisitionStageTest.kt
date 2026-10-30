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
import org.ide.lti.core.domain.pipeline.toolkit.ChecksumResult
import org.ide.lti.core.domain.pipeline.toolkit.PackageFormat
import org.ide.lti.core.domain.pipeline.toolkit.PackageManifest
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
    }

    @Test
    fun `a blank archiveRef in IMPORT_ARCHIVE fails with ARCHIVE_REF_MISSING`() {
        for (mode in listOf(AcquisitionMode.IMPORT_ARCHIVE)) {
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

    @Test
    fun md5ExpectedChecksumPlansMd5sumAndSetsPackageChecksumResult() {
        val expectedMd5 = "d41d8cd98f00b204e9800998ecf8427e"
        val ctx = baseContext.copy(
            snapshot = baseContext.snapshot.copy(
                acquisition = baseContext.snapshot.acquisition.copy(expectedSha256 = expectedMd5),
            ),
            runtimeValues = mapOf(RuntimeKeys.ARCHIVE_MD5 to "$expectedMd5  archive.zip"),
        )

        val steps = stage.plan(ctx)
        val toolIds = steps.filterIsInstance<PipelineStep.Tool>().map { it.command.toolId }
        assertTrue("md5sum" in toolIds, "Expected md5sum tool for 32-char digest")
        assertFalse("sha256sum" in toolIds, "sha256sum should not be planned when MD5 is specified")

        val checksumResultStep = steps.filterIsInstance<PipelineStep.Tool>()
            .firstOrNull { it.resultKey == RuntimeKeys.PACKAGE_CHECKSUM_RESULT }
        assertNotNull(checksumResultStep)
        val decoded = ChecksumResult.decode(checksumResultStep.command.args[0])
        assertTrue(decoded.ok)
        assertEquals(expectedMd5, decoded.expected)
        assertEquals(expectedMd5, decoded.actual)
    }

    @Test
    fun detectStepReadsHeaderAndRecordsPackageManifestWhenHexIsPresent() {
        val ctxWithChecksum = baseContext.copy(
            runtimeValues = mapOf(RuntimeKeys.ARCHIVE_SHA256 to "abc"),
        )
        val initialSteps = stage.plan(ctxWithChecksum)
        val headerStep = initialSteps.filterIsInstance<PipelineStep.Tool>()
            .firstOrNull { it.resultKey == RuntimeKeys.PACKAGE_HEADER_HEX }
        assertNotNull(headerStep, "Header read step must be planned after checksum")
        assertEquals("xxd", headerStep.command.toolId)

        val ctxWithZipHex = baseContext.copy(
            runtimeValues = mapOf(
                RuntimeKeys.ARCHIVE_SHA256 to "abc",
                RuntimeKeys.PACKAGE_HEADER_HEX to "504b0304",
            ),
        )
        val replannedSteps = stage.plan(ctxWithZipHex)
        val manifestStep = replannedSteps.filterIsInstance<PipelineStep.Tool>()
            .firstOrNull { it.resultKey == RuntimeKeys.PACKAGE_MANIFEST }
        assertNotNull(manifestStep, "Manifest step must be planned when header hex is present")
        val manifest = PackageManifest.decode(manifestStep.command.args[0])
        assertEquals(PackageFormat.Zip, manifest.format)
        assertNull(manifest.refusal)
        assertNull(manifest.refusal)
    }

    @Test
    fun unknownFormatHeaderRecordsRefusalManifestAndAddsFailingFormatCheck() {
        val ctxWithUnknownHex = baseContext.copy(
            runtimeValues = mapOf(
                RuntimeKeys.ARCHIVE_SHA256 to "abc",
                RuntimeKeys.PACKAGE_HEADER_HEX to "0011223344",
            ),
        )
        val steps = stage.plan(ctxWithUnknownHex)
        val manifestStep = steps.filterIsInstance<PipelineStep.Tool>()
            .firstOrNull { it.resultKey == RuntimeKeys.PACKAGE_MANIFEST }
        assertNotNull(manifestStep)
        val manifest = PackageManifest.decode(manifestStep.command.args[0])
        assertEquals(PackageFormat.Unknown, manifest.format)
        assertNotNull(manifest.refusal)
        assertNotNull(manifest.refusal)

        val formatCheck = steps.filterIsInstance<PipelineStep.Check>()
            .firstOrNull { it.label == "verify package format" }
        assertNotNull(formatCheck, "Failing format Check must be planned on refusal")
        val error = formatCheck.verify(emptyMap())
        assertNotNull(error)
        assertTrue(error.contains("The package format was not recognized"))
    }
}
