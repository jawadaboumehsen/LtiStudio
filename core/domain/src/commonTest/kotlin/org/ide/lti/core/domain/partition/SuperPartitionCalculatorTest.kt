/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.partition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuperPartitionCalculatorTest {

    private val calculator = SuperPartitionCalculator()

    @Test
    fun calculateBudgetCalculatesHeadroomCorrectlyWhenWithinCapacity() {
        val superCapacity = 9_000_000_000L // ~9 GB
        val partitions = listOf(
            PartitionSpec("system", 3_000_000_000L, FilesystemType.EROFS, CompressionAlgorithm.LZ4HC),
            PartitionSpec("vendor", 1_000_000_000L, FilesystemType.EROFS, CompressionAlgorithm.LZ4HC),
            PartitionSpec("product", 2_000_000_000L, FilesystemType.EROFS, CompressionAlgorithm.LZ4HC),
            PartitionSpec("system_ext", 500_000_000L, FilesystemType.EROFS, CompressionAlgorithm.LZ4HC),
        )

        val forecast = calculator.calculateBudget(superCapacity, partitions)

        assertFalse(forecast.isOverflown)
        assertEquals(0L, forecast.overflowBytes)
        assertTrue(forecast.freeHeadroomBytes > 2_000_000_000L)
        assertTrue(forecast.headroomPercent > 0.20f)
        assertEquals(4, forecast.partitionBreakdown.size)
    }

    @Test
    fun calculateBudgetFlagsOverflowWhenPartitionsExceedCapacity() {
        val superCapacity = 5_000_000_000L // 5 GB
        val partitions = listOf(
            PartitionSpec("system", 3_500_000_000L, FilesystemType.EXT4),
            PartitionSpec("vendor", 1_500_000_000L, FilesystemType.EXT4),
            PartitionSpec("product", 1_000_000_000L, FilesystemType.EXT4),
        )

        val forecast = calculator.calculateBudget(superCapacity, partitions)

        assertTrue(forecast.isOverflown)
        assertTrue(forecast.overflowBytes > 1_000_000_000L)
        assertEquals(0f, forecast.headroomPercent)
        assertTrue(forecast.warnings.any { it.contains("capacity exceeded") })
        // Check recommendations include EROFS conversion
        assertTrue(forecast.recommendations.any { it.contains("Converting 'system' from EXT4 to EROFS") })
    }

    @Test
    fun calculateBudgetAlignsToBlockBoundaries() {
        val superCapacity = 10_000_000L
        // should align to 8192 on 4096 block
        val partitions = listOf(
            PartitionSpec("vendor", 4097L, FilesystemType.EROFS),
        )

        val forecast = calculator.calculateBudget(superCapacity, partitions, blockSizeBytes = 4096L)
        val vendorAlloc = forecast.partitionBreakdown.first()

        assertEquals(4097L, vendorAlloc.rawSizeBytes)
        assertEquals(8192L, vendorAlloc.alignedSizeBytes)
    }

    @Test
    fun estimateErofsSavingsEstimatesCorrectReduction() {
        val ext4Size = 1_000_000_000L // 1 GB
        val savings = calculator.estimateErofsSavings(ext4Size, CompressionAlgorithm.LZ4HC)

        // LZ4HC ratio is 0.65 -> savings should be ~350 MB (350,000,000 bytes)
        assertEquals(350_000_000L, savings)
    }
}
