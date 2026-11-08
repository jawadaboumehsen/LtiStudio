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
import kotlinx.serialization.json.Json

/**
 * A partition group definition inside a dynamic partition (super) image.
 */
@Serializable
public data class SuperGroup(val name: String, val maxSize: Long) {
    init {
        require(name.isNotBlank()) { "Group name must not be blank" }
        require(maxSize >= 0L) { "Group maxSize must be non-negative" }
    }
}

/**
 * A logical partition inside a dynamic partition (super) image.
 */
@Serializable
public data class SuperPartition(val name: String, val group: String, val size: Long) {
    init {
        require(name.isNotBlank()) { "Partition name must not be blank" }
        require(group.isNotBlank()) { "Group name must not be blank" }
        require(size >= 0L) { "Partition size must be non-negative" }
    }
}

/**
 * Layout model of a dynamic partition container (contracts: `step-results.md`, US2).
 *
 * Key: `super.layout`
 */
@Serializable
public data class SuperLayout(
    val superSize: Long,
    val groups: List<SuperGroup> = emptyList(),
    val partitions: List<SuperPartition> = emptyList(),
    val reserve: Long = 0L,
    val alignment: Long = 4096L,
    val freeBytes: Long = 0L,
    val shortBy: Long = 0L,
    val fits: Boolean = true,
    val reason: String? = null,
) {
    public companion object {
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        public fun encode(layout: SuperLayout): String = json.encodeToString(serializer(), layout)
        public fun decode(string: String): SuperLayout = json.decodeFromString(serializer(), string)
    }
}

/**
 * Planner calculating layout fitting, free space, and shortfalls for dynamic partitions (super).
 */
public object SuperLayoutPlanner {
    public const val DEFAULT_ALIGNMENT: Long = 4096L

    public fun alignUp(size: Long, alignment: Long = DEFAULT_ALIGNMENT): Long {
        if (alignment <= 0L || size <= 0L) return maxOf(0L, size)
        val remainder = size % alignment
        return if (remainder == 0L) size else size + (alignment - remainder)
    }

    /** Why [groups] and [partitions] cannot describe one unambiguous super, or null when they can. */
    private fun structureProblem(groups: List<SuperGroup>, partitions: List<SuperPartition>): String? {
        val names = groups.map { it.name }
        val parts = partitions.map { it.name }
        return names.firstOrNull { name -> names.count { it == name } > 1 }?.let { "Group '$it' is listed twice" }
            ?: partitions.firstOrNull { it.group !in names }?.let { "'${it.name}' is in unknown group '${it.group}'" }
            ?: parts.firstOrNull { name -> parts.count { it == name } > 1 }?.let { "Partition '$it' is listed twice" }
    }

    public fun plan(
        superSize: Long,
        groups: List<SuperGroup>,
        partitions: List<SuperPartition>,
        reserve: Long = 0L,
        alignment: Long = DEFAULT_ALIGNMENT,
        metadataOverhead: Long = 0L,
    ): SuperLayout {
        require(superSize >= 0L) { "superSize must be non-negative" }
        require(reserve >= 0L) { "reserve must be non-negative" }
        require(alignment > 0L) { "alignment must be positive" }

        val invalid = structureProblem(groups, partitions)

        val partitionsByGroup = partitions.groupBy { it.group }

        // Check group constraints
        var groupOverflow: Pair<SuperGroup, Long>? = null
        for (group in groups) {
            val groupPartitions = partitionsByGroup[group.name].orEmpty()
            val groupUsed = groupPartitions.sumOf { alignUp(it.size, alignment) }
            if (group.maxSize > 0L && groupUsed > group.maxSize) {
                groupOverflow = group to (groupUsed - group.maxSize)
                break
            }
        }

        val totalAlignedPartitionBytes = partitions.sumOf { alignUp(it.size, alignment) }
        val totalRequired = totalAlignedPartitionBytes + reserve + metadataOverhead
        val superShortBy = maxOf(0L, totalRequired - superSize)
        val freeBytes = maxOf(0L, superSize - totalRequired)

        val fits = superShortBy == 0L && groupOverflow == null && invalid == null
        val shortBy = if (superShortBy > 0L) {
            superShortBy
        } else {
            groupOverflow?.second ?: 0L
        }

        val reason = when {
            invalid != null -> invalid
            superShortBy > 0L -> "Super partition is short by $superShortBy bytes"
            groupOverflow != null -> "Group '${groupOverflow.first.name}' is short by ${groupOverflow.second} bytes"
            else -> null
        }

        return SuperLayout(
            superSize = superSize,
            groups = groups,
            partitions = partitions,
            reserve = reserve,
            alignment = alignment,
            freeBytes = freeBytes,
            shortBy = shortBy,
            fits = fits,
            reason = reason,
        )
    }
}
