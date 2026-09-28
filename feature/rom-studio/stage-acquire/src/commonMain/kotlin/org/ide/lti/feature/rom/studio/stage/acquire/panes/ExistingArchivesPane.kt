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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.IdeTable
import org.ide.lti.core.designsystem.component.display.IdeTableHeader
import org.ide.lti.core.designsystem.component.display.IdeTableHeaderCell
import org.ide.lti.core.designsystem.component.display.IdeTableRow
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AcquisitionSettings

private data class ArchiveItemUi(
    val id: String,
    val fileName: String,
    val subtitle: String,
    val sizeString: String,
    val byteCount: Long,
    val hashTruncated: String,
    val fullHash: String,
    val isVerified: Boolean,
    val isMissing: Boolean = false,
    val dateModified: String,
    val type: String,
    val sourcePath: String,
)

@Suppress("UnusedParameter")
@Composable
public fun ExistingArchivesPane(
    settings: AcquisitionSettings,
    target: TargetDevice,
    onChange: (AcquisitionSettings) -> Unit,
    onValidate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sampleArchives = remember {
        listOf(
            ArchiveItemUi(
                id = "arch_1",
                fileName = "PQ84P01_20250301_1201.zip",
                subtitle = "System image archive",
                sizeString = "3.24 GB",
                byteCount = 3482915328L,
                hashTruncated = "a3f9e7c4d1b6a2e9....",
                fullHash = "a3f9e7c4d1b6a2e9875f3c0a9e12d6b4f2a9e7c1d0f3b8a9c6d7e1f4a2b3c9d0e",
                isVerified = true,
                dateModified = "2025-03-01 12:01:18",
                type = "System image archive",
                sourcePath = "C:\\ROMs\\Archives",
            ),
            ArchiveItemUi(
                id = "arch_2",
                fileName = "PQ84P01_vendor_20250301.zip",
                subtitle = "Vendor image archive",
                sizeString = "789 MB",
                byteCount = 827326464L,
                hashTruncated = "7c22d1b5e3a8f910....",
                fullHash = "7c22d1b5e3a8f9104b5a6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f",
                isVerified = true,
                dateModified = "2025-03-01 11:45:30",
                type = "Vendor image archive",
                sourcePath = "C:\\ROMs\\Archives",
            ),
            ArchiveItemUi(
                id = "arch_3",
                fileName = "PQ84P01_boot_20250215.zip",
                subtitle = "Boot image archive",
                sizeString = "96 MB",
                byteCount = 100663296L,
                hashTruncated = "d91e6a0f2c4b7798....",
                fullHash = "d91e6a0f2c4b77985a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f",
                isVerified = false,
                isMissing = true,
                dateModified = "2025-02-15 09:30:00",
                type = "Boot image archive",
                sourcePath = "C:\\ROMs\\Archives",
            ),
        )
    }

    var selectedArchiveId by remember { mutableStateOf(sampleArchives.first().id) }
    val selectedArchive = sampleArchives.find { it.id == selectedArchiveId } ?: sampleArchives.first()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ExistingArchivesPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        TopInfoInstruction()

        ArchivesTableCard(
            archives = sampleArchives,
            selectedId = selectedArchiveId,
            onSelect = {
                selectedArchiveId = it.id
                onChange(settings.copy(archiveRef = it.fileName, expectedSha256 = it.fullHash))
            },
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassButton(
                onClick = {},
                variant = GlassButtonVariant.Primary,
                shape = GlassShapes.ShellControl,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Probe",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(IconSize.ExtraSmall),
                    )
                    Text("Probe", color = MaterialTheme.colorScheme.onSurface, fontSize = FontSize.BodySmall)
                }
            }

            GlassButton(
                onClick = {},
                variant = GlassButtonVariant.Standard,
                shape = GlassShapes.ShellControl,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = "Import archive",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(IconSize.ExtraSmall),
                    )
                    Text("Import archive", color = MaterialTheme.colorScheme.onSurface, fontSize = FontSize.BodySmall)
                }
            }
        }

        SelectedArchiveCard(archive = selectedArchive)

        BottomLogsDrawer()
    }
}

@Composable
private fun TopInfoInstruction() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Subtle), GlassShapes.ShellCard)
            .border(
                GlassDimens.HairlineBorder,
                MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Ambient),
                GlassShapes.ShellCard,
            )
            .padding(Spacing.SmallMedium),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.Small),
            )
            Text(
                text = "Select archives to use for this build. We verify size and hash before using any archive. " +
                    "Use Probe to scan the current archive directory, or import an archive from disk.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }
    }
}

