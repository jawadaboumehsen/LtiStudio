// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

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
