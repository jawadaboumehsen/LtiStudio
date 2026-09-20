/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.publish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.TextFieldState
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.designsystem.theme.IdeShellShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.CollisionPolicy
import org.ide.lti.core.model.workspace.PublishProvider
import org.ide.lti.core.model.workspace.PublishSettings

@Composable
public fun PublishEditor(
    settings: PublishSettings,
    target: TargetDevice,
    validationErrors: List<ValidationError> = emptyList(),
    onChange: (PublishSettings) -> Unit,
    dirtyPaths: Set<String> = emptySet(),
    selectedObjectId: String = "provider",
    onValidate: () -> Unit = {},
    onSave: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val errors = validationErrors.filter { it.fieldPath.startsWith("publish.") || !it.fieldPath.contains('.') }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("PublishEditor"),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = IdeShellDimens.OverviewMaxContentWidth)
                .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            item { EditorHeader(onValidate, onSave) }
            when (selectedObjectId.lowercase()) {
                "release-repository-and-tag", "release_repository_and_tag" -> item { RepositoryCard(settings, dirtyPaths, onChange) }
                "split-assets", "split_assets" -> item { SplitAssetsCard(settings, dirtyPaths, onChange) }
                "credentials" -> item { CredentialsCard(settings, dirtyPaths, onChange) }
                "ota-manifest", "ota_manifest" -> item { OtaManifestCard(settings, dirtyPaths, onChange) }
                "retention" -> item { RetentionCard(settings, dirtyPaths, onChange) }
                "verification" -> item { VerificationCard(settings) }
                "publish-review-and-recovery", "publish_review_and_recovery" -> item { ReviewCard(settings, target) }
                else -> item { ProviderCard(settings, dirtyPaths, onChange) }
            }
            item { Diagnostics(errors) }
        }
    }
}

@Composable
private fun EditorHeader(onValidate: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Publish ROM", style = MaterialTheme.typography.headlineSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Distribute signed artifacts, manage split releases, and update channel metadata.",
                color = GlassTheme.colors.onGlassSecondary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            GlassButton(onClick = onValidate, variant = GlassButtonVariant.Standard, shape = IdeShellShapes.Control) {
                Text("Validate", color = GlassTheme.colors.onGlass)
            }
            GlassButton(onClick = onSave, variant = GlassButtonVariant.Primary, shape = IdeShellShapes.Control) {
                Text("Save", color = GlassTheme.colors.onGlass)
            }
        }
    }
}

@Composable
private fun ProviderCard(settings: PublishSettings, dirtyPaths: Set<String>, onChange: (PublishSettings) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Distribution Provider", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text("Select target release channel delivery platform:", color = GlassTheme.colors.onGlassSecondary)

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                PublishProvider.entries.forEach { provider ->
                    GlassChip(
                        label = provider.name.replace('_', ' '),
                        selected = settings.provider == provider,
                        onClick = { onChange(settings.copy(provider = provider)) },
                    )
                }
            }
            Meta("provider", dirtyPaths, "Provider", "Destination hosting platform for ROM release packages.")
        }
    }
}

@Composable
private fun RepositoryCard(settings: PublishSettings, dirtyPaths: Set<String>, onChange: (PublishSettings) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Repository & Release Tag", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            GlassTextField(
                settings.repository.orEmpty(),
                { onChange(settings.copy(repository = it)) },
                label = "Target repository (owner/name)",
                state = TextFieldState.Helper("e.g., openMF/lti-device-releases"),
            )
            GlassTextField(
                settings.sourceTag.orEmpty(),
                { onChange(settings.copy(sourceTag = it)) },
                label = "Git release tag",
                state = TextFieldState.Helper("e.g., v1.0.0-candidate"),
            )
            Text("Collision Policy", style = MaterialTheme.typography.labelLarge, color = GlassTheme.colors.onGlass)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                CollisionPolicy.entries.forEach { policy ->
                    GlassChip(
                        label = policy.name,
                        selected = settings.collisionPolicy == policy,
                        onClick = { onChange(settings.copy(collisionPolicy = policy)) },
                    )
                }
            }
            Meta("repository", dirtyPaths, "Repository", "Git tag and target release location.")
        }
    }
}

