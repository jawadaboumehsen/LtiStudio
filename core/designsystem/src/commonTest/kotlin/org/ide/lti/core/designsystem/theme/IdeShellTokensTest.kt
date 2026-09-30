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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdeShellTokensTest {

    @Test
    fun testLogicalFrameDimensions() {
        assertEquals(44.dp, GlassDimens.TopBarHeight, "Top bar height must be 44 dp")
        assertEquals(150.dp, GlassDimens.PipelineRailWidth, "Pipeline rail width must be 150 dp")
        assertEquals(44.dp, GlassDimens.CompactRailWidth, "Compact rail width must be 44 dp")
        assertEquals(215.dp, GlassDimens.WorkspaceNavigatorWidth, "Workspace navigator width must be 215 dp")
        assertEquals(44.dp, GlassDimens.CompactNavigatorWidth, "Compact navigator width must be 44 dp")
        assertEquals(32.dp, GlassDimens.BreadcrumbHeight, "Breadcrumb bar height must be 32 dp")
        assertEquals(24.dp, GlassDimens.StatusBarHeight, "Bottom status bar height must be 24 dp")

        assertEquals(8.dp, GlassDimens.ContentInset, "Content inset must be 8 dp")
        assertEquals(8.dp, GlassDimens.ContentRadius, "Content radius must be 8 dp")
        assertEquals(36.dp, GlassDimens.PipelineStageItemHeight, "Pipeline stage item height must be 36 dp")
        assertEquals(40.dp, GlassDimens.NavigatorItemHeight, "Navigator item height must be 40 dp")

        assertEquals(1024.dp, GlassDimens.CompactBreakpoint)
        assertEquals(768.dp, GlassDimens.CompactHeightBreakpoint)
        assertEquals(1280.dp, GlassDimens.ReadableContentMaxWidth)
        assertEquals(1.dp, GlassDimens.HairlineBorder)
        assertEquals(6.dp, GlassDimens.CardCornerRadius)
        assertEquals(4.dp, GlassDimens.ControlCornerRadius)
        assertEquals(3.dp, GlassDimens.BadgeCornerRadius)
    }

    @Test
    fun testShapesAndMotionTokens() {
        assertEquals(androidx.compose.ui.graphics.RectangleShape, GlassShapes.ShellFrame)
        assertTrue(GlassShapes.ShellPanel is androidx.compose.foundation.shape.RoundedCornerShape)
        assertTrue(GlassShapes.ShellPill is androidx.compose.foundation.shape.RoundedCornerShape)
        assertTrue(GlassShapes.ShellCard is androidx.compose.foundation.shape.RoundedCornerShape)
        assertTrue(GlassShapes.ShellControl is androidx.compose.foundation.shape.RoundedCornerShape)
        assertTrue(GlassShapes.ShellBadge is androidx.compose.foundation.shape.RoundedCornerShape)

        assertEquals(100, IdeShellMotion.StateTransitionDuration)
        assertEquals(200, IdeShellMotion.DrawerTransitionDuration)
        assertEquals(MotionEasing.Standard, IdeShellMotion.Easing)
    }

    @Test
    fun testDarkThemeWcagContrast() {
        val scheme = DarkColorScheme
        assertContrast(scheme.onSurface, scheme.background, minRatio = 4.5, "Dark onSurface on background")
        assertContrast(scheme.onSurface, scheme.surfaceContainer, minRatio = 4.5, "Dark onSurface on surfaceContainer")
        assertContrast(
            scheme.onSurface,
            scheme.surfaceContainerHigh,
            minRatio = 4.5,
            "Dark onSurface on surfaceContainerHigh",
        )

        assertContrast(
            scheme.onSurfaceVariant,
            scheme.background,
            minRatio = 4.5,
            "Dark onSurfaceVariant on background",
        )
        assertContrast(
            scheme.onSurfaceVariant,
            scheme.surfaceContainer,
            minRatio = 4.5,
            "Dark onSurfaceVariant on surfaceContainer",
        )
        assertContrast(
            scheme.onSurfaceVariant,
            scheme.surfaceContainerHigh,
            minRatio = 4.0,
            "Dark onSurfaceVariant on surfaceContainerHigh",
        )

        assertContrast(scheme.outline, scheme.background, minRatio = 1.3, "Dark outline on background")
        assertContrast(scheme.primary, scheme.surfaceContainer, minRatio = 3.0, "Dark primary on surfaceContainer")
        assertContrast(
            scheme.primary,
            scheme.surfaceContainerHigh,
            minRatio = 2.9,
            "Dark primary on surfaceContainerHigh",
        )
        assertContrast(
            scheme.onPrimaryContainer,
            scheme.primaryContainer,
            minRatio = 4.5,
            "Dark onPrimaryContainer on primaryContainer",
        )
    }

    @Test
    fun testLightThemeWcagContrast() {
        val scheme = LightColorScheme
        assertContrast(scheme.onSurface, scheme.surfaceContainer, minRatio = 4.5, "Light onSurface on surfaceContainer")
        assertContrast(scheme.onSurface, scheme.background, minRatio = 4.5, "Light onSurface on background")
        assertContrast(
            scheme.onSurface,
            scheme.surfaceContainerHigh,
            minRatio = 4.5,
            "Light onSurface on surfaceContainerHigh",
        )

        assertContrast(
            scheme.onSurfaceVariant,
            scheme.surfaceContainer,
            minRatio = 4.5,
            "Light onSurfaceVariant on surfaceContainer",
        )
        assertContrast(
            scheme.onSurfaceVariant,
            scheme.background,
            minRatio = 4.5,
            "Light onSurfaceVariant on background",
        )

        assertContrast(scheme.outline, scheme.background, minRatio = 1.3, "Light outline on background")
        assertContrast(scheme.primary, scheme.surfaceContainer, minRatio = 3.0, "Light primary on surfaceContainer")
        assertContrast(scheme.primary, scheme.background, minRatio = 3.0, "Light primary on background")
        assertContrast(
            scheme.onPrimaryContainer,
            scheme.primaryContainer,
            minRatio = 4.5,
            "Light onPrimaryContainer on primaryContainer",
        )
    }

    @Test
    fun testBlueThemeWcagContrast() {
        val scheme = BlueColorScheme
        assertContrast(scheme.onSurface, scheme.background, minRatio = 4.5, "Blue onSurface on background")
        assertContrast(
            scheme.onSurface,
            scheme.surfaceContainerHigh,
            minRatio = 4.5,
            "Blue onSurface on surfaceContainerHigh",
        )
        assertContrast(scheme.onSurface, scheme.surfaceContainer, minRatio = 4.5, "Blue onSurface on surfaceContainer")

        assertContrast(
            scheme.onSurfaceVariant,
            scheme.background,
            minRatio = 4.5,
            "Blue onSurfaceVariant on background",
        )
        assertContrast(
            scheme.onSurfaceVariant,
            scheme.surfaceContainerHigh,
            minRatio = 4.5,
            "Blue onSurfaceVariant on surfaceContainerHigh",
        )

        assertContrast(scheme.outline, scheme.background, minRatio = 1.3, "Blue outline on background")
        assertContrast(scheme.primary, scheme.surfaceContainer, minRatio = 3.0, "Blue primary on surfaceContainer")
        assertContrast(
            scheme.onPrimaryContainer,
            scheme.primaryContainer,
            minRatio = 4.5,
            "Blue onPrimaryContainer on primaryContainer",
        )
    }

    private fun assertContrast(fg: Color, bg: Color, minRatio: Double, label: String) {
        val ratio = ColorContrast.contrastRatio(fg, bg)
        assertTrue(
            ratio >= minRatio,
            "$label contrast ratio $ratio is below required minimum $minRatio " +
                "(${ColorContrast.toHexString(fg)} on ${ColorContrast.toHexString(bg)})",
        )
    }
}
