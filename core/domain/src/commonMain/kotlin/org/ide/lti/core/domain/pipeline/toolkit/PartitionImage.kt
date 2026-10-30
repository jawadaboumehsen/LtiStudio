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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Filesystem kind of an extracted partition image (data-model §PartitionImage).
 */
@Serializable
public enum class FsType {
    Ext4,
    F2fs,
    Erofs,
    Raw,
    Unknown,
}

public object FsTypes {
    /** Bytes to read, from offset [SUPERBLOCK_OFFSET], for [fromSuperblock] (`xxd -p -s 1024 -l 64`). */
    public const val SUPERBLOCK_OFFSET: Int = 1024
    public const val SUPERBLOCK_BYTES: Int = 64

    private val EROFS_MAGIC = byteArrayOf(0xE2.toByte(), 0xE1.toByte(), 0xF5.toByte(), 0xE0.toByte())
    private val F2FS_MAGIC = byteArrayOf(0x10, 0x20, 0xF5.toByte(), 0xF2.toByte())
    private val EXT4_MAGIC = byteArrayOf(0x53, 0xEF.toByte())
    private const val EXT4_MAGIC_AT = 56

    /** The filesystem of an image from the [SUPERBLOCK_BYTES] bytes at [SUPERBLOCK_OFFSET]. */
    public fun fromSuperblock(bytes: ByteArray): FsType = when {
        startsWith(bytes, 0, EROFS_MAGIC) -> FsType.Erofs
        startsWith(bytes, 0, F2FS_MAGIC) -> FsType.F2fs
        startsWith(bytes, EXT4_MAGIC_AT, EXT4_MAGIC) -> FsType.Ext4
        else -> FsType.Unknown
    }

    private fun startsWith(bytes: ByteArray, at: Int, magic: ByteArray): Boolean =
        bytes.size >= at + magic.size && magic.indices.all { bytes[at + it] == magic[it] }
}

/**
 * Descriptor for a partition image extracted from vendor firmware (contract: `contracts/step-results.md`).
 *
 * Used under result key `extract.images`.
 */
@Serializable
public data class PartitionImage(
    val name: String,
    val fsType: FsType,
    val sizeBytes: Long,
    val sparse: Boolean,
    val path: String,
) {
    public companion object {
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        public fun encodeList(images: List<PartitionImage>): String = json.encodeToString(images)

        public fun decodeList(string: String): List<PartitionImage> = json.decodeFromString(string)
    }
}
