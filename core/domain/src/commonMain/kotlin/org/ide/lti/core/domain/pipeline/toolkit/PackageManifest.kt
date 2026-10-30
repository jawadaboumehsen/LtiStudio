/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.toolkit

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Metadata entry for a single partition discovered in an incoming firmware package.
 */
@Serializable
public data class PartitionEntry(val name: String, val sizeBytes: Long, val sourceMember: String) {
    init {
        require(name.isNotBlank()) { "Partition name must not be blank" }
        require(sizeBytes >= 0) { "Partition sizeBytes must be non-negative" }
        require(sourceMember.isNotBlank()) { "sourceMember must not be blank" }
    }
}

/**
 * Manifest describing an imported vendor firmware package (FR-001, FR-007).
 */
@Serializable
public data class PackageManifest(
    val format: PackageFormat,
    val declaredChecksum: String? = null,
    val partitions: List<PartitionEntry> = emptyList(),
    val refusal: Refusal? = null,
) {

    public companion object {
        private val json = Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        public fun encode(manifest: PackageManifest): String =
            json.encodeToString(PackageManifest.serializer(), manifest)

        public fun decode(string: String): PackageManifest = json.decodeFromString(PackageManifest.serializer(), string)

        public fun incrementalRefusal(item: String, details: String? = null): PackageManifest = PackageManifest(
            format = PackageFormat.AndroidPayload,
            refusal = Refusal(
                cause = "The package is an incremental update.",
                item = item,
                nextStep = "Import the full update instead.",
                details = details,
            ),
        )

        public fun unknownRefusal(item: String, details: String? = null): PackageManifest = PackageManifest(
            format = PackageFormat.Unknown,
            refusal = Refusal(
                cause = "The package format was not recognized.",
                item = item,
                nextStep = "Use a supported ZIP, 7z, TAR, payload.bin, block-OTA, or Odin archive.",
                details = details,
            ),
        )
    }
}

/**
 * Result of comparing a firmware package's actual checksum against its expected checksum (FR-006).
 */
@Serializable
public data class ChecksumResult(val ok: Boolean, val expected: String?, val actual: String) {
    public companion object {
        private val json = Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        public fun encode(result: ChecksumResult): String = json.encodeToString(serializer(), result)

        public fun decode(string: String): ChecksumResult = json.decodeFromString(serializer(), string)
    }
}
