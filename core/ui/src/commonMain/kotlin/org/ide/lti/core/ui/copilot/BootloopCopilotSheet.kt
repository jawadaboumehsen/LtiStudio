/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui.copilot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassTextButton
import org.ide.lti.core.designsystem.component.display.rememberCopyToClipboard
import org.ide.lti.core.designsystem.component.layout.GlassRole
import org.ide.lti.core.designsystem.component.layout.glassContainer
import org.ide.lti.core.designsystem.component.layout.glassSurface
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.copilot.BootloopDiagnosis
import org.ide.lti.core.domain.copilot.CrashSeverity

/**
 * Intelligent diagnostic sheet displaying real-time bootloop anomalies, native crashes,
 * watchdog timeouts, and generated SELinux allow rules.
 */
@Composable
fun BootloopCopilotSheet(
    diagnoses: List<BootloopDiagnosis>,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    val copyToClipboard = rememberCopyToClipboard()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                role = GlassRole.TextCard,
                shape = GlassShapes.HazeLarge,
            )
            .padding(Spacing.CardPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                Text(
                    text = "Bootloop & Crash Copilot",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Continuous Runtime & Kernel Telemetry Analysis",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (onDismiss != null) {
                GlassTextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }

        if (diagnoses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GlassShapes.Medium)
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AlphaTokens.Faint))
                    .padding(Spacing.Large),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✓ No active crash signatures, deadlocks, or SELinux denials detected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassContainer(),
                verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
            ) {
                items(diagnoses, key = { it.id }) { item ->
                    DiagnosisItem(
                        diagnosis = item,
                        onCopyRule = { rule ->
                            copyToClipboard(rule)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosisItem(diagnosis: BootloopDiagnosis, onCopyRule: (String) -> Unit) {
    val severityColor = when (diagnosis.severity) {
        CrashSeverity.CRITICAL -> MaterialTheme.colorScheme.error
        CrashSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
        CrashSeverity.INFO -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(
                role = GlassRole.TextCard,
                shape = GlassShapes.HazeMedium,
            )
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        // Title & Severity Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = diagnosis.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            Box(
                modifier = Modifier
                    .clip(GlassShapes.Pill)
                    .background(severityColor.copy(alpha = AlphaTokens.Hover))
                    .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall),
            ) {
                Text(
                    text = diagnosis.severity.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = severityColor,
                )
            }
        }

        // Offending Process
        if (diagnosis.offendingProcess != null) {
            Text(
                text = "Process: ${diagnosis.offendingProcess}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        // Root Cause Description
        Text(
            text = diagnosis.rootCauseDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // Remediation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassShapes.Small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = AlphaTokens.Faint))
                .padding(Spacing.Small),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                Text(
                    text = "Recommended Remediation:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = diagnosis.suggestedRemediation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Generated SELinux Allow Rules
        if (diagnosis.generatedSepolicyRules.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.ExtraSmall),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Text(
                    text = "Generated AOSP SELinux Allow Rules:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                diagnosis.generatedSepolicyRules.forEach { rule ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(GlassShapes.Small)
                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AlphaTokens.Subtle))
                            .padding(Spacing.Small),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = rule.formattedRule,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = codeFontFamily(),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        GlassTextButton(
                            onClick = { onCopyRule(rule.formattedRule) },
                        ) {
                            Text("Copy")
                        }
                    }
                }
            }
        }
    }
}
