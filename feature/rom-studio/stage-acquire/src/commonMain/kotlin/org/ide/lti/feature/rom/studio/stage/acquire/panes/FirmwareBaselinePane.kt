/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.acquire.panes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.designsystem.theme.IdeShellShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AcquisitionSettings

private data class FirmwareVersionRowUi(
    val id: String,
    val releaseIdentifier: String,
    val version: String,
    val releaseDate: String,
    val isCompatible: Boolean,
    val notes: String,
    val description: String,
    val sha256: String,
    val sha1: String,
    val fileSize: String,
    val source: String,
    val retrieved: String,
)

@Suppress("UnusedParameter")
@Composable
public fun FirmwareBaselinePane(
    settings: AcquisitionSettings,
    target: TargetDevice,
    onChange: (AcquisitionSettings) -> Unit,
    onValidate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
) {
    val firmwares = remember {
        listOf(
            FirmwareVersionRowUi(
                id = "fw_1",
                releaseIdentifier = "PQ84P01.4.0.2312.001",
                version = "4.0.2312.001",
                releaseDate = "2023-12-15",
                isCompatible = true,
                notes = "Recommended (latest stable)",
                description = "Official stable release. Recommended for new workspaces.",
                sha256 = "3f4a2c6d9e8f1b171e3c0a9d7f8b2e6c4d1a9f0b3e6c8d7f1a2b3c4d5e6f7a8b9",
                sha1 = "9d7c3a1b4e6f8d2c0a9e3f7b1c6d5a2e9f8b4c3d",
                fileSize = "4.27 GB (4,586,893,312 bytes)",
                source = "Official vendor servers",
                retrieved = "2023-12-15 14:22:31 UTC",
            ),
            FirmwareVersionRowUi(
                id = "fw_2",
                releaseIdentifier = "PQ84P01.4.0.2309.004",
                version = "4.0.2309.004",
                releaseDate = "2023-09-28",
                isCompatible = true,
                notes = "Stable release",
                description = "Official maintenance release for target PQ84P01.",
                sha256 = "5b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c",
                sha1 = "7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8c",
                fileSize = "4.25 GB (4,563,402,752 bytes)",
                source = "Official vendor servers",
                retrieved = "2023-09-28 10:15:00 UTC",
            ),
            FirmwareVersionRowUi(
                id = "fw_3",
                releaseIdentifier = "PQ84P01.4.0.2306.002",
                version = "4.0.2306.002",
                releaseDate = "2023-06-21",
                isCompatible = true,
                notes = "Stable release",
                description = "Q2 baseline release for target PQ84P01.",
                sha256 = "7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b",
                sha1 = "1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a",
                fileSize = "4.21 GB (4,521,234,432 bytes)",
                source = "Official vendor servers",
                retrieved = "2023-06-21 08:30:12 UTC",
            ),
            FirmwareVersionRowUi(
                id = "fw_4",
                releaseIdentifier = "PQ84P01.4.0.2212.005",
                version = "4.0.2212.005",
                releaseDate = "2022-12-05",
                isCompatible = true,
                notes = "Older, still supported",
                description = "Initial stable LTS release for target PQ84P01.",
                sha256 = "9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f",
                sha1 = "4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d",
                fileSize = "4.15 GB (4,456,448,000 bytes)",
                source = "Official vendor servers",
                retrieved = "2022-12-05 16:45:00 UTC",
            ),
            FirmwareVersionRowUi(
                id = "fw_5",
                releaseIdentifier = "PQ84P01.3.5.2108.001",
                version = "3.5.2108.001",
                releaseDate = "2021-08-17",
                isCompatible = false,
                notes = "Major version mismatch",
                description = "Legacy 3.x baseline firmware; not supported by target revision 4.",
                sha256 = "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b",
                sha1 = "2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c",
                fileSize = "3.80 GB (4,080,218,880 bytes)",
                source = "Official vendor servers",
                retrieved = "2021-08-17 12:00:00 UTC",
            ),
            FirmwareVersionRowUi(
                id = "fw_6",
                releaseIdentifier = "PQ84P01.3.1.2011.003",
                version = "3.1.2011.003",
                releaseDate = "2020-11-03",
                isCompatible = false,
                notes = "Unsupported platform",
                description = "Obsolete platform firmware; rejected by target validator.",
                sha256 = "3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d",
                sha1 = "5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f",
                fileSize = "3.65 GB (3,919,104,000 bytes)",
                source = "Official vendor servers",
                retrieved = "2020-11-03 09:12:00 UTC",
            ),
        )
    }

    var selectedId by remember { mutableStateOf(firmwares.first().id) }
    val selectedFw = firmwares.find { it.id == selectedId } ?: firmwares.first()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("FirmwareBaselinePane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        AcquireHeader(
            title = "Firmware baseline",
            subtitle = "Select the official firmware build to use as the foundation for this workspace build.",
            targetSpec = "${target.codename} / Development",
            targetRevision = "Target revision ${target.revision}",
            additionalContext = "Save affects next build only.",
            onValidate = onValidate,
            onSave = onSave,
            colors = colors,
        )

        RegionSelectorRow(colors = colors)

        FirmwareVersionsTable(
            firmwares = firmwares,
            selectedId = selectedId,
            onSelect = { fw ->
                if (fw.isCompatible) {
                    selectedId = fw.id
                }
            },
            colors = colors,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                SelectedBaselineDetailsCard(firmware = selectedFw, colors = colors)
            }
            Box(modifier = Modifier.weight(1f)) {
                ChecksumAndProvenanceCard(firmware = selectedFw, colors = colors)
            }
        }

        FirmwareInfoBanner(colors = colors)
    }
}

