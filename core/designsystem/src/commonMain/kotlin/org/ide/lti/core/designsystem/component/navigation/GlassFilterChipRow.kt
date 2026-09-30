/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

/**
 * Reusable Liquid Glass horizontal wrap-flow of category filter capsules.
 *
 * Generalizes the category filter rows in ToolMatrixTable and DoctorDiagnosticsDrawer:
 * renders a [FlowRow] of capsule chips displaying "$label ($count)" with glass accent tint
 * and border when selected, and faint background with glass border when unselected.
 *
 * @param items List of filter chip specifications.
 * @param selectedValue Currently selected item value.
 * @param onSelect Callback invoked with the selected item's [GlassFilterChipItem.value].
 * @param modifier Optional layout modifier.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlassFilterChipRow(
    items: List<GlassFilterChipItem>,
    selectedValue: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            items.forEach { item ->
                val isSelected = selectedValue == item.value
                val countText = if (item.count >= 0) " (${item.count})" else ""
                val chipText = "${item.label}$countText"

                val background = if (GlassTheme.effectsEnabled) {
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.GlassSecondaryTint)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaTokens.UltraFaint)
                    }
                } else {
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                }
                val borderColor = if (GlassTheme.effectsEnabled) {
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                } else {
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(GlassShapes.Capsule)
                        .background(background)
                        .glassOutlineBorder(
                            width = StrokeWidth.Hairline,
                            color = borderColor,
                            shape = GlassShapes.Capsule,
                        )
                        .clickable { onSelect(item.value) }
                        .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
                ) {
                    Text(
                        text = chipText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
