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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.plugin.IndexEntry
import org.ide.lti.core.domain.plugin.IndexFailure
import org.ide.lti.core.domain.plugin.IndexFetchResult
import org.ide.lti.core.domain.plugin.IndexSource
import org.ide.lti.core.domain.plugin.InstallState
import org.ide.lti.core.domain.plugin.TrustDecision

@Composable
internal fun PluginMarketplaceTab(
    source: IndexSource?,
    urlInput: String,
    entries: List<IndexEntry>,
    fetchResult: IndexFetchResult?,
    isLoading: Boolean,
    installState: InstallState?,
    searchQuery: String,
    onUrlInputChange: (String) -> Unit,
    onConfigureSource: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onNavigateToImport: () -> Unit,
    onInstall: (IndexEntry, TrustDecision) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingInstallEntry by remember { mutableStateOf<IndexEntry?>(null) }
    var showConfigureModal by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalContentColor provides GlassTheme.colors.onGlass) {
        Column(modifier = modifier.fillMaxSize().padding(Spacing.Medium)) {
            MarketplaceSourceHeader(
                source = source,
                isLoading = isLoading,
                onOpenConfigure = { showConfigureModal = true },
                onRefresh = onRefresh,
                onNavigateToImport = onNavigateToImport,
            )

            Spacer(modifier = Modifier.height(Spacing.Small))

            fetchResult?.let { result ->
                MarketplaceResultBanner(result = result)
                Spacer(modifier = Modifier.height(Spacing.Small))
            }

            installState?.let { state ->
                InstallStateBanner(state = state)
                Spacer(modifier = Modifier.height(Spacing.Small))
            }

            val filtered = entries.filter {
                searchQuery.isBlank() ||
                    it.publisher.contains(searchQuery, ignoreCase = true) ||
                    it.id.contains(searchQuery, ignoreCase = true) ||
                    it.targetSummary.contains(searchQuery, ignoreCase = true)
            }

            if (entries.isEmpty() && source == null) {
                UnconfiguredMarketplaceState(
                    onConfigureSource = { showConfigureModal = true },
                    onNavigateToImport = onNavigateToImport,
                )
            } else if (filtered.isEmpty()) {
                val emptyMsg = if (searchQuery.isBlank()) {
                    "No entries in marketplace index."
                } else {
                    "No entries match '$searchQuery'."
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = emptyMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTheme.colors.onGlassSecondary,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    items(filtered, key = { "${it.publisher}:${it.id}:${it.version}" }) { entry ->
                        MarketplaceEntryCard(
                            entry = entry,
                            onInstallClick = { pendingInstallEntry = entry },
                        )
                    }
                }
            }
        }

        if (showConfigureModal) {
            ConfigureSourceDialog(
                url = urlInput,
                onUrlChange = onUrlInputChange,
                onDismiss = { showConfigureModal = false },
                onConfirm = {
                    onConfigureSource("Repository", urlInput)
                    showConfigureModal = false
                },
            )
        }

        pendingInstallEntry?.let { entry ->
            MarketplaceTrustDialog(
                entry = entry,
                onDismiss = { pendingInstallEntry = null },
                onConfirmTrust = { decision ->
                    onInstall(entry, decision)
                    pendingInstallEntry = null
                },
            )
        }
    }
}

@Composable
private fun MarketplaceSourceHeader(
    source: IndexSource?,
    isLoading: Boolean,
    onOpenConfigure: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateToImport: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.Small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source?.label ?: "No Marketplace Source Configured",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassTheme.colors.onGlass,
                )
                Text(
                    text = source?.url ?: "Configure a repository HTTPS index URL to browse plugins",
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTheme.colors.onGlassMuted,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                GlassButton(onClick = onOpenConfigure, variant = GlassButtonVariant.Standard) {
                    Text("Configure source")
                }
                if (source != null) {
                    GlassButton(onClick = onRefresh, enabled = !isLoading, variant = GlassButtonVariant.Standard) {
                        Text(if (isLoading) "Fetching..." else "Refresh")
                    }
                }
                GlassButton(onClick = onNavigateToImport, variant = GlassButtonVariant.Standard) {
                    Text("Import local")
                }
            }
        }
    }
}

@Composable
private fun MarketplaceResultBanner(result: IndexFetchResult) {
    when (result) {
        is IndexFetchResult.Cached -> {
            val ageSec = ((System.currentTimeMillis() - result.observedAtEpochMs) / 1000).coerceAtLeast(0)
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Offline Mode: Showing cached index observed $ageSec seconds ago.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTheme.colors.glassAccent,
                    modifier = Modifier.padding(Spacing.Small),
                )
            }
        }
        is IndexFetchResult.Failed -> {
            val failureText = when (val reason = result.reason) {
                IndexFailure.Unauthenticated -> "Authentication Required: Access token or credentials missing"
                IndexFailure.Offline -> "Network Offline: Unable to reach marketplace endpoint"
                is IndexFailure.Malformed -> "Malformed Response: ${reason.detail}"
                is IndexFailure.RateLimited -> {
                    val waitSec = reason.retryAfterSeconds ?: 60
                    "Rate Limited: Too many requests, retry after ${waitSec}s"
                }
                IndexFailure.InsecureSource -> "Insecure Source Rejected: HTTPS protocol is strictly required"
                is IndexFailure.ServerError -> "Server Error: Endpoint returned HTTP ${reason.code}"
            }
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Marketplace Fetch Error: $failureText",
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTheme.colors.glassHighlight,
                    modifier = Modifier.padding(Spacing.Small),
                )
            }
        }
        is IndexFetchResult.Fetched -> Unit
    }
}

