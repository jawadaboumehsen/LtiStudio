/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.core.ui.partition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.layout.GlassRole
import org.ide.lti.core.designsystem.component.layout.glassSurface
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.partition.PartitionBudgetForecast

/**
 * Professional visual budget meter and headroom forecaster for Android Dynamic Partitions (super.img).
 * Displays partition allocations, free storage headroom, and predictive optimization tips.
 */
@Composable
fun SuperPartitionBudgetMeter(
    forecast: PartitionBudgetForecast,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                role = GlassRole.TextCard,
                shape = GlassShapes.HazeMedium,
            )
            .padding(Spacing.CardPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                Text(
                    text = "Dynamic Partitions (super.img) Capacity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "AOSP lpmake Block Geometry & Headroom Forecast",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val badgeColor = if (forecast.isOverflown) {
                MaterialTheme.colorScheme.error
            } else if (forecast.headroomPercent < 0.05f) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.primary
            }

            val statusText = if (forecast.isOverflown) {
                "OVERFLOW (+${forecast.overflowBytes / (1024 * 1024)} MB)"
            } else {
                "${(forecast.headroomPercent * 100).toInt()}% HEADROOM"
            }

            Box(
                modifier = Modifier
                    .clip(GlassShapes.Pill)
                    .background(badgeColor.copy(alpha = AlphaTokens.Hover))
                    .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                )
            }
        }

        // Segmented Visual Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.Large)
                .clip(GlassShapes.Compact)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = AlphaTokens.Subtle)),
        ) {
            Row(modifier = Modifier.matchParentSize()) {
                val totalCapacity = maxOf(1L, forecast.usableCapacityBytes)

                for (part in forecast.partitionBreakdown) {
                    val weight = (part.alignedSizeBytes.toFloat() / totalCapacity.toFloat()).coerceAtLeast(0.01f)
                    val sliceColor = when (part.name.lowercase()) {
                        "system" -> MaterialTheme.colorScheme.primary
                        "vendor" -> MaterialTheme.colorScheme.secondary
                        "product" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.outline
                    }

                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .fillMaxHeight()
                            .background(sliceColor.copy(alpha = AlphaTokens.Prominent)),
                    )
                }

                if (!forecast.isOverflown && forecast.headroomPercent > 0.001f) {
                    Box(
                        modifier = Modifier
                            .weight(forecast.headroomPercent)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = AlphaTokens.Faint)),
                    )
                }
            }
        }

        // Metrics Summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetricItem(
                label = "Super Capacity",
                value = formatBytes(forecast.superCapacityBytes),
            )
            MetricItem(
                label = "Allocated",
                value = formatBytes(forecast.allocatedBytes),
            )
            MetricItem(
                label = "Free Headroom",
                value = formatBytes(forecast.freeHeadroomBytes),
            )
        }

        // Warnings / Optimization Recommendations
        if (forecast.warnings.isNotEmpty() || forecast.recommendations.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GlassShapes.Small)
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AlphaTokens.Faint))
                    .padding(Spacing.SmallMedium),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                forecast.warnings.forEach { warning ->
                    Text(
                        text = "• $warning",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                    )
                }
                forecast.recommendations.forEach { tip ->
                    Text(
                        text = "💡 $tip",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        val gb = mb / 1024.0
        // Round to 2 decimals
        "${((gb * 100).toLong() / 100.0)} GB"
    } else {
        "${((mb * 100).toLong() / 100.0)} MB"
    }
}
