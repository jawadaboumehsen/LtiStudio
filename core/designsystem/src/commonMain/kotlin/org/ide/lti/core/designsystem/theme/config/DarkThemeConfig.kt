/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme.config

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import com.kashif_e.backdrop.highlight.Highlight
import androidx.compose.material3.ColorScheme
import com.materialkolor.dynamicColorScheme
import com.kashif_e.backdrop.shadow.Shadow
import org.ide.lti.core.designsystem.component.layout.LtiLayoutDefaults
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.DarkGlassColorScheme
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.ThemeColors
import org.ide.lti.core.designsystem.theme.deriveDiagnosticColors
import org.ide.lti.core.designsystem.theme.deriveSyntaxColorScheme

/**
 * Factory for creating the Fleet Dark theme [ColorScheme] with electric blue accents.
 */
fun createDarkFleetColorScheme(
    baseScheme: ColorScheme = dynamicColorScheme(seedColor = ThemeColors.FleetBlue, isDark = true, isAmoled = true),
): ColorScheme = baseScheme.copy(
    primary = ThemeColors.FleetBlue,
    onPrimary = Color(0xFFFFFFFF),
    secondary = ThemeColors.DarkLayerControl,
    onSecondary = Color(0xFFF1F5F9),
    tertiary = ThemeColors.DarkLayerHighlight,
    background = ThemeColors.DarkLayerBase,
    surface = ThemeColors.DarkLayerSurface,
    surfaceContainerLowest = ThemeColors.DarkLayerBase,
    surfaceContainerLow = ThemeColors.DarkLayerSurface,
    surfaceContainer = ThemeColors.DarkLayerElevated,
    surfaceContainerHigh = ThemeColors.DarkLayerControl,
    surfaceContainerHighest = ThemeColors.DarkLayerHighlight,
    onSurface = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF8C93A0),
    outline = ThemeColors.DarkLayerHighlight,
)

/**
 * Complete specification for the Fleet-inspired Dark application theme.
 *
 * Features:
 * - Pure Black base (#000000) for contrast and deep space focus
 * - Layered Black-Gray surfaces (#131518) and elevated panels (#1C1E23)
 * - Slate-Gray hairline dividers and outlines (#353A45)
 * - JetBrains Fleet electric blue & indigo accents (#3574F0, #716BF6)
 * - Floating squircle panels with subtle shadows
 */
val DarkThemeConfig: ThemeConfiguration by lazy {
    val materialScheme = createDarkFleetColorScheme()
    val glassScheme = DarkGlassColorScheme
    val syntax = deriveSyntaxColorScheme(
        primaryColor = materialScheme.primary,
        onSurfaceColor = materialScheme.onSurface,
        isDark = true,
    )
    val diagnostics = deriveDiagnosticColors(isDark = true)

    ThemeConfiguration(
        id = "dark",
        displayName = "Dark",
        indicatorColor = ThemeColors.Dark,
        isDark = true,
        seedColor = ThemeColors.FleetBlue,
        materialColorScheme = materialScheme,
        glassColorScheme = glassScheme,
        syntaxColorScheme = syntax,
        diagnosticColors = diagnostics,
        backdropConfig = BackdropConfiguration(
            style = BackdropStyle.CosmicAurora(
                nebulaTint = ThemeColors.FleetBlue.copy(alpha = AlphaTokens.Ambient),
                secondaryTint = Color(0xFF716BF6).copy(alpha = AlphaTokens.Faded),
                isAnimated = true,
            ),
            baseColor = ThemeColors.DarkLayerBase,
        ),
        panelConfig = PanelConfiguration(
            shape = RectangleShape,
            surfaceColor = ThemeColors.DarkLayerSurface.copy(alpha = AlphaTokens.GlassElevated),
            tint = ThemeColors.FleetBlue.copy(alpha = AlphaTokens.UltraFaint),
            borderColor = Color.Transparent,
            borderWidth = Dp.Hairline,
            highlight = null,
            shadow = null,
            effects = LtiLayoutDefaults.EnhancedPanelEffects,
            hasFloatingGaps = false,
            scrollEdgeFade = true,
            focusedBorderColor = Color.Transparent,
        ),
        brandingConfig = BrandingConfiguration(
            logoBadgeBrush = Brush.linearGradient(
                listOf(
                    ThemeColors.FleetBlue,
                    ThemeColors.FleetCyan,
                ),
            ),
            logoGradientEnd = ThemeColors.FleetCyan,
        ),
        ideShellColorScheme = org.ide.lti.core.designsystem.theme.IdeShellTokens.Dark,
    )
}
