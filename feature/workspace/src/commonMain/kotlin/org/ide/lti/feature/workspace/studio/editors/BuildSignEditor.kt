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
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.core.model.workspace.SigningPolicy
import org.ide.lti.feature.workspace.studio.StageObjectNavigator
import org.ide.lti.feature.workspace.studio.StudioDestinationCatalog

@Composable
public fun BuildSignEditor(
    settings: BuildSettings,
    target: TargetDevice,
    validationErrors: List<ValidationError>,
    onChange: (BuildSettings) -> Unit,
    dirtyPaths: Set<String> = emptySet(),
    selectedObjectId: String = "compression",
    onSelectedObjectChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val stage = StudioDestinationCatalog.stage(StageId.BUILD_FLASHABLE_ZIP)!!
    val errors = validationErrors.filter { it.fieldPath.startsWith("build.") || !it.fieldPath.contains('.') }
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
            item { EditorHeader("Build & sign", "Configure a compatible flashable package and its signing policy.") }
            item {
                when (selectedObjectId) {
                    "images" -> ImagesCard(settings, target, dirtyPaths, onChange)
                    "partition-layout" -> PartitionLayoutCard(settings, target, dirtyPaths)
                    "flashable-members" -> FlashableMembersCard(settings, target, dirtyPaths, onChange)
                    "avb-keys" -> AvbKeysCard(settings, errors, dirtyPaths, onChange)
                    "package-signing" -> PackageSigningCard(settings, dirtyPaths, onChange)
                    "output-naming" -> OutputNamingCard(settings, errors, dirtyPaths, onChange)
                    else -> CompressionCard(settings, errors, dirtyPaths, onChange)
                }
            }
            item { Diagnostics(errors) }
        }
    }
}

@Composable
private fun ImagesCard(
    settings: BuildSettings,
    target: TargetDevice,
    dirtyPaths: Set<String>,
    onChange: (BuildSettings) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Images", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "The pinned target supplies the image set; geometry and measured sizes remain read-only.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            GlassTextField(
                settings.filesystem,
                {},
                label = "Filesystem",
                readOnly = true,
                state = TextFieldState.Helper("Target capability; only EROFS is supported."),
            )
            GlassTextField(
                target.dynamicPartitions.joinToString(),
                {},
                label = "Dynamic images",
                readOnly = true,
                state = TextFieldState.Helper("Derived from the pinned target partition map."),
            )
            GlassTextField(
                "${target.formattedBootSize} boot · ${target.formattedVendorBootSize} vendor_boot · " +
                    "${target.formattedInitBootSize} init_boot",
                {},
                label = "Boot image geometry",
                readOnly = true,
                state = TextFieldState.Helper("Measured target geometry; revise the target profile to change it."),
            )
            FieldMeta("filesystem", dirtyPaths, "Target default", "Filesystem is gated by target/tool capability.")
            ResetButton { onChange(settings.copy(filesystem = BuildSettings().filesystem)) }
        }
    }
}

@Composable
private fun CompressionCard(
    settings: BuildSettings,
    errors: List<ValidationError>,
    dirtyPaths: Set<String>,
    onChange: (BuildSettings) -> Unit,
) {
    val parts = settings.compression.split(',')
    val algorithm = parts.firstOrNull().orEmpty().ifBlank { "lz4hc" }
    val compressionLevel = parts.getOrNull(1)?.toFloatOrNull() ?: 9f
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("EROFS compression", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Supported compression is selected as a typed capability choice for the pinned target.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                GlassChip(
                    label = "lz4hc",
                    selected = algorithm == "lz4hc",
                    onClick = { onChange(settings.copy(compression = "lz4hc,${compressionLevel.toInt()}")) },
                )
            }
            Text(
                "Compression level: ${compressionLevel.toInt()} · 1-12",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            GlassSlider(
                value = { compressionLevel },
                onValueChange = { onChange(settings.copy(compression = "$algorithm,${it.toInt().coerceIn(1, 12)}")) },
                valueRange = 1f..12f,
                visibilityThreshold = 1f,
            )
            GlassTextField(
                settings.level?.toString().orEmpty().ifBlank { "Derived by build tool" },
                {},
                label = "Tool level",
                readOnly = true,
                state = fieldState(errors, "level"),
            )
            FieldMeta("compression", dirtyPaths, "Workspace default", "Higher levels trade build time for size.")
            ResetButton {
                onChange(settings.copy(compression = BuildSettings().compression, level = BuildSettings().level))
            }
        }
    }
}

@Composable
private fun PartitionLayoutCard(settings: BuildSettings, target: TargetDevice, dirtyPaths: Set<String>) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Partition layout", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Layout values are target-derived and cannot be typed over in this stage.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            GlassTextField(
                target.formattedSuperGroupSize,
                {},
                label = "Super group",
                readOnly = true,
                state = TextFieldState.Helper("Derived from target geometry."),
            )
            GlassTextField(
                "${target.formattedSuperSize} · ${target.superMetadataSlots} metadata slots",
                {},
                label = "Super partition",
                readOnly = true,
                state = TextFieldState.Helper("Target geometry and metadata policy are read-only here."),
            )
            GlassTextField(
                settings.blockSize.toString(),
                {},
                label = "Block size (bytes)",
                readOnly = true,
                state = TextFieldState.Default,
            )
            GlassTextField(
                settings.alignment?.toString() ?: "Target default",
                {},
                label = "Alignment (bytes)",
                readOnly = true,
                state = TextFieldState.Helper("Derived from target/tool capability."),
            )
            FieldMeta(
                "blockSize / alignment",
                dirtyPaths,
                "Target capability",
                "Change geometry via target revision/rebind.",
            )
        }
    }
}

