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

import kotlin.test.Test
import kotlin.test.assertEquals

class CompressionGoalTest {
    private fun step(p: String, now: Long, after: Long) =
        CompressionStep(p, TrialOption("lzma", "lzma", false, null), now, after, 10.0, 60.0)

    private val steps = listOf(step("vendor", 1000, 900), step("system", 4000, 3500), step("product", 1800, 1600))

    @Test
    fun theLargestSavingsComeFirstUntilTheShortfallIsCovered() {
        assertEquals(listOf("system"), CompressionGoal.pick(steps, 400).map { it.partition })
        assertEquals(listOf("system", "product"), CompressionGoal.pick(steps, 600).map { it.partition })
        // More than every partition can save: all of them, the rest stays short.
        assertEquals(3, CompressionGoal.pick(steps, 10_000).size)
        assertEquals(emptyList(), CompressionGoal.pick(steps, 0))
    }
}
