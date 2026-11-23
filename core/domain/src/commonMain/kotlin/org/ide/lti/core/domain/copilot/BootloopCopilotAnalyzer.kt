/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.core.domain.copilot

/**
 * Intelligent analyzer that inspects raw Android runtime and kernel logs (logcat, dmesg, pstore, tombstones),
 * isolates bootloop and crash signatures, and generates prescriptive remediations and SELinux policies.
 */
class BootloopCopilotAnalyzer {

    companion object {
        // SELinux audit denial regex:
        // type=1400 audit(...): avc:  denied  { read open } for  pid=123 comm="cameraserver" path="/dev/..." scontext=u:r:cameraserver:s0 tcontext=u:object_r:vendor_video_device:s0 tclass=chr_file
        private val AVC_DENIAL_REGEX = Regex(
            """avc:\s+denied\s+\{\s*([^}]+)\s*\}\s+for\s+(?:pid=\d+\s+)?(?:comm="([^"]+)"\s+)?.*scontext=u:(?:r|object_r):([^:]+):[^\s]+\s+tcontext=u:(?:r|object_r):([^:]+):[^\s]+\s+tclass=([^\s]+)""",
            RegexOption.IGNORE_CASE,
        )

        // Native crash signal 11 / 6 regex:
        // Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0 in tid 1234 (system_server)
        private val FATAL_SIGNAL_REGEX = Regex(
            """Fatal signal\s+(\d+)\s*\((SIG[A-Z0-9]+)\)""",
            RegexOption.IGNORE_CASE,
        )

        private val IN_TID_PROC_REGEX = Regex(
            """in tid\s+\d+\s*\(([^)]+)\)""",
            RegexOption.IGNORE_CASE,
        )

        // Tombstone / DEBUG crash header:
        // *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***
        // pid: 1234, tid: 1234, name: surfaceflinger  >>> /system/bin/surfaceflinger <<<
        private val TOMBSTONE_PID_REGEX = Regex(
            """pid:\s+(\d+),\s*tid:\s+\d+,\s*name:\s+([^\s]+)\s+>>>\s+([^<]+)\s+<<<""",
            RegexOption.IGNORE_CASE,
        )

        // Watchdog killing system process
        private val WATCHDOG_REGEX = Regex(
            """Watchdog:\s+\*\*\*\s+WATCHDOG KILLING SYSTEM PROCESS:\s+(.*)""",
            RegexOption.IGNORE_CASE,
        )

        // Missing HAL or service manager failure
        private val MISSING_SERVICE_REGEX = Regex(
            """(?:Service\s+([^\s]+)\s+does not exist|Could not find service\s+([^\s]+)\s+in manifest|Waiting for service\s+([^\s]+))""",
            RegexOption.IGNORE_CASE,
        )

        // Vold or Cryptfs mount/decrypt failure
        private val VOLD_DECRYPT_REGEX = Regex(
            """(?:vold:\s+Failed to mount\s+([^\s:]+)|Cryptfs:\s+(Error decrypting|Cannot mount)|Failed to decrypt\s+([^\s:]+))""",
            RegexOption.IGNORE_CASE,
        )

        // Init service crash or failure
        private val INIT_FAILURE_REGEX = Regex(
            """init:\s+(?:Service\s+'([^']+)'\s+\(pid\s+\d+\)\s+exited with status\s+(\d+)|execv\(\)\s+failed:\s+([^\n]+)|Failed to parse line in\s+([^\s:]+))""",
            RegexOption.IGNORE_CASE,
        )
    }

