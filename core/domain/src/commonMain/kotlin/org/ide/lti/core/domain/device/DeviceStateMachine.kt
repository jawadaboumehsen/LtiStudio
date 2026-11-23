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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Events triggering transitions in the physical device bridge.
 */
sealed interface DeviceEvent {
    data class DeviceConnected(val serial: String, val mode: DeviceMode) : DeviceEvent
    data class ModeTransition(val targetMode: DeviceMode) : DeviceEvent
    data class PropertiesDiscovered(
        val codename: String? = null,
        val hardware: String? = null,
        val slot: String? = null,
        val unlocked: Boolean? = null,
        val avbEnforced: Boolean? = null,
        val battery: Int? = null,
        val model: String? = null,
    ) : DeviceEvent
    data object DeviceDisconnected : DeviceEvent
}

/**
 * Pure domain state machine tracking Android device connectivity, mode transitions
 * (ADB -> Fastboot -> Fastbootd -> EDL -> Recovery), and hardware profile attributes.
 */
class DeviceStateMachine(initialProfile: DeviceProfile = DeviceProfile.Disconnected) {
    private val _currentProfile = MutableStateFlow(initialProfile)
    val currentProfile: StateFlow<DeviceProfile> = _currentProfile.asStateFlow()

    fun transition(event: DeviceEvent): DeviceProfile {
        val current = _currentProfile.value
        val updated = when (event) {
            is DeviceEvent.DeviceConnected -> {
                current.copy(
                    serial = event.serial,
                    mode = event.mode,
                )
            }
            is DeviceEvent.ModeTransition -> {
                current.copy(mode = event.targetMode)
            }
            is DeviceEvent.PropertiesDiscovered -> {
                current.copy(
                    codename = event.codename ?: current.codename,
                    platform = event.hardware?.let { CpuPlatform.fromHardwareString(it) } ?: current.platform,
                    activeSlot = event.slot?.let { DeviceSlot.fromSlotString(it) } ?: current.activeSlot,
                    isUnlocked = event.unlocked ?: current.isUnlocked,
                    isAvbEnforced = event.avbEnforced ?: current.isAvbEnforced,
                    batteryPercent = event.battery ?: current.batteryPercent,
                    productModel = event.model ?: current.productModel,
                )
            }
            is DeviceEvent.DeviceDisconnected -> {
                DeviceProfile.Disconnected
            }
        }
        _currentProfile.value = updated
        return updated
    }
}
