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

class CoverageGateTest {

    @Test
    fun coverageGateDetectsMissingAndCoverageFixHeals() {
        // As PQ84P01's: system's root line has an empty path.
        val initialFsConfig = " 0 0 755 capabilities=0x0\n" + """
            system 0 0 0755 capabilities=0x0
            system/bin 0 0 0755 capabilities=0x0
            system/bin/sh 0 2000 0755 capabilities=0x0
        """.trimIndent() + "\n"

        val initialContexts = """
            / u:object_r:rootfs:s0
            /system(/.*)? u:object_r:system_file:s0
            /system/bin/sh u:object_r:shell_exec:s0
        """.trimIndent() + "\n"

        // work/system is the system-as-root image: its paths start with system/, as fs_config's do.
        val paths = listOf(
            "system/bin/sh",
            "system/bin/app_process64",
            "system/priv-app/Settings/Settings.apk",
        )

        val result = CoverageGate.check(
            partition = "system",
            paths = paths,
            fsConfigText = initialFsConfig,
            fileContextsText = initialContexts,
        )

        assertFalse(result.isClean)
        val missing = listOf("system/bin/app_process64", "system/priv-app/Settings/Settings.apk")
        assertEquals(missing, result.uncoveredFsConfig)
        assertEquals(missing, result.uncoveredContexts)

        // Fix through CoverageFix
        val fixedFsConfig = CoverageFix.fixFsConfig(
            fsConfigText = initialFsConfig,
            partition = "system",
            uncoveredPaths = result.uncoveredFsConfig,
            isDirectory = { false },
        )

        val fixedContexts = CoverageFix.fixFileContexts(
            fileContextsText = initialContexts,
            partition = "system",
            uncoveredPaths = result.uncoveredContexts,
        )

        // Re-check
        val healedResult = CoverageGate.check(
            partition = "system",
            paths = paths,
            fsConfigText = fixedFsConfig,
            fileContextsText = fixedContexts,
        )

        assertTrue(healedResult.isClean)
        assertTrue(healedResult.uncoveredFsConfig.isEmpty())
        assertTrue(healedResult.uncoveredContexts.isEmpty())

        // Serialization
        val json = CoverageResult.encode(result)
        val decoded = CoverageResult.decode(json)
        assertEquals(result, decoded)
    }

    @Test
    fun aMissingPartitionRootIsReportedAndNeverInvented() {
        // vendor's root is "/" in fs_config and "/vendor" in file_contexts; here both are gone.
        val fs = "vendor/bin 0 2000 755 capabilities=0x0\n"
        val ctx = "/vendor/bin u:object_r:vendor_file:s0\n"
        val result = CoverageGate.check("vendor", listOf("bin"), fs, ctx)
        assertEquals(listOf(CoverageGate.ROOT), result.uncoveredFsConfig)
        assertEquals(listOf(CoverageGate.ROOT), result.uncoveredContexts)
        assertEquals(fs, CoverageFix.fixFsConfig(fs, "vendor", result.uncoveredFsConfig))
        assertEquals(ctx, CoverageFix.fixFileContexts(ctx, "vendor", result.uncoveredContexts))
        val withRoot = CoverageGate.check(
            "vendor",
            listOf("bin"),
            "/ 0 2000 755 capabilities=0x0\n$fs",
            "/vendor u:object_r:vendor_file:s0\n$ctx",
        )
        assertTrue(withRoot.isClean)
    }
}
