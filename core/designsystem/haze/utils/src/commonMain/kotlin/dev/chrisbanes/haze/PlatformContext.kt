/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package dev.chrisbanes.haze

import androidx.compose.ui.node.CompositionLocalConsumerModifierNode

/** Platform-specific context required by built-in Haze renderers. */
@InternalHazeApi
public expect abstract class PlatformContext

/** Returns the [PlatformContext] associated with this modifier node. */
@InternalHazeApi
public expect fun CompositionLocalConsumerModifierNode.requirePlatformContext(): PlatformContext
