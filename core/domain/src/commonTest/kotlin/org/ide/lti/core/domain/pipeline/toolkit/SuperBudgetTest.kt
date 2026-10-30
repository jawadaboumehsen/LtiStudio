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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuperBudgetTest {
    private val mib = 1024L * 1024

    @Test
    fun aPartitionNotBuiltYetIsEstimatedFromItsStockImage() {
        val budget = SuperBudget.of(
            partitions = listOf("system", "vendor", "odm"),
            filesystems = mapOf("system" to "erofs", "vendor" to "f2fs", "odm" to "erofs"),
            built = mapOf("vendor" to 1918 * mib),
            stock = mapOf("system" to 2600 * mib, "vendor" to 900 * mib),
            groupBytes = 4000 * mib,
        )
        assertEquals(SuperBudget.Row("vendor", "f2fs", 1918 * mib, estimated = false), budget.rows[1])
        assertEquals(SuperBudget.Row("system", "erofs", 2600 * mib, estimated = true), budget.rows[0])
        assertNull(budget.rows[2].bytes, "odm: neither built nor extracted")
        assertEquals(4518 * mib, budget.usedBytes)
        assertFalse(budget.fits)
        assertTrue(budget.estimated)
    }

    @Test
    fun theBuildSaysWhatOverflowsTheGroup() {
        val sizes = mapOf("system" to 4988 * mib, "vendor" to 1918 * mib)
        assertNull(SuperBudget.overflow(sizes, "qti_dynamic_partitions", 8000 * mib))
        assertEquals(
            "SUPER_FULL: the images take 6906 MiB, the super group main holds 6000 MiB (906 MiB over): system 4988 " +
                "MiB, vendor 1918 MiB. Build the large ones as EROFS, compress them more, or remove apps in Debloat.",
            SuperBudget.overflow(sizes, "main", 6000 * mib),
        )
    }
}
