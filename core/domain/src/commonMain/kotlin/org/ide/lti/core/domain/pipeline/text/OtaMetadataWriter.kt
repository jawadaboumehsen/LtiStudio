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

public object OtaMetadataWriter {

    public data class OtaMetadataParams(
        val postBuild: String,
        val postBuildIncremental: String,
        val postSdkLevel: String = "35",
        val postSecurityPatchLevel: String,
        val postTimestamp: Long,
        val preDevice: String,
    )

    public fun generate(params: OtaMetadataParams): String {
        return buildString {
            appendLine("ota-type=BLOCK")
            appendLine("post-build=${params.postBuild}")
            appendLine("post-build-incremental=${params.postBuildIncremental}")
            appendLine("post-sdk-level=${params.postSdkLevel}")
            appendLine("post-security-patch-level=${params.postSecurityPatchLevel}")
            appendLine("post-timestamp=${params.postTimestamp}")
            appendLine("pre-device=${params.preDevice}")
        }
    }
}
