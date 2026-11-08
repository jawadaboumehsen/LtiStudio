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

    /** PQ84P01's boot kernel, the lines its embedded config holds for these facts. */
    @Test
    fun pageSizeF2fsCompressionAndHardeningAreRead() {
        val facts = KernelFacts.parse(
            """
            version 6.6.92-android15-8-g3637f4904cf5-ab13944661-4k
            CONFIG_ARM64_4K_PAGES=y
            CONFIG_ARM64_PTR_AUTH_KERNEL=y
            CONFIG_ARM64_MTE=y
            CONFIG_RANDOMIZE_BASE=y
            CONFIG_STACKPROTECTOR_STRONG=y
            CONFIG_SHADOW_CALL_STACK=y
            CONFIG_CFI_CLANG=y
            CONFIG_F2FS_FS=y
            CONFIG_F2FS_FS_COMPRESSION=y
            CONFIG_F2FS_FS_LZO=y
            CONFIG_F2FS_FS_LZORLE=y
            CONFIG_F2FS_FS_LZ4=y
            CONFIG_F2FS_FS_LZ4HC=y
            CONFIG_F2FS_FS_ZSTD=y
            CONFIG_INIT_STACK_ALL_ZERO=y
            """.trimIndent(),
        )!!
        assertEquals(4, facts.pageSizeKib)
        assertEquals(listOf("lz4", "lz4hc", "lzo", "lzo-rle", "zstd"), facts.f2fsCompressions)
        // No CONFIG_ARM64_BTI_KERNEL in this kernel: branch target identification is not claimed.
        assertEquals(
            listOf(
                "control-flow integrity (CFI)",
                "shadow call stack",
                "address randomisation (KASLR)",
                "strong stack protector",
                "pointer authentication",
                "memory tagging (MTE)",
                "zeroed stack variables",
            ),
            facts.protections,
        )
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
