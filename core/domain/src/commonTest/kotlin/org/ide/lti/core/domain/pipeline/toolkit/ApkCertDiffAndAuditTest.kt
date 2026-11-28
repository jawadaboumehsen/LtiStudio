/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.toolkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApkCertDiffAndAuditTest {

    @Test
    fun parseReportAndCompareDetectsRotationAndSchemeChanges() {
        val stockRaw = """
            scheme	v2
            scheme	v3
            cert	v2	aaaa1111bbbb2222cccc3333dddd4444	CN=OEM Platform	CN=OEM Root
            cert	v3	aaaa1111bbbb2222cccc3333dddd4444	CN=OEM Platform	CN=OEM Root
        """.trimIndent()
        val modRaw = """
            scheme	v1
            scheme	v2
            cert	v1	ffff9999eeee8888dddd7777cccc6666	CN=TestKey	CN=TestKey
            cert	v2	ffff9999eeee8888dddd7777cccc6666	CN=TestKey	CN=TestKey
        """.trimIndent()

        val stockReport = ApkCertInspector.parseReport(stockRaw)
        val modReport = ApkCertInspector.parseReport(modRaw)
        val diff = ApkCertInspector.diff(stockReport, modReport)

        assertTrue(stockReport.isSigned)
        assertTrue(modReport.isSigned)
        assertTrue(diff.schemesChanged)
        assertTrue(diff.certificatesChanged)
        assertTrue(diff.hasSecurityChange)
        assertTrue(diff.summary.contains("Signing certificate changed"), diff.summary)
    }

    @Test
    fun compareDetectsRemovedSignatureAsSecurityRisk() {
        val signed = ApkCertInspector.parseReport(
            "scheme\tv2\ncert\tv2\taabbccddeeff00112233445566778899\tCN=Plat\tCN=Plat",
        )
        val unsigned = ApkCertInspector.parseReport("")
        val diff = ApkCertInspector.diff(signed, unsigned)

        assertFalse(unsigned.isSigned)
        assertTrue(diff.hasSecurityChange)
        assertTrue(diff.summary.contains("unsigned APK", ignoreCase = true), diff.summary)
    }

    @Test
    fun structuralAuditClassifiesHashesSymlinksPermissionsSelinuxCapsAndApks() {
        val diff = TreeDiff(
            partition = "system",
            unchanged = 10,
            changes = listOf(
                TreeChange(
                    path = "priv-app/SystemUI/SystemUI.apk",
                    kind = TreeChangeKind.CONTENT,
                    details = listOf("100 -> 120 bytes", "label u:object_r:system_file:s0 -> none"),
                ),
                TreeChange(
                    path = "bin/sh",
                    kind = TreeChangeKind.META,
                    details = listOf("link /system/bin/mksh -> /system/bin/bash", "mode 0755 -> 4755"),
                ),
                TreeChange(
                    path = "bin/netutils",
                    kind = TreeChangeKind.META,
                    details = listOf("capabilities none -> cap_net_raw=ep", "owner 0:0 -> 0:2000"),
                ),
                TreeChange(
                    path = "xbin/su",
                    kind = TreeChangeKind.ADDED,
                ),
            ),
        )

        val audit = diff.structuralAudit()
        assertEquals(1, audit.hashDiffCount)
        assertEquals(1, audit.symlinkDiffCount)
        assertEquals(2, audit.ownerOrModeDiffCount)
        assertEquals(1, audit.selinuxDiffCount)
        assertEquals(1, audit.capabilityDiffCount)
        assertEquals(1, audit.apkDiffCount)

        assertTrue(audit.securityFindings.any { it.severity == StructuralRiskSeverity.CRITICAL })
        assertTrue(audit.securityFindings.any { it.path == "bin/sh" && it.severity == StructuralRiskSeverity.WARNING })
        assertTrue(audit.securityFindings.any { it.path == "xbin/su" })
    }
}
