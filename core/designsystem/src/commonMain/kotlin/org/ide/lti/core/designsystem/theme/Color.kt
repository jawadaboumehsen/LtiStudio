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
            // Apple Liquid Glass Dark Theme: Translucent charcoal surfaces, luminous specular hairlines, subtle frost tint, Fleet electric blue accents
            GlassColorScheme(
                glassSurface = colorScheme.surface.copy(alpha = AlphaTokens.GlassTranslucent),
                glassSurfaceElevated = colorScheme.surfaceContainer.copy(alpha = AlphaTokens.GlassElevated),
                chromeSurface = colorScheme.surfaceContainerLowest.copy(alpha = AlphaTokens.GlassChrome),
                canvasSurface = colorScheme.surfaceContainerLowest,
                glassTint = Color.White.copy(alpha = AlphaTokens.UltraFaint),
                glassTintSecondary = ThemeColors.FleetBlue.copy(alpha = AlphaTokens.GlassSecondaryTint),
                glassBorder = Color.White.copy(alpha = AlphaTokens.GlassBorderSpecular),
                glassBorderHighlight = Color.White.copy(alpha = AlphaTokens.GlassBorderSpecularHighlight),
                onGlass = colorScheme.onSurface,
                onGlassSecondary = colorScheme.onSurfaceVariant,
                onGlassMuted = colorScheme.onSurfaceVariant.copy(alpha = AlphaTokens.TextMuted),
                glassHighlight = Color.White.copy(alpha = AlphaTokens.GlassHighlightBright),
                glassShadow = Color.Black.copy(alpha = AlphaTokens.GlassShadowDeep),
                glassAccent = ThemeColors.FleetBlue,
                focusRing = ThemeColors.FleetFocusGlow,
                syntax = syntaxColors,
                diagnostics = diagnosticColors,
            )
        } else {
            // Blue Theme: Signature vibrant glass tints, sky blue accents, luminous cyan specular hairlines
            GlassColorScheme(
                glassSurface = colorScheme.surface.copy(alpha = AlphaTokens.Elevated),
                glassSurfaceElevated = colorScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.Prominent),
                chromeSurface = colorScheme.surfaceContainerLowest.copy(alpha = AlphaTokens.Elevated),
                canvasSurface = colorScheme.surfaceContainerLow,
                glassTint = colorScheme.primary.copy(alpha = AlphaTokens.Glow),
                glassTintSecondary = colorScheme.secondary.copy(alpha = AlphaTokens.Glow),
                glassBorder = colorScheme.primary.copy(alpha = AlphaTokens.Faded),
                glassBorderHighlight = colorScheme.primary.copy(alpha = AlphaTokens.IconTint),
                onGlass = colorScheme.onSurface,
                onGlassSecondary = colorScheme.onSurfaceVariant,
                onGlassMuted = colorScheme.onSurfaceVariant.copy(alpha = AlphaTokens.TextMuted),
                glassHighlight = colorScheme.primary.copy(alpha = AlphaTokens.Strong),
                glassShadow = colorScheme.scrim.copy(alpha = AlphaTokens.Prominent),
                glassAccent = vibrantAccent,
                focusRing = vibrantAccent,
                syntax = syntaxColors,
                diagnostics = diagnosticColors,
            )
        }
    } else {
        // Light Theme: Platinum frosted glass surfaces, delicate hairline borders, crisp white specular rim
        GlassColorScheme(
            glassSurface = colorScheme.surface.copy(alpha = AlphaTokens.Elevated),
            glassSurfaceElevated = colorScheme.surfaceContainerHighest.copy(alpha = AlphaTokens.PrimaryButtonTint),
            chromeSurface = colorScheme.surfaceContainer.copy(alpha = AlphaTokens.Elevated),
            canvasSurface = colorScheme.surfaceContainerLowest,
            glassTint = colorScheme.primary.copy(alpha = AlphaTokens.Subtle),
            glassTintSecondary = colorScheme.secondary.copy(alpha = AlphaTokens.Subtle),
            glassBorder = colorScheme.outline.copy(alpha = AlphaTokens.Hover),
            glassBorderHighlight = colorScheme.primary.copy(alpha = AlphaTokens.Prominent),
            onGlass = colorScheme.onSurface,
            onGlassSecondary = colorScheme.onSurfaceVariant,
            onGlassMuted = colorScheme.onSurfaceVariant.copy(alpha = AlphaTokens.TextMuted),
            glassHighlight = Color.White.copy(alpha = AlphaTokens.IconTint),
            glassShadow = colorScheme.scrim.copy(alpha = AlphaTokens.Faint),
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
        seedColor = ThemeColors.FleetBlue,
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

    /** Foreground color for glyphs/icons drawn directly on [LogoGradientEnd]'s brand badge fill. */
    val OnLogoBadge = Color.White
}

/**
 * Centralized token colors for interactive press/drag highlight overlays. These are deliberately
 * theme-agnostic additive light sources (drawn with BlendMode.Plus), not surface colors.
 */
object InteractionColors {
    val PressHighlight = Color.White
}

/**
 * Centralized theme indicator and layer token colors.
 */
object ThemeColors {
    val Blue = Color(0xFF38BDF8)
    val Dark = Color(0xFF3574F0)
    val Light = Color(0xFFFFFFFF)

    // Fleet & Linear-inspired Dark Theme Layer Architecture (Obsidian base, Charcoal panels, Elevated cards)
    val DarkLayerBase = Color(0xFF0D1117)          // Level 0: Deep Obsidian base canvas & gutters
    val DarkLayerSurface = Color(0xFF161B22)       // Level 1: Charcoal Obsidian panel / topbar / rail surface
    val DarkLayerElevated = Color(0xFF161B22)      // Level 2: Dark elevated card & dialog surface
    val DarkLayerControl = Color(0xFF21262D)       // Level 3: Gray active tab / chip / control surface
    val DarkLayerHighlight = Color(0xFF30363D)     // Hairline card border & outline
    val DarkLayerBorderSubtle = Color(0xFF21262D)  // Subtle panel divider
    val FleetBlue = Color(0xFF3574F0)              // Fleet Action & Primary Blue
    val FleetFocusGlow = Color(0xFF716BF6)         // Fleet Input & Focus Ring Glow
    val FleetCyan = Color(0xFF38BDF8)              // Fleet Cyan / Sky-blue accent

    // Frosted Light Theme Layer Architecture (blue-tinted frosted palette)
    val LightLayerBase = Color(0xFFF0F4FA)         // Light canvas & app background (blue-tinted)
    val LightLayerSurface = Color(0xFFF8FAFD)      // Light editor/content surface
    val LightLayerElevated = Color(0xFFE8F0FD)     // Light activity rail (distinctly blue-tinted)
    val LightLayerBorder = Color(0xFFD0D7DE)       // Light border & divider
    val LightLayerText = Color(0xFF1A202C)         // Light primary text
    val LightLayerAccent = Color(0xFF1570EF)       // Light cobalt-blue primary accent

    // Window control preview mini dots
    val WindowControlClose = Color(0xFFFF5F56)     // macOS Red
    val WindowControlMinimize = Color(0xFFFFBD2E)  // macOS Yellow
    val WindowControlMaximize = Color(0xFF27C93F)  // macOS Green

    // Blue Glass / AMOLED canvas layer architecture
    val BlueAmoledCanvas = Color(0xFF060C18)       // Deepest navy-black base canvas
    val BlueAmoledRail = Color(0xFF07101E)         // Activity rail (deepest sidebar)
    val BlueAmoledBorder = Color(0xFF1A2B48)       // Border & outline
    val BlueAmoledBorderMuted = Color(0xFF263D62)  // Muted divider
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
 * Centralized token colors for scenic landscape/wallpaper preview backdrops.
 */
object WallpaperColors {
    val DarkSkyTop = Color(0xFF070B10)
    val DarkSkyMiddle = Color(0xFF101720)
    val DarkSkyHorizon = Color(0xFF1E2632)
    val DarkMountainFar = Color(0xFF151D28)
    val DarkMountainNear = Color(0xFF0C1017)

    val LightSkyTop = Color(0xFFE2E8F0)
    val LightSkyMiddle = Color(0xFFCBD5E1)
    val LightSkyHorizon = Color(0xFF94A3B8)
    val LightMountainFar = Color(0xFF64748B)
    val LightMountainNear = Color(0xFF475569)
}

/**
 * Centralized token colors for theme accent color selection.
 */
object AccentPresetColors {
    val Blue = Color(0xFF2563EB)
    val Cyan = Color(0xFF06B6D4)
    val Emerald = Color(0xFF10B981)
    val Purple = Color(0xFF8B5CF6)
    val Amber = Color(0xFFF59E0B)
    val Crimson = Color(0xFFEF4444)
}

/**
 * Centralized token colors for glass layout shadows and dividers.
 */
object GlassLayoutColors {
    val PanelShadow = Color(0x26000000)
}

/**
 * Semantic token colors for Plugin Manager screens (Concepts 12, 13, 14, 15).
 */
object PluginThemeColors {
    val SelectedRowBackground = Color(0xD5195EB7)
    val SelectedRowBorder = Color(0xFF38BDF8)
    val CardBackground = Color(0xC40A1D36)
    val CardBorder = Color(0x9958A9E8)
    val HeaderBackground = Color(0xD9081930)
    val TableRowAlt = Color(0x8F07162B)
    val WarningBackground = Color(0xD12B1C09)
    val WarningBorder = Color(0xCCB96A13)
    val WarningText = Color(0xFFF59E0B)
    val WarningBody = Color(0xFFD4B088)
    val ActiveTabBackground = Color(0xD5195EB7)
    val ActiveTabBorder = Color(0xFF38BDF8)
    val StatusVerified = Color(0xFF22C55E)
    val StatusUnverified = Color(0xFFF59E0B)
}

/** Optical palette for the saturated blue studio canvas used by IDE workspaces. */
object StudioBackdropColors {
    val Base = Color(0xFF031A33)
    val PrimaryAura = Color(0xFF1677D2)
    val CyanAura = Color(0xFF38BDF8)
    val SidebarAura = Color(0xFF075FC7)
    val VioletAura = Color(0xFF4949B8)
    val Vignette = Color(0xFF010813)
    val ChromeStart = Color(0xD5195FB5)
    val ChromeCenter = Color(0xB90C427F)
    val ChromeEnd = Color(0xD2072445)
    val SidebarStart = Color(0xC9195FB5)
    val SidebarCenter = Color(0xA90C4385)
    val SidebarEnd = Color(0xC9062342)
}

/**
 * Complete color tokens for the IDE application frame, navigation, and overview.
 */
@Immutable
data class IdeShellColorScheme(
    val appBackground: Color,
    val topBarBackground: Color,
    val railBackground: Color,
    val navigatorBackground: Color,
    val contentBackground: Color,
    val statusBarBackground: Color,
    val cardBackground: Color,
    val cardBackgroundMuted: Color,
    val border: Color,
    val borderMuted: Color,
    val textMain: Color,
    val textMuted: Color,
    val textDim: Color,
    val accentGreen: Color,
    val accentGreenHover: Color,
    val accentBlue: Color,
    val accentBlueHover: Color,
    val warningAmber: Color,
    val warningAmberBg: Color,
    val errorRed: Color,
    val errorRedBg: Color,
    val focusRing: Color,
    val controlHover: Color,
    val controlSelected: Color,
    val isDark: Boolean,
)

/**
 * Predefined palette configurations for the IDE shell across Dark, Light, and Blue themes.
 */
object IdeShellTokens {
    val Dark = IdeShellColorScheme(
        appBackground = Color(0xFF0D1117),
        topBarBackground = Color(0xFF161B22),
        railBackground = Color(0xFF13181F),
        navigatorBackground = Color(0xFF161B22),
        contentBackground = Color(0xFF0D1117),
        statusBarBackground = Color(0xFF161B22),
        cardBackground = Color(0xFF161B22),
        cardBackgroundMuted = Color(0xFF0D1117),
        border = Color(0xFF30363D),
        borderMuted = Color(0xFF21262D),
        textMain = Color(0xFFF0F6FC),
        textMuted = Color(0xFFC9D1D9),
        textDim = Color(0xFF8B949E),
        accentGreen = Color(0xFF238636),
        accentGreenHover = Color(0xFF2EA043),
        accentBlue = Color(0xFF3574F0),
        accentBlueHover = Color(0xFF4A8FF5),
        warningAmber = Color(0xFFD29922),
        warningAmberBg = Color(0x26BB8009),
        errorRed = Color(0xFFF85149),
        errorRedBg = Color(0x26F85149),
        focusRing = Color(0xFF3574F0),
        controlHover = Color(0xFF21262D),
        controlSelected = Color(0xFF21262D),
        isDark = true,
    )

    val Light = IdeShellColorScheme(
        appBackground = Color(0xFFF0F4FA),
        topBarBackground = Color(0xFFFFFFFF),
        railBackground = Color(0xFFE8F0FD),
        navigatorBackground = Color(0xFFFFFFFF),
        contentBackground = Color(0xFFF8FAFD),
        statusBarBackground = Color(0xFFFFFFFF),
        cardBackground = Color(0xFFFFFFFF),
        cardBackgroundMuted = Color(0xFFF0F4FA),
        border = Color(0xFFD0D7DE),
        borderMuted = Color(0xFFAFBFD0),
        textMain = Color(0xFF1A202C),
        textMuted = Color(0xFF4A5568),
        textDim = Color(0xFF5D6A7E),
        accentGreen = Color(0xFF1F883D),
        accentGreenHover = Color(0xFF1A7F37),
        accentBlue = Color(0xFF1570EF),
        accentBlueHover = Color(0xFF1254CC),
        warningAmber = Color(0xFF9A6700),
        warningAmberBg = Color(0x26D4A72C),
        errorRed = Color(0xFFCF222E),
        errorRedBg = Color(0x26CF222E),
        focusRing = Color(0xFF1570EF),
        controlHover = Color(0xFFEBF0F9),
        controlSelected = Color(0xFFD6E8FF),
        isDark = false,
    )

    val Blue = IdeShellColorScheme(
        appBackground = Color.Transparent,
        topBarBackground = StudioBackdropColors.ChromeCenter.copy(alpha = AlphaTokens.Prominent),
        railBackground = StudioBackdropColors.SidebarStart.copy(alpha = AlphaTokens.Strong),
        navigatorBackground = StudioBackdropColors.SidebarCenter.copy(alpha = AlphaTokens.Strong),
        contentBackground = StudioBackdropColors.Base.copy(alpha = AlphaTokens.GlassElevated),
        statusBarBackground = StudioBackdropColors.ChromeEnd.copy(alpha = AlphaTokens.High),
        cardBackground = StudioBackdropColors.Base.copy(alpha = AlphaTokens.GlassTranslucent),
        cardBackgroundMuted = StudioBackdropColors.ChromeEnd.copy(alpha = AlphaTokens.Strong),
        border = ThemeColors.FleetCyan.copy(alpha = AlphaTokens.Ambient),
        borderMuted = ThemeColors.FleetCyan.copy(alpha = AlphaTokens.Glow),
        textMain = Color(0xFFEDF5FF),
        textMuted = Color(0xFFB8D0EB),
        textDim = Color(0xFF9CBCE0),
        accentGreen = Color(0xFF238636),
        accentGreenHover = Color(0xFF2EA043),
        accentBlue = Color(0xFF38BDF8),
        accentBlueHover = Color(0xFF60A5FA),
        warningAmber = Color(0xFFEAB308),
        warningAmberBg = Color(0x26EAB308),
        errorRed = Color(0xFFF85149),
        errorRedBg = Color(0x26F85149),
        focusRing = Color(0xFF38BDF8),
        controlHover = Color(0xFF162744).copy(alpha = AlphaTokens.Elevated),
        controlSelected = Color(0xFF1E3A6E).copy(alpha = AlphaTokens.Elevated),
        isDark = true,
    )
}
