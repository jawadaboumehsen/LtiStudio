/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.InternalHazeApi
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme

internal actual fun readPlatformGlassSystemAppearance(scope: HazeEffectLifecycleScope): GlassSystemAppearance =
    when (currentSystemTheme) {
        SystemTheme.DARK -> GlassSystemAppearance.Dark
        else -> GlassSystemAppearance.Light
    }
