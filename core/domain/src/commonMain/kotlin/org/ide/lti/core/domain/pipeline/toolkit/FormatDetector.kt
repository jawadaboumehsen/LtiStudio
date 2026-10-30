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

/**
 * Supported vendor firmware package formats classified from content magic bytes (FR-001).
 */
@Serializable
public enum class PackageFormat {
    Zip,
    SevenZ,
    Rar,
    Tar,
    Gz,
    Xz,
    Lz4,
    Bz2,
    Zstd,
    Brotli,
    AndroidPayload,
    SparseChunks,
    Ntpi,
    Unknown,
}

/**
 * Detects firmware container formats purely from header bytes and structure,
 * never from file extensions or names.
 */
public object FormatDetector {
    private val ZIP_MAGIC_LOCAL = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val ZIP_MAGIC_EMPTY = byteArrayOf(0x50, 0x4B, 0x05, 0x06)
    private val ZIP_MAGIC_SPANNED = byteArrayOf(0x50, 0x4B, 0x07, 0x08)

    private val SEVEN_Z_MAGIC = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
    private val RAR4_MAGIC = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)
    private val RAR5_MAGIC = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00)

    private val GZ_MAGIC = byteArrayOf(0x1F.toByte(), 0x8B.toByte())
    private val XZ_MAGIC = byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58, 0x5A, 0x00)
    private val LZ4_FRAME_MAGIC = byteArrayOf(0x04, 0x22, 0x4D, 0x18)
    private val LZ4_LEGACY_MAGIC = byteArrayOf(0x02, 0x21, 0x4C, 0x18)
    private val BZ2_MAGIC = byteArrayOf(0x42, 0x5A, 0x68) // BZh
    private val ZSTD_MAGIC = byteArrayOf(0x28, 0xB5.toByte(), 0x2F, 0xFD.toByte())

    private val PAYLOAD_CRAU_MAGIC = byteArrayOf(0x43, 0x72, 0x41, 0x55) // CrAU
    private val ANDROID_SPARSE_MAGIC = byteArrayOf(0x3A, 0xFF.toByte(), 0x26, 0xED.toByte()) // 0xED26FF3A LE
    private val NTPI_MAGIC = byteArrayOf(0x4E, 0x54, 0x50, 0x49) // NTPI

    private const val HEX_RADIX = 16
    private val USTAR_MAGIC = byteArrayOf(0x75, 0x73, 0x74, 0x61, 0x72) // ustar at offset 257
    private const val USTAR_OFFSET = 257

    /** Magic numbers at offset 0, checked in this order (a payload or sparse image is never mistaken for a zip). */
    private val SIGNATURES = listOf(
        PAYLOAD_CRAU_MAGIC to PackageFormat.AndroidPayload,
        ANDROID_SPARSE_MAGIC to PackageFormat.SparseChunks,
        NTPI_MAGIC to PackageFormat.Ntpi,
        SEVEN_Z_MAGIC to PackageFormat.SevenZ,
        RAR5_MAGIC to PackageFormat.Rar,
        RAR4_MAGIC to PackageFormat.Rar,
        ZIP_MAGIC_LOCAL to PackageFormat.Zip,
        ZIP_MAGIC_EMPTY to PackageFormat.Zip,
        ZIP_MAGIC_SPANNED to PackageFormat.Zip,
        XZ_MAGIC to PackageFormat.Xz,
        ZSTD_MAGIC to PackageFormat.Zstd,
        LZ4_FRAME_MAGIC to PackageFormat.Lz4,
        LZ4_LEGACY_MAGIC to PackageFormat.Lz4,
        BZ2_MAGIC to PackageFormat.Bz2,
        GZ_MAGIC to PackageFormat.Gz,
    )

    /**
     * The [PackageFormat] of a file from its first bytes (at least 512 for TAR). What a zip or tar holds
     * (payload, raw images, block OTA, Odin) is decided later from its member list ([FirmwareLayouts]).
     */
    public fun detect(bytes: ByteArray): PackageFormat =
        SIGNATURES.firstOrNull { (magic, _) -> matches(bytes, 0, magic) }?.second
            ?: PackageFormat.Tar.takeIf { matches(bytes, USTAR_OFFSET, USTAR_MAGIC) }
            ?: PackageFormat.Unknown

    /** The bytes of an `xxd -p` dump (whitespace ignored). */
    public fun fromHex(hex: String): ByteArray {
        val clean = hex.filter { !it.isWhitespace() }
        return ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(HEX_RADIX).toByte() }
    }

    private fun matches(source: ByteArray, offset: Int, pattern: ByteArray): Boolean =
        source.size >= offset + pattern.size && pattern.indices.all { source[offset + it] == pattern[it] }
}
