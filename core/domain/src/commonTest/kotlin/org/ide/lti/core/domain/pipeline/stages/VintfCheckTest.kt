/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.stages

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VintfCheckTest {
    /** What the script prints for a tree shaped like PQ84P01's, with the parts a test changes as parameters. */
    private fun tree(
        mappings: String = "202404.cil\n34.0.cil",
        levels: String = "202404\n8",
        deviceMatrixHal: String = OPTIONAL_HAL,
        frameworkSdk: String = "<version>34</version><version>35</version>",
    ) = """
@@@ vendor/etc/vintf/manifest_sun.xml
<!-- Input: <hal format="aidl"><name>commented.out</name></hal> -->
<manifest version="8.0" type="device" target-level="202404">
    <hal format="aidl"><name>android.hardware.gatekeeper</name><fqname>IGatekeeper/default</fqname></hal>
</manifest>
@@@ vendor/etc/vintf/manifest/power.xml
<manifest version="8.0" type="device"><hal format="aidl"><name>android.hardware.power</name></hal></manifest>
@@@ vendor/etc/vintf/compatibility_matrix.xml
<compatibility-matrix version="8.0" type="device">
    $deviceMatrixHal
    <system-sdk><version>35</version></system-sdk>
</compatibility-matrix>
@@@ system/system/etc/vintf/manifest.xml
<manifest version="8.0" type="framework">
    <hal format="aidl"><name>android.frameworks.sensorservice</name><fqname>ISensorManager/default</fqname></hal>
    <system-sdk>$frameworkSdk</system-sdk>
</manifest>
@@@ system/system/etc/vintf/compatibility_matrix.202404.xml
<compatibility-matrix version="8.0" type="framework" level="202404">
    <hal format="aidl" optional="false"><name>android.hardware.power</name></hal>
    <hal format="aidl" optional="true"><name>android.hardware.nfc</name></hal>
</compatibility-matrix>
@@@ levels
$levels
@@@ mapping
$mappings
@@@ vendor-sepolicy
202404
    """.trimIndent()

    @Test
    fun aMatchingSystemAndVendorHaveNoFindings() {
        assertEquals(emptyList(), VintfCheck.findings(tree()))
    }

    @Test
    fun aMissingSelinuxMappingIsTheOneThatStopsTheBuild() {
        val found = VintfCheck.findings(tree(mappings = "33.0.cil\n34.0.cil"))
        assertEquals(1, found.size, "$found")
        assertTrue(found[0].startsWith("VINTF_SEPOLICY: vendor's sepolicy is 202404"), found[0])
        assertTrue("33.0, 34.0" in found[0], found[0])
        assertTrue("VINTF_SEPOLICY" !in VintfCheck.NOTES)
    }

    @Test
    fun aMissingLevelSdkOrRequiredHalIsANote() {
        val found = VintfCheck.findings(
            tree(
                levels = "7\n8",
                deviceMatrixHal = """<hal format="aidl"><name>vendor.qti.required</name></hal>""",
                frameworkSdk = "<version>34</version>",
            ),
        )
        assertTrue(found.any { it.startsWith("VINTF_LEVEL: vendor targets FCM level 202404") }, "$found")
        assertTrue(found.any { it.startsWith("VINTF_SYSTEM_SDK: vendor's matrix asks for system SDK 35") }, "$found")
        assertTrue(
            found.any { it == "VINTF_HAL: vendor's matrix requires vendor.qti.required (aidl), $NOT_DECLARED" },
            "$found",
        )
        assertTrue(found.all { it.substringBefore(':') in VintfCheck.NOTES }, "$found")
    }

    private companion object {
        const val OPTIONAL_HAL =
            """<hal format="aidl" optional="true"><name>android.frameworks.sensorservice</name></hal>"""
        const val NOT_DECLARED = "which the system's manifests do not declare"
    }

    @Test
    fun missingEvidenceIsSaid() {
        assertEquals(
            listOf("VINTF_NOT_CHECKED: no device manifest under vendor/etc/vintf or odm/etc/vintf"),
            VintfCheck.findings("@@@ levels\n202404\n@@@ mapping\n@@@ vendor-sepolicy\n"),
        )
    }
}
