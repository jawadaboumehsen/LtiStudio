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

import androidx.compose.runtime.Immutable
import dev.chrisbanes.haze.HazeState

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
