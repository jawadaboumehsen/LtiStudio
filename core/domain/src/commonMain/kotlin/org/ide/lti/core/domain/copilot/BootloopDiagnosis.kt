/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.copilot

/**
 * Origin of crash diagnostic logs.
 */
enum class CrashSource {
    LOGCAT,
    DMESG,
    PSTORE,
    TOMBSTONE,
}

/**
 * Severity level of an identified diagnostic issue.
 */
enum class CrashSeverity {
    CRITICAL,
    WARNING,
    INFO,
}

/**
 * High-level signature classification for bootloops and crashes in Android ROMs.
 */
enum class CrashSignatureType {
    SIGSEGV,
    SIGABRT,
    WATCHDOG_TIMEOUT,
    MISSING_HAL_SERVICE,
    SELINUX_DENIAL,
    VOLD_CRYPTO_FAILURE,
    INIT_FAILURE,
    UNKNOWN,
}

/**
 * A machine-generated SELinux allow rule derived from audit denial logs.
 * Formats directly to standard AOSP sepolicy syntax:
 * `allow <sourceContext> <targetContext>:<classType> { <permissions> };`
 */
data class SepolicyRule(
    val sourceContext: String,
    val targetContext: String,
    val classType: String,
    val permissions: List<String>,
) {
    val formattedRule: String
        get() {
            val perms = permissions.distinct().joinToString(" ")
            return "allow $sourceContext $targetContext:$classType { $perms };"
        }
}

/**
 * An actionable diagnostic report identifying a potential root cause of a bootloop or crash.
 */
data class BootloopDiagnosis(
    val id: String,
    val timestampMs: Long = 0L,
    val source: CrashSource,
    val signatureType: CrashSignatureType,
    val severity: CrashSeverity,
    val title: String,
    val offendingProcess: String?,
    val rootCauseDescription: String,
    val rawEvidence: List<String>,
    val suggestedRemediation: String,
    val generatedSepolicyRules: List<SepolicyRule> = emptyList(),
    val affectedFiles: List<String> = emptyList(),
)
