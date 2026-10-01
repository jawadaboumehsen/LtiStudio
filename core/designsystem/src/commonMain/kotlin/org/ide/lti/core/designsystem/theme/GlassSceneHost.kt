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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState

/**
 * Ambient [GlassSceneContext] provided by the enclosing [GlassSceneHost].
 */
val LocalGlassSceneHost = staticCompositionLocalOf<GlassSceneContext?> { null }

/**
 * Composition local specifically providing the local content [HazeState] for floating controls
 * overlapping scrolling pane content.
 */
val LocalContentHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Root host managing Haze scene state and accessibility effects policy per window.
 *
 * @param effectsEnabled When `false`, forces all descendant glass surfaces to opaque solid fallback.
 *   If an enclosing [GlassSceneHost] already disabled effects, `false` is preserved unconditionally.
 * @param wallpaperHazeState Custom [HazeState] for wallpaper capture (defaults to `rememberHazeState()`).
 * @param localContentHazeState Custom [HazeState] for local content capture (defaults to `rememberHazeState()`).
 * @param content The composable tree within this scene host.
 */
@Composable
fun GlassSceneHost(
    effectsEnabled: Boolean = true,
    wallpaperHazeState: HazeState = rememberHazeState(),
    localContentHazeState: HazeState = rememberHazeState(),
    content: @Composable () -> Unit,
) {
    val parentHost = LocalGlassSceneHost.current
    val parentEffectsEnabled = LocalGlassEffectsEnabled.current

    // Monotonic accessibility: an off parent can never be overridden to on by a child host
    val resolvedEffectsEnabled = if (parentHost != null && !parentHost.effectsEnabled) {
        false
    } else if (!parentEffectsEnabled) {
        false
    } else {
        effectsEnabled
    }

    val resolvedWallpaperState = parentHost?.wallpaperHazeState ?: wallpaperHazeState
    val resolvedLocalState = parentHost?.localContentHazeState ?: localContentHazeState

    val sceneContext = GlassSceneContext(
        wallpaperHazeState = resolvedWallpaperState,
        localContentHazeState = resolvedLocalState,
        effectsEnabled = resolvedEffectsEnabled,
    )

    CompositionLocalProvider(
        LocalGlassSceneHost provides sceneContext,
        LocalHazeState provides resolvedWallpaperState,
        LocalContentHazeState provides resolvedLocalState,
        LocalGlassEffectsEnabled provides resolvedEffectsEnabled,
    ) {
        content()
    }
}