@Composable
private fun ArchivesTableCard(archives: List<ArchiveItemUi>, selectedId: String, onSelect: (ArchiveItemUi) -> Unit) {
    IdeTable(
        modifier = Modifier.fillMaxWidth(),
    ) {
        IdeTableHeader {
            IdeTableHeaderCell(
                text = "Select",
                modifier = Modifier.width(GlassDimens.AcquireTableIconColWidth),
            )
            IdeTableHeaderCell(
                text = "Archive",
                modifier = Modifier.weight(2f),
            )
            IdeTableHeaderCell(
                text = "Size",
                modifier = Modifier.weight(0.8f),
            )
            IdeTableHeaderCell(
                text = "Measured hash (SHA256)",
                modifier = Modifier.weight(1.5f),
            )
            IdeTableHeaderCell(
                text = "Verification",
                modifier = Modifier.weight(1f),
            )
            IdeTableHeaderCell(
                text = "Action",
                modifier = Modifier.width(GlassDimens.AcquireTableActionColWidth),
            )
        }

        archives.forEach { archive ->
            ArchiveTableRow(
                archive = archive,
                isSelected = archive.id == selectedId,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun ArchiveTableRow(archive: ArchiveItemUi, isSelected: Boolean, onSelect: (ArchiveItemUi) -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    IdeTableRow(
        isSelected = isSelected,
        onClick = { if (!archive.isMissing) onSelect(archive) },
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { if (!archive.isMissing) onSelect(archive) },
            enabled = !archive.isMissing,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.width(GlassDimens.AcquireTableIconColWidth),
        )

        Row(
            modifier = Modifier.weight(2f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                imageVector = Icons.Default.FolderZip,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.Small),
            )
            Column {
                Text(
                    text = archive.fileName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.Medium,
                    fontFamily = codeFontFamily(),
                )
                Text(
                    text = archive.subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
        }

        Text(
            text = archive.sizeString,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontFamily = codeFontFamily(),
            modifier = Modifier.weight(0.8f),
        )

        Row(
            modifier = Modifier.weight(1.5f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Text(
                text = archive.hashTruncated,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontFamily = codeFontFamily(),
            )
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy hash",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(IconSize.ExtraSmall)
                    .clickable { clipboardManager.setText(AnnotatedString(archive.fullHash)) },
            )
        }

        ArchiveStatusCell(
            isVerified = archive.isVerified,
            modifier = Modifier.weight(1f),
        )

        Box(modifier = Modifier.width(GlassDimens.AcquireTableActionColWidth)) {
            GlassButton(
                onClick = { onSelect(archive) },
                enabled = !archive.isMissing,
                variant = if (isSelected) GlassButtonVariant.Primary else GlassButtonVariant.Standard,
                shape = GlassShapes.ShellControl,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Hairline),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(IconSize.ExtraSmall),
                    )
                    Text(
                        text = "Select",
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = FontSize.Micro,
                    )
                }
            }
        }
    }
}

/** Status cell displaying verification state for an archive. */
@Composable
private fun ArchiveStatusCell(isVerified: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        if (isVerified) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Verified",
                tint = GlassTheme.diagnosticColors.success,
                modifier = Modifier.size(IconSize.Small),
            )
            Column {
                Text(
                    text = "Verified",
                    color = GlassTheme.diagnosticColors.success,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Hash matches",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.Cancel,
                contentDescription = "Missing",
                tint = GlassTheme.diagnosticColors.error,
                modifier = Modifier.size(IconSize.Small),
            )
            Column {
                Text(
                    text = "Missing",
                    color = GlassTheme.diagnosticColors.error,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "File not found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                )
            }
        }
    }
}

@Composable
private fun SelectedArchiveCard(archive: ArchiveItemUi) {
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Selected archive",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.TitleSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Icon(
                imageVector = Icons.Default.ViewInAr,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(GlassDimens.AcquirePreviewBadgeSize),
            )

            Column(
                modifier = Modifier.weight(1.2f),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                PropertyRow(label = "File", value = archive.fileName)
                PropertyRow(
                    label = "Size",
                    value = "${archive.sizeString} (${archive.byteCount} bytes)",
                )
                PropertyRow(label = "SHA256") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = archive.fullHash,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = FontSize.Micro,
                            fontFamily = codeFontFamily(),
                        )
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy hash",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(IconSize.ExtraSmall)
                                .clickable { clipboardManager.setText(AnnotatedString(archive.fullHash)) },
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(0.8f),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                PropertyRow(label = "Type", value = archive.type)
                PropertyRow(label = "Date modified", value = archive.dateModified)
                PropertyRow(label = "Source", value = archive.sourcePath)
                PropertyRow(label = "Status") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = GlassTheme.diagnosticColors.success,
                            modifier = Modifier.size(IconSize.ExtraSmall),
                        )
                        Text(
                            text = "Ready to use",
                            color = GlassTheme.diagnosticColors.success,
                            fontSize = FontSize.Micro,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomLogsDrawer() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Logs", "Problems", "Validation")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Small),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.Small),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            tabs.forEachIndexed { index, tabTitle ->
                val isTabSelected = index == selectedTab
                Row(
                    modifier = Modifier
                        .clip(GlassShapes.ShellControl)
                        .then(
                            if (isTabSelected) {
                                Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Faded))
                            } else {
                                Modifier
                            },
                        )
                        .clickable { selectedTab = index }
                        .padding(horizontal = Spacing.Medium, vertical = Spacing.ExtraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    val icon = when (index) {
                        0 -> Icons.Default.Terminal
                        1 -> Icons.Default.Warning
                        else -> Icons.Default.CheckCircle
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isTabSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(IconSize.ExtraSmall),
                    )
                    Text(
                        text = tabTitle,
                        color = if (isTabSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = FontSize.Micro,
                        fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = ideFontFamily(),
                    )
                }
            }
        }

        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.Small)
                .background(MaterialTheme.colorScheme.surfaceContainer, GlassShapes.ShellControl)
                .padding(Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(Spacing.Hairline),
        ) {
            val logLines = listOf(
                "1  [08:41] Scanning archive directory: C:\\ROMs\\Archives",
                "2  [08:41] Found 3 archive(s)",
                "3  [08:41] Verified PQ84P01_20250301_1201.zip (SHA256 matches)",
                "4  [08:41] Verified PQ84P01_vendor_20250301.zip (SHA256 matches)",
                "5  [08:41] Missing archive: PQ84P01_boot_20250215.zip",
            )
            logLines.forEach { line ->
                Text(
                    text = line,
                    color = if (line.contains(
                            "Missing",
                        )
                    ) {
                        GlassTheme.diagnosticColors.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = FontSize.Micro,
                    fontFamily = codeFontFamily(),
                )
            }
        }
    }
}
