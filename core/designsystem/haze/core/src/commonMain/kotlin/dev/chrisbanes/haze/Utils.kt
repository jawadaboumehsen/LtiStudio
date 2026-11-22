/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
@file:Suppress("NOTHING_TO_INLINE")

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode

/**
 * Returns [LayoutCoordinates.positionOnScreen] if it succeeds, or [Offset.Unspecified] if it
 * throws. [positionOnScreen] can throw (e.g. on certain Skiko backends or when a layout is
 * detached); callers should always route through this helper rather than catching at the call
 * site, to keep error policy consistent.
 *
 * We catch [Exception] deliberately — not [Throwable] — so that `OutOfMemoryError` and other
 * JVM errors propagate rather than being silently swallowed as an "unspecified position".
 */
internal fun LayoutCoordinates.safePositionOnScreen(): Offset = try {
    positionOnScreen()
} catch (_: Exception) {
    Offset.Unspecified
}

internal fun LayoutCoordinates.safeTransformToScreen(): Matrix? = try {
    Matrix().also { transformToScreen(it) }
} catch (_: Exception) {
    null
}

internal fun LayoutCoordinates.positionForHaze(
    strategy: HazePositionStrategy,
): Offset = when (strategy) {
    HazePositionStrategy.Local, HazePositionStrategy.Auto -> positionInRoot()
    HazePositionStrategy.Screen -> safePositionOnScreen()
}

internal fun LayoutCoordinates.transformToRoot(): Matrix = Matrix().also { matrix ->
    findRootCoordinates().transformFrom(this, matrix)
}

internal fun LayoutCoordinates.transformToScreenOrRoot(): Matrix =
    safeTransformToScreen() ?: transformToRoot()

internal expect fun CompositionLocalConsumerModifierNode.getWindowId(): Any?

internal inline fun <T> T.letIf(condition: Boolean, block: (T) -> T): T {
    return if (condition) block(this) else this
}