@Composable
private fun InstallStateBanner(state: InstallState) {
    val text = when (state) {
        InstallState.SourceSelected -> "Preparing download..."
        InstallState.Downloading -> "Downloading package archive..."
        InstallState.Quarantined -> "Package quarantined; verifying digest and manifest..."
        InstallState.Verified -> "Integrity verified; promoting to installed library..."
        is InstallState.Installed -> "Successfully installed ${state.record.publisher}:${state.record.id}"
        is InstallState.Failed -> "Install failed: ${state.report.errors.firstOrNull()?.message ?: "Check rejected"}"
        InstallState.Cancelled -> "Installation cancelled"
    }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = GlassTheme.colors.glassAccent,
            modifier = Modifier.padding(Spacing.Small),
        )
    }
}

@Composable
private fun UnconfiguredMarketplaceState(onConfigureSource: () -> Unit, onNavigateToImport: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No marketplace index configured.",
                style = MaterialTheme.typography.titleMedium,
                color = GlassTheme.colors.onGlass,
            )
            Spacer(modifier = Modifier.height(Spacing.ExtraSmall))
            Text(
                text = "Marketplace repositories are explicit user-configured HTTPS URLs.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            Spacer(modifier = Modifier.height(Spacing.Medium))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                GlassButton(onClick = onConfigureSource, variant = GlassButtonVariant.Primary) {
                    Text("Configure source")
                }
                GlassButton(onClick = onNavigateToImport, variant = GlassButtonVariant.Standard) {
                    Text("Import local")
                }
            }
        }
    }
}

@Composable
private fun MarketplaceEntryCard(entry: IndexEntry, onInstallClick: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.Medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${entry.publisher}:${entry.id}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GlassTheme.colors.onGlass,
                    )
                    Spacer(modifier = Modifier.height(Spacing.ExtraExtraSmall))
                    Text(
                        text = "v${entry.version} • SDK ${entry.sdkApiRange}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = codeFontFamily(),
                        color = GlassTheme.colors.onGlassMuted,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                    if (entry.revoked) {
                        GlassChip(label = "Known Revocation")
                    } else if (entry.signatureIdentity != null) {
                        GlassChip(label = "Signed")
                    } else {
                        GlassChip(label = "Unsigned")
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.Small))
            Text(
                text = entry.targetSummary,
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            Spacer(modifier = Modifier.height(Spacing.Small))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "sha256:${entry.contentDigest.take(12)}...",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = codeFontFamily(),
                    color = GlassTheme.colors.onGlassMuted,
                )

                GlassButton(
                    onClick = onInstallClick,
                    enabled = !entry.revoked,
                    variant = GlassButtonVariant.Primary,
                ) {
                    Text("Install...")
                }
            }
        }
    }
}

@Composable
private fun ConfigureSourceDialog(
    url: String,
    onUrlChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    GlassDialog(
        title = "Configure Marketplace Source",
        onDismissRequest = onDismiss,
        confirmButton = {
            GlassButton(onClick = onConfirm, variant = GlassButtonVariant.Primary) {
                Text("Save & Fetch")
            }
        },
        dismissButton = {
            GlassButton(onClick = onDismiss, variant = GlassButtonVariant.Standard) {
                Text("Cancel")
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Enter a repository-backed HTTPS index URL (must start with https://):",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            Spacer(modifier = Modifier.height(Spacing.Small))
            GlassTextField(
                value = url,
                onValueChange = onUrlChange,
                placeholder = "https://example.com/lti-rom/index.json",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MarketplaceTrustDialog(entry: IndexEntry, onDismiss: () -> Unit, onConfirmTrust: (TrustDecision) -> Unit) {
    val isSigned = entry.signatureIdentity != null
    GlassDialog(
        title = "Trust Decision: ${entry.publisher}:${entry.id}",
        onDismissRequest = onDismiss,
        confirmButton = {
            GlassButton(
                onClick = {
                    val decision = if (isSigned) TrustDecision.TRUSTED_SIGNATURE else TrustDecision.TRUSTED_UNVERIFIED
                    onConfirmTrust(decision)
                },
                variant = GlassButtonVariant.Primary,
            ) {
                Text(if (isSigned) "Approve & Install" else "Accept Unverified & Install")
            }
        },
        dismissButton = {
            GlassButton(onClick = onDismiss, variant = GlassButtonVariant.Standard) {
                Text("Cancel")
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val trustExplanation = if (isSigned) {
                "This package is cryptographically signed by '${entry.signatureIdentity}'. " +
                    "Installing will add it to your local library without enabling it for any workspace."
            } else {
                "This package is unsigned. An explicit Unverified acknowledgment is required " +
                    "before installation can proceed. Installing will never execute author code directly."
            }
            Text(
                text = trustExplanation,
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTheme.colors.onGlass,
            )
            Spacer(modifier = Modifier.height(Spacing.Small))
            Text(
                text = "Workflow note: Installing adds this package to the local library. " +
                    "Enabling it for a workspace is a separate, explicit action in ROM Setup Studio.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassMuted,
            )
        }
    }
}
