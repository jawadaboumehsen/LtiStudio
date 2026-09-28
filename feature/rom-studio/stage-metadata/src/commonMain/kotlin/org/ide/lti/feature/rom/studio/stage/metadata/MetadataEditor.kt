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
                    ManifestCard(settings, target)
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
            text = "Pinned target provides device identity; select distribution channel:",
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GlassTextField(
            target.codename,
            {},
            label = "Manifest device",
            readOnly = true,
            state = TextFieldState.Helper("Derived from pinned target device."),
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
        Meta("channel", dirtyPaths, "Release channel", "Distribution channel ring.")
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
        Meta("changelog", dirtyPaths, "Changelog", "Rendered in system updater UI on client devices.")
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Manifest Preview",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
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
