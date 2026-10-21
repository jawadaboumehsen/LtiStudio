/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.debloat

import org.ide.lti.core.domain.debloat.DebloatCandidate
import org.ide.lti.core.domain.debloat.DebloatDecision
import org.ide.lti.core.domain.debloat.DebloatResolvedRemoval
import org.ide.lti.core.domain.debloat.RemovalReason
import kotlin.test.Test
import kotlin.test.assertEquals

class InventoryWithRemovedTest {
    @Test
    fun appsTheLastBuildRemovedStayInTheInventory() {
        val tree = listOf(DebloatCandidate("product", "app/Drive", 1, null))
        val removed =
            DebloatResolvedRemoval("product", "app/YouTube", 2, null, RemovalReason.entries.first(), emptyList())
        val inventory = DebloatViewModel.withRemoved(tree, DebloatDecision(listOf(removed), emptyList(), 2))
        assertEquals(listOf("product:app/Drive", "product:app/YouTube"), inventory.map(DebloatViewModel::selectorOf))
        assertEquals(tree, DebloatViewModel.withRemoved(tree, null))
    }
}
