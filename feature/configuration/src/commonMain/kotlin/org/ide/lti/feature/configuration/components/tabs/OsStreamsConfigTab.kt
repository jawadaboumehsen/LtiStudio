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
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.target.FirmwareAcquisitionMode
import org.ide.lti.core.model.target.TargetDevice

@Suppress("DEPRECATION")
@Composable
fun OsStreamsConfigTab(
    target: TargetDevice,
    onTargetChange: (TargetDevice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassColors = GlassTheme.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = "Deployment Region",
                    style = MaterialTheme.typography.titleSmall,
                    color = glassColors.onGlass,
                )
                Text(
                    text = "Baseline OS market/region driving OTA endpoints and locale streams.",
                    style = MaterialTheme.typography.bodySmall,
                    color = glassColors.onGlassSecondary,
                )

                Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    target.availableRegions.forEach { region ->
                        val isSelected = target.selectedRegion == region
                        GlassButton(
                            onClick = { onTargetChange(target.copy(selectedRegion = region)) },
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

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = "Baseline Firmware Stream",
                    style = MaterialTheme.typography.titleSmall,
                    color = glassColors.onGlass,
                )
                Text(
                    text = "Firmware identity used to acquire and verify the baseline OTA/image dump.",
                    style = MaterialTheme.typography.bodySmall,
                    color = glassColors.onGlassSecondary,
                )

                Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

                GlassTextField(
                    value = target.selectedFirmware.version,
                    onValueChange = { onTargetChange(target.copy(selectedFirmware = target.selectedFirmware.copy(version = it))) },
                    label = "Firmware Version",
                    placeholder = "e.g. REDMAGICOS10.5.15_NP05J_GB",
                    modifier = Modifier.fillMaxWidth(),
                )

                GlassTextField(
                    value = target.selectedFirmware.buildId,
                    onValueChange = { onTargetChange(target.copy(selectedFirmware = target.selectedFirmware.copy(buildId = it))) },
                    label = "Build Fingerprint",
                    placeholder = "e.g. PQ84P01:15/AQ3A.240812.002/20260311.222527",
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    GlassTextField(
                        value = target.selectedFirmware.androidVersion,
                        onValueChange = { onTargetChange(target.copy(selectedFirmware = target.selectedFirmware.copy(androidVersion = it))) },
                        label = "Android Version",
                        placeholder = "15",
                        modifier = Modifier.weight(1f),
                    )
                    GlassTextField(
                        value = target.selectedFirmware.securityPatch,
                        onValueChange = { onTargetChange(target.copy(selectedFirmware = target.selectedFirmware.copy(securityPatch = it))) },
                        label = "Security Patch",
                        placeholder = "2026-02-01",
                        modifier = Modifier.weight(1f),
                    )
                }

                GlassTextField(
                    value = target.selectedFirmware.otaUrl.orEmpty(),
                    onValueChange = { onTargetChange(target.copy(selectedFirmware = target.selectedFirmware.copy(otaUrl = it.ifBlank { null }))) },
                    label = "OTA / Acquisition URL",
                    placeholder = "https://rom.download.nubia.com/...",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = "Acquisition Mode",
                    style = MaterialTheme.typography.titleSmall,
                    color = glassColors.onGlass,
                )
                Text(
                    text = "How the baseline firmware stream is fetched prior to unpacking.",
                    style = MaterialTheme.typography.bodySmall,
                    color = glassColors.onGlassSecondary,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    FirmwareAcquisitionMode.entries.forEach { mode ->
                        val isSelected = target.acquisitionMode == mode
                        GlassButton(
                            onClick = { onTargetChange(target.copy(acquisitionMode = mode)) },
                            modifier = Modifier.weight(1f),
                            variant = if (isSelected) GlassButtonVariant.Primary else GlassButtonVariant.Standard,
                        ) {
                            Text(
                                text = mode.displayName,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }

                Text(
                    text = target.acquisitionMode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = glassColors.onGlassSecondary,
                )
            }
        }
    }
}
