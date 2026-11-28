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

/** Where a partition's projected size comes from. */
enum class BudgetSizeSource(val label: String) {
    STOCK("Stock estimate"),
    BUILD("Last-build estimate"),
    TRIAL("Measured trial"),
    KEPT("Measured stock"),
    UNKNOWN("Unknown"),
}

data class PartitionBudget(
    val partition: String,
    val before: Long?,
    val projected: Long?,
    val source: BudgetSizeSource,
) {
    val growth: Long? get() = if (before != null && projected != null) projected - before else null
}

data class GroupBudget(val name: String, val limit: Long, val rows: List<PartitionBudget>) {
    val beforeBytes: Long? get() = rows.mapNotNull { it.before }.takeIf { it.size == rows.size }?.sum()
    val projectedBytes: Long? get() = rows.mapNotNull { it.projected }.takeIf { it.size == rows.size }?.sum()
}

/**
 * How the dynamic partitions fill the super group: each partition's built
 * image, else its stock image as the estimate, against the group's bytes.
 */
data class SuperBudget(val rows: List<Row>, val groupBytes: Long) {
    /** [bytes] is the built image, or the stock one when [estimated]; null when neither exists. */
    data class Row(val partition: String, val filesystem: String, val bytes: Long?, val estimated: Boolean)

    val usedBytes: Long get() = rows.sumOf { it.bytes ?: 0L }
    val freeBytes: Long get() = groupBytes - usedBytes
    val fits: Boolean get() = freeBytes >= 0

    /** True when a row is a stock estimate or has no size at all: the real total is known only after a build. */
    val estimated: Boolean get() = rows.any { it.estimated || it.bytes == null }

    companion object {
        private const val MIB = 1024L * 1024

        fun of(
            partitions: List<String>,
            filesystems: Map<String, String>,
            built: Map<String, Long>,
            stock: Map<String, Long>,
            groupBytes: Long,
        ): SuperBudget = SuperBudget(
            partitions.map { p ->
                val own = built[p]
                Row(p, filesystems[p].orEmpty(), own ?: stock[p], estimated = own == null && stock[p] != null)
            },
            groupBytes,
        )

        /** Projection for stock metadata groups. Empty A/B members keep their measured zero allocation. */
        fun groups(
            layout: SuperLayout?,
            fallback: SuperBudget,
            stock: Map<String, Long>,
            built: Map<String, Long>,
            trials: Map<String, Long>,
            keepStock: Set<String>,
        ): List<GroupBudget> {
            fun row(name: String, original: Long?): PartitionBudget {
                val partition = name.removeSuffix("_a").removeSuffix("_b")
                val stockSize = stock[partition] ?: original
                val keptSource = if (stockSize == null) BudgetSizeSource.UNKNOWN else BudgetSizeSource.KEPT
                val (bytes, source) = when {
                    original == 0L -> original to BudgetSizeSource.KEPT
                    partition in keepStock -> stockSize to keptSource
                    trials[partition] != null -> trials[partition] to BudgetSizeSource.TRIAL
                    built[partition] != null -> built[partition] to BudgetSizeSource.BUILD
                    stockSize != null -> stockSize to BudgetSizeSource.STOCK
                    else -> null to BudgetSizeSource.UNKNOWN
                }
                return PartitionBudget(name, original ?: stock[partition], bytes, source)
            }
            val measured = layout?.groups.orEmpty().filter { it.maxSize > 0 }.mapNotNull { group ->
                val rows = layout?.partitions.orEmpty().filter { it.group == group.name }.map { row(it.name, it.size) }
                if (rows.isEmpty()) null else GroupBudget(group.name, group.maxSize, rows)
            }
            return measured.ifEmpty {
                listOf(
                    GroupBudget(
                        "Device profile group (estimated layout)",
                        fallback.groupBytes,
                        fallback.rows.map { row(it.partition, null) },
                    ),
                )
            }
        }

        /** The build's refusal when [sizes] (partition to built bytes) overflow [group] of [groupBytes]. */
        fun overflow(sizes: Map<String, Long>, group: String, groupBytes: Long): String? {
            val total = sizes.values.sum()
            if (total <= groupBytes) return null
            val largest = sizes.entries.sortedByDescending {
                it.value
            }.joinToString { "${it.key} ${it.value / MIB} MiB" }
            return "SUPER_FULL: the images take ${total / MIB} MiB, the super group $group holds ${groupBytes / MIB} " +
                "MiB (${(total - groupBytes) / MIB} MiB over): $largest. Build the large ones as EROFS, compress " +
                "them more, or remove apps in Debloat."
        }
    }
}
