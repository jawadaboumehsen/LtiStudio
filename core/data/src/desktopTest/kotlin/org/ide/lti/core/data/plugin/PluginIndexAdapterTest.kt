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

import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.plugin.IndexFailure
import org.ide.lti.core.domain.plugin.IndexFetchResult
import org.ide.lti.core.domain.plugin.IndexSource
import org.ide.lti.core.domain.plugin.PluginPackagePolicy
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class PluginIndexAdapterTest {

    private lateinit var tempDir: Path

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("plugin-index-test-")
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private class FakeHttpURLConnection(
        url: URL,
        private val responseCodeToReturn: Int = 200,
        private val responseBodyToReturn: String = "",
        private val responseBytesToReturn: ByteArray? = null,
        private val responseHeaders: Map<String, String> = emptyMap(),
        private val onConnect: (() -> Unit)? = null,
    ) : HttpURLConnection(url) {
        var disconnected: Boolean = false

        override fun connect() {
            connected = true
            onConnect?.invoke()
        }

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int {
            onConnect?.invoke()
            return responseCodeToReturn
        }

        override fun getInputStream(): InputStream {
            if (responseBytesToReturn != null) {
                return ByteArrayInputStream(responseBytesToReturn)
            }
            return ByteArrayInputStream(responseBodyToReturn.toByteArray(Charsets.UTF_8))
        }

        override fun getHeaderField(name: String): String? = responseHeaders[name]

        override fun getContentLengthLong(): Long {
            if (responseBytesToReturn != null) return responseBytesToReturn.size.toLong()
            return responseBodyToReturn.toByteArray(Charsets.UTF_8).size.toLong()
        }
    }

    @Test
    fun testHttpAndUserInfoUrlsAreInsecureSourceWithoutConnecting() = runTest {
        val adapter = PluginIndexAdapter(
            cacheDir = tempDir,
            connectionOpener = { uri ->
                fail("Connection opener should not be called for insecure URL: $uri")
            },
        )

        // Plain HTTP
        val httpResult = adapter.fetch(IndexSource("repo", "http://example.com/index.json"), allowCache = false)
        assertIs<IndexFetchResult.Failed>(httpResult)
        assertIs<IndexFailure.InsecureSource>(httpResult.reason)

        // HTTPS with userinfo credentials
        val userInfoResult = adapter.fetch(
            IndexSource("repo", "https://user:password@example.com/index.json"),
            allowCache = false,
        )
        assertIs<IndexFetchResult.Failed>(userInfoResult)
        assertIs<IndexFailure.InsecureSource>(userInfoResult.reason)
    }

    @Test
    fun testMalformedBadJson() = runTest {
        val adapter = PluginIndexAdapter(
            cacheDir = tempDir,
            connectionOpener = { uri ->
                FakeHttpURLConnection(
                    url = uri.toURL(),
                    responseCodeToReturn = 200,
                    responseBodyToReturn = "{ malformed json",
                )
            },
        )

        val result = adapter.fetch(IndexSource("repo", "https://example.com/index.json"), allowCache = false)
        assertIs<IndexFetchResult.Failed>(result)
        val reason = result.reason
        assertIs<IndexFailure.Malformed>(reason)
        assertTrue(reason.detail.contains("JSON", ignoreCase = true))
    }

    @Test
    fun testMalformedWrongSchemaVersion() = runTest {
        val adapter = PluginIndexAdapter(
            cacheDir = tempDir,
            connectionOpener = { uri ->
                FakeHttpURLConnection(
                    url = uri.toURL(),
                    responseCodeToReturn = 200,
                    responseBodyToReturn = """{"schemaVersion": 2, "entries": []}""",
                )
            },
        )

        val result = adapter.fetch(IndexSource("repo", "https://example.com/index.json"), allowCache = false)
        assertIs<IndexFetchResult.Failed>(result)
        val reason = result.reason
        assertIs<IndexFailure.Malformed>(reason)
        assertTrue(reason.detail.contains("schemaVersion", ignoreCase = true))
    }

    @Test
    fun testOversizeDocumentExceedsManifestMaxBytes() = runTest {
        val oversizeBytes = ByteArray(PluginPackagePolicy.MANIFEST_MAX_BYTES.toInt() + 10) { 0x20 }
        val adapter = PluginIndexAdapter(
            cacheDir = tempDir,
            connectionOpener = { uri ->
                FakeHttpURLConnection(
                    url = uri.toURL(),
                    responseCodeToReturn = 200,
                    responseBytesToReturn = oversizeBytes,
                )
            },
        )

        val result = adapter.fetch(IndexSource("repo", "https://example.com/index.json"), allowCache = false)
        assertIs<IndexFetchResult.Failed>(result)
        val reason = result.reason
        assertIs<IndexFailure.Malformed>(reason)
        assertTrue(reason.detail.contains("index exceeds", ignoreCase = true))
    }

    @Test
    fun testHttpStatusMappings() = runTest {
        fun adapterWithStatus(
            code: Int,
            headers: Map<String, String> = emptyMap(),
            body: String = "",
        ): PluginIndexAdapter = PluginIndexAdapter(
            cacheDir = tempDir,
            connectionOpener = { uri ->
                FakeHttpURLConnection(
                    url = uri.toURL(),
                    responseCodeToReturn = code,
                    responseHeaders = headers,
                    responseBodyToReturn = body,
                )
            },
        )

        // 401 & 403 -> Unauthenticated
        val r401 = adapterWithStatus(401).fetch(IndexSource("r", "https://example.com/index.json"), allowCache = false)
        assertIs<IndexFetchResult.Failed>(r401)
        assertIs<IndexFailure.Unauthenticated>(r401.reason)

        val r403 = adapterWithStatus(403).fetch(IndexSource("r", "https://example.com/index.json"), allowCache = false)
        assertIs<IndexFetchResult.Failed>(r403)
        assertIs<IndexFailure.Unauthenticated>(r403.reason)

        // 429 -> RateLimited with parsed Retry-After
        val r429WithHeader = adapterWithStatus(429, mapOf("Retry-After" to "120"))
            .fetch(IndexSource("r", "https://example.com/index.json"), allowCache = false)
        assertIs<IndexFetchResult.Failed>(r429WithHeader)
        val r429Reason = r429WithHeader.reason
        assertIs<IndexFailure.RateLimited>(r429Reason)
        assertEquals(120L, r429Reason.retryAfterSeconds)

        val r429WithoutHeader = adapterWithStatus(429)
            .fetch(IndexSource("r", "https://example.com/index.json"), allowCache = false)
        assertIs<IndexFetchResult.Failed>(r429WithoutHeader)
        val r429NoHeaderReason = r429WithoutHeader.reason
        assertIs<IndexFailure.RateLimited>(r429NoHeaderReason)
        assertNull(r429NoHeaderReason.retryAfterSeconds)

        // 5xx -> ServerError(code)
        val r500 = adapterWithStatus(500).fetch(IndexSource("r", "https://example.com/index.json"), allowCache = false)
        assertIs<IndexFetchResult.Failed>(r500)
        val r500Reason = r500.reason
        assertIs<IndexFailure.ServerError>(r500Reason)
        assertEquals(500, r500Reason.code)

        val r503 = adapterWithStatus(503).fetch(IndexSource("r", "https://example.com/index.json"), allowCache = false)
        assertIs<IndexFetchResult.Failed>(r503)
        val r503Reason = r503.reason
        assertIs<IndexFailure.ServerError>(r503Reason)
        assertEquals(503, r503Reason.code)

        // 404 -> Malformed
        val r404 = adapterWithStatus(404).fetch(IndexSource("r", "https://example.com/index.json"), allowCache = false)
        assertIs<IndexFetchResult.Failed>(r404)
        assertIs<IndexFailure.Malformed>(r404.reason)
    }

    @Test
    fun testConnectAndUnknownHostFailuresMapToOffline() = runTest {
        val unknownHostAdapter = PluginIndexAdapter(
            cacheDir = tempDir,
            connectionOpener = { throw UnknownHostException("No such host") },
        )
        val unknownHostResult = unknownHostAdapter.fetch(
            IndexSource("r", "https://unknown.host/index.json"),
            allowCache = false,
        )
        assertIs<IndexFetchResult.Failed>(unknownHostResult)
        assertIs<IndexFailure.Offline>(unknownHostResult.reason)

        val connectFailAdapter = PluginIndexAdapter(
            cacheDir = tempDir,
            connectionOpener = { throw ConnectException("Connection refused") },
        )
        val connectFailResult = connectFailAdapter.fetch(
            IndexSource("r", "https://refused.host/index.json"),
            allowCache = false,
        )
        assertIs<IndexFetchResult.Failed>(connectFailResult)
        assertIs<IndexFailure.Offline>(connectFailResult.reason)
    }

    @Test
    fun testCacheRoundTripReturnsCachedWithOriginalObservedAt() = runTest {
        var currentTime = 1000L
        var shouldFailNetwork = false
        var serverErrorCode: Int? = null

        val sampleJson = """
            {
                "schemaVersion": 1,
                "entries": [
                    {
                        "publisher": "org.example",
                        "id": "mod-a",
                        "version": "1.0.0",
                        "sdkApiRange": "1.0..2.0",
                        "targetSummary": "Test mod",
                        "packageUrl": "https://example.com/mod-a.zip",
                        "contentDigest": "abc123digest",
                        "signatureIdentity": null,
                        "revoked": false
                    }
                ]
            }
        """.trimIndent()

        val adapter = PluginIndexAdapter(
            cacheDir = tempDir,
            timeProvider = { currentTime },
            connectionOpener = { uri ->
                if (shouldFailNetwork) {
                    throw UnknownHostException("Network offline")
                }
                val code = serverErrorCode
                if (code != null) {
                    FakeHttpURLConnection(url = uri.toURL(), responseCodeToReturn = code)
                } else {
                    FakeHttpURLConnection(
                        url = uri.toURL(),
                        responseCodeToReturn = 200,
                        responseBodyToReturn = sampleJson,
                    )
                }
            },
        )

        val source = IndexSource("primary", "https://example.com/repo/index.json")

        // 1. Initial successful fetch at epoch 1000
        val fetchResult = adapter.fetch(source, allowCache = false)
        assertIs<IndexFetchResult.Fetched>(fetchResult)
        assertEquals(1000L, fetchResult.observedAtEpochMs)
        assertEquals(1, fetchResult.doc.entries.size)
        assertEquals("mod-a", fetchResult.doc.entries[0].id)

        // 2. Advance clock to epoch 5000, simulate network going offline
        currentTime = 5000L
        shouldFailNetwork = true

        // 2a. Fetch with allowCache = true -> Cached with original observedAt (1000L, NOT 5000L)
        val cachedResult = adapter.fetch(source, allowCache = true)
        assertIs<IndexFetchResult.Cached>(cachedResult)
        assertEquals(1000L, cachedResult.observedAtEpochMs)
        assertEquals(1, cachedResult.doc.entries.size)
        assertEquals("mod-a", cachedResult.doc.entries[0].id)

        // 2b. Fetch with allowCache = false -> Failed(Offline)
        val noCacheResult = adapter.fetch(source, allowCache = false)
        assertIs<IndexFetchResult.Failed>(noCacheResult)
        assertIs<IndexFailure.Offline>(noCacheResult.reason)

        // 3. When server returns 500 error, even with allowCache = true, never return cached!
        shouldFailNetwork = false
        serverErrorCode = 500
        val serverErrorResult = adapter.fetch(source, allowCache = true)
        assertIs<IndexFetchResult.Failed>(serverErrorResult)
        val serverReason = serverErrorResult.reason
        assertIs<IndexFailure.ServerError>(serverReason)
        assertEquals(500, serverReason.code)
    }
}
