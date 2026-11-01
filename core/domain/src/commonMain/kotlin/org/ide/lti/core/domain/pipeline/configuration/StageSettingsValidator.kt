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

import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage
import org.ide.lti.core.domain.pipeline.stages.ProjectScripts
import org.ide.lti.core.domain.pipeline.toolkit.BuildFilesystems
import org.ide.lti.core.domain.pipeline.toolkit.CoverageRule
import org.ide.lti.core.domain.pipeline.toolkit.PackageKind
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.AssemblySettings
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.core.model.workspace.CustomizationSettings
import org.ide.lti.core.model.workspace.DebloatSettings
import org.ide.lti.core.model.workspace.ExtractionSettings
import org.ide.lti.core.model.workspace.ProjectScript
import org.ide.lti.core.model.workspace.PublishSettings
import org.ide.lti.core.model.workspace.ReleaseSettings
import org.ide.lti.core.model.workspace.SigningPolicy

/**
 * Centralized, stage-scoped settings validator producing structured [ValidationReport]
 * diagnostics for the ROM Setup Studio UI and pipeline preflights without throwing exceptions.
 */
public object StageSettingsValidator {

    public const val INCOMPATIBLE_REGION: String = "INCOMPATIBLE_REGION"
    public const val INCOMPATIBLE_FIRMWARE: String = "INCOMPATIBLE_FIRMWARE"
    public const val UNSUPPORTED_EXTRACTION_ADAPTER: String = "UNSUPPORTED_EXTRACTION_ADAPTER"
    public const val PARTITION_NOT_ON_TARGET: String = "PARTITION_NOT_ON_TARGET"
    public const val INVALID_FILENAME: String = "INVALID_FILENAME"
    private const val PATH_SEPARATORS = "/\\"

    public const val UNSUPPORTED_SLOT: String = "UNSUPPORTED_SLOT"
    public const val UNSUPPORTED_SYSTEM_EXT_MODE: String = "UNSUPPORTED_SYSTEM_EXT_MODE"
    public const val UNSUPPORTED_FOOTER_POLICY: String = "UNSUPPORTED_FOOTER_POLICY"
    public const val RESERVED_PROPERTY: String = "RESERVED_PROPERTY"
    public const val INVALID_SCHEMA_VERSION: String = "INVALID_SCHEMA_VERSION"
    public const val INVALID_DIGEST: String = "INVALID_DIGEST"
    public const val INVALID_BLOCK_SIZE: String = "INVALID_BLOCK_SIZE"
    public const val INVALID_ALIGNMENT: String = "INVALID_ALIGNMENT"
    public const val INCOMPATIBLE_FILESYSTEM: String = "INCOMPATIBLE_FILESYSTEM"
    public const val UNSUPPORTED_FILESYSTEM: String = "UNSUPPORTED_FILESYSTEM"
    public const val INVALID_PACKAGE_OPTION: String = "INVALID_PACKAGE_OPTION"
    private val BROTLI_LEVELS = 0..11
    public const val BOOT_IMAGE_LEFT_OUT: String = "BOOT_IMAGE_LEFT_OUT"
    public const val NO_DEVICE_CHECK: String = "NO_DEVICE_CHECK"
    public const val INVALID_SCRIPT: String = "INVALID_SCRIPT"
    public const val INVALID_COVERAGE_RULE: String = "INVALID_COVERAGE_RULE"
    public const val INLINE_CREDENTIAL_NOT_ALLOWED: String = "INLINE_CREDENTIAL_NOT_ALLOWED"
    public const val MISSING_KEY_REF: String = "MISSING_KEY_REF"
    public const val INVALID_PART_SIZE: String = "INVALID_PART_SIZE"
    public const val INVALID_RETENTION: String = "INVALID_RETENTION"

