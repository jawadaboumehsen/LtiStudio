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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.target.TargetDevice

// LongMethod suppressed on @Composable screen tab assembling hardware configuration sections.
@Suppress("LongMethod")
@Composable
fun HardwareConfigTab(target: TargetDevice, onTargetChange: (TargetDevice) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = "Silicon & Filesystem",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Chipset platform and super partition filesystem used for unpack/pack.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    GlassTextField(
                        value = target.socPlatform,
                        onValueChange = { onTargetChange(target.copy(socPlatform = it)) },
                        label = "SoC Platform",
                        placeholder = "e.g. Snapdragon 8 Elite",
                        modifier = Modifier.weight(1f),
                    )
                    GlassTextField(
                        value = target.filesystemType,
                        onValueChange = { onTargetChange(target.copy(filesystemType = it)) },
                        label = "Filesystem Type",
                        placeholder = "e.g. erofs",
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
                    text = "Super Partition Geometry",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Dynamic partition metadata sizing, in bytes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    GlassTextField(
                        value = target.superPartitionBytes.toString(),
                        onValueChange = { raw ->
                            raw.toLongOrNull()?.let { onTargetChange(target.copy(superPartitionBytes = it)) }
                        },
                        label = "Super Partition Bytes",
                        placeholder = "17179869184",
                        modifier = Modifier.weight(1f),
                    )
                    GlassTextField(
                        value = target.superGroupBytes.toString(),
                        onValueChange = { raw ->
                            raw.toLongOrNull()?.let { onTargetChange(target.copy(superGroupBytes = it)) }
                        },
                        label = "Super Group Bytes",
                        placeholder = "17175674880",
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    GlassTextField(
                        value = target.superGroupName,
                        onValueChange = { onTargetChange(target.copy(superGroupName = it)) },
                        label = "Super Group Name",
                        placeholder = "qti_dynamic_partitions",
                        modifier = Modifier.weight(1f),
                    )
                    GlassTextField(
                        value = target.superMetadataSlots.toString(),
                        onValueChange = { raw ->
                            raw.toIntOrNull()?.let { onTargetChange(target.copy(superMetadataSlots = it)) }
                        },
                        label = "Metadata Slots",
                        placeholder = "3",
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
                    text = "Fastboot & Slotting",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Identifiers used by fastboot flashing and A/B slot management.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    GlassTextField(
                        value = target.fastbootProduct,
                        onValueChange = { onTargetChange(target.copy(fastbootProduct = it)) },
                        label = "Fastboot Product",
                        placeholder = "e.g. sun",
                        modifier = Modifier.weight(1f),
                    )
                    GlassTextField(
                        value = target.activeSlotSuffix,
                        onValueChange = { onTargetChange(target.copy(activeSlotSuffix = it)) },
                        label = "Active Slot Suffix",
                        placeholder = "_a",
                        modifier = Modifier.weight(1f),
                    )
                }

                GlassTextField(
                    value = target.bootDevicePath,
                    onValueChange = { onTargetChange(target.copy(bootDevicePath = it)) },
                    label = "Boot Device Path",
                    placeholder = "/dev/block/bootdevice/by-name",
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    Text(
                        text = "Virtual A/B",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    GlassToggle(
                        checked = target.virtualAb,
                        onCheckedChange = { onTargetChange(target.copy(virtualAb = it)) },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    Text(
                        text = "Standalone system_ext",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    GlassToggle(
                        checked = target.hasStandaloneSystemExt,
                        onCheckedChange = { onTargetChange(target.copy(hasStandaloneSystemExt = it)) },
                    )
                }
            }
        }
    }
}
