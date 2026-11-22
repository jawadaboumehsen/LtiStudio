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

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope

internal fun interface GlassRimBrushProvider {
    operator fun invoke(key: GlassRimEffectKey): Brush?
}

internal expect fun createGlassRimBrushProvider(): GlassRimBrushProvider?

internal expect fun DrawScope.supportsGlassRimBrush(): Boolean

internal fun DrawScope.drawGlassRimWithBrush(brush: Brush): Boolean {
    if (!supportsGlassRimBrush()) return false
    // Only this optional built-in brush draw is guarded; caller content is drawn separately.
    return runCatchingGlassRim { drawRect(brush) }.isSuccess
}

/** Keep optional rim fallback limited to runtime exceptions; propagate other failures. */
internal inline fun <T> runCatchingGlassRim(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it !is RuntimeException) throw it }
