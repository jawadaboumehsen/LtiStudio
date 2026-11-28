/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.layout.GlassRole
import org.ide.lti.core.designsystem.component.layout.glassContainer
import org.ide.lti.core.designsystem.component.layout.glassSurface
import org.ide.lti.core.designsystem.theme.AccentPresetColors
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.diff.ConfigDiffResult
import org.ide.lti.core.domain.diff.DiffKind
import org.ide.lti.core.domain.diff.PropertyDiffEntry
import org.ide.lti.core.domain.diff.PropertyRiskLevel

/**
 * Visual glass property & config diff inspector for Android build.prop, system.prop,
 * and pre-flash rollback verification.
 */
@Composable
fun PropertyDiffViewer(
    diffResult: ConfigDiffResult,
    modifier: Modifier = Modifier,
    snapshotName: String? = null,
    onRollback: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .testTag("PropertyDiffViewer")
            .glassSurface(role = GlassRole.TextCard, shape = GlassShapes.HazeMedium, drawBorder = true)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Property & Config Diff",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = ideFontFamily(),
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                if (snapshotName != null) {
                    Text(
                        text = "Snapshot: $snapshotName",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = ideFontFamily(),
                            fontSize = FontSize.Micro,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                DiffCountBadge("+${diffResult.addedCount}", AccentPresetColors.Emerald)
                DiffCountBadge("-${diffResult.removedCount}", AccentPresetColors.Crimson)
                DiffCountBadge("~${diffResult.modifiedCount}", AccentPresetColors.Cobalt)

                if (onRollback != null) {
                    Box(
                        modifier = Modifier
                            .testTag("RollbackButton")
                            .clip(GlassShapes.Small)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Hover))
                            .clickable(onClick = onRollback)
                            .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
                    ) {
                        Text(
                            text = "Rollback",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = ideFontFamily(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        }

        if (diffResult.hasCriticalRisks) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GlassShapes.Small)
                    .background(AccentPresetColors.Crimson.copy(alpha = AlphaTokens.Hover))
                    .padding(Spacing.Small),
            ) {
                Text(
                    text = "CRITICAL: Security or encryption property modified. Bootloop or decryption risks present.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = ideFontFamily(),
                        color = AccentPresetColors.Crimson,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }

        // Virtualized entries list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = ComponentSize.DialogDefaultHeight)
                .glassContainer(),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            items(diffResult.entries, key = { it.key }) { entry ->
                PropertyDiffRow(entry = entry)
            }
        }
    }
}

@Composable
private fun DiffCountBadge(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = codeFontFamily(),
            fontSize = FontSize.Micro,
            fontWeight = FontWeight.Bold,
            color = color,
        ),
        modifier = Modifier
            .clip(GlassShapes.ExtraSmall)
            .background(color.copy(alpha = AlphaTokens.Hover))
            .padding(horizontal = Spacing.ExtraSmall, vertical = Spacing.ExtraExtraSmall),
    )
}

@Composable
private fun PropertyDiffRow(entry: PropertyDiffEntry) {
    val (kindColor, kindSymbol) = when (entry.kind) {
        DiffKind.ADDED -> AccentPresetColors.Emerald to "+"
        DiffKind.REMOVED -> AccentPresetColors.Crimson to "-"
        DiffKind.MODIFIED -> AccentPresetColors.Cobalt to "~"
        DiffKind.UNCHANGED -> MaterialTheme.colorScheme.onSurfaceVariant to " "
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.Small)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AlphaTokens.Half))
            .padding(Spacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Text(
                    text = kindSymbol,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = codeFontFamily(),
                        fontWeight = FontWeight.Bold,
                        color = kindColor,
                    ),
                )
                Text(
                    text = entry.key,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = codeFontFamily(),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }

            if (entry.riskLevel != PropertyRiskLevel.SAFE) {
                val riskColor = when (entry.riskLevel) {
                    PropertyRiskLevel.CRITICAL -> AccentPresetColors.Crimson
                    PropertyRiskLevel.WARNING -> AccentPresetColors.Amber
                    PropertyRiskLevel.INFO -> AccentPresetColors.Cyan
                    PropertyRiskLevel.SAFE -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = entry.riskLevel.name,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = codeFontFamily(),
                        fontSize = FontSize.Micro,
                        color = riskColor,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier
                        .clip(GlassShapes.ExtraSmall)
                        .background(riskColor.copy(alpha = AlphaTokens.Hover))
                        .padding(horizontal = Spacing.ExtraSmall, vertical = Spacing.ExtraExtraSmall),
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.ExtraExtraSmall))

        val oldValue = entry.oldValue
        val newValue = entry.newValue
        val riskExplanation = entry.riskExplanation

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            if (oldValue != null) {
                Text(
                    text = oldValue,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = codeFontFamily(),
                        fontSize = FontSize.Micro,
                        color = AccentPresetColors.Crimson.copy(alpha = AlphaTokens.Prominent),
                    ),
                )
            }
            if (oldValue != null && newValue != null) {
                Text(
                    text = "->",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = codeFontFamily(),
                        fontSize = FontSize.Micro,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
            if (newValue != null) {
                Text(
                    text = newValue,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = codeFontFamily(),
                        fontSize = FontSize.Micro,
                        color = AccentPresetColors.Emerald,
                    ),
                )
            }
        }

        if (riskExplanation != null) {
            Text(
                text = riskExplanation,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = ideFontFamily(),
                    fontSize = FontSize.Micro,
                    color = AccentPresetColors.Amber,
                ),
                modifier = Modifier.padding(top = Spacing.ExtraExtraSmall),
            )
        }
    }
}
