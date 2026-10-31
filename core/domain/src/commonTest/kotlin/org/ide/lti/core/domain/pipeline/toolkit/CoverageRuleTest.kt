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

class CoverageRuleTest {
    private val text = """
        # a mod's own app, and its native library
        /system/app/MyApp(/.*)? 0 0 0644 u:object_r:system_file:s0
        /vendor/bin/mytool 0 2000 0755 u:object_r:vendor_file:s0
        /system/broken
        /product/app/x 0 0 999 u:object_r:system_file:s0
    """.trimIndent()

    @Test
    fun aRuleGivesAnUncoveredFileItsOwnerModeAndLabelBeforeItsFoldersLines() {
        val (rules, errors) = CoverageRule.parse(text)
        assertEquals(2, rules.size)
        assertEquals(
            listOf(
                "line 4: '/system/broken' is not <regex> <uid> <gid> <mode> <label>",
                "line 5: '/product/app/x 0 0 999 u:object_r:system_file:s0' is not <regex> <uid> <gid> <mode> <label>",
            ),
            errors,
        )
        // system's sidecars use paths relative to the image root; the rule sees the device path.
        val fs = CoverageFix.fixFsConfig("", "system", listOf("system/app/MyApp/MyApp.apk"), rules = rules)
        assertEquals("system/app/MyApp/MyApp.apk 0 0 0644 capabilities=0x0", fs.lines().first { "MyApp.apk" in it })
        val vendor = CoverageFix.fixFsConfig("", "vendor", listOf("bin/mytool"), rules = rules)
        assertEquals("vendor/bin/mytool 0 2000 0755 capabilities=0x0", vendor.lines().first { "mytool" in it })
        val ctx = CoverageFix.fixFileContexts(
            "/vendor u:object_r:vendor_file:s0\n",
            "vendor",
            listOf("bin/other"),
            rules = rules,
        )
        assertEquals(
            "/vendor/bin/other u:object_r:vendor_file:s0",
            ctx.lines().first { "other" in it },
            "no rule: like its folder",
        )
    }
}
