/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package dev.chrisbanes.haze.blur

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

internal fun calculateLength(
    start: Offset,
    end: Offset,
    size: Size,
): Float {
    val (startX, startY) = start
    val endX = end.x.coerceAtMost(size.width)
    val endY = end.y.coerceAtMost(size.height)
    return hypot(endX - startX, endY - startY)
}

internal fun Size.expand(expansionWidth: Float, expansionHeight: Float): Size {
    return Size(width = width + expansionWidth, height = height + expansionHeight)
}

internal fun ceil(size: Size): Size = Size(width = ceil(size.width), height = ceil(size.height))

internal fun Offset.round(): Offset = Offset(x.roundToInt().toFloat(), y.roundToInt().toFloat())
