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

/** How a firmware container lays out its partitions, decided from its member list (FR-001..FR-005). */
public enum class FirmwareLayout {
    /** A full A/B OTA: `payload.bin`. */
    Payload,

    /** A block-based OTA: `<p>.transfer.list` with `<p>.new.dat[.br]`. */
    BlockOta,

    /** A Samsung Odin set: `AP_*.tar[.md5]` carrying `*.img.lz4`. */
    Odin,

    /** Images split into `<p>.img_sparsechunk.N` parts. */
    SparseChunks,

    /** Plain partition images (`super.img`, `boot.img`, ...). */
    RawImages,

    Unknown,
}

public object FirmwareLayouts {
    /** The layout of a container whose members (relative paths) are [members]; the first match wins. */
    public fun classify(members: List<String>): FirmwareLayout {
        val names = members.map { it.substringAfterLast('/') }
        return when {
            names.any { it == "payload.bin" } -> FirmwareLayout.Payload
            names.any { it.endsWith(".transfer.list") } -> FirmwareLayout.BlockOta
            names.any { OdinExtractor.classifySlot(it) == OdinSlot.AP } -> FirmwareLayout.Odin
            names.any { ChunkExtractor.isSparseChunk(it) } -> FirmwareLayout.SparseChunks
            names.any { it.endsWith(".img") } -> FirmwareLayout.RawImages
            else -> FirmwareLayout.Unknown
        }
    }

    /** The member whose file name is [fileName], wherever it sits in the container. */
    public fun member(members: List<String>, fileName: String): String? =
        members.firstOrNull { it.substringAfterLast('/') == fileName }

    /** Splits a `find -printf '%P\n'` / `unzip -Z1` / `tar -tf` listing into member paths (no directories). */
    public fun parseListing(listing: String): List<String> =
        listing.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.endsWith("/") }.toList()
}