    private val DEBLOAT_SELECTOR = Regex("""^[A-Za-z0-9_-]+:[A-Za-z0-9._+@-]+(/[A-Za-z0-9._+@-]+)*$""")
    private val SUPPORTED_SYSTEM_EXT_MODES = setOf("auto", "fold", "standalone")
    private val SUPPORTED_FOOTER_POLICIES = setOf("erase", "preserve")
    private val RESERVED_PROPERTIES = setOf("ro.build.version.release", "ro.build.version.security_patch")

    // retries-in-0..5 and archiveRef-required-for-IMPORT_ARCHIVE are already enforced by
    // AcquisitionSettings' own constructor (see ConfigurationSettings.kt's init block) - an instance
    // reaching this function can never violate either, so there is nothing to re-check here.
    public fun validateAcquisition(settings: AcquisitionSettings, target: TargetDevice? = null): ValidationReport {
        val errors = mutableListOf<ValidationError>()
        if (target != null) {
            if (settings.region !in target.availableRegions) {
                errors += ValidationError(
                    stageId = StageId.FIRMWARE_ACQUISITION,
                    objectId = target.id,
                    fieldPath = "region",
                    code = INCOMPATIBLE_REGION,
                    severity = Severity.ERROR,
                    message = "Region '${settings.region.id}' is not declared on target device '${target.id}'",
                )
            }
            val available = target.availableFirmwares[settings.region].orEmpty()
            val matchesFirmware = available.any {
                it.version == settings.firmware.version && it.buildId == settings.firmware.buildId
            }
            if (!matchesFirmware) {
                errors += ValidationError(
                    stageId = StageId.FIRMWARE_ACQUISITION,
                    objectId = target.id,
                    fieldPath = "firmware",
                    code = INCOMPATIBLE_FIRMWARE,
                    severity = Severity.ERROR,
                    message = "Firmware '${settings.firmware.version}' is not in available firmwares for " +
                        "region '${settings.region.id}' on target '${target.id}'",
                )
            }
        }
        return ValidationReport(errors = errors)
    }

    public fun validateExtraction(settings: ExtractionSettings, target: TargetDevice? = null): ValidationReport {
        val errors = mutableListOf<ValidationError>()
        val adapter = settings.adapter
        val isAuto = adapter.equals("AUTO", ignoreCase = true)
        val isPayloadDumper = adapter.equals("payload-dumper-go", ignoreCase = true)
        val isLpunpack = adapter.equals("lpunpack", ignoreCase = true)
        if (!isAuto && !isPayloadDumper && !isLpunpack) {
            errors += ValidationError(
                stageId = StageId.FIRMWARE_EXTRACTION,
                objectId = null,
                fieldPath = "adapter",
                code = UNSUPPORTED_EXTRACTION_ADAPTER,
                severity = Severity.ERROR,
                message = "Extraction adapter '$adapter' is not supported",
            )
        }
        if (target != null) {
            val invalidDynamic = settings.dynamicPartitions.filter { it !in target.dynamicPartitions }
            if (invalidDynamic.isNotEmpty()) {
                errors += ValidationError(
                    stageId = StageId.FIRMWARE_EXTRACTION,
                    objectId = target.id,
                    fieldPath = "dynamicPartitions",
                    code = PARTITION_NOT_ON_TARGET,
                    severity = Severity.ERROR,
                    message = "Dynamic partition(s) not on target: ${invalidDynamic.joinToString()}",
                )
            }
            val invalidBoot = settings.bootPartitions.filter { it !in target.bootPartitions }
            if (invalidBoot.isNotEmpty()) {
                errors += ValidationError(
                    stageId = StageId.FIRMWARE_EXTRACTION,
                    objectId = target.id,
                    fieldPath = "bootPartitions",
                    code = PARTITION_NOT_ON_TARGET,
                    severity = Severity.ERROR,
                    message = "Boot partition(s) not on target: ${invalidBoot.joinToString()}",
                )
            }
            val slot = settings.slot
            if (!slot.isNullOrBlank()) {
                val validSlots = buildSet {
                    addAll(target.packagePolicy.bootSlots)
                    addAll(target.packagePolicy.bootSlots.map { "_$it" })
                    add(target.activeSlotSuffix)
                    add(target.activeSlotSuffix.removePrefix("_"))
                }
                if (slot !in validSlots) {
                    errors += ValidationError(
                        stageId = StageId.FIRMWARE_EXTRACTION,
                        objectId = target.id,
                        fieldPath = "slot",
                        code = UNSUPPORTED_SLOT,
                        severity = Severity.ERROR,
                        message = "Slot '$slot' is not supported on target device '${target.id}'",
                    )
                }
            }
        }
        return ValidationReport(errors = errors)
    }

