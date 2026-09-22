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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import org.ide.lti.core.designsystem.theme.config.AppTheme
import org.ide.lti.core.designsystem.theme.config.BackdropConfiguration
import org.ide.lti.core.designsystem.theme.config.BlueThemeConfig
import org.ide.lti.core.designsystem.theme.config.BrandingConfiguration
import org.ide.lti.core.designsystem.theme.config.PanelConfiguration
import org.ide.lti.core.designsystem.theme.config.ThemeConfiguration
import org.ide.lti.core.designsystem.theme.glass.GlassSceneState
import org.ide.lti.core.designsystem.theme.glass.rememberGlassSceneState

/**
 * Composition local for the glass color scheme.
 */
val LocalGlassColors = staticCompositionLocalOf {
    BlueGlassColorScheme
}

/**
 * Composition local for the ambient shared backdrop scene. Internal - [GlassSceneState] is the
 * only backdrop-related type any module outside `core:designsystem` may hold; read it through
 * [GlassTheme.scene] instead.
 */
internal val LocalGlassSceneState = staticCompositionLocalOf<GlassSceneState?> {
    null
}

/**
 * Composition local for whether glass effects are enabled.
 */
val LocalGlassEffectsEnabled = staticCompositionLocalOf {
    true
}

/**
 * Composition local for whether reduced motion is requested.
 */
val LocalReducedMotion = staticCompositionLocalOf {
    false
}

/**
 * Composition local tracking whether the most recent input event was a pointer press rather
 * than a keyboard event. Interactive components use this to show a keyboard focus ring only
 * for focus gained via Tab/keyboard or programmatic requestFocus() - not for focus gained by
 * clicking with the mouse - mirroring the CSS `:focus-visible` pattern.
 */
val LocalFocusRingSuppressedByPointer = staticCompositionLocalOf {
    false
}

/**
 * Composition local for whether the composition is under memory pressure (e.g. the app window is
 * minimized/backgrounded). Glass rendering releases its compiled-shader caches while `true` -
 * mirrors Haze's `onTrimMemory(MODERATE)` firing "whenever its composition lifecycle reaches
 * ON_STOP" on Desktop/Web. A host observes its own platform signal (window minimize state, etc.)
 * and passes it to [LtiTheme]'s `lowMemory` parameter, exactly like [LocalReducedMotion].
 */
val LocalGlassLowMemory = staticCompositionLocalOf {
    false
}

/**
 * Composition local for whether the dark theme is active.
 */
val LocalIsDarkTheme = staticCompositionLocalOf {
    true
}

/**
 * Composition local for the current active [AppTheme].
 */
val LocalAppTheme = staticCompositionLocalOf<AppTheme> {
    AppTheme.Blue
}

/**
 * Composition local for the current active [ThemeConfiguration].
 */
val LocalThemeConfiguration = staticCompositionLocalOf {
    BlueThemeConfig
}

/**
 * Accessor object for glass theme values anywhere in the composable hierarchy.
 */
object GlassTheme {
    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalIsDarkTheme.current

    val appTheme: AppTheme
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTheme.current

    val configuration: ThemeConfiguration
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeConfiguration.current

    val panel: PanelConfiguration
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeConfiguration.current.panelConfig

    val backdropConfig: BackdropConfiguration
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeConfiguration.current.backdropConfig

    val branding: BrandingConfiguration
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeConfiguration.current.brandingConfig

    val colors: GlassColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassColors.current

    val ideShellColors: IdeShellColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeConfiguration.current.ideShellColorScheme

    /** Code editor token palette - not an M3 role, stays as its own non-M3 addition. */
    val syntaxColors: SyntaxColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeConfiguration.current.syntaxColorScheme

    /** Diagnostic/git-status semantic tokens (error/warning/success/info/...) - not M3 roles. */
    val diagnosticColors: DiagnosticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeConfiguration.current.diagnosticColors

    val effectsEnabled: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassEffectsEnabled.current

    val reducedMotion: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalReducedMotion.current

    val scene: GlassSceneState?
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassSceneState.current

    val focusRingSuppressedByPointer: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusRingSuppressedByPointer.current

    val accessibility: GlassAccessibilitySettings
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassAccessibilitySettings.current

    val lowMemory: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassLowMemory.current

    /** Centralized registry of all backdrop visual effect stacks. */
    val effects: GlassEffects
        get() = GlassEffects

    /** Centralized registry of all glass specular highlight strategies. */
    val highlights: GlassHighlights
        get() = GlassHighlights

    /** Centralized registry of all glass ambient shadow presets. */
    val shadows: GlassShadows
        get() = GlassShadows

    /** Centralized registry of all glass squircle and capsule shapes. */
    val shapes: GlassShapes
        get() = GlassShapes

    /** Centralized design system typography. */
    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography
}

/**
 * Single unified Lti Glass Theme.
 *
 * Configures both Material 3 color & typography styling and GPU-accelerated Liquid Glass morphism
 * directly from the polymorphic [ThemeConfiguration].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LtiTheme(
    modifier: Modifier = Modifier,
    appTheme: AppTheme? = null,
    darkTheme: Boolean = true,
    glassColors: GlassColorScheme? = null,
    effectsEnabled: Boolean = true,
    reducedMotion: Boolean = false,
    accessibility: GlassAccessibilitySettings = GlassAccessibilitySettings(),
    lowMemory: Boolean = false,
    materialColorScheme: ColorScheme? = null,
    sharedScene: GlassSceneState? = null,
    content: @Composable () -> Unit,
) {
    val effectiveAppTheme = appTheme ?: if (darkTheme) AppTheme.Blue else AppTheme.Light
    val themeConfig = effectiveAppTheme.configuration

    val resolvedMaterialScheme = materialColorScheme ?: themeConfig.materialColorScheme
    val resolvedGlassColors = glassColors ?: themeConfig.glassColorScheme
    val scene = sharedScene ?: rememberGlassSceneState()
    var focusRingSuppressedByPointer by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = resolvedMaterialScheme,
        typography = appTypography(),
    ) {
        CompositionLocalProvider(
            LocalIsDarkTheme provides themeConfig.isDark,
            LocalAppTheme provides effectiveAppTheme,
            LocalThemeConfiguration provides themeConfig,
            LocalGlassColors provides resolvedGlassColors,
            LocalGlassSceneState provides scene,
            LocalGlassEffectsEnabled provides effectsEnabled,
            LocalReducedMotion provides reducedMotion,
            LocalGlassAccessibilitySettings provides accessibility,
            LocalGlassLowMemory provides lowMemory,
            LocalContentColor provides resolvedGlassColors.onGlass,
            LocalFocusRingSuppressedByPointer provides focusRingSuppressedByPointer,
        ) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .onPointerEvent(PointerEventType.Press, pass = PointerEventPass.Initial) {
                        focusRingSuppressedByPointer = true
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            focusRingSuppressedByPointer = false
                        }
                        false
                    },
            ) {
                content()
            }
        }
    }
}

/**
 * Helper to override glass colors for a subtree.
 */
@Composable
fun ProvideGlassColors(
    colors: GlassColorScheme,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalGlassColors provides colors,
        LocalContentColor provides colors.onGlass,
        content = content,
    )
}
