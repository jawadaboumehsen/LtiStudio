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
import com.materialkolor.dynamicColorScheme
import org.ide.lti.core.designsystem.component.layout.LtiLayoutDefaults
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.LightGlassColorScheme
import org.ide.lti.core.designsystem.theme.LtiSeedColor
import org.ide.lti.core.designsystem.theme.ThemeColors
import org.ide.lti.core.designsystem.theme.deriveDiagnosticColors
import org.ide.lti.core.designsystem.theme.deriveSyntaxColorScheme

/**
 * Complete specification for the Frosted Glass Light application theme.
 *
 * Features:
 * - Crisp, frosted translucent white surfaces with ambient soft blue highlights
 * - High-contrast dark typography and syntax highlighting (WCAG AA)
 * - Luminous light gradient backdrop for fluid refraction
 */
val LightThemeConfig: ThemeConfiguration by lazy {
    val materialScheme = dynamicColorScheme(
        seedColor = LtiSeedColor,
        isDark = false,
    )
    val glassScheme = LightGlassColorScheme
    val syntax = deriveSyntaxColorScheme(
        primaryColor = materialScheme.primary,
        onSurfaceColor = materialScheme.onSurface,
        isDark = false,
    )
    val diagnostics = deriveDiagnosticColors(isDark = false)

    ThemeConfiguration(
        id = "light",
        displayName = "Light",
        indicatorColor = ThemeColors.Light,
        isDark = false,
        seedColor = LtiSeedColor,
        materialColorScheme = materialScheme,
        glassColorScheme = glassScheme,
        syntaxColorScheme = syntax,
        diagnosticColors = diagnostics,
        backdropConfig = BackdropConfiguration(
            style = BackdropStyle.FrostedLight(
                nebulaTint = LtiSeedColor,
                secondaryTint = BrandColors.LogoGradientEnd,
            ),
            baseColor = materialScheme.background,
        ),
        panelConfig = PanelConfiguration(
            shape = RectangleShape,
            surfaceColor = glassScheme.canvasSurface.copy(alpha = AlphaTokens.Medium),
            tint = glassScheme.glassTint.copy(alpha = AlphaTokens.UltraFaint),
            borderColor = Color.Transparent,
            borderWidth = Dp.Hairline,
            highlight = null,
            shadow = null,
            effect = LtiLayoutDefaults.EnhancedPanelEffect,
            hasFloatingGaps = false,
            scrollEdgeFade = true,
        ),
        brandingConfig = BrandingConfiguration(
            logoBadgeBrush = Brush.linearGradient(
                listOf(
                    glassScheme.glassAccent,
                    BrandColors.LogoGradientEnd,
                ),
            ),
            logoGradientEnd = BrandColors.LogoGradientEnd,
        ),
        ideShellColorScheme = org.ide.lti.core.designsystem.theme.IdeShellTokens.Light,
    )
}