    public fun validateAssembly(settings: AssemblySettings, target: TargetDevice? = null): ValidationReport {
        val errors = mutableListOf<ValidationError>()
        if (target != null) {
            val invalidIncluded = settings.includedPartitions.filter { it !in target.dynamicPartitions }
            if (invalidIncluded.isNotEmpty()) {
                errors += ValidationError(
                    stageId = StageId.WORK_TREE_ASSEMBLY,
                    objectId = target.id,
                    fieldPath = "includedPartitions",
                    code = PARTITION_NOT_ON_TARGET,
                    severity = Severity.ERROR,
                    message = "Included partition(s) not on target: ${invalidIncluded.joinToString()}",
                )
            }
        }
        // The version goes into the package's file name ({version}), so it must keep that a plain name.
        val version = settings.romVersion
        if (version.any { it in PATH_SEPARATORS || it.isISOControl() } || version == "." || version == "..") {
            errors += ValidationError(
                stageId = StageId.WORK_TREE_ASSEMBLY,
                objectId = null,
                fieldPath = "romVersion",
                code = INVALID_FILENAME,
                severity = Severity.ERROR,
                message = "The ROM version names the package file, so it cannot hold '/', '\\' or control characters",
            )
        }
        if (settings.systemExtMode.lowercase() !in SUPPORTED_SYSTEM_EXT_MODES) {
            errors += ValidationError(
                stageId = StageId.WORK_TREE_ASSEMBLY,
                objectId = null,
                fieldPath = "systemExtMode",
                code = UNSUPPORTED_SYSTEM_EXT_MODE,
                severity = Severity.ERROR,
                message = "Unsupported system_ext mode '${settings.systemExtMode}'",
            )
        }
        if (settings.bootFooterPolicy.lowercase() !in SUPPORTED_FOOTER_POLICIES) {
            errors += ValidationError(
                stageId = StageId.WORK_TREE_ASSEMBLY,
                objectId = null,
                fieldPath = "bootFooterPolicy",
                code = UNSUPPORTED_FOOTER_POLICY,
                severity = Severity.ERROR,
                message = "Unsupported boot footer policy '${settings.bootFooterPolicy}'",
            )
        }
        // Newline/NUL rejection in propertyOverrides keys/values is already enforced by
        // AssemblySettings' own constructor - an instance reaching this function can never violate
        // it, so only the reserved-key rule (not a constructor invariant) is checked here.
        settings.propertyOverrides.forEach { (k, _) ->
            if (k in RESERVED_PROPERTIES) {
                errors += ValidationError(
                    stageId = StageId.WORK_TREE_ASSEMBLY,
                    objectId = k,
                    fieldPath = "propertyOverrides['$k']",
                    code = RESERVED_PROPERTY,
                    severity = Severity.ERROR,
                    message = "Property override contains reserved key: '$k'",
                )
            }
        }
        return ValidationReport(errors = errors)
    }

    /** Picks are `partition:path` app folders from the inventory; the stage checks they still exist. */
    public fun validateDebloat(settings: DebloatSettings): ValidationReport {
        val errors = (settings.removeSelectors + settings.keepSelectors)
            .filterNot { DEBLOAT_SELECTOR.matches(it) && ".." !in it.substringAfter(':').split('/') }
            .map { selector ->
                ValidationError(
                    stageId = StageId.DEBLOAT,
                    objectId = null,
                    fieldPath = selector,
                    code = org.ide.lti.core.domain.debloat.MALFORMED_SELECTOR,
                    severity = Severity.ERROR,
                    message = "'$selector' is not a partition:path app folder",
                )
            }
        return ValidationReport(errors = errors)
    }

