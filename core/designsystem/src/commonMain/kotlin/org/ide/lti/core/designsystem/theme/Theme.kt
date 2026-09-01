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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.materialkolor.rememberDynamicColorScheme

/**
 * Composition local for the glass color scheme.
 */
val LocalGlassColors = staticCompositionLocalOf {
    DarkGlassColorScheme
}

/**
 * Composition local for the ambient shared backdrop.
 */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> {
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
 * Accessor object for glass theme values anywhere in the composable hierarchy.
 */
object GlassTheme {
    val colors: GlassColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassColors.current

    val effectsEnabled: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassEffectsEnabled.current

    val reducedMotion: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalReducedMotion.current

    val backdrop: Backdrop?
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassBackdrop.current

    val focusRingSuppressedByPointer: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusRingSuppressedByPointer.current
}

/**
 * Single unified Lti Glass Theme.
 *
 * Configures both Material 3 color & typography styling and GPU-accelerated Liquid Glass morphism.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LtiTheme(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
    glassColors: GlassColorScheme? = null,
    effectsEnabled: Boolean = true,
    reducedMotion: Boolean = false,
    materialColorScheme: ColorScheme? = null,
    sharedBackdrop: Backdrop? = null,
    content: @Composable () -> Unit,
) {
    val colorScheme = materialColorScheme ?: rememberDynamicColorScheme(
        seedColor = LtiSeedColor,
        isDark = darkTheme,
        isAmoled = darkTheme,
    )
    val resolvedGlassColors = glassColors ?: rememberDynamicGlassColorScheme(
        colorScheme = colorScheme,
        isDark = darkTheme,
    )
    val backdrop = sharedBackdrop ?: rememberLayerBackdrop()
    var focusRingSuppressedByPointer by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = appTypography(),
    ) {
        CompositionLocalProvider(
            LocalGlassColors provides resolvedGlassColors,
            LocalGlassBackdrop provides backdrop,
            LocalGlassEffectsEnabled provides effectsEnabled,
            LocalReducedMotion provides reducedMotion,
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
