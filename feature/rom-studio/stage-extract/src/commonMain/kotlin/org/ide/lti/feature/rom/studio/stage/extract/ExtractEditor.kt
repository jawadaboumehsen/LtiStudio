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
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
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
import org.ide.lti.feature.rom.studio.api.formatBytes
import org.ide.lti.feature.rom.studio.api.toggledSelection

@Suppress("UnusedParameter")
@Composable
public fun ExtractEditor(
    settings: ExtractionSettings,
    target: TargetDevice,
    validationErrors: List<ValidationError> = emptyList(),
    onChange: (ExtractionSettings) -> Unit,
    dirtyPaths: Set<String> = emptySet(),
    selectedObjectId: String = "archive-layout",
    facts: ExtractFacts = ExtractFacts(),
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
                "slot" -> item { SlotCard(settings, target, dirtyPaths, onChange) }
                "filesystem-handling", "filesystem_handling" -> item {
                    FilesystemCard(settings, target, dirtyPaths, onChange)
                }
                else -> {
                    item { ArchiveLayoutCard(settings, errors, dirtyPaths, onChange) }
                    item { ArchiveContentsCard(facts) }
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
            text = "How the firmware archive is unpacked. Auto picks the tool from what the archive holds.",
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

/** The source archive's members and the images the last Extract produced: real files, or what to run first. */
@Composable
private fun ArchiveContentsCard(facts: ExtractFacts) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text(
            text = "Archive contents",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.TitleSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )
        val archive = facts.archive
        when {
            !facts.loaded -> Hint("Reading the workspace…")
            archive == null -> Hint("No firmware archive yet. Acquire or import one first.")
            facts.members.isEmpty() -> Hint("$archive could not be read.")
            else -> {
                Hint("$archive · ${facts.members.size} members")
                facts.members.filterNot { it.startsWith("META-INF/") || it.endsWith("/") }.take(MAX_MEMBERS)
                    .forEach { Mono(it) }
            }
        }
        Text(
            text = "Extracted images",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = ideFontFamily(),
        )
        if (facts.extracted.isEmpty()) {
            Hint(if (facts.loaded) "None yet. Run the build through Extract to unpack the archive." else "")
        } else {
            facts.extracted.forEach { image ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Mono(image.path)
                    Mono(formatBytes(image.sizeBytes))
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = FontSize.BodySmall,
        fontFamily = ideFontFamily(),
    )
}

@Composable
private fun Mono(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = FontSize.Micro,
        fontFamily = codeFontFamily(),
    )
}

private const val MAX_MEMBERS = 12

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
            text = "The device's partitions to unpack from the firmware. All are unpacked unless you narrow it.",
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
            help = "From the device configuration.",
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
            help = "From the device configuration.",
        )
        Diagnostics(errors.filter { it.fieldPath.endsWith("bootPartitions") })
    }
}

@Composable
private fun SlotCard(
    settings: ExtractionSettings,
    target: TargetDevice,
    dirtyPaths: Set<String>,
    onChange: (ExtractionSettings) -> Unit,
) {
    ExtractCard("Slot") {
        val bootSlots = target.packagePolicy.bootSlots.joinToString()
        Hint("Which A/B slot's images to unpack. Device slots: $bootSlots · active ${target.activeSlotSuffix}.")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            listOf(null, *target.packagePolicy.bootSlots.toTypedArray()).forEach { slot ->
                GlassChip(
                    label = slot ?: "Auto",
                    selected = settings.slot == slot,
                    onClick = { onChange(settings.copy(slot = slot)) },
                )
            }
        }
        Mono("Unpacks slot " + (settings.slot ?: "${target.activeSlotSuffix.removePrefix("_")} (the active one)"))
        Meta(
            path = "slot",
            dirty = dirtyPaths,
            source = "This workspace",
            help = "Auto follows the device's active slot.",
        )
    }
}

@Composable
private fun FilesystemCard(
    settings: ExtractionSettings,
    target: TargetDevice,
    dirtyPaths: Set<String>,
    onChange: (ExtractionSettings) -> Unit,
) {
    ExtractCard("Filesystem handling") {
        Hint(
            "Images are unpacked to firmware/extracted/ as raw ${target.filesystemType} images; " +
                "Assemble copies their files into work/ with their owners, modes and SELinux labels.",
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
                text = "Reuse the last extraction when the archive's SHA-256 is unchanged",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontFamily = ideFontFamily(),
            )
        }
        Meta(
            path = "reuseVerifiedExtraction",
            dirty = dirtyPaths,
            source = "This workspace",
            help = "Off unpacks the archive again on every build.",
        )
    }
}

@Composable
private fun ExtractCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.TitleSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )
        content()
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

        val effective = selected.ifEmpty { options }
        options.forEach { name ->
            val isChecked = name in effective
            IdeTableRow(
                isSelected = isChecked,
                onClick = { onChange(toggledSelection(options, selected, name)) },
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { onChange(toggledSelection(options, selected, name)) },
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
                    text = if (isChecked) "Unpacked" else "Skipped",
                    color = if (isChecked) {
                        GlassTheme.diagnosticColors.success
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
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
