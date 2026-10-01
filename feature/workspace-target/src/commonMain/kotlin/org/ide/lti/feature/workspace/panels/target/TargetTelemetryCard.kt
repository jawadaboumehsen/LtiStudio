/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.target

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.target.TargetDevice

/**
 * Silicon, partition geometry, and active firmware telemetry summary for [target].
 */
@Composable
fun TargetTelemetryCard(target: TargetDevice, modifier: Modifier = Modifier) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = Spacing.CardPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium)) {
            Text(
                text = "Hardware Telemetry",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TelemetryRow("SoC Platform", target.socPlatform)
            TelemetryRow("Filesystem", target.filesystemType)
            TelemetryRow("Super Partition", target.formattedSuperSize)
            TelemetryRow("Super Group", target.formattedSuperGroupSize)
            TelemetryRow("Boot Partition", target.formattedBootSize)
            TelemetryRow("Vendor Boot", target.formattedVendorBootSize)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                GlassChip(label = target.status.displayName, selected = false)
                GlassChip(label = "${target.availableRegions.size} Regions", selected = false)
                GlassChip(label = if (target.virtualAb) "Virtual A/B" else "A/B", selected = false)
            }
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
