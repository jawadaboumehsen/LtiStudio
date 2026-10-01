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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion

@Composable
fun OsStreamsConfigTab(target: TargetDevice, onTargetChange: (TargetDevice) -> Unit, modifier: Modifier = Modifier) {
    var activeRegion by remember(target.id) {
        mutableStateOf(target.availableRegions.firstOrNull() ?: TargetRegion.GLOBAL)
    }

    val firmwares = target.availableFirmwares[activeRegion].orEmpty()
    val currentFirmware = firmwares.firstOrNull() ?: TargetFirmware(
        version = "1.0.0",
        buildId = "${target.codename}:15/AQ3A.240812.002/20260101.000000",
        isOfficial = false,
    )

    fun updateFirmware(updated: TargetFirmware) {
        val updatedMap = target.availableFirmwares.toMutableMap().apply {
            val existing = get(activeRegion).orEmpty()
            put(activeRegion, listOf(updated) + existing.drop(1))
        }
        onTargetChange(target.copy(availableFirmwares = updatedMap))
    }

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
                    text = "Deployment Region",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Baseline OS market/region driving OTA endpoints and locale streams.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    target.availableRegions.forEach { region ->
                        val isSelected = activeRegion == region
                        GlassButton(
                            onClick = { activeRegion = region },
                            modifier = Modifier.weight(1f),
                            variant = if (isSelected) GlassButtonVariant.Primary else GlassButtonVariant.Standard,
                        ) {
                            Text(
                                text = "${region.displayName} (${region.shortName})",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
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
                    text = "Baseline Firmware Stream (${activeRegion.displayName})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Firmware identity used to acquire and verify the baseline OTA/image dump for this region.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

                GlassTextField(
                    value = currentFirmware.version,
                    onValueChange = { updateFirmware(currentFirmware.copy(version = it)) },
                    label = "Firmware Version",
                    placeholder = "e.g. REDMAGICOS10.5.15_NP05J_GB",
                    modifier = Modifier.fillMaxWidth(),
                )

                GlassTextField(
                    value = currentFirmware.buildId,
                    onValueChange = { updateFirmware(currentFirmware.copy(buildId = it)) },
                    label = "Build Fingerprint",
                    placeholder = "e.g. PQ84P01:15/AQ3A.240812.002/20260311.222527",
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    GlassTextField(
                        value = currentFirmware.androidVersion,
                        onValueChange = { updateFirmware(currentFirmware.copy(androidVersion = it)) },
                        label = "Android Version",
                        placeholder = "15",
                        modifier = Modifier.weight(1f),
                    )
                    GlassTextField(
                        value = currentFirmware.securityPatch,
                        onValueChange = { updateFirmware(currentFirmware.copy(securityPatch = it)) },
                        label = "Security Patch",
                        placeholder = "2026-02-01",
                        modifier = Modifier.weight(1f),
                    )
                }

                GlassTextField(
                    value = currentFirmware.otaUrl.orEmpty(),
                    onValueChange = { updateFirmware(currentFirmware.copy(otaUrl = it.ifBlank { null })) },
                    label = "OTA / Acquisition URL",
                    placeholder = "https://rom.download.nubia.com/...",
                    modifier = Modifier.fillMaxWidth(),
                )
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
                    text = "Firmware Acquisition Policy",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                    "Baseline firmware acquisition mode (official OTA download or raw archive import) is bound " +
                        "per-workspace inside Configuration Snapshots.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
