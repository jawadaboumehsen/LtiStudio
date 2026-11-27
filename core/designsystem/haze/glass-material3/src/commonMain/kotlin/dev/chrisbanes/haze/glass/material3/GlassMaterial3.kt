/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package dev.chrisbanes.haze.glass.material3

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.glass.GlassStyle

/**
 * Returns a Material 3 Glass Style with this Style's writes applied after the theme defaults.
 *
 * The default [containerColor] comes from the current [MaterialTheme]. A null [tint] omits the
 * theme tint write, preserving any inherited tint. This Style can override either value. Call it
 * on [GlassStyle] for the Material 3 values alone. The returned Style is reused while this Style,
 * [containerColor], and [tint] are unchanged, so `remember` or hoist this Style.
 *
 * `Color.Unspecified` is not treated as null: the Style rejects an unspecified [tint] when it is
 * evaluated, so pass null to omit the tint.
 *
 * It is named `material3` because it applies to an existing Style; `HazeBlurStyle.Material3()`
 * creates one.
 */
@Composable
fun GlassStyle.material3(
    containerColor: Color = MaterialTheme.colorScheme.surface,
    tint: Color? = null,
): GlassStyle = remember(this, containerColor, tint) {
    GlassStyle {
        backgroundColor(containerColor)
        if (tint != null) tint(tint)
    }.then(this)
}
