package com.kashif_e.backdrop.highlight

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color

/**
 * Defines the visual style for highlights.
 * 
 * Platform implementations may use different rendering techniques:
 * - Android: Can use RuntimeShader for gradient-based highlights
 * - iOS: Uses Skia for similar effects
 */
@Immutable
expect sealed interface HighlightStyle {

    val color: Color

    val blendMode: BlendMode

    companion object {
        /**
         * Default gradient-based highlight style.
         */
        @Stable
        val Default: HighlightStyle

        /**
         * Ambient lighting highlight style.
         */
        @Stable
        val Ambient: HighlightStyle

        /**
         * Simple plain-colored highlight style.
         */
        @Stable
        val Plain: HighlightStyle

        /**
         * Apple-style dual-tone physical glass bevel highlight.
         */
        @Stable
        val GlassBevel: HighlightStyle
    }
}

/**
 * A [HighlightStyle.GlassBevel] tuned with custom rim parameters, rather than the fixed
 * [HighlightStyle.Companion.GlassBevel] default. `angleDegrees` follows the shader's own
 * convention (0 = light from the right, 90 = light from the top).
 */
expect fun glassBevel(
    specularAlpha: Float = 0.75f,
    shadowAlpha: Float = 0.25f,
    angleDegrees: Float = 90f,
    falloff: Float = 1.6f,
): HighlightStyle
