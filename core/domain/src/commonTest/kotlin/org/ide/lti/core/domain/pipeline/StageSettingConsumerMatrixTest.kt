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

import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage
import org.ide.lti.core.domain.pipeline.stages.DebloatStage
import org.ide.lti.core.domain.pipeline.stages.FirmwareAcquisitionStage
import org.ide.lti.core.domain.pipeline.stages.FirmwareExtractionStage
import org.ide.lti.core.domain.pipeline.stages.GenerateOtaManifestStage
import org.ide.lti.core.domain.pipeline.stages.ModuleApplicationStage
import org.ide.lti.core.domain.pipeline.stages.WorkTreeAssemblyStage
import org.ide.lti.core.model.target.FirmwareSourceKind
import org.ide.lti.core.model.target.MetadataPolicy
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.PluginLockfile
import org.ide.lti.core.model.workspace.PluginLockfileEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Task T078: Setting-to-consumer coverage matrix test scoped to what exists today.
 *
 * Table-driven test verifying that each settings group field with an active consumer
 * changes its owning stage's cache key, and verifying that every currently unconsumed
 * field is explicitly recorded in [UNCONSUMED_FIELDS] with the task planned to consume it.
 */
class StageSettingConsumerMatrixTest {

    private val fx = PipelineFixture()
    private val baseContext = fx.globalOtaContext()
    private val baseSnapshot = baseContext.snapshot
    private val prevKey = "prev-stage-cache-key-12345"

    data class ConsumedFieldCase(
        val fieldName: String,
        val stage: StageDefinition,
        val mutate: (ConfigurationSnapshot) -> ConfigurationSnapshot,
    )

    data class UnconsumedField(
        val fieldName: String,
        val targetTask: String,
        val stage: StageDefinition,
        val mutate: (ConfigurationSnapshot) -> ConfigurationSnapshot,
    )

    companion object {
        /**
         * Explicit register of settings fields that have no consumer in stage cache keys today.
         *
         * When a task wires an unconsumed field into its owning stage's cache key, this test
         * will fail loudly until the field is moved from this list to the consumed matrix.
         */
        val UNCONSUMED_FIELDS: List<UnconsumedField> = emptyList()
    }

