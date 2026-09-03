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
import org.ide.lti.core.designsystem.theme.config.AppTheme
import org.ide.lti.core.designsystem.theme.config.createDarkFleetColorScheme

/**
 * Brand seed color used to dynamically generate the Material3 color scheme and glass palette.
 * Light Sky Blue (#38BDF8) - paired with an AMOLED true-black dark surface base (see
 * [DarkGlassColorScheme]'s `isAmoled = true`). Matches [BrandColors.LogoGradientEnd], so the app's
 * accent (buttons, focus rings, selected icon backgrounds, syntax highlighting) and the logo badge
 * gradient read as the same color.
 */
val LtiSeedColor = Color(0xFF38BDF8)

/**
 * Syntax highlighting color scheme for code editor tokens.
 *
 * @property keyword Language keywords (e.g. `fun`, `val`, `class`, `return`).
 * @property function Function names and declarations (e.g. `remember()`, `tokenize()`).
 * @property string String literals and characters (e.g. `"..."`, `'a'`).
 * @property number Numeric literals (e.g. `42`, `3.14f`, `0xFF`).
 * @property comment Single-line and multi-line comments (e.g. `// ...`, `/* ... */`).
 * @property type Type names and classes (e.g. `String`, `List`, `Modifier`).
 * @property annotation Annotations (e.g. `@Composable`, `@OptIn`).
 * @property plain Default code text (variables, punctuation, operators).
 */
@Immutable
data class SyntaxColorScheme(
    val keyword: Color,
    val function: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val type: Color,
    val annotation: Color,
    val plain: Color,
)

/**
 * Derives a [SyntaxColorScheme] from a brand primary [Color] and theme mode using HCT dynamic color science.
 * Ensures WCAG AA contrast (>= 4.5:1) against `canvasSurface` in both dark and light modes.
 */
fun deriveSyntaxColorScheme(
    primaryColor: Color,
    onSurfaceColor: Color,
    isDark: Boolean,
): SyntaxColorScheme {
    val seedArgb = ((primaryColor.alpha * 255).toInt() shl 24) or
        ((primaryColor.red * 255).toInt() shl 16) or
        ((primaryColor.green * 255).toInt() shl 8) or
        (primaryColor.blue * 255).toInt()
    val seedHct = com.materialkolor.hct.Hct.fromInt(seedArgb)

    return if (isDark) {
        val tone = 80.0
        val commentTone = 68.0
        SyntaxColorScheme(
            keyword = Color(com.materialkolor.hct.Hct.from(32.0, 60.0, tone).toInt()),
            function = Color(com.materialkolor.hct.Hct.from(300.0, 50.0, tone).toInt()),
            string = Color(com.materialkolor.hct.Hct.from(135.0, 50.0, tone).toInt()),
            number = Color(com.materialkolor.hct.Hct.from(212.0, 50.0, tone).toInt()),
            type = Color(com.materialkolor.hct.Hct.from(168.0, 50.0, tone).toInt()),
            annotation = Color(com.materialkolor.hct.Hct.from(80.0, 60.0, tone).toInt()),
            comment = Color(com.materialkolor.hct.Hct.from(seedHct.hue, 12.0, commentTone).toInt()),
            plain = onSurfaceColor,
        )
    } else {
        val tone = 38.0
        val commentTone = 46.0
        SyntaxColorScheme(
            keyword = Color(com.materialkolor.hct.Hct.from(28.0, 65.0, tone).toInt()),
            function = Color(com.materialkolor.hct.Hct.from(300.0, 55.0, tone).toInt()),
            string = Color(com.materialkolor.hct.Hct.from(140.0, 55.0, 36.0).toInt()),
            number = Color(com.materialkolor.hct.Hct.from(255.0, 55.0, 36.0).toInt()),
            type = Color(com.materialkolor.hct.Hct.from(175.0, 55.0, 34.0).toInt()),
            annotation = Color(com.materialkolor.hct.Hct.from(80.0, 60.0, 36.0).toInt()),
            comment = Color(com.materialkolor.hct.Hct.from(seedHct.hue, 12.0, commentTone).toInt()),
            plain = onSurfaceColor,
        )
    }
}

/**
 * Semantic diagnostic and git-status color tokens.
 *
 * @property error Error state or indicator (e.g. build errors, fatal diagnostics).
 * @property warning Warning state or indicator (e.g. linter warnings, deprecations).
 * @property success Success state or indicator (e.g. tests passed, build succeeded).
 * @property info Information state or indicator (e.g. hints, notes).
 * @property gitModified Git modified status indicator.
 * @property gitAdded Git added/untracked status indicator.
 * @property gitDeleted Git deleted status indicator.
 */
@Immutable
data class DiagnosticColors(
    val error: Color,
    val warning: Color,
    val success: Color,
    val info: Color,
    val gitModified: Color,
    val gitAdded: Color,
    val gitDeleted: Color,
)

/**
 * Derives a [DiagnosticColors] scheme using HCT dynamic color science.
 * Ensures WCAG AA contrast (>= 4.5:1 text/diagnostic, >= 3.0:1 UI) against both
 * `canvasSurface` and `glassSurface` in both dark and light modes.
 */
fun deriveDiagnosticColors(
    isDark: Boolean,
): DiagnosticColors {
    return if (isDark) {
        val tone = 80.0
        DiagnosticColors(
            error = Color(com.materialkolor.hct.Hct.from(25.0, 70.0, tone).toInt()),
            warning = Color(com.materialkolor.hct.Hct.from(75.0, 70.0, tone).toInt()),
            success = Color(com.materialkolor.hct.Hct.from(145.0, 60.0, tone).toInt()),
            info = Color(com.materialkolor.hct.Hct.from(220.0, 60.0, tone).toInt()),
            gitModified = Color(com.materialkolor.hct.Hct.from(50.0, 65.0, tone).toInt()),
            gitAdded = Color(com.materialkolor.hct.Hct.from(140.0, 60.0, tone).toInt()),
            gitDeleted = Color(com.materialkolor.hct.Hct.from(15.0, 70.0, tone).toInt()),
        )
    } else {
        val tone = 36.0
        DiagnosticColors(
            error = Color(com.materialkolor.hct.Hct.from(25.0, 70.0, tone).toInt()),
            warning = Color(com.materialkolor.hct.Hct.from(75.0, 70.0, 32.0).toInt()),
            success = Color(com.materialkolor.hct.Hct.from(145.0, 60.0, 32.0).toInt()),
            info = Color(com.materialkolor.hct.Hct.from(220.0, 60.0, tone).toInt()),
            gitModified = Color(com.materialkolor.hct.Hct.from(50.0, 65.0, 32.0).toInt()),
            gitAdded = Color(com.materialkolor.hct.Hct.from(140.0, 60.0, 32.0).toInt()),
            gitDeleted = Color(com.materialkolor.hct.Hct.from(15.0, 70.0, tone).toInt()),
        )
    }
}

/**
 * Glass color scheme definition for translucent UI surfaces.
 */
@Immutable
data class GlassColorScheme(
    val glassSurface: Color,
    val glassSurfaceElevated: Color,
    val chromeSurface: Color,
    val canvasSurface: Color,
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
    val syntax: SyntaxColorScheme,
    val diagnostics: DiagnosticColors,
)

/**
 * Derives a vibrant, high-chroma accent color from [LtiSeedColor]'s own hue.
 *
 * Material3's dynamic `colorScheme.primary` role tonal-maps any seed to a fixed, desaturated
 * tone (e.g. a vivid cyan seed becomes a pastel steel-teal in dark mode) - correct for general
 * M3 surfaces, but it defeats the point of a punchy brand accent used for icon fills, selected
 * states, and focus rings. This samples the seed's real hue at a high, fixed chroma instead, the
 * same direct-HCT approach [deriveSyntaxColorScheme] already uses for syntax token colors.
 */
fun deriveVibrantAccent(seedColor: Color = LtiSeedColor, isDark: Boolean): Color {
    val seedArgb = ((seedColor.alpha * 255).toInt() shl 24) or
        ((seedColor.red * 255).toInt() shl 16) or
        ((seedColor.green * 255).toInt() shl 8) or
        (seedColor.blue * 255).toInt()
    val seedHct = com.materialkolor.hct.Hct.fromInt(seedArgb)
    val tone = if (isDark) 75.0 else 45.0
    val chroma = if (seedHct.chroma < 10.0) 0.0 else maxOf(seedHct.chroma, 60.0)
    return Color(com.materialkolor.hct.Hct.from(seedHct.hue, chroma, tone).toInt())
}

/**
 * Derives a [GlassColorScheme] from a generated [ColorScheme] and theme mode.
 */
fun dynamicGlassColorScheme(
    colorScheme: ColorScheme,
    isDark: Boolean,
    seedColor: Color = LtiSeedColor,
    appTheme: AppTheme = AppTheme.Blue,
): GlassColorScheme {
    val vibrantAccent = deriveVibrantAccent(seedColor, isDark)
    val syntaxColors = deriveSyntaxColorScheme(
        primaryColor = colorScheme.primary,
        onSurfaceColor = colorScheme.onSurface,
        isDark = isDark,
    )
    val diagnosticColors = deriveDiagnosticColors(
        isDark = isDark,
    )
    return if (isDark) {
        if (appTheme == AppTheme.Dark) {
            // Fleet Neutral Dark Theme: Pure black canvas, black-gray surfaces, neutral hairline borders, Fleet electric blue accents
            GlassColorScheme(
                glassSurface = colorScheme.surface,
                glassSurfaceElevated = colorScheme.surfaceContainer,
                chromeSurface = colorScheme.surfaceContainerLowest,
                canvasSurface = colorScheme.surfaceContainerLowest,
                glassTint = Color.Transparent,
                glassTintSecondary = Color.Transparent,
                glassBorder = colorScheme.outline,
                glassBorderHighlight = ThemeColors.FleetBlue,
                onGlass = colorScheme.onSurface,
                onGlassSecondary = colorScheme.onSurfaceVariant,
                onGlassMuted = colorScheme.onSurfaceVariant.copy(alpha = AlphaTokens.TextMuted),
                glassHighlight = Color(0x1AFFFFFF),
                glassShadow = colorScheme.scrim.copy(alpha = AlphaTokens.Prominent),
                glassAccent = ThemeColors.FleetBlue,
                focusRing = ThemeColors.FleetFocusGlow,
                syntax = syntaxColors,
                diagnostics = diagnosticColors,
            )
        } else {
            // Blue Theme: Signature vibrant glass tints, sky blue accents, cyan glow
            GlassColorScheme(
                glassSurface = colorScheme.surface.copy(alpha = AlphaTokens.Elevated),
                glassSurfaceElevated = colorScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.Prominent),
                chromeSurface = colorScheme.surfaceContainerLowest.copy(alpha = AlphaTokens.Elevated),
                canvasSurface = colorScheme.surfaceContainerLow,
                glassTint = colorScheme.primary.copy(alpha = AlphaTokens.Glow),
                glassTintSecondary = colorScheme.secondary.copy(alpha = AlphaTokens.Glow),
                glassBorder = colorScheme.onSurface.copy(alpha = AlphaTokens.Hover),
                glassBorderHighlight = colorScheme.primary.copy(alpha = AlphaTokens.Prominent),
                onGlass = colorScheme.onSurface,
                onGlassSecondary = colorScheme.onSurfaceVariant,
                onGlassMuted = colorScheme.onSurfaceVariant.copy(alpha = AlphaTokens.TextMuted),
                glassHighlight = colorScheme.primary.copy(alpha = AlphaTokens.Medium),
                glassShadow = colorScheme.scrim.copy(alpha = AlphaTokens.Prominent),
                glassAccent = vibrantAccent,
                focusRing = vibrantAccent,
                syntax = syntaxColors,
                diagnostics = diagnosticColors,
            )
        }
    } else {
        GlassColorScheme(
            glassSurface = colorScheme.surface.copy(alpha = AlphaTokens.Elevated),
            glassSurfaceElevated = colorScheme.surfaceContainerHighest.copy(alpha = AlphaTokens.PrimaryButtonTint),
            chromeSurface = colorScheme.surfaceContainer.copy(alpha = AlphaTokens.Elevated),
            canvasSurface = colorScheme.surfaceContainerLowest,
            glassTint = colorScheme.primary.copy(alpha = AlphaTokens.Subtle),
            glassTintSecondary = colorScheme.secondary.copy(alpha = AlphaTokens.Subtle),
            glassBorder = colorScheme.outline.copy(alpha = AlphaTokens.Medium),
            glassBorderHighlight = colorScheme.primary.copy(alpha = AlphaTokens.Prominent),
            onGlass = colorScheme.onSurface,
            onGlassSecondary = colorScheme.onSurfaceVariant,
            onGlassMuted = colorScheme.onSurfaceVariant.copy(alpha = AlphaTokens.TextMuted),
            glassHighlight = colorScheme.primary.copy(alpha = AlphaTokens.Muted),
            glassShadow = colorScheme.scrim.copy(alpha = AlphaTokens.Glow),
            glassAccent = vibrantAccent,
            focusRing = vibrantAccent,
            syntax = syntaxColors,
            diagnostics = diagnosticColors,
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
    seedColor: Color = LtiSeedColor,
    appTheme: AppTheme = AppTheme.Blue,
): GlassColorScheme {
    return remember(colorScheme, isDark, seedColor, appTheme) {
        dynamicGlassColorScheme(colorScheme, isDark, seedColor, appTheme)
    }
}

/**
 * Dynamically generated default Blue Glass Color Scheme (Signature AMOLED).
 */
val BlueGlassColorScheme: GlassColorScheme
    get() = dynamicGlassColorScheme(
        colorScheme = dynamicColorScheme(seedColor = LtiSeedColor, isDark = true, isAmoled = true),
        isDark = true,
        seedColor = LtiSeedColor,
        appTheme = AppTheme.Blue,
    )

/**
 * Dynamically generated default Dark Glass Color Scheme (Fleet Neutral Dark).
 */
val DarkGlassColorScheme: GlassColorScheme
    get() = dynamicGlassColorScheme(
        colorScheme = createDarkFleetColorScheme(),
        isDark = true,
        seedColor = Color(0xFF475569),
        appTheme = AppTheme.Dark,
    )

/**
 * Dynamically generated default Light Glass Color Scheme.
 */
val LightGlassColorScheme: GlassColorScheme
    get() = dynamicGlassColorScheme(
        colorScheme = dynamicColorScheme(seedColor = LtiSeedColor, isDark = false),
        isDark = false,
        seedColor = LtiSeedColor,
        appTheme = AppTheme.Light,
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
    val LogoGradientEnd = Color(0xFF38BDF8)
}

/**
 * Centralized theme indicator and layer token colors.
 */
object ThemeColors {
    val Blue = Color(0xFF38BDF8)
    val Dark = Color(0xFF131518)
    val Light = Color(0xFFFFFFFF)

    // Fleet-inspired Dark Theme Layer Architecture (Black base, Black-Gray surfaces, Gray elevated)
    val DarkLayerBase = Color(0xFF000000)          // Pure Black base canvas & gutters (#000000)
    val DarkLayerSurface = Color(0xFF131518)       // Black-Gray panel / editor / terminal surface (#131518)
    val DarkLayerElevated = Color(0xFF1C1E23)      // Dark-Gray elevated card / dialog (#1C1E23)
    val DarkLayerControl = Color(0xFF262931)       // Gray active tab / chip / button (#262931)
    val DarkLayerHighlight = Color(0xFF353A45)     // Slate-Gray hairline divider & outline (#353A45)
    val FleetBlue = Color(0xFF3574F0)              // Fleet Action & Primary Blue (#3574F0)
    val FleetFocusGlow = Color(0xFF716BF6)         // Fleet Input & Focus Ring Glow (#716BF6)
    val FleetCyan = Color(0xFF38BDF8)              // Fleet Cyan Gradient End (#38BDF8)
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
