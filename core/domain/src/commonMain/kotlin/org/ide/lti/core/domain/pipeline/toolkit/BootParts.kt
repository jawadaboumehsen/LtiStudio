/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.toolkit

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * AVB footer details if present on the partition (FR-024).
 */
@Serializable
data class AvbFooterInfo(
    val algorithmName: String = "",
    val keyId: String = "",
    val partitionName: String = "",
    val imageSize: Long = 0L,
    val originalFooterSize: Long = 0L,
)

/**
 * AVB signing plan displayed to user before signing (FR-024).
 */
@Serializable
data class SigningPlan(
    val partitionName: String,
    val algorithmName: String,
    val keyPath: String,
    val partitionSize: Long,
)

/**
 * Result of removing dm-verity keys from a ramdisk (FR-025).
 */
@Serializable
data class VerityRemovalResult(val removedKeys: List<String>, val restorePointId: String?)

/**
 * Supported root patchers (FR-026).
 */
@Serializable
enum class RootPatcherType {
    MAGISK,
    APATCH,
}

/**
 * Detected root status of a boot image (FR-026).
 */
@Serializable
sealed interface RootStatus {
    @Serializable
    data object NotPatched : RootStatus

    @Serializable
    data class AlreadyPatched(val patcher: RootPatcherType, val details: String = "") : RootStatus
}

/**
 * Device tree diff preview between original and edited device trees (FR-022).
 */
@Serializable
data class DeviceTreeDiff(val originalDts: String, val newDts: String, val differences: List<String> = emptyList()) {
    val hasChanges: Boolean get() = differences.isNotEmpty()
}

/**
 * Header facts parsed from boot/vendor_boot/init_boot (FR-020).
 */
@Serializable
data class BootHeaderFacts(
    val headerVersion: Int = 0,
    val osVersion: String = "",
    val osPatchLevel: String = "",
    val pageSize: Int = 2048,
    val cmdline: String = "",
    val kernelSize: Long = 0L,
    val ramdiskSize: Long = 0L,
    val dtbSize: Long = 0L,
    val recoveryDtboSize: Long = 0L,
)

/**
 * Disassembled boot image components (FR-020).
 *
 * Key: `boot.unpack.<name>`
 */
@Serializable
data class BootParts(
    val imageName: String,
    val kernelPath: String? = null,
    val ramdiskFolderPath: String? = null,
    val dtbPath: String? = null,
    val header: BootHeaderFacts = BootHeaderFacts(),
    val footer: AvbFooterInfo? = null,
    val flags: Int = 0,
) {
    val hasRamdisk: Boolean get() = !ramdiskFolderPath.isNullOrBlank()
    val hasDtb: Boolean get() = !dtbPath.isNullOrBlank()
    val hasFooter: Boolean get() = footer != null

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        fun fromJson(text: String): BootParts = json.decodeFromString(serializer(), text)
        fun toJson(parts: BootParts): String = json.encodeToString(serializer(), parts)
    }
}
