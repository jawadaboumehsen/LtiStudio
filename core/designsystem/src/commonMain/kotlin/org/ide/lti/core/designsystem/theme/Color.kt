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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Light theme color literals - official Material3 pattern
 * (developer.android.com/develop/ui/compose/designsystems/material3): flat `Color(0x..)`
 * constants feeding a `lightColorScheme()`/`darkColorScheme()` builder below, no dynamic
 * generation. Frosted, blue-tinted light palette.
 */
private val md_theme_light_primary = Color(0xFF1570EF)
private val md_theme_light_onPrimary = Color(0xFFFFFFFF)
private val md_theme_light_primaryContainer = Color(0xFFD1E9FF)
private val md_theme_light_onPrimaryContainer = Color(0xFF0040C1)
private val md_theme_light_secondary = Color(0xFF1F883D)
private val md_theme_light_onSecondary = Color(0xFFFFFFFF)
private val md_theme_light_secondaryContainer = Color(0xFFE2E8F0)
private val md_theme_light_onSecondaryContainer = Color(0xFF1A202C)
private val md_theme_light_tertiary = Color(0xFF0E7090)
private val md_theme_light_onTertiary = Color(0xFFFFFFFF)
private val md_theme_light_tertiaryContainer = Color(0xFFCFFAFE)
private val md_theme_light_onTertiaryContainer = Color(0xFF164E63)
private val md_theme_light_background = Color(0xFFF0F4FA)
private val md_theme_light_onBackground = Color(0xFF1A202C)
private val md_theme_light_surface = Color(0xFFF8FAFD)
private val md_theme_light_onSurface = Color(0xFF1A202C)
private val md_theme_light_surfaceVariant = Color(0xFFE8F0FD)
private val md_theme_light_onSurfaceVariant = Color(0xFF4A5568)
private val md_theme_light_outline = Color(0xFFD0D7DE)
private val md_theme_light_outlineVariant = Color(0xFFAFBFD0)
private val md_theme_light_error = Color(0xFFA6101D)
private val md_theme_light_onError = Color(0xFFFFFFFF)
private val md_theme_light_errorContainer = Color(0xFFFEE2E2)
private val md_theme_light_onErrorContainer = Color(0xFF991B1B)
private val md_theme_light_inversePrimary = Color(0xFF84CAFF)
private val md_theme_light_inverseSurface = Color(0xFF161B22)
private val md_theme_light_inverseOnSurface = Color(0xFFF3F4F6)
private val md_theme_light_scrim = Color(0xFF000000)
private val md_theme_light_surfaceContainerLowest = Color(0xFFFFFFFF)
private val md_theme_light_surfaceContainerLow = Color(0xFFF8FAFD)
private val md_theme_light_surfaceContainer = Color(0xFFE8F0FD)
private val md_theme_light_surfaceContainerHigh = Color(0xFFFFFFFF)
private val md_theme_light_surfaceContainerHighest = Color(0xFFD6E8FF)

/** Dark theme color literals - Fleet-inspired neutral obsidian palette. */
private val md_theme_dark_primary = Color(0xFF3574F0)
private val md_theme_dark_onPrimary = Color(0xFFFFFFFF)
private val md_theme_dark_primaryContainer = Color(0xFF1B2F52)
private val md_theme_dark_onPrimaryContainer = Color(0xFFD1E4FF)
private val md_theme_dark_secondary = Color(0xFF21262D)
private val md_theme_dark_onSecondary = Color(0xFFF1F5F9)
private val md_theme_dark_secondaryContainer = Color(0xFF2D333D)
private val md_theme_dark_onSecondaryContainer = Color(0xFFF1F5F9)
private val md_theme_dark_tertiary = Color(0xFF38BDF8)
private val md_theme_dark_onTertiary = Color(0xFF0C1322)
private val md_theme_dark_tertiaryContainer = Color(0xFF0F2E4D)
private val md_theme_dark_onTertiaryContainer = Color(0xFFBAE6FD)
private val md_theme_dark_background = Color(0xFF0D1117)
private val md_theme_dark_onBackground = Color(0xFFF3F4F6)
private val md_theme_dark_surface = Color(0xFF161B22)
private val md_theme_dark_onSurface = Color(0xFFF3F4F6)
private val md_theme_dark_surfaceVariant = Color(0xFF21262D)
private val md_theme_dark_onSurfaceVariant = Color(0xFF969DAA)
private val md_theme_dark_outline = Color(0xFF30363D)
private val md_theme_dark_outlineVariant = Color(0xFF21262D)
private val md_theme_dark_error = Color(0xFFFFA49E)
private val md_theme_dark_onError = Color(0xFF0D1117)
private val md_theme_dark_errorContainer = Color(0xFF3B1818)
private val md_theme_dark_onErrorContainer = Color(0xFFFCA5A5)
private val md_theme_dark_inversePrimary = Color(0xFF3574F0)
private val md_theme_dark_inverseSurface = Color(0xFFF3F4F6)
private val md_theme_dark_inverseOnSurface = Color(0xFF161B22)
private val md_theme_dark_scrim = Color(0xFF000000)
private val md_theme_dark_surfaceContainerLowest = Color(0xFF0D1117)
private val md_theme_dark_surfaceContainerLow = Color(0xFF161B22)
private val md_theme_dark_surfaceContainer = Color(0xFF262C36)
private val md_theme_dark_surfaceContainerHigh = Color(0xFF2D333D)
private val md_theme_dark_surfaceContainerHighest = Color(0xFF363D48)

