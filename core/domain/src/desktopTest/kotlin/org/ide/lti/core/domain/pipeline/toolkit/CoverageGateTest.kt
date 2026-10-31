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
        val initialFsConfig = """
            system 0 0 0755 capabilities=0x0
            system/bin 0 0 0755 capabilities=0x0
            system/bin/sh 0 2000 0755 capabilities=0x0
        """.trimIndent() + "\n"

        val initialContexts = """
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
}
