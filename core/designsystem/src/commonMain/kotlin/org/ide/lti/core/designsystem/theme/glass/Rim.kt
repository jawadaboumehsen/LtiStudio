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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.highlight.glassBevel

/**
 * A [GlassStyle]'s edge treatment: how bright the light-struck side of the rim reads, how present
 * the opposing counter-shadow side is, and which direction the light comes from. Resolves to the
 * existing `GlassBevel` shader (`backdrop/`'s dual-tone bevel, via [glassBevel]) - this type exists
 * so [GlassStyle] can carry rim config as a value (per backdrop-refactor-plan-20260920.md §1.1)
 * instead of every component hand-picking a `Highlight` instance.
 *
 * `angleDegrees` matches the shader's own parameter directly (0 = light from the right, 90 = light
 * from the top) rather than the plan's illustrative `Alignment` sketch: the current shader takes an
 * angle, not an alignment, and converting one to the other isn't specified anywhere precisely
 * enough to implement without guessing.
 */
@Immutable
public data class Rim(
    public val specularAlpha: Float = 0.75f,
    public val shadowAlpha: Float = 0.25f,
    public val angleDegrees: Float = 90f,
    public val width: Dp = 1.dp,
) {
    init {
        require(specularAlpha.isFinite() && specularAlpha in 0f..1f) {
            "specularAlpha must be finite and in 0f..1f, was $specularAlpha"
        }
        require(shadowAlpha.isFinite() && shadowAlpha in 0f..1f) {
            "shadowAlpha must be finite and in 0f..1f, was $shadowAlpha"
        }
        require(angleDegrees.isFinite()) { "angleDegrees must be finite, was $angleDegrees" }
        require(width.isSpecified && width.value.isFinite() && width >= 0.dp) {
            "width must be specified, finite, and non-negative, was $width"
        }
    }

    public companion object {
        /** No rim - the fallback (`effectsEnabled == false`) path still draws [GlassStyle.border]. */
        public val None: Rim? = null
    }
}

/** Resolves a [Rim] to the [Highlight] the existing engine already knows how to draw. */
public fun Rim.toHighlight(): Highlight = Highlight(
    width = width,
    blurRadius = width / 2f,
    style = glassBevel(
        specularAlpha = specularAlpha,
        shadowAlpha = shadowAlpha,
        angleDegrees = angleDegrees,
    ),
)
