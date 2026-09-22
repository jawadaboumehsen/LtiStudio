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
import org.ide.lti.core.designsystem.theme.BlueGlassColorScheme
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.LtiSeedColor
import org.ide.lti.core.designsystem.theme.ThemeColors
import org.ide.lti.core.designsystem.theme.deriveDiagnosticColors
import org.ide.lti.core.designsystem.theme.deriveSyntaxColorScheme
import org.ide.lti.core.designsystem.theme.withVibrantPrimary

/**
 * Complete specification for the signature AMOLED Blue application theme.
 *
 * Features:
 * - Vibrant light sky-blue (#38BDF8) brand accents
 * - Translucent blue-tinted glass surfaces and specular glowing borders
 * - Multi-point luminous Cosmic Aurora backdrop gradients
 * - Seamless edge-to-edge layout panels
 */
val BlueThemeConfig: ThemeConfiguration by lazy {
    val materialScheme = dynamicColorScheme(
        seedColor = LtiSeedColor,
        isDark = true,
        isAmoled = true,
    ).withVibrantPrimary(LtiSeedColor, isDark = true)
    val glassScheme = BlueGlassColorScheme
    val syntax = deriveSyntaxColorScheme(
        primaryColor = materialScheme.primary,
        onSurfaceColor = materialScheme.onSurface,
        isDark = true,
    )
    val diagnostics = deriveDiagnosticColors(isDark = true)

    ThemeConfiguration(
        id = "blue",
        displayName = "Blue",
        indicatorColor = ThemeColors.Blue,
        isDark = true,
        seedColor = LtiSeedColor,
        materialColorScheme = materialScheme,
        glassColorScheme = glassScheme,
        syntaxColorScheme = syntax,
        diagnosticColors = diagnostics,
        backdropConfig = BackdropConfiguration(
            style = BackdropStyle.CosmicAurora(
                nebulaTint = LtiSeedColor,
                secondaryTint = BrandColors.LogoGradientEnd,
                isAnimated = true,
            ),
            baseColor = Color(0xFF000000),
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
        ideShellColorScheme = org.ide.lti.core.designsystem.theme.IdeShellTokens.Blue,
    )
}
