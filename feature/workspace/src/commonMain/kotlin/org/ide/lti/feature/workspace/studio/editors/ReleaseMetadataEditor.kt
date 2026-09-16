package org.ide.lti.feature.workspace.studio.editors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.TextFieldConfig
import org.ide.lti.core.designsystem.component.inputs.TextFieldState
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.ReleaseSettings
import org.ide.lti.feature.workspace.studio.StageObjectNavigator
import org.ide.lti.feature.workspace.studio.StudioDestinationCatalog

@Composable
public fun ReleaseMetadataEditor(
    settings: ReleaseSettings,
    target: TargetDevice,
    validationErrors: List<ValidationError>,
    onChange: (ReleaseSettings) -> Unit,
    dirtyPaths: Set<String> = emptySet(),
    selectedObjectId: String = "release-identity",
    onSelectedObjectChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val stage = StudioDestinationCatalog.stage(StageId.GENERATE_OTA_MANIFEST)!!
    val errors = validationErrors.filter { it.fieldPath.startsWith("release.") || !it.fieldPath.contains('.') }
    Row(modifier.fillMaxSize()) {
        StageObjectNavigator(
            currentStage = stage,
            selectedObjectId = selectedObjectId,
            searchQuery = query,
            onSearchQueryChange = { query = it },
            onSelectObject = { _, id -> onSelectedObjectChange(id) },
            onOpenOverview = {},
            validationErrors = errors,
            searchFocusRequester = remember { FocusRequester() },
            modifier = Modifier.width(Spacing.ExtraExtraLarge * 8),
        )
        Spacer(Modifier.width(Spacing.Small))
        LazyColumn(
            modifier = Modifier.weight(1f).padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            item { Header("Release metadata", "Edit release identity and review target-derived distribution details.") }
            item {
                when (selectedObjectId) {
                    "ota-url" -> OtaCard(settings, errors, dirtyPaths, onChange)
                    "changelog" -> ChangelogCard(settings, errors, dirtyPaths, onChange)
                    "compatibility" -> CompatibilityCard(target, dirtyPaths)
                    "manifest-preview" -> ManifestCard(settings, target)
                    else -> IdentityCard(settings, target, dirtyPaths, onChange)
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
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Release identity", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Version is not part of the current ReleaseSettings model; the pinned target supplies device identity.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            GlassTextField(
                target.codename,
                {},
                label = "Manifest device",
                readOnly = true,
                state = TextFieldState.Helper("Derived from the pinned target; cannot be edited here."),
            )
            Text("Channel", style = MaterialTheme.typography.labelLarge, color = GlassTheme.colors.onGlass)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                listOf("internal", "beta", "stable").forEach { channel ->
                    GlassChip(
                        label = channel,
                        selected = settings.channel == channel,
                        onClick = { onChange(settings.copy(channel = channel)) },
                    )
                }
            }
            FieldMeta(
                "channel",
                dirtyPaths,
                "Workspace default",
                "Typed release channels keep publishing intent clear.",
            )
            ResetButton { onChange(ReleaseSettings()) }
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
            Text("OTA distribution URL", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "The base URL must use HTTPS and must not end with a slash.",
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
            FieldMeta(
                "otaBaseUrl",
                dirtyPaths,
                "Workspace default",
                "The URL is used as the trusted distribution base for generated metadata.",
            )
            ResetButton { onChange(ReleaseSettings()) }
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
                "Summarize the changes included in this candidate. Editing it requires rebuilding the package.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            GlassTextField(
                settings.changelog,
                { onChange(settings.copy(changelog = it)) },
                label = "Release notes",
                config = TextFieldConfig(singleLine = false, maxLines = 12),
                state = fieldState(errors, "changelog"),
            )
            FieldMeta(
                "changelog",
                dirtyPaths,
                "Workspace default",
                "Plain text or Markdown is preserved for the manifest.",
            )
            ResetButton { onChange(ReleaseSettings()) }
        }
    }
}

@Composable
private fun CompatibilityCard(target: TargetDevice, dirtyPaths: Set<String>) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Compatibility", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Compatibility constraints are derived from the pinned target in the current model.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            GlassTextField(
                target.friendlyTitle,
                {},
                label = "Supported device",
                readOnly = true,
                state = TextFieldState.Helper("Pinned target device identity."),
            )
            GlassTextField(
                "Android version is target-firmware derived",
                {},
                label = "Android compatibility",
                readOnly = true,
                state = TextFieldState.Helper("ReleaseSettings has no independent compatibility constraint fields."),
            )
            GlassTextField(
                "Target revision ${target.revision} · ${target.status}",
                {},
                label = "Target binding",
                readOnly = true,
                state = TextFieldState.Helper("Rebind through the explicit target revision flow."),
            )
            FieldMeta("target", dirtyPaths, "Pinned target", "Only the target profile changes release compatibility.")
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
            Text("Manifest preview", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Candidate only -- filename, size, hash, and build time are derived after a verified package exists.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.diagnostics.warning,
            )
            GlassTextField(
                manifest,
                {},
                label = "Generated manifest JSON",
                readOnly = true,
                config = TextFieldConfig(singleLine = false, maxLines = 12),
                state = TextFieldState.Helper("Device and compatibility values derive from the pinned target."),
            )
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = GlassTheme.colors.onGlass)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = GlassTheme.colors.onGlassSecondary)
    }
}

@Composable
private fun FieldMeta(path: String, dirty: Set<String>, source: String, help: String) {
    val isDirty = dirty.any { it.startsWith("release.$path") || it.startsWith(path) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            "$source${if (isDirty) " · Dirty" else ""}",
            style = MaterialTheme.typography.labelSmall,
            color = if (isDirty) GlassTheme.colors.glassAccent else GlassTheme.colors.onGlassMuted,
        )
        Text(help, style = MaterialTheme.typography.labelSmall, color = GlassTheme.colors.onGlassMuted)
    }
}

@Composable
private fun ResetButton(onClick: () -> Unit) {
    GlassButton(onClick, variant = GlassButtonVariant.Standard) { Text("Reset to default") }
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
    GlassCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
            Text("Diagnostics", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            if (errors.isEmpty()) {
                Text("No validation messages for Release Metadata.", color = GlassTheme.colors.diagnostics.success)
            } else {
                errors.forEach {
                    Text(
                        "${it.severity}: ${it.message}${it.remediation?.let { r -> " · $r" }.orEmpty()}",
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
    }
}
