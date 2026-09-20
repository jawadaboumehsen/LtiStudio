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
import java.security.MessageDigest

/**
 * Immutable configuration snapshot representing the exact settings for a ROM build run.
 */
@Serializable
data class ConfigurationSnapshot(
    val id: String,
    val workspaceId: String,
    val profileRevision: Int,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val acquisition: AcquisitionSettings,
    val extraction: ExtractionSettings,
    val assembly: AssemblySettings,
    val debloat: DebloatSettings,
    val customization: CustomizationSettings,
    val build: BuildSettings,
    val release: ReleaseSettings,
    val publish: PublishSettings,
    val createdAt: Instant,
    val digest: String,
) {
    init {
        require(profileRevision >= 1) { "profileRevision must be >= 1, but was $profileRevision" }
        require(schemaVersion >= 1) { "schemaVersion must be >= 1, but was $schemaVersion" }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1

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
            val acquisition: AcquisitionSettings,
            val extraction: ExtractionSettings,
            val assembly: AssemblySettings,
            val debloat: DebloatSettings,
            val customization: CustomizationSettings,
            val build: BuildSettings,
            val release: ReleaseSettings,
            val publish: PublishSettings,
        )

        fun computeDigest(
            workspaceId: String,
            profileRevision: Int,
            acquisition: AcquisitionSettings,
            extraction: ExtractionSettings,
            assembly: AssemblySettings,
            debloat: DebloatSettings,
            customization: CustomizationSettings,
            build: BuildSettings,
            release: ReleaseSettings,
            publish: PublishSettings,
        ): String {
            val payload = DigestPayload(
                workspaceId = workspaceId,
                profileRevision = profileRevision,
                acquisition = acquisition,
                extraction = extraction,
                assembly = assembly,
                debloat = debloat,
                customization = customization,
                build = build,
                release = release,
                publish = publish,
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
            acquisition: AcquisitionSettings,
            extraction: ExtractionSettings = ExtractionSettings(),
            assembly: AssemblySettings = AssemblySettings(),
            debloat: DebloatSettings = DebloatSettings(),
            customization: CustomizationSettings = CustomizationSettings(),
            build: BuildSettings = BuildSettings(),
            release: ReleaseSettings = ReleaseSettings(),
            publish: PublishSettings = PublishSettings(),
            createdAt: Instant,
            schemaVersion: Int = CURRENT_SCHEMA_VERSION,
        ): ConfigurationSnapshot {
            val digest = computeDigest(
                workspaceId = workspaceId,
                profileRevision = profileRevision,
                acquisition = acquisition,
                extraction = extraction,
                assembly = assembly,
                debloat = debloat,
                customization = customization,
                build = build,
                release = release,
                publish = publish,
            )
            return ConfigurationSnapshot(
                id = id,
                workspaceId = workspaceId,
                profileRevision = profileRevision,
                schemaVersion = schemaVersion,
                acquisition = acquisition,
                extraction = extraction,
                assembly = assembly,
                debloat = debloat,
                customization = customization,
                build = build,
                release = release,
                publish = publish,
                createdAt = createdAt,
                digest = digest,
            )
        }
    }
}
