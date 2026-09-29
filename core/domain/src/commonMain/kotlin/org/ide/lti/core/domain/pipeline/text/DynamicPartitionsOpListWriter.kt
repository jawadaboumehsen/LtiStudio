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

public object DynamicPartitionsOpListWriter {

    public data class PartitionImageInfo(
        val partitionName: String,
        val sizeBytes: Long,
        val sha256: String = "",
        val pubkeySha1: String = "",
    )

    public fun generateOpList(
        groupName: String,
        groupSizeBytes: Long,
        partitions: List<PartitionImageInfo>,
    ): String {
        return buildString {
            appendLine("remove_all_groups")
            appendLine("add_group $groupName $groupSizeBytes")
            for (p in partitions) {
                appendLine("add ${p.partitionName} $groupName")
            }
            for (p in partitions) {
                appendLine("resize ${p.partitionName} ${p.sizeBytes}")
            }
        }
    }

    public fun generateAvbImages(
        partitions: List<PartitionImageInfo>,
    ): String {
        return buildString {
            for (p in partitions) {
                appendLine("${p.partitionName} size=${p.sizeBytes} sha256=${p.sha256} public_key_sha1=${p.pubkeySha1}")
            }
        }
    }
}
