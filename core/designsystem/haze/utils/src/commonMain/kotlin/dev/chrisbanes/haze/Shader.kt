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

import androidx.compose.ui.graphics.BlendMode

/**
 * Creates a color filter that blends with a given color.
 */
@InternalHazeApi
public expect fun createBlendColorFilter(
    color: Int,
    blendMode: BlendMode,
): PlatformColorFilter