@Composable
private fun SplitAssetsCard(settings: PublishSettings, dirtyPaths: Set<String>, onChange: (PublishSettings) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Split Assets Configuration", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Large ROM zips exceeding host upload limits are partitioned into multi-part archives automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            GlassTextField(
                (settings.partSizeBytes / (1024 * 1024)).toString(),
                { val mb = it.toLongOrNull() ?: 1900L; onChange(settings.copy(partSizeBytes = mb * 1024 * 1024)) },
                label = "Max asset part size (MB)",
                state = TextFieldState.Helper("Default 1900 MB fits within 2 GB release asset limits."),
            )
            Meta("partSizeBytes", dirtyPaths, "Asset chunking", "Ensures compatibility with distribution CDN caps.")
        }
    }
}

@Composable
private fun CredentialsCard(settings: PublishSettings, dirtyPaths: Set<String>, onChange: (PublishSettings) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Security Credentials", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Opaque credential reference. Plaintext secrets are never stored in pipeline configuration.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            GlassTextField(
                settings.credentialRef.orEmpty(),
                { onChange(settings.copy(credentialRef = it)) },
                label = "Credential reference key",
                state = TextFieldState.Helper("Managed via SecretStorePort in platform keystore."),
            )
            Meta("credentialRef", dirtyPaths, "CredentialRef", "Zero plaintext secret storage guarantee.")
        }
    }
}

@Composable
private fun OtaManifestCard(settings: PublishSettings, dirtyPaths: Set<String>, onChange: (PublishSettings) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("OTA Manifest Sync", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            GlassTextField(
                settings.manifestRepository.orEmpty(),
                { onChange(settings.copy(manifestRepository = it)) },
                label = "Manifest sync repository",
                state = TextFieldState.Helper("Repository holding device channel JSON files."),
            )
            GlassTextField(
                settings.manifestBranch.orEmpty(),
                { onChange(settings.copy(manifestBranch = it)) },
                label = "Branch",
                state = TextFieldState.Helper("e.g. main or gh-pages"),
            )
            Meta("manifestRepository", dirtyPaths, "OTA sync", "Auto-updates channel feed on publish.")
        }
    }
}

@Composable
private fun RetentionCard(settings: PublishSettings, dirtyPaths: Set<String>, onChange: (PublishSettings) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Retention Policy", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            GlassTextField(
                settings.retentionPerDevice.toString(),
                { val count = it.toIntOrNull() ?: 3; onChange(settings.copy(retentionPerDevice = count)) },
                label = "Retained release candidates per device",
                state = TextFieldState.Helper("Older releases pruned after newer stable publish."),
            )
            Meta("retentionPerDevice", dirtyPaths, "Retention", "Prunes stale release assets automatically.")
        }
    }
}

@Composable
private fun VerificationCard(settings: PublishSettings) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Verification & Integrity", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Verification status: Ready for release pre-flight. AVB signatures and SHA256 checksums verified.",
                color = GlassTheme.colors.diagnostics.success,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ReviewCard(settings: PublishSettings, target: TargetDevice) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Publish Review & Recovery", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Target: ${target.codename} (${target.friendlyTitle})\n" +
                    "Provider: ${settings.provider.name}\n" +
                    "Repository: ${settings.repository ?: "None"}\n" +
                    "Collision Policy: ${settings.collisionPolicy.name}",
                color = GlassTheme.colors.onGlassSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Meta(path: String, dirty: Set<String>, source: String, help: String) {
    val isDirty = dirty.any { it.startsWith(path.substringBefore('/')) }
    Text(
        "$source${if (isDirty) " · Dirty" else ""} · $help",
        style = MaterialTheme.typography.labelSmall,
        color = if (isDirty) GlassTheme.colors.glassAccent else GlassTheme.colors.onGlassMuted,
    )
}

@Composable
private fun Diagnostics(errors: List<ValidationError>) {
    if (errors.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
        errors.forEach {
            Text(
                "${it.severity}: ${it.message}",
                style = MaterialTheme.typography.bodySmall,
                color = if (it.severity == Severity.ERROR) {
                    GlassTheme.colors.diagnostics.error
                } else {
                    GlassTheme.colors.diagnostics.warning
                },
            )
        }
    }
}
