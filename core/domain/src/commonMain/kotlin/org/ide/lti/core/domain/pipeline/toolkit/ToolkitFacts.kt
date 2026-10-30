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
 * Android slot mode classification.
 */
@Serializable
enum class SlotMode {
    A_ONLY,
    AB,
    VIRTUAL_AB,
    UNKNOWN,
}

/**
 * Extracted partition metadata fact.
 */
@Serializable
data class ToolkitPartitionFact(val name: String, val fsType: String, val sizeBytes: Long, val inSuper: Boolean)

/**
 * Basic facts stored under `facts/basic_info.json`.
 */
@Serializable
data class ToolkitBasicInfo(val deviceIdentity: String, val slotMode: SlotMode, val isVirtualAbCompressed: Boolean)

/**
 * Partition facts stored under `facts/partition_info.json`.
 */
@Serializable
data class ToolkitPartitionInfo(val partitions: List<ToolkitPartitionFact> = emptyList())

/**
 * Complete immutable facts collected for a target workspace.
 *
 * Single writer rule: the first tool to read images writes these facts;
 * all subsequent pipeline steps read them.
 */
@Serializable
data class ToolkitFacts(
    val deviceIdentity: String,
    val slotMode: SlotMode,
    val isVirtualAbCompressed: Boolean,
    val partitions: List<ToolkitPartitionFact> = emptyList(),
) {
    fun toBasicInfo(): ToolkitBasicInfo = ToolkitBasicInfo(
        deviceIdentity = deviceIdentity,
        slotMode = slotMode,
        isVirtualAbCompressed = isVirtualAbCompressed,
    )

    fun toPartitionInfo(): ToolkitPartitionInfo = ToolkitPartitionInfo(
        partitions = partitions,
    )

    companion object {
        fun from(basicInfo: ToolkitBasicInfo, partitionInfo: ToolkitPartitionInfo): ToolkitFacts = ToolkitFacts(
            deviceIdentity = basicInfo.deviceIdentity,
            slotMode = basicInfo.slotMode,
            isVirtualAbCompressed = basicInfo.isVirtualAbCompressed,
            partitions = partitionInfo.partitions,
        )

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encodeBasicInfo(basicInfo: ToolkitBasicInfo): String =
            json.encodeToString(ToolkitBasicInfo.serializer(), basicInfo)

        fun decodeBasicInfo(string: String): ToolkitBasicInfo =
            json.decodeFromString(ToolkitBasicInfo.serializer(), string)

        fun encodePartitionInfo(partitionInfo: ToolkitPartitionInfo): String =
            json.encodeToString(ToolkitPartitionInfo.serializer(), partitionInfo)

        fun decodePartitionInfo(string: String): ToolkitPartitionInfo =
            json.decodeFromString(ToolkitPartitionInfo.serializer(), string)

        fun encode(facts: ToolkitFacts): String = json.encodeToString(ToolkitFacts.serializer(), facts)

        fun decode(string: String): ToolkitFacts = json.decodeFromString(ToolkitFacts.serializer(), string)
    }
}
