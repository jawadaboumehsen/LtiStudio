/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSourceMetadata
import dev.chrisbanes.haze.HazeSourceSelection
import dev.chrisbanes.haze.HazeState

/**
 * Identifies control-local optical sources (such as toggle tracks) to allow selective inclusion or exclusion.
 */
@Immutable
data class ControlSourceKey(val type: String, val id: String)

/**
 * Central policy for scene consumers: excludes all control-local sources to eliminate bloom halos.
 */
@OptIn(ExperimentalHazeApi::class)
val SceneConsumersSelection: HazeSourceSelection = HazeSourceSelection.Behind.where { metadata: HazeSourceMetadata ->
    metadata.key !is ControlSourceKey
}

/**
 * Thumb selection policy: samples all scene sources (keyed or unkeyed) and the control's own track,
 * while excluding peer control sources.
 */
@OptIn(ExperimentalHazeApi::class)
fun toggleThumbSelection(myKey: ControlSourceKey): HazeSourceSelection =
    HazeSourceSelection.Behind.where { metadata: HazeSourceMetadata ->
        metadata.key !is ControlSourceKey || metadata.key == myKey
    }

/**
 * Single shared factory for scene consumers to sample [HazeState] with the halo-exclusion policy.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun sceneHazeInput(
    state: HazeState = GlassTheme.hazeState,
    selection: HazeSourceSelection = SceneConsumersSelection,
): HazeInput.Sources = HazeInput.Sources(state = state, selection = selection)
