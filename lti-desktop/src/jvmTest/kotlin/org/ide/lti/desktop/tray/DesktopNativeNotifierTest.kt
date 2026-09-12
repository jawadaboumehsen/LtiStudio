/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.desktop.tray

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.ide.lti.core.common.notification.NotificationLevel
import org.ide.lti.core.common.notification.SystemNotification
import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopNativeNotifierTest {

    @Test
    fun testDesktopNativeNotifier_isSupportedOnDesktop() {
        val notifier = DesktopNativeNotifier(scope = CoroutineScope(Dispatchers.Unconfined))
        assertTrue(notifier.isSupported)
    }

    @Test
    fun testDesktopNativeNotifier_notifyDispatchesWithoutError() = runBlocking {
        val notifier = DesktopNativeNotifier(scope = CoroutineScope(Dispatchers.Unconfined))

        val notification = SystemNotification(
            title = "Test Title",
            message = "Test Message",
            level = NotificationLevel.Info,
        )

        notifier.notify(notification)
    }
}
