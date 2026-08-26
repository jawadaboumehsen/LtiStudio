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

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

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
    val glassAccent: Color
) {
    companion object {
        fun defaultDark(): GlassColorScheme = DarkGlassColorScheme
        fun defaultLight(): GlassColorScheme = LightGlassColorScheme
        fun vibrant(): GlassColorScheme = DarkGlassColorScheme
    }
}

/**
 * Cosmic Aurora Liquid Glass palette.
 * Rich translucent blue and obsidian layers that refract the vibrant cosmic aurora beneath.
 */
val DarkGlassColorScheme = GlassColorScheme(
    glassSurface = Color(0x660B101E),            // ~40% Translucent Deep Cosmic Sapphire
    glassSurfaceElevated = Color(0x8A131C31),     // ~54% Elevated Frosted Glass
    glassTint = Color(0x2438BDF8),                // ~14% Glowing Cyan Tint
    glassTintSecondary = Color(0x24818CF8),       // ~14% Indigo Tint
    glassBorder = Color(0x33FFFFFF),              // ~20% Crisp Specular Light Bleed
    glassBorderHighlight = Color(0x9938BDF8),     // ~60% Luminous Cyan Border Highlight
    onGlass = Color(0xFFFFFFFF),                  // Crisp Pure White
    onGlassSecondary = Color(0xFFCBD5E1),         // Slate 300
    onGlassMuted = Color(0xFF94A3B8),             // Slate 400
    glassHighlight = Color(0x6638BDF8),           // ~40% Electric Cyan Glow
    glassShadow = Color(0x99000000),              // ~60% Deep Contrast Drop Shadow
    glassAccent = Color(0xFF38BDF8)               // Electric Cyan #38BDF8 Accent
)

/**
 * Light Glass Color Scheme â€” Frosted glass with clean ambient lighting.
 */
val LightGlassColorScheme = GlassColorScheme(
    glassSurface = Color(0xB3FFFFFF),             // ~70% Frosted White
    glassSurfaceElevated = Color(0xD9F8FAFC),     // ~85% Slate 50
    glassTint = Color(0x1F0284C7),                // ~12% Sky Blue
    glassTintSecondary = Color(0x1F6366F1),       // ~12% Indigo
    glassBorder = Color(0x260F172A),              // ~15% Dark Border
    glassBorderHighlight = Color(0x800284C7),     // ~50% Sky Blue
    onGlass = Color(0xFF0F172A),                  // Slate 900
    onGlassSecondary = Color(0xFF334155),         // Slate 700
    onGlassMuted = Color(0xFF64748B),             // Slate 500
    glassHighlight = Color(0x4D0284C7),           // ~30% Blue glow
    glassShadow = Color(0x26000000),              // ~15% Shadow
    glassAccent = Color(0xFF0284C7)               // Sky 600
)

fun GlassColorScheme.surfaceWithTint(): Color = glassSurface

val darkScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF070B14),
    primaryContainer = Color(0xFF131D33),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF818CF8),
    onSecondary = Color(0xFF070B14),
    secondaryContainer = Color(0xFF1E2648),
    onSecondaryContainer = Color(0xFFEEF2FF),
    tertiary = Color(0xFF2DD4BF),
    onTertiary = Color(0xFF070B14),
    tertiaryContainer = Color(0xFF0D3834),
    onTertiaryContainer = Color(0xFFCCFBF1),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    background = Color(0xFF060911),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF0C1322),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF162035),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFF8FAFC),
    inverseOnSurface = Color(0xFF070B14),
    inversePrimary = Color(0xFF0284C7),
    surfaceDim = Color(0xFF060911),
    surfaceBright = Color(0xFF162035),
    surfaceContainerLowest = Color(0xFF04060B),
    surfaceContainerLow = Color(0xFF060911),
    surfaceContainer = Color(0xFF0C1322),
    surfaceContainerHigh = Color(0xFF131D33),
    surfaceContainerHighest = Color(0xFF1E293B)
)

val lightScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFF6366F1),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEF2FF),
    onSecondaryContainer = Color(0xFF3730A3),
    tertiary = Color(0xFF0D9488),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCFBF1),
    onTertiaryContainer = Color(0xFF115E59),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF0F172A),
    inverseOnSurface = Color(0xFFF8FAFC),
    inversePrimary = Color(0xFF38BDF8),
    surfaceDim = Color(0xFFE2E8F0),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFCBD5E1)
)