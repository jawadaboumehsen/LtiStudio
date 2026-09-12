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

/**
 * Shared canonical catalog of default official hardware target definitions and firmware baselines.
 */
object DefaultTargetCatalog {
    val PQ84P01_GLOBAL_FIRMWARES: List<TargetFirmware> = listOf(
        TargetFirmware(
            version = "REDMAGICOS10.5.15_NP05J_GB",
            buildId = "PQ84P01:15/AQ3A.240812.002/20260311.222527",
            androidVersion = "15",
            securityPatch = "2026-02-01",
            otaUrl = "https://rom.download.nubia.com/Europe&Asia/PQ84P01(NP05J)/REDMAGICOS10.5.15_NP05J_GB/update.zip",
            isOfficial = true,
            isRecommended = true,
            acquisitionUrl = "https://rom.download.nubia.com/Europe&Asia/PQ84P01(NP05J)/REDMAGICOS10.5.15_NP05J_GB/update.zip",
            checksumUnavailable = true,
            sourceKind = FirmwareSourceKind.OTA_ZIP,
        ),
        TargetFirmware(
            version = "REDMAGICOS10.5.12_NP05J_GB",
            buildId = "PQ84P01:15/AQ3A.240812.002/20260120.143210",
            androidVersion = "15",
            securityPatch = "2026-01-01",
            otaUrl = "https://rom.download.nubia.com/Europe&Asia/PQ84P01(NP05J)/REDMAGICOS10.5.12_NP05J_GB/update.zip",
            isOfficial = true,
            isRecommended = false,
            acquisitionUrl = "https://rom.download.nubia.com/Europe&Asia/PQ84P01(NP05J)/REDMAGICOS10.5.12_NP05J_GB/update.zip",
            checksumUnavailable = true,
            sourceKind = FirmwareSourceKind.OTA_ZIP,
        ),
        TargetFirmware(
            version = "REDMAGICOS10.5.8_NP05J_GB",
            buildId = "PQ84P01:15/AQ3A.240812.002/20251115.110543",
            androidVersion = "15",
            securityPatch = "2025-11-01",
            otaUrl = "https://rom.download.nubia.com/Europe&Asia/PQ84P01(NP05J)/REDMAGICOS10.5.8_NP05J_GB/update.zip",
            isOfficial = true,
            isRecommended = false,
            acquisitionUrl = "https://rom.download.nubia.com/Europe&Asia/PQ84P01(NP05J)/REDMAGICOS10.5.8_NP05J_GB/update.zip",
            checksumUnavailable = true,
            sourceKind = FirmwareSourceKind.OTA_ZIP,
        ),
    )