    /**
     * Analyzes a list of log lines according to the specified [CrashSource].
     * Returns a deduplicated, prioritized list of diagnoses.
     */
    fun analyze(source: CrashSource, lines: List<String>): List<BootloopDiagnosis> {
        if (lines.isEmpty()) return emptyList()

        val results = mutableListOf<BootloopDiagnosis>()

        // 1. Check for SELinux denials and generate sepolicy rules
        val sepolicyRules = parseSepolicyDenials(lines)
        if (sepolicyRules.isNotEmpty()) {
            val sampleEvidence = lines.filter { it.contains("avc:", ignoreCase = true) }.take(5)
            val isEnforcing = lines.any { it.contains("permissive=0", ignoreCase = true) }
            val offendingProcesses = sepolicyRules.map { it.sourceContext }.distinct()

            results.add(
                BootloopDiagnosis(
                    id = "selinux-denials-${sepolicyRules.size}",
                    source = source,
                    signatureType = CrashSignatureType.SELINUX_DENIAL,
                    severity = if (isEnforcing) CrashSeverity.CRITICAL else CrashSeverity.WARNING,
                    title = "SELinux Policy Denials Detected (${sepolicyRules.size} rules required)",
                    offendingProcess = offendingProcesses.joinToString(", "),
                    rootCauseDescription = "The kernel blocked one or more hardware access operations because the current SELinux security policy lacks explicit permissions.",
                    rawEvidence = sampleEvidence,
                    suggestedRemediation = "Append the generated allow rules to your device's sepolicy files (e.g. `device/<oem>/<device>/sepolicy/vendor/`), or set `androidboot.selinux=permissive` in kernel cmdline during bringup.",
                    generatedSepolicyRules = sepolicyRules,
                    affectedFiles = listOf("sepolicy/vendor/", "file_contexts"),
                ),
            )
        }

        // 2. Scan line by line for critical runtime signatures
        for (i in lines.indices) {
            val line = lines[i]

            // Fatal Signal (SIGSEGV, SIGABRT)
            val signalMatch = FATAL_SIGNAL_REGEX.find(line)
            if (signalMatch != null) {
                val sigName = signalMatch.groupValues[2].uppercase()
                val procMatch = IN_TID_PROC_REGEX.find(line)
                val proc = procMatch?.groupValues?.get(1) ?: run {
                    val following = lines.subList(i, minOf(lines.size, i + 5))
                    following.firstNotNullOfOrNull { TOMBSTONE_PID_REGEX.find(it)?.groupValues?.get(2) }
                }
                val isSegv = sigName.contains("SEGV")

                val evidence = lines.subList(maxOf(0, i - 1), minOf(lines.size, i + 6))
                val signature = if (isSegv) CrashSignatureType.SIGSEGV else CrashSignatureType.SIGABRT

                results.add(
                    BootloopDiagnosis(
                        id = "signal-$sigName-$i",
                        source = source,
                        signatureType = signature,
                        severity = CrashSeverity.CRITICAL,
                        title = "Fatal Native Crash: $sigName${if (proc != null) " in $proc" else ""}",
                        offendingProcess = proc,
                        rootCauseDescription = if (isSegv) {
                            "A native process crashed due to an invalid memory access (SIGSEGV / segmentation fault). Often caused by null pointers, mismatched CPU architecture flags (32-bit vs 64-bit), or binary ABI incompatibility."
                        } else {
                            "A process aborted execution via SIGABRT. Typically caused by a runtime assertion failure or missing JNI symbol."
                        },
                        rawEvidence = evidence,
                        suggestedRemediation = "Inspect the accompanying tombstone log in `/data/tombstones/`. Verify that all proprietary native shared libraries (`.so`) match the target Android release and CPU architecture.",
                        affectedFiles = listOf("vendor/lib64/", "system/lib64/"),
                    ),
                )
            }

            // Watchdog Timeout
            val watchdogMatch = WATCHDOG_REGEX.find(line)
            if (watchdogMatch != null) {
                val details = watchdogMatch.groupValues[1]
                val evidence = lines.subList(maxOf(0, i - 1), minOf(lines.size, i + 4))

                results.add(
                    BootloopDiagnosis(
                        id = "watchdog-$i",
                        source = source,
                        signatureType = CrashSignatureType.WATCHDOG_TIMEOUT,
                        severity = CrashSeverity.CRITICAL,
                        title = "System Server Watchdog Timeout",
                        offendingProcess = "system_server",
                        rootCauseDescription = "The Android System Server Watchdog killed the system because a core thread handler was blocked: $details",
                        rawEvidence = evidence,
                        suggestedRemediation = "A core service is deadlocked or blocking the main thread for over 60 seconds. Check for slow synchronous HAL callbacks or uninitialized hardware binder interfaces.",
                        affectedFiles = listOf("frameworks/base/services/"),
                    ),
                )
            }

            // Missing HAL Service
            val missingServiceMatch = MISSING_SERVICE_REGEX.find(line)
            if (missingServiceMatch != null) {
                val serviceName = listOf(
                    missingServiceMatch.groupValues[1],
                    missingServiceMatch.groupValues[2],
                    missingServiceMatch.groupValues[3],
                ).firstOrNull { it.isNotBlank() } ?: "unknown.service"

                val evidence = lines.subList(maxOf(0, i - 1), minOf(lines.size, i + 3))

                results.add(
                    BootloopDiagnosis(
                        id = "missing-hal-$serviceName",
                        source = source,
                        signatureType = CrashSignatureType.MISSING_HAL_SERVICE,
                        severity = CrashSeverity.CRITICAL,
                        title = "Missing or Unregistered Hardware Service: $serviceName",
                        offendingProcess = serviceName,
                        rootCauseDescription = "The framework tried to bind to a hardware service ($serviceName) that is not running or not declared in the device manifest.",
                        rawEvidence = evidence,
                        suggestedRemediation = "Verify that the vendor service binary is included in the ROM image and that its interface is declared in `vendor/etc/vintf/manifest.xml`.",
                        affectedFiles = listOf("vendor/etc/vintf/manifest.xml", "device.mk"),
                    ),
                )
            }

            // Vold / Decryption Failure
            val voldMatch = VOLD_DECRYPT_REGEX.find(line)
            if (voldMatch != null) {
                val evidence = lines.subList(maxOf(0, i - 1), minOf(lines.size, i + 4))

                results.add(
                    BootloopDiagnosis(
                        id = "vold-crypto-$i",
                        source = source,
                        signatureType = CrashSignatureType.VOLD_CRYPTO_FAILURE,
                        severity = CrashSeverity.CRITICAL,
                        title = "Storage Decryption / Mount Failure on /data",
                        offendingProcess = "vold",
                        rootCauseDescription = "The volume daemon (`vold`) failed to decrypt or mount the userdata partition.",
                        rawEvidence = evidence,
                        suggestedRemediation = "Check your device's `fstab` encryption flags. If migrating from older Android releases or testing non-encrypted builds, verify if `fileencryption=` or forced encryption is required. Format `/data` with metadata encryption if required by kernel.",
                        affectedFiles = listOf("vendor/etc/fstab.*"),
                    ),
                )
            }

            // Init Failure
            val initMatch = INIT_FAILURE_REGEX.find(line)
            if (initMatch != null) {
                val serviceName = initMatch.groupValues[1].ifBlank { null }
                val evidence = lines.subList(maxOf(0, i - 1), minOf(lines.size, i + 3))

                results.add(
                    BootloopDiagnosis(
                        id = "init-failure-$i",
                        source = source,
                        signatureType = CrashSignatureType.INIT_FAILURE,
                        severity = CrashSeverity.WARNING,
                        title = "Early Init Daemon Failure: ${serviceName ?: "init.rc syntax error"}",
                        offendingProcess = serviceName ?: "init",
                        rootCauseDescription = "An early boot daemon failed to start or exited unexpectedly during `init.rc` execution.",
                        rawEvidence = evidence,
                        suggestedRemediation = "Check the executable permissions and dynamic linker dependencies of the failing binary. Ensure required `/dev/` or `/sys/` nodes exist before service start.",
                        affectedFiles = listOf("vendor/etc/init/"),
                    ),
                )
            }
        }

        // Deduplicate diagnoses by title + signatureType
        return results.distinctBy { "${it.signatureType}:${it.title}" }
            .sortedBy { if (it.severity == CrashSeverity.CRITICAL) 0 else 1 }
    }

    /**
     * Parses all SELinux `avc: denied` denial lines in [lines] and converts them into structured [SepolicyRule] objects.
     */
    fun parseSepolicyDenials(lines: List<String>): List<SepolicyRule> {
        val rules = mutableMapOf<String, SepolicyRule>()

        for (line in lines) {
            val match = AVC_DENIAL_REGEX.find(line) ?: continue
            val permsRaw = match.groupValues[1].trim()
            val scontext = match.groupValues[3].trim()
            val tcontext = match.groupValues[4].trim()
            val tclass = match.groupValues[5].trim()

            val perms = permsRaw.split(Regex("""\s+""")).filter { it.isNotBlank() }
            val key = "$scontext:$tcontext:$tclass"

            val existing = rules[key]
            if (existing != null) {
                val combinedPerms = (existing.permissions + perms).distinct()
                rules[key] = existing.copy(permissions = combinedPerms)
            } else {
                rules[key] = SepolicyRule(
                    sourceContext = scontext,
                    targetContext = tcontext,
                    classType = tclass,
                    permissions = perms,
                )
            }
        }

        return rules.values.toList()
    }
}
