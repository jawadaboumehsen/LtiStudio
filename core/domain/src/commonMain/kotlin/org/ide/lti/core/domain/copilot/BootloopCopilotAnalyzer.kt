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
 * Intelligent log correlation & crash analysis engine for Android ROM bringup and porters.
 * Correlates Logcat, Kernel dmesg, and Tombstones to pinpoint root causes of bootloops,
 * missing HAL services, and automatically synthesizes valid SELinux `.te` allow rules.
 */
object BootloopCopilotAnalyzer {

    // Regex for SELinux audit denials:
    // audit: type=1400 avc: denied { read open } for pid=123 comm="cameraserver"
    //   path="/dev/video0" scontext=u:r:cameraserver:s0 tcontext=u:object_r:video_device:s0 tclass=chr_file
    private val AVC_REGEX = Regex(
        """avc:\s+denied\s+\{\s*([^}]+)\s*\}\s+for\s+.*?\s+""" +
            """scontext=([^\s]+)\s+tcontext=([^\s]+)\s+tclass=([^\s]+)""",
        RegexOption.IGNORE_CASE,
    )

    // Regex for fatal signals (SIGSEGV, SIGABRT)
    private val FATAL_SIGNAL_REGEX = Regex(
        """Fatal signal (\d+)\s*\((SIG[A-Z]+)\)""",
        RegexOption.IGNORE_CASE,
    )

    // Regex for thread/process identifiers
    private val IN_TID_PROC_REGEX = Regex(
        """in tid \d+ \(([^)]+)\)""",
        RegexOption.IGNORE_CASE,
    )

    // Regex for Tombstone process header
    private val TOMBSTONE_PID_REGEX = Regex(
        """pid: (\d+), tid: \d+, name: ([^\s]+)\s+>>> ([^\s]+) <<<""",
    )

    // Regex for Watchdog timeouts
    private val WATCHDOG_REGEX = Regex(
        """WATCHDOG KILLING SYSTEM PROCESS:\s*(.+)""",
        RegexOption.IGNORE_CASE,
    )

    // Regex for missing hardware services / HALs
    private val MISSING_SERVICE_REGEX = Regex(
        """(?:Waiting for service '([^']+)'|could not find hwservice '([^']+)'|""" +
            """Service (android\.hardware\.[^\s]+) (?:not found|does not exist))""",
        RegexOption.IGNORE_CASE,
    )

    // Regex for vold decryption failure
    private val VOLD_DECRYPT_REGEX = Regex(
        """(?:Failed to mount \/data|vold.*failed to decrypt|fstab.*mount failure)""",
        RegexOption.IGNORE_CASE,
    )

