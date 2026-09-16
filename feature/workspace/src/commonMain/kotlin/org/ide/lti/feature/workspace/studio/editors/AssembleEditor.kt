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
import org.ide.lti.core.model.workspace.AssemblySettings
import org.ide.lti.feature.workspace.studio.StageObjectNavigator
import org.ide.lti.feature.workspace.studio.StudioDestinationCatalog

@Composable
public fun AssembleEditor(
    settings: AssemblySettings,
    target: TargetDevice,
    validationErrors: List<ValidationError>,
    onChange: (AssemblySettings) -> Unit,
    dirtyPaths: Set<String> = emptySet(),
    selectedObjectId: String = "work-trees",
    onSelectedObjectChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val stage = StudioDestinationCatalog.stage(StageId.WORK_TREE_ASSEMBLY)!!
    val errors = validationErrors.filter { it.fieldPath.startsWith("assembly.") || !it.fieldPath.contains('.') }
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
                "system-ext-handling" -> item { SystemExtCard(settings, target, errors, dirtyPaths, onChange) }
                "boot-preparation", "aot-cleanup" -> item {
                    BootPreparationCard(settings, errors, dirtyPaths, onChange)
                }
                "build-properties" -> item { BuildPropertiesCard(settings, errors, dirtyPaths, onChange) }
                else -> item { WorkTreesCard(settings, target, dirtyPaths, onChange) }
            }
            item { Diagnostics(errors) }
        }
    }
}

@Composable
private fun EditorHeader() {
    Column {
        Text("Assemble", style = MaterialTheme.typography.headlineSmall, color = GlassTheme.colors.onGlass)
        Text(
            "Shape the staged work tree with target-gated partition and build settings.",
            color = GlassTheme.colors.onGlassSecondary,
        )
    }
}

@Composable
private fun WorkTreesCard(
    settings: AssemblySettings,
    target: TargetDevice,
    dirtyPaths: Set<String>,
    onChange: (AssemblySettings) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Work trees", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Included partitions",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            target.dynamicPartitions.forEach { name ->
                GlassChip(
                    label = name,
                    selected = name in settings.includedPartitions,
                    onClick = {
                        onChange(
                            settings.copy(
                                includedPartitions = if (name in settings.includedPartitions) {
                                    settings.includedPartitions - name
                                } else {
                                    settings.includedPartitions + name
                                },
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Meta(
                "includedPartitions",
                dirtyPaths,
                "Target capability",
                "Only target dynamic partitions can be included in the assembled work tree.",
            )
        }
    }
}

@Composable
private fun SystemExtCard(
    settings: AssemblySettings,
    target: TargetDevice,
    errors: List<ValidationError>,
    dirtyPaths: Set<String>,
    onChange: (AssemblySettings) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("system_ext handling", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                if (target.hasStandaloneSystemExt) {
                    "Standalone system_ext is supported by this target."
                } else {
                    "This target does not advertise a standalone system_ext partition."
                },
                color = GlassTheme.colors.onGlassSecondary,
            )
            listOf("auto", "standalone", "fold").forEach { mode ->
                GlassChip(
                    label = mode,
                    selected = settings.systemExtMode.equals(mode, ignoreCase = true),
                    onClick = { onChange(settings.copy(systemExtMode = mode)) },
                )
            }
            Meta(
                "systemExtMode",
                dirtyPaths,
                "Target capability",
                "Modes are limited to the validator-supported typed values.",
            )
            Diagnostics(errors.filter { it.fieldPath.endsWith("systemExtMode") })
        }
    }
}

@Composable
private fun BootPreparationCard(
    settings: AssemblySettings,
    errors: List<ValidationError>,
    dirtyPaths: Set<String>,
    onChange: (AssemblySettings) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text(
                "Boot preparation and AOT cleanup",
                style = MaterialTheme.typography.titleSmall,
                color = GlassTheme.colors.onGlass,
            )
            Text(
                "Boot footer handling",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            listOf("erase", "preserve").forEach { policy ->
                GlassChip(
                    label = policy,
                    selected = settings.bootFooterPolicy.equals(policy, ignoreCase = true),
                    onClick = { onChange(settings.copy(bootFooterPolicy = policy)) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                GlassToggle(settings.baselineCleanup, { onChange(settings.copy(baselineCleanup = it)) })
                Text(
                    "Remove baseline AOT artifacts",
                    modifier = Modifier.padding(top = Spacing.Small),
                    color = GlassTheme.colors.onGlass,
                )
            }
            Meta(
                "bootFooterPolicy / baselineCleanup",
                dirtyPaths,
                "Workspace override",
                "The model exposes one baseline cleanup toggle; no separate AOT switches are invented.",
            )
            Diagnostics(errors.filter { it.fieldPath.endsWith("bootFooterPolicy") })
        }
    }
}

@Composable
private fun BuildPropertiesCard(
    settings: AssemblySettings,
    errors: List<ValidationError>,
    dirtyPaths: Set<String>,
    onChange: (AssemblySettings) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Build properties", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text("Build type", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlassSecondary)
            listOf("user", "userdebug", "eng").forEach { type ->
                GlassChip(
                    label = type,
                    selected = settings.buildType == type,
                    onClick = { onChange(settings.copy(buildType = type)) },
                )
            }
            GlassTextField(
                settings.romVersion,
                { value -> onChange(settings.copy(romVersion = value)) },
                label = "ROM version",
                state = fieldState(errors, "romVersion"),
            )
            settings.propertyOverrides.forEach { (key, value) ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                    GlassTextField(
                        key,
                        {},
                        modifier = Modifier.weight(1f),
                        label = "Property key",
                        readOnly = true,
                        state = fieldState(errors, "propertyOverrides['$key']"),
                    )
                    GlassTextField(
                        value,
                        { next ->
                            val sanitized = next.filter { it != '\n' && it != '\u0000' }
                            onChange(settings.copy(propertyOverrides = settings.propertyOverrides + (key to sanitized)))
                        },
                        modifier = Modifier.weight(1f),
                        label = "Value",
                    )
                }
            }
            GlassButton(
                onClick = {
                    onChange(settings.copy(propertyOverrides = settings.propertyOverrides + ("ro.ltirom.custom" to "")))
                },
            ) { Text("Add property") }
            Meta(
                "buildType / romVersion / propertyOverrides",
                dirtyPaths,
                "Workspace override",
                "Property entries reject newline/NUL characters; reserved-key diagnostics come from the validator.",
            )
            Diagnostics(errors.filter { it.fieldPath.contains("propertyOverrides") })
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