    private val consumedFieldCases: List<ConsumedFieldCase> = listOf(
        ConsumedFieldCase(
            fieldName = "build.filenameTemplate",
            stage = BuildFlashableZipStage(),
            mutate = { snap -> snap.copy(build = snap.build.copy(filenameTemplate = "ROM_{version}.zip")) },
        ),
        // AcquisitionSettings
        ConsumedFieldCase(
            fieldName = "acquisition.mode",
            stage = FirmwareAcquisitionStage(),
            mutate = { snap ->
                snap.copy(
                    acquisition = snap.acquisition.copy(
                        mode = AcquisitionMode.IMPORT_ARCHIVE,
                        archiveRef = "fw/archive.zip",
                    ),
                )
            },
        ),
        ConsumedFieldCase(
            fieldName = "acquisition.region",
            stage = FirmwareAcquisitionStage(),
            mutate = { snap -> snap.copy(acquisition = snap.acquisition.copy(region = TargetRegion.CHINA)) },
        ),
        ConsumedFieldCase(
            fieldName = "acquisition.archiveRef",
            stage = FirmwareAcquisitionStage(),
            mutate = { snap -> snap.copy(acquisition = snap.acquisition.copy(archiveRef = "fw/archive.zip")) },
        ),
        ConsumedFieldCase(
            fieldName = "acquisition.expectedSha256",
            stage = FirmwareAcquisitionStage(),
            mutate = { snap -> snap.copy(acquisition = snap.acquisition.copy(expectedSha256 = "custom-sha256")) },
        ),
        ConsumedFieldCase(
            fieldName = "acquisition.retries",
            stage = FirmwareAcquisitionStage(),
            mutate = { snap -> snap.copy(acquisition = snap.acquisition.copy(retries = 5)) },
        ),
        ConsumedFieldCase(
            fieldName = "acquisition.resume",
            stage = FirmwareAcquisitionStage(),
            mutate = { snap -> snap.copy(acquisition = snap.acquisition.copy(resume = false)) },
        ),
        ConsumedFieldCase(
            fieldName = "acquisition.firmware.acquisitionUrl",
            stage = FirmwareAcquisitionStage(),
            mutate = { snap ->
                snap.copy(
                    acquisition = snap.acquisition.copy(
                        firmware = snap.acquisition.firmware.copy(acquisitionUrl = "https://new.url/fw.zip"),
                    ),
                )
            },
        ),
        ConsumedFieldCase(
            fieldName = "acquisition.firmware.sha256",
            stage = FirmwareAcquisitionStage(),
            mutate = { snap ->
                snap.copy(
                    acquisition = snap.acquisition.copy(
                        firmware = snap.acquisition.firmware.copy(sha256 = "fedcba9876543210"),
                    ),
                )
            },
        ),
        ConsumedFieldCase(
            fieldName = "acquisition.firmware.sourceKind",
            stage = FirmwareAcquisitionStage(),
            mutate = { snap ->
                snap.copy(
                    acquisition = snap.acquisition.copy(
                        firmware = snap.acquisition.firmware.copy(sourceKind = FirmwareSourceKind.RAW_IMAGE_ZIP),
                    ),
                )
            },
        ),

        // ExtractionSettings
        ConsumedFieldCase(
            fieldName = "extraction.adapter",
            stage = FirmwareExtractionStage(),
            mutate = { snap -> snap.copy(extraction = snap.extraction.copy(adapter = "lpunpack")) },
        ),
        ConsumedFieldCase(
            fieldName = "extraction.dynamicPartitions",
            stage = FirmwareExtractionStage(),
            mutate = { snap -> snap.copy(extraction = snap.extraction.copy(dynamicPartitions = listOf("system"))) },
        ),
        ConsumedFieldCase(
            fieldName = "extraction.bootPartitions",
            stage = FirmwareExtractionStage(),
            mutate = { snap -> snap.copy(extraction = snap.extraction.copy(bootPartitions = listOf("boot"))) },
        ),
        ConsumedFieldCase(
            fieldName = "extraction.slot",
            stage = FirmwareExtractionStage(),
            mutate = { snap -> snap.copy(extraction = snap.extraction.copy(slot = "_b")) },
        ),
        ConsumedFieldCase(
            fieldName = "extraction.reuseVerifiedExtraction",
            stage = FirmwareExtractionStage(),
            mutate = { snap -> snap.copy(extraction = snap.extraction.copy(reuseVerifiedExtraction = false)) },
        ),

        // AssemblySettings
        ConsumedFieldCase(
            fieldName = "assembly.buildType",
            stage = WorkTreeAssemblyStage(),
            mutate = { snap -> snap.copy(assembly = snap.assembly.copy(buildType = "eng")) },
        ),
        ConsumedFieldCase(
            fieldName = "assembly.romVersion",
            stage = WorkTreeAssemblyStage(),
            mutate = { snap -> snap.copy(assembly = snap.assembly.copy(romVersion = "2.0.0")) },
        ),
        ConsumedFieldCase(
            fieldName = "assembly.includedPartitions",
            stage = WorkTreeAssemblyStage(),
            mutate = { snap -> snap.copy(assembly = snap.assembly.copy(includedPartitions = listOf("vendor"))) },
        ),
        ConsumedFieldCase(
            fieldName = "assembly.systemExtMode",
            stage = WorkTreeAssemblyStage(),
            mutate = { snap -> snap.copy(assembly = snap.assembly.copy(systemExtMode = "fold")) },
        ),
        ConsumedFieldCase(
            fieldName = "assembly.bootFooterPolicy",
            stage = WorkTreeAssemblyStage(),
            mutate = { snap -> snap.copy(assembly = snap.assembly.copy(bootFooterPolicy = "preserve")) },
        ),
        ConsumedFieldCase(
            fieldName = "assembly.baselineCleanup",
            stage = WorkTreeAssemblyStage(),
            mutate = { snap -> snap.copy(assembly = snap.assembly.copy(baselineCleanup = false)) },
        ),
        ConsumedFieldCase(
            fieldName = "assembly.propertyOverrides",
            stage = WorkTreeAssemblyStage(),
            mutate = { snap ->
                snap.copy(assembly = snap.assembly.copy(propertyOverrides = mapOf("ro.custom" to "1")))
            },
        ),

        // DebloatSettings
        ConsumedFieldCase(
            fieldName = "debloat.enabled",
            stage = DebloatStage(),
            mutate = { snap -> snap.copy(debloat = snap.debloat.copy(enabled = true)) },
        ),
        ConsumedFieldCase(
            fieldName = "debloat.presetId",
            stage = DebloatStage(),
            mutate = { snap -> snap.copy(debloat = snap.debloat.copy(presetId = "preset_aggressive")) },
        ),
        ConsumedFieldCase(
            fieldName = "debloat.removeSelectors",
            stage = DebloatStage(),
            mutate = { snap -> snap.copy(debloat = snap.debloat.copy(removeSelectors = listOf("system/app/Bloat"))) },
        ),

        // CustomizationSettings
        ConsumedFieldCase(
            fieldName = "customization.enabledPackages",
            stage = ModuleApplicationStage(),
            mutate = { snap ->
                snap.copy(customization = snap.customization.copy(enabledPackages = listOf("org.ide:new-mod")))
            },
        ),
        ConsumedFieldCase(
            fieldName = "customization.settings",
            stage = ModuleApplicationStage(),
            mutate = { snap ->
                snap.copy(
                    customization = snap.customization.copy(
                        settings = mapOf("mod" to mapOf("k" to "v")),
                    ),
                )
            },
        ),
        ConsumedFieldCase(
            fieldName = "customization.lockfile",
            stage = ModuleApplicationStage(),
            mutate = { snap ->
                snap.copy(
                    customization = snap.customization.copy(
                        lockfile = PluginLockfile(
                            entries = listOf(PluginLockfileEntry("org.ide", "mod", "1.0.0", "sha256:digest")),
                        ),
                    ),
                )
            },
        ),

        // BuildSettings
        ConsumedFieldCase(
            fieldName = "build.filesystem",
            stage = BuildFlashableZipStage(),
            mutate = { snap -> snap.copy(build = snap.build.copy(filesystem = "f2fs")) },
        ),
        ConsumedFieldCase(
            fieldName = "build.compression",
            stage = BuildFlashableZipStage(),
            mutate = { snap -> snap.copy(build = snap.build.copy(compression = "lz4")) },
        ),
        ConsumedFieldCase(
            fieldName = "build.level",
            stage = BuildFlashableZipStage(),
            mutate = { snap -> snap.copy(build = snap.build.copy(level = 4)) },
        ),
        ConsumedFieldCase(
            fieldName = "build.blockSize",
            stage = BuildFlashableZipStage(),
            mutate = { snap -> snap.copy(build = snap.build.copy(blockSize = 8192)) },
        ),
        ConsumedFieldCase(
            fieldName = "build.alignment",
            stage = BuildFlashableZipStage(),
            mutate = { snap -> snap.copy(build = snap.build.copy(alignment = 4096)) },
        ),
        ConsumedFieldCase(
            fieldName = "build.packagePolicy.flashablePartitions",
            stage = BuildFlashableZipStage(),
            mutate = { snap ->
                snap.copy(
                    build = snap.build.copy(
                        packagePolicy = snap.build.packagePolicy.copy(flashablePartitions = listOf("system")),
                    ),
                )
            },
        ),
        ConsumedFieldCase(
            fieldName = "build.packagePolicy.flashableBootPartitions",
            stage = BuildFlashableZipStage(),
            mutate = { snap ->
                snap.copy(
                    build = snap.build.copy(
                        packagePolicy = snap.build.packagePolicy.copy(flashableBootPartitions = listOf("boot")),
                    ),
                )
            },
        ),
        ConsumedFieldCase(
            fieldName = "build.packagePolicy.bootSlots",
            stage = BuildFlashableZipStage(),
            mutate = { snap ->
                val policy = snap.build.packagePolicy.copy(bootSlots = listOf("a"))
                snap.copy(build = snap.build.copy(packagePolicy = policy))
            },
        ),
        ConsumedFieldCase(
            fieldName = "build.packagePolicy.excludeVbmeta",
            stage = BuildFlashableZipStage(),
            mutate = { snap ->
                val policy = snap.build.packagePolicy.copy(excludeVbmeta = false)
                snap.copy(build = snap.build.copy(packagePolicy = policy))
            },
        ),
        ConsumedFieldCase(
            fieldName = "build.packagePolicy.recoverySystemMountPoint",
            stage = BuildFlashableZipStage(),
            mutate = { snap ->
                val policy = snap.build.packagePolicy.copy(recoverySystemMountPoint = "/system")
                snap.copy(build = snap.build.copy(packagePolicy = policy))
            },
        ),
        ConsumedFieldCase(
            fieldName = "build.packagePolicy.metadataPolicy",
            stage = BuildFlashableZipStage(),
            mutate = { snap ->
                val policy = snap.build.packagePolicy.copy(metadataPolicy = MetadataPolicy.PACKAGE_ONLY)
                snap.copy(build = snap.build.copy(packagePolicy = policy))
            },
        ),
        ConsumedFieldCase(
            fieldName = "build.signing.signImages",
            stage = BuildFlashableZipStage(),
            mutate = { snap ->
                snap.copy(
                    build = snap.build.copy(
                        signing = snap.build.signing.copy(signImages = !snap.build.signing.signImages),
                    ),
                )
            },
        ),
        ConsumedFieldCase(
            fieldName = "build.signing.signPackage",
            stage = BuildFlashableZipStage(),
            mutate = { snap ->
                snap.copy(
                    build = snap.build.copy(
                        signing = snap.build.signing.copy(signPackage = !snap.build.signing.signPackage),
                    ),
                )
            },
        ),
        ConsumedFieldCase(
            fieldName = "build.signing.avbKeyRef",
            stage = BuildFlashableZipStage(),
            mutate = { snap ->
                snap.copy(
                    build = snap.build.copy(
                        signing = snap.build.signing.copy(avbKeyRef = "keys/custom_avb.pem"),
                    ),
                )
            },
        ),
        ConsumedFieldCase(
            fieldName = "build.signing.platformKeyRef",
            stage = BuildFlashableZipStage(),
            mutate = { snap ->
                snap.copy(
                    build = snap.build.copy(
                        signing = snap.build.signing.copy(platformKeyRef = "keys/custom_platform"),
                    ),
                )
            },
        ),

        // ReleaseSettings
        ConsumedFieldCase(
            fieldName = "release.otaBaseUrl",
            stage = GenerateOtaManifestStage(),
            mutate = { snap ->
                snap.copy(
                    release = snap.release.copy(otaBaseUrl = "https://updates.custom.org/ota"),
                )
            },
        ),
        ConsumedFieldCase(
            fieldName = "release.channel",
            stage = GenerateOtaManifestStage(),
            mutate = { snap -> snap.copy(release = snap.release.copy(channel = "beta")) },
        ),
        ConsumedFieldCase(
            fieldName = "release.changelog",
            stage = GenerateOtaManifestStage(),
            mutate = { snap -> snap.copy(release = snap.release.copy(changelog = "Initial beta changelog")) },
        ),
    )

