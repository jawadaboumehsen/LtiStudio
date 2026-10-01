/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import io.ltirom.tooling.core.remote.WslServerInfo
import org.ide.lti.core.domain.ports.DaemonFailureCategory
import org.ide.lti.core.domain.setup.ToolCapabilities

/**
 * Structured evaluation outcome of the execution daemon's health response.
 */
public data class DaemonHealthEvaluation(
    val isHealthy: Boolean,
    val category: DaemonFailureCategory?,
    val message: String,
    val remediation: String?,
    val technicalDetails: String? = null,
)

/**
 * Authoritative common policy for required tools, versions, execution verification probes,
 * and publication evidence across WslToolchainProvisioner, EnvironmentReadinessAdapter, and ToolPublicationAdapter.
 *
 * Enforces:
 * - "Readiness uses a common authoritative required-tool/version/publication policy;
 *    installation, execution verification and publication are distinct measured facts.
 *    Null daemon health and stale results fail closed." (FR-002)
 * - "Empty tool inventory is not healthy."
 */
public object ToolchainReadinessPolicy {
    /** Minimum supported Java runtime major version on host/daemon. */
    public const val MIN_JAVA_VERSION: Int = 17

    /** Canonical primary health status defined by WslServerInfo in LtiRomServer. */
    public const val CANONICAL_HEALTHY_STATUS: String = "UP"

    /** Backward-compatibility health status accepted if an older server instance is deployed. */
    public const val COMPATIBILITY_HEALTHY_STATUS: String = "HEALTHY"

    private val HEALTHY_STATUSES = setOf(CANONICAL_HEALTHY_STATUS, COMPATIBILITY_HEALTHY_STATUS)
    private val UNHEALTHY_STATUSES = setOf("DOWN", "UNHEALTHY", "DEGRADED", "ERROR", "OUT_OF_SERVICE")

    /**
     * The 10 core native toolchain binaries required for Android dynamic partition and ROM operations.
     * Derived from authoritative [ToolCatalog.REQUIRED_PRODUCT_TOOLS] (FR-028).
     */
    public val REQUIRED_PRODUCT_TOOLS: Set<String> get() = ToolCatalog.REQUIRED_PRODUCT_TOOLS

    /**
     * Host environment utilities and runtime dependencies required for tool execution and packaging.
     */
    public val REQUIRED_HOST_PACKAGES: Set<String> = setOf(
        "curl", "unzip", "zip", "sha256sum", "sha1sum", "rsync", "getfattr", "fusermount3", "brotli", "file",
        "xxd", "truncate", "find", "stat", "cat", "test", "cp", "mv", "rm", "mkdir", "java",
    )

    /**
     * Required capability check for mkfs.erofs: must support --fs-config-file.
     */
    public const val EROFS_REQUIRED_FLAG: String = "--fs-config-file"

    /**
     * Checks whether mkfs.erofs execution output supports the required --fs-config-file capability.
     */
    public fun isErofsCompatible(exitCode: Int, stdout: String): Boolean =
        ToolCapabilities.isAcceptedExitCode("mkfs.erofs", exitCode) &&
            stdout.contains(EROFS_REQUIRED_FLAG)

    /**
     * Validates that Java version string meets [MIN_JAVA_VERSION].
     */
    public fun isJavaVersionSupported(versionStr: String?): Boolean {
        val trimmed = versionStr?.trim() ?: return false
        val match = Regex("""^(\d+)""").find(trimmed)
            ?: Regex("""^1\.(\d+)""").find(trimmed)
        val major = match?.groupValues?.get(1)?.toIntOrNull()
        return major != null && major >= MIN_JAVA_VERSION
    }

    /**
     * Evaluates remote daemon health.
     * Evaluates daemon status BEFORE Java runtime so a DOWN daemon is not mislabeled as Java incompatible.
     * Keeps user-facing message concise and clean; logs/tails live in technicalDetails.
     */
    public fun evaluateDaemonHealth(health: WslServerInfo?, launchErrorTail: String? = null): DaemonHealthEvaluation {
        val details = launchErrorTail?.takeIf { it.isNotBlank() }
        val statusUpper = health?.status?.trim()?.uppercase()
        return when {
            health == null -> DaemonHealthEvaluation(
                isHealthy = false,
                category = DaemonFailureCategory.UNREACHABLE,
                message = "Daemon health check failed: response was null (daemon unreachable)",
                remediation = "Start the LtiRomServer daemon or verify WSL connectivity",
                technicalDetails = details,
            )
            // An explicit unhealthy status (DOWN, DEGRADED) wins over everything else.
            statusUpper in UNHEALTHY_STATUSES -> DaemonHealthEvaluation(
                isHealthy = false,
                category = DaemonFailureCategory.UNHEALTHY_STATUS,
                message = "Daemon reported unhealthy status: ${health.status}",
                remediation = "Check daemon logs and restart the daemon",
                technicalDetails = details,
            )
            statusUpper !in HEALTHY_STATUSES -> DaemonHealthEvaluation(
                isHealthy = false,
                category = DaemonFailureCategory.UNRECOGNIZED_STATUS,
                message = "Daemon reported unrecognized status: ${health.status}",
                remediation = "Verify daemon version and check daemon logs",
                technicalDetails = details,
            )
            // Only a canonical UP (or compatibility HEALTHY) daemon gets its Java version judged.
            !isJavaVersionSupported(health.javaVersion) -> DaemonHealthEvaluation(
                isHealthy = false,
                category = DaemonFailureCategory.UNSUPPORTED_JAVA,
                message = "Unsupported daemon Java runtime version: ${health.javaVersion}. " +
                    "Requires Java >= $MIN_JAVA_VERSION",
                remediation = "Install Java $MIN_JAVA_VERSION+ in WSL distribution",
                technicalDetails = "Reported runtime: ${health.javaVersion}",
            )
            else -> DaemonHealthEvaluation(
                isHealthy = true,
                category = null,
                message = "Daemon running and healthy (${health.status})",
                remediation = null,
                technicalDetails = null,
            )
        }
    }
}
