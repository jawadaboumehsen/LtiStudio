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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.plugin.InstalledRecord
import org.ide.lti.core.domain.plugin.TrustDecision
import org.ide.lti.core.domain.plugin.key

@Composable
internal fun PluginInstalledTab(
    records: List<InstalledRecord>,
    references: Map<String, List<String>>,
    searchQuery: String,
    activeWorkspaceId: String?,
    onOpenDetails: (InstalledRecord) -> Unit,
    onEnableWorkspace: (InstalledRecord, String) -> Unit,
    onDisableWorkspace: (InstalledRecord, String) -> Unit,
    onArchive: (InstalledRecord) -> Unit,
    onUninstall: (InstalledRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalContentColor provides GlassTheme.colors.onGlass) {
        val filtered = records.filter { record ->
            searchQuery.isBlank() ||
                record.identity.publisher.contains(searchQuery, ignoreCase = true) ||
                record.identity.id.contains(searchQuery, ignoreCase = true) ||
                record.identity.version.contains(searchQuery, ignoreCase = true)
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = modifier.fillMaxSize().padding(Spacing.Large),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (searchQuery.isBlank()) {
                        "No plugins installed yet. Explore Marketplace or Import a local package."
                    } else {
                        "No installed plugins match '$searchQuery'."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTheme.colors.onGlassSecondary,
                )
            }
        } else {
            LazyColumn(
                modifier = modifier.fillMaxSize().padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                items(filtered, key = { it.identity.key }) { record ->
                    InstalledPluginCard(
                        record = record,
                        refs = references[record.identity.key] ?: emptyList(),
                        activeWorkspaceId = activeWorkspaceId,
                        onOpenDetails = { onOpenDetails(record) },
                        onEnableWorkspace = { wsId -> onEnableWorkspace(record, wsId) },
                        onDisableWorkspace = { wsId -> onDisableWorkspace(record, wsId) },
                        onArchive = { onArchive(record) },
                        onUninstall = { onUninstall(record) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledPluginCard(
    record: InstalledRecord,
    refs: List<String>,
    activeWorkspaceId: String?,
    onOpenDetails: () -> Unit,
    onEnableWorkspace: (String) -> Unit,
    onDisableWorkspace: (String) -> Unit,
    onArchive: () -> Unit,
    onUninstall: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.Medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.identity.key,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GlassTheme.colors.onGlass,
                    )
                    Spacer(modifier = Modifier.height(Spacing.ExtraExtraSmall))
                    Text(
                        text = "v${record.identity.version} • sha256:${record.identity.contentDigest.take(8)}...",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = codeFontFamily(),
                        color = GlassTheme.colors.onGlassMuted,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                    TrustBadge(record.trust)
                    if (record.revoked) {
                        GlassChip(label = "Revoked")
                    } else if (record.archived) {
                        GlassChip(label = "Archived")
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.Small))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Referenced by: ${refs.size} consumer(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTheme.colors.onGlassSecondary,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                    GlassButton(
                        onClick = onOpenDetails,
                        variant = GlassButtonVariant.Standard,
                    ) {
                        Text("Details")
                    }

                    if (activeWorkspaceId != null) {
                        val isEnabled = record.enabledInWorkspaces.contains(activeWorkspaceId)
                        GlassButton(
                            onClick = {
                                if (isEnabled) {
                                    onDisableWorkspace(activeWorkspaceId)
                                } else {
                                    onEnableWorkspace(activeWorkspaceId)
                                }
                            },
                            enabled = !record.revoked && !record.archived,
                            variant = if (isEnabled) GlassButtonVariant.Secondary else GlassButtonVariant.Primary,
                        ) {
                            Text(if (isEnabled) "Disable for Workspace" else "Enable for Workspace")
                        }
                    }

                    if (!record.archived) {
                        GlassButton(
                            onClick = onArchive,
                            variant = GlassButtonVariant.Standard,
                        ) {
                            Text("Archive")
                        }
                    }

                    GlassButton(
                        onClick = onUninstall,
                        variant = GlassButtonVariant.Standard,
                    ) {
                        Text("Uninstall")
                    }
                }
            }
        }
    }
}

@Composable
private fun TrustBadge(decision: TrustDecision) {
    val label = when (decision) {
        TrustDecision.TRUSTED_SIGNATURE -> "Signed & Trusted"
        TrustDecision.TRUSTED_UNVERIFIED -> "Unverified"
        TrustDecision.UNTRUSTED -> "Untrusted"
    }
    GlassChip(label = label)
}
