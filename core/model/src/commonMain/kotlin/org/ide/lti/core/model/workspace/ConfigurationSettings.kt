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

import kotlinx.serialization.Serializable
import org.ide.lti.core.model.target.PackagePolicy
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion

@Serializable
public enum class AcquisitionMode { DOWNLOAD, IMPORT_ARCHIVE, EXISTING_ARCHIVE }

@Serializable
public data class AcquisitionSettings(
    val mode: AcquisitionMode = AcquisitionMode.DOWNLOAD,
    val region: TargetRegion,
    val firmware: TargetFirmware,
    val archiveRef: String? = null,
    val expectedSha256: String? = null,
    val retries: Int = 3,
    val resume: Boolean = true,
) {
    init {
        require(retries in 0..5) { "retries must be in 0..5, was $retries" }
        if (mode == AcquisitionMode.IMPORT_ARCHIVE || mode == AcquisitionMode.EXISTING_ARCHIVE) {
            require(!archiveRef.isNullOrBlank()) {
                "archiveRef is required for IMPORT_ARCHIVE and EXISTING_ARCHIVE modes"
            }
        }
    }
}

@Serializable
public data class ExtractionSettings(
    val adapter: String = "AUTO",
    val dynamicPartitions: List<String> = emptyList(),
    val bootPartitions: List<String> = emptyList(),
    val slot: String? = null,
    val reuseVerifiedExtraction: Boolean = true,
)

@Serializable
public data class AssemblySettings(
    val includedPartitions: List<String> = emptyList(),
    val systemExtMode: String = "auto",
    val bootFooterPolicy: String = "erase",
    val baselineCleanup: Boolean = true,
    val buildType: String = "user",
    val romVersion: String = "1.0.0",
    val propertyOverrides: Map<String, String> = emptyMap(),
) {
    init {
        propertyOverrides.forEach { (k, v) ->
            require('\n' !in k && '\u0000' !in k) {
                "propertyOverrides key must not contain newline or NUL: '$k'"
            }
            require('\n' !in v && '\u0000' !in v) {
                "propertyOverrides value must not contain newline or NUL for key '$k'"
            }
        }
    }
}

@Serializable
public data class DebloatSettings(
    val enabled: Boolean = false,
    val presetId: String? = null,
    val presetRevision: Int? = null,
    val presetDigest: String? = null,
    val removeSelectors: List<String> = emptyList(),
    val keepSelectors: List<String> = emptyList(),
    val protectedPolicyRevision: Int? = null,
)

@Serializable
public data class PluginLockfile(
    val schemaVersion: Int = 1,
    val entries: List<PluginLockfileEntry> = emptyList(),
    val edges: List<String> = emptyList(),
)

@Serializable
public data class PluginLockfileEntry(
    val publisher: String,
    val id: String,
    val version: String,
    val contentDigest: String,
)

@Serializable
public data class CustomizationSettings(
    val lockfile: PluginLockfile = PluginLockfile(),
    val enabledPackages: List<String> = emptyList(),
    val settings: Map<String, Map<String, String>> = emptyMap(),
    val templateDigest: String? = null,
    val resolvedPlanDigest: String? = null,
)

@Serializable
public data class BuildSettings(
    val filesystem: String = "erofs",
    val compression: String = "lz4hc,9",
    val level: Int? = null,
    val blockSize: Int = 4096,
    val alignment: Int? = null,
    val packagePolicy: PackagePolicy = PackagePolicy(),
    val signing: SigningPolicy = SigningPolicy(),
    val filenameTemplate: String = "Lti_{version}_{date}_{device}-sign.zip",
)

@Serializable
public data class SigningPolicy(
    val signImages: Boolean = true,
    val signPackage: Boolean = true,
    val avbKeyRef: String = "keys/avb.pem",
    val platformKeyRef: String = "keys/platform",
)

@Serializable
public data class ReleaseSettings(
    val channel: String = "internal",
    val changelog: String = "",
    val otaBaseUrl: String = "https://example.com/updates",
) {
    init {
        require(otaBaseUrl.startsWith("https://")) { "otaBaseUrl must start with https://, was '$otaBaseUrl'" }
        require(!otaBaseUrl.endsWith("/")) {
            "otaBaseUrl must not end with a trailing slash, was '$otaBaseUrl'"
        }
    }
}

@Serializable
public enum class PublishProvider { NONE, GITHUB_RELEASE, LOCAL_EXPORT }

@Serializable
public enum class CollisionPolicy { FAIL, OVERWRITE, SKIP }

@Serializable
public data class PublishSettings(
    val provider: PublishProvider = PublishProvider.NONE,
    val repository: String? = null,
    val sourceTag: String? = null,
    val partSizeBytes: Long = 1_900_000_000L,
    val manifestRepository: String? = null,
    val manifestBranch: String? = null,
    val manifestPath: String? = null,
    val manifestPublicUrl: String? = null,
    val retentionPerDevice: Int = 3,
    val credentialRef: String? = null,
    val localExportFolder: String? = null,
    val collisionPolicy: CollisionPolicy = CollisionPolicy.FAIL,
)
