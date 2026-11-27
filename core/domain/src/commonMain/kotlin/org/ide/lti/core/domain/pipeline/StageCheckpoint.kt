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

import org.ide.lti.core.model.run.StageId

enum class ManifestEntryKind {
    FILE,
    SYMLINK,
    SIDECAR,
}

data class ManifestEntry(val relPath: String, val sizeBytes: Long, val sha256: String, val kind: ManifestEntryKind)

data class ContentManifest(val entries: List<ManifestEntry>) {
    fun canonicalDigest(): String {
        val serialized = entries
            .sortedBy { it.relPath }
            .joinToString("") { entry ->
                "${entry.kind.name}\t${entry.relPath}\t${entry.sizeBytes}\t${entry.sha256}\n"
            }
        return CacheKeys.sha256(serialized)
    }
}

data class VerificationReceipt(
    val verifiedAtEpochMs: Long,
    val method: String,
    val verified: Boolean,
    val mismatches: List<String> = emptyList(),
)

/**
 * Working paths are private staging copies/reflinks, never writable upstream inputs.
 */
data class StageCheckpoint(
    val stageId: StageId,
    val stageVersion: Int,
    val inputDigest: String,
    val rootRelPath: String,
    val manifest: ContentManifest,
    val sidecarRelPaths: List<String>,
    val outputDigest: String,
    val verification: VerificationReceipt?,
) {
    init {
        require(outputDigest == manifest.canonicalDigest()) {
            "outputDigest '$outputDigest' does not match manifest canonicalDigest '${manifest.canonicalDigest()}'"
        }
    }
}
