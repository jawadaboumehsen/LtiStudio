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
public data class AvbFooterInfo(
    public val algorithmName: String = "",
    public val keyId: String = "",
    public val partitionName: String = "",
    public val imageSize: Long = 0L,
    public val originalFooterSize: Long = 0L,
)

/**
 * AVB signing plan displayed to user before signing (FR-024).
 */
@Serializable
public data class SigningPlan(
    public val partitionName: String,
    public val algorithmName: String,
    public val keyPath: String,
    public val partitionSize: Long,
)

/**
 * Result of removing dm-verity keys from a ramdisk (FR-025).
 */
@Serializable
public data class VerityRemovalResult(public val removedKeys: List<String>, public val restorePointId: String?)

/**
 * Supported root patchers (FR-026).
 */
@Serializable
public enum class RootPatcherType {
    MAGISK,
    APATCH,
}

/**
 * Detected root status of a boot image (FR-026).
 */
@Serializable
public sealed interface RootStatus {
    @Serializable
    public data object NotPatched : RootStatus

    @Serializable
    public data class AlreadyPatched(val patcher: RootPatcherType, val details: String = "") : RootStatus
}

/**
 * Device tree diff preview between original and edited device trees (FR-022).
 */
@Serializable
public data class DeviceTreeDiff(
    public val originalDts: String,
    public val newDts: String,
    public val differences: List<String> = emptyList(),
) {
    public val hasChanges: Boolean get() = differences.isNotEmpty()
}

/**
 * Header facts parsed from boot/vendor_boot/init_boot (FR-020).
 */
@Serializable
public data class BootHeaderFacts(
    public val headerVersion: Int = 0,
    public val osVersion: String = "",
    public val osPatchLevel: String = "",
    public val pageSize: Int = 2048,
    public val cmdline: String = "",
    public val kernelSize: Long = 0L,
    public val ramdiskSize: Long = 0L,
    public val dtbSize: Long = 0L,
    public val recoveryDtboSize: Long = 0L,
)

/**
 * Disassembled boot image components (FR-020).
 *
 * Key: `boot.unpack.<name>`
 */
@Serializable
public data class BootParts(
    public val imageName: String,
    public val kernelPath: String? = null,
    public val ramdiskFolderPath: String? = null,
    public val dtbPath: String? = null,
    public val header: BootHeaderFacts = BootHeaderFacts(),
    public val footer: AvbFooterInfo? = null,
    public val flags: Int = 0,
) {
    public val hasRamdisk: Boolean get() = !ramdiskFolderPath.isNullOrBlank()
    public val hasDtb: Boolean get() = !dtbPath.isNullOrBlank()
    public val hasFooter: Boolean get() = footer != null

    public companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        public fun fromJson(text: String): BootParts = json.decodeFromString(serializer(), text)
        public fun toJson(parts: BootParts): String = json.encodeToString(serializer(), parts)
    }
}