/** Blue theme color literals - signature studio sapphire and midnight obsidian palette. */
private val md_theme_blue_primary = Color(0xFF3B82F6)
private val md_theme_blue_onPrimary = Color(0xFFFFFFFF)
private val md_theme_blue_primaryContainer = Color(0xFF162D5A)
private val md_theme_blue_onPrimaryContainer = Color(0xFFD6E4FF)
private val md_theme_blue_secondary = Color(0xFF38BDF8)
private val md_theme_blue_onSecondary = Color(0xFF060B14)
private val md_theme_blue_secondaryContainer = Color(0xFF16233B)
private val md_theme_blue_onSecondaryContainer = Color(0xFFF1F5F9)
private val md_theme_blue_tertiary = Color(0xFF06B6D4)
private val md_theme_blue_onTertiary = Color(0xFF060B14)
private val md_theme_blue_tertiaryContainer = Color(0xFF083344)
private val md_theme_blue_onTertiaryContainer = Color(0xFFCFFAFE)
private val md_theme_blue_background = Color(0xFF060B14)
private val md_theme_blue_onBackground = Color(0xFFF1F5F9)
private val md_theme_blue_surface = Color(0xFF080E1A)
private val md_theme_blue_onSurface = Color(0xFFF1F5F9)
private val md_theme_blue_surfaceVariant = Color(0xFF121B2C)
private val md_theme_blue_onSurfaceVariant = Color(0xFF94A9C9)
private val md_theme_blue_outline = Color(0xFF223554)
private val md_theme_blue_outlineVariant = Color(0xFF2C436B)
private val md_theme_blue_error = Color(0xFFFFA49E)
private val md_theme_blue_onError = Color(0xFF060B14)
private val md_theme_blue_errorContainer = Color(0xFF3B1818)
private val md_theme_blue_onErrorContainer = Color(0xFFFCA5A5)
private val md_theme_blue_inversePrimary = Color(0xFF3B82F6)
private val md_theme_blue_inverseSurface = Color(0xFFF1F5F9)
private val md_theme_blue_inverseOnSurface = Color(0xFF080E1A)
private val md_theme_blue_scrim = Color(0xFF000000)
private val md_theme_blue_surfaceContainerLowest = Color(0xFF060B14)
private val md_theme_blue_surfaceContainerLow = Color(0xFF0C1322)
private val md_theme_blue_surfaceContainer = Color(0xFF10192B)
private val md_theme_blue_surfaceContainerHigh = Color(0xFF16233B)
private val md_theme_blue_surfaceContainerHighest = Color(0xFF1F3050)

/** Official-pattern M3 color scheme for the Light theme. */
internal val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    outlineVariant = md_theme_light_outlineVariant,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    inversePrimary = md_theme_light_inversePrimary,
    inverseSurface = md_theme_light_inverseSurface,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    scrim = md_theme_light_scrim,
    surfaceContainerLowest = md_theme_light_surfaceContainerLowest,
    surfaceContainerLow = md_theme_light_surfaceContainerLow,
    surfaceContainer = md_theme_light_surfaceContainer,
    surfaceContainerHigh = md_theme_light_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_light_surfaceContainerHighest,
)

/** Official-pattern M3 color scheme for the Dark theme. */
internal val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    outlineVariant = md_theme_dark_outlineVariant,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    inversePrimary = md_theme_dark_inversePrimary,
    inverseSurface = md_theme_dark_inverseSurface,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    scrim = md_theme_dark_scrim,
    surfaceContainerLowest = md_theme_dark_surfaceContainerLowest,
    surfaceContainerLow = md_theme_dark_surfaceContainerLow,
    surfaceContainer = md_theme_dark_surfaceContainer,
    surfaceContainerHigh = md_theme_dark_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_dark_surfaceContainerHighest,
)

