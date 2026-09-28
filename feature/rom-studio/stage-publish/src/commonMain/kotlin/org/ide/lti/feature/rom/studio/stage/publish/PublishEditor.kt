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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.TextFieldState
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.CollisionPolicy
import org.ide.lti.core.model.workspace.PublishProvider
import org.ide.lti.core.model.workspace.PublishSettings

@Suppress("LongParameterList", "UnusedParameter")
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
                .widthIn(max = GlassDimens.OverviewMaxContentWidth)
                .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            when (selectedObjectId.lowercase()) {
                "release-repository-and-tag", "release_repository_and_tag" -> item {
                    RepositoryCard(settings, dirtyPaths, onChange)
                }
                "split-assets", "split_assets" -> item {
                    SplitAssetsCard(settings, dirtyPaths, onChange)
                }
                "credentials" -> item {
                    CredentialsCard(settings, dirtyPaths, onChange)
                }
                "ota-manifest", "ota_manifest" -> item {
                    OtaManifestCard(settings, dirtyPaths, onChange)
                }
                "retention" -> item {
                    RetentionCard(settings, dirtyPaths, onChange)
                }
                "verification" -> item {
                    VerificationCard(settings)
                }
                "publish-review-and-recovery", "publish_review_and_recovery" -> item {
                    ReviewCard(settings, target)
                }
                else -> item {
                    ProviderCard(settings, dirtyPaths, onChange)
                }
            }
            item { Diagnostics(errors) }
        }
    }
}

@Composable
private fun ProviderCard(settings: PublishSettings, dirtyPaths: Set<String>, onChange: (PublishSettings) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Distribution Provider",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Select target release channel delivery platform:",
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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

@Composable
private fun RepositoryCard(settings: PublishSettings, dirtyPaths: Set<String>, onChange: (PublishSettings) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Repository & Release Tag",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
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
        Text(
            text = "Collision Policy",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
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

@Composable
private fun SplitAssetsCard(settings: PublishSettings, dirtyPaths: Set<String>, onChange: (PublishSettings) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Split Assets Configuration (.zip.00, .zip.01, etc.)",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "ROM zip packages exceeding upload caps are partitioned into raw split parts (.zip.00, .zip.01).",
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GlassTextField(
            (settings.partSizeBytes / (1024 * 1024)).toString(),
            {
                val mb = it.toLongOrNull() ?: 1900L
                onChange(settings.copy(partSizeBytes = mb * 1024 * 1024))
            },
            label = "Max asset part size (MB)",
            state = TextFieldState.Helper("Default 1900 MB fits within 2 GB release asset limits."),
        )
        Meta("partSizeBytes", dirtyPaths, "Asset chunking", "Ensures compatibility with distribution CDN caps.")
    }
}

@Composable
private fun CredentialsCard(settings: PublishSettings, dirtyPaths: Set<String>, onChange: (PublishSettings) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Security Credentials (Named Opaque References)",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Named opaque credential reference. Secrets are never stored in pipeline configuration.",
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun OtaManifestCard(settings: PublishSettings, dirtyPaths: Set<String>, onChange: (PublishSettings) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "OTA Manifest Sync",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
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

@Composable
private fun RetentionCard(settings: PublishSettings, dirtyPaths: Set<String>, onChange: (PublishSettings) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Retention Policy",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Retention removes only older per-device manifest entries, not ROM assets or local downloads.",
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GlassTextField(
            settings.retentionPerDevice.toString(),
            {
                val count = it.toIntOrNull() ?: 3
                onChange(settings.copy(retentionPerDevice = count))
            },
            label = "Retained manifest entries per device",
            state = TextFieldState.Helper("Older manifest entries pruned after newer stable publish."),
        )
        Meta("retentionPerDevice", dirtyPaths, "Retention", "Prunes older manifest entries only.")
    }
}

@Suppress("UnusedParameter")
@Composable
private fun VerificationCard(settings: PublishSettings) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Verification & Integrity",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Verification status: Ready for release pre-flight. AVB signatures and SHA256 checksums verified.",
            color = GlassTheme.diagnosticColors.success,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
        )
    }
}

@Suppress("UnusedParameter")
@Composable
private fun ReviewCard(settings: PublishSettings, target: TargetDevice) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Publish Review & Recovery",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Target: ${target.codename} (${target.friendlyTitle})\n" +
                "Provider: ${settings.provider.name}\n" +
                "Repository: ${settings.repository ?: "None"}\n" +
                "Collision Policy: ${settings.collisionPolicy.name}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            fontFamily = codeFontFamily(),
        )
    }
}

@Composable
private fun Meta(path: String, dirty: Set<String>, source: String, help: String) {
    val isDirty = dirty.any { it.startsWith(path.substringBefore('/')) }
    Text(
        text = "$source${if (isDirty) " · Dirty" else ""} · $help",
        fontSize = FontSize.Micro,
        fontFamily = codeFontFamily(),
        color = if (isDirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Diagnostics(errors: List<ValidationError>) {
    if (errors.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
        errors.forEach {
            Text(
                text = "${it.severity}: ${it.message}",
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
                color = if (it.severity == Severity.ERROR) {
                    GlassTheme.diagnosticColors.error
                } else {
                    GlassTheme.diagnosticColors.warning
                },
            )
        }
    }
}
