/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.catalog

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.ide.lti.core.designsystem.theme.ComponentSize

fun main() = application {
    val windowState = rememberWindowState(
        size = DpSize(
            width = ComponentSize.MaxPanelWidth * 2,
            height = ComponentSize.MaxPanelHeight * 1.4f
        )
    )

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "LtiRom Design System & Token Catalog",
    ) {
        CatalogApp()
    }
}
