/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.TileMode

/**
 * Platform-specific render effect type.
 * - On Android (API 31+): `android.graphics.RenderEffect`
 * - On Skiko: `org.jetbrains.skia.ImageFilter`
 */
@InternalHazeApi
expect class PlatformRenderEffect

/**
 * Platform-specific color filter type.
 * - On Android: `android.graphics.ColorFilter`
 * - On Skiko: `org.jetbrains.skia.ColorFilter`
 */
@InternalHazeApi
expect class PlatformColorFilter

/**
 * Creates a [PlatformRenderEffect] from a shader.
 */
@InternalHazeApi
expect fun createShaderRenderEffect(shader: Shader, crop: Rect? = null): PlatformRenderEffect

/**
 * Creates a blend [PlatformRenderEffect] from two render effects.
 */
@InternalHazeApi
expect fun createBlendRenderEffect(
    blendMode: BlendMode,
    background: PlatformRenderEffect,
    foreground: PlatformRenderEffect,
    crop: Rect? = null,
): PlatformRenderEffect

/**
 * Creates a color filter [PlatformRenderEffect].
 */
@InternalHazeApi
expect fun createColorFilterRenderEffect(
    colorFilter: PlatformColorFilter,
    input: PlatformRenderEffect? = null,
    crop: Rect? = null,
): PlatformRenderEffect

/**
 * Creates a blur [PlatformRenderEffect].
 *
 * Returns `null` when both [radiusX] and [radiusY] are <= 0, indicating no blur is needed.
 *
 * @param radiusX Blur radius in the X direction (pixels)
 * @param radiusY Blur radius in the Y direction (pixels)
 */
@InternalHazeApi
expect fun createBlurRenderEffect(
    radiusX: Float,
    radiusY: Float,
    tileMode: TileMode,
    input: PlatformRenderEffect? = null,
    crop: Rect? = null,
): PlatformRenderEffect?

/**
 * Creates an offset [PlatformRenderEffect].
 */
@InternalHazeApi
expect fun createOffsetRenderEffect(
    offsetX: Float,
    offsetY: Float,
    input: PlatformRenderEffect? = null,
    crop: Rect? = null,
): PlatformRenderEffect

/**
 * Chains this render effect with another, composing them.
 */
@InternalHazeApi
expect fun PlatformRenderEffect.then(other: PlatformRenderEffect): PlatformRenderEffect

/**
 * Blends this render effect with a foreground effect.
 */
@InternalHazeApi
fun PlatformRenderEffect.blendForeground(
    foreground: PlatformRenderEffect,
    blendMode: BlendMode,
    offset: Offset = Offset.Unspecified,
): PlatformRenderEffect = createBlendRenderEffect(
    blendMode = blendMode,
    background = this,
    foreground = when {
        offset.isUnspecified -> foreground
        offset == Offset.Zero -> foreground
        else -> createOffsetRenderEffect(offset.x, offset.y, foreground)
    },
)

/**
 * Converts this platform render effect to a Compose [RenderEffect].
 */
@InternalHazeApi
expect fun PlatformRenderEffect.asComposeRenderEffect(): RenderEffect
