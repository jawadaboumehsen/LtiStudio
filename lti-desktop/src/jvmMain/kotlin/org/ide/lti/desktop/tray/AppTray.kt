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
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import org.ide.lti.core.common.notification.DelegatingSystemNotifier
import org.koin.core.context.GlobalContext

@Composable
internal fun rememberClasspathPainter(resourcePath: String): Painter = remember(resourcePath) {
    val loader = Thread.currentThread().contextClassLoader ?: AppTrayLoader::class.java.classLoader
    val bytes =
        loader.getResourceAsStream(resourcePath)
            ?.use { it.readAllBytes() }
            ?: error("Missing classpath resource: $resourcePath")
    BitmapPainter(bytes.decodeToImageBitmap())
}

private object AppTrayLoader

/**
 * System tray integration for LtiRom Studio.
 *
 * Uses Compose Multiplatform Desktop [Tray] when supported by the host desktop environment,
 * and binds [DesktopNativeNotifier] to [DelegatingSystemNotifier] for OS notifications.
 */
@Composable
fun ApplicationScope.AppTray(
    icon: Painter = rememberClasspathPainter("icons/ic_launcher.png"),
    tooltip: String = "LtiRom Studio",
    onOpenStudio: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
) {
    val delegatingNotifier = remember {
        GlobalContext.getOrNull()?.getOrNull<DelegatingSystemNotifier>()
    }

    DisposableEffect(delegatingNotifier, tooltip) {
        val nativeNotifier = DesktopNativeNotifier(appTitle = tooltip)
        delegatingNotifier?.attach(nativeNotifier)
        onDispose {
            delegatingNotifier?.detach()
        }
    }

    if (isTraySupported) {
        val trayState = rememberTrayState()
        Tray(
            icon = icon,
            state = trayState,
            tooltip = tooltip,
            onAction = onOpenStudio,
            menu = {
                Item(
                    text = "Open LtiRom Studio",
                    onClick = onOpenStudio,
                )
                Item(
                    text = "Settings...",
                    onClick = onOpenSettings,
                )
                Separator()
                Item(
                    text = "Exit",
                    onClick = onExit,
                )
            },
        )
    }
}
