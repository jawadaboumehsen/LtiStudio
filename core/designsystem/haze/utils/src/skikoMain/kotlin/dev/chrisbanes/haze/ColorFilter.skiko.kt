/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asSkiaColorFilter

/**
 * Converts a Compose [ColorFilter] to a Skia platform [PlatformColorFilter].
 */
@InternalHazeApi
public actual fun ColorFilter.toPlatformColorFilter(): PlatformColorFilter {
    return asSkiaColorFilter()
}
