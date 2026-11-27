package io.ltirom.tooling.core

import java.io.File

data class ProcessEnvironment(
    val cwd: File,
    val extraEnv: Map<String, String> = emptyMap(),
    val allowlistedKeys: Set<String> = DEFAULT_ALLOWLIST
) {
    companion object {
        val DEFAULT_ALLOWLIST: Set<String> = setOf(
            "PATH", "HOME", "USER", "LANG", "LC_ALL", "TERM", "SHELL", "TMPDIR"
        )
    }

    fun applyTo(pb: ProcessBuilder) {
        pb.directory(cwd)
        val env = pb.environment()

        // Capture allowlisted variables from parent environment
        val parentEnv = System.getenv()
        val cleaned = parentEnv.filterKeys { it in allowlistedKeys }.toMutableMap()

        // Enforce deterministic locale and timezone
        cleaned["LC_ALL"] = "C.UTF-8"
        cleaned["LANG"] = "C.UTF-8"
        cleaned["TZ"] = "UTC"

        // Apply extra variables
        cleaned.putAll(extraEnv)

        env.clear()
        env.putAll(cleaned)
    }
}
