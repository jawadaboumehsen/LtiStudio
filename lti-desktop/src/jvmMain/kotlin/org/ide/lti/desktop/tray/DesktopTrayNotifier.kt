/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.desktop.tray

import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.TrayState
import androidx.compose.ui.window.isTraySupported
import org.ide.lti.core.common.notification.NotificationLevel
import org.ide.lti.core.common.notification.SystemNotification
import org.ide.lti.core.common.notification.SystemNotifier

/**
 * Desktop implementation of [SystemNotifier] backed by Compose Desktop's [TrayState].
 */
class DesktopTrayNotifier(
    private val trayState: TrayState,
) : SystemNotifier {

    override val isSupported: Boolean
        get() = isTraySupported

    override fun notify(notification: SystemNotification) {
        if (!isSupported) return

        val type = when (notification.level) {
            NotificationLevel.Info -> Notification.Type.Info
            NotificationLevel.Warning -> Notification.Type.Warning
            NotificationLevel.Error -> Notification.Type.Error
            NotificationLevel.None -> Notification.Type.None
        }

        trayState.sendNotification(
            Notification(
                title = notification.title,
                message = notification.message,
                type = type,
            )
        )
    }
}
