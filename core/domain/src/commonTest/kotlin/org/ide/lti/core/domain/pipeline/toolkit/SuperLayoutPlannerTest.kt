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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuperLayoutPlannerTest {

    @Test
    fun alignUpCalculatesCorrectBlockBoundaries() {
        assertEquals(0L, SuperLayoutPlanner.alignUp(0L, 4096L))
        assertEquals(4096L, SuperLayoutPlanner.alignUp(4096L, 4096L))
        assertEquals(8192L, SuperLayoutPlanner.alignUp(4097L, 4096L))
        assertEquals(1048576L, SuperLayoutPlanner.alignUp(500L, 1048576L))
    }

    @Test
    fun planFitsWhenWithinAllLimits() {
        val groups = listOf(SuperGroup("qti_dynamic_partitions", 3_000_000_000L))
        val partitions = listOf(
            SuperPartition("system", "qti_dynamic_partitions", 1_000_000_000L),
            SuperPartition("vendor", "qti_dynamic_partitions", 500_000_000L),
        )

        val layout = SuperLayoutPlanner.plan(
            superSize = 4_000_000_000L,
            groups = groups,
            partitions = partitions,
            reserve = 10_000_000L,
            alignment = 4096L,
        )

        assertTrue(layout.fits)
        assertEquals(0L, layout.shortBy)
        assertNull(layout.reason)
        assertTrue(layout.freeBytes > 2_000_000_000L)
        assertEquals(1, layout.groups.size)
        assertEquals(2, layout.partitions.size)
    }

    @Test
    fun planReportsShortByWhenSuperCapacityExceeded() {
        val groups = listOf(SuperGroup("main", 5_000_000_000L))
        val partitions = listOf(
            SuperPartition("system", "main", 2_000_000_000L),
            SuperPartition("vendor", "main", 1_500_000_000L),
        )

        // Super is 3GB, required is 3.5GB
        val layout = SuperLayoutPlanner.plan(
            superSize = 3_000_000_000L,
            groups = groups,
            partitions = partitions,
            reserve = 0L,
            alignment = 4096L,
        )

        assertFalse(layout.fits)
        assertEquals(0L, layout.freeBytes)
        assertTrue(layout.shortBy >= 500_000_000L)
        assertNotNull(layout.reason)
        assertTrue("short by" in layout.reason.orEmpty())
    }

    @Test
    fun planReportsShortByWhenGroupLimitExceeded() {
        // Super is 10GB (plenty of room), but group max is only 1GB
        val groups = listOf(SuperGroup("small_group", 1_000_000_000L))
        val partitions = listOf(
            SuperPartition("system", "small_group", 1_200_000_000L),
        )

        val layout = SuperLayoutPlanner.plan(
            superSize = 10_000_000_000L,
            groups = groups,
            partitions = partitions,
            reserve = 0L,
            alignment = 4096L,
        )

        assertFalse(layout.fits)
        assertTrue(layout.shortBy >= 200_000_000L)
        assertNotNull(layout.reason)
        assertTrue("small_group" in layout.reason.orEmpty())
    }

    @Test
    fun planIncorporatesReserveAndMetadataOverhead() {
        val groups = listOf(SuperGroup("main", 1_000_000_000L))
        val partitions = listOf(SuperPartition("system", "main", 100_000_000L))

        val layout = SuperLayoutPlanner.plan(
            superSize = 200_000_000L,
            groups = groups,
            partitions = partitions,
            reserve = 50_000_000L,
            alignment = 4096L,
            metadataOverhead = 1_000_000L,
        )

        assertTrue(layout.fits)
        // 200MB - (100MB + 50MB + 1MB) = 49MB (exact minus alignment)
        assertTrue(layout.freeBytes in 48_000_000L..49_000_000L)
    }

    @Test
    fun superLayoutSerializationRoundTrip() {
        val layout = SuperLayout(
            superSize = 4_000_000_000L,
            groups = listOf(SuperGroup("g1", 2_000_000_000L)),
            partitions = listOf(SuperPartition("p1", "g1", 1_000_000L)),
            reserve = 1000L,
            alignment = 4096L,
            freeBytes = 3_000_000_000L,
            shortBy = 0L,
            fits = true,
        )

        val encoded = SuperLayout.encode(layout)
        val decoded = SuperLayout.decode(encoded)
        assertEquals(layout, decoded)
    }

    @Test
    fun superGroupAndPartitionValidation() {
        assertFailsWith<IllegalArgumentException> { SuperGroup("", 100L) }
        assertFailsWith<IllegalArgumentException> { SuperGroup("name", -1L) }
        assertFailsWith<IllegalArgumentException> { SuperPartition("", "group", 100L) }
        assertFailsWith<IllegalArgumentException> { SuperPartition("name", "", 100L) }
        assertFailsWith<IllegalArgumentException> { SuperPartition("name", "group", -1L) }
    }
}
