/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.workspace

import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion

/**
 * Mutable, per-workspace editing state for target configuration.
 *
 * Preserved across environment readiness changes (FR-029).
 * Saving this draft creates an immutable [ConfigurationSnapshot].
 */
data class ConfigurationDraft(
    val region: TargetRegion,
    val firmware: TargetFirmware,
    val acquisitionMode: AcquisitionMode = AcquisitionMode.DOWNLOAD,
    val importedArchiveName: String? = null,
    val buildType: String = "user",
    val romVersion: String = "1.0.0",
    val otaBaseUrl: String = "https://example.com/updates",
) {
    fun toSnapshot(
        id: String,
        workspaceId: String,
        profileRevision: Int,
        packagePolicy: org.ide.lti.core.model.target.PackagePolicy,
        createdAt: kotlinx.datetime.Instant,
    ): ConfigurationSnapshot = ConfigurationSnapshot.create(
        id = id,
        workspaceId = workspaceId,
        profileRevision = profileRevision,
        acquisition = AcquisitionSettings(
            mode = acquisitionMode,
            region = region,
            firmware = firmware,
            archiveRef = importedArchiveName,
        ),
        assembly = AssemblySettings(
            buildType = buildType,
            romVersion = romVersion,
        ),
        build = BuildSettings(
            packagePolicy = packagePolicy,
        ),
        release = ReleaseSettings(
            otaBaseUrl = otaBaseUrl,
        ),
        createdAt = createdAt,
    )
}

fun ConfigurationSnapshot.toDraft(): ConfigurationDraft = ConfigurationDraft(
    region = acquisition.region,
    firmware = acquisition.firmware,
    acquisitionMode = acquisition.mode,
    importedArchiveName = acquisition.archiveRef,
    buildType = assembly.buildType,
    romVersion = assembly.romVersion,
    otaBaseUrl = release.otaBaseUrl,
)
