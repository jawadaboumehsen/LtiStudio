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

import androidx.compose.ui.graphics.Paint

private const val MAX_PAINT_POOL_SIZE = 3

internal val PaintPool: ArrayDeque<Paint> = ArrayDeque(MAX_PAINT_POOL_SIZE)

internal expect fun Paint.reset()

internal inline fun <R> ArrayDeque<Paint>.usePaint(block: (Paint) -> R): R {
    val paint = removeLastOrNull() ?: Paint()
    return try {
        block(paint)
    } finally {
        paint.reset()
        if (size < MAX_PAINT_POOL_SIZE) {
            addLast(paint)
        }
    }
}
