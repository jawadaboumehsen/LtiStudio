/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.plugins.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.PluginThemeColors
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.plugin.AuthorDiagnostic

/**
 * Dense diagnostics table matching Concept 15.
 */
@Composable
public fun PluginDiagnosticsTable(diagnostics: List<AuthorDiagnostic>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.MediumSmall)
            .background(PluginThemeColors.CardBackground)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = PluginThemeColors.CardBorder,
                shape = GlassShapes.MediumSmall,
            ),
    ) {
        DiagnosticTableHeader()

        if (diagnostics.isEmpty()) {
            DiagnosticEmptyState()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ComponentSize.ActionCardHeight * 2),
            ) {
                itemsIndexed(diagnostics) { idx, diag ->
                    DiagnosticTableRow(idx = idx, diag = diag)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticTableHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PluginThemeColors.HeaderBackground)
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(ComponentSize.PluginDiagnosticsTableColIndex),
        )
        Text(
            text = "Severity",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(ComponentSize.PluginDiagnosticsTableColSeverity),
        )
        Text(
            text = "File",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(ComponentSize.PluginDiagnosticsTableColFile),
        )
        Text(
            text = "Message",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(2f),
        )
        Text(
            text = "Code",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(ComponentSize.PluginDiagnosticsTableColTime),
        )
    }
}

@Composable
private fun DiagnosticEmptyState(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.Medium),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "No diagnostic errors or warnings emitted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiagnosticTableRow(idx: Int, diag: AuthorDiagnostic, modifier: Modifier = Modifier) {
    val isError = diag.severity.equals("error", ignoreCase = true)
    val isWarn = diag.severity.equals("warning", ignoreCase = true)
    val sevColor = when {
        isError -> GlassTheme.diagnosticColors.error
        isWarn -> GlassTheme.diagnosticColors.warning
        else -> GlassTheme.diagnosticColors.info
    }

    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Faint)
    val borderModifier = Modifier.drawBehind {
        drawLine(
            color = dividerColor,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = StrokeWidth.Hairline.toPx(),
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(borderModifier)
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (idx + 1).toString(),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = codeFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(ComponentSize.PluginDiagnosticsTableColIndex),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            modifier = Modifier.width(ComponentSize.PluginDiagnosticsTableColSeverity),
        ) {
            Icon(
                painter = if (isError) {
                    AppIcons.ClearPainterResource()
                } else {
                    AppIcons.InfoPainterResource()
                },
                contentDescription = diag.severity,
                tint = sevColor,
                modifier = Modifier.size(IconSize.Small),
            )
            Text(
                text = diag.severity.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = sevColor,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = diag.file ?: "-",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = codeFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(ComponentSize.PluginDiagnosticsTableColFile),
        )

        Text(
            text = diag.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(2f),
        )

        Text(
            text = diag.code.ifBlank { "—" },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = codeFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(ComponentSize.PluginDiagnosticsTableColTime),
        )
    }
}
