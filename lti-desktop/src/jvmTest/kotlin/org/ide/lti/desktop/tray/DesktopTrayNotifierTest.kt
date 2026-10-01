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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.ide.lti.core.common.notification.NotificationLevel
import org.ide.lti.core.common.notification.SystemNotification
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopTrayNotifierTest {

    @Test
    fun testDesktopTrayNotifier_isSupportedReflectsPlatform() {
        val trayState = TrayState()
        val notifier = DesktopTrayNotifier(trayState)
        assertEquals(isTraySupported, notifier.isSupported)
    }

    @Test
    fun testDesktopTrayNotifier_dispatchesNotificationToTrayStateFlow() = runBlocking {
        if (!isTraySupported) return@runBlocking

        val trayState = TrayState()
        val notifier = DesktopTrayNotifier(trayState)

        val notification = SystemNotification(
            title = "Build Completed",
            message = "All modules compiled successfully",
            level = NotificationLevel.Warning,
        )

        val receivedDeferred = CompletableDeferred<Notification>()
        val collectorJob = launch {
            trayState.notificationFlow.collect {
                receivedDeferred.complete(it)
            }
        }

        // Allow collector coroutine to start and suspend on the rendezvous channel
        yield()

        notifier.notify(notification)

        val received = withTimeout(3000) { receivedDeferred.await() }
        collectorJob.cancel()

        assertEquals("Build Completed", received.title)
        assertEquals("All modules compiled successfully", received.message)
        assertEquals(Notification.Type.Warning, received.type)
    }
}
