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

class BootFlagDiffTest {

    @Test
    fun computesEmptyDiffWhenFlagsMatch() {
        val diff = BootFlagDiff.compute(oldFlags = 0, newFlags = 0)
        assertFalse(diff.hasChanges)
        assertTrue(diff.changes.isEmpty())
    }

    @Test
    fun computesAddedAndRemovedFlags() {
        // Old: 1 (DISABLE_VERITY), New: 2 (DISABLE_VERIFICATION)
        val diff = BootFlagDiff.compute(oldFlags = 1, newFlags = 2)
        assertTrue(diff.hasChanges)
        assertEquals(listOf(VbmetaFlag.DISABLE_VERIFICATION), diff.changes.filter { it.isAdded }.map { it.flag })
        assertEquals(listOf(VbmetaFlag.DISABLE_VERITY), diff.changes.filter { it.isRemoved }.map { it.flag })
        assertEquals(2, diff.changes.size)
    }

    @Test
    fun togglesAndSetsFlagsCorrectly() {
        val flags0 = 0
        val flags1 = BootFlagDiff.toggleFlag(flags0, VbmetaFlag.DISABLE_VERITY)
        assertEquals(1, flags1)

        val flags2 = BootFlagDiff.toggleFlag(flags1, VbmetaFlag.DISABLE_VERIFICATION)
        assertEquals(3, flags2)

        val flags3 = BootFlagDiff.toggleFlag(flags2, VbmetaFlag.DISABLE_VERITY)
        assertEquals(2, flags3)
    }

    @Test
    fun describesChangesHumanReadably() {
        val diff = BootFlagDiff.compute(oldFlags = 0, newFlags = 3)
        val descriptions = diff.describe()
        assertEquals(2, descriptions.size)
        assertTrue(descriptions.any { it.contains("FLAG_DISABLE_VERITY") && it.contains("Enable") })
        assertTrue(descriptions.any { it.contains("FLAG_DISABLE_VERIFICATION") && it.contains("Enable") })
    }
}
