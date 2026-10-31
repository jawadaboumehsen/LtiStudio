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
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion

@Serializable
public enum class AcquisitionMode { DOWNLOAD, IMPORT_ARCHIVE }

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
        if (mode == AcquisitionMode.IMPORT_ARCHIVE) {
            require(!archiveRef.isNullOrBlank()) {
                "archiveRef is required for IMPORT_ARCHIVE mode"
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
    /**
     * Partitions whose stock image is replaced by the user's own (`firmware/replacements/<p>.img`), with that
     * image's SHA-256: a file changed outside the Studio stops the build.
     */
    val replacementImages: Map<String, String> = emptyMap(),
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
    /** Boot-chain images edited in the Studio (`overrides/kernel/<p>.img`), by partition, with their SHA-256. */
    val kernelOverrides: Map<String, String> = emptyMap(),
    /** Remove the encryption flags of `/data` from the work tree's fstabs; the phone must be formatted once. */
    val disableDataEncryption: Boolean = false,
    /** Remove the AVB/verity fs_mgr flags (`avb`, `avb_keys`, `verify`) from every line of the work tree's fstabs. */
    val removeIntegrityFlags: Boolean = false,
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
    /** App folders to remove, as `partition:path` (e.g. `system:system/app/Foo`), picked from the inventory. */
    val removeSelectors: List<String> = emptyList(),
    val keepSelectors: List<String> = emptyList(),
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
    /** Shell scripts run on the work tree after the modules, in this order (only the enabled ones). */
    val scripts: List<ProjectScript> = emptyList(),
    /** Give files without fs_config / file_contexts lines those of their folder, at the end of every Patches run. */
    val fillMissingSidecars: Boolean = false,
    /**
     * Rules for files without sidecar lines, tried before "like its folder": one per line, `<regex> <uid> <gid>
     * <mode> <label>`, the regex matched against the whole device path (`/system/app/Foo/Foo.apk`); `#` comments.
     */
    val coverageRules: String = "",
)

/**
 * A shell script kept in the workspace settings and run by the Patches stage as `sh <script> <work dir>
 * <workspace dir>` from inside the work tree. Its text is part of the settings, so editing it re-runs the stage.
 */
@Serializable
public data class ProjectScript(val name: String, val content: String = "", val enabled: Boolean = true)

@Serializable
public data class BuildSettings(
    /** The filesystem dynamic partitions are built as (`erofs`, `ext4`, `f2fs`), unless [partitionFilesystem] says. */
    val filesystem: String = "erofs",
    val compression: String = "lz4hc,9",
    val level: Int? = null,
    val blockSize: Int = 4096,
    val alignment: Int? = null,
    val signing: SigningPolicy = SigningPolicy(),
    val filenameTemplate: String = "Lti_{version}_{date}_{device}-sign.zip",
    /** Per-partition `mkfs.erofs -z` value overriding [compression]; `none` builds that partition uncompressed. */
    val partitionCompression: Map<String, String> = emptyMap(),
    /** Per-partition filesystem overriding [filesystem] (`erofs`, `ext4` or `f2fs`). */
    val partitionFilesystem: Map<String, String> = emptyMap(),
    /** Packages built besides the flashable zip (`PackageKind` names in core:domain: FullPackage, Odin). */
    val extraPackages: Set<String> = emptySet(),
    /** Dynamic partitions also put in the fastboot images package as standalone images (they are in super too). */
    val standaloneImages: List<String> = emptyList(),
    /** Build warnings (`BuildWarningKind` names) the user accepted. */
    val acknowledgedWarnings: Set<String> = emptySet(),
    /** `brotli --quality` of the package's `.new.dat.br` members: 0 (fastest) to 11 (smallest zip). */
    val brotliLevel: Int = 6,
    /** MiB of free space left in a partition built as ext4, by partition, so it can be written to later. */
    val partitionFreeMib: Map<String, Int> = emptyMap(),
    /**
     * Exact image size in bytes of a partition built as ext4 or F2FS, instead of its content's size plus a margin
     * (auto); a whole number of blocks. EROFS is always sized by its content.
     */
    val partitionSizeBytes: Map<String, Long> = emptyMap(),
    /** [standaloneImages] written as Android sparse images in the fastboot images package (smaller, flashed as is). */
    val sparseImages: Set<String> = emptySet(),
    /**
     * EROFS data deduplication by partition (`mkfs.erofs -Ededupe,fragments`): true or false forces it; absent
     * means automatic, on when the boot kernel is 6.1 or newer.
     */
    val partitionDedupe: Map<String, Boolean> = emptyMap(),
    /**
     * EROFS compression cluster in KiB by partition (`mkfs.erofs -C`): bigger compresses better and makes small
     * reads on the device decompress more. Absent means one block (4 KiB), as AOSP builds.
     */
    val partitionClusterKib: Map<String, Int> = emptyMap(),
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
