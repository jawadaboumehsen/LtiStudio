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
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.glass.GlassSceneState
import org.ide.lti.core.model.target.TargetDevice

/**
 * Silicon, partition geometry, and active firmware telemetry summary for [target].
 */
@Composable
fun TargetTelemetryCard(
    target: TargetDevice,
    modifier: Modifier = Modifier,
    backdrop: GlassSceneState? = null,
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        backdrop = backdrop,
        contentPadding = Spacing.CardPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium)) {
            Text(
                text = "Hardware Telemetry",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = GlassTheme.colors.onGlass,
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
            color = GlassTheme.colors.onGlassMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = GlassTheme.colors.onGlass,
        )
    }
}
