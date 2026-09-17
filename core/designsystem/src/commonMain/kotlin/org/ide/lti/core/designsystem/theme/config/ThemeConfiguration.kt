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

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.kashif_e.backdrop.BackdropEffectScope
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.shadow.Shadow
import org.ide.lti.core.designsystem.theme.DiagnosticColors
import org.ide.lti.core.designsystem.theme.GlassColorScheme
import org.ide.lti.core.designsystem.theme.SyntaxColorScheme

/**
 * Visual backdrop rendering style for the underlying application canvas.
 */
sealed interface BackdropStyle {
    /**
     * Luminous multi-point cosmic nebula and aurora gradients for dark AMOLED glass refraction.
     */
    data class CosmicAurora(
        val nebulaTint: Color,
        val secondaryTint: Color,
        val isAnimated: Boolean = true,
    ) : BackdropStyle

    /**
     * Solid, distraction-free neutral backdrop for high-focus dark or light environments.
     */
    data class Solid(
        val color: Color,
    ) : BackdropStyle

    /**
     * Soft luminous ambient frosted gradient for light glass refraction.
     */
    data class FrostedLight(
        val nebulaTint: Color,
        val secondaryTint: Color,
    ) : BackdropStyle
}

/**
 * Configuration for the application backdrop canvas.
 */
@Immutable
data class BackdropConfiguration(
    val style: BackdropStyle,
    val baseColor: Color,
)

/**
 * Geometric, surface, and specular styling for layout panels.
 */
@Immutable
data class PanelConfiguration(
    val shape: Shape,
    val surfaceColor: Color,
    val tint: Color,
    val borderColor: Color,
    val borderWidth: Dp,
    val highlight: Highlight? = null,
    val shadow: Shadow? = null,
    val effects: (BackdropEffectScope.() -> Unit)? = null,
    val hasFloatingGaps: Boolean = false,
    val scrollEdgeFade: Boolean = false,
    val focusedBorderColor: Color? = null,
)

/**
 * Brand assets and gradient brushes.
 */
@Immutable
data class BrandingConfiguration(
    val logoBadgeBrush: Brush,
    val logoGradientEnd: Color,
)

/**
 * Complete, self-contained configuration for an application theme.
 *
 * Adheres to Clean Architecture and Single Responsibility Principle: each theme
 * owns its full specification without depending on external if-else branches.
 */
@Immutable
data class ThemeConfiguration(
    val id: String,
    val displayName: String,
    val indicatorColor: Color,
    val isDark: Boolean,
    val seedColor: Color,
    val materialColorScheme: ColorScheme,
    val glassColorScheme: GlassColorScheme,
    val syntaxColorScheme: SyntaxColorScheme,
    val diagnosticColors: DiagnosticColors,
    val backdropConfig: BackdropConfiguration,
    val panelConfig: PanelConfiguration,
    val brandingConfig: BrandingConfiguration,
    val ideShellColorScheme: org.ide.lti.core.designsystem.theme.IdeShellColorScheme =
        if (isDark) {
            org.ide.lti.core.designsystem.theme.IdeShellTokens.Dark
        } else {
            org.ide.lti.core.designsystem.theme.IdeShellTokens.Light
        },
)
