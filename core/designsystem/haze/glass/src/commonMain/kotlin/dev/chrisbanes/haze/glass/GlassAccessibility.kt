/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package dev.chrisbanes.haze.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import dev.chrisbanes.haze.ExperimentalHazeApi

/**
 * Semantic accessibility preferences that change how Glass separates itself from its content.
 *
 * Haze deliberately does not read platform accessibility services from common code. Hosts map
 * their platform settings to this value and provide it to the subtree that renders Glass.
 */
@ExperimentalHazeApi
@Immutable
public data class GlassAccessibilitySettings(
    /** Reduces the resolved blur radius used by Glass materials. */
    val reduceTransparency: Boolean = false,
    /** Enforces minimum tone contrast and edge separation. */
    val increaseContrast: Boolean = false,
    /** Enforces a visible edge without changing tone contrast. */
    val showBorders: Boolean = false,
)

/** Accessibility preferences inherited by Glass nodes in this composition subtree. */
@ExperimentalHazeApi
public val LocalGlassAccessibilitySettings: ProvidableCompositionLocal<GlassAccessibilitySettings> =
    compositionLocalOf { GlassAccessibilitySettings() }
