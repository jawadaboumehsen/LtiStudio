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

/**
 * Protocol/execution mode of a connected physical Android device.
 */
enum class DeviceMode {
    DISCONNECTED,
    ADB_UNAUTHORIZED,
    ADB_ONLINE,
    FASTBOOT,
    FASTBOOTD,
    RECOVERY,
    EDL_9008,
    ;

    val isConnected: Boolean get() = this != DISCONNECTED

    val isAdb: Boolean get() = this == ADB_ONLINE || this == ADB_UNAUTHORIZED

    val isFastbootFamily: Boolean get() = this == FASTBOOT || this == FASTBOOTD

    val canFlashLogicalPartitions: Boolean get() = this == FASTBOOTD

    val canExecuteShell: Boolean get() = this == ADB_ONLINE || this == RECOVERY
}

enum class CpuPlatform(val displayName: String) {
    SNAPDRAGON("Qualcomm Snapdragon"),
    MEDIATEK("MediaTek Dimensity"),
    TENSOR("Google Tensor"),
    EXYNOS("Samsung Exynos"),
    GENERIC("Generic ARM64"),
    ;

    companion object {
        private val SNAPDRAGON_KEYS = listOf("qcom", "qualcomm", "sm", "sdm", "msm")
        private val MEDIATEK_KEYS = listOf("mt", "mediatek", "dimensity")
        private val TENSOR_KEYS = listOf("tensor", "gs101", "gs201", "zuma")
        private val EXYNOS_KEYS = listOf("exynos", "s5e")

        fun fromHardwareString(hardware: String?): CpuPlatform {
            if (hardware.isNullOrBlank()) return GENERIC
            val lower = hardware.lowercase()
            return when {
                SNAPDRAGON_KEYS.any { lower.contains(it) } -> SNAPDRAGON
                MEDIATEK_KEYS.any { lower.contains(it) } -> MEDIATEK
                TENSOR_KEYS.any { lower.contains(it) } -> TENSOR
                EXYNOS_KEYS.any { lower.contains(it) } -> EXYNOS
                else -> GENERIC
            }
        }
    }
}

enum class DeviceSlot {
    A,
    B,
    NONE,
    ;

    val suffix: String get() = when (this) {
        A -> "_a"
        B -> "_b"
        NONE -> ""
    }

    val alternate: DeviceSlot get() = when (this) {
        A -> B
        B -> A
        NONE -> NONE
    }

    companion object {
        fun fromSlotString(slot: String?): DeviceSlot = when (slot?.trim()?.lowercase()?.removePrefix("_")) {
            "a" -> A
            "b" -> B
            else -> NONE
        }
    }
}

/**
 * Full hardware and state profile of a connected Android device.
 */
data class DeviceProfile(
    val serial: String,
    val codename: String = "unknown",
    val mode: DeviceMode = DeviceMode.DISCONNECTED,
    val platform: CpuPlatform = CpuPlatform.GENERIC,
    val activeSlot: DeviceSlot = DeviceSlot.NONE,
    val isUnlocked: Boolean = false,
    val isAvbEnforced: Boolean = true,
    val batteryPercent: Int? = null,
    val productModel: String? = null,
) {
    val isDualSlot: Boolean get() = activeSlot != DeviceSlot.NONE

    val displayTitle: String get() = when {
        codename.isNotBlank() && codename != "unknown" -> "$codename ($serial)"
        productModel != null -> "$productModel ($serial)"
        else -> serial
    }

    companion object {
        val Disconnected = DeviceProfile(
            serial = "None",
            mode = DeviceMode.DISCONNECTED,
        )
    }
}
