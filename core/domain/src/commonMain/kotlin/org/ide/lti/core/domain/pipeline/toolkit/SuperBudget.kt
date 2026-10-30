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

/**
 * How the dynamic partitions fill the super group: each partition's built
 * image, else its stock image as the estimate, against the group's bytes.
 */
public data class SuperBudget(val rows: List<Row>, val groupBytes: Long) {
    /** [bytes] is the built image, or the stock one when [estimated]; null when neither exists. */
    public data class Row(val partition: String, val filesystem: String, val bytes: Long?, val estimated: Boolean)

    public val usedBytes: Long get() = rows.sumOf { it.bytes ?: 0L }
    public val freeBytes: Long get() = groupBytes - usedBytes
    public val fits: Boolean get() = freeBytes >= 0

    /** True when a row is a stock estimate: the real total is known only after a build. */
    public val estimated: Boolean get() = rows.any { it.estimated }

    public companion object {
        private const val MIB = 1024L * 1024

        public fun of(
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

        /** The build's refusal when [sizes] (partition to built bytes) overflow [group] of [groupBytes]. */
        public fun overflow(sizes: Map<String, Long>, group: String, groupBytes: Long): String? {
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
