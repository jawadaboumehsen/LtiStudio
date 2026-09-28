/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.extract

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.IdeTable
import org.ide.lti.core.designsystem.component.display.IdeTableHeader
import org.ide.lti.core.designsystem.component.display.IdeTableHeaderCell
import org.ide.lti.core.designsystem.component.display.IdeTableRow
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
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
import org.ide.lti.core.model.workspace.ExtractionSettings

@Suppress("UnusedParameter")
@Composable
public fun ExtractEditor(
    settings: ExtractionSettings,
    target: TargetDevice,
    validationErrors: List<ValidationError> = emptyList(),
    onChange: (ExtractionSettings) -> Unit,
    dirtyPaths: Set<String> = emptySet(),
    selectedObjectId: String = "archive-layout",
    onValidate: () -> Unit = {},
    onSave: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val errors = validationErrors.filter { it.fieldPath.startsWith("extraction.") || !it.fieldPath.contains('.') }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("ExtractEditor"),
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
                "dynamic-partitions", "dynamic_partitions" -> item {
                    DynamicPartitionsCard(settings, target, errors, dirtyPaths, onChange)
                }
                "boot-partitions", "boot_partitions" -> item {
                    BootPartitionsCard(settings, target, errors, dirtyPaths, onChange)
                }
                "slot", "filesystem-handling", "filesystem_handling" -> item {
                    SlotFilesystemCard(settings, target, dirtyPaths, onChange)
                }
                else -> item {
                    ArchiveLayoutCard(settings, errors, dirtyPaths, onChange)
                }
            }
            item { Diagnostics(errors) }
        }
    }
}

@Composable
private fun ArchiveLayoutCard(
    settings: ExtractionSettings,
    errors: List<ValidationError>,
    dirtyPaths: Set<String>,
    onChange: (ExtractionSettings) -> Unit,
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
            text = "Archive layout",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.TitleSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )
        Text(
            text = "Source archive inspection is artifact-derived and read-only here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.BodySmall,
            fontFamily = ideFontFamily(),
        )
        Text(
            text = "Adapter",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = ideFontFamily(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            listOf("AUTO", "payload-dumper-go", "lpunpack").forEach { adapter ->
                GlassChip(
                    label = adapter,
                    selected = settings.adapter.equals(adapter, ignoreCase = true),
                    onClick = { onChange(settings.copy(adapter = adapter)) },
                )
            }
        }
        Meta("adapter", dirtyPaths, "Target capability", "Auto selects a supported archive adapter.")
        Diagnostics(errors.filter { it.fieldPath.endsWith("adapter") })
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Dynamic partitions",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.TitleSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )
        Text(
            text = "Select only partitions declared by the pinned target.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.BodySmall,
            fontFamily = ideFontFamily(),
        )
        PartitionChoices(
            options = target.dynamicPartitions,
            selected = settings.dynamicPartitions,
            groupLabel = "Dynamic partition",
        ) {
            onChange(settings.copy(dynamicPartitions = it))
        }
        Meta(
            path = "dynamicPartitions",
            dirty = dirtyPaths,
            source = "Target capability",
            help = "Constrained to target.dynamicPartitions.",
        )
        Diagnostics(errors.filter { it.fieldPath.endsWith("dynamicPartitions") })
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Boot partitions",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.TitleSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )
        PartitionChoices(
            options = target.bootPartitions,
            selected = settings.bootPartitions,
            groupLabel = "Boot partition",
        ) {
            onChange(settings.copy(bootPartitions = it))
        }
        Meta(
            path = "bootPartitions",
            dirty = dirtyPaths,
            source = "Target capability",
            help = "Pinned to target.bootPartitions.",
        )
        Diagnostics(errors.filter { it.fieldPath.endsWith("bootPartitions") })
    }
}

@Composable
private fun SlotFilesystemCard(
    settings: ExtractionSettings,
    target: TargetDevice,
    dirtyPaths: Set<String>,
    onChange: (ExtractionSettings) -> Unit,
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
            text = "Slot and filesystem handling",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.TitleSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )
        val bootSlots = target.packagePolicy.bootSlots.joinToString()
        Text(
            text = "Supported slots: $bootSlots · active ${target.activeSlotSuffix}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.BodySmall,
            fontFamily = ideFontFamily(),
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
            value = settings.slot.orEmpty(),
            onValueChange = {},
            label = "Derived extraction slot",
            readOnly = true,
            state = TextFieldState.Helper("Typed target capability; output paths are workspace-derived."),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            GlassToggle(
                checked = settings.reuseVerifiedExtraction,
                onCheckedChange = { onChange(settings.copy(reuseVerifiedExtraction = it)) },
                activeColor = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Reuse existing extraction only after verified digest",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontFamily = ideFontFamily(),
            )
        }
        Meta(
            path = "slot / reuseVerifiedExtraction",
            dirty = dirtyPaths,
            source = "Workspace override",
            help = "Reuse checks digest and sidecar.",
        )
    }
}

@Composable
private fun PartitionChoices(
    options: List<String>,
    selected: List<String>,
    groupLabel: String,
    onChange: (List<String>) -> Unit,
) {
    IdeTable(
        modifier = Modifier.fillMaxWidth(),
    ) {
        IdeTableHeader {
            IdeTableHeaderCell(
                text = "Select",
                modifier = Modifier.width(GlassDimens.AcquireTableIconColWidth),
            )
            IdeTableHeaderCell(
                text = "Partition",
                modifier = Modifier.weight(2f),
            )
            IdeTableHeaderCell(
                text = "Group",
                modifier = Modifier.weight(1.5f),
            )
            IdeTableHeaderCell(
                text = "Status",
                modifier = Modifier.weight(1.5f),
            )
        }

        options.forEach { name ->
            val isChecked = name in selected
            IdeTableRow(
                isSelected = isChecked,
                onClick = { onChange(if (isChecked) selected - name else selected + name) },
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { onChange(if (isChecked) selected - name else selected + name) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.width(GlassDimens.AcquireTableIconColWidth),
                )
                Text(
                    text = name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.Medium,
                    fontFamily = codeFontFamily(),
                    modifier = Modifier.weight(2f),
                )
                Text(
                    text = groupLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.BodySmall,
                    fontFamily = ideFontFamily(),
                    modifier = Modifier.weight(1.5f),
                )
                Text(
                    text = "Target capability",
                    color = GlassTheme.diagnosticColors.success,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ideFontFamily(),
                    modifier = Modifier.weight(1.5f),
                )
            }
        }
    }
}

@Composable
private fun Meta(path: String, dirty: Set<String>, source: String, help: String) {
    val isDirty = dirty.any { it.startsWith(path.substringBefore('/')) }
    Text(
        text = "$source${if (isDirty) " · Dirty" else ""} · $help",
        color = if (isDirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = FontSize.Micro,
        fontFamily = ideFontFamily(),
    )
}

@Composable
private fun Diagnostics(errors: List<ValidationError>) {
    if (errors.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
        errors.forEach {
            Text(
                text = "${it.severity}: ${it.message}",
                color = if (it.severity ==
                    Severity.ERROR
                ) {
                    GlassTheme.diagnosticColors.error
                } else {
                    GlassTheme.diagnosticColors.warning
                },
                fontSize = FontSize.BodySmall,
                fontFamily = ideFontFamily(),
            )
        }
    }
}
