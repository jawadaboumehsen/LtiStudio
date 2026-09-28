/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.plugin.InstalledRecord
import org.ide.lti.core.domain.plugin.key

@Composable
internal fun PluginDetailsDialog(record: InstalledRecord, references: List<String>, onDismiss: () -> Unit) {
    GlassDialog(
        title = "Package Details: ${record.identity.key}",
        onDismissRequest = onDismiss,
        confirmButton = {
            GlassButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(ComponentSize.ActionCardHeight * 3),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                item {
                    DetailSection(title = "Identity") {
                        DetailRow("Publisher", record.identity.publisher)
                        DetailRow("Package ID", record.identity.id)
                        DetailRow("Version", record.identity.version)
                        DetailRow("Content Digest", record.identity.contentDigest)
                    }
                }

                item {
                    DetailSection(title = "Status & Trust") {
                        DetailRow("Trust Decision", record.trust.name)
                        DetailRow("Revoked", if (record.revoked) "Yes (Blocked from execution)" else "No")
                        DetailRow("Archived", if (record.archived) "Yes (Hidden from active builds)" else "No")
                    }
                }

                item {
                    DetailSection(title = "References (${references.size})") {
                        if (references.isEmpty()) {
                            Text(
                                text = "Not referenced by any active workspace or dependency.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            references.forEach { ref ->
                                Text(
                                    text = "• $ref",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                item {
                    DetailSection(title = "Dependencies (${record.dependencies.size})") {
                        if (record.dependencies.isEmpty()) {
                            Text(
                                text = "None declared.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            record.dependencies.forEach { dep ->
                                Text(
                                    text = "• ${dep.publisher}:${dep.id} (${dep.versionRange})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                item {
                    DetailSection(title = "Conflicts (${record.conflicts.size})") {
                        if (record.conflicts.isEmpty()) {
                            Text(
                                text = "None declared.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            record.conflicts.forEach { c ->
                                Text(
                                    text = "• ${c.publisher}:${c.id} (${c.versionRange})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                item {
                    DetailSection(title = "Typed Permissions & Execution Policy") {
                        Text(
                            text = "Data-only package. Author code is compiled to smali/DEX payloads and " +
                                "executed by ROM engine operations. No arbitrary shell script execution permitted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item {
                    DetailSection(title = "Safe README / License") {
                        Text(
                            text = "Standard LtiRom SDK Data Plugin License. All operations declarative.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.ExtraExtraSmall),
        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(Spacing.Small)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(Spacing.ExtraSmall))
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.ExtraExtraSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = codeFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
