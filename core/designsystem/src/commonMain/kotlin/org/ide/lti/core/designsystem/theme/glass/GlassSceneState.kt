/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop

/**
 * Opaque handle to the application's captured backdrop scene.
 *
 * This is the only backdrop-related type any module outside `core:designsystem`'s internal
 * rendering adapter (`Modifier.glass`, `GlassModifiers.kt`) may hold. It carries the real
 * `:backdrop` engine's [LayerBackdrop] but never exposes it publicly - screens and feature
 * modules thread a [GlassSceneState] through their own parameters to opt a subtree into a
 * particular captured scene (or a scene of their own, for isolated content like a dialog),
 * without ever importing `com.kashif_e.backdrop.*` themselves.
 */
@Stable
class GlassSceneState internal constructor(
    internal val engineBackdrop: LayerBackdrop,
)

/**
 * Creates and remembers a new [GlassSceneState], capturing whatever content this scene's
 * `Modifier.layerBackdrop` is later attached to.
 */
@Composable
fun rememberGlassSceneState(): GlassSceneState {
    val engineBackdrop = rememberLayerBackdrop()
    return remember(engineBackdrop) { GlassSceneState(engineBackdrop) }
}