@Composable
private fun FlashableMembersCard(
    settings: BuildSettings,
    target: TargetDevice,
    dirtyPaths: Set<String>,
    onChange: (BuildSettings) -> Unit,
) {
    val policy = settings.packagePolicy
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Flashable members", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Select only members permitted by the pinned target package policy.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            Text("Dynamic partitions", style = MaterialTheme.typography.labelLarge, color = GlassTheme.colors.onGlass)
            target.dynamicPartitions.forEach { partition ->
                GlassChip(
                    label = partition,
                    selected = partition in policy.flashablePartitions,
                    onClick = {
                        onChange(
                            settings.copy(
                                packagePolicy = policy.copy(
                                    flashablePartitions = policy.flashablePartitions.toggled(partition),
                                ),
                            ),
                        )
                    },
                )
            }
            Text("Boot partitions", style = MaterialTheme.typography.labelLarge, color = GlassTheme.colors.onGlass)
            target.bootPartitions.forEach { partition ->
                GlassChip(
                    label = partition,
                    selected = partition in policy.flashableBootPartitions,
                    onClick = {
                        onChange(
                            settings.copy(
                                packagePolicy = policy.copy(
                                    flashableBootPartitions = policy.flashableBootPartitions.toggled(partition),
                                ),
                            ),
                        )
                    },
                )
            }
            FieldMeta(
                "packagePolicy",
                dirtyPaths,
                "Target policy",
                "Unsigned test artifacts are non-publishable by default.",
            )
            ResetButton { onChange(settings.copy(packagePolicy = BuildSettings().packagePolicy)) }
        }
    }
}

@Composable
private fun AvbKeysCard(
    settings: BuildSettings,
    errors: List<ValidationError>,
    dirtyPaths: Set<String>,
    onChange: (BuildSettings) -> Unit,
) {
    val signing = settings.signing
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("AVB keys", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Use opaque workspace references; inline key material is rejected by validation.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            GlassTextField(
                signing.avbKeyRef,
                { onChange(settings.copy(signing = signing.copy(avbKeyRef = it))) },
                label = "AVB key reference",
                state = fieldState(errors, "avbKeyRef"),
            )
            GlassTextField(
                signing.platformKeyRef,
                { onChange(settings.copy(signing = signing.copy(platformKeyRef = it))) },
                label = "Platform key reference",
                state = fieldState(errors, "platformKeyRef"),
            )
            FieldMeta(
                "signing.avbKeyRef / signing.platformKeyRef",
                dirtyPaths,
                "Workspace default",
                "References identify managed keys without exposing secrets.",
            )
            ResetButton { onChange(settings.copy(signing = SigningPolicy())) }
        }
    }
}

@Composable
private fun PackageSigningCard(settings: BuildSettings, dirtyPaths: Set<String>, onChange: (BuildSettings) -> Unit) {
    val signing = settings.signing
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Package signing", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Signed content is required for publishable artifacts; disabled signing is visibly non-publishable.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
            ToggleRow("Sign images", signing.signImages) {
                onChange(settings.copy(signing = signing.copy(signImages = it)))
            }
            ToggleRow("Sign package", signing.signPackage) {
                onChange(settings.copy(signing = signing.copy(signPackage = it)))
            }
            FieldMeta("signing", dirtyPaths, "Workspace default", "AVB and package signatures protect the artifact.")
            ResetButton { onChange(settings.copy(signing = SigningPolicy())) }
        }
    }
}

@Composable
private fun OutputNamingCard(
    settings: BuildSettings,
    errors: List<ValidationError>,
    dirtyPaths: Set<String>,
    onChange: (BuildSettings) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Output naming", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            GlassTextField(
                settings.filenameTemplate,
                { onChange(settings.copy(filenameTemplate = it)) },
                label = "Release filename template",
                state = fieldState(errors, "filenameTemplate"),
            )
            Text(
                "Supported tokens: {version}, {date}, {device}. The final name is derived at build time.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassMuted,
            )
            FieldMeta(
                "filenameTemplate",
                dirtyPaths,
                "Workspace default",
                "Naming is editable; artifact size, hash, and timestamp are derived.",
            )
            ResetButton { onChange(settings.copy(filenameTemplate = BuildSettings().filenameTemplate)) }
        }
    }
}

private fun List<String>.toggled(value: String): List<String> =
    if (value in this) filterNot { it == value } else this + value

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        GlassToggle(checked, onCheckedChange)
        Text(label, modifier = Modifier.padding(top = Spacing.Small), color = GlassTheme.colors.onGlass)
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
    val isDirty = dirty.any { it.startsWith("build.$path") || it.startsWith(path) }
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
                Text("No validation messages for Build & Sign.", color = GlassTheme.colors.diagnostics.success)
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
