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

    /** Width the A/B package's property-file offsets are padded to, so filling them in keeps the zip's layout. */
    public const val PROPERTY_FILES_WIDTH: Int = 384

    public fun generate(params: OtaMetadataParams): String = buildString {
        appendLine("ota-type=BLOCK")
        appendLine("post-build=${params.postBuild}")
        appendLine("post-build-incremental=${params.postBuildIncremental}")
        appendLine("post-sdk-level=${params.postSdkLevel}")
        appendLine("post-security-patch-level=${params.postSecurityPatchLevel}")
        appendLine("post-timestamp=${params.postTimestamp}")
        appendLine("pre-device=${params.preDevice}")
    }

    /**
     * The A/B (payload) package's metadata, keys sorted as AOSP writes them. The two property-file lists are blank
     * here: they hold the members' offsets in the signed zip, which `AbOtaPackage.SCRIPT props` fills in.
     */
    public fun generateAb(params: OtaMetadataParams): String {
        val blank = " ".repeat(PROPERTY_FILES_WIDTH)
        return buildString {
            appendLine("ota-property-files=$blank")
            appendLine("ota-required-cache=0")
            appendLine("ota-streaming-property-files=$blank")
            appendLine("ota-type=AB")
            appendLine("post-build=${params.postBuild}")
            appendLine("post-build-incremental=${params.postBuildIncremental}")
            appendLine("post-sdk-level=${params.postSdkLevel}")
            appendLine("post-security-patch-level=${params.postSecurityPatchLevel}")
            appendLine("post-timestamp=${params.postTimestamp}")
            appendLine("pre-device=${params.preDevice}")
        }
    }
}
