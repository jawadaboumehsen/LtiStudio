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

import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionTest {
    private val all = listOf("system", "vendor", "odm")

    @Test
    fun emptyMeansAllAndNarrowingStartsFromAll() {
        assertEquals(listOf("system", "odm"), toggledSelection(all, all, "vendor"))
        assertEquals(emptyList(), toggledSelection(all, listOf("system", "odm"), "vendor"))
        assertEquals(listOf("odm"), toggledSelection(all, listOf("odm"), "odm"))
    }
}
