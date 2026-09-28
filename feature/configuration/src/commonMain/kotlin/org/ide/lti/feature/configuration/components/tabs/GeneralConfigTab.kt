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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetStatus

// LongMethod suppressed on @Composable screen tab assembling multiple target identity cards.
@Suppress("LongMethod")
@Composable
fun GeneralConfigTab(target: TargetDevice, onTargetChange: (TargetDevice) -> Unit, modifier: Modifier = Modifier) {
    val superGb = target.superPartitionBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        // =========================================================================
        // CARD 1: Hardware Target Identity (With Explicit Field Labels)
        // =========================================================================
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = "Hardware Target Identity",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Primary metadata identifying this hardware target device within the toolchain.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

                GlassTextField(
                    value = target.name,
                    onValueChange = { onTargetChange(target.copy(name = it)) },
                    label = "Device Display Name",
                    placeholder = "e.g. REDMAGIC Astra Gaming Tablet",
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    GlassTextField(
                        value = target.codename,
                        onValueChange = { onTargetChange(target.copy(codename = it, id = it)) },
                        label = "Hardware Codename",
                        placeholder = "e.g. PQ84P01",
                        modifier = Modifier.weight(1f),
                    )

                    GlassTextField(
                        value = target.id,
                        onValueChange = { onTargetChange(target.copy(id = it)) },
                        label = "Target Configuration ID",
                        placeholder = "e.g. PQ84P01",
                        modifier = Modifier.weight(1f),
                    )
                }

                GlassTextField(
                    value = target.description,
                    onValueChange = { onTargetChange(target.copy(description = it)) },
                    label = "Baseline Description & Release Notes",
                    placeholder = "e.g. Official Global & European retail baseline (NP05J_GB)",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // =========================================================================
        // CARD 2: Architecture & Platform Specifications Profile
        // =========================================================================
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = "Architecture & Platform Specifications",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "System configuration matrix used by build engines, unpackers, and doctor validation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    TargetSpecBadge(
                        label = "Architecture",
                        value = "arm64-v8a (64-bit)",
                        modifier = Modifier.weight(1f),
                    )
                    TargetSpecBadge(
                        label = "Android Platform",
                        value = "Android 15 (API 35)",
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    val formFactor = if (target.name.contains("Tablet", ignoreCase = true)) {
                        "Gaming Tablet"
                    } else {
                        "Mobile Handset"
                    }
                    TargetSpecBadge(
                        label = "Device Category",
                        value = formFactor,
                        modifier = Modifier.weight(1f),
                    )
                    TargetSpecBadge(
                        label = "Available Regions",
                        value = target.availableRegions.joinToString { it.shortName },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // =========================================================================
        // CARD 3: Qualification & Toolchain Doctor Status
        // =========================================================================
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = "Qualification Status",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Defines stability tier for toolchain doctor and automated build validation rules.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TargetStatus.entries.forEach { status ->
                        val isSelected = target.status == status
                        GlassButton(
                            onClick = { onTargetChange(target.copy(status = status)) },
                            modifier = Modifier.weight(1f),
                            variant = if (isSelected) GlassButtonVariant.Primary else GlassButtonVariant.Standard,
                        ) {
                            Text(
                                text = status.name,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }

                val statusExplanation = when (target.status) {
                    TargetStatus.QUALIFIED ->
                        "Qualified: Verified for production builds, OTA payload unpack, EROFS conversion, and direct fastboot flashing."
                    TargetStatus.EXPERIMENTAL ->
                        "Experimental: Target under active development. Toolchain doctor operates in permissive mode."
                    TargetStatus.UNVERIFIED ->
                        "Unverified: Draft target profile. Manual partition geometry verification is required prior to flashing."
                }

                Text(
                    text = statusExplanation,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = FontSize.Chip),
                    color = when (target.status) {
                        TargetStatus.QUALIFIED -> GlassTheme.diagnosticColors.success
                        TargetStatus.EXPERIMENTAL -> GlassTheme.diagnosticColors.warning
                        TargetStatus.UNVERIFIED -> GlassTheme.diagnosticColors.error
                    },
                )
            }
        }

        // =========================================================================
        // CARD 4: Storage Footprint & Dynamic Partition Summary
        // =========================================================================
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = "Storage Geometry & Partition Footprint",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Active partition topology and super.img allocation quota.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    TargetSpecBadge(
                        label = "Dynamic Partitions",
                        value = "${target.dynamicPartitions.size} Logical Blocks",
                        modifier = Modifier.weight(1f),
                    )
                    TargetSpecBadge(
                        label = "Super Capacity",
                        value = "${"%.2f".format(superGb)} GiB (${target.filesystemType.uppercase()})",
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    TargetSpecBadge(
                        label = "Direct-Flash Partitions",
                        value = "${target.bootPartitions.size} Physical Partitions",
                        modifier = Modifier.weight(1f),
                    )
                    TargetSpecBadge(
                        label = "Target Key",
                        value = target.targetKey,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetSpecBadge(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(GlassShapes.Card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = GlassShapes.Card,
            )
            .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Compact),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = FontSize.Chip,
                    fontFamily = codeFontFamily(),
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
