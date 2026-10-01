/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.plugin

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.ide.lti.core.data.admission.atomicWrite
import org.ide.lti.core.domain.plugin.IndexFailure
import org.ide.lti.core.domain.plugin.IndexFetchResult
import org.ide.lti.core.domain.plugin.IndexSource
import org.ide.lti.core.domain.plugin.MarketplaceIndexDocument
import org.ide.lti.core.domain.plugin.MarketplaceIndexPort
import org.ide.lti.core.domain.plugin.PluginPackagePolicy
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Adapter that fetches and caches marketplace index documents over HTTPS.
 *
 * Successful network fetches cache the document under `<cacheDir>/<sha256 of source.url>.json` alongside
 * the timestamp at which it was observed.
 *
 * When a network fetch encounters an [IndexFailure.Offline] error (and only Offline), and [allowCache] is true,
 * the adapter returns [IndexFetchResult.Cached] containing the cached document and the ORIGINAL observation
 * timestamp ([IndexFetchResult.Cached.observedAtEpochMs]), so that the UI can display the true observation age
 * without restamping it as fresh.
 *
 * Other failures (e.g. server errors, rate limiting, authentication issues, malformed content, or insecure sources)
 * never fall back to cached data.
 */
@Suppress("ReturnCount")
class PluginIndexAdapter(
    private val cacheDir: Path,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val connectionOpener: (URI) -> HttpURLConnection = { uri ->
        uri.toURL().openConnection() as HttpURLConnection
    },
) : MarketplaceIndexPort {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        prettyPrint = false
    }

    @Serializable
    private data class CachedIndexRecord(val observedAtEpochMs: Long, val document: MarketplaceIndexDocument)

    override suspend fun fetch(source: IndexSource, allowCache: Boolean): IndexFetchResult {
        val uri = try {
            URI(source.url)
        } catch (_: Exception) {
            return IndexFetchResult.Failed(IndexFailure.InsecureSource)
        }

        if (uri.scheme?.lowercase() != "https" || !uri.userInfo.isNullOrBlank()) {
            return IndexFetchResult.Failed(IndexFailure.InsecureSource)
        }

        return when (val stepResult = fetchWithRedirects(uri)) {
            is RemoteFetchResult.Success -> {
                val now = timeProvider()
                cacheSuccess(source.url, stepResult.doc, now)
                IndexFetchResult.Fetched(stepResult.doc, now)
            }
            is RemoteFetchResult.Failure -> {
                if (stepResult.reason is IndexFailure.Offline && allowCache) {
                    val cached = readCached(source.url)
                    if (cached != null) {
                        IndexFetchResult.Cached(cached.document, cached.observedAtEpochMs)
                    } else {
                        IndexFetchResult.Failed(stepResult.reason)
                    }
                } else {
                    IndexFetchResult.Failed(stepResult.reason)
                }
            }
        }
    }

    private fun fetchWithRedirects(initialUri: URI): RemoteFetchResult {
        var currentUri = initialUri
        var redirects = 0

        while (redirects <= MAX_REDIRECTS) {
            when (val step = stepConnection(currentUri)) {
                is StepResult.Redirect -> {
                    redirects++
                    if (redirects > MAX_REDIRECTS) {
                        return RemoteFetchResult.Failure(IndexFailure.Malformed("Too many redirects"))
                    }
                    currentUri = step.nextUri
                }
                is StepResult.Done -> return step.result
            }
        }
        return RemoteFetchResult.Failure(IndexFailure.Malformed("Too many redirects"))
    }

    private fun stepConnection(currentUri: URI): StepResult {
        val conn = try {
            connectionOpener(currentUri).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
            }
        } catch (_: IOException) {
            return StepResult.Done(RemoteFetchResult.Failure(IndexFailure.Offline))
        } catch (_: Exception) {
            return StepResult.Done(RemoteFetchResult.Failure(IndexFailure.Offline))
        }

        return try {
            when (val code = conn.responseCode) {
                in REDIRECT_RANGE -> handleRedirect(conn, currentUri)
                in SUCCESS_RANGE -> StepResult.Done(handleSuccess(conn))
                401, 403 -> {
                    conn.disconnect()
                    StepResult.Done(RemoteFetchResult.Failure(IndexFailure.Unauthenticated))
                }
                429 -> {
                    val retryAfter = conn.getHeaderField("Retry-After")?.trim()?.toLongOrNull()
                    conn.disconnect()
                    StepResult.Done(RemoteFetchResult.Failure(IndexFailure.RateLimited(retryAfter)))
                }
                in 500..599 -> {
                    conn.disconnect()
                    StepResult.Done(RemoteFetchResult.Failure(IndexFailure.ServerError(code)))
                }
                else -> {
                    conn.disconnect()
                    StepResult.Done(RemoteFetchResult.Failure(IndexFailure.Malformed("HTTP $code")))
                }
            }
        } catch (_: IOException) {
            conn.disconnect()
            StepResult.Done(RemoteFetchResult.Failure(IndexFailure.Offline))
        } catch (e: Exception) {
            conn.disconnect()
            StepResult.Done(RemoteFetchResult.Failure(IndexFailure.Malformed(e.message ?: "Unknown error")))
        }
    }

    private fun handleRedirect(conn: HttpURLConnection, currentUri: URI): StepResult {
        val loc = conn.getHeaderField("Location")
        conn.disconnect()
        if (loc.isNullOrBlank()) {
            return StepResult.Done(
                RemoteFetchResult.Failure(IndexFailure.Malformed("Redirect missing Location header")),
            )
        }
        val nextUri = try {
            currentUri.resolve(loc)
        } catch (_: Exception) {
            return StepResult.Done(
                RemoteFetchResult.Failure(IndexFailure.Malformed("Invalid redirect URI: $loc")),
            )
        }
        return when {
            nextUri.scheme?.lowercase() != "https" ->
                StepResult.Done(RemoteFetchResult.Failure(IndexFailure.InsecureSource))
            !nextUri.userInfo.isNullOrBlank() ->
                StepResult.Done(RemoteFetchResult.Failure(IndexFailure.InsecureSource))
            else -> StepResult.Redirect(nextUri)
        }
    }

    private fun handleSuccess(conn: HttpURLConnection): RemoteFetchResult {
        try {
            val maxBytes = PluginPackagePolicy.MANIFEST_MAX_BYTES
            val reportedLength = conn.contentLengthLong
            if (reportedLength > maxBytes) {
                return RemoteFetchResult.Failure(
                    IndexFailure.Malformed("index exceeds $maxBytes bytes"),
                )
            }
            val text = readBoundedUtf8(conn.inputStream, maxBytes)
                ?: return RemoteFetchResult.Failure(
                    IndexFailure.Malformed("index exceeds $maxBytes bytes"),
                )

            val doc = try {
                json.decodeFromString(MarketplaceIndexDocument.serializer(), text)
            } catch (e: Exception) {
                return RemoteFetchResult.Failure(IndexFailure.Malformed("Invalid JSON: ${e.message}"))
            }

            if (doc.schemaVersion != 1) {
                return RemoteFetchResult.Failure(
                    IndexFailure.Malformed("Unsupported schemaVersion: ${doc.schemaVersion}"),
                )
            }

            return RemoteFetchResult.Success(doc)
        } finally {
            conn.disconnect()
        }
    }

    private fun readBoundedUtf8(input: InputStream, maxBytes: Long): String? {
        val buffer = ByteArray(PluginPackagePolicy.TRANSFER_CHUNK_BYTES.toInt())
        val baos = ByteArrayOutputStream()
        var totalBytes = 0L
        input.use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                totalBytes += read
                if (totalBytes > maxBytes) {
                    return null
                }
                baos.write(buffer, 0, read)
            }
        }
        return baos.toString(Charsets.UTF_8)
    }

    private fun cacheSuccess(sourceUrl: String, doc: MarketplaceIndexDocument, observedAt: Long) {
        try {
            Files.createDirectories(cacheDir)
            val cacheFile = cacheFileFor(sourceUrl)
            val record = CachedIndexRecord(observedAtEpochMs = observedAt, document = doc)
            val text = json.encodeToString(CachedIndexRecord.serializer(), record)
            atomicWrite(cacheFile, text)
        } catch (_: Exception) {
            // Caching failure must not fail the fetch result
        }
    }

    private fun readCached(sourceUrl: String): CachedIndexRecord? {
        val cacheFile = cacheFileFor(sourceUrl)
        if (!Files.exists(cacheFile) || !Files.isRegularFile(cacheFile)) return null
        return try {
            val text = Files.readString(cacheFile)
            json.decodeFromString(CachedIndexRecord.serializer(), text)
        } catch (_: Exception) {
            null
        }
    }

    private fun cacheFileFor(sourceUrl: String): Path = cacheDir.resolve("${sha256(sourceUrl)}.json")

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private sealed interface RemoteFetchResult {
        data class Success(val doc: MarketplaceIndexDocument) : RemoteFetchResult
        data class Failure(val reason: IndexFailure) : RemoteFetchResult
    }

    private sealed interface StepResult {
        data class Redirect(val nextUri: URI) : StepResult
        data class Done(val result: RemoteFetchResult) : StepResult
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        private const val CONNECT_TIMEOUT_MS = 15000
        private const val READ_TIMEOUT_MS = 30000
        private val REDIRECT_RANGE = 300..399
        private val SUCCESS_RANGE = 200..299
    }
}