    @Test
    fun `each consumed field changes its owning stage cache key`() {
        for (case in consumedFieldCases) {
            val baseKey = case.stage.computeCacheKey(baseContext, prevKey)
            val mutatedSnap = case.mutate(baseSnapshot)
            val mutatedCtx = baseContext.copy(snapshot = mutatedSnap)
            val mutatedKey = case.stage.computeCacheKey(mutatedCtx, prevKey)

            assertNotEquals(
                baseKey,
                mutatedKey,
                "Field '${case.fieldName}' is registered as CONSUMED, but mutating it did not change " +
                    "the cache key for stage '${case.stage.id.name}'",
            )
        }
    }

    @Test
    fun `each unconsumed field does NOT change its owning stage cache key`() {
        for (unconsumed in UNCONSUMED_FIELDS) {
            val baseKey = unconsumed.stage.computeCacheKey(baseContext, prevKey)
            val mutatedSnap = unconsumed.mutate(baseSnapshot)
            val mutatedCtx = baseContext.copy(snapshot = mutatedSnap)
            val mutatedKey = unconsumed.stage.computeCacheKey(mutatedCtx, prevKey)

            assertEquals(
                baseKey,
                mutatedKey,
                "Field '${unconsumed.fieldName}' is listed in UNCONSUMED_FIELDS " +
                    "(target task: '${unconsumed.targetTask}'), " +
                    "but mutating it changed the cache key of stage '${unconsumed.stage.id.name}'! " +
                    "Update UNCONSUMED_FIELDS and move this field to consumedFieldCases.",
            )
        }
    }

    @Test
    fun `unconsumed fields are uniquely named and each names the task that will consume it`() {
        // An empty register is the goal state: every settings field has a consumer.
        val fieldNames = UNCONSUMED_FIELDS.map { it.fieldName }
        assertEquals(fieldNames.distinct().size, fieldNames.size, "Duplicate field names in UNCONSUMED_FIELDS")

        for (unconsumed in UNCONSUMED_FIELDS) {
            assertTrue(
                unconsumed.targetTask.isNotBlank(),
                "Target task must be specified for unconsumed field '${unconsumed.fieldName}'",
            )
        }
    }
}
