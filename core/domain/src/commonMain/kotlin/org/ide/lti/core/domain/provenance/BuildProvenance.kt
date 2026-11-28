/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.provenance

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.ide.lti.core.domain.pipeline.CacheKeys
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

/**
 * Machine-verifiable Build Provenance Manifest / SBOM linking built `.img` and `.zip` outputs
 * to exact stock firmware inputs, applied mods, debloat removals, LtiRomServer tools, and signing keys.
 */
@Serializable
data class BuildProvenance(
    val schemaVersion: Int = SCHEMA_VERSION,
    val format: String = FORMAT_ID,
    val runId: String,
    val workspaceId: String,
    val workspaceName: String,
    val builtAtIso: String,
    val reproducibleEpochSeconds: Long,
    val configurationDigest: String,
    val targetDevice: TargetProvenance,
    val firmwareInput: FirmwareProvenance,
    val appliedMods: List<ModProvenance> = emptyList(),
    val removedPackages: List<String> = emptyList(),
    val toolVersions: Map<String, String> = emptyMap(),
    val signing: SigningProvenance,
    val outputs: List<OutputProvenanceEntry> = emptyList(),
    val manifestDigest: String? = null,
) {
    /** Canonical JSON encoding without pretty whitespace, preserving deterministic field order. */
    fun toCanonicalJson(): String = ConfigurationSnapshot.canonicalJson.encodeToString(this)

    /** Computes [manifestDigest] over the normalized manifest with [manifestDigest] cleared. */
    fun canonicalContentDigest(): String = CacheKeys.sha256(copy(manifestDigest = null).toCanonicalJson())

    /** Returns a copy stamped with its self-verifying [manifestDigest]. */
    fun withSelfDigest(): BuildProvenance = copy(manifestDigest = canonicalContentDigest())

    /** True iff [manifestDigest] is present and matches [canonicalContentDigest]. */
    fun hasValidSelfDigest(): Boolean = !manifestDigest.isNullOrBlank() && manifestDigest == canonicalContentDigest()

    companion object {
        const val SCHEMA_VERSION: Int = 1
        const val FORMAT_ID: String = "ltirom.provenance.sbom/v1"
        const val WORKSPACE_PATH: String = "out/provenance.sbom.json"
        const val RECORD_FILE_NAME: String = "provenance.sbom.json"

        fun fromJson(json: String): BuildProvenance =
            ConfigurationSnapshot.canonicalJson.decodeFromString(serializer(), json)
    }
}

@Serializable
data class TargetProvenance(
    val id: String,
    val codename: String,
    val name: String,
    val superGroupName: String,
    val superGroupBytes: Long,
)

@Serializable
data class FirmwareProvenance(
    val mode: String,
    val version: String,
    val buildId: String,
    val region: String,
    val securityPatch: String,
    val expectedSha256: String? = null,
    val measuredArchiveSha256: String? = null,
    val archiveRelPath: String,
)

@Serializable
data class ModProvenance(
    val id: String,
    val name: String,
    val version: String,
    val contentDigest: String? = null,
    val changedFilesCount: Int = 0,
)

@Serializable
data class SigningProvenance(
    val signImages: Boolean,
    val signPackage: Boolean,
    val avbKeyRef: String,
    val avbKeySha256Der: String? = null,
    val avbPubkeySha1: String? = null,
    val platformKeyRef: String,
    val platformCertSha256: String? = null,
    val platformCertSha1: String? = null,
)

@Serializable
data class OutputProvenanceEntry(
    val relPath: String,
    val kind: String,
    val sizeBytes: Long? = null,
    val sha256: String,
    val filesystem: String? = null,
    val compression: String? = null,
    val avbSigned: Boolean = false,
)

enum class ProvenanceCheckStatus {
    VERIFIED,
    DRIFTED,
    MISSING,
    UNRECORDED_HASH,
}

data class ProvenanceArtifactCheck(
    val relPath: String,
    val kind: String,
    val expectedSha256: String,
    val actualSha256: String?,
    val expectedSizeBytes: Long?,
    val actualSizeBytes: Long?,
    val status: ProvenanceCheckStatus,
)

data class ProvenanceVerificationResult(
    val runId: String,
    val selfDigestValid: Boolean,
    val firmwareMatches: Boolean?,
    val avbKeyMatches: Boolean?,
    val platformKeyMatches: Boolean?,
    val outputChecks: List<ProvenanceArtifactCheck>,
    val problems: List<String>,
) {
    val passed: Boolean
        get() = selfDigestValid &&
            problems.isEmpty() &&
            outputChecks.isNotEmpty() &&
            outputChecks.all { it.status == ProvenanceCheckStatus.VERIFIED }
}
