/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.setup

/**
 * The single definition of the activity retention bounds (FR-011). It lives in core/model because the
 * persisted journal (core/datastore) must apply the same limits as the domain/adapter buffers, and
 * core/datastore cannot depend on core/domain. core/domain re-exports these as `SetupLogConstants`.
 *
 * "Each producer and presentation buffer retains at most 10,000 lines and 8 MiB of UTF-8 payload;
 * each line is at most 16 KiB including its truncation marker."
 */
public object SetupLogBounds {
    public const val MAX_BUFFER_LINES: Int = 10_000
    public const val MAX_PAYLOAD_BYTES: Long = 8L * 1024L * 1024L
    public const val MAX_LINE_BYTES: Int = 16 * 1024
    public const val TRUNCATION_MARKER: String = "... [truncated]"

    private const val ONE_BYTE_MAX = 0x7F
    private const val TWO_BYTE_MAX = 0x7FF
    private const val SURROGATE_PAIR_BYTES = 4
    private const val THREE_BYTES = 3
    private const val TWO_BYTES = 2

    /**
     * Truncates [text] so its UTF-8 encoding is at most [maxBytes] including [marker], cutting only on
     * code point boundaries (a surrogate pair is kept or dropped as a unit, never halved).
     */
    public fun truncateToUtf8Bytes(
        text: String,
        maxBytes: Int = MAX_LINE_BYTES,
        marker: String = TRUNCATION_MARKER,
    ): String {
        if (text.encodeToByteArray().size <= maxBytes) return text

        val availableBytes = maxBytes - marker.encodeToByteArray().size
        require(availableBytes >= 0) { "maxBytes must accommodate marker bytes" }

        var usedBytes = 0
        var cut = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val isPair = c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()
            val charLen = if (isPair) 2 else 1
            val byteLen = when {
                isPair -> SURROGATE_PAIR_BYTES
                c.code <= ONE_BYTE_MAX -> 1
                c.code <= TWO_BYTE_MAX -> TWO_BYTES
                else -> THREE_BYTES
            }
            if (usedBytes + byteLen > availableBytes) break
            usedBytes += byteLen
            i += charLen
            cut = i
        }
        return text.substring(0, cut) + marker
    }
}
