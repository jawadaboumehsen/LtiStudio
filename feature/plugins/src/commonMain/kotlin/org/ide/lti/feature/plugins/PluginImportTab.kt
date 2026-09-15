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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.plugin.InstallState
import org.ide.lti.core.domain.plugin.PackageInspection
import org.ide.lti.core.domain.plugin.TrustDecision
import org.ide.lti.core.domain.plugin.key

@Composable
internal fun PluginImportTab(
    importPath: String,
    quarantinedPath: String?,
    inspection: PackageInspection?,
    isLegacyUnsupported: Boolean,
    legacyFormatName: String?,
    installState: InstallState?,
    onImportPathChange: (String) -> Unit,
    onInspectPath: (String) -> Unit,
    onDismissLegacyWarning: () -> Unit,
    onConfirmInstall: (TrustDecision) -> Unit,
    modifier: Modifier = Modifier,
) {
    var acknowledgedUnverified by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalContentColor provides GlassTheme.colors.onGlass) {
        Column(
            modifier = modifier.fillMaxSize().padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.Medium)) {
                    Text(
                        text = "Import Local Package Archive",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = GlassTheme.colors.onGlass,
                    )
                    Spacer(modifier = Modifier.height(Spacing.ExtraSmall))
                    Text(
                        text = "Select a local Kotlin SDK data package (.lti-mod.zip) to " +
                            "quarantine, inspect, and install.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassTheme.colors.onGlassSecondary,
                    )
                    Spacer(modifier = Modifier.height(Spacing.Medium))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassTextField(
                            value = importPath,
                            onValueChange = onImportPathChange,
                            placeholder = "C:/path/to/my-plugin.lti-mod.zip or /path/to/pkg.lti-mod.zip",
                            modifier = Modifier.weight(1f),
                        )
                        GlassButton(
                            onClick = { onInspectPath(importPath) },
                            enabled = importPath.isNotBlank(),
                            variant = GlassButtonVariant.Primary,
                        ) {
                            Text("Inspect")
                        }
                    }
                }
            }

            if (isLegacyUnsupported) {
                UnsupportedLegacyFormatPanel(
                    formatName = legacyFormatName ?: "legacy script",
                    onDismiss = onDismissLegacyWarning,
                )
            } else if (inspection != null) {
                QuarantinedInspectionCard(
                    inspection = inspection,
                    quarantinedPath = quarantinedPath,
                    acknowledgedUnverified = acknowledgedUnverified,
                    onToggleAcknowledge = { acknowledgedUnverified = !acknowledgedUnverified },
                    onConfirmInstall = {
                        val decision = if (inspection.hasSignature) {
                            TrustDecision.TRUSTED_SIGNATURE
                        } else {
                            TrustDecision.TRUSTED_UNVERIFIED
                        }
                        onConfirmInstall(decision)
                    },
                )
            }

            installState?.let { state ->
                ImportInstallStateCard(state = state)
            }
        }
    }
}

