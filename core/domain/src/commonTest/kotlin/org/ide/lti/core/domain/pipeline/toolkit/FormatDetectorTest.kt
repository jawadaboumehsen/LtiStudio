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

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatDetectorTest {
    @Test
    fun detectsZipFromMagicBytes() {
        val bytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00)
        assertEquals(PackageFormat.Zip, FormatDetector.detect(bytes))
    }

    @Test
    fun detectsSevenZFromMagicBytes() {
        val bytes = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
        assertEquals(PackageFormat.SevenZ, FormatDetector.detect(bytes))
    }

    @Test
    fun detectsRar4AndRar5FromMagicBytes() {
        val rar4 = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)
        assertEquals(PackageFormat.Rar, FormatDetector.detect(rar4))

        val rar5 = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00)
        assertEquals(PackageFormat.Rar, FormatDetector.detect(rar5))
    }

    @Test
    fun detectsTarFromUStarAtOffset257() {
        val bytes = ByteArray(512)
        // "ustar" at offset 257
        val ustar = "ustar".encodeToByteArray()
        ustar.copyInto(bytes, destinationOffset = 257)
        assertEquals(PackageFormat.Tar, FormatDetector.detect(bytes))
    }

    @Test
    fun detectsGzFromMagicBytes() {
        val bytes = byteArrayOf(0x1F.toByte(), 0x8B.toByte(), 0x08, 0x00)
        assertEquals(PackageFormat.Gz, FormatDetector.detect(bytes))
    }

    @Test
    fun detectsXzFromMagicBytes() {
        val bytes = byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58, 0x5A, 0x00)
        assertEquals(PackageFormat.Xz, FormatDetector.detect(bytes))
    }

    @Test
    fun detectsLz4FromMagicBytes() {
        val bytes = byteArrayOf(0x04, 0x22, 0x4D, 0x18)
        assertEquals(PackageFormat.Lz4, FormatDetector.detect(bytes))

        val legacy = byteArrayOf(0x02, 0x21, 0x4C, 0x18)
        assertEquals(PackageFormat.Lz4, FormatDetector.detect(legacy))
    }

    @Test
    fun detectsBz2FromMagicBytes() {
        val bytes = byteArrayOf(0x42, 0x5A, 0x68) // "BZh"
        assertEquals(PackageFormat.Bz2, FormatDetector.detect(bytes))
    }

    @Test
    fun detectsZstdFromMagicBytes() {
        val bytes = byteArrayOf(0x28, 0xB5.toByte(), 0x2F, 0xFD.toByte())
        assertEquals(PackageFormat.Zstd, FormatDetector.detect(bytes))
    }

    @Test
    fun detectsPayloadCrAUFromMagicBytes() {
        val bytes = byteArrayOf(0x43, 0x72, 0x41, 0x55) // "CrAU"
        assertEquals(PackageFormat.AndroidPayload, FormatDetector.detect(bytes))
    }

    @Test
    fun detectsSparseChunksFromMagicBytes() {
        val bytes = byteArrayOf(0x3A, 0xFF.toByte(), 0x26, 0xED.toByte())
        assertEquals(PackageFormat.SparseChunks, FormatDetector.detect(bytes))
    }

    @Test
    fun detectsNtpiFromMagicBytes() {
        val bytes = byteArrayOf(0x4E, 0x54, 0x50, 0x49) // "NTPI"
        assertEquals(PackageFormat.Ntpi, FormatDetector.detect(bytes))
    }

    @Test
    fun detectsUnknownFromRandomBytes() {
        val bytes = byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44)
        assertEquals(PackageFormat.Unknown, FormatDetector.detect(bytes))
    }

    @Test
    fun handlesShortOrEmptyByteArrayGracefully() {
        assertEquals(PackageFormat.Unknown, FormatDetector.detect(byteArrayOf()))
        assertEquals(PackageFormat.Unknown, FormatDetector.detect(byteArrayOf(0x50)))
    }
}
