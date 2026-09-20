/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.metadata

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
import org.ide.lti.core.designsystem.component.inputs.TextFieldConfig
import org.ide.lti.core.designsystem.component.inputs.TextFieldState
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.designsystem.theme.IdeShellShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.ReleaseSettings

@Composable
public fun MetadataEditor(
    settings: ReleaseSettings,
    target: TargetDevice,
    validationErrors: List<ValidationError> = emptyList(),
    onChange: (ReleaseSettings) -> Unit,
    dirtyPaths: Set<String> = emptySet(),
    selectedObjectId: String = "release-identity",
    onValidate: () -> Unit = {},
    onSave: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val errors = validationErrors.filter { it.fieldPath.startsWith("release.") || !it.fieldPath.contains('.') }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("MetadataEditor"),
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
                "ota-url", "ota_url" -> item { OtaCard(settings, errors, dirtyPaths, onChange) }
                "changelog" -> item { ChangelogCard(settings, errors, dirtyPaths, onChange) }
                "compatibility" -> item { CompatibilityCard(target, dirtyPaths) }
                "manifest-preview", "manifest_preview" -> item { ManifestCard(settings, target) }
                else -> item { IdentityCard(settings, target, dirtyPaths, onChange) }
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
            Text("Release Metadata", style = MaterialTheme.typography.headlineSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Configure OTA update descriptors, manifest properties, changelog, and compatibility constraints.",
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
private fun IdentityCard(
    settings: ReleaseSettings,
    target: TargetDevice,
    dirtyPaths: Set<String>,
    onChange: (ReleaseSettings) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Release Identity", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Pinned target provides device identity; select distribution channel:",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            GlassTextField(
                target.codename,
                {},
                label = "Manifest device",
                readOnly = true,
                state = TextFieldState.Helper("Derived from pinned target device."),
            )
            Text("Channel", style = MaterialTheme.typography.labelLarge, color = GlassTheme.colors.onGlass)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                listOf("internal", "beta", "stable").forEach { channel ->
                    GlassChip(
                        label = channel.replaceFirstChar { it.uppercase() },
                        selected = settings.channel == channel,
                        onClick = { onChange(settings.copy(channel = channel)) },
                    )
                }
            }
            Meta("channel", dirtyPaths, "Release channel", "Distribution channel ring.")
        }
    }
}

@Composable
private fun OtaCard(
    settings: ReleaseSettings,
    errors: List<ValidationError>,
    dirtyPaths: Set<String>,
    onChange: (ReleaseSettings) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("OTA Distribution URL", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Base URL must use HTTPS and must not end with a slash.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            GlassTextField(
                settings.otaBaseUrl,
                { value ->
                    runCatching { settings.copy(otaBaseUrl = value) }.onSuccess(onChange)
                },
                label = "HTTPS distribution base URL",
                state = fieldState(errors, "otaBaseUrl"),
            )
            Meta("otaBaseUrl", dirtyPaths, "OTA endpoint", "Server endpoint hosting delta/full payloads.")
        }
    }
}

@Composable
private fun ChangelogCard(
    settings: ReleaseSettings,
    errors: List<ValidationError>,
    dirtyPaths: Set<String>,
    onChange: (ReleaseSettings) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Changelog", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Summarize key updates and release highlights:",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            GlassTextField(
                settings.changelog,
                { onChange(settings.copy(changelog = it)) },
                label = "Release notes",
                config = TextFieldConfig(singleLine = false, maxLines = 10),
                state = fieldState(errors, "changelog"),
            )
            Meta("changelog", dirtyPaths, "Changelog", "Rendered in system updater UI on client devices.")
        }
    }
}

@Composable
private fun CompatibilityCard(target: TargetDevice, dirtyPaths: Set<String>) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Compatibility Constraints", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            GlassTextField(
                target.friendlyTitle,
                {},
                label = "Supported device",
                readOnly = true,
                state = TextFieldState.Helper("Pinned target hardware."),
            )
            GlassTextField(
                "Target revision ${target.revision} · ${target.status}",
                {},
                label = "Target revision",
                readOnly = true,
                state = TextFieldState.Helper("Rebind through target catalog."),
            )
            Meta("target", dirtyPaths, "Pinned target", "Governed by hardware security policy.")
        }
    }
}

@Composable
private fun ManifestCard(settings: ReleaseSettings, target: TargetDevice) {
    val manifest = buildString {
        appendLine("{")
        appendLine("  \"device\": \"${target.codename}\",")
        appendLine("  \"channel\": \"${settings.channel}\",")
        appendLine("  \"ota_base_url\": \"${settings.otaBaseUrl}\",")
        appendLine("  \"target_revision\": ${target.revision},")
        appendLine("  \"status\": \"candidate\"")
        append("}")
    }
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Manifest Preview", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            GlassTextField(
                manifest,
                {},
                label = "Generated manifest JSON",
                readOnly = true,
                config = TextFieldConfig(singleLine = false, maxLines = 10),
                state = TextFieldState.Helper("Final build hashes and timestamps are injected at build completion."),
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
private fun fieldState(errors: List<ValidationError>, field: String): TextFieldState {
    val error = errors.firstOrNull { it.fieldPath.endsWith(field) }
    return when (error?.severity) {
        Severity.ERROR -> TextFieldState.Error(error.message)
        Severity.WARNING -> TextFieldState.Warning(error.message)
        else -> TextFieldState.Default
    }
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