/** Official-pattern M3 color scheme for the signature AMOLED Blue theme. */
internal val BlueColorScheme = darkColorScheme(
    primary = md_theme_blue_primary,
    onPrimary = md_theme_blue_onPrimary,
    primaryContainer = md_theme_blue_primaryContainer,
    onPrimaryContainer = md_theme_blue_onPrimaryContainer,
    secondary = md_theme_blue_secondary,
    onSecondary = md_theme_blue_onSecondary,
    secondaryContainer = md_theme_blue_secondaryContainer,
    onSecondaryContainer = md_theme_blue_onSecondaryContainer,
    tertiary = md_theme_blue_tertiary,
    onTertiary = md_theme_blue_onTertiary,
    tertiaryContainer = md_theme_blue_tertiaryContainer,
    onTertiaryContainer = md_theme_blue_onTertiaryContainer,
    background = md_theme_blue_background,
    onBackground = md_theme_blue_onBackground,
    surface = md_theme_blue_surface,
    onSurface = md_theme_blue_onSurface,
    surfaceVariant = md_theme_blue_surfaceVariant,
    onSurfaceVariant = md_theme_blue_onSurfaceVariant,
    outline = md_theme_blue_outline,
    outlineVariant = md_theme_blue_outlineVariant,
    error = md_theme_blue_error,
    onError = md_theme_blue_onError,
    errorContainer = md_theme_blue_errorContainer,
    onErrorContainer = md_theme_blue_onErrorContainer,
    inversePrimary = md_theme_blue_inversePrimary,
    inverseSurface = md_theme_blue_inverseSurface,
    inverseOnSurface = md_theme_blue_inverseOnSurface,
    scrim = md_theme_blue_scrim,
    surfaceContainerLowest = md_theme_blue_surfaceContainerLowest,
    surfaceContainerLow = md_theme_blue_surfaceContainerLow,
    surfaceContainer = md_theme_blue_surfaceContainer,
    surfaceContainerHigh = md_theme_blue_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_blue_surfaceContainerHighest,
)

/**
 * Syntax highlighting color scheme for code editor tokens - not an M3 role, kept as its own
 * flat, literal, non-M3 addition (same reasoning as [DiagnosticColors]).
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

internal val LightSyntaxColors = SyntaxColorScheme(
    keyword = Color(0xFFB1720A),
    function = Color(0xFF9333A3),
    string = Color(0xFF1A7F37),
    number = Color(0xFF1F5FBF),
    comment = Color(0xFF6E7781),
    type = Color(0xFF0E7C86),
    annotation = Color(0xFF8A6D00),
    plain = md_theme_light_onSurface,
)

internal val DarkSyntaxColors = SyntaxColorScheme(
    keyword = Color(0xFFE8A657),
    function = Color(0xFFD8A6F0),
    string = Color(0xFF8FD19E),
    number = Color(0xFF8FC2F0),
    comment = Color(0xFF8B949E),
    type = Color(0xFF7EE0D6),
    annotation = Color(0xFFE0D68F),
    plain = md_theme_dark_onSurface,
)

internal val BlueSyntaxColors = SyntaxColorScheme(
    keyword = Color(0xFFE8A657),
    function = Color(0xFFD8A6F0),
    string = Color(0xFF8FD19E),
    number = Color(0xFF8FC2F0),
    comment = Color(0xFF9CBCE0),
    type = Color(0xFF7EE0D6),
    annotation = Color(0xFFE0D68F),
    plain = md_theme_blue_onSurface,
)

/**
 * Semantic diagnostic and git-status color tokens - not an M3 role, kept as its own flat,
 * literal, non-M3 addition.
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

internal val LightDiagnosticColors = DiagnosticColors(
    error = Color(0xFFCF222E),
    warning = Color(0xFF9A6700),
    success = Color(0xFF1F883D),
    info = Color(0xFF1570EF),
    gitModified = Color(0xFF9A6700),
    gitAdded = Color(0xFF1F883D),
    gitDeleted = Color(0xFFCF222E),
)

internal val DarkDiagnosticColors = DiagnosticColors(
    error = Color(0xFFF85149),
    warning = Color(0xFFD29922),
    success = Color(0xFF3FB950),
    info = Color(0xFF3574F0),
    gitModified = Color(0xFFD29922),
    gitAdded = Color(0xFF3FB950),
    gitDeleted = Color(0xFFF85149),
)

internal val BlueDiagnosticColors = DiagnosticColors(
    error = Color(0xFFF85149),
    warning = Color(0xFFEAB308),
    success = Color(0xFF22C55E),
    info = Color(0xFF38BDF8),
    gitModified = Color(0xFFEAB308),
    gitAdded = Color(0xFF22C55E),
    gitDeleted = Color(0xFFF85149),
)

/** Brand seed - logo badge gradient end color, matches the Blue theme's primary. */
val BrandLogoGradientEnd = Color(0xFF38BDF8)

/** Centralized token colors for brand assets and logos. */
object BrandColors {
    /** Foreground color for glyphs/icons drawn directly on the brand badge fill. */
    val OnLogoBadge = Color.White
}