    // Regex for early init failures
    private val INIT_FAILURE_REGEX = Regex(
        """init: (?:Service '([^']+)' .* failed|Control message: Could not find service '([^']+)')""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Analyzes a list of log lines according to the specified [CrashSource].
     * Returns a deduplicated, prioritized list of diagnoses.
     */
    fun analyze(source: CrashSource, lines: List<String>): List<BootloopDiagnosis> {
        if (lines.isEmpty()) return emptyList()

        val results = mutableListOf<BootloopDiagnosis>()
        detectSelinuxDenials(source, lines)?.let { results.add(it) }

        for (i in lines.indices) {
            val line = lines[i]
            detectFatalSignal(source, line, i, lines)?.let { results.add(it) }
            detectWatchdogTimeout(source, line, i, lines)?.let { results.add(it) }
            detectMissingHalService(source, line, i, lines)?.let { results.add(it) }
            detectVoldDecryption(source, line, i, lines)?.let { results.add(it) }
            detectInitFailure(source, line, i, lines)?.let { results.add(it) }
        }

        return results.distinctBy { "${it.signatureType}:${it.title}" }
            .sortedBy { if (it.severity == CrashSeverity.CRITICAL) 0 else 1 }
    }

    private fun detectSelinuxDenials(source: CrashSource, lines: List<String>): BootloopDiagnosis? {
        val sepolicyRules = parseSepolicyDenials(lines)
        if (sepolicyRules.isEmpty()) return null

        val sampleEvidence = lines.filter { it.contains("avc:", ignoreCase = true) }.take(5)
        val isEnforcing = lines.any { it.contains("permissive=0", ignoreCase = true) }
        val offendingProcesses = sepolicyRules.map { it.sourceContext }.distinct()

        return BootloopDiagnosis(
            id = "selinux-denials-${sepolicyRules.size}",
            source = source,
            signatureType = CrashSignatureType.SELINUX_DENIAL,
            severity = if (isEnforcing) CrashSeverity.CRITICAL else CrashSeverity.WARNING,
            title = "SELinux Policy Denials Detected (${sepolicyRules.size} rules required)",
            offendingProcess = offendingProcesses.joinToString(", "),
            rootCauseDescription = "The kernel blocked one or more hardware access operations because the current " +
                "SELinux security policy lacks explicit permissions.",
            rawEvidence = sampleEvidence,
            suggestedRemediation = "Append the generated allow rules to your device's sepolicy files " +
                "(e.g. `device/<oem>/<device>/sepolicy/vendor/`), or set `androidboot.selinux=permissive`.",
            generatedSepolicyRules = sepolicyRules,
            affectedFiles = listOf("sepolicy/vendor/", "file_contexts"),
        )
    }

    private fun detectFatalSignal(
        source: CrashSource,
        line: String,
        index: Int,
        lines: List<String>,
    ): BootloopDiagnosis? {
        val signalMatch = FATAL_SIGNAL_REGEX.find(line) ?: return null
        val sigName = signalMatch.groupValues[2].uppercase()
        val procMatch = IN_TID_PROC_REGEX.find(line)
        val proc = procMatch?.groupValues?.get(1) ?: run {
            val following = lines.subList(index, minOf(lines.size, index + 5))
            following.firstNotNullOfOrNull { TOMBSTONE_PID_REGEX.find(it)?.groupValues?.get(2) }
        }
        val isSegv = sigName.contains("SEGV")
        val evidence = lines.subList(maxOf(0, index - 1), minOf(lines.size, index + 6))
        val signature = if (isSegv) CrashSignatureType.SIGSEGV else CrashSignatureType.SIGABRT

        return BootloopDiagnosis(
            id = "signal-$sigName-$index",
            source = source,
            signatureType = signature,
            severity = CrashSeverity.CRITICAL,
            title = "Fatal Native Crash: $sigName${if (proc != null) " in $proc" else ""}",
            offendingProcess = proc,
            rootCauseDescription = if (isSegv) {
                "A native process crashed due to an invalid memory access (SIGSEGV). Often caused by null pointers " +
                    "or mismatched CPU architecture flags (32-bit vs 64-bit)."
            } else {
                "A process aborted execution via SIGABRT. Typically caused by a runtime assertion failure or missing JNI."
            },
            rawEvidence = evidence,
            suggestedRemediation = "Inspect tombstone log in `/data/tombstones/`. Verify all proprietary shared " +
                "libraries (`.so`) match the target Android release.",
            affectedFiles = listOf("vendor/lib64/", "system/lib64/"),
        )
    }

    private fun detectWatchdogTimeout(
        source: CrashSource,
        line: String,
        index: Int,
        lines: List<String>,
    ): BootloopDiagnosis? {
        val watchdogMatch = WATCHDOG_REGEX.find(line) ?: return null
        val details = watchdogMatch.groupValues[1]
        val evidence = lines.subList(maxOf(0, index - 1), minOf(lines.size, index + 4))

        return BootloopDiagnosis(
            id = "watchdog-$index",
            source = source,
            signatureType = CrashSignatureType.WATCHDOG_TIMEOUT,
            severity = CrashSeverity.CRITICAL,
            title = "System Server Watchdog Timeout",
            offendingProcess = "system_server",
            rootCauseDescription = "The Android System Server Watchdog killed the system because a core thread " +
                "handler was blocked: $details",
            rawEvidence = evidence,
            suggestedRemediation = "A core service is deadlocked or blocking the main thread for over 60 seconds. " +
                "Check for slow synchronous HAL callbacks or uninitialized binder interfaces.",
            affectedFiles = listOf("frameworks/base/services/"),
        )
    }

    private fun detectMissingHalService(
        source: CrashSource,
        line: String,
        index: Int,
        lines: List<String>,
    ): BootloopDiagnosis? {
        val missingServiceMatch = MISSING_SERVICE_REGEX.find(line) ?: return null
        val serviceName = listOf(
            missingServiceMatch.groupValues[1],
            missingServiceMatch.groupValues[2],
            missingServiceMatch.groupValues[3],
        ).firstOrNull { it.isNotBlank() } ?: "unknown.service"
        val evidence = lines.subList(maxOf(0, index - 1), minOf(lines.size, index + 3))

        return BootloopDiagnosis(
            id = "missing-hal-$serviceName",
            source = source,
            signatureType = CrashSignatureType.MISSING_HAL_SERVICE,
            severity = CrashSeverity.CRITICAL,
            title = "Missing or Unregistered Hardware Service: $serviceName",
            offendingProcess = serviceName,
            rootCauseDescription = "The framework tried to bind to a hardware service ($serviceName) that is not " +
                "running or not declared in the device manifest.",
            rawEvidence = evidence,
            suggestedRemediation = "Verify that the vendor service binary is included in the ROM image and that its " +
                "interface is declared in `vendor/etc/vintf/manifest.xml`.",
            affectedFiles = listOf("vendor/etc/vintf/manifest.xml", "device.mk"),
        )
    }

    private fun detectVoldDecryption(
        source: CrashSource,
        line: String,
        index: Int,
        lines: List<String>,
    ): BootloopDiagnosis? {
        if (!VOLD_DECRYPT_REGEX.containsMatchIn(line)) return null
        val evidence = lines.subList(maxOf(0, index - 1), minOf(lines.size, index + 4))

        return BootloopDiagnosis(
            id = "vold-crypto-$index",
            source = source,
            signatureType = CrashSignatureType.VOLD_CRYPTO_FAILURE,
            severity = CrashSeverity.CRITICAL,
            title = "Storage Decryption / Mount Failure on /data",
            offendingProcess = "vold",
            rootCauseDescription = "The volume daemon (`vold`) failed to decrypt or mount the userdata partition.",
            rawEvidence = evidence,
            suggestedRemediation = "Check your device's `fstab` encryption flags. Verify if forced encryption is " +
                "required and format `/data` with metadata encryption if required by kernel.",
            affectedFiles = listOf("vendor/etc/fstab.*"),
        )
    }

    private fun detectInitFailure(
        source: CrashSource,
        line: String,
        index: Int,
        lines: List<String>,
    ): BootloopDiagnosis? {
        val initMatch = INIT_FAILURE_REGEX.find(line) ?: return null
        val serviceName = initMatch.groupValues[1].ifBlank { null }
        val evidence = lines.subList(maxOf(0, index - 1), minOf(lines.size, index + 3))

        return BootloopDiagnosis(
            id = "init-failure-$index",
            source = source,
            signatureType = CrashSignatureType.INIT_FAILURE,
            severity = CrashSeverity.WARNING,
            title = "Early Init Daemon Failure: ${serviceName ?: "init.rc syntax error"}",
            offendingProcess = serviceName ?: "init",
            rootCauseDescription = "An early boot daemon failed to start or exited unexpectedly during `init.rc`.",
            rawEvidence = evidence,
            suggestedRemediation = "Check the executable permissions and dynamic linker dependencies of the failing " +
                "binary. Ensure required `/dev/` or `/sys/` nodes exist before service start.",
            affectedFiles = listOf("vendor/etc/init/"),
        )
    }

    /**
     * Parses all SELinux `avc: denied` denial lines in [lines] and converts them into structured rules.
     */
    fun parseSepolicyDenials(lines: List<String>): List<SepolicyRule> {
        val rules = mutableListOf<SepolicyRule>()

        for (line in lines) {
            val match = AVC_REGEX.find(line) ?: continue
            val deniedPermissionsRaw = match.groupValues[1]
            val scontext = match.groupValues[2]
            val tcontext = match.groupValues[3]
            val tclass = match.groupValues[4]

            val permissions = deniedPermissionsRaw.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
            val sourceDomain = cleanDomainContext(scontext)
            val targetType = cleanTypeContext(tcontext)

            rules.add(
                SepolicyRule(
                    sourceContext = sourceDomain,
                    targetContext = targetType,
                    classType = tclass.trim(),
                    permissions = permissions,
                ),
            )
        }

        // Merge rules by (source, target, class)
        return rules.groupBy { Triple(it.sourceContext, it.targetContext, it.classType) }
            .map { (key, group) ->
                val allPermissions = group.flatMap { it.permissions }.distinct()
                SepolicyRule(
                    sourceContext = key.first,
                    targetContext = key.second,
                    classType = key.third,
                    permissions = allPermissions,
                )
            }
    }

    private fun cleanDomainContext(context: String): String {
        // Formats: u:r:cameraserver:s0 -> cameraserver
        val parts = context.trim().split(":")
        return if (parts.size >= 3) parts[2] else context
    }

    private fun cleanTypeContext(context: String): String {
        // Formats: u:object_r:video_device:s0 -> video_device
        val parts = context.trim().split(":")
        return if (parts.size >= 3) parts[2] else context
    }
}
