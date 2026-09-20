/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme.glass

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RimTest {

    @Test
    fun defaultsAreValid() {
        val rim = Rim()
        assertEquals(0.75f, rim.specularAlpha)
        assertEquals(0.25f, rim.shadowAlpha)
        assertEquals(90f, rim.angleDegrees)
        assertEquals(1.dp, rim.width)
    }

    @Test
    fun rejectsOutOfRangeSpecularAlpha() {
        assertFailsWith<IllegalArgumentException> { Rim(specularAlpha = 1.5f) }
        assertFailsWith<IllegalArgumentException> { Rim(specularAlpha = -0.1f) }
    }

    @Test
    fun rejectsOutOfRangeShadowAlpha() {
        assertFailsWith<IllegalArgumentException> { Rim(shadowAlpha = 1.1f) }
    }

    @Test
    fun rejectsNegativeWidth() {
        assertFailsWith<IllegalArgumentException> { Rim(width = (-1).dp) }
    }

    @Test
    fun toHighlightCarriesWidthAndHalvesBlurRadius() {
        val highlight = Rim(width = 4.dp).toHighlight()
        assertEquals(4.dp, highlight.width)
        assertEquals(2.dp, highlight.blurRadius)
    }
}
