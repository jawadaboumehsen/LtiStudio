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

public object BuildInfoWriter {

    public data class BuildInfoParams(
        val device: String,
        val version: String,
        val timestamp: Long,
        val incremental: String,
        val securityPatch: String,
    )

    public fun generate(params: BuildInfoParams): String {
        return buildString {
            appendLine("ro.lti.device=${params.device}")
            appendLine("ro.lti.version=${params.version}")
            appendLine("ro.lti.timestamp=${params.timestamp}")
            appendLine("ro.build.version.incremental=${params.incremental}")
            appendLine("ro.build.version.security_patch=${params.securityPatch}")
        }
    }
}