    public fun validateCustomization(settings: CustomizationSettings): ValidationReport {
        val errors = mutableListOf<ValidationError>()
        if (settings.lockfile.schemaVersion < 1) {
            errors += ValidationError(
                stageId = StageId.MODULE_APPLICATION,
                objectId = null,
                fieldPath = "lockfile.schemaVersion",
                code = INVALID_SCHEMA_VERSION,
                severity = Severity.ERROR,
                message = "lockfile schemaVersion must be >= 1, was ${settings.lockfile.schemaVersion}",
            )
        }
        val templateDigest = settings.templateDigest
        if (templateDigest != null && !templateDigest.startsWith("sha256:")) {
            errors += ValidationError(
                stageId = StageId.MODULE_APPLICATION,
                objectId = null,
                fieldPath = "templateDigest",
                code = INVALID_DIGEST,
                severity = Severity.ERROR,
                message = "templateDigest must start with 'sha256:', was '$templateDigest'",
            )
        }
        val resolvedPlanDigest = settings.resolvedPlanDigest
        if (resolvedPlanDigest != null && !resolvedPlanDigest.startsWith("sha256:")) {
            errors += ValidationError(
                stageId = StageId.MODULE_APPLICATION,
                objectId = null,
                fieldPath = "resolvedPlanDigest",
                code = INVALID_DIGEST,
                severity = Severity.ERROR,
                message = "resolvedPlanDigest must start with 'sha256:', was '$resolvedPlanDigest'",
            )
        }
        settings.lockfile.entries.forEach { entry ->
            if (entry.contentDigest.isNotBlank() && !entry.contentDigest.startsWith("sha256:")) {
                errors += ValidationError(
                    stageId = StageId.MODULE_APPLICATION,
                    objectId = "${entry.publisher}:${entry.id}",
                    fieldPath = "lockfile.entries",
                    code = INVALID_DIGEST,
                    severity = Severity.ERROR,
                    message = "Entry contentDigest must start with 'sha256:', was '${entry.contentDigest}'",
                )
            }
        }
        errors += scriptErrors(settings.scripts)
        errors += CoverageRule.parse(settings.coverageRules).second.map {
            // Filed under Build & Sign, whose Coverage pane edits them: a Patches error would lock that editor away.
            ValidationError(
                stageId = StageId.BUILD_FLASHABLE_ZIP,
                objectId = null,
                fieldPath = "coverageRules",
                code = INVALID_COVERAGE_RULE,
                severity = Severity.ERROR,
                message = "Coverage rule $it",
            )
        }
        return ValidationReport(errors = errors)
    }

    /** A script needs a plain, unique file name and some text to run. */
    private fun scriptErrors(scripts: List<ProjectScript>): List<ValidationError> {
        val repeated = scripts.groupBy { it.name }.filterValues { it.size > 1 }.keys
        return scripts.mapNotNull { script ->
            val message = when {
                !ProjectScripts.NAME.matches(script.name) ->
                    "Script name '${script.name}' may only hold letters, digits, '_' and '-' (64 at most)"

                script.name in repeated -> "Two scripts are named '${script.name}'"

                script.enabled && script.content.isBlank() -> "Script '${script.name}' is empty"

                else -> null
            }
            message?.let {
                ValidationError(
                    stageId = StageId.MODULE_APPLICATION,
                    objectId = script.name,
                    fieldPath = "scripts",
                    code = INVALID_SCRIPT,
                    severity = Severity.ERROR,
                    message = it,
                )
            }
        }
    }

    /** A plain file name in out/package ending in .zip: no separator, control character, or a dots-only base. */
    private fun isPackageName(name: String): Boolean {
        val base = name.removeSuffix(".zip")
        return name.endsWith(".zip") && name.none { it in PATH_SEPARATORS || it.isISOControl() } &&
            base.isNotBlank() && !base.all { it == '.' }
    }