/** Centralized token colors for window control buttons (e.g. native Windows close hover). */
object WindowControlColors {
    val CloseHover = Color(0xFFE81123)
}

/** Centralized token colors for cross-component press/drag interaction affordances. */
object InteractionColors {
    /** Additive press-highlight overlay drawn under [BlendMode.Plus][androidx.compose.ui.graphics.BlendMode.Plus]. */
    val PressHighlight = Color.White
}

/** Sample tint colors for design-system previews only (not real UI chrome). */
object PreviewSampleColors {
    val Blue = Color(0xFF2196F3)
    val Purple = Color(0xFF9C27B0)
    val Orange = Color(0xFFFF9800)
    val Green = Color(0xFF4CAF50)
}

/** Centralized token colors for the multi-panel [org.ide.lti.core.designsystem.component.layout.GlassLayout] shell. */
object GlassLayoutColors {
    val PanelShadow = Color(0x26000000)
}

/** Centralized token colors for fluid glass controls (toggles, sliders, inputs). */
object GlassControlColors {
    /** Bevel inner shadow for 3D fluid controls (15% black). */
    val InnerShadow = Color(0x26000000)

    /** Inactive track fill in dark theme (36% opacity neutral). */
    val TrackInactiveDark = Color(0x5C787880)

    /** Inactive track fill in light theme (20% opacity neutral). */
    val TrackInactiveLight = Color(0x33787878)

    /** Resting fill color for glass toggle thumb. */
    val ThumbResting = Color.White
}

/** Centralized token colors for theme accent color selection. */
object AccentPresetColors {
    val Blue = Color(0xFF2563EB)
    val Cyan = Color(0xFF06B6D4)
    val Emerald = Color(0xFF10B981)
    val Purple = Color(0xFF8B5CF6)
    val Amber = Color(0xFFF59E0B)
    val Crimson = Color(0xFFEF4444)
}

/** Illustrative sky/mountain gradient colors for the settings theme-picker's scenic wallpaper preview. */
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

/** Multi-spectral cosmic sapphire aura colors for the Blue theme backdrop canvas. */
object StudioBackdropColors {
    val Base = Color(0xFF060B14)
    val PrimaryAura = Color(0xFF1D4ED8)
    val PrimaryAuraSubtle = Color(0xFF1E3A8A)
    val CyanAura = Color(0xFF0891B2)
    val CyanAuraSubtle = Color(0xFF0E7490)
    val SidebarAura = Color(0xFF2563EB)
    val VioletAura = Color(0xFF6366F1)
    val AmberAura = Color(0xFF92400E)
    val Vignette = Color(0xFF020408)
    val SidebarStart = Color(0xC9195FB5)
    val SidebarCenter = Color(0xA90C4385)
    val SidebarEnd = Color(0xC9062342)
}

/** Multi-depth cosmic celestial aura colors for the Dark theme backdrop canvas. */
object DarkBackdropColors {
    val Base = Color(0xFF090B10)
    val IndigoAura = Color(0xFF3B4874)
    val IndigoAuraSubtle = Color(0xFF222B45)
    val CyanAura = Color(0xFF0E4E63)
    val CyanAuraSubtle = Color(0xFF0A3140)
    val AmethystAura = Color(0xFF4C2A66)
    val AmberAura = Color(0xFF5A3D1E)
    val Vignette = Color(0xFF040508)
}

/** Soft prismatic bloom colors for the Light theme backdrop canvas. */
object LightBackdropColors {
    val Base = Color(0xFFF5F7FA)
    val SkyBloom = Color(0xFFBAE6FD)
    val SkyBloomSubtle = Color(0xFFE0F2FE)
    val LilacBloom = Color(0xFFE9D5FF)
    val LilacBloomSubtle = Color(0xFFF3E8FF)
    val PeachBloom = Color(0xFFFFEDD5)
    val Vignette = Color(0xFFE2E8F0)
}

/** Centralized token colors for the plugin manager's cards, tables, tabs, and status chips. */
object PluginThemeColors {
    val SelectedRowBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Faded)

    val SelectedRowBorder: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary

    val CardBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerHigh

    val CardBorder: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.outline

    val HeaderBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerHigh

    val TableRowAlt: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainer

    val WarningBackground: Color
        @Composable
        get() = GlassTheme.diagnosticColors.warning.copy(alpha = AlphaTokens.Subtle)

    val WarningBorder: Color
        @Composable
        get() = GlassTheme.diagnosticColors.warning

    val WarningText: Color
        @Composable
        get() = GlassTheme.diagnosticColors.warning

    val WarningBody: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurfaceVariant

    val ActiveTabBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Faded)

    val ActiveTabBorder: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.outline

    val StatusVerified: Color
        @Composable
        get() = GlassTheme.diagnosticColors.success

    val StatusUnverified: Color
        @Composable
        get() = GlassTheme.diagnosticColors.warning
}
