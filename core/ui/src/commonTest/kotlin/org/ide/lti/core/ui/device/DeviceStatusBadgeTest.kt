/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui.device

import org.ide.lti.core.domain.device.CpuPlatform
import org.ide.lti.core.domain.device.DeviceMode
import org.ide.lti.core.domain.device.DeviceProfile
import org.ide.lti.core.domain.device.DeviceSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceStatusBadgeTest {

    @Test
    fun testDisconnectedProfileProperties() {
        val disconnected = DeviceProfile.Disconnected
        assertEquals(DeviceMode.DISCONNECTED, disconnected.mode)
        assertFalse(disconnected.mode.isConnected)
        assertFalse(disconnected.isDualSlot)
    }

    @Test
    fun testFastbootdDualSlotProfileProperties() {
        val fastbootdProfile = DeviceProfile(
            serial = "DEMO_SERIAL_99",
            codename = "cheetah",
            mode = DeviceMode.FASTBOOTD,
            platform = CpuPlatform.TENSOR,
            activeSlot = DeviceSlot.A,
            isUnlocked = true,
            isAvbEnforced = false,
            productModel = "Pixel 7 Pro",
        )

        assertEquals("cheetah", fastbootdProfile.codename)
        assertEquals(DeviceMode.FASTBOOTD, fastbootdProfile.mode)
        assertTrue(fastbootdProfile.mode.canFlashLogicalPartitions)
        assertTrue(fastbootdProfile.isDualSlot)
        assertEquals(DeviceSlot.B, fastbootdProfile.activeSlot.alternate)
        assertTrue(fastbootdProfile.isUnlocked)
        assertFalse(fastbootdProfile.isAvbEnforced)
        assertEquals("cheetah (DEMO_SERIAL_99)", fastbootdProfile.displayTitle)
    }

    @Test
    fun testAdbUnauthorizedProfile() {
        val unauthProfile = DeviceProfile(
            serial = "UNAUTH_DEVICE",
            mode = DeviceMode.ADB_UNAUTHORIZED,
        )

        assertEquals(DeviceMode.ADB_UNAUTHORIZED, unauthProfile.mode)
        assertTrue(unauthProfile.mode.isAdb)
        assertFalse(unauthProfile.mode.canExecuteShell)
    }
}
