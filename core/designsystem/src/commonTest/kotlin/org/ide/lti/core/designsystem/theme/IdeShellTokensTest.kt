package org.ide.lti.core.designsystem.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdeShellTokensTest {

    @Test
    fun testLogicalFrameDimensions() {
        assertEquals(40.dp, IdeShellDimens.TopBarHeight, "Top bar height must be 40 dp")
        assertEquals(56.dp, IdeShellDimens.PipelineRailWidth, "Pipeline rail width must be 56 dp")
        assertEquals(230.dp, IdeShellDimens.WorkspaceNavigatorWidth, "Workspace navigator width must be 230 dp")
        assertEquals(32.dp, IdeShellDimens.BreadcrumbHeight, "Breadcrumb bar height must be 32 dp")
        assertEquals(24.dp, IdeShellDimens.StatusBarHeight, "Bottom status bar height must be 24 dp")

        assertEquals(1024.dp, IdeShellDimens.CompactBreakpoint)
        assertEquals(768.dp, IdeShellDimens.CompactHeightBreakpoint)
        assertEquals(1280.dp, IdeShellDimens.ReadableContentMaxWidth)
        assertEquals(1.dp, IdeShellDimens.HairlineBorder)
        assertEquals(6.dp, IdeShellDimens.CardCornerRadius)
        assertEquals(4.dp, IdeShellDimens.ControlCornerRadius)
        assertEquals(3.dp, IdeShellDimens.BadgeCornerRadius)
    }

    @Test
    fun testShapesAndMotionTokens() {
        assertEquals(androidx.compose.ui.graphics.RectangleShape, IdeShellShapes.Frame)
        assertEquals(androidx.compose.ui.graphics.RectangleShape, IdeShellShapes.Panel)
        assertTrue(IdeShellShapes.Card is androidx.compose.foundation.shape.RoundedCornerShape)
        assertTrue(IdeShellShapes.Control is androidx.compose.foundation.shape.RoundedCornerShape)
        assertTrue(IdeShellShapes.Badge is androidx.compose.foundation.shape.RoundedCornerShape)

        assertEquals(100, IdeShellMotion.StateTransitionDuration)
        assertEquals(200, IdeShellMotion.DrawerTransitionDuration)
        assertEquals(MotionEasing.Standard, IdeShellMotion.Easing)
    }

    @Test
    fun testDarkThemeWcagContrast() {
        val scheme = IdeShellTokens.Dark
        assertContrast(scheme.textMain, scheme.appBackground, minRatio = 4.5, "Dark textMain on appBackground")
        assertContrast(scheme.textMain, scheme.topBarBackground, minRatio = 4.5, "Dark textMain on topBarBackground")
        assertContrast(scheme.textMain, scheme.cardBackground, minRatio = 4.5, "Dark textMain on cardBackground")

        assertContrast(scheme.textMuted, scheme.appBackground, minRatio = 4.5, "Dark textMuted on appBackground")
        assertContrast(scheme.textMuted, scheme.topBarBackground, minRatio = 4.5, "Dark textMuted on topBarBackground")
        assertContrast(scheme.textMuted, scheme.cardBackground, minRatio = 4.5, "Dark textMuted on cardBackground")

        assertContrast(scheme.textDim, scheme.appBackground, minRatio = 4.5, "Dark textDim on appBackground")
        assertContrast(scheme.textDim, scheme.topBarBackground, minRatio = 4.5, "Dark textDim on topBarBackground")

        assertContrast(scheme.focusRing, scheme.topBarBackground, minRatio = 3.0, "Dark focusRing on topBarBackground")
        assertContrast(scheme.focusRing, scheme.cardBackground, minRatio = 3.0, "Dark focusRing on cardBackground")
        assertContrast(
            scheme.accentBlue,
            scheme.topBarBackground,
            minRatio = 3.0,
            label = "Dark accentBlue on topBarBackground",
        )
    }

    @Test
    fun testLightThemeWcagContrast() {
        val scheme = IdeShellTokens.Light
        assertContrast(scheme.textMain, scheme.topBarBackground, minRatio = 4.5, "Light textMain on topBarBackground")
        assertContrast(scheme.textMain, scheme.appBackground, minRatio = 4.5, "Light textMain on appBackground")
        assertContrast(scheme.textMain, scheme.cardBackground, minRatio = 4.5, "Light textMain on cardBackground")

        assertContrast(scheme.textMuted, scheme.topBarBackground, minRatio = 4.5, "Light textMuted on topBarBackground")
        assertContrast(scheme.textMuted, scheme.appBackground, minRatio = 4.5, "Light textMuted on appBackground")

        assertContrast(scheme.textDim, scheme.topBarBackground, minRatio = 4.5, "Light textDim on topBarBackground")
        assertContrast(scheme.textDim, scheme.appBackground, minRatio = 4.5, "Light textDim on appBackground")

        assertContrast(scheme.focusRing, scheme.topBarBackground, minRatio = 3.0, "Light focusRing on topBarBackground")
        assertContrast(scheme.focusRing, scheme.appBackground, minRatio = 3.0, "Light focusRing on appBackground")
    }

    @Test
    fun testBlueThemeWcagContrast() {
        val scheme = IdeShellTokens.Blue
        assertContrast(scheme.textMain, scheme.appBackground, minRatio = 4.5, "Blue textMain on appBackground")
        assertContrast(scheme.textMain, scheme.cardBackground, minRatio = 4.5, "Blue textMain on cardBackground")
        assertContrast(scheme.textMain, scheme.topBarBackground, minRatio = 4.5, "Blue textMain on topBarBackground")

        assertContrast(scheme.textMuted, scheme.appBackground, minRatio = 4.5, "Blue textMuted on appBackground")
        assertContrast(scheme.textMuted, scheme.cardBackground, minRatio = 4.5, "Blue textMuted on cardBackground")

        assertContrast(scheme.textDim, scheme.appBackground, minRatio = 4.5, "Blue textDim on appBackground")
        assertContrast(scheme.textDim, scheme.cardBackground, minRatio = 4.5, "Blue textDim on cardBackground")

        assertContrast(scheme.focusRing, scheme.topBarBackground, minRatio = 3.0, "Blue focusRing on topBarBackground")
    }

    private fun assertContrast(
        fg: androidx.compose.ui.graphics.Color,
        bg: androidx.compose.ui.graphics.Color,
        minRatio: Double,
        label: String,
    ) {
        val ratio = ColorContrast.contrastRatio(fg, bg)
        assertTrue(
            ratio >= minRatio,
            "$label contrast ratio $ratio is below required minimum $minRatio " +
                "(${ColorContrast.toHexString(fg)} on ${ColorContrast.toHexString(bg)})",
        )
    }
}
