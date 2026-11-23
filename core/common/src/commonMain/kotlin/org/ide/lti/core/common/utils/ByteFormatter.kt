/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.common.utils

import kotlin.math.round

/**
 * Shared utility for formatting byte counts into human-readable strings (B, KB, MB, GB, TB).
 */
public object ByteFormatter {
    private val UNITS = listOf("B", "KB", "MB", "GB", "TB")
    private const val KILO = 1024.0
    private const val HUNDRED = 100.0
    private const val TEN = 10.0

    public fun format(bytes: Long): String {
        if (bytes < KILO) return "$bytes B"
        var value = bytes.toDouble()
        var unit = 0
        while (value >= KILO && unit < UNITS.lastIndex) {
            value /= KILO
            unit++
        }
        val shown = if (value >= HUNDRED) {
            round(value).toLong().toString()
        } else {
            val tenths = round(value * TEN).toLong()
            "${tenths / 10}.${tenths % 10}"
        }
        return "$shown ${UNITS[unit]}"
    }
}

public fun formatBytes(bytes: Long): String = ByteFormatter.format(bytes)
