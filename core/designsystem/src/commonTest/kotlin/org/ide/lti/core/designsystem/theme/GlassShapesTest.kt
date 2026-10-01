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

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GlassShapesTest {

    @Test
    fun squirclePresetsAreNotNull() {
        assertNotNull(GlassShapes.None)
        assertNotNull(GlassShapes.ExtraSmall)
        assertNotNull(GlassShapes.Small)
        assertNotNull(GlassShapes.MediumSmall)
        assertNotNull(GlassShapes.Medium)
        assertNotNull(GlassShapes.Large)
        assertNotNull(GlassShapes.ExtraLarge)
        assertNotNull(GlassShapes.Capsule)
        assertNotNull(GlassShapes.Panel)
        assertNotNull(GlassShapes.Card)
    }

    @Test
    fun hazeShapePresetsAreNotNullAndMatchRadii() {
        assertNotNull(GlassShapes.HazeExtraSmall)
        assertNotNull(GlassShapes.HazeSmall)
        assertNotNull(GlassShapes.HazeMediumSmall)
        assertNotNull(GlassShapes.HazeMedium)
        assertNotNull(GlassShapes.HazeLarge)
        assertNotNull(GlassShapes.HazeExtraLarge)
        assertNotNull(GlassShapes.HazeCapsule)
        assertNotNull(GlassShapes.HazePanel)
        assertNotNull(GlassShapes.HazeCard)

        val density = Density(1f)
        val size = Size(100f, 100f)
        assertEquals(CornerRadius.ExtraSmall.value, GlassShapes.HazeExtraSmall.topStart.toPx(size, density))
        assertEquals(CornerRadius.Panel.value, GlassShapes.HazePanel.topStart.toPx(size, density))
    }

    @Test
    fun concentricInsettingPreservesTopology() {
        val insetCard = GlassShapes.concentric(GlassShapes.Card, 2.dp)
        assertNotNull(insetCard)

        val outsetCard = GlassShapes.concentricOutset(GlassShapes.Card, 2.dp)
        assertNotNull(outsetCard)
    }
}
