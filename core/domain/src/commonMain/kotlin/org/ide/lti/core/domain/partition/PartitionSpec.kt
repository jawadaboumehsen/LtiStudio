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

/**
 * Filesystem type for Android partition images.
 */
enum class FilesystemType {
    EROFS,
    EXT4,
    F2FS,
    RAW,
}

/**
 * Compression algorithm used for EROFS or sparse packaging.
 */
enum class CompressionAlgorithm {
    NONE,
    LZ4,
    LZ4HC,
    DEFLATE,
}

/**
 * Specification of an individual partition within the ROM build.
 */
data class PartitionSpec(
    val name: String,
    val sizeBytes: Long,
    val filesystem: FilesystemType,
    val compression: CompressionAlgorithm = CompressionAlgorithm.NONE,
    val isDynamic: Boolean = true,
    val groupName: String = "qti_dynamic_partitions",
    val slotSuffix: String? = null,
)

/**
 * Calculated allocation details for a single partition within the super container.
 */
data class PartitionAllocation(
    val name: String,
    val rawSizeBytes: Long,
    val alignedSizeBytes: Long,
    val shareOfTotal: Float,
    val filesystem: FilesystemType,
    val compression: CompressionAlgorithm,
)

/**
 * Forecast of dynamic partition super storage budget, headroom, and overflow risks.
 */
data class PartitionBudgetForecast(
    val superCapacityBytes: Long,
    val metadataReserveBytes: Long,
    val usableCapacityBytes: Long,
    val allocatedBytes: Long,
    val freeHeadroomBytes: Long,
    val headroomPercent: Float,
    val isOverflown: Boolean,
    val overflowBytes: Long,
    val partitionBreakdown: List<PartitionAllocation>,
    val warnings: List<String>,
    val recommendations: List<String>,
)
