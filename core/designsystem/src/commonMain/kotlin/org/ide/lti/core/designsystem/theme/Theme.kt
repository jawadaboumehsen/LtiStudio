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

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState

/** The three application themes - official pattern's `darkTheme: Boolean`, generalized to three. */
enum class AppTheme {
    Light, Dark, Blue;

    /** Persisted theme id (`AppSettingsState.THEME_*`) for this theme. */
    val id: String
        get() = when (this) {
            Light -> "light"
            Dark -> "dark"
            Blue -> "blue"
        }

    companion object {
        /** All themes, in display order. */
        val allThemes: List<AppTheme> = listOf(Blue, Dark, Light)

        /** Resolves the persisted theme id (`AppSettingsState.THEME_*`) to an [AppTheme], defaulting to [Blue]. */
        fun fromId(id: String): AppTheme = when (id) {
            "light" -> Light
            "dark" -> Dark
            else -> Blue
        }
    }
}

/** Composition local for the current active [AppTheme]. */
val LocalAppTheme = staticCompositionLocalOf { AppTheme.Blue }

/** The real [ColorScheme] a given [AppTheme] resolves to - lets callers preview a theme other than the active one. */
fun colorSchemeFor(theme: AppTheme): ColorScheme = when (theme) {
    AppTheme.Light -> LightColorScheme
    AppTheme.Dark -> DarkColorScheme
    AppTheme.Blue -> BlueColorScheme
}

/**
 * Composition local for the single shared [HazeState] every glass surface samples from - one
 * backdrop capture per window, same sharing pattern the old `:backdrop` module's
 * `LocalGlassSceneState` used. `rememberHazeState()` itself is a plain `remember { HazeState() }`
 * with no built-in sharing mechanism (Haze's own samples just pass it as a parameter); this
 * CompositionLocal is our app's choice of how to share the one instance [LtiTheme] creates.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * M3 [Shapes] built from [GlassShapes]' corner radius tokens (same dp values, plain
 * [RoundedCornerShape] instead of [GlassShapes]' squircle [com.kyant.capsule.ContinuousRoundedRectangle] -
 * M3's `Shapes` constructor requires `CornerBasedShape`, which the squircle shapes don't extend).
 * Components that want the real continuous-curvature look use `GlassShapes.X` directly instead of
 * `MaterialTheme.shapes`.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(CornerRadius.ExtraSmall),
    small = RoundedCornerShape(CornerRadius.Small),
    medium = RoundedCornerShape(CornerRadius.Medium),
    large = RoundedCornerShape(CornerRadius.Large),
    extraLarge = RoundedCornerShape(CornerRadius.ExtraLarge),
)

/** Accessor object for design system theme values anywhere in the composable hierarchy. */
object GlassTheme {
    val appTheme: AppTheme
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTheme.current

    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = appTheme != AppTheme.Light

    /** Code editor token palette - not an M3 role, stays as its own non-M3 addition. */
    val syntaxColors: SyntaxColorScheme
        @Composable
        @ReadOnlyComposable
        get() = when (appTheme) {
            AppTheme.Light -> LightSyntaxColors
            AppTheme.Dark -> DarkSyntaxColors
            AppTheme.Blue -> BlueSyntaxColors
        }

    /** Diagnostic/git-status semantic tokens (error/warning/success/info/...) - not M3 roles. */
    val diagnosticColors: DiagnosticColors
        @Composable
        @ReadOnlyComposable
        get() = when (appTheme) {
            AppTheme.Light -> LightDiagnosticColors
            AppTheme.Dark -> DarkDiagnosticColors
            AppTheme.Blue -> BlueDiagnosticColors
        }

    /** Centralized registry of all glass squircle and capsule shapes. */
    val shapes: GlassShapes
        get() = GlassShapes

    /** Centralized design system typography. */
    val typography
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography

    /** The single shared [HazeState] every glass surface samples from - see [LocalHazeState]. */
    val hazeState: HazeState
        @Composable
        @ReadOnlyComposable
        get() = requireNotNull(LocalHazeState.current) {
            "GlassTheme.hazeState read outside LtiTheme - wrap the composition in LtiTheme { }"
        }
}

/**
 * Single unified Lti application theme - official M3 pattern
 * (developer.android.com/develop/ui/compose/designsystems/material3), generalized from a boolean
 * `darkTheme` to the three-way [AppTheme]: switch color scheme, feed [MaterialTheme] color scheme
 * + typography + shapes, done.
 */
@Composable
fun LtiTheme(
    modifier: Modifier = Modifier,
    appTheme: AppTheme = AppTheme.Blue,
    content: @Composable () -> Unit,
) {
    val colorScheme = colorSchemeFor(appTheme)
    val hazeState = rememberHazeState()

    CompositionLocalProvider(
        LocalAppTheme provides appTheme,
        LocalHazeState provides hazeState,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = appTypography(),
            shapes = AppShapes,
            content = content,
        )
    }
}
