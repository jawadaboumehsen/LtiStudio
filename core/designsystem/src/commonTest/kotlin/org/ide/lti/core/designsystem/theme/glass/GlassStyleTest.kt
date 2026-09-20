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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlassStyleTest {

    private fun baseline() = GlassStyle(surface = Color.Black)

    @Test
    fun defaultsAreValid() {
        val style = baseline()
        assertTrue(style.vibrancy)
        assertEquals(0f, style.grain)
        assertEquals(1f, style.inputScale)
        assertEquals(Rim.None, style.rim)
    }

    @Test
    fun rejectsOutOfRangeGrain() {
        assertFailsWith<IllegalArgumentException> { baseline().copy(grain = 1.5f) }
        assertFailsWith<IllegalArgumentException> { baseline().copy(grain = -0.1f) }
    }

    @Test
    fun rejectsNegativeBorderWidth() {
        assertFailsWith<IllegalArgumentException> { baseline().copy(borderWidth = (-1).dp) }
    }

    @Test
    fun rejectsOutOfRangeInputScale() {
        assertFailsWith<IllegalArgumentException> { baseline().copy(inputScale = 0f) }
        assertFailsWith<IllegalArgumentException> { baseline().copy(inputScale = 1.1f) }
    }

    @Test
    fun thenAppliesCopyBasedOverrides() {
        val style = baseline().then { copy(grain = 0.2f, vibrancy = false) }
        assertEquals(0.2f, style.grain)
        assertFalse(style.vibrancy)
        // The base style is untouched - `then` is copy-based, not mutating.
        assertTrue(baseline().vibrancy)
    }
}