    public fun validateBuild(settings: BuildSettings, target: TargetDevice? = null): ValidationReport {
        val errors = mutableListOf<ValidationError>()
        val name = settings.filenameTemplate
        // The name as it is written: the device id fills {device} ({version} is checked with Assemble).
        val rendered = BuildFlashableZipStage.renderName(name, "v", "20260101", target?.id ?: "device")
        if (!isPackageName(rendered)) {
            errors += ValidationError(
                stageId = StageId.BUILD_FLASHABLE_ZIP,
                objectId = null,
                fieldPath = "filenameTemplate",
                code = INVALID_FILENAME,
                severity = Severity.ERROR,
                message = "The package name must be a file name ending in .zip, was '$rendered'",
            )
        }
        if (settings.blockSize <= 0 || (settings.blockSize and (settings.blockSize - 1)) != 0) {
            errors += ValidationError(
                stageId = StageId.BUILD_FLASHABLE_ZIP,
                objectId = null,
                fieldPath = "blockSize",
                code = INVALID_BLOCK_SIZE,
                severity = Severity.ERROR,
                message = "blockSize must be a positive power of 2, was ${settings.blockSize}",
            )
        }
        val alignment = settings.alignment
        if (alignment != null) {
            if (alignment <= 0 || (alignment and (alignment - 1)) != 0) {
                errors += ValidationError(
                    stageId = StageId.BUILD_FLASHABLE_ZIP,
                    objectId = null,
                    fieldPath = "alignment",
                    code = INVALID_ALIGNMENT,
                    severity = Severity.ERROR,
                    message = "alignment must be a positive power of 2, was $alignment",
                )
            } else if (settings.blockSize > 0 && alignment < settings.blockSize) {
                errors += ValidationError(
                    stageId = StageId.BUILD_FLASHABLE_ZIP,
                    objectId = null,
                    fieldPath = "alignment",
                    code = INVALID_ALIGNMENT,
                    severity = Severity.ERROR,
                    message = "alignment ($alignment) must be >= blockSize (${settings.blockSize})",
                )
            }
        }
        errors += unsupportedFilesystems(settings)
        errors += packageSpace(settings)
        if (target != null && target.filesystemType.isNotBlank()) {
            if (!settings.filesystem.equals(target.filesystemType, ignoreCase = true)) {
                errors += ValidationError(
                    stageId = StageId.BUILD_FLASHABLE_ZIP,
                    objectId = target.id,
                    fieldPath = "filesystem",
                    code = INCOMPATIBLE_FILESYSTEM,
                    severity = Severity.WARNING,
                    message = "Build filesystem '${settings.filesystem}' differs from " +
                        "target filesystem '${target.filesystemType}'",
                )
            }
        }
        target?.let { errors += bootImagesLeftOut(it) }
        errors += validateSigning(settings.signing).errors
        return ValidationReport(errors = errors)
    }

