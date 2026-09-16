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
import org.ide.lti.core.designsystem.component.inputs.GlassSlider
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.component.inputs.TextFieldState
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.feature.workspace.studio.StageObjectNavigator
import org.ide.lti.feature.workspace.studio.StudioDestinationCatalog

@Composable
public fun AcquireEditor(
    settings: AcquisitionSettings,
    target: TargetDevice,
    validationErrors: List<ValidationError>,
    onChange: (AcquisitionSettings) -> Unit,
    dirtyPaths: Set<String> = emptySet(),
    selectedObjectId: String = "download-policy",
    onSelectedObjectChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val stage = StudioDestinationCatalog.stage(StageId.FIRMWARE_ACQUISITION)!!
    val errors = validationErrors.filter { it.fieldPath.startsWith("acquisition.") || !it.fieldPath.contains('.') }
    Row(modifier = modifier.fillMaxSize()) {
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
            item { EditorHeader("Acquire", "Select a compatible firmware source and download policy.") }
            when (selectedObjectId) {
                "firmware-baseline" -> item { FirmwareBaselineCard(settings, target, dirtyPaths, onChange) }
                "existing-archives", "integrity" -> item {
                    ArchiveIntegrityCard(settings, errors, dirtyPaths, onChange)
                }
                "download-policy" -> item { DownloadPolicyCard(settings, dirtyPaths, onChange) }
                else -> item { SourceModeCard(settings, target, dirtyPaths, onChange) }
            }
            item { Diagnostics(errors) }
        }
    }
}

@Composable
private fun SourceModeCard(
    settings: AcquisitionSettings,
    target: TargetDevice,
    dirtyPaths: Set<String>,
    onChange: (AcquisitionSettings) -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Source mode", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Pinned target: ${target.friendlyTitle}",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                AcquisitionMode.entries.forEach { mode ->
                    val enabled = mode == AcquisitionMode.DOWNLOAD || !settings.archiveRef.isNullOrBlank()
                    GlassChip(
                        label = mode.name.replace('_', ' '),
                        selected = settings.mode == mode,
                        enabled = enabled,
                        onClick = if (enabled) {
                            { onChange(settings.copy(mode = mode)) }
                        } else {
                            null
                        },
                    )
                }
            }
            FieldMeta(
                "mode",
                dirtyPaths,
                "Workspace override",
                "Typed acquisition mode; archive modes require a selected archive.",
            )
        }
    }
}

@Composable
private fun FirmwareBaselineCard(
    settings: AcquisitionSettings,
    target: TargetDevice,
    dirtyPaths: Set<String>,
    onChange: (AcquisitionSettings) -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Firmware baseline", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Choose from the pinned target's compatible regions and firmware list.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                target.availableRegions.forEach { region ->
                    GlassChip(
                        label = region.displayName,
                        selected = settings.region == region,
                        onClick = {
                            onChange(
                                settings.copy(
                                    region = region,
                                    firmware = target.availableFirmwares[region].orEmpty().firstOrNull()
                                        ?: settings.firmware,
                                ),
                            )
                        },
                    )
                }
            }
            target.availableFirmwares[settings.region].orEmpty().forEach { firmware ->
                GlassChip(
                    label = "${firmware.version} · ${firmware.buildId}",
                    selected = settings.firmware == firmware,
                    onClick = { onChange(settings.copy(firmware = firmware)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            GlassTextField(
                value = settings.firmware.acquisitionUrl ?: settings.firmware.otaUrl.orEmpty(),
                onValueChange = {},
                label = "Source URL (target-provided)",
                readOnly = true,
                state = TextFieldState.Helper("Derived from the selected target firmware; read-only here."),
            )
            FieldMeta(
                "region / firmware",
                dirtyPaths,
                "Target default",
                "Region and firmware remain compatible with the pinned target.",
            )
        }
    }
}

@Composable
private fun ArchiveIntegrityCard(
    settings: AcquisitionSettings,
    errors: List<ValidationError>,
    dirtyPaths: Set<String>,
    onChange: (AcquisitionSettings) -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text(
                "Archive and integrity",
                style = MaterialTheme.typography.titleSmall,
                color = GlassTheme.colors.onGlass,
            )
            GlassTextField(
                value = settings.archiveRef.orEmpty(),
                onValueChange = { value -> if (value.isNotBlank()) onChange(settings.copy(archiveRef = value)) },
                label = "Selected archive reference",
                placeholder = "Select or enter a contained archive reference",
                state = fieldState(errors, "archiveRef"),
            )
            GlassTextField(
                value = settings.expectedSha256.orEmpty(),
                onValueChange = { value -> onChange(settings.copy(expectedSha256 = value.ifBlank { null })) },
                label = "Expected SHA-256",
                placeholder = "Optional digest; blank remains Unverified",
                state = fieldState(errors, "expectedSha256"),
            )
            val verified = !settings.expectedSha256.isNullOrBlank()
            Text(
                if (verified) "Checksum expectation recorded" else "Unverified · no checksum supplied",
                style = MaterialTheme.typography.bodySmall,
                color = if (verified) GlassTheme.colors.diagnostics.success else GlassTheme.colors.diagnostics.warning,
            )
            FieldMeta(
                "archiveRef / expectedSha256",
                dirtyPaths,
                "Workspace override",
                "Archive identity is immutable after selection; checksums are never fabricated.",
            )
        }
    }
}

@Composable
private fun DownloadPolicyCard(
    settings: AcquisitionSettings,
    dirtyPaths: Set<String>,
    onChange: (AcquisitionSettings) -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Download policy", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Retry count: ${settings.retries} · 0–5",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            GlassSlider(
                value = { settings.retries.toFloat() },
                onValueChange = { onChange(settings.copy(retries = it.toInt().coerceIn(0, 5))) },
                valueRange = 0f..5f,
                visibilityThreshold = 1f,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                GlassToggle(settings.resume, { onChange(settings.copy(resume = it)) })
                Text(
                    "Resume interrupted downloads",
                    modifier = Modifier.padding(top = Spacing.Small),
                    color = GlassTheme.colors.onGlass,
                )
            }
            FieldMeta(
                "retries / resume",
                dirtyPaths,
                "Target default",
                "Retry transient failures and resume partial files when supported.",
            )
            ResetButton(onClick = { onChange(settings.copy(retries = 3, resume = true)) })
        }
    }
}

@Composable
private fun EditorHeader(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = GlassTheme.colors.onGlass)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = GlassTheme.colors.onGlassSecondary)
    }
}

@Composable
private fun FieldMeta(path: String, dirty: Set<String>, source: String, help: String) {
    val isDirty = dirty.any { it.startsWith(path.substringBefore('/')) }
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
    GlassButton(onClick = onClick, variant = GlassButtonVariant.Standard) { Text("Reset group") }
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
                Text("No validation messages for Acquire.", color = GlassTheme.colors.diagnostics.success)
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