@Composable
private fun UnsupportedLegacyFormatPanel(formatName: String, onDismiss: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.Medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Unsupported Legacy Format Detected ($formatName)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GlassTheme.colors.glassHighlight,
                )
                GlassChip(label = "Format Incompatible")
            }

            Spacer(modifier = Modifier.height(Spacing.Small))

            Text(
                text = "Legacy shell scripts (e.g. customize.sh) and foreign patch bundles cannot be " +
                    "directly executed or imported. To prevent arbitrary code execution on host ROMs, " +
                    "this app strictly enforces the declarative Kotlin SDK package format.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlass,
            )

            Spacer(modifier = Modifier.height(Spacing.Medium))

            Text(
                text = "Conversion Checklist (Kotlin SDK Package):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = GlassTheme.colors.glassAccent,
            )
            Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

            val checklist = listOf(
                "1. Create a Kotlin Gradle project in IntelliJ IDEA targeting ':sdk:patch-mod'.",
                "2. Define manifest.json declaring publisher, package id, version, and sdkApiRange.",
                "3. Replace imperative shell commands with typed mutation operations (FileOp, PropertyOp, etc.).",
                "4. Compile DEX/Smali hook classes using the Kotlin Author SDK payload compiler.",
                "5. Package everything into a hermetic .lti-mod.zip archive with manifest and SHA-256 content digests.",
                "6. Verify with ':sdk:patch-mod:validateMod' and test offline before importing.",
            )

            checklist.forEach { item ->
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTheme.colors.onGlassSecondary,
                    modifier = Modifier.padding(vertical = Spacing.ExtraExtraSmall),
                )
            }

            Spacer(modifier = Modifier.height(Spacing.Medium))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GlassButton(onClick = onDismiss, variant = GlassButtonVariant.Standard) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun QuarantinedInspectionCard(
    inspection: PackageInspection,
    quarantinedPath: String?,
    acknowledgedUnverified: Boolean,
    onToggleAcknowledge: () -> Unit,
    onConfirmInstall: () -> Unit,
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
                        text = inspection.identity?.key ?: "Unknown Package",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GlassTheme.colors.onGlass,
                    )
                    quarantinedPath?.let {
                        Text(
                            text = "Quarantined archive: $it",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = codeFontFamily(),
                            color = GlassTheme.colors.onGlassMuted,
                        )
                    }
                }

                GlassChip(label = if (inspection.hasSignature) "Cryptographically Signed" else "Unsigned")
            }

            Spacer(modifier = Modifier.height(Spacing.Small))

            if (inspection.report.hasBlockingErrors()) {
                Text(
                    text = "Package Inspection Errors:",
                    style = MaterialTheme.typography.labelMedium,
                    color = GlassTheme.colors.glassHighlight,
                )
                inspection.report.errors.forEach { err ->
                    Text(
                        text = "• ${err.fieldPath}: ${err.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassTheme.colors.glassHighlight,
                    )
                }
            } else {
                val digestPrefix = inspection.identity?.contentDigest?.take(12)
                Text(
                    text = "Version: ${inspection.identity?.version} • sha256:$digestPrefix...",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = codeFontFamily(),
                    color = GlassTheme.colors.onGlass,
                )

                Spacer(modifier = Modifier.height(Spacing.Small))

                if (inspection.dependencies.isNotEmpty()) {
                    Text(
                        text = "Dependencies: ${inspection.dependencies.joinToString { "${it.publisher}:${it.id}" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassTheme.colors.onGlassSecondary,
                    )
                }

                if (!inspection.hasSignature) {
                    Spacer(modifier = Modifier.height(Spacing.Small))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val ackText = if (acknowledgedUnverified) {
                            "[✓] Unverified Acknowledged"
                        } else {
                            "[ ] Acknowledge Unverified"
                        }
                        GlassButton(
                            onClick = onToggleAcknowledge,
                            variant = if (acknowledgedUnverified) {
                                GlassButtonVariant.Primary
                            } else {
                                GlassButtonVariant.Standard
                            },
                        ) {
                            Text(ackText)
                        }
                        Text(
                            text = "Unsigned packages require explicit acknowledgment before installation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassTheme.colors.onGlassMuted,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.Medium))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    val canInstall = inspection.hasSignature || acknowledgedUnverified
                    GlassButton(
                        onClick = onConfirmInstall,
                        enabled = canInstall,
                        variant = GlassButtonVariant.Primary,
                    ) {
                        Text("Approve & Install")
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportInstallStateCard(state: InstallState) {
    val message = when (state) {
        InstallState.SourceSelected -> "Source selected..."
        InstallState.Downloading -> "Processing package bytes..."
        InstallState.Quarantined -> "Quarantining package..."
        InstallState.Verified -> "Integrity verified. Promoting package to installed library..."
        is InstallState.Installed -> "Installed ${state.record.publisher}:${state.record.id}"
        is InstallState.Failed -> "Failed: ${state.report.errors.firstOrNull()?.message ?: "Error"}"
        InstallState.Cancelled -> "Cancelled"
    }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = GlassTheme.colors.glassAccent,
            modifier = Modifier.padding(Spacing.Small),
        )
    }
}
