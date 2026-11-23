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

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun CompositionLocalConsumerModifierNode.getWindowId(): Any? {
    return try {
        currentValueOf(LocalAwtWindow)
    } catch (_: IllegalStateException) {
        null
    }
}
