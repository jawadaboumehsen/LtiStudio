/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.plugin

import kotlinx.serialization.Serializable

@Serializable
data class IndexEntry(
    val publisher: String,
    val id: String,
    val version: String,
    val sdkApiRange: String,
    val targetSummary: String,
    val packageUrl: String,
    val contentDigest: String,
    val signatureIdentity: String?,
    val revoked: Boolean = false,
    /** The sha256 of the file at [packageUrl], when the source lists it (a GitHub release asset's digest). */
    val fileSha256: String? = null,
)

@Serializable
data class MarketplaceIndexDocument(val schemaVersion: Int = 1, val entries: List<IndexEntry> = emptyList())

data class IndexSource(val label: String, val url: String)

sealed interface IndexFetchResult {
    data class Fetched(val doc: MarketplaceIndexDocument, val observedAtEpochMs: Long) : IndexFetchResult
    data class Cached(val doc: MarketplaceIndexDocument, val observedAtEpochMs: Long) : IndexFetchResult
    data class Failed(val reason: IndexFailure) : IndexFetchResult
}

sealed interface IndexFailure {
    data object Unauthenticated : IndexFailure
    data object Offline : IndexFailure
    data class Malformed(val detail: String) : IndexFailure
    data class RateLimited(val retryAfterSeconds: Long?) : IndexFailure
    data object InsecureSource : IndexFailure
    data class ServerError(val code: Int) : IndexFailure
}

/**
 * A marketplace source: an index document over HTTPS, or a GitHub repository (`https://github.com/owner/repo`) whose
 * latest release's `.lti-mod.zip` assets are its entries.
 */
interface MarketplaceIndexPort {
    suspend fun fetch(source: IndexSource, allowCache: Boolean): IndexFetchResult

    /** The source fetched last, so the marketplace opens on it again. */
    suspend fun lastSource(): IndexSource? = null
}
