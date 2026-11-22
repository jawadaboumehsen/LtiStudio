/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
@file:JvmName("PlatformContextKt")
@file:Suppress("DuplicateSourceClass")

package dev.chrisbanes.haze

import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName

/** Stateless platform context used by Skiko-backed Haze renderers. */
@InternalHazeApi
public actual abstract class PlatformContext private constructor() {
    /** Provides the shared Skiko platform context. */
    public companion object {
        /** Shared Skiko platform context instance. */
        @JvmField public val INSTANCE: PlatformContext = object : PlatformContext() {}
    }
}

/** Returns the shared Skiko [PlatformContext]. */
@InternalHazeApi
public actual fun CompositionLocalConsumerModifierNode.requirePlatformContext(): PlatformContext {
    return PlatformContext.INSTANCE
}
