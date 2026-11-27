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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BootloopCopilotAnalyzerTest {

    private val analyzer = BootloopCopilotAnalyzer

    @Test
    fun parseSepolicyDenialsGeneratesCleanAospRules() {
        val lines = listOf(
            "type=1400 audit(1710000000.123:45): avc:  denied  { read } for  pid=1234 comm=\"cameraserver\" " +
                "path=\"/dev/v4l-subdev0\" scontext=u:r:cameraserver:s0 tcontext=u:object_r:vendor_video_device:s0 " +
                "tclass=chr_file permissive=0",
            "type=1400 audit(1710000000.124:46): avc:  denied  { open ioctl } for  pid=1234 comm=\"cameraserver\" " +
                "path=\"/dev/v4l-subdev0\" scontext=u:r:cameraserver:s0 tcontext=u:object_r:vendor_video_device:s0 " +
                "tclass=chr_file permissive=0",
        )

        val rules = analyzer.parseSepolicyDenials(lines)
        assertEquals(1, rules.size)

        val rule = rules.first()
        assertEquals("cameraserver", rule.sourceContext)
        assertEquals("vendor_video_device", rule.targetContext)
        assertEquals("chr_file", rule.classType)
        assertTrue(rule.permissions.contains("read"))
        assertTrue(rule.permissions.contains("open"))
        assertTrue(rule.permissions.contains("ioctl"))
        assertEquals("allow cameraserver vendor_video_device:chr_file { read open ioctl };", rule.formattedRule)
    }

    @Test
    fun analyzeDetectsFatalSignal11InSystemServer() {
        val lines = listOf(
            "03-25 12:00:01.100  1000  1000 I SystemServer: Init starting",
            "03-25 12:00:02.345  1000  1000 F libc    : Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), " +
                "fault addr 0x0 in tid 1000 (system_server)",
            "03-25 12:00:02.350   500   500 I DEBUG   : " +
                "*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***",
            "03-25 12:00:02.351   500   500 I DEBUG   : " +
                "pid: 1000, tid: 1000, name: system_server  >>> system_server <<<",
        )

        val diagnoses = analyzer.analyze(CrashSource.LOGCAT, lines)
        val segv = diagnoses.firstOrNull { it.signatureType == CrashSignatureType.SIGSEGV }

        assertNotNull(segv)
        assertEquals(CrashSeverity.CRITICAL, segv.severity)
        assertEquals("system_server", segv.offendingProcess)
        assertTrue(segv.suggestedRemediation.contains("`/data/tombstones/`"))
    }

    @Test
    fun analyzeDetectsWatchdogKillingSystemProcess() {
        val lines = listOf(
            "03-25 12:05:00.000  1000  1050 W Watchdog: *** WATCHDOG KILLING SYSTEM PROCESS: " +
                "Blocked in handler on ActivityManager (ActivityManager)",
            "03-25 12:05:00.001  1000  1050 W Watchdog: " +
                "ActivityManager resolving binder call to android.hardware.camera",
        )

        val diagnoses = analyzer.analyze(CrashSource.LOGCAT, lines)
        val watchdog = diagnoses.firstOrNull { it.signatureType == CrashSignatureType.WATCHDOG_TIMEOUT }

        assertNotNull(watchdog)
        assertEquals(CrashSeverity.CRITICAL, watchdog.severity)
        assertEquals("system_server", watchdog.offendingProcess)
        assertTrue(watchdog.rootCauseDescription.contains("ActivityManager"))
    }

    @Test
    fun analyzeDetectsMissingHalService() {
        val lines = listOf(
            "03-25 12:01:10.123  1000  1000 E ServiceManager: " +
                "Service android.hardware.biometrics.face.IFace/default does not exist",
            "03-25 12:01:10.124  1000  1000 E BiometricsFace: Failed to get face HAL interface",
        )

        val diagnoses = analyzer.analyze(CrashSource.LOGCAT, lines)
        val hal = diagnoses.firstOrNull { it.signatureType == CrashSignatureType.MISSING_HAL_SERVICE }

        assertNotNull(hal)
        assertEquals(CrashSeverity.CRITICAL, hal.severity)
        assertEquals("android.hardware.biometrics.face.IFace/default", hal.offendingProcess)
        assertTrue(hal.suggestedRemediation.contains("manifest.xml"))
    }

    @Test
    fun analyzeDetectsVoldStorageDecryptionFailure() {
        val lines = listOf(
            "03-25 12:02:00.000   300   300 E vold    : Failed to mount /data: Invalid argument",
            "03-25 12:02:00.001   300   300 E vold    : Cryptfs: Error decrypting master key",
        )

        val diagnoses = analyzer.analyze(CrashSource.LOGCAT, lines)
        val vold = diagnoses.firstOrNull { it.signatureType == CrashSignatureType.VOLD_CRYPTO_FAILURE }

        assertNotNull(vold)
        assertEquals(CrashSeverity.CRITICAL, vold.severity)
        assertEquals("vold", vold.offendingProcess)
        assertTrue(vold.suggestedRemediation.contains("fstab"))
    }

    @Test
    fun analyzeReturnsEmptyListOnCleanLogs() {
        val cleanLogs = listOf(
            "03-25 12:00:00.000 I SystemServer: Phase 500 reached",
            "03-25 12:00:01.000 I ActivityManager: System UI ready",
            "03-25 12:00:02.000 I PackageManager: Scanning complete",
        )

        val diagnoses = analyzer.analyze(CrashSource.LOGCAT, cleanLogs)
        assertTrue(diagnoses.isEmpty())
    }
}
