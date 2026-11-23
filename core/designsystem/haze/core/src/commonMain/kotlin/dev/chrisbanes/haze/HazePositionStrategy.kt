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

internal enum class HazePositionStrategy {
    /**
     * Uses `positionInRoot()` coordinates.
     *
     * This works correctly when source and effect are in the same composition root.
     * This is the most common case and handles split-window modes
     * (e.g. Huawei Parallel Space) correctly.
     *
     * Does **not** work across separate roots, including sibling ComposeViews in one window.
     */
    Local,

    /**
     * Uses screen-level coordinates (`positionOnScreen()`) on all platforms.
     *
     * Required when source and effect are in different roots or windows (e.g. dialogs, popups).
     */
    Screen,

    /**
     * The default strategy. Uses [Local] coordinates, but automatically promotes to
     * [Screen] when it detects that source areas are in a different root or window than the
     * effect node.
     */
    Auto,
}
