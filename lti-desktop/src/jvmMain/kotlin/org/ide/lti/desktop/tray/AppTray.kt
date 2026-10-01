/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.desktop.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import dev.nucleusframework.composenativetray.tray.api.Tray
import org.ide.lti.core.common.notification.DelegatingSystemNotifier
import org.koin.core.context.GlobalContext

/**
 * System tray integration for LtiRom Studio.
 *
 * Uses Tao-native ComposeNativeTray to provide native Win32/Linux/macOS system tray presence,
 * quick action menu, and binds [DesktopNativeNotifier] to [DelegatingSystemNotifier] for OS notifications.
 */
@Composable
fun AppTray(
    icon: Painter = painterResource("icons/ic_launcher.png"),
    tooltip: String = "LtiRom Studio",
    onOpenStudio: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
) {
    val delegatingNotifier = remember {
        GlobalContext.getOrNull()?.getOrNull<DelegatingSystemNotifier>()
    }

    DisposableEffect(delegatingNotifier) {
        val nativeNotifier = DesktopNativeNotifier(appTitle = tooltip)
        delegatingNotifier?.attach(nativeNotifier)
        onDispose {
            delegatingNotifier?.detach()
        }
    }

    Tray(
        icon = icon,
        tooltip = tooltip,
        primaryAction = onOpenStudio,
        menuContent = {
            Item(
                label = "Open LtiRom Studio",
                onClick = onOpenStudio,
            )
            Item(
                label = "Settings...",
                onClick = onOpenSettings,
            )
            Divider()
            Item(
                label = "Exit",
                onClick = onExit,
            )
        },
    )
}
