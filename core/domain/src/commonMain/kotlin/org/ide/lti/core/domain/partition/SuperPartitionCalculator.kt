/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.core.domain.partition

/**
 * High-performance budget and geometry calculator for Android Dynamic Partitions (super.img).
 * Follows AOSP `lpmake` block alignment rules and calculates headroom and compression savings.
 */
class SuperPartitionCalculator {

    companion object {
        const val DEFAULT_BLOCK_SIZE_BYTES: Long = 4096L
        const val DEFAULT_METADATA_SLOT_RESERVE: Long = 1048576L // 1MB for primary + 1MB for backup metadata

        // Average compression ratio achieved by EROFS LZ4HC compared to uncompressed raw EXT4
        const val ESTIMATED_EROFS_LZ4HC_RATIO: Float = 0.65f // ~35% size reduction
        const val ESTIMATED_EROFS_LZ4_RATIO: Float = 0.72f // ~28% size reduction
    }

    /**
     * Calculates the super partition budget, alignment allocations, and headroom.
     *
     * @param superCapacityBytes Physical capacity of the super partition in bytes.
     * @param partitions List of planned partition specifications.
     * @param blockSizeBytes Block size alignment (typically 4096 bytes).
     * @param metadataReserveBytes Size reserved for LP metadata slots (default 1MB).
     */
    fun calculateBudget(
        superCapacityBytes: Long,
        partitions: List<PartitionSpec>,
        blockSizeBytes: Long = DEFAULT_BLOCK_SIZE_BYTES,
        metadataReserveBytes: Long = DEFAULT_METADATA_SLOT_RESERVE,
    ): PartitionBudgetForecast {
        val usableCapacity = maxOf(0L, superCapacityBytes - (metadataReserveBytes * 2))

        val dynamicPartitions = partitions.filter { it.isDynamic }
        val allocations = mutableListOf<PartitionAllocation>()
        var totalAllocated = 0L

        for (part in dynamicPartitions) {
            val alignedSize = alignToBlockSize(part.sizeBytes, blockSizeBytes)
            totalAllocated += alignedSize
        }

        for (part in dynamicPartitions) {
            val alignedSize = alignToBlockSize(part.sizeBytes, blockSizeBytes)
            val share = if (totalAllocated > 0L) {
                (alignedSize.toDouble() / totalAllocated.toDouble()).toFloat()
            } else {
                0f
            }
            allocations.add(
                PartitionAllocation(
                    name = part.name,
                    rawSizeBytes = part.sizeBytes,
                    alignedSizeBytes = alignedSize,
                    shareOfTotal = share,
                    filesystem = part.filesystem,
                    compression = part.compression,
                ),
            )
        }

        val isOverflown = totalAllocated > usableCapacity
        val overflowBytes = if (isOverflown) totalAllocated - usableCapacity else 0L
        val freeHeadroom = if (isOverflown) 0L else usableCapacity - totalAllocated
        val headroomPercent = if (usableCapacity > 0L && !isOverflown) {
            (freeHeadroom.toDouble() / usableCapacity.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

        val warnings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        if (isOverflown) {
            val overflowMb = overflowBytes / (1024 * 1024)
            warnings.add("Super partition capacity exceeded by $overflowMb MB ($overflowBytes bytes). lpmake will fail.")
        } else if (headroomPercent < 0.05f) {
            val freeMb = freeHeadroom / (1024 * 1024)
            warnings.add("Low storage headroom: only $freeMb MB (${(headroomPercent * 100).toInt()}%) remaining in super partition.")
        }

        // Identify conversion opportunities to EROFS for EXT4 partitions
        for (part in dynamicPartitions) {
            if (part.filesystem == FilesystemType.EXT4) {
                val estimatedSavings = estimateErofsSavings(part.sizeBytes, CompressionAlgorithm.LZ4HC)
                val savingsMb = estimatedSavings / (1024 * 1024)
                if (savingsMb > 20L) {
                    recommendations.add("Converting '${part.name}' from EXT4 to EROFS (LZ4HC) could recover approximately $savingsMb MB of space.")
                }
            }
        }

        return PartitionBudgetForecast(
            superCapacityBytes = superCapacityBytes,
            metadataReserveBytes = metadataReserveBytes,
            usableCapacityBytes = usableCapacity,
            allocatedBytes = totalAllocated,
            freeHeadroomBytes = freeHeadroom,
            headroomPercent = headroomPercent,
            isOverflown = isOverflown,
            overflowBytes = overflowBytes,
            partitionBreakdown = allocations,
            warnings = warnings,
            recommendations = recommendations,
        )
    }

    /**
     * Estimates the storage savings in bytes achieved by converting an EXT4 partition to compressed EROFS.
     */
    fun estimateErofsSavings(
        currentSizeBytes: Long,
        compression: CompressionAlgorithm = CompressionAlgorithm.LZ4HC,
    ): Long {
        val ratio = when (compression) {
            CompressionAlgorithm.LZ4HC -> ESTIMATED_EROFS_LZ4HC_RATIO
            CompressionAlgorithm.LZ4 -> ESTIMATED_EROFS_LZ4_RATIO
            else -> 0.85f
        }
        val estimatedSize = (currentSizeBytes * ratio).toLong()
        return maxOf(0L, currentSizeBytes - estimatedSize)
    }

    /**
     * Rounds [sizeBytes] up to the nearest multiple of [blockSizeBytes].
     */
    private fun alignToBlockSize(sizeBytes: Long, blockSizeBytes: Long): Long {
        if (blockSizeBytes <= 1L || sizeBytes <= 0L) return sizeBytes
        val remainder = sizeBytes % blockSizeBytes
        return if (remainder == 0L) sizeBytes else sizeBytes + (blockSizeBytes - remainder)
    }
}
