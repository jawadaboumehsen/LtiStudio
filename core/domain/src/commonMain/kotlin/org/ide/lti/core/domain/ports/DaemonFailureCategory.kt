package org.ide.lti.core.domain.ports

/**
 * Diagnostic categorization for execution daemon health/connectivity failures.
 */
public enum class DaemonFailureCategory {
    UNREACHABLE,
    UNHEALTHY_STATUS,
    UNRECOGNIZED_STATUS,
    UNSUPPORTED_JAVA,
    COMPATIBILITY_MODE
}
