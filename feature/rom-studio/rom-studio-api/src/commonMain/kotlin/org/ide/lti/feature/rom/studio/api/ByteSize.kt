/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.api

/** A file size for people: `512 B`, `8.4 KB`, `31 MB`, `3.9 GB` (binary units, one decimal below 100). */
public fun formatBytes(bytes: Long): String {
    if (bytes < KILO) return "$bytes B"
    var value = bytes.toDouble()
    var unit = 0
    while (value >= KILO && unit < UNITS.lastIndex) {
        value /= KILO
        unit++
    }
    val shown = if (value >= HUNDRED) {
        kotlin.math.round(value).toLong().toString()
    } else {
        val tenths = kotlin.math.round(value * TEN).toLong()
        "${tenths / 10}.${tenths % 10}"
    }
    return "$shown ${UNITS[unit]}"
}

private val UNITS = listOf("B", "KB", "MB", "GB", "TB")
private const val KILO = 1024.0
private const val HUNDRED = 100.0
private const val TEN = 10.0
