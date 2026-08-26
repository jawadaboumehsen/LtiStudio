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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop

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

    val backdrop: Backdrop?
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassBackdrop.current
}

/**
 * Single unified Lti Glass Theme.
 *
 * Configures both Material 3 color & typography styling and GPU-accelerated Liquid Glass morphism.
 */
@Composable
fun LtiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    glassColors: GlassColorScheme = if (darkTheme) DarkGlassColorScheme else LightGlassColorScheme,
    effectsEnabled: Boolean = true,
    materialColorScheme: ColorScheme = if (darkTheme) darkScheme else lightScheme,
    sharedBackdrop: Backdrop? = null,
    content: @Composable () -> Unit,
) {
    val backdrop = sharedBackdrop ?: rememberLayerBackdrop()

    MaterialTheme(
        colorScheme = materialColorScheme,
        typography = appTypography(),
    ) {
        CompositionLocalProvider(
            LocalGlassColors provides glassColors,
            LocalGlassBackdrop provides backdrop,
            LocalGlassEffectsEnabled provides effectsEnabled,
            content = content
        )
    }
}

/**
 * Helper to override glass colors for a subtree.
 */
@Composable
fun ProvideGlassColors(
    colors: GlassColorScheme,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalGlassColors provides colors,
        content = content
    )
}