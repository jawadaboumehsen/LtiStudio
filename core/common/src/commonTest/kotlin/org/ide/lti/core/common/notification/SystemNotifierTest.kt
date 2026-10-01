/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.common.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemNotifierTest {

    @Test
    fun testNoOpSystemNotifier_doesNotCrash() {
        assertFalse(NoOpSystemNotifier.isSupported)
        // Calling notify should safely execute without exceptions
        NoOpSystemNotifier.notify(
            SystemNotification(
                title = "Test",
                message = "Should not crash",
                level = NotificationLevel.Info,
            ),
        )
    }

    @Test
    fun testDelegatingSystemNotifier_forwardsToAttachedDelegate() {
        val delegatingNotifier = DelegatingSystemNotifier()
        assertFalse(delegatingNotifier.isSupported)

        val recorded = mutableListOf<SystemNotification>()
        val mockDelegate = object : SystemNotifier {
            override val isSupported: Boolean = true
            override fun notify(notification: SystemNotification) {
                recorded.add(notification)
            }
        }

        delegatingNotifier.attach(mockDelegate)
        assertTrue(delegatingNotifier.isSupported)

        val notification = SystemNotification(
            title = "Build Succeeded",
            message = "Workspace compiled with 0 errors",
            level = NotificationLevel.Info,
        )
        delegatingNotifier.notify(notification)

        assertEquals(1, recorded.size)
        assertEquals("Build Succeeded", recorded[0].title)
        assertEquals("Workspace compiled with 0 errors", recorded[0].message)
        assertEquals(NotificationLevel.Info, recorded[0].level)

        // Detach should restore NoOp
        delegatingNotifier.detach()
        assertFalse(delegatingNotifier.isSupported)

        delegatingNotifier.notify(
            SystemNotification(
                title = "Another",
                message = "Should not reach mock",
            ),
        )
        assertEquals(1, recorded.size)
    }

    @Test
    fun testSystemNotification_defaultsAndEquality() {
        val defaultNotif = SystemNotification(title = "Default Title", message = "Default Body")
        assertEquals(NotificationLevel.Info, defaultNotif.level)

        val warningNotif = SystemNotification(
            title = "Warning Title",
            message = "Warning Body",
            level = NotificationLevel.Warning,
        )
        assertEquals(NotificationLevel.Warning, warningNotif.level)

        val errorNotif = SystemNotification(
            title = "Error Title",
            message = "Error Body",
            level = NotificationLevel.Error,
        )
        assertEquals(NotificationLevel.Error, errorNotif.level)
    }
}