    val PQ84P01_CHINA_FIRMWARES: List<TargetFirmware> = listOf(
        TargetFirmware(
            version = "REDMAGICOS10.5.16_NP05J_CN",
            buildId = "PQ84P01:15/AQ3A.240812.002/20260315.191244",
            androidVersion = "15",
            securityPatch = "2026-02-01",
            otaUrl = "https://rom.download.nubia.com/China/PQ84P01(NP05J)/REDMAGICOS10.5.16_NP05J_CN/update.zip",
            isOfficial = true,
            isRecommended = true,
            acquisitionUrl = "https://rom.download.nubia.com/China/PQ84P01(NP05J)/REDMAGICOS10.5.16_NP05J_CN/update.zip",
            checksumUnavailable = true,
            sourceKind = FirmwareSourceKind.OTA_ZIP,
        ),
        TargetFirmware(
            version = "REDMAGICOS10.5.12_NP05J_CN",
            buildId = "PQ84P01:15/AQ3A.240812.002/20260125.102230",
            androidVersion = "15",
            securityPatch = "2026-01-01",
            otaUrl = "https://rom.download.nubia.com/China/PQ84P01(NP05J)/REDMAGICOS10.5.12_NP05J_CN/update.zip",
            isOfficial = true,
            isRecommended = false,
            acquisitionUrl = "https://rom.download.nubia.com/China/PQ84P01(NP05J)/REDMAGICOS10.5.12_NP05J_CN/update.zip",
            checksumUnavailable = true,
            sourceKind = FirmwareSourceKind.OTA_ZIP,
        ),
        TargetFirmware(
            version = "REDMAGICOS10.5.8_NP05J_CN",
            buildId = "PQ84P01:15/AQ3A.240812.002/20251120.091512",
            androidVersion = "15",
            securityPatch = "2025-11-01",
            otaUrl = "https://rom.download.nubia.com/China/PQ84P01(NP05J)/REDMAGICOS10.5.8_NP05J_CN/update.zip",
            isOfficial = true,
            isRecommended = false,
            acquisitionUrl = "https://rom.download.nubia.com/China/PQ84P01(NP05J)/REDMAGICOS10.5.8_NP05J_CN/update.zip",
            checksumUnavailable = true,
            sourceKind = FirmwareSourceKind.OTA_ZIP,
        ),
        TargetFirmware(
            version = "REDMAGICOS10.5.16_MR01A",
            buildId = "PQ84P01:15/AQ3A.240812.002/20260315.191244",
            androidVersion = "15",
            securityPatch = "2026-02-01",
            otaUrl = "https://rom.download.nubia.com/China/PQ84P01/REDMAGICOS10.5.16_MR01A/update.zip",
            isOfficial = true,
            isRecommended = false,
            acquisitionUrl = "https://rom.download.nubia.com/China/PQ84P01/REDMAGICOS10.5.16_MR01A/update.zip",
            checksumUnavailable = true,
            sourceKind = FirmwareSourceKind.OTA_ZIP,
        ),
    )

    val PQ84P01_FIRMWARES: Map<TargetRegion, List<TargetFirmware>> = mapOf(
        TargetRegion.GLOBAL to PQ84P01_GLOBAL_FIRMWARES,
        TargetRegion.CHINA to PQ84P01_CHINA_FIRMWARES,
    )

    val PQ84P01_DEFAULT: TargetDevice = TargetDevice(
        id = "PQ84P01",
        name = "REDMAGIC Astra Gaming Tablet",
        codename = "PQ84P01",
        revision = 1,
        availableRegions = listOf(TargetRegion.GLOBAL, TargetRegion.CHINA),
        availableFirmwares = PQ84P01_FIRMWARES,
        socPlatform = "Snapdragon 8 Elite (SM8750 / sun)",
        filesystemType = "erofs",
        superPartitionBytes = 17179869184L,
        dynamicPartitions = listOf(
            "system",
            "vendor",
            "product",
            "system_ext",
            "odm",
            "vendor_dlkm",
            "system_dlkm",
        ),
        bootPartitions = listOf("boot", "init_boot", "vendor_boot", "dtbo"),
        status = TargetStatus.QUALIFIED,
        description = "Official Global & European retail baseline (NP05J_GB)",
        isDefault = true,
        productName = "PQ84P01",
        fastbootProduct = "sun",
        assertModels = listOf("PQ84P01"),
        superMetadataSlots = 3,
        superGroupName = "qti_dynamic_partitions",
        virtualAb = true,
        activeSlotSuffix = "_a",
        hasStandaloneSystemExt = true,
        bootDevicePath = "/dev/block/bootdevice/by-name",
        packagePolicy = PackagePolicy(
            flashablePartitions = listOf(
                "system",
                "vendor",
                "product",
                "system_ext",
                "odm",
                "vendor_dlkm",
                "system_dlkm",
            ),
            flashableBootPartitions = listOf("boot", "init_boot", "vendor_boot", "dtbo"),
            bootSlots = listOf("a", "b"),
            excludeVbmeta = true,
            recoverySystemMountPoint = "/system_root",
            metadataPolicy = MetadataPolicy.DEVICE_FIRST_WITH_PACKAGE_FALLBACK,
        ),
    )
}
