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
 * Adapter for reading (`lpdump`) and building (`lpmake`) dynamic partition (super) containers
 * (US2, contracts: `step-results.md`).
 *
 * Key: `super.layout`
 */
public object SuperTool {

    /**
     * Parses `lpdump` output (AOSP `PrintMetadata`: "Partition table", "Block device table" and "Group table"
     * sections) into a [SuperLayout] planned with [SuperLayoutPlanner]. A partition's size is the sum of its
     * linear extents (`<first> .. <last> linear <device> <sector>`, 512-byte sectors).
     */
    public fun parseLpdumpOutput(output: String): SuperLayout {
        val sections = sections(output)
        val groups = records(sections[GROUPS].orEmpty()).mapNotNull { r ->
            val size = field(r, "Maximum size")?.substringBefore(' ')?.toLongOrNull() ?: return@mapNotNull null
            field(r, "Name")?.takeIf { it != "default" }?.let { SuperGroup(it, size) }
        }
        val partitions = records(sections[PARTITIONS].orEmpty()).map { r ->
            val sectors = r.mapNotNull { EXTENT.find(it)?.destructured }
                .sumOf { (first, last) -> last.toLong() - first.toLong() + 1 }
            SuperPartition(field(r, "Name").orEmpty(), field(r, "Group").orEmpty(), sectors * SECTOR)
        }
        val superSize = sections[DEVICES].orEmpty().filter { it.startsWith("Size:") }
            .sumOf { it.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0L }
        return SuperLayoutPlanner.plan(
            superSize = superSize,
            groups = groups,
            partitions = partitions.filter { p -> p.group != "default" || groups.none() },
        )
    }

    /** The trimmed lines under each `... table:` heading (and `Super partition layout:`). */
    private fun sections(output: String): Map<String, List<String>> {
        val sections = mutableMapOf<String, MutableList<String>>()
        var current: MutableList<String>? = null
        output.lines().map(String::trim).forEach { line ->
            if (line.endsWith("table:") || line == "Super partition layout:") {
                current = sections.getOrPut(line) { mutableListOf() }
            } else {
                current?.add(line)
            }
        }
        return sections
    }

    /** A section's entries: each starts at its `Name:` line. */
    private fun records(lines: List<String>): List<List<String>> {
        val records = mutableListOf<MutableList<String>>()
        lines.forEach { line ->
            if (line.startsWith("Name:")) records += mutableListOf(line) else records.lastOrNull()?.add(line)
        }
        return records
    }

    private fun field(record: List<String>, key: String): String? =
        record.firstOrNull { it.startsWith("$key:") }?.substringAfter(':')?.trim()

    private const val SECTOR = 512L
    private const val PARTITIONS = "Partition table:"
    private const val DEVICES = "Block device table:"
    private const val GROUPS = "Group table:"
    private val EXTENT = Regex("""^(\d+) \.\. (\d+) linear .*$""")
}
