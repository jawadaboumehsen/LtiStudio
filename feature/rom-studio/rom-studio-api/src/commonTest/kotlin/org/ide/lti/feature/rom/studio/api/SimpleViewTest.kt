/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.api

import org.ide.lti.core.model.run.StageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimpleViewTest {
    private val all = CanonicalStageDescriptors.ALL

    @Test
    fun everySimplePaneExistsAndEveryStageOpensOnOne() {
        val ids = all.flatMap { stage -> stage.subobjects.map { it.id.value } }.toSet()
        assertEquals(emptySet(), CanonicalStageDescriptors.SIMPLE - ids)
        all.forEach { stage ->
            assertTrue(stage.subobjects.first().id.value in CanonicalStageDescriptors.SIMPLE, stage.title)
        }
    }

    @Test
    fun simpleHidesAdvancedPanesButKeepsTheOneOpen() {
        val assemble = checkNotNull(CanonicalStageDescriptors.descriptorFor(StageId.WORK_TREE_ASSEMBLY))
        fun ids(simple: Boolean, open: String?) =
            CanonicalStageDescriptors.visible(assemble, simple, open).map { it.id.value }
        assertEquals(listOf("work-trees", "build-properties"), ids(simple = true, open = null))
        // A preflight fix may open the mount table: it shows while open.
        assertEquals(listOf("work-trees", "build-properties", "mount-table"), ids(simple = true, open = "mount-table"))
        assertEquals(assemble.subobjects.size, ids(simple = false, open = null).size)
    }
}
