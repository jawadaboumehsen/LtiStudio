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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.IdeTable
import org.ide.lti.core.designsystem.component.display.IdeTableHeader
import org.ide.lti.core.designsystem.component.display.IdeTableHeaderCell
import org.ide.lti.core.designsystem.component.display.IdeTableRow
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.workspace.AcquisitionSettings

/** The firmware the build starts from: the device's own list (device.json), per region. */
@Suppress("UnusedParameter")
@Composable
public fun FirmwareBaselinePane(
    settings: AcquisitionSettings,
    target: TargetDevice,
    onChange: (AcquisitionSettings) -> Unit = {},
    onValidate: () -> Unit = {},
    onSave: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val firmwares = target.availableFirmwares[settings.region].orEmpty()
    Column(
        modifier = modifier.fillMaxWidth().testTag("FirmwareBaselinePane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label("Region")
            target.availableRegions.forEach { region ->
                GlassChip(
                    label = region.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected = settings.region == region,
                    onClick = {
                        // A firmware of another region is never kept (FR-005).
                        val first = target.availableFirmwares[region]?.firstOrNull() ?: return@GlassChip
                        onChange(settings.copy(region = region, firmware = first))
                    },
                )
            }
        }

        if (firmwares.isEmpty()) {
            Label(
                "The device lists no firmware for this region. Add one in Device configuration, " +
                    "or import an archive.",
            )
        } else {
            FirmwareTable(firmwares, settings.firmware) { onChange(settings.copy(firmware = it)) }
        }

        FirmwareDetails(settings.firmware)
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = FontSize.BodySmall,
        fontFamily = ideFontFamily(),
    )
}

@Composable
private fun FirmwareTable(
    firmwares: List<TargetFirmware>,
    selected: TargetFirmware,
    onSelect: (TargetFirmware) -> Unit,
) {
    IdeTable(modifier = Modifier.fillMaxWidth()) {
        IdeTableHeader {
            IdeTableHeaderCell(text = "Version", modifier = Modifier.weight(2.4f))
            IdeTableHeaderCell(text = "Android", modifier = Modifier.weight(0.8f))
            IdeTableHeaderCell(text = "Security patch", modifier = Modifier.weight(1.2f))
            IdeTableHeaderCell(text = "Source", modifier = Modifier.weight(1f))
            IdeTableHeaderCell(text = "Action", modifier = Modifier.width(GlassDimens.AcquireTableActionColWidth))
        }
        firmwares.forEach { fw ->
            val isSelected = fw.version == selected.version && fw.buildId == selected.buildId
            IdeTableRow(isSelected = isSelected, onClick = { onSelect(fw) }) {
                Column(modifier = Modifier.weight(2.4f)) {
                    Text(
                        text = fw.version + if (fw.isRecommended) "  (recommended)" else "",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = FontSize.BodySmall,
                        fontWeight = FontWeight.Medium,
                        fontFamily = codeFontFamily(),
                    )
                    Text(
                        text = fw.buildId,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = FontSize.Micro,
                        fontFamily = codeFontFamily(),
                    )
                }
                Cell(fw.androidVersion, Modifier.weight(0.8f))
                Cell(fw.securityPatch, Modifier.weight(1.2f))
                Cell(fw.sourceKind.name.replace('_', ' ').lowercase(), Modifier.weight(1f))
                Box(modifier = Modifier.width(GlassDimens.AcquireTableActionColWidth)) {
                    GlassButton(
                        onClick = { onSelect(fw) },
                        variant = if (isSelected) GlassButtonVariant.Primary else GlassButtonVariant.Standard,
                        shape = GlassShapes.ShellControl,
                    ) {
                        Text(
                            if (isSelected) "✓ Selected" else "Select",
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

@Composable
private fun Cell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = FontSize.BodySmall,
        fontFamily = codeFontFamily(),
        modifier = modifier,
    )
}

@Composable
private fun FirmwareDetails(fw: TargetFirmware) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium)
            .testTag("SelectedFirmwareDetails"),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        Text(
            text = "Selected baseline",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.TitleSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )
        Detail("Version", fw.version)
        Detail("Build", fw.buildId)
        Detail("Download URL", fw.acquisitionUrl ?: "none — import the archive instead")
        Detail(
            "Expected SHA-256",
            fw.sha256?.takeIf { it.isNotBlank() }
                ?: if (fw.checksumUnavailable) "not published by the vendor" else "not set",
        )
        Detail("Fingerprint", fw.fingerprintTriple ?: "not set")
        fw.archiveSizeBytes?.let { Detail("Archive size", "$it bytes") }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.BodySmall,
            fontFamily = ideFontFamily(),
            modifier = Modifier.width(GlassDimens.AcquirePolicyLabelWidth),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontFamily = codeFontFamily(),
        )
    }
}
