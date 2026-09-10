/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.target

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TargetPropertiesTest {

    @Test
    fun testFromTargetDeviceEmitsEveryRequiredKey() {
        // Mirrors LtiRomKt/app/src/main/kotlin/io/ltirom/cli/EnvCommand.kt's OUTPUT_ORDER constant
        val requiredKeys = listOf(
            "TARGET_ID", "TARGET_NAME", "TARGET_CODENAME", "DEVICE_CODENAME", "DEVICE_PRODUCT_NAME",
            "TARGET_ASSERT_MODEL", "DEVICE_FASTBOOT_PRODUCT", "TARGET_FIRMWARE", "TARGET_FIRMWARE_SOURCE",
            "TARGET_FIRMWARE_PRODUCT", "TARGET_FIRMWARE_DEVICE", "TARGET_FIRMWARE_INCREMENTAL",
            "TARGET_FIRMWARE_SECURITY_PATCH", "TARGET_BOOT_FIRMWARE_PRODUCT", "TARGET_BOOT_FIRMWARE_DEVICE",
            "TARGET_BOOT_FIRMWARE_INCREMENTAL", "TARGET_BOOT_FIRMWARE_SECURITY_PATCH",
            "TARGET_FIRMWARE_SOURCE_OTA_URL", "TARGET_FIRMWARE_SOURCE_OTA_SHA256",
            "TARGET_FIRMWARE_SOURCE_OTA_SHA256_UNAVAILABLE", "TARGET_FIRMWARE_SOURCE_ZIP_IMAGE_URL",
            "TARGET_FIRMWARE_SOURCE_ZIP_IMAGE_SHA256", "TARGET_FIRMWARE_SOURCE_ZIP_IMAGE_SHA256_UNAVAILABLE",
            "TARGET_FS_TYPE", "TARGET_HAS_SYSTEM_EXT", "TARGET_FLASHABLE_PARTITIONS",
            "TARGET_FLASHABLE_BOOT_PARTITIONS", "TARGET_FLASHABLE_BOOT_SLOTS", "TARGET_FIRMWARE_SOURCE_DIR",
            "TARGET_RECOVERY_SYSTEM_MOUNT_POINT", "TARGET_RECOVERY_DYNAMIC_PARTITION_METADATA_POLICY",
            "SUPER_PARTITION_SIZE", "SUPER_METADATA_SLOTS", "SUPER_VIRTUAL_AB", "TARGET_DISABLE_AVB_SIGNING",
            "SUPER_GROUP_NAME", "SUPER_GROUP_SIZE", "BOOT_PARTITION_SIZE", "VENDOR_BOOT_PARTITION_SIZE",
            "INIT_BOOT_PARTITION_SIZE", "DTBO_PARTITION_SIZE", "AVB_SIGNING_KEY", "BOOT_DEVICE_PATH",
            "TARGET_BUILD_TYPE", "OTA_BASE_URL", "ROM_VERSION", "ROM_BUILD_TIMESTAMP", "ROM_IS_OFFICIAL",
        )

        val sample = TargetDevice.EMPTY.copy(
            id = "PQ84P01",
            name = "REDMAGIC Astra Gaming Tablet",
            codename = "PQ84P01",
        )
        val propertiesString = TargetProperties.fromTargetDevice(sample).toPropertiesString()

        for (key in requiredKeys) {
            assertTrue(
                propertiesString.contains("$key="),
                "Expected properties string to contain key '$key='",
            )
        }
    }

    @Test
    fun testFromTargetDeviceMatchesKnownGoodExample() {
        val defaultFirmware = TargetFirmware(
            version = "REDMAGICOS10.5.15_NP05J_GB",
            buildId = "PQ84P01:15/AQ3A.240812.002/20260311.222527",
            androidVersion = "15",
            securityPatch = "2026-02-01",
            otaUrl = "https://rom.download.nubia.com/Europe&Asia/PQ84P01(NP05J)/REDMAGICOS10.5.15_NP05J_GB/update.zip",
            isOfficial = true,
            isRecommended = true,
        )
        val defaultTarget = TargetDevice(
            id = "PQ84P01",
            name = "REDMAGIC Astra Gaming Tablet",
            codename = "PQ84P01",
            selectedRegion = TargetRegion.GLOBAL,
            availableRegions = listOf(TargetRegion.GLOBAL, TargetRegion.CHINA),
            selectedFirmware = defaultFirmware,
            availableFirmwares = mapOf(TargetRegion.GLOBAL to listOf(defaultFirmware)),
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
        )

        val properties = TargetProperties.fromTargetDevice(defaultTarget)

        assertEquals("PQ84P01-EEA", properties.targetFirmwareProduct)
        assertEquals("PQ84P01", properties.targetFirmwareDevice)
        assertEquals("20260311.222527", properties.targetFirmwareIncremental)
        assertEquals("2026-02-01", properties.targetFirmwareSecurityPatch)
    }

    @Test
    fun testTargetHasSystemExtMatchesFlashablePartitions() {
        // LtiRomKt derives "does this target have a system_ext partition" from
        // TARGET_FLASHABLE_PARTITIONS list membership (ModulesContext.readOnly()), not from
        // TARGET_HAS_SYSTEM_EXT directly - the two must always agree or a config load fails.
        val withSystemExt = TargetDevice.EMPTY.copy(
            dynamicPartitions = listOf("system", "vendor", "system_ext"),
        )
        assertEquals(true, TargetProperties.fromTargetDevice(withSystemExt).targetHasSystemExt)

        val withoutSystemExt = TargetDevice.EMPTY.copy(
            dynamicPartitions = listOf("system", "vendor"),
        )
        assertEquals(false, TargetProperties.fromTargetDevice(withoutSystemExt).targetHasSystemExt)
    }

    @Test
    fun testToPropertiesStringParseRoundTrips() {
        val instance = TargetProperties(
            targetId = "TEST_ID",
            targetName = "Test Name",
            targetCodename = "TEST_DEVICE",
            deviceCodename = "TEST_DEVICE",
            deviceProductName = "TEST_DEVICE-EEA",
            targetAssertModel = "TEST_ASSERT",
            deviceFastbootProduct = "test_fastboot",
            targetFirmware = "TEST:15/AQ3A/20260311.123456",
            targetFirmwareProduct = "TEST_DEVICE-EEA",
            targetFirmwareDevice = "TEST_DEVICE",
            targetFirmwareIncremental = "20260311.123456",
            targetFirmwareSecurityPatch = "2026-03-01",
            targetBootFirmwareProduct = "TEST_DEVICE-EEA",
            targetBootFirmwareDevice = "TEST_DEVICE",
            targetBootFirmwareIncremental = "20260311.123456",
            targetBootFirmwareSecurityPatch = "2026-03-01",
            romBuildTimestamp = "1773273600",
            romIsOfficial = true,
            versionMajor = 2,
            versionMinor = 1,
            versionPatch = 3,
            romVersion = "2.1.3",
            targetFirmwareSource = "zip_image",
            targetFsType = "ext4",
            superPartitionSize = 20000000000L,
        )

        val propertiesString = instance.toPropertiesString()
        val parsed = TargetProperties.parse(propertiesString)

        assertEquals(instance, parsed)
    }
}
