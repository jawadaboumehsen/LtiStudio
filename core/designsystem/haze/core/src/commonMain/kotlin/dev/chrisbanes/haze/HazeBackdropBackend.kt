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

internal enum class HazeBackdropBackendSelection {
    Undecided,
    Native,
    FallbackUnavailable,
    FallbackFailed,
}

internal class HazeBackdropBackendState {
    var selection: HazeBackdropBackendSelection = HazeBackdropBackendSelection.Undecided
        private set

    val usesFallback: Boolean
        get() = selection == HazeBackdropBackendSelection.FallbackUnavailable ||
            selection == HazeBackdropBackendSelection.FallbackFailed

    fun attach(platformBackdropEnabled: Boolean) {
        selection = if (platformBackdropEnabled) {
            HazeBackdropBackendSelection.Undecided
        } else {
            HazeBackdropBackendSelection.FallbackUnavailable
        }
    }

    fun resolve(nativeAvailable: Boolean) {
        if (!usesFallback) {
            selection = if (nativeAvailable) {
                HazeBackdropBackendSelection.Native
            } else {
                HazeBackdropBackendSelection.FallbackUnavailable
            }
        }
    }

    fun fail() {
        if (!usesFallback) {
            selection = HazeBackdropBackendSelection.FallbackFailed
        }
    }

    fun reset() {
        selection = HazeBackdropBackendSelection.Undecided
    }
}
