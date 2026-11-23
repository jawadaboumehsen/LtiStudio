/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.asComposeShader
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.Shader

/**
 * Creates a fractal noise shader.
 */
@InternalHazeApi
public fun createFractalNoiseShader(
    baseFrequencyX: Float,
    baseFrequencyY: Float,
    numOctaves: Int,
    seed: Float,
): androidx.compose.ui.graphics.Shader = Shader.makeFractalNoise(
    baseFrequencyX = baseFrequencyX,
    baseFrequencyY = baseFrequencyY,
    numOctaves = numOctaves,
    seed = seed,
).asComposeShader()

@InternalHazeApi
public actual fun createBlendColorFilter(
    color: Int,
    blendMode: BlendMode,
): PlatformColorFilter {
    return ColorFilter.makeBlend(color, blendMode.toSkiaBlendMode())
}
