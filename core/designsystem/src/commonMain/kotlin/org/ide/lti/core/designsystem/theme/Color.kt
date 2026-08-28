/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamicColorScheme

/**
 * Brand seed color used to dynamically generate the Material3 color scheme and glass palette.
 * Saturated Azure Blue (#0B84FF) - paired with an AMOLED true-black dark surface base (see
 * [DarkGlassColorScheme]'s `isAmoled = true`), matching Samsung One UI's signature punchy blue accent.
 */
val LtiSeedColor = Color(0xFF0B84FF)

/**
 * Glass color scheme definition for translucent UI surfaces.
 */
@Immutable
data class GlassColorScheme(
    val glassSurface: Color,
    val glassSurfaceElevated: Color,
    val glassTint: Color,
    val glassTintSecondary: Color,
    val glassBorder: Color,
    val glassBorderHighlight: Color,
    val onGlass: Color,
    val onGlassSecondary: Color,
    val onGlassMuted: Color,
    val glassHighlight: Color,
    val glassShadow: Color,
    val glassAccent: Color,
    val focusRing: Color,
)

/**
 * Derives a [GlassColorScheme] from a generated [ColorScheme] and theme mode.
 */
fun dynamicGlassColorScheme(
    colorScheme: ColorScheme,
    isDark: Boolean,
): GlassColorScheme {
    return if (isDark) {
        GlassColorScheme(
            glassSurface = colorScheme.surface.copy(alpha = 0.40f),
            glassSurfaceElevated = colorScheme.surfaceContainerHigh.copy(alpha = 0.54f),
            glassTint = colorScheme.primary.copy(alpha = 0.14f),
            glassTintSecondary = colorScheme.secondary.copy(alpha = 0.14f),
            glassBorder = colorScheme.onSurface.copy(alpha = 0.20f),
            glassBorderHighlight = colorScheme.primary.copy(alpha = 0.60f),
            onGlass = colorScheme.onSurface,
            onGlassSecondary = colorScheme.onSurfaceVariant,
            onGlassMuted = colorScheme.outline,
            glassHighlight = colorScheme.primary.copy(alpha = 0.40f),
            glassShadow = colorScheme.scrim.copy(alpha = 0.60f),
            glassAccent = colorScheme.primary,
            focusRing = colorScheme.primary,
        )
    } else {
        GlassColorScheme(
            glassSurface = colorScheme.surface.copy(alpha = 0.70f),
            glassSurfaceElevated = colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
            glassTint = colorScheme.primary.copy(alpha = 0.12f),
            glassTintSecondary = colorScheme.secondary.copy(alpha = 0.12f),
            glassBorder = colorScheme.outline.copy(alpha = 0.15f),
            glassBorderHighlight = colorScheme.primary.copy(alpha = 0.50f),
            onGlass = colorScheme.onSurface,
            onGlassSecondary = colorScheme.onSurfaceVariant,
            onGlassMuted = colorScheme.outline,
            glassHighlight = colorScheme.primary.copy(alpha = 0.30f),
            glassShadow = colorScheme.scrim.copy(alpha = 0.15f),
            glassAccent = colorScheme.primary,
            focusRing = colorScheme.primary,
        )
    }
}

/**
 * Composable helper to remember dynamic glass colors derived from a [ColorScheme].
 */
@Composable
fun rememberDynamicGlassColorScheme(
    colorScheme: ColorScheme,
    isDark: Boolean,
): GlassColorScheme {
    return remember(colorScheme, isDark) {
        dynamicGlassColorScheme(colorScheme, isDark)
    }
}

/**
 * Dynamically generated default Dark Glass Color Scheme.
 */
val DarkGlassColorScheme: GlassColorScheme
    get() = dynamicGlassColorScheme(
        colorScheme = dynamicColorScheme(seedColor = LtiSeedColor, isDark = true, isAmoled = true),
        isDark = true,
    )

/**
 * Dynamically generated default Light Glass Color Scheme.
 */
val LightGlassColorScheme: GlassColorScheme
    get() = dynamicGlassColorScheme(
        colorScheme = dynamicColorScheme(seedColor = LtiSeedColor, isDark = false),
        isDark = false,
    )

/**
 * Centralized token colors for window control buttons (e.g. native Windows close hover).
 */
object WindowControlColors {
    val CloseHover = Color(0xFFE81123)
}

/**
 * Centralized token colors for brand assets and logos.
 */
object BrandColors {
    val LogoGradientEnd = Color(0xFF00E5FF)
}

/**
 * Centralized token colors for performance monitoring metrics.
 */
object PerformanceMonitorColors {
    val Good = Color(0xFF4EC9B0)
    val Warning = Color(0xFFD7BA7D)
    val Degraded = Color(0xFFCE9178)
    val Bad = Color(0xFFF48771)
}

/**
 * Centralized token colors for toggle switch active states.
 */
object ToggleColors {
    val ActiveLight = Color(0xFF34C759)
    val ActiveDark = Color(0xFF30D158)
}

/**
 * Centralized token colors for layout previews and placeholders.
 */
object PreviewSampleColors {
    val Blue = Color(0xFF2196F3)
    val Purple = Color(0xFF9C27B0)
    val Orange = Color(0xFFFF9800)
    val Green = Color(0xFF4CAF50)
}

/**
 * Centralized token colors for glass layout shadows and dividers.
 */
object GlassLayoutColors {
    val PanelShadow = Color(0x26000000)
}

/**
 * Centralized token colors for password strength indicator states.
 */
object PasswordStrengthColors {
    val Weak = Color(0xFF8B6609)
    val Strong = Color(0xFF41B06D)
    val VeryStrong = Color(0xFF9C27B0)
}
