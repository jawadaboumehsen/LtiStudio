/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceStateMachineTest {

    @Test
    fun testInitialStateIsDisconnected() {
        val stateMachine = DeviceStateMachine()
        val initial = stateMachine.currentProfile.value

        assertEquals(DeviceMode.DISCONNECTED, initial.mode)
        assertFalse(initial.mode.isConnected)
    }

    @Test
    fun testDeviceConnectionAndModeTransitions() {
        val stateMachine = DeviceStateMachine()

        // 1. Device connects via ADB
        stateMachine.transition(
            DeviceEvent.DeviceConnected(serial = "SERIAL1234", mode = DeviceMode.ADB_ONLINE),
        )
        var profile = stateMachine.currentProfile.value
        assertEquals("SERIAL1234", profile.serial)
        assertEquals(DeviceMode.ADB_ONLINE, profile.mode)
        assertTrue(profile.mode.isAdb)
        assertTrue(profile.mode.canExecuteShell)
        assertFalse(profile.mode.canFlashLogicalPartitions)

        // 2. Transition to Fastboot
        stateMachine.transition(DeviceEvent.ModeTransition(DeviceMode.FASTBOOT))
        profile = stateMachine.currentProfile.value
        assertEquals(DeviceMode.FASTBOOT, profile.mode)
        assertTrue(profile.mode.isFastbootFamily)
        assertFalse(profile.mode.canFlashLogicalPartitions)

        // 3. Transition to Fastbootd
        stateMachine.transition(DeviceEvent.ModeTransition(DeviceMode.FASTBOOTD))
        profile = stateMachine.currentProfile.value
        assertEquals(DeviceMode.FASTBOOTD, profile.mode)
        assertTrue(profile.mode.isFastbootFamily)
        assertTrue(profile.mode.canFlashLogicalPartitions)

        // 4. Transition to Recovery
        stateMachine.transition(DeviceEvent.ModeTransition(DeviceMode.RECOVERY))
        profile = stateMachine.currentProfile.value
        assertEquals(DeviceMode.RECOVERY, profile.mode)
        assertTrue(profile.mode.canExecuteShell)

        // 5. Transition to EDL 9008
        stateMachine.transition(DeviceEvent.ModeTransition(DeviceMode.EDL_9008))
        profile = stateMachine.currentProfile.value
        assertEquals(DeviceMode.EDL_9008, profile.mode)
    }

    @Test
    fun testHardwarePlatformAndSlotInference() {
        val stateMachine = DeviceStateMachine()
        stateMachine.transition(
            DeviceEvent.DeviceConnected(serial = "PIXEL_SERIAL", mode = DeviceMode.ADB_ONLINE),
        )

        stateMachine.transition(
            DeviceEvent.PropertiesDiscovered(
                codename = "husky",
                hardware = "zuma",
                slot = "b",
                unlocked = true,
                avbEnforced = false,
                battery = 92,
                model = "Pixel 8 Pro",
            ),
        )

        val profile = stateMachine.currentProfile.value
        assertEquals("husky", profile.codename)
        assertEquals(CpuPlatform.TENSOR, profile.platform)
        assertEquals(DeviceSlot.B, profile.activeSlot)
        assertEquals(DeviceSlot.A, profile.activeSlot.alternate)
        assertTrue(profile.isDualSlot)
        assertTrue(profile.isUnlocked)
        assertFalse(profile.isAvbEnforced)
        assertEquals(92, profile.batteryPercent)
        assertEquals("husky (PIXEL_SERIAL)", profile.displayTitle)
    }

    @Test
    fun testCpuPlatformDetectionPatterns() {
        assertEquals(CpuPlatform.SNAPDRAGON, CpuPlatform.fromHardwareString("qcom"))
        assertEquals(CpuPlatform.SNAPDRAGON, CpuPlatform.fromHardwareString("sm8650"))
        assertEquals(CpuPlatform.SNAPDRAGON, CpuPlatform.fromHardwareString("qualcomm"))
        assertEquals(CpuPlatform.MEDIATEK, CpuPlatform.fromHardwareString("mt6895"))
        assertEquals(CpuPlatform.MEDIATEK, CpuPlatform.fromHardwareString("dimensity 9300"))
        assertEquals(CpuPlatform.EXYNOS, CpuPlatform.fromHardwareString("exynos2400"))
        assertEquals(CpuPlatform.EXYNOS, CpuPlatform.fromHardwareString("s5e9945"))
        assertEquals(CpuPlatform.TENSOR, CpuPlatform.fromHardwareString("gs101"))
        assertEquals(CpuPlatform.GENERIC, CpuPlatform.fromHardwareString("unknown_board"))
        assertEquals(CpuPlatform.GENERIC, CpuPlatform.fromHardwareString(null))
    }

    @Test
    fun testDisconnectResetsProfile() {
        val stateMachine = DeviceStateMachine()
        stateMachine.transition(
            DeviceEvent.DeviceConnected(serial = "SERIAL_X", mode = DeviceMode.FASTBOOT),
        )
        assertEquals(DeviceMode.FASTBOOT, stateMachine.currentProfile.value.mode)

        stateMachine.transition(DeviceEvent.DeviceDisconnected)
        assertEquals(DeviceMode.DISCONNECTED, stateMachine.currentProfile.value.mode)
        assertFalse(stateMachine.currentProfile.value.mode.isConnected)
    }
}
