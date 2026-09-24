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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configurable dimensions for `GlassToggle`, allowing candidate geometry testing and overrides.
 */
@Immutable
data class ToggleDimensions(
    val trackWidth: Dp = ComponentSize.ToggleTrackWidth,
    val trackHeight: Dp = ComponentSize.ToggleTrackHeight,
    val thumbWidth: Dp = ComponentSize.ToggleThumbWidth,
    val thumbHeight: Dp = ComponentSize.ToggleThumbHeight,
    val thumbIsCircle: Boolean = true,
    val padding: Dp = Spacing.ExtraExtraSmall,
    val minTouchTarget: Dp = ComponentSize.ToggleMinTouchTarget,
) {
    companion object {
        /** Pair A: Legacy baseline (64x28 dp track, 40x24 dp thumb, 20 dp exposed). */
        val PairA = ToggleDimensions(
            trackWidth = 64.dp,
            trackHeight = 28.dp,
            thumbWidth = 40.dp,
            thumbHeight = 24.dp,
            thumbIsCircle = false,
            padding = 2.dp,
            minTouchTarget = 28.dp,
        )

        /** Pair B: Standard production ergonomic (56x32 dp track, 28x28 dp circular thumb, 24 dp exposed). */
        val PairB = ToggleDimensions()

        /** Pair C: Haze reference candidate (64x48 dp track, 40x40 dp circular thumb). */
        val PairC = ToggleDimensions(
            trackWidth = 64.dp,
            trackHeight = 48.dp,
            thumbWidth = 40.dp,
            thumbHeight = 40.dp,
            thumbIsCircle = true,
            padding = 4.dp,
            minTouchTarget = 48.dp,
        )
    }
}

/**
 * CompositionLocal providing [ToggleDimensions] to `GlassToggle` instances down the tree.
 */
val LocalToggleDimensions = compositionLocalOf { ToggleDimensions() }
