/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.text

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.ide.lti.core.domain.pipeline.toolkit.F2fsImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SidecarCapabilitiesTest {
    // SidecarNormalizer.LABEL_DUMP of PQ84P01's vendor.img (erofsfuse), cut to two daemons and their folder.
    private val dump = """
        # file: /m/bin
        security.selinux="u:object_r:vendor_file:s0"

        # file: /m/bin/cnd
        security.selinux="u:object_r:vendor_cnd_exec:s0"

        # file: /m/bin/imsdaemon
        security.selinux="u:object_r:vendor_ims_service_exec:s0"

        # file: /m/bin/cnd
        security.capability=0x0100000200140000000000001000000000000000

        # file: /m/bin/imsdaemon
        security.capability=0x0100000200040000000000001800000000000000
    """.trimIndent()

    @Test
    fun stockCapabilitiesAreKeptInFsConfig() {
        val stat = "0 2000 751 /m/bin\n1000 1000 755 /m/bin/cnd\n1001 1001 755 /m/bin/imsdaemon"
        val sidecars = SidecarNormalizer.fromDumps("vendor", "/m", stat, dump)
        // NET_BIND_SERVICE | NET_ADMIN | BLOCK_SUSPEND, and NET_BIND_SERVICE | WAKE_ALARM | BLOCK_SUSPEND.
        assertTrue("vendor/bin/cnd 1000 1000 755 capabilities=0x1000001400" in sidecars.fsConfigContent.lines())
        assertTrue("vendor/bin/imsdaemon 1001 1001 755 capabilities=0x1800000400" in sidecars.fsConfigContent.lines())
        assertTrue("vendor/bin 0 2000 751 capabilities=0x0" in sidecars.fsConfigContent.lines())
    }

    @Test
    fun getfattrOctalEscapesAreDecoded() {
        val escaped = "# file: /m/a\\134b\nsecurity.selinux=\"u:object_r:vendor_file:s0\""
        assertEquals(mapOf("/m/a\\b" to "u:object_r:vendor_file:s0"), SidecarNormalizer.parseGetfattr(escaped))
    }

    @Test
    fun everyRegexCharacterOfAPathIsEscaped() {
        assertEquals("/vendor/a\\(b\\)\\{1\\}\\|\\$\\^\\?\\+", SidecarNormalizer.escapeRegex("/vendor/a(b){1}|$^?+"))
        val label = SidecarNormalizer.escapeRegex("/vendor/lost+found")
        assertTrue(Regex("^$label$").matches("/vendor/lost+found"))
    }

    @Test
    fun f2fsIsRefusedForFilesThatNeedCapabilities() {
        val fsConfig = "vendor/bin/cnd 1000 1000 755 capabilities=0x1000001400\nvendor/bin/x 0 0 755 capabilities=0x0"
        assertNotNull(F2fsImage.capabilityRefusal("vendor", fsConfig)).let { assertTrue("vendor/bin/cnd" in it) }
        // The two AOSP debug runners alone do not stop it.
        assertNull(F2fsImage.capabilityRefusal("system", "system/bin/run-as 0 2000 750 capabilities=0xc0"))
    }

    @Test
    fun manifestTextAndUrlsAreEscaped() {
        val json = OtaManifestWriter.generate(
            datetime = 1L,
            device = "PQ84P01",
            filename = "rom #1?.zip",
            patch = "2026-10-01",
            size = 10L,
            otaBaseUrl = "https://x/",
            version = "1.0 \"beta\"\n",
            incremental = "1",
            sha256 = "ab",
            changelog = "",
        )
        val entry = Json.parseToJsonElement(json).jsonObject.getValue("response").jsonArray.first().jsonObject
        assertEquals("1.0 \"beta\"\n", entry.getValue("version").jsonPrimitive.content)
        assertEquals("https://x/rom%20%231%3F.zip", entry.getValue("urls").jsonArray.first().jsonPrimitive.content)
    }
}
