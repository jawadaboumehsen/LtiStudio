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

class TreeCompareTest {
    // LISTING_SCRIPT lines of PQ84P01's system partition (fw-baseline): stock, then the build.
    private val stock = TreeCompare.parseListing(
        listOf(
            "d\t4096\t1230768000.0000000000\tsystem/app/SecureElement\t",
            "f\t192926\t1230768000.0000000000\tsystem/app/SecureElement/SecureElement.apk\t",
            "f\t11450\t1230768000.0000000000\tsystem/build.prop\t",
            "f\t2048\t1230768000.0000000000\tsystem/etc/boot-image.bprof\t",
            "f\t100\t1230768000.0000000000\tsystem/etc/hosts\t",
            "l\t11\t1230768000.0000000000\tbin\t/system/bin",
        ).joinToString("\n"),
    )
    private val build = TreeCompare.parseListing(
        listOf(
            "d\t4096\t1230768000.0000000000\tsystem/app/SecureElement\t",
            "f\t192926\t1230768000.0000000000\tsystem/app/SecureElement/SecureElement.apk\t",
            "f\t11562\t1792672508.7154567970\tsystem/build.prop\t",
            "f\t100\t1792672508.0000000000\tsystem/etc/hosts\t",
            "f\t5\t1792672508.0000000000\tsystem/bin/lti\t",
            "l\t11\t1230768000.0000000000\tbin\t/system/bin",
        ).joinToString("\n"),
    )

    @Test
    fun onlyFilesOfTheSameSizeAndADifferentTimeAreHashed() {
        assertEquals(listOf("system/etc/hosts"), TreeCompare.hashCandidates(stock, build))
    }

    @Test
    fun theBuildIsComparedWithStockByContentAndSidecars() {
        val stockSidecars = TreeCompare.Sidecars(
            "system",
            "bin 0 0 644 capabilities=0x0\nsystem/build.prop 0 0 600 capabilities=0x0\n" +
                "system/app/SecureElement 0 0 755 capabilities=0x0",
            "/bin u:object_r:rootfs:s0\n/system/build\\.prop u:object_r:system_file:s0",
        )
        val buildSidecars = TreeCompare.Sidecars(
            "system",
            "bin 0 0 644 capabilities=0x0\nsystem/build.prop 0 0 644 capabilities=0x0\n" +
                "system/app/SecureElement 0 0 755 capabilities=0x0",
            "/bin u:object_r:rootfs:s0\n/system/build\\.prop u:object_r:system_file:s0",
        )
        // hosts was rewritten with the same text: same hash, so unchanged.
        val diff = TreeCompare.compare(
            "system",
            stock,
            build,
            stockHashes = mapOf("system/etc/hosts" to "aa"),
            buildHashes = mapOf("system/etc/hosts" to "aa"),
            stockSidecars = stockSidecars,
            buildSidecars = buildSidecars,
        )
        assertEquals(
            listOf(
                TreeChange("system/bin/lti", TreeChangeKind.ADDED),
                TreeChange(
                    "system/build.prop",
                    TreeChangeKind.CONTENT,
                    listOf("11450 -> 11562 bytes", "mode 600 -> 644"),
                ),
                TreeChange("system/etc/boot-image.bprof", TreeChangeKind.REMOVED),
            ),
            diff.changes,
        )
        assertEquals(4, diff.unchanged)
    }

    @Test
    fun aLineDiffKeepsTheCommonLines() {
        val old = listOf("const/4 v0, 0x0", "return v0")
        val new = listOf("const/4 v0, 0x1", "return v0")
        assertEquals(listOf("- const/4 v0, 0x0", "+ const/4 v0, 0x1"), LineDiff.diff(old, new))
        // An inserted call is one line, not every line after it (what a line-by-line comparison would show).
        val hook = "invoke-static {}, Lx;->hook()V"
        assertEquals(listOf("+ $hook"), LineDiff.diff(old, listOf(hook) + old))
        assertEquals(emptyList(), LineDiff.diff(old, old))
        assertNull(LineDiff.diff(List(LineDiff.MAX_LINES + 1) { "nop" }, emptyList()))
    }
}
