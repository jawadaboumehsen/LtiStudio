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

/** Reads the appearance supplied by the current platform host during the observed node update. */
internal expect fun readPlatformGlassSystemAppearance(scope: HazeEffectLifecycleScope): GlassSystemAppearance
