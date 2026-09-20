/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme

import androidx.compose.ui.geometry.Offset
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.highlight.HighlightStyle
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Validates standardized Glass Material optical presets, dual-tone physical bevels,
 * and interactive pointer-reactive highlights.
 */
class GlassOpticalParityTest {

    @Test
    fun testGlassMaterialEffectStacksExist() {
        assertNotNull(GlassEffects.UltraThin, "UltraThin material stack must be non-null")
        assertNotNull(GlassEffects.Thin, "Thin material stack must be non-null")
        assertNotNull(GlassEffects.Regular, "Regular material stack must be non-null")
        assertNotNull(GlassEffects.Thick, "Thick material stack must be non-null")
        assertNotNull(GlassEffects.Chrome, "Chrome material stack must be non-null")
    }

    @Test
    fun testGlassBevelHighlightPreset() {
        val bevelHighlight = GlassHighlights.Bevel()
        assertNotNull(bevelHighlight, "GlassHighlights.Bevel() must return a non-null Highlight")
        assertTrue(
            bevelHighlight.style is HighlightStyle.GlassBevel,
            "Bevel highlight must use HighlightStyle.GlassBevel",
        )
    }

    @Test
    fun testPointerLightHighlightStyle() {
        val pointerLight = HighlightStyle.PointerLight(
            pointerPosition = Offset(100f, 50f),
            intensity = 0.8f,
            falloff = 2f,
        )
        assertNotNull(pointerLight, "PointerLight style must be constructible with pointer coordinates")
        val highlight = Highlight(style = pointerLight)
        assertNotNull(highlight, "Highlight wrapping PointerLight must be non-null")
    }

    @Test
    fun testInteractiveHighlightHelper() {
        val highlight = GlassHighlights.interactive(Offset(120f, 60f))
        assertNotNull(highlight, "GlassHighlights.interactive() must return a non-null Highlight")
        assertTrue(
            highlight.style is HighlightStyle.PointerLight,
            "Interactive highlight must use HighlightStyle.PointerLight",
        )
    }
}
