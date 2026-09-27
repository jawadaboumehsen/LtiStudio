package org.ide.lti.core.domain.ports

/**
 * Diagnostic categorization for toolchain compilation and compatibility failures.
 */
public enum class ToolchainFailureCategory {
    MISSING_BINARIES,
    MISSING_PUBLISHED_TOOLS,
    EROFS_INCOMPATIBLE,
}
