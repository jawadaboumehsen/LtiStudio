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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.TextFieldConfig
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
import org.ide.lti.core.model.workspace.ReleaseSettings

@Suppress("LongParameterList", "UnusedParameter")
@Composable
public fun MetadataEditor(
    settings: ReleaseSettings,
    target: TargetDevice,
    validationErrors: List<ValidationError> = emptyList(),
    onChange: (ReleaseSettings) -> Unit,
    dirtyPaths: Set<String> = emptySet(),
    selectedObjectId: String = "release-identity",
    /** The manifest the last build wrote, or null. */
    manifest: String? = null,
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
                .widthIn(max = GlassDimens.OverviewMaxContentWidth)
                .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            when (selectedObjectId.lowercase()) {
                "ota-url", "ota_url" -> item {
                    OtaCard(settings, errors, dirtyPaths, onChange)
                }
                "changelog" -> item {
                    ChangelogCard(settings, errors, dirtyPaths, onChange)
                }
                "compatibility" -> item {
                    CompatibilityCard(target, dirtyPaths)
                }
                "manifest-preview", "manifest_preview" -> item {
                    ManifestCard(manifest)
                }
                else -> item {
                    IdentityCard(settings, target, dirtyPaths, onChange)
                }
            }
            item { Diagnostics(errors) }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Release Identity",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "The device the update is for, and the channel it is published on.",
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GlassTextField(
            target.id,
            {},
            label = "Device",
            readOnly = true,
            state = TextFieldState.Helper("From this workspace's device configuration."),
        )
        Text(
            text = "Channel",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            listOf("internal", "beta", "stable").forEach { channel ->
                GlassChip(
                    label = channel.replaceFirstChar { it.uppercase() },
                    selected = settings.channel == channel,
                    onClick = { onChange(settings.copy(channel = channel)) },
                )
            }
        }
        Meta("channel", dirtyPaths, "Release channel", "Devices follow one channel; the manifest records it.")
    }
}

@Composable
private fun OtaCard(
    settings: ReleaseSettings,
    errors: List<ValidationError>,
    dirtyPaths: Set<String>,
    onChange: (ReleaseSettings) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "OTA Distribution URL",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Base URL must use HTTPS and must not end with a slash.",
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // What is typed stays in the field while it is not a valid URL yet; only a valid one is saved.
        var text by remember(settings.otaBaseUrl) { mutableStateOf(settings.otaBaseUrl) }
        val invalid = runCatching { settings.copy(otaBaseUrl = text) }.exceptionOrNull()
        GlassTextField(
            text,
            { value ->
                text = value
                runCatching { settings.copy(otaBaseUrl = value) }.onSuccess(onChange)
            },
            label = "HTTPS distribution base URL",
            state = invalid?.let {
                TextFieldState.Error("Not saved: it must start with https:// and not end with /.")
            } ?: fieldState(errors, "otaBaseUrl"),
        )
        Meta("otaBaseUrl", dirtyPaths, "OTA download", "Where devices download the package from: <url>/<zip name>.")
    }
}

@Composable
private fun ChangelogCard(
    settings: ReleaseSettings,
    errors: List<ValidationError>,
    dirtyPaths: Set<String>,
    onChange: (ReleaseSettings) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Changelog",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Summarize key updates and release highlights:",
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GlassTextField(
            settings.changelog,
            { onChange(settings.copy(changelog = it)) },
            label = "Release notes",
            config = TextFieldConfig(singleLine = false, maxLines = 10),
            state = fieldState(errors, "changelog"),
        )
        Meta("changelog", dirtyPaths, "Changelog", "Shown by the updater on the device. Empty: a generated summary.")
    }
}

@Composable
private fun CompatibilityCard(target: TargetDevice, dirtyPaths: Set<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Compatibility Constraints",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        GlassTextField(
            target.friendlyTitle,
            {},
            label = "Supported device",
            readOnly = true,
            state = TextFieldState.Helper("From this workspace's device configuration."),
        )
        GlassTextField(
            "Android ${target.availableFirmwares.values.flatten().firstOrNull()?.androidVersion ?: "?"} · " +
                "slots ${target.packagePolicy.bootSlots.joinToString()}",
            {},
            label = "Firmware",
            readOnly = true,
            state = TextFieldState.Helper("The update installs only on this device (the package checks its id)."),
        )
        Meta("target", dirtyPaths, "This workspace", "Edit the device in ROM configuration.")
    }
}

/** The manifest the last build wrote, as devices will read it; before a build, what to run first. */
@Composable
private fun ManifestCard(manifest: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Manifest preview",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (manifest == null) {
            Text(
                text = "No manifest yet. Run the build through Release metadata to generate out/manifest.json.",
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "out/manifest.json from the last build. It changes when you build again.",
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = manifest.trim(),
                fontSize = FontSize.Micro,
                fontFamily = codeFontFamily(),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("ManifestJson"),
            )
        }
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
