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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState

/**
 * Encapsulates the per-window Haze scene state and effects policy.
 *
 * Separates scene ownership (Haze state lifetime, source management, monotonic accessibility)
 * from theme styling (colors, typography, shapes).
 *
 * Architecture:
 * 1. [wallpaperHazeState]: Captures the full-window [org.ide.lti.core.designsystem.component.layout.GlassBackdrop].
 *    Sampled by shell chrome (TopBar, PipelineRail, Navigator, Breadcrumb, StatusBar) and text-bearing cards.
 * 2. [localContentHazeState]: Captures local scrolling/pane content behind floating controls or sheets.
 * 3. [effectsEnabled]: Root-level switch for glass effects. Monotonically inherited: if an ancestor host
 *    disables effects (e.g. system reduce-transparency), no child can re-enable it.
 */
@Immutable
data class GlassSceneContext(
    val wallpaperHazeState: HazeState,
    val localContentHazeState: HazeState,
    val effectsEnabled: Boolean,
)

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
