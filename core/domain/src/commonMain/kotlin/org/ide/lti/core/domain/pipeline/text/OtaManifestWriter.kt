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

import java.security.MessageDigest

public object OtaManifestWriter {

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
    ): String {
        val cleanBaseUrl = otaBaseUrl.removeSuffix("/")
        val idInput = "$sha256$datetime"
        val md = MessageDigest.getInstance("SHA-256")
        val id = md.digest(idInput.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val safeChangelog = changelog.take(16384)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        return """{
    "response": [
        {
            "datetime": $datetime,
            "device": "$device",
            "filename": "$filename",
            "id": "$id",
            "patch": "$patch",
            "size": $size,
            "urls": [
                "$cleanBaseUrl/$filename"
            ],
            "version": "$version",
            "incremental": "$incremental",
            "sha256": "$sha256",
            "changelog": "$safeChangelog"
        }
    ]
}
"""
    }
}