@Composable
private fun RegionSelectorRow(colors: IdeShellColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = colors.accentBlue,
                modifier = Modifier.size(IconSize.Small),
            )
            Text(
                text = "Region",
                color = colors.textDim,
                fontSize = FontSize.BodySmall,
                fontFamily = GlassFontFamily.ide(),
            )
            Row(
                modifier = Modifier
                    .clip(IdeShellShapes.Control)
                    .background(colors.cardBackgroundMuted)
                    .border(IdeShellDimens.HairlineBorder, colors.border, IdeShellShapes.Control)
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = "Global (Worldwide)",
                    color = colors.textMain,
                    fontSize = FontSize.BodySmall,
                    fontFamily = GlassFontFamily.ide(),
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.textDim,
                    modifier = Modifier.size(IconSize.ExtraSmall),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = null,
                tint = colors.accentBlue,
                modifier = Modifier.size(IconSize.Small),
            )
            Text(
                text = "Select the region matching your target device. Incorrect region may cause incompatibility.",
                color = colors.textDim,
                fontSize = FontSize.Micro,
                fontFamily = GlassFontFamily.ide(),
            )
        }
    }
}

@Composable
private fun FirmwareVersionsTable(
    firmwares: List<FirmwareVersionRowUi>,
    selectedId: String,
    onSelect: (FirmwareVersionRowUi) -> Unit,
    colors: IdeShellColorScheme,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        surfaceColor = colors.cardBackground,
        borderColor = colors.border,
        contentPadding = Spacing.Medium,
    ) {
        Column {
            Text(
                text = "Available firmware versions",
                color = colors.textMain,
                fontSize = FontSize.TitleSmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GlassFontFamily.ide(),
                modifier = Modifier.padding(bottom = Spacing.Small),
            )

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Select",
                    color = colors.textDim,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GlassFontFamily.ide(),
                    modifier = Modifier.width(IdeShellDimens.AcquireTableSelectColWidth),
                )
                Text(
                    text = "Release identifier",
                    color = colors.textDim,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GlassFontFamily.ide(),
                    modifier = Modifier.weight(2f),
                )
                Text(
                    text = "Version",
                    color = colors.textDim,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GlassFontFamily.ide(),
                    modifier = Modifier.weight(1.4f),
                )
                Text(
                    text = "Release date",
                    color = colors.textDim,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GlassFontFamily.ide(),
                    modifier = Modifier.weight(1.2f),
                )
                Text(
                    text = "Status",
                    color = colors.textDim,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GlassFontFamily.ide(),
                    modifier = Modifier.weight(1.2f),
                )
                Text(
                    text = "Notes",
                    color = colors.textDim,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GlassFontFamily.ide(),
                    modifier = Modifier.weight(2f),
                )
            }

            HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)

            firmwares.forEach { fw ->
                FirmwareVersionRowItem(
                    fw = fw,
                    isSelected = fw.id == selectedId,
                    onSelect = onSelect,
                    colors = colors,
                )
            }
        }
    }
}

@Composable
private fun FirmwareVersionRowItem(
    fw: FirmwareVersionRowUi,
    isSelected: Boolean,
    onSelect: (FirmwareVersionRowUi) -> Unit,
    colors: IdeShellColorScheme,
) {
    val textColor = if (fw.isCompatible) colors.textMain else colors.textDim
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Small)
            .clickable(enabled = fw.isCompatible) { onSelect(fw) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isSelected,
            onClick = { if (fw.isCompatible) onSelect(fw) },
            enabled = fw.isCompatible,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.accentBlue,
                unselectedColor = colors.textDim,
            ),
            modifier = Modifier.width(IdeShellDimens.AcquireTableSelectColWidth),
        )

        Text(
            text = fw.releaseIdentifier,
            color = textColor,
            fontSize = FontSize.BodySmall,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            fontFamily = GlassFontFamily.code(),
            modifier = Modifier.weight(2f),
        )

        Text(
            text = fw.version,
            color = textColor,
            fontSize = FontSize.BodySmall,
            fontFamily = GlassFontFamily.code(),
            modifier = Modifier.weight(1.4f),
        )

        Text(
            text = fw.releaseDate,
            color = textColor,
            fontSize = FontSize.BodySmall,
            fontFamily = GlassFontFamily.code(),
            modifier = Modifier.weight(1.2f),
        )

        Row(
            modifier = Modifier.weight(1.2f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            if (fw.isCompatible) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Compatible",
                    tint = colors.accentGreen,
                    modifier = Modifier.size(IconSize.ExtraSmall),
                )
                Text(
                    text = "Compatible",
                    color = colors.accentGreen,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.RemoveCircleOutline,
                    contentDescription = "Incompatible",
                    tint = colors.textDim,
                    modifier = Modifier.size(IconSize.ExtraSmall),
                )
                Text(
                    text = "Incompatible",
                    color = colors.textDim,
                    fontSize = FontSize.Micro,
                )
            }
        }

        Text(
            text = fw.notes,
            color = if (fw.isCompatible) colors.textMuted else colors.textDim,
            fontSize = FontSize.Micro,
            fontFamily = GlassFontFamily.ide(),
            modifier = Modifier.weight(2f),
        )
    }
}

