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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.IdeTable
import org.ide.lti.core.designsystem.component.display.IdeTableHeader
import org.ide.lti.core.designsystem.component.display.IdeTableHeaderCell
import org.ide.lti.core.designsystem.component.display.IdeTableRow
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.workspace.FirmwareArchive
import org.ide.lti.core.domain.workspace.FirmwareArchives
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.feature.rom.studio.api.formatBytes
import org.ide.lti.feature.rom.studio.stage.acquire.ArchiveState

/** The firmware archives in the workspace folder: probe them, import one from this computer, pick one. */
@Composable
public fun ExistingArchivesPane(
    settings: AcquisitionSettings,
    archiveState: ArchiveState,
    onProbe: () -> Unit,
    onImport: () -> Unit,
    onSelect: (FirmwareArchive) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { if (!archiveState.probed) onProbe() }
    val selectedRef = settings.archiveRef.takeIf { settings.mode == AcquisitionMode.IMPORT_ARCHIVE }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ExistingArchivesPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Notice(
            "Archives in the workspace folder (${FirmwareArchives.DIR}). The one you select is used instead of " +
                "downloading; its SHA-256 is checked when the build runs.",
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val idle = !archiveState.busy
            PaneButton("Probe", Icons.Default.Search, GlassButtonVariant.Primary, idle, onProbe)
            PaneButton("Import archive", Icons.Default.Upload, GlassButtonVariant.Standard, idle, onImport)
            val status = when {
                archiveState.busy -> "Working… a large archive takes a few minutes to copy."
                archiveState.probed -> "Found ${archiveState.archives.size} archive(s) in the workspace folder."
                else -> null
            }
            status?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = FontSize.Micro)
            }
        }

        archiveState.error?.let {
            Text(
                text = it,
                color = GlassTheme.diagnosticColors.error,
                fontSize = FontSize.BodySmall,
                modifier = Modifier.testTag("ArchiveError"),
            )
        }

        if (archiveState.probed && archiveState.archives.isEmpty()) {
            Text(
                "No archives in this workspace yet. Import one from this computer.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.BodySmall,
                modifier = Modifier.testTag("NoArchives"),
            )
        } else if (archiveState.archives.isNotEmpty()) {
            ArchivesTable(archiveState.archives, selectedRef, onSelect)
        }
    }
}

@Composable
private fun Notice(text: String) {
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
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }
    }
}

@Composable
private fun PaneButton(
    label: String,
    icon: ImageVector,
    variant: GlassButtonVariant,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    GlassButton(onClick = onClick, enabled = enabled, variant = variant, shape = GlassShapes.ShellControl) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Icon(icon, label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(IconSize.ExtraSmall))
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = FontSize.BodySmall)
        }
    }
}

@Composable
private fun ArchivesTable(archives: List<FirmwareArchive>, selectedRef: String?, onSelect: (FirmwareArchive) -> Unit) {
    IdeTable(modifier = Modifier.fillMaxWidth()) {
        IdeTableHeader {
            IdeTableHeaderCell(text = "Archive", modifier = Modifier.weight(2f))
            IdeTableHeaderCell(text = "Size", modifier = Modifier.weight(0.8f))
            IdeTableHeaderCell(text = "Action", modifier = Modifier.width(GlassDimens.AcquireTableActionColWidth))
        }
        archives.forEach { archive ->
            val selected = archive.ref == selectedRef
            IdeTableRow(isSelected = selected, onClick = { onSelect(archive) }) {
                Row(
                    modifier = Modifier.weight(2f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    Icon(
                        Icons.Default.FolderZip,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.Small),
                    )
                    Text(
                        text = archive.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = FontSize.BodySmall,
                        fontWeight = FontWeight.Medium,
                        fontFamily = codeFontFamily(),
                    )
                }
                Text(
                    text = formatBytes(archive.sizeBytes),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.BodySmall,
                    fontFamily = codeFontFamily(),
                    modifier = Modifier.weight(0.8f),
                )
                Box(modifier = Modifier.width(GlassDimens.AcquireTableActionColWidth)) {
                    GlassButton(
                        onClick = { onSelect(archive) },
                        variant = if (selected) GlassButtonVariant.Primary else GlassButtonVariant.Standard,
                        shape = GlassShapes.ShellControl,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Hairline),
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(IconSize.ExtraSmall),
                                )
                            }
                            Text(
                                text = if (selected) "Selected" else "Select",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = FontSize.Micro,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
