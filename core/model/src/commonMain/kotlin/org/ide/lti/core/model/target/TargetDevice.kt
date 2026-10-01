/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.target

import kotlinx.serialization.Serializable

/**
 * Qualification certification status for a target device profile.
 */
enum class TargetStatus(val displayName: String) {
    QUALIFIED("Qualified"),
    EXPERIMENTAL("Experimental"),
    UNVERIFIED("Unverified"),
}

/**
 * Target hardware deployment region / market.
 */
enum class TargetRegion(
    val id: String,
    val displayName: String,
    val shortName: String,
    val code: String,
) {
    GLOBAL("global", "Global OS", "Global", "GL"),
    CHINA("china", "CN OS", "CN", "CN"),
    ;

    companion object {
        fun fromId(id: String): TargetRegion? = entries.find { it.id.equals(id, ignoreCase = true) }
    }
}

/**
 * Target baseline firmware acquisition mode.
 */
enum class FirmwareAcquisitionMode(
    val id: String,
    val displayName: String,
    val description: String,
) {
    OTA(
        id = "ota",
        displayName = "Official OTA (Nubia CDN)",
        description = "Direct download from official update server with SHA-256 verification",
    ),
    ZIP_IMAGE(
        id = "zip_image",
        displayName = "Raw Image Dump Archive",
        description = "Unpacked raw partition images from cloud storage or local dump",
    ),
    ;

    companion object {
        fun fromId(id: String): FirmwareAcquisitionMode =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: OTA
    }
}

/**
 * Metadata policy for dynamic partition recovery.
 */
@Serializable
enum class MetadataPolicy {
    DEVICE_FIRST_WITH_PACKAGE_FALLBACK,
    PACKAGE_ONLY,
    DEVICE_ONLY,
}

/**
 * Packaging and flash policy for a target device profile.
 */
@Serializable
data class PackagePolicy(
    val flashablePartitions: List<String> = emptyList(),
    val flashableBootPartitions: List<String> = emptyList(),
    val bootSlots: List<String> = listOf("a", "b"),
    val excludeVbmeta: Boolean = true,
    val recoverySystemMountPoint: String = "/system_root",
    val metadataPolicy: MetadataPolicy = MetadataPolicy.DEVICE_FIRST_WITH_PACKAGE_FALLBACK,
)

/**
 * Firmware source kind (OTA payload or raw image dump).
 */
@Serializable
enum class FirmwareSourceKind {
    OTA_ZIP,
    RAW_IMAGE_ZIP,
}

/**
 * Target baseline firmware specification.
 */
@Serializable
data class TargetFirmware(
    val version: String,
    val buildId: String,
    val androidVersion: String = "15",
    val securityPatch: String = "2026-02-01",
    val otaUrl: String? = null,
    val isOfficial: Boolean = true,
    val isRecommended: Boolean = false,
    val sha256: String? = null,
    val checksumUnavailable: Boolean = false,
    val acquisitionUrl: String? = null,
    val sourceKind: FirmwareSourceKind = FirmwareSourceKind.OTA_ZIP,
    val fingerprintTriple: String? = null,
    val archiveSizeBytes: Long? = null,
)

/**
 * Immutable entity representing a target device profile for ROM unpacking,
 * partition assembly, patching, and repackaging.
 */
@Serializable
data class TargetDevice(
    val id: String,
    val name: String,
    val codename: String,
    val revision: Int = 1,
    val availableRegions: List<TargetRegion> = listOf(TargetRegion.GLOBAL, TargetRegion.CHINA),
    val availableFirmwares: Map<TargetRegion, List<TargetFirmware>> = emptyMap(),
    val socPlatform: String,
    val filesystemType: String,
    val superPartitionBytes: Long = 17179869184L,
    val superGroupBytes: Long = 17175674880L,
    val bootPartitionBytes: Long = 100663296L,
    val vendorBootPartitionBytes: Long = 100663296L,
    val initBootPartitionBytes: Long = 8388608L,
    val dtboPartitionBytes: Long = 25165824L,
    val dynamicPartitions: List<String>,
    val bootPartitions: List<String>,
    val status: TargetStatus,
    val description: String,
    val isDefault: Boolean = false,
    val productName: String = codename,
    val fastbootProduct: String = "sun",
    val assertModels: List<String> = listOf(codename),
    val superMetadataSlots: Int = 3,
    val superGroupName: String = "qti_dynamic_partitions",
    val virtualAb: Boolean = true,
    val activeSlotSuffix: String = "_a",
    val hasStandaloneSystemExt: Boolean = true,
    val bootDevicePath: String = "/dev/block/bootdevice/by-name",
    val packagePolicy: PackagePolicy = PackagePolicy(
        flashablePartitions = dynamicPartitions,
        flashableBootPartitions = bootPartitions,
        bootSlots = listOf("a", "b"),
        excludeVbmeta = true,
        recoverySystemMountPoint = "/system_root",
        metadataPolicy = MetadataPolicy.DEVICE_FIRST_WITH_PACKAGE_FALLBACK,
    ),
) {
    init {
        require(revision >= 1) { "revision must be >= 1, but was $revision" }
    }

    val formattedSuperSize: String
        get() = "${superPartitionBytes / (1024L * 1024L * 1024L)}.0 GB"

    val formattedSuperGroupSize: String
        get() = "${(superGroupBytes * 10 / (1024L * 1024L * 1024L)) / 10.0} GB"

    val formattedBootSize: String
        get() = "${bootPartitionBytes / (1024L * 1024L)} MB"

    val formattedVendorBootSize: String
        get() = "${vendorBootPartitionBytes / (1024L * 1024L)} MB"

    val formattedInitBootSize: String
        get() = "${initBootPartitionBytes / (1024L * 1024L)} MB"

    val formattedDtboSize: String
        get() = "${dtboPartitionBytes / (1024L * 1024L)} MB"

    val targetKey: String
        get() = codename.uppercase()

    val workspaceSlug: String
        get() = targetKey

    val friendlyTitle: String
        get() = "$name ($codename)"

    val displayLabel: String
        get() = "$name ($codename)"

    companion object {
        val EMPTY = TargetDevice(
            id = "",
            name = "",
            codename = "",
            revision = 1,
            availableRegions = listOf(TargetRegion.GLOBAL, TargetRegion.CHINA),
            availableFirmwares = emptyMap(),
            socPlatform = "Snapdragon 8 Elite",
            filesystemType = "erofs",
            superPartitionBytes = 17179869184L,
            dynamicPartitions = listOf("system", "vendor", "product", "system_ext", "odm"),
            bootPartitions = listOf("boot", "init_boot", "vendor_boot", "dtbo"),
            status = TargetStatus.EXPERIMENTAL,
            description = "Default empty target profile",
            isDefault = false,
            productName = "",
            fastbootProduct = "",
            assertModels = emptyList(),
            superMetadataSlots = 3,
            superGroupName = "qti_dynamic_partitions",
            virtualAb = true,
            activeSlotSuffix = "_a",
            hasStandaloneSystemExt = true,
            bootDevicePath = "/dev/block/bootdevice/by-name",
            packagePolicy = PackagePolicy(
                flashablePartitions = listOf("system", "vendor", "product", "system_ext", "odm"),
                flashableBootPartitions = listOf("boot", "init_boot", "vendor_boot", "dtbo"),
                bootSlots = listOf("a", "b"),
                excludeVbmeta = true,
                recoverySystemMountPoint = "/system_root",
                metadataPolicy = MetadataPolicy.DEVICE_FIRST_WITH_PACKAGE_FALLBACK,
            ),
        )
    }
}
