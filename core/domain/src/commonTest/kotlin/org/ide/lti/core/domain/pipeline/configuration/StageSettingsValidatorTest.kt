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

import org.ide.lti.core.model.target.DefaultTargetCatalog
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
        // retries-in-0..5 and archiveRef-required-for-IMPORT/EXISTING_ARCHIVE are AcquisitionSettings'
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
    fun validateDebloatEnforcesPresetAndPolicyInvariants() {
        val valid = DebloatSettings(
            enabled = true,
            presetId = "mifos_minimal",
            presetRevision = 1,
            presetDigest = "sha256:0123456789abcdef",
            protectedPolicyRevision = 2,
        )
        val validReport = StageSettingsValidator.validateDebloat(valid)
        assertFalse(validReport.hasBlockingErrors())

        // Invalid preset revision (< 1)
        val badRev = valid.copy(presetRevision = 0)
        val revReport = StageSettingsValidator.validateDebloat(badRev)
        assertTrue(revReport.errors.any { it.code == StageSettingsValidator.INVALID_PRESET_REVISION })

        // Invalid preset digest (missing sha256: prefix)
        val badDigest = valid.copy(presetDigest = "raw-hash-value")
        val digestReport = StageSettingsValidator.validateDebloat(badDigest)
        assertTrue(digestReport.errors.any { it.code == StageSettingsValidator.INVALID_DIGEST })

        // Invalid policy revision (< 1)
        val badPolicy = valid.copy(protectedPolicyRevision = 0)
        val policyReport = StageSettingsValidator.validateDebloat(badPolicy)
        assertTrue(policyReport.errors.any { it.code == StageSettingsValidator.INVALID_POLICY_REVISION })
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
}
