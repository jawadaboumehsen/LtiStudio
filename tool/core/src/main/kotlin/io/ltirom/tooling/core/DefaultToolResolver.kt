package io.ltirom.tooling.core

import java.io.File

public class DefaultToolResolver(
    private val toolsBinDir: File,
    private val expectedSha256: Map<ToolId, String> = emptyMap(),
    private val expectedPlatforms: Map<ToolId, Set<String>> = emptyMap(),
    private val allowPathFallback: Boolean = false,
    private val enforceFingerprint: Boolean = false
) {
    public fun resolve(toolId: ToolId): ToolBinary {
        val vendored = File(toolsBinDir, toolId.logicalName)
        if (vendored.isFile && vendored.canExecute()) {
            return verify(toolId, vendored)
        }

        if (!allowPathFallback) {
            throw ToolResolutionException(
                toolId,
                listOf(vendored),
                "Pinned executable for '${toolId.logicalName}' is missing or not executable in ${toolsBinDir.absolutePath}"
            )
        }

        val path = System.getenv("PATH").orEmpty()
        val searched = mutableListOf(vendored)
        for (dir in path.split(':')) {
            if (dir.isBlank()) continue
            val candidate = File(dir, toolId.logicalName)
            searched.add(candidate)
            if (candidate.isFile && candidate.canExecute()) {
                return verify(toolId, candidate)
            }
        }

        throw ToolResolutionException(
            toolId,
            searched,
            "Could not resolve executable for tool '${toolId.logicalName}' in ${toolsBinDir.absolutePath} or PATH"
        )
    }

    private fun verify(toolId: ToolId, file: File): ToolBinary {
        val platforms = expectedPlatforms[toolId]
        if (!platforms.isNullOrEmpty() && PlatformIdentity.CURRENT.key() !in platforms) {
            throw ToolFingerprintException(
                toolId,
                platforms.joinToString(","),
                PlatformIdentity.CURRENT.key(),
                "Pinned executable '${toolId.logicalName}' is not supported on this platform"
            )
        }
        val expected = expectedSha256[toolId]
        if (expected == null && enforceFingerprint) {
            throw ToolFingerprintException(
                toolId,
                "<missing-pin>",
                ToolFingerprint.computeFor(file).sha256,
                "No pinned SHA-256 is registered for '${toolId.logicalName}'"
            )
        }
        if (expected == null) return ToolBinary(toolId, file)
        val actual = ToolFingerprint.computeFor(file).sha256
        if (!actual.equals(expected, ignoreCase = true)) {
            throw ToolFingerprintException(
                toolId,
                expected,
                actual,
                "Pinned executable fingerprint mismatch for '${toolId.logicalName}'"
            )
        }
        return ToolBinary(toolId, file)
    }
}
