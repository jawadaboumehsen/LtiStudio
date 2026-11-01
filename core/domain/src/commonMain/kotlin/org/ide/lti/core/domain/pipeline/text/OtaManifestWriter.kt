/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.text

import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder
import java.security.MessageDigest

public object OtaManifestWriter {

    /** The A/B OTA zip offered next to the main package (`"ota"` in the entry). */
    public data class OtaPackage(val filename: String, val size: Long, val sha256: String)

    public fun generate(
        datetime: Long,
        device: String,
        filename: String,
        patch: String,
        size: Long,
        otaBaseUrl: String,
        version: String,
        incremental: String,
        sha256: String,
        changelog: String,
        ota: OtaPackage? = null,
    ): String {
        val cleanBaseUrl = otaBaseUrl.removeSuffix("/")
        val idInput = "$sha256$datetime"
        val md = MessageDigest.getInstance("SHA-256")
        val id = md.digest(idInput.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val safeChangelog = jsonText(changelog.take(16384))
        val otaField = ota?.let {
            """
            "ota": {
                "filename": "${jsonText(it.filename)}",
                "size": ${it.size},
                "sha256": "${jsonText(it.sha256)}",
                "urls": [
                    "${jsonText("$cleanBaseUrl/${urlSegment(it.filename)}")}"
                ]
            },"""
        }.orEmpty()

        return """{
    "response": [
        {
            "datetime": $datetime,
            "device": "${jsonText(device)}",
            "filename": "${jsonText(filename)}",
            "id": "${jsonText(id)}",
            "patch": "${jsonText(patch)}",
            "size": $size,
            "urls": [
                "${jsonText("$cleanBaseUrl/${urlSegment(filename)}")}"
            ],
            "version": "${jsonText(version)}",
            "incremental": "${jsonText(incremental)}",
            "sha256": "${jsonText(sha256)}",$otaField
            "changelog": "$safeChangelog"
        }
    ]
}
"""
    }

    /** [text] escaped for use between JSON quotes (any quote, backslash or control character). */
    public fun jsonText(text: String): String = JsonPrimitive(text).toString().removeSurrounding("\"")

    /** [text] as one URL path segment: `#`, `?`, spaces and `/` cannot change what the URL names. */
    public fun urlSegment(text: String): String = URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20")
}
