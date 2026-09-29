/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.text

import kotlin.math.ceil

public object AvbSizeCalculator {

    public fun computeReserveBytes(imageSizeBytes: Long): Long {
        val expanded = ceil(imageSizeBytes * 1.003 + 999.0 / 1000.0).toLong()
        val rawOverhead = expanded - imageSizeBytes
        val minOverhead = maxOf(rawOverhead, 262144L)
        return roundTo4KiB(minOverhead)
    }

    public fun roundTo4KiB(bytes: Long): Long {
        return (bytes + 4095L) / 4096L * 4096L
    }

    public suspend fun findPartitionSize(
        imageSizeBytes: Long,
        calcMaxImageSize: suspend (partitionSize: Long) -> Long,
    ): Long {
        val reserve = computeReserveBytes(imageSizeBytes)
        var low = roundTo4KiB(imageSizeBytes)
        var high = roundTo4KiB(imageSizeBytes + reserve * 2)

        var probes = 0
        while (calcMaxImageSize(high) < imageSizeBytes && probes < 20) {
            high += reserve
            probes++
        }
        if (probes >= 20) {
            return roundTo4KiB(imageSizeBytes + reserve)
        }

        var optimal = high
        var iterations = 0
        while (low <= high && iterations < 30) {
            iterations++
            val mid = roundTo4KiB(low + (high - low) / 2)
            val maxImage = calcMaxImageSize(mid)
            if (maxImage >= imageSizeBytes) {
                optimal = mid
                high = mid - 4096L
            } else {
                low = mid + 4096L
            }
        }
        return optimal
    }
}
