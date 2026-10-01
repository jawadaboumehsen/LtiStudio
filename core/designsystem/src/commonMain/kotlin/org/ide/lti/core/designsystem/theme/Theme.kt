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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.LocalGlassAccessibilitySettings
import dev.chrisbanes.haze.rememberHazeState

/** The three application themes - official pattern's `darkTheme: Boolean`, generalized to three. */
enum class AppTheme {
    Light,
    Dark,
    Blue,
    ;

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
        fun fromId(id: String?): AppTheme = when (id) {
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
 * Composition local for the [GlassReducedMotionPolicy] that interactive glass surfaces should use.
 * Haze does not ship a `LocalGlassReducedMotionPolicy`, so we own it here and thread it through
 * [LtiTheme] based on the user's "Reduce motion" accessibility setting.
 *
 * Default is [GlassReducedMotionPolicy.System] — honour the OS-level preference.
 */
val LocalGlassReducedMotionPolicy = compositionLocalOf { GlassReducedMotionPolicy.System }

/**
 * Composition local controlling whether glass rendering effects are enabled app-wide.
 * When `false`, all glass components fall back to solid, opaque surfaces and solid borders.
 */
val LocalGlassEffectsEnabled = compositionLocalOf { true }

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

    /** The single shared [HazeState], or null if read outside [LtiTheme]. */
    val hazeStateOrNull: HazeState?
        @Composable
        @ReadOnlyComposable
        get() = LocalHazeState.current

    /**
     * The [GlassReducedMotionPolicy] that all interactive glass surfaces should pass to
     * `hazeGlass(interactionReducedMotionPolicy = …)`.  Driven by the user's "Reduce motion"
     * preference via [LtiTheme] → [LocalGlassReducedMotionPolicy].
     */
    val reducedMotionPolicy: GlassReducedMotionPolicy
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassReducedMotionPolicy.current

    /**
     * Optional local content [HazeState] for floating controls or sheets overlapping scrolling content.
     */
    val localContentHazeState: HazeState?
        @Composable
        @ReadOnlyComposable
        get() = LocalContentHazeState.current

    /**
     * Whether fluid glass effects are currently enabled app-wide.
     * When `false`, all glass surfaces render flat solid fills instead of `hazeGlass`.
     */
    val effectsEnabled: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassEffectsEnabled.current
}

/**
 * Single unified Lti application theme - official M3 pattern
 * (developer.android.com/develop/ui/compose/designsystems/material3), generalized from a boolean
 * `darkTheme` to the three-way [AppTheme]: switch color scheme, feed [MaterialTheme] color scheme
 * + typography + shapes, done.
 *
 * @param effectsEnabled When `false`, all design-system glass surfaces render flat solid fills,
 *   fulfilling the user-facing "Reduce transparency: Use opaque surfaces" setting, while also
 *   updating [LocalGlassAccessibilitySettings] with `reduceTransparency = true`. When `null`,
 *   inherits [LocalGlassEffectsEnabled] from an enclosing [GlassSceneHost] or [LtiTheme], or defaults
 *   to `true` (effects enabled). Monotonically constrained: if an enclosing host disabled effects,
 *   a child theme cannot override it to `true`.
 * @param reducedMotion When `true`, interactive glass surfaces use
 *   [GlassReducedMotionPolicy.Reduced] instead of deferring to the OS preference. When `null`,
 *   inherits the ambient [LocalGlassReducedMotionPolicy] from an enclosing [LtiTheme], or defaults
 *   to `false` (system motion policy) if unprovided.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun LtiTheme(
    appTheme: AppTheme = AppTheme.Blue,
    effectsEnabled: Boolean? = null,
    reducedMotion: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val colorScheme = colorSchemeFor(appTheme)
    val parentSceneHost = LocalGlassSceneHost.current
    val existingHazeState = LocalHazeState.current
    val hazeState = existingHazeState ?: rememberHazeState()

    val parentEffectsEnabled = parentSceneHost?.effectsEnabled ?: LocalGlassEffectsEnabled.current
    // Monotonic accessibility: an off parent can never be overridden to on by a nested theme
    val resolvedEffectsEnabled = if (!parentEffectsEnabled) {
        false
    } else {
        effectsEnabled ?: true
    }

    val parentMotionPolicy = LocalGlassReducedMotionPolicy.current
    val resolvedMotionPolicy = when {
        reducedMotion != null -> if (reducedMotion) {
            GlassReducedMotionPolicy.Reduced
        } else {
            GlassReducedMotionPolicy.System
        }
        else -> parentMotionPolicy
    }

    val currentAccessibilitySettings = LocalGlassAccessibilitySettings.current
    val accessibilitySettings = if (effectsEnabled != null || currentAccessibilitySettings.reduceTransparency) {
        currentAccessibilitySettings.copy(
            reduceTransparency = currentAccessibilitySettings.reduceTransparency || !resolvedEffectsEnabled,
        )
    } else {
        currentAccessibilitySettings
    }

    CompositionLocalProvider(
        LocalAppTheme provides appTheme,
        LocalHazeState provides hazeState,
        LocalGlassEffectsEnabled provides resolvedEffectsEnabled,
        LocalGlassAccessibilitySettings provides accessibilitySettings,
        LocalGlassReducedMotionPolicy provides resolvedMotionPolicy,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = appTypography(),
            shapes = AppShapes,
            content = content,
        )
    }
}
