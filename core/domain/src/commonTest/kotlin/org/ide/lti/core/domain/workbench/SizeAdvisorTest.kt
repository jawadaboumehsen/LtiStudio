/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workbench

import org.ide.lti.core.model.workspace.BuildSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class SizeAdvisorTest {
    private val gki = "6.6.92-android15-8"

    @Test
    fun theWaysTriedFollowWhatTheBootKernelReads() {
        // PQ84P01's kernel: dedupe (6.6) but only the lz4 decoder; dedupe is already on, so it is not tried twice.
        val labels = SizeAdvisor.options(BuildSettings(), "vendor", gki, listOf("lz4")).map { it.label }
        assertEquals(listOf("as set now", "64 KiB cluster"), labels)
        val old = SizeAdvisor.options(BuildSettings(), "vendor", "5.10.1", listOf("lz4", "lzma")).map { it.label }
        assertEquals(listOf("as set now", "64 KiB cluster", "lzma"), old)
    }

    @Test
    fun theAdviceIsTheFastestThatFitsElseTheSmallest() {
        val now = TrialOption("as set now", "lz4hc,9", true, null)
        val cluster = now.copy(label = "64 KiB cluster", clusterKib = 64)
        // PQ84P01 vendor: 947 MiB deduplicated, 1161 MiB with a 64 KiB cluster and no dedupe.
        val results = listOf(TrialResult(now, 993_001_472, 190.0), TrialResult(cluster, 1_217_396_736, 40.0))
        assertEquals(cluster, SizeAdvisor.recommend(results, room = 2_000_000_000)?.option)
        assertEquals(now, SizeAdvisor.recommend(results, room = 1_000_000_000)?.option)
        assertEquals(now, SizeAdvisor.recommend(results, room = 500_000_000)?.option)
        val applied = cluster.applyTo(BuildSettings(), "vendor")
        assertEquals(64, applied.partitionClusterKib["vendor"])
        assertEquals(true, applied.partitionDedupe["vendor"])
    }

    @Test
    fun theAdviceCountsTheHashtreeTheBuildAdds() {
        // 1 GiB of filesystem: hashtree levels of 8 MiB, 64 KiB and one block, then the footer reserve.
        val gib = 1L shl 30
        assertEquals(gib + 8_388_608 + 65_536 + 4_096 + 65_536, SizeAdvisor.signedBytes(gib))
        val fast = TrialOption("as set now", "lz4hc,9", false, null)
        val small = fast.copy(label = "64 KiB cluster", clusterKib = 64)
        val results = listOf(TrialResult(fast, gib, 10.0), TrialResult(small, gib - 20_000_000, 40.0))
        // The bare fast image fits 1 MiB of room; signed it does not.
        assertEquals(small, SizeAdvisor.recommend(results, room = gib + 1_048_576)?.option)
    }
}
