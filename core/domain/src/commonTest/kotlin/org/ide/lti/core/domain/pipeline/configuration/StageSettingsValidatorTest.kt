/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.configuration

import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.AssemblySettings
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.core.model.workspace.CustomizationSettings
import org.ide.lti.core.model.workspace.DebloatSettings
import org.ide.lti.core.model.workspace.ExtractionSettings
import org.ide.lti.core.model.workspace.PluginLockfile
import org.ide.lti.core.model.workspace.PluginLockfileEntry
import org.ide.lti.core.model.workspace.ProjectScript
import org.ide.lti.core.model.workspace.PublishSettings
import org.ide.lti.core.model.workspace.ReleaseSettings
import org.ide.lti.core.model.workspace.SigningPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StageSettingsValidatorTest {

    private val target = DefaultTargetCatalog.PQ84P01_DEFAULT
    private val validFirmware = DefaultTargetCatalog.PQ84P01_GLOBAL_FIRMWARES.first()

    @Test
    fun aVersionThatWouldLeaveThePackageFolderIsRefused() {
        val report = StageSettingsValidator.validateAssembly(AssemblySettings(romVersion = "../1.0"), target)
        assertTrue(report.errors.any { it.fieldPath == "romVersion" })
        assertTrue(
            StageSettingsValidator.validateAssembly(
                AssemblySettings(romVersion = "1.0-beta"),
                target,
            ).errors.none {
                it.fieldPath == "romVersion"
            },
        )
    }

    @Test
    fun aDeviceIdThatWouldLeaveThePackageFolderIsRefused() {
        val build = BuildSettings(filenameTemplate = "{device}.zip")
        val escaping = StageSettingsValidator.validateBuild(build, target.copy(id = "../x"))
        assertTrue(escaping.errors.any { it.fieldPath == "filenameTemplate" })
        assertTrue(
            StageSettingsValidator.validateBuild(build, target).errors.none { it.fieldPath == "filenameTemplate" },
        )
    }

    @Test
    fun aZeroBlockSizeIsReportedNotThrown() {
        val settings = BuildSettings(blockSize = 0, partitionSizeBytes = mapOf("vendor" to 4096L))
        val report = StageSettingsValidator.validateBuild(settings, target)
        assertTrue(report.errors.any { it.fieldPath == "blockSize" })
    }

    @Test
    fun aRunThroughAnEarlyStageIgnoresLaterStagesProblems() {
        fun error(stage: StageId?) = ValidationError(stage, null, "f", "C", Severity.ERROR, "m")
        val issues = listOf(error(null), error(StageId.FIRMWARE_EXTRACTION), error(StageId.BUILD_FLASHABLE_ZIP))
        assertEquals(2, DeviceConfigReadiness.upTo(issues, StageId.FIRMWARE_EXTRACTION).size)
        assertEquals(3, DeviceConfigReadiness.upTo(issues, null).size)
    }

    @Test
    fun validateAcquisitionReturnsEmptyReportForValidSettings() {
        val settings = AcquisitionSettings(
            mode = AcquisitionMode.DOWNLOAD,
            region = TargetRegion.GLOBAL,
            firmware = validFirmware,
            retries = 3,
        )
        val report = StageSettingsValidator.validateAcquisition(settings, target)
        assertFalse(report.hasBlockingErrors())
        assertTrue(report.errors.isEmpty())
    }

    @Test
    fun validateAcquisitionDetectsTargetIncompatibilities() {
        // retries-in-0..5 and archiveRef-required-for-IMPORT_ARCHIVE are AcquisitionSettings'
        // own constructor invariants (see its init block) - no instance can ever violate them, so
        // StageSettingsValidator does not re-check them and there is nothing to test here for those
        // two rules. Only the target-dependent checks (region/firmware membership) apply.
        val base = AcquisitionSettings(
            mode = AcquisitionMode.DOWNLOAD,
            region = TargetRegion.GLOBAL,
            firmware = validFirmware,
        )

        // 1. Firmware not on target
        val alienFirmware = TargetFirmware(version = "ALIEN_1.0", buildId = "ALIEN_BUILD_999")
        val alienFwSettings = base.copy(firmware = alienFirmware)
        val fwReport = StageSettingsValidator.validateAcquisition(alienFwSettings, target)
        assertTrue(fwReport.errors.any { it.code == StageSettingsValidator.INCOMPATIBLE_FIRMWARE })

        // 2. Region not declared on target
        val targetChinaOnly = target.copy(availableRegions = listOf(TargetRegion.CHINA))
        val regionReport = StageSettingsValidator.validateAcquisition(base, targetChinaOnly)
        assertTrue(regionReport.errors.any { it.code == StageSettingsValidator.INCOMPATIBLE_REGION })
    }

    @Test
    fun validateExtractionReturnsEmptyReportForValidSettings() {
        val settings = ExtractionSettings(
            adapter = "AUTO",
            dynamicPartitions = listOf("system", "vendor"),
            bootPartitions = listOf("boot"),
            slot = "a",
        )
        val report = StageSettingsValidator.validateExtraction(settings, target)
        assertFalse(report.hasBlockingErrors())
        assertTrue(report.errors.isEmpty())
    }

    @Test
    fun validateExtractionDetectsUnsupportedAdaptersSlotsAndPartitions() {
        // Unsupported adapter
        val badAdapter = ExtractionSettings(adapter = "custom_dump_v2")
        val adapterReport = StageSettingsValidator.validateExtraction(badAdapter, target)
        assertTrue(adapterReport.errors.any { it.code == StageSettingsValidator.UNSUPPORTED_EXTRACTION_ADAPTER })

        // Unsupported dynamic partition
        val badDynamic = ExtractionSettings(dynamicPartitions = listOf("unknown_partition"))
        val dynamicReport = StageSettingsValidator.validateExtraction(badDynamic, target)
        assertTrue(dynamicReport.errors.any { it.code == StageSettingsValidator.PARTITION_NOT_ON_TARGET })

        // Unsupported boot partition
        val badBoot = ExtractionSettings(bootPartitions = listOf("recovery_extra"))
        val bootReport = StageSettingsValidator.validateExtraction(badBoot, target)
        assertTrue(bootReport.errors.any { it.code == StageSettingsValidator.PARTITION_NOT_ON_TARGET })

        // Unsupported slot
        val badSlot = ExtractionSettings(slot = "slot_c")
        val slotReport = StageSettingsValidator.validateExtraction(badSlot, target)
        assertTrue(slotReport.errors.any { it.code == StageSettingsValidator.UNSUPPORTED_SLOT })
    }

    @Test
    fun validateAssemblyReturnsEmptyReportForValidSettings() {
        val settings = AssemblySettings(
            includedPartitions = listOf("system", "vendor"),
            systemExtMode = "auto",
            bootFooterPolicy = "erase",
            propertyOverrides = mapOf("ro.product.custom" to "true"),
        )
        val report = StageSettingsValidator.validateAssembly(settings, target)
        assertFalse(report.hasBlockingErrors())
        assertTrue(report.errors.isEmpty())
    }

    @Test
    fun validateAssemblyDetectsPartitionsModesAndPropertyViolations() {
        val base = AssemblySettings()

        // Unsupported included partition
        val badPart = base.copy(includedPartitions = listOf("not_dynamic"))
        val partReport = StageSettingsValidator.validateAssembly(badPart, target)
        assertTrue(partReport.errors.any { it.code == StageSettingsValidator.PARTITION_NOT_ON_TARGET })

        // Unsupported systemExtMode
        val badMode = base.copy(systemExtMode = "invalid_mode")
        val modeReport = StageSettingsValidator.validateAssembly(badMode, target)
        assertTrue(modeReport.errors.any { it.code == StageSettingsValidator.UNSUPPORTED_SYSTEM_EXT_MODE })

        // Unsupported bootFooterPolicy
        val badFooter = base.copy(bootFooterPolicy = "corrupt")
        val footerReport = StageSettingsValidator.validateAssembly(badFooter, target)
        assertTrue(footerReport.errors.any { it.code == StageSettingsValidator.UNSUPPORTED_FOOTER_POLICY })

        // Newline/NUL rejection in propertyOverrides is AssemblySettings' own constructor invariant -
        // no instance can ever violate it, so there is nothing to test here for that rule.

        // Reserved property
        val reservedProp = base.copy(propertyOverrides = mapOf("ro.build.version.release" to "16"))
        val resReport = StageSettingsValidator.validateAssembly(reservedProp, target)
        assertTrue(resReport.errors.any { it.code == StageSettingsValidator.RESERVED_PROPERTY })
    }

    @Test
    fun validateDebloatAcceptsOnlyPartitionPathSelectors() {
        val valid = DebloatSettings(
            enabled = true,
            removeSelectors = listOf("system:system/app/Foo", "product:app/Bar"),
        )
        assertFalse(StageSettingsValidator.validateDebloat(valid).hasBlockingErrors())

        listOf("system/app/Foo", "system:../etc", "system:app/*", "pkg:").forEach { bad ->
            val report = StageSettingsValidator.validateDebloat(valid.copy(removeSelectors = listOf(bad)))
            assertTrue(report.hasBlockingErrors(), "'$bad' must be rejected")
        }
    }

    @Test
    fun validateCustomizationEnforcesLockfileAndDigestInvariants() {
        val valid = CustomizationSettings(
            lockfile = PluginLockfile(
                schemaVersion = 1,
                entries = listOf(
                    PluginLockfileEntry(
                        publisher = "org.lti",
                        id = "tweaks",
                        version = "1.0",
                        contentDigest = "sha256:fedcba",
                    ),
                ),
            ),
            templateDigest = "sha256:112233",
            resolvedPlanDigest = "sha256:445566",
        )
        val validReport = StageSettingsValidator.validateCustomization(valid)
        assertFalse(validReport.hasBlockingErrors())

        // Schema version < 1
        val badSchema = valid.copy(lockfile = valid.lockfile.copy(schemaVersion = 0))
        val schemaReport = StageSettingsValidator.validateCustomization(badSchema)
        assertTrue(schemaReport.errors.any { it.code == StageSettingsValidator.INVALID_SCHEMA_VERSION })

        // Bad template digest
        val badTemplate = valid.copy(templateDigest = "md5:112233")
        val templateReport = StageSettingsValidator.validateCustomization(badTemplate)
        assertTrue(templateReport.errors.any { it.code == StageSettingsValidator.INVALID_DIGEST })

        // Bad entry digest
        val badEntry = valid.copy(
            lockfile = valid.lockfile.copy(
                entries = listOf(valid.lockfile.entries.first().copy(contentDigest = "not-sha256")),
            ),
        )
        val entryReport = StageSettingsValidator.validateCustomization(badEntry)
        assertTrue(entryReport.errors.any { it.code == StageSettingsValidator.INVALID_DIGEST })
    }

    @Test
    fun validateBuildEnforcesGeometryAndBlockSizeInvariants() {
        val valid = BuildSettings(
            blockSize = 4096,
            alignment = 4096,
            filesystem = "erofs",
        )
        val validReport = StageSettingsValidator.validateBuild(valid, target)
        assertFalse(validReport.hasBlockingErrors())

        // Non power of 2 block size
        val badBlockSize = valid.copy(blockSize = 3000)
        val blockReport = StageSettingsValidator.validateBuild(badBlockSize, target)
        assertTrue(blockReport.errors.any { it.code == StageSettingsValidator.INVALID_BLOCK_SIZE })

        // Alignment smaller than block size
        val badAlignment = valid.copy(blockSize = 4096, alignment = 2048)
        val alignReport = StageSettingsValidator.validateBuild(badAlignment, target)
        assertTrue(alignReport.errors.any { it.code == StageSettingsValidator.INVALID_ALIGNMENT })

        // Filesystem differs from target (Warning)
        val diffFs = valid.copy(filesystem = "f2fs")
        val fsReport = StageSettingsValidator.validateBuild(diffFs, target)
        val warning = fsReport.errors.find { it.code == StageSettingsValidator.INCOMPATIBLE_FILESYSTEM }
        assertEquals(Severity.WARNING, warning?.severity)
        // F2FS builds (read back by dump.f2fs); a filesystem nothing builds is an error, per field.
        assertTrue(fsReport.errors.none { it.code == StageSettingsValidator.UNSUPPORTED_FILESYSTEM })
        val perPartition = StageSettingsValidator.validateBuild(
            valid.copy(partitionFilesystem = mapOf("vendor" to "btrfs")),
            target,
        )
        assertEquals(
            "partitionFilesystem.vendor",
            perPartition.errors.single { it.code == StageSettingsValidator.UNSUPPORTED_FILESYSTEM }.fieldPath,
        )
        val ext4 = StageSettingsValidator.validateBuild(
            valid.copy(partitionFilesystem = mapOf("vendor" to "ext4")),
            target,
        )
        assertTrue(ext4.errors.none { it.code == StageSettingsValidator.UNSUPPORTED_FILESYSTEM })

        // Brotli takes 0..11; free space only on a partition built as ext4, never negative.
        fun packageErrors(settings: BuildSettings) = StageSettingsValidator.validateBuild(settings, target).errors
            .filter { it.code == StageSettingsValidator.INVALID_PACKAGE_OPTION }.map { it.fieldPath }
        assertEquals(listOf("brotliLevel"), packageErrors(valid.copy(brotliLevel = 12)))
        assertEquals(
            listOf("partitionFreeMib.system", "partitionFreeMib.vendor"),
            packageErrors(
                valid.copy(
                    partitionFilesystem = mapOf("vendor" to "ext4"),
                    partitionFreeMib = mapOf("system" to 100, "vendor" to -1, "odm" to 0),
                ),
            ),
        )
        assertEquals(
            emptyList(),
            packageErrors(
                valid.copy(partitionFilesystem = mapOf("vendor" to "ext4"), partitionFreeMib = mapOf("vendor" to 256)),
            ),
        )
        // A fixed size: whole blocks, not on EROFS (sized by its content), not with free space as well.
        val writable = mapOf("vendor" to "ext4", "odm" to "f2fs", "product" to "ext4")
        assertEquals(
            listOf("partitionSizeBytes.system", "partitionSizeBytes.vendor", "partitionSizeBytes.product"),
            packageErrors(
                valid.copy(
                    partitionFilesystem = writable,
                    partitionSizeBytes = mapOf(
                        "system" to (1L shl 30),
                        "vendor" to 1000L,
                        "odm" to (64L shl 20),
                        "product" to (2L shl 30),
                    ),
                    partitionFreeMib = mapOf("product" to 64),
                ),
            ),
        )
        // Sparse output: only a standalone image of the fastboot package.
        assertEquals(listOf("sparseImages.odm"), packageErrors(valid.copy(sparseImages = setOf("odm"))))
        assertEquals(
            emptyList(),
            packageErrors(
                valid.copy(
                    extraPackages = setOf("FullPackage"),
                    standaloneImages = listOf("odm"),
                    sparseImages = setOf("odm"),
                ),
            ),
        )
    }

    @Test
    fun validateSigningRejectsInlineCredentialsAndMissingKeys() {
        val valid = SigningPolicy(
            signImages = true,
            signPackage = true,
            avbKeyRef = "keys/avb.pem",
            platformKeyRef = "keys/platform",
        )
        val validReport = StageSettingsValidator.validateSigning(valid)
        assertFalse(validReport.hasBlockingErrors())

        // Inline PEM secret in avbKeyRef
        val inlineAvb = valid.copy(avbKeyRef = "-----BEGIN RSA PRIVATE KEY-----\nMIIE...")
        val avbReport = StageSettingsValidator.validateSigning(inlineAvb)
        assertTrue(avbReport.errors.any { it.code == StageSettingsValidator.INLINE_CREDENTIAL_NOT_ALLOWED })

        // Inline secret in platformKeyRef
        val inlinePlatform = valid.copy(platformKeyRef = "-----BEGIN PRIVATE KEY-----")
        val platReport = StageSettingsValidator.validateSigning(inlinePlatform)
        assertTrue(platReport.errors.any { it.code == StageSettingsValidator.INLINE_CREDENTIAL_NOT_ALLOWED })

        // Missing key ref when enabled
        val blankAvb = valid.copy(avbKeyRef = "   ")
        val blankReport = StageSettingsValidator.validateSigning(blankAvb)
        assertTrue(blankReport.errors.any { it.code == StageSettingsValidator.MISSING_KEY_REF })
    }

    @Test
    fun validateReleaseReturnsEmptyReportSinceOtaBaseUrlIsAlreadyEnforcedByTheConstructor() {
        // ReleaseSettings.init already requires https:// and rejects a trailing slash, so no instance
        // reaching validateRelease can ever violate either rule - nothing else needs re-checking.
        val settings = ReleaseSettings(otaBaseUrl = "https://updates.example.com/nubia")
        val report = StageSettingsValidator.validateRelease(settings, target)
        assertFalse(report.hasBlockingErrors())
        assertTrue(report.errors.isEmpty())
    }

    @Test
    fun validatePublishEnforcesOpaqueCredentialsAndPartSize() {
        val valid = PublishSettings(
            credentialRef = "credentials/github-token-ref",
            partSizeBytes = 1_000_000_000L,
            retentionPerDevice = 3,
        )
        val validReport = StageSettingsValidator.validatePublish(valid)
        assertFalse(validReport.hasBlockingErrors())

        // Raw GitHub token as inline credential
        val rawToken = valid.copy(credentialRef = "ghp_ABC123secrettokenhere")
        val tokenReport = StageSettingsValidator.validatePublish(rawToken)
        assertTrue(tokenReport.errors.any { it.code == StageSettingsValidator.INLINE_CREDENTIAL_NOT_ALLOWED })

        // Invalid part size
        val badPartSize = valid.copy(partSizeBytes = 0L)
        val partReport = StageSettingsValidator.validatePublish(badPartSize)
        assertTrue(partReport.errors.any { it.code == StageSettingsValidator.INVALID_PART_SIZE })

        // Invalid retention (< 1)
        val badRetention = valid.copy(retentionPerDevice = 0)
        val retReport = StageSettingsValidator.validatePublish(badRetention)
        assertTrue(retReport.errors.any { it.code == StageSettingsValidator.INVALID_RETENTION })
    }

    @Test
    fun aPackageThatLeavesOutABootImageIsWarnedAbout() {
        val target = DefaultTargetCatalog.PQ84P01_DEFAULT
        val withoutDtbo = target.copy(
            packagePolicy = target.packagePolicy.copy(
                flashableBootPartitions = target.packagePolicy.flashableBootPartitions - "dtbo",
            ),
        )
        val warning = StageSettingsValidator.validateBuild(BuildSettings(), withoutDtbo).errors
            .single { it.code == StageSettingsValidator.BOOT_IMAGE_LEFT_OUT }
        assertEquals(Severity.WARNING, warning.severity)
        assertTrue("dtbo" in warning.message)
        assertTrue(
            StageSettingsValidator.validateBuild(BuildSettings(), target).errors
                .none { it.code == StageSettingsValidator.BOOT_IMAGE_LEFT_OUT },
        )
    }

    @Test
    fun anEroFsClusterOutsideTheOfferedSizesIsRefused() {
        fun codes(kib: Int) = StageSettingsValidator.validateBuild(
            BuildSettings(partitionClusterKib = mapOf("vendor" to kib)),
        )
            .errors.filter { it.fieldPath == "partitionClusterKib.vendor" }.map { it.code }
        assertTrue(codes(64).isEmpty())
        assertEquals(listOf(StageSettingsValidator.INVALID_PACKAGE_OPTION), codes(48))
    }

    @Test
    fun aDeviceWithNoModelToAssertIsWarnedAbout() {
        val target = DefaultTargetCatalog.PQ84P01_DEFAULT
        fun codes(t: TargetDevice) = StageSettingsValidator.validateBuild(BuildSettings(), t).errors.map { it.code }
        assertTrue(StageSettingsValidator.NO_DEVICE_CHECK !in codes(target))
        assertTrue(StageSettingsValidator.NO_DEVICE_CHECK in codes(target.copy(assertModels = emptyList())))
    }

    @Test
    fun scriptsNeedAPlainUniqueNameAndText() {
        fun codes(vararg scripts: ProjectScript) =
            StageSettingsValidator.validateCustomization(CustomizationSettings(scripts = scripts.toList())).errors
                .filter { it.code == StageSettingsValidator.INVALID_SCRIPT }.map { it.objectId }
        assertEquals(emptyList(), codes(ProjectScript("debrand", "rm -f $1/product/app/Foo")))
        assertEquals(listOf("../x"), codes(ProjectScript("../x", "true")))
        assertEquals(listOf("a", "a"), codes(ProjectScript("a", "true"), ProjectScript("a", "true")))
        assertEquals(listOf("empty"), codes(ProjectScript("empty", " ")))
        assertEquals(emptyList(), codes(ProjectScript("draft", "", enabled = false)))
    }

    @Test
    fun aBadCoverageRuleIsBuildAndSignsErrorWhereItIsEdited() {
        val rules = "/system/x 0 0\n^/system/app/A(/.*)? 0 0 0644 u:object_r:system_file:s0"
        val errors = StageSettingsValidator.validateCustomization(CustomizationSettings(coverageRules = rules))
            .errors.filter { it.code == StageSettingsValidator.INVALID_COVERAGE_RULE }
        assertEquals(listOf(StageId.BUILD_FLASHABLE_ZIP), errors.map { it.stageId })
    }
}
