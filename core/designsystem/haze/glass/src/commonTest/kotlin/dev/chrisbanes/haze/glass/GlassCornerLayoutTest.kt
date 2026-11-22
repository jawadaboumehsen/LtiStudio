/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassCornerLayoutTest {
    private val size = Size(120f, 80f)
    private val density = Density(1f)

    @Test
    fun missingOrInvalidShapeFallsBackToDefaultRadii() {
        val expected = GlassDefaults.shape.toCornerRadiiPx(size, density, LayoutDirection.Ltr)
        assertEquals(expected, resolveGlassCornerRadii(null, size, density, LayoutDirection.Ltr))
        val invalid = RoundedCornerShape(CornerSize(-5f))
        assertEquals(expected, resolveGlassCornerRadii(invalid, size, density, LayoutDirection.Ltr))
    }

    @Test
    fun layerBoundsUseSamplingStyleWithoutEvaluatingCorners() {
        val corner = object : CornerSize {
            override fun toPx(shapeSize: Size, density: Density): Float =
                error("Layout must not evaluate drawing geometry")
        }
        val effect = GlassRuntimeEffect()
        effect.style = GlassStyle { shape(RoundedCornerShape(corner)) }
        effect.accessibilitySettings = GlassAccessibilitySettings(reduceTransparency = true, showBorders = true)

        val rect = Rect(0f, 0f, size.width, size.height)
        val bounds = effect.calculateLayerBounds(rect, density)
        val reference = GlassRuntimeEffect().apply { accessibilitySettings = effect.accessibilitySettings }
        assertTrue(bounds.width > rect.width)
        assertEquals(reference.calculateLayerBounds(rect, density), bounds)

        val sampling = resolveGlassSamplingStyle(reference, size, density)
        val drawing = resolveGlassStyle(reference, size, density, LayoutDirection.Ltr)
        assertEquals(drawing.resolvedOptics, sampling.optics)
        assertEquals(drawing.edgeSoftnessPx, sampling.edgeSoftnessPx)
    }
}
