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
import kotlin.test.assertNull

class KernelFactsTest {
    @Test
    fun readsWhatThePq84p01KernelCanMount() {
        // The kernel script's output for PQ84P01's stock boot.img (trimmed).
        val facts = KernelFacts.parse(
            """
            version 6.6.92-android15-8-g3637f4904cf5-ab13944661-4k
            CONFIG_RD_GZIP=y
            CONFIG_RD_LZ4=y
            CONFIG_RD_ZSTD=y
            CONFIG_EROFS_FS=y
            CONFIG_EROFS_FS_XATTR=y
            CONFIG_EROFS_FS_ZIP=y
            CONFIG_DM_VERITY=y
            CONFIG_DM_VERITY_FEC=y
            CONFIG_FS_VERITY=y
            """.trimIndent(),
        )!!
        assertEquals("6.6.92-android15-8-g3637f4904cf5-ab13944661-4k", facts.version)
        assertEquals(listOf("lz4"), facts.erofsCompressions)
        assertEquals(listOf("gzip", "lz4", "zstd"), facts.ramdiskFormats)
        assertEquals(true, facts.dmVerity)
        assertEquals(true, facts.dmVerityFec)
        assertEquals(true, facts.fsVerity)
    }

    @Test
    fun withoutAnEmbeddedConfigNothingIsClaimed() {
        val facts = KernelFacts.parse("version 5.10.1\nnoconfig")!!
        assertEquals("5.10.1", facts.version)
        assertNull(facts.erofsCompressions)
        assertNull(facts.dmVerity)
        assertNull(KernelFacts.parse("nokernel"))
    }
}
