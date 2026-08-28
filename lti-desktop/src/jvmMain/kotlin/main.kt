/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mobile-wallet/blob/master/LICENSE.md
 */

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.rememberWindowState
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.LocalNucleusWindow
import dev.nucleusframework.application.nucleusApplication
import org.ide.lti.core.designsystem.component.LocalWindowControlActions
import org.ide.lti.core.designsystem.component.WindowControlActions
import org.ide.lti.shared.LtiSharedApp
import org.ide.lti.shared.di.initKoin

fun main(args: Array<String>) = nucleusApplication(args) {
    initKoin()
    val windowState = rememberWindowState()

    DecoratedWindow(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "LtiRom Studio",
        icon = painterResource("icons/ic_launcher.png"),
    ) {
        val nucleusWin = LocalNucleusWindow.current
        CompositionLocalProvider(
            LocalWindowControlActions provides WindowControlActions(
                onMinimize = {
                    windowState.isMinimized = true
                    try { nucleusWin.setMinimized(true) } catch (_: Throwable) {}
                },
                onMaximize = {
                    windowState.placement = if (windowState.placement == androidx.compose.ui.window.WindowPlacement.Maximized) {
                        androidx.compose.ui.window.WindowPlacement.Floating
                    } else {
                        androidx.compose.ui.window.WindowPlacement.Maximized
                    }
                    try { nucleusWin.setMaximized(!nucleusWin.isMaximized) } catch (_: Throwable) {}
                },
                onClose = {
                    exitApplication()
                },
                onDragWindow = {
                    try { nucleusWin.unsafe.taoWindow?.dragWindow() } catch (_: Throwable) {}
                },
            )
        ) {
            LtiSharedApp()
        }
    }
}
