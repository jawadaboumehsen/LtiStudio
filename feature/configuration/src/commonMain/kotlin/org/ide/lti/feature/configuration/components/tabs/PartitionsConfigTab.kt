/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.configuration.components.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.target.TargetDevice

@Composable
fun PartitionsConfigTab(target: TargetDevice, onTargetChange: (TargetDevice) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        PartitionListCard(
            title = "Dynamic Partitions",
            description = "Logical blocks packed into the super.img dynamic partition table.",
            partitions = target.dynamicPartitions,
            onPartitionsChange = { onTargetChange(target.copy(dynamicPartitions = it)) },
        )

        PartitionListCard(
            title = "Direct-Flash Boot Partitions",
            description = "Physical partitions flashed directly via fastboot (not dynamic).",
            partitions = target.bootPartitions,
            onPartitionsChange = { onTargetChange(target.copy(bootPartitions = it)) },
        )

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = "Boot Chain Partition Sizes",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Fixed-size boot chain images, in bytes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    GlassTextField(
                        value = target.bootPartitionBytes.toString(),
                        onValueChange = { raw ->
                            raw.toLongOrNull()?.let { onTargetChange(target.copy(bootPartitionBytes = it)) }
                        },
                        label = "boot",
                        modifier = Modifier.weight(1f),
                    )
                    GlassTextField(
                        value = target.vendorBootPartitionBytes.toString(),
                        onValueChange = { raw ->
                            raw.toLongOrNull()?.let { onTargetChange(target.copy(vendorBootPartitionBytes = it)) }
                        },
                        label = "vendor_boot",
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    GlassTextField(
                        value = target.initBootPartitionBytes.toString(),
                        onValueChange = { raw ->
                            raw.toLongOrNull()?.let { onTargetChange(target.copy(initBootPartitionBytes = it)) }
                        },
                        label = "init_boot",
                        modifier = Modifier.weight(1f),
                    )
                    GlassTextField(
                        value = target.dtboPartitionBytes.toString(),
                        onValueChange = { raw ->
                            raw.toLongOrNull()?.let { onTargetChange(target.copy(dtboPartitionBytes = it)) }
                        },
                        label = "dtbo",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = "Packaging Policy",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Flashable ZIP assembly rules for this target's packagePolicy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                GlassTextField(
                    value = target.packagePolicy.recoverySystemMountPoint,
                    onValueChange = {
                        onTargetChange(
                            target.copy(packagePolicy = target.packagePolicy.copy(recoverySystemMountPoint = it)),
                        )
                    },
                    label = "Recovery System Mount Point",
                    placeholder = "/system_root",
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    Text(
                        text = "Exclude vbmeta from package",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    GlassToggle(
                        checked = target.packagePolicy.excludeVbmeta,
                        onCheckedChange = {
                            onTargetChange(target.copy(packagePolicy = target.packagePolicy.copy(excludeVbmeta = it)))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PartitionListCard(
    title: String,
    description: String,
    partitions: List<String>,
    onPartitionsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newPartitionName by remember { mutableStateOf("") }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                partitions.forEach { partition ->
                    GlassChip(
                        label = partition,
                        onClick = { onPartitionsChange(partitions - partition) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                GlassTextField(
                    value = newPartitionName,
                    onValueChange = { newPartitionName = it },
                    placeholder = "partition_name",
                    modifier = Modifier.weight(1f),
                )
                GlassIconButton(
                    icon = AppIcons.Add,
                    contentDescription = "Add partition",
                    onClick = {
                        val name = newPartitionName.trim()
                        if (name.isNotEmpty() && name !in partitions) {
                            onPartitionsChange(partitions + name)
                            newPartitionName = ""
                        }
                    },
                )
            }
        }
    }
}
