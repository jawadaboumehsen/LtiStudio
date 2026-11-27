/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.Poko

/**
 * Semantic accessibility preferences that change how Glass separates itself from its content.
 *
 * Haze deliberately does not read platform accessibility services from common code. Hosts map
 * their platform settings to this value and provide it to the subtree that renders Glass.
 */
@Immutable
@Poko
class GlassAccessibilitySettings(
    /**
     * Makes Glass materials more opaque by fully diffusing a stronger blur, so content behind them
     * is less legible. Fallback rendering uses an opaque base that follows the system appearance
     * supplied by the Compose host, not `MaterialTheme` or other in-app theme state. Set
     * [reducedTransparencyFallbackColor] to match an in-app theme.
     */
    val reduceTransparency: Boolean = false,
    /**
     * Enforces minimum tone contrast and edge separation. Tone contrast applies only to full
     * rendering; fallback rendering omits color adjustments, so it gets the stronger edge alone.
     */
    val increaseContrast: Boolean = false,
    /** Enforces a visible edge without changing tone contrast. */
    val showBorders: Boolean = false,
    /**
     * Opaque base for reduced-transparency fallback, or null for white in light and black in dark
     * host appearance. Set it when the app theme differs from the host appearance. `Color.Unspecified`
     * is rejected, not treated as null: pass null to select the default.
     */
    val reducedTransparencyFallbackColor: Color? = null,
) {
    init {
        require(
            reducedTransparencyFallbackColor == null ||
                (reducedTransparencyFallbackColor.isSpecified && reducedTransparencyFallbackColor.alpha == 1f),
        ) {
            "reducedTransparencyFallbackColor must be specified and opaque"
        }
    }

    /** Returns a copy of these settings with the given values replaced. */
    fun copy(
        reduceTransparency: Boolean = this.reduceTransparency,
        increaseContrast: Boolean = this.increaseContrast,
        showBorders: Boolean = this.showBorders,
        reducedTransparencyFallbackColor: Color? = this.reducedTransparencyFallbackColor,
    ): GlassAccessibilitySettings = GlassAccessibilitySettings(
        reduceTransparency = reduceTransparency,
        increaseContrast = increaseContrast,
        showBorders = showBorders,
        reducedTransparencyFallbackColor = reducedTransparencyFallbackColor,
    )
}

/** Accessibility preferences inherited by Glass nodes in this composition subtree. */
val LocalGlassAccessibilitySettings: ProvidableCompositionLocal<GlassAccessibilitySettings> =
    compositionLocalOf { GlassAccessibilitySettings() }
