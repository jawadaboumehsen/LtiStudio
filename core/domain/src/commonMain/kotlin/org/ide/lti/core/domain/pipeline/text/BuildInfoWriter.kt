package org.ide.lti.core.domain.pipeline.text

public object BuildInfoWriter {

    public data class BuildInfoParams(
        val device: String,
        val version: String,
        val timestamp: Long,
        val incremental: String,
        val securityPatch: String
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
