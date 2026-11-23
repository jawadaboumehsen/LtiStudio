/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package dev.chrisbanes.haze

import kotlin.jvm.JvmField

/** Experimental process-wide runtime feature switches. */
@ExperimentalHazeApi
public object HazeFeatureFlags {

    /**
     * Whether built-in effects may use a platform window-backdrop renderer.
     *
     * This value is read when a Haze effect modifier node is attached. Changing it does not alter
     * nodes that are already attached; reattach a node for it to observe the new value. Enabling the
     * flag only makes native rendering eligible. Platform, window, canvas, effect, setup, and draw
     * checks can still select the configured source fallback, which remains the portable behavior.
     * The default is `false` while the platform implementation is experimental.
     *
     * Once physical Android 37.2 acceptance is complete, a later release may flip the default while
     * retaining a temporary `false` escape hatch for regressions, then remove this experimental flag.
     */
    @JvmField
    public var isPlatformBackdropEnabled: Boolean = false
}