@Composable
private fun SelectedBaselineDetailsCard(firmware: FirmwareVersionRowUi, colors: IdeShellColorScheme) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        surfaceColor = colors.cardBackground,
        borderColor = colors.border,
        contentPadding = Spacing.Medium,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium)) {
            Text(
                text = "Selected baseline details",
                color = colors.textMain,
                fontSize = FontSize.TitleSmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GlassFontFamily.ide(),
            )

            PropertyRow(label = "Release identifier", value = firmware.releaseIdentifier, colors = colors)
            PropertyRow(label = "Version", value = firmware.version, colors = colors)
            PropertyRow(label = "Region", value = "Global (Worldwide)", colors = colors)
            PropertyRow(label = "Release date", value = firmware.releaseDate, colors = colors)
            PropertyRow(label = "Status", colors = colors) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = colors.accentGreen,
                        modifier = Modifier.size(IconSize.ExtraSmall),
                    )
                    Text(
                        text = "Compatible",
                        color = colors.accentGreen,
                        fontSize = FontSize.Micro,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            PropertyRow(label = "Description", value = firmware.description, colors = colors)
        }
    }
}

@Composable
private fun ChecksumAndProvenanceCard(firmware: FirmwareVersionRowUi, colors: IdeShellColorScheme) {
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by remember { mutableStateOf(0) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        surfaceColor = colors.cardBackground,
        borderColor = colors.border,
        contentPadding = Spacing.Medium,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Checksum & provenance",
                    color = colors.textMain,
                    fontSize = FontSize.TitleSmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GlassFontFamily.ide(),
                )

                Row(
                    modifier = Modifier
                        .clip(IdeShellShapes.Control)
                        .background(colors.cardBackgroundMuted)
                        .padding(Spacing.Hairline),
                ) {
                    listOf("Checksums", "Source").forEachIndexed { index, tabTitle ->
                        val isTabSelected = index == selectedTab
                        Box(
                            modifier = Modifier
                                .clip(IdeShellShapes.Control)
                                .background(if (isTabSelected) colors.controlSelected else colors.cardBackgroundMuted)
                                .clickable { selectedTab = index }
                                .padding(horizontal = Spacing.Small, vertical = Spacing.Hairline),
                        ) {
                            Text(
                                text = tabTitle,
                                color = if (isTabSelected) colors.textMain else colors.textDim,
                                fontSize = FontSize.Micro,
                                fontFamily = GlassFontFamily.ide(),
                            )
                        }
                    }
                }
            }

            PropertyRow(label = "SHA-256", colors = colors) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = firmware.sha256,
                        color = colors.textMain,
                        fontSize = FontSize.Micro,
                        fontFamily = GlassFontFamily.code(),
                    )
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy hash",
                        tint = colors.textDim,
                        modifier = Modifier
                            .size(IconSize.ExtraSmall)
                            .clickable { clipboardManager.setText(AnnotatedString(firmware.sha256)) },
                    )
                }
            }

            PropertyRow(label = "SHA-1", colors = colors) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = firmware.sha1,
                        color = colors.textMain,
                        fontSize = FontSize.Micro,
                        fontFamily = GlassFontFamily.code(),
                    )
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy hash",
                        tint = colors.textDim,
                        modifier = Modifier
                            .size(IconSize.ExtraSmall)
                            .clickable { clipboardManager.setText(AnnotatedString(firmware.sha1)) },
                    )
                }
            }

            PropertyRow(label = "File size", value = firmware.fileSize, colors = colors)
            PropertyRow(label = "Source", value = firmware.source, colors = colors)
            PropertyRow(label = "Retrieved", value = firmware.retrieved, colors = colors)
        }
    }
}

@Composable
private fun FirmwareInfoBanner(colors: IdeShellColorScheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.cardBackgroundMuted, IdeShellShapes.Card)
            .border(IdeShellDimens.HairlineBorder, colors.borderMuted, IdeShellShapes.Card)
            .padding(Spacing.SmallMedium),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = colors.accentBlue,
                modifier = Modifier.size(IconSize.Small),
            )
            Text(
                text = "Save applies to the next build only. " +
                    "You can change the firmware baseline at any time before starting a build.",
                color = colors.textMuted,
                fontSize = FontSize.Micro,
                fontFamily = GlassFontFamily.ide(),
            )
        }
    }
}
