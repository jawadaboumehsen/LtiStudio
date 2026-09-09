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
    UNVERIFIED("Unverified")
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
    CHINA("china", "CN OS", "CN", "CN");

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
    );

    companion object {
        fun fromId(id: String): FirmwareAcquisitionMode =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: OTA
    }
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
    val selectedRegion: TargetRegion = TargetRegion.GLOBAL,
    val availableRegions: List<TargetRegion> = listOf(TargetRegion.GLOBAL, TargetRegion.CHINA),
    val selectedFirmware: TargetFirmware = TargetFirmware(
        version = "REDMAGICOS10.5.15_NP05J_GB",
        buildId = "PQ84P01:15/AQ3A.240812.002/20260311.222527",
        androidVersion = "15",
        securityPatch = "2026-02-01",
        otaUrl = "https://rom.download.nubia.com/Europe&Asia/PQ84P01(NP05J)/REDMAGICOS10.5.15_NP05J_GB/update.zip",
        isOfficial = true,
        isRecommended = true,
    ),
    val availableFirmwares: Map<TargetRegion, List<TargetFirmware>> = emptyMap(),
    val socPlatform: String,
    val filesystemType: String,
    val superPartitionBytes: Long = 17179869184L,
    val superGroupBytes: Long = 17175674880L,
    val bootPartitionBytes: Long = 100663296L,
    val vendorBootPartitionBytes: Long = 100663296L,
    val initBootPartitionBytes: Long = 8388608L,
    val dtboPartitionBytes: Long = 25165824L,
    val acquisitionMode: FirmwareAcquisitionMode = FirmwareAcquisitionMode.OTA,
    val dynamicPartitions: List<String>,
    val bootPartitions: List<String>,
    val status: TargetStatus,
    val description: String,
    val isDefault: Boolean = false,
) {
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
        get() {
            val regionSlug = when (selectedRegion) {
                TargetRegion.GLOBAL -> "GLOBAL"
                TargetRegion.CHINA -> "CN"
            }
            val androidSlug = "A${selectedFirmware.androidVersion.ifBlank { "15" }}"
            return "${codename.uppercase()}_${regionSlug}_$androidSlug"
        }

    val workspaceSlug: String
        get() = targetKey

    val workspacePath: String
        get() = "~/LtiRomWorkDir/target/$targetKey"

    val friendlyTitle: String
        get() = "$name ($codename • ${selectedRegion.displayName} • Android ${selectedFirmware.androidVersion})"

    val displayLabel: String
        get() = "$codename (${selectedRegion.displayName} • Android ${selectedFirmware.androidVersion})"

    companion object {
        val EMPTY = TargetDevice(
            id = "",
            name = "",
            codename = "",
            selectedRegion = TargetRegion.GLOBAL,
            availableRegions = listOf(TargetRegion.GLOBAL, TargetRegion.CHINA),
            selectedFirmware = TargetFirmware(
                version = "1.0.0",
                buildId = "TARGET:15/AQ3A.240812.002/20260101.000000",
                isOfficial = false,
            ),
            availableFirmwares = emptyMap(),
            socPlatform = "Snapdragon 8 Elite",
            filesystemType = "erofs",
            superPartitionBytes = 17179869184L,
            dynamicPartitions = listOf("system", "vendor", "product", "system_ext", "odm"),
            bootPartitions = listOf("boot", "init_boot", "vendor_boot", "dtbo"),
            status = TargetStatus.EXPERIMENTAL,
            description = "Default empty target profile",
            isDefault = false,
        )
    }
}
