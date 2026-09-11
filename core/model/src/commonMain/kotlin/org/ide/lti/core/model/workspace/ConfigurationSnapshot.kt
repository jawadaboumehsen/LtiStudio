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

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.model.target.PackagePolicy
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import java.security.MessageDigest

@Serializable
enum class SnapshotAcquisitionMode {
    DOWNLOAD,
    IMPORT_ARCHIVE,
}

@Serializable
data class SigningPolicy(
    val signImages: Boolean = true,
    val signPackage: Boolean = true,
    val avbKeyRef: String = "keys/avb.pem",
    val platformKeyRef: String = "keys/platform",
)

/**
 * Immutable configuration snapshot representing the exact settings for a ROM build run.
 */
@Serializable
data class ConfigurationSnapshot(
    val id: String,
    val workspaceId: String,
    val profileRevision: Int,
    val region: TargetRegion,
    val firmware: TargetFirmware,
    val acquisitionMode: SnapshotAcquisitionMode = SnapshotAcquisitionMode.DOWNLOAD,
    val importedArchiveName: String? = null,
    val enabledModuleIds: List<String> = emptyList(),
    val packagePolicy: PackagePolicy,
    val signing: SigningPolicy = SigningPolicy(),
    val buildType: String = "user",
    val romVersion: String = "1.0.0",
    val otaBaseUrl: String = "https://example.com/updates",
    val pipelineVersion: Int = 1,
    val createdAt: Instant,
    val digest: String,
) {
    init {
        require(profileRevision >= 1) { "profileRevision must be >= 1, but was $profileRevision" }
        require(pipelineVersion >= 1) { "pipelineVersion must be >= 1, but was $pipelineVersion" }
        require(otaBaseUrl.startsWith("https://")) { "otaBaseUrl must start with https://, was '$otaBaseUrl'" }
        require(!otaBaseUrl.endsWith("/")) { "otaBaseUrl must not end with a trailing slash, was '$otaBaseUrl'" }
        if (acquisitionMode == SnapshotAcquisitionMode.IMPORT_ARCHIVE) {
            require(!importedArchiveName.isNullOrBlank()) { "importedArchiveName is required for IMPORT_ARCHIVE mode" }
        }
    }

    companion object {
        val canonicalJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            prettyPrint = false
        }

        @Serializable
        private data class DigestPayload(
            val workspaceId: String,
            val profileRevision: Int,
            val region: TargetRegion,
            val firmware: TargetFirmware,
            val acquisitionMode: SnapshotAcquisitionMode,
            val importedArchiveName: String?,
            val enabledModuleIds: List<String>,
            val packagePolicy: PackagePolicy,
            val signing: SigningPolicy,
            val buildType: String,
            val romVersion: String,
            val otaBaseUrl: String,
            val pipelineVersion: Int,
        )

        fun computeDigest(
            workspaceId: String,
            profileRevision: Int,
            region: TargetRegion,
            firmware: TargetFirmware,
            acquisitionMode: SnapshotAcquisitionMode,
            importedArchiveName: String?,
            enabledModuleIds: List<String>,
            packagePolicy: PackagePolicy,
            signing: SigningPolicy,
            buildType: String,
            romVersion: String,
            otaBaseUrl: String,
            pipelineVersion: Int,
        ): String {
            val payload = DigestPayload(
                workspaceId = workspaceId,
                profileRevision = profileRevision,
                region = region,
                firmware = firmware,
                acquisitionMode = acquisitionMode,
                importedArchiveName = importedArchiveName,
                enabledModuleIds = enabledModuleIds,
                packagePolicy = packagePolicy,
                signing = signing,
                buildType = buildType,
                romVersion = romVersion,
                otaBaseUrl = otaBaseUrl,
                pipelineVersion = pipelineVersion,
            )
            val jsonString = canonicalJson.encodeToString(payload)
            val md = MessageDigest.getInstance("SHA-256")
            val hashBytes = md.digest(jsonString.toByteArray(Charsets.UTF_8))
            return "sha256:" + hashBytes.joinToString("") { "%02x".format(it) }
        }

        fun create(
            id: String,
            workspaceId: String,
            profileRevision: Int,
            region: TargetRegion,
            firmware: TargetFirmware,
            acquisitionMode: SnapshotAcquisitionMode = SnapshotAcquisitionMode.DOWNLOAD,
            importedArchiveName: String? = null,
            enabledModuleIds: List<String> = emptyList(),
            packagePolicy: PackagePolicy,
            signing: SigningPolicy = SigningPolicy(),
            buildType: String = "user",
            romVersion: String = "1.0.0",
            otaBaseUrl: String = "https://example.com/updates",
            pipelineVersion: Int = 1,
            createdAt: Instant,
        ): ConfigurationSnapshot {
            val digest = computeDigest(
                workspaceId = workspaceId,
                profileRevision = profileRevision,
                region = region,
                firmware = firmware,
                acquisitionMode = acquisitionMode,
                importedArchiveName = importedArchiveName,
                enabledModuleIds = enabledModuleIds,
                packagePolicy = packagePolicy,
                signing = signing,
                buildType = buildType,
                romVersion = romVersion,
                otaBaseUrl = otaBaseUrl,
                pipelineVersion = pipelineVersion,
            )
            return ConfigurationSnapshot(
                id = id,
                workspaceId = workspaceId,
                profileRevision = profileRevision,
                region = region,
                firmware = firmware,
                acquisitionMode = acquisitionMode,
                importedArchiveName = importedArchiveName,
                enabledModuleIds = enabledModuleIds,
                packagePolicy = packagePolicy,
                signing = signing,
                buildType = buildType,
                romVersion = romVersion,
                otaBaseUrl = otaBaseUrl,
                pipelineVersion = pipelineVersion,
                createdAt = createdAt,
                digest = digest,
            )
        }
    }
}
