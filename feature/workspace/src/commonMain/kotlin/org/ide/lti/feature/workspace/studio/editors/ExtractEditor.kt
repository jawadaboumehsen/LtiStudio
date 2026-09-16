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
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
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
import org.ide.lti.core.model.workspace.ExtractionSettings
import org.ide.lti.feature.workspace.studio.StageObjectNavigator
import org.ide.lti.feature.workspace.studio.StudioDestinationCatalog

@Composable
public fun ExtractEditor(
    settings: ExtractionSettings,
    target: TargetDevice,
    validationErrors: List<ValidationError>,
    onChange: (ExtractionSettings) -> Unit,
    dirtyPaths: Set<String> = emptySet(),
    selectedObjectId: String = "archive-layout",
    onSelectedObjectChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val stage = StudioDestinationCatalog.stage(StageId.FIRMWARE_EXTRACTION)!!
    val errors = validationErrors.filter { it.fieldPath.startsWith("extraction.") || !it.fieldPath.contains('.') }
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
            item { EditorHeader() }
            when (selectedObjectId) {
                "dynamic-partitions" -> item { DynamicPartitionsCard(settings, target, errors, dirtyPaths, onChange) }
                "boot-partitions" -> item { BootPartitionsCard(settings, target, errors, dirtyPaths, onChange) }
                "slot", "filesystem-handling" -> item { SlotFilesystemCard(settings, target, dirtyPaths, onChange) }
                else -> item { ArchiveLayoutCard(settings, errors, dirtyPaths, onChange) }
            }
            item { Diagnostics(errors) }
        }
    }
}

@Composable
private fun EditorHeader() {
    Column {
        Text("Extract", style = MaterialTheme.typography.headlineSmall, color = GlassTheme.colors.onGlass)
        Text(
            "Inspect the archive layout and choose target-supported extraction inputs.",
            color = GlassTheme.colors.onGlassSecondary,
        )
    }
}

@Composable
private fun ArchiveLayoutCard(
    settings: ExtractionSettings,
    errors: List<ValidationError>,
    dirtyPaths: Set<String>,
    onChange: (ExtractionSettings) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Archive layout", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Source archive inspection is artifact-derived and read-only here.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            Text("Adapter", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlass)
            listOf("AUTO", "payload-dumper-go", "lpunpack").forEach { adapter ->
                GlassChip(
                    label = adapter,
                    selected = settings.adapter.equals(adapter, ignoreCase = true),
                    onClick = { onChange(settings.copy(adapter = adapter)) },
                )
            }
            Meta(
                "adapter",
                dirtyPaths,
                "Target capability",
                "Auto selects a supported archive adapter; explicit choices stay typed.",
            )
            Diagnostics(errors.filter { it.fieldPath.endsWith("adapter") })
        }
    }
}

@Composable
private fun DynamicPartitionsCard(
    settings: ExtractionSettings,
    target: TargetDevice,
    errors: List<ValidationError>,
    dirtyPaths: Set<String>,
    onChange: (ExtractionSettings) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Dynamic partitions", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Select only partitions declared by the pinned target.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            PartitionChoices(target.dynamicPartitions, settings.dynamicPartitions) {
                onChange(settings.copy(dynamicPartitions = it))
            }
            Meta(
                "dynamicPartitions",
                dirtyPaths,
                "Target capability",
                "Partition choices are constrained to target.dynamicPartitions.",
            )
            Diagnostics(errors.filter { it.fieldPath.endsWith("dynamicPartitions") })
        }
    }
}

@Composable
private fun BootPartitionsCard(
    settings: ExtractionSettings,
    target: TargetDevice,
    errors: List<ValidationError>,
    dirtyPaths: Set<String>,
    onChange: (ExtractionSettings) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Boot partitions", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            PartitionChoices(target.bootPartitions, settings.bootPartitions) {
                onChange(settings.copy(bootPartitions = it))
            }
            Meta(
                "bootPartitions",
                dirtyPaths,
                "Target capability",
                "Boot image selection is pinned to target.bootPartitions.",
            )
            Diagnostics(errors.filter { it.fieldPath.endsWith("bootPartitions") })
        }
    }
}

@Composable
private fun SlotFilesystemCard(
    settings: ExtractionSettings,
    target: TargetDevice,
    dirtyPaths: Set<String>,
    onChange: (ExtractionSettings) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text(
                "Slot and filesystem handling",
                style = MaterialTheme.typography.titleSmall,
                color = GlassTheme.colors.onGlass,
            )
            Text(
                "Supported slots: ${target.packagePolicy.bootSlots.joinToString()} · " +
                    "active ${target.activeSlotSuffix}",
                color = GlassTheme.colors.onGlassSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                listOf(null, *target.packagePolicy.bootSlots.toTypedArray()).forEach { slot ->
                    GlassChip(
                        label = slot ?: "Auto",
                        selected = settings.slot == slot,
                        onClick = { onChange(settings.copy(slot = slot)) },
                    )
                }
            }
            GlassTextField(
                settings.slot.orEmpty(),
                {},
                label = "Derived extraction slot",
                readOnly = true,
                state = TextFieldState.Helper("Typed target capability; output paths are workspace-derived."),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                GlassToggle(settings.reuseVerifiedExtraction, { onChange(settings.copy(reuseVerifiedExtraction = it)) })
                Text(
                    "Reuse existing extraction only after verified digest",
                    modifier = Modifier.padding(top = Spacing.Small),
                    color = GlassTheme.colors.onGlass,
                )
            }
            Meta(
                "slot / reuseVerifiedExtraction",
                dirtyPaths,
                "Derived / workspace override",
                "Reuse never bypasses artifact digest and sidecar checks.",
            )
        }
    }
}

@Composable
private fun PartitionChoices(options: List<String>, selected: List<String>, onChange: (List<String>) -> Unit) {
    options.forEach { name ->
        GlassChip(
            label = name,
            selected = name in selected,
            onClick = { onChange(if (name in selected) selected - name else selected + name) },
            modifier = Modifier.fillMaxWidth(),
        )
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