    /**
     * The brotli level must be one brotli takes (0..11); free space only works on a partition built as ext4 (EROFS
     * and the read-only F2FS image cannot be written to) and is never negative.
     */
    private fun packageSpace(settings: BuildSettings): List<ValidationError> {
        fun error(field: String, message: String) = ValidationError(
            stageId = StageId.BUILD_FLASHABLE_ZIP,
            objectId = null,
            fieldPath = field,
            code = INVALID_PACKAGE_OPTION,
            severity = Severity.ERROR,
            message = message,
        )
        val brotli = listOfNotNull(
            error("brotliLevel", "The brotli level must be 0 to 11, was ${settings.brotliLevel}")
                .takeIf { settings.brotliLevel !in BROTLI_LEVELS },
        )
        val free = settings.partitionFreeMib.mapNotNull { (p, mib) ->
            val fs = BuildFilesystems.of(settings, p)
            when {
                mib < 0 -> error("partitionFreeMib.$p", "Free space for $p cannot be negative, was $mib MiB")

                mib > 0 && fs != BuildFilesystems.EXT4 ->
                    error("partitionFreeMib.$p", "$p is built as $fs, which cannot be written to: build it as ext4")

                else -> null
            }
        }
        val sizes = settings.partitionSizeBytes.mapNotNull { (p, bytes) ->
            val fs = BuildFilesystems.of(settings, p)
            when {
                bytes <= 0 || settings.blockSize <= 0 || bytes % settings.blockSize != 0L -> error(
                    "partitionSizeBytes.$p",
                    "The size of $p must be a whole number of ${settings.blockSize}-byte blocks, was $bytes bytes",
                )

                fs == BuildFilesystems.EROFS ->
                    error("partitionSizeBytes.$p", "$p is built as EROFS, which is always sized by its content")

                (settings.partitionFreeMib[p] ?: 0) > 0 ->
                    error("partitionSizeBytes.$p", "$p has both a fixed size and free space: keep one")

                else -> null
            }
        }
        // Sparse output only changes the standalone images of the fastboot images package.
        val sparse = settings.sparseImages.filter {
            it !in settings.standaloneImages || PackageKind.FullPackage.name !in settings.extraPackages
        }.map {
            error("sparseImages.$it", "$it is not a standalone image of the fastboot images package")
        }
        val clusters = settings.partitionClusterKib.filterValues { it !in BuildFilesystems.CLUSTER_KIB }.map { (p, k) ->
            error(
                "partitionClusterKib.$p",
                "The cluster of $p must be ${BuildFilesystems.CLUSTER_KIB.joinToString(", ")} KiB, was $k KiB",
            )
        }
        return brotli + free + sizes + sparse + clusters
    }

    /** A filesystem the build cannot make, for the stage or for one partition. */
    private fun unsupportedFilesystems(settings: BuildSettings): List<ValidationError> {
        val chosen = mapOf("filesystem" to settings.filesystem) +
            settings.partitionFilesystem.mapKeys { "partitionFilesystem.${it.key}" }
        return chosen.filterValues { it.lowercase() !in BuildFilesystems.SUPPORTED }.map { (field, fs) ->
            ValidationError(
                stageId = StageId.BUILD_FLASHABLE_ZIP,
                objectId = null,
                fieldPath = field,
                code = UNSUPPORTED_FILESYSTEM,
                severity = Severity.ERROR,
                message = "'$fs' cannot be built; choose ${BuildFilesystems.SUPPORTED.joinToString(" or ")}",
            )
        }
    }

    /**
     * A boot partition the package does not write keeps what the slot already has: the device then boots another
     * build's kernel (or device tree) against this build's system.
     */
    private fun bootImagesLeftOut(target: TargetDevice): List<ValidationError> {
        val left = target.bootPartitions - target.packagePolicy.flashableBootPartitions.toSet()
        // With no model to assert, the package's updater-script checks nothing and installs on any device.
        val unchecked = ValidationError(
            stageId = StageId.BUILD_FLASHABLE_ZIP,
            objectId = target.id,
            fieldPath = "assertModels",
            code = NO_DEVICE_CHECK,
            severity = Severity.WARNING,
            message = "The device names no model to check, so the package installs on any device, including " +
                "ones it would break. Add the device codename to its models.",
        ).takeIf { target.assertModels.isEmpty() }
        return listOfNotNull(unchecked) + if (left.isEmpty()) {
            emptyList()
        } else {
            listOf(
                ValidationError(
                    stageId = StageId.BUILD_FLASHABLE_ZIP,
                    objectId = target.id,
                    fieldPath = "packagePolicy.flashableBootPartitions",
                    code = BOOT_IMAGE_LEFT_OUT,
                    severity = Severity.WARNING,
                    message = "The package does not write ${left.joinToString()}: the slot it installs to keeps " +
                        "the old ones, so the device boots another build's image with this system.",
                ),
            )
        }
    }

