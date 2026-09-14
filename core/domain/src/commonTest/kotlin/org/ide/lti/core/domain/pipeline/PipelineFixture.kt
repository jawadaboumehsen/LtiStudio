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

import kotlinx.datetime.Instant
import org.ide.lti.core.model.target.FirmwareSourceKind
import org.ide.lti.core.model.target.PackagePolicy
import org.ide.lti.core.model.target.TargetBinding
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.target.TargetStatus
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.SigningPolicy
import org.ide.lti.core.model.workspace.Workspace

/** Shared PQ84P01 profile / workspace / snapshot fixture for pipeline tests. */
@Suppress("MaxLineLength")
class PipelineFixture {
    val targetGlobal = TargetDevice(
        id = "PQ84P01",
        name = "REDMAGIC Astra Gaming Tablet",
        codename = "PQ84P01",
        revision = 2,
        availableRegions = listOf(TargetRegion.GLOBAL),
        availableFirmwares = mapOf(
            TargetRegion.GLOBAL to listOf(
                TargetFirmware(
                    version = "REDMAGICOS10.5.15_NP05J_GB",
                    buildId = "PQ84P01:15/AQ3A.240812.002",
                    androidVersion = "15",
                    securityPatch = "2026-02-01",
                    isRecommended = true,
                ),
            ),
        ),
        packagePolicy = PackagePolicy(
            flashablePartitions = listOf("odm", "product", "system", "system_dlkm", "system_ext", "vendor", "vendor_dlkm"),
            flashableBootPartitions = listOf("boot", "init_boot", "vendor_boot", "dtbo"),
            bootSlots = listOf("a", "b"),
            recoverySystemMountPoint = "/system_root",
        ),
        socPlatform = "Snapdragon 8 Elite",
        filesystemType = "erofs",
        superPartitionBytes = 17179869184L,
        superGroupBytes = 17175674880L,
        superGroupName = "qti_dynamic_partitions",
        superMetadataSlots = 3,
        virtualAb = true,
        dynamicPartitions = listOf("odm", "product", "system", "system_dlkm", "system_ext", "vendor", "vendor_dlkm"),
        bootPartitions = listOf("boot", "init_boot", "vendor_boot", "dtbo"),
        activeSlotSuffix = "_a",
        bootDevicePath = "/dev/block/bootdevice/by-name",
        assertModels = listOf("PQ84P01"),
        hasStandaloneSystemExt = true,
        status = TargetStatus.QUALIFIED,
        description = "Official baseline",
    )

    val targetChina = targetGlobal.copy(
        availableRegions = listOf(TargetRegion.CHINA),
        hasStandaloneSystemExt = false,
    )

    val workspace = Workspace(
        id = "ws_test_01",
        name = "PQ84P01-Global",
        path = "/home/lti/LtiRomWorkDir/workspaces/pq84p01-global",
        linuxPath = "/home/lti/LtiRomWorkDir/workspaces/pq84p01-global",
        targetBinding = TargetBinding("PQ84P01", 2),
    )

    val snapshotGlobalOta = ConfigurationSnapshot.create(
        id = "snap_01",
        workspaceId = "ws_test_01",
        profileRevision = 2,
        acquisition = org.ide.lti.core.model.workspace.AcquisitionSettings(
            mode = org.ide.lti.core.model.workspace.AcquisitionMode.DOWNLOAD,
            region = TargetRegion.GLOBAL,
            firmware = TargetFirmware(
                version = "REDMAGICOS10.5.15_NP05J_GB",
                buildId = "PQ84P01:15/AQ3A.240812.002",
                androidVersion = "15",
                securityPatch = "2026-02-01",
                sourceKind = FirmwareSourceKind.OTA_ZIP,
                acquisitionUrl = "https://update.redmagic.gg/firmware/NP05J_GB.zip",
                sha256 = "c2b647f1146f8c79a29e4726bfcf1a58a7da09f193758bdf214ff94e0192e4ab",
            ),
        ),
        extraction = org.ide.lti.core.model.workspace.ExtractionSettings(),
        assembly = org.ide.lti.core.model.workspace.AssemblySettings(romVersion = "1.0.0", buildType = "userdebug"),
        debloat = org.ide.lti.core.model.workspace.DebloatSettings(enabled = false),
        customization = org.ide.lti.core.model.workspace.CustomizationSettings(),
        build = org.ide.lti.core.model.workspace.BuildSettings(
            packagePolicy = targetGlobal.packagePolicy,
            signing = org.ide.lti.core.model.workspace.SigningPolicy(signImages = true, signPackage = true),
        ),
        release = org.ide.lti.core.model.workspace.ReleaseSettings(otaBaseUrl = "https://ota.ltirom.org/updates"),
        publish = org.ide.lti.core.model.workspace.PublishSettings(),
        createdAt = Instant.fromEpochSeconds(1726056000L),
    )

    val snapshotChinaRaw = ConfigurationSnapshot.create(
        id = "snap_02",
        workspaceId = "ws_test_01",
        profileRevision = 2,
        acquisition = org.ide.lti.core.model.workspace.AcquisitionSettings(
            mode = org.ide.lti.core.model.workspace.AcquisitionMode.DOWNLOAD,
            region = TargetRegion.CHINA,
            firmware = TargetFirmware(
                version = "REDMAGICOS10.5.15_NP05J_CN",
                buildId = "PQ84P01:15/AQ3A.240812.002",
                androidVersion = "15",
                securityPatch = "2026-02-01",
                sourceKind = FirmwareSourceKind.RAW_IMAGE_ZIP,
                acquisitionUrl = "https://update.redmagic.cn/firmware/NP05J_CN.zip",
                sha256 = "a1b2c3d4e5f60000000000000000000000000000000000000000000000000000",
            ),
        ),
        extraction = org.ide.lti.core.model.workspace.ExtractionSettings(),
        assembly = org.ide.lti.core.model.workspace.AssemblySettings(romVersion = "1.0.0", buildType = "user"),
        debloat = org.ide.lti.core.model.workspace.DebloatSettings(enabled = false),
        customization = org.ide.lti.core.model.workspace.CustomizationSettings(),
        build = org.ide.lti.core.model.workspace.BuildSettings(
            packagePolicy = targetChina.packagePolicy,
            signing = org.ide.lti.core.model.workspace.SigningPolicy(signImages = true, signPackage = true),
        ),
        release = org.ide.lti.core.model.workspace.ReleaseSettings(otaBaseUrl = "https://ota.ltirom.org/updates"),
        publish = org.ide.lti.core.model.workspace.PublishSettings(),
        createdAt = Instant.fromEpochSeconds(1726056000L),
    )

    /** Values the orchestrator seeds at run start. */
    val runValues = mapOf(RuntimeKeys.RUN_DATE to "20260911", RuntimeKeys.RUN_TIMESTAMP to "1726056000")

    fun globalOtaContext(): StageContext = StageContext(workspace, snapshotGlobalOta, targetGlobal, runValues)
    fun chinaRawContext(): StageContext = StageContext(workspace, snapshotChinaRaw, targetChina, runValues)
}