    public fun validateSigning(settings: SigningPolicy): ValidationReport {
        val errors = mutableListOf<ValidationError>()
        if (isInlineSecret(settings.avbKeyRef)) {
            errors += ValidationError(
                stageId = StageId.BUILD_FLASHABLE_ZIP,
                objectId = null,
                fieldPath = "signing.avbKeyRef",
                code = INLINE_CREDENTIAL_NOT_ALLOWED,
                severity = Severity.ERROR,
                message = "avbKeyRef must be an opaque reference or file path, not inline key material",
            )
        } else if (settings.signImages && settings.avbKeyRef.isBlank()) {
            errors += ValidationError(
                stageId = StageId.BUILD_FLASHABLE_ZIP,
                objectId = null,
                fieldPath = "signing.avbKeyRef",
                code = MISSING_KEY_REF,
                severity = Severity.ERROR,
                message = "avbKeyRef must not be blank when signImages is enabled",
            )
        }
        if (isInlineSecret(settings.platformKeyRef)) {
            errors += ValidationError(
                stageId = StageId.BUILD_FLASHABLE_ZIP,
                objectId = null,
                fieldPath = "signing.platformKeyRef",
                code = INLINE_CREDENTIAL_NOT_ALLOWED,
                severity = Severity.ERROR,
                message = "platformKeyRef must be an opaque reference or file path, not inline key material",
            )
        } else if (settings.signPackage && settings.platformKeyRef.isBlank()) {
            errors += ValidationError(
                stageId = StageId.BUILD_FLASHABLE_ZIP,
                objectId = null,
                fieldPath = "signing.platformKeyRef",
                code = MISSING_KEY_REF,
                severity = Severity.ERROR,
                message = "platformKeyRef must not be blank when signPackage is enabled",
            )
        }
        return ValidationReport(errors = errors)
    }

    // ReleaseSettings' own constructor already enforces both the https:// prefix and the no-trailing-
    // slash rule for otaBaseUrl - an instance reaching this function can never violate either, and no
    // other field needs external (target) context, so there is nothing left to check. Kept as a
    // function, returning an empty report, purely so every settings group has a uniform validate*
    // entry point for callers that dispatch across all stages.
    @Suppress("UnusedParameter")
    public fun validateRelease(settings: ReleaseSettings, target: TargetDevice? = null): ValidationReport =
        ValidationReport()

    public fun validatePublish(settings: PublishSettings): ValidationReport {
        val errors = mutableListOf<ValidationError>()
        val credentialRef = settings.credentialRef
        if (!credentialRef.isNullOrBlank() && isInlineSecret(credentialRef)) {
            errors += ValidationError(
                stageId = StageId.PUBLISH_RELEASE,
                objectId = null,
                fieldPath = "credentialRef",
                code = INLINE_CREDENTIAL_NOT_ALLOWED,
                severity = Severity.ERROR,
                message = "credentialRef must be an opaque reference or secret ID, not inline credential material",
            )
        }
        if (settings.partSizeBytes <= 0L) {
            errors += ValidationError(
                stageId = StageId.PUBLISH_RELEASE,
                objectId = null,
                fieldPath = "partSizeBytes",
                code = INVALID_PART_SIZE,
                severity = Severity.ERROR,
                message = "partSizeBytes must be positive, was ${settings.partSizeBytes}",
            )
        }
        if (settings.retentionPerDevice < 1) {
            errors += ValidationError(
                stageId = StageId.PUBLISH_RELEASE,
                objectId = null,
                fieldPath = "retentionPerDevice",
                code = INVALID_RETENTION,
                severity = Severity.ERROR,
                message = "retentionPerDevice must be >= 1, was ${settings.retentionPerDevice}",
            )
        }
        return ValidationReport(errors = errors)
    }

    private fun isInlineSecret(value: String): Boolean = value.contains("-----BEGIN") ||
        '\n' in value ||
        '\r' in value ||
        '\u0000' in value ||
        value.contains("PRIVATE KEY") ||
        value.startsWith("ghp_") ||
        value.startsWith("github_pat_")
}
