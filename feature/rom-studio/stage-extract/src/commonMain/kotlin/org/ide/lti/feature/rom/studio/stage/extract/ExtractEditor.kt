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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.IdeTable
import org.ide.lti.core.designsystem.component.display.IdeTableHeader
import org.ide.lti.core.designsystem.component.display.IdeTableHeaderCell
import org.ide.lti.core.designsystem.component.display.IdeTableRow
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
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
import org.ide.lti.core.ui.toolkit.RefusalView
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
    onReplace: (String) -> Unit = {},
    onUseStock: (String) -> Unit = {},
    onReextract: (String) -> Unit = {},
    archiveActions: ArchiveActions = ArchiveActions(),
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
            facts.refusal?.let { refusal ->
                item {
                    RefusalView(refusal = refusal)
                }
            }
            when (selectedObjectId.lowercase()) {
                "dynamic-partitions", "dynamic_partitions" -> {
                    item { DynamicPartitionsCard(settings, target, errors, dirtyPaths, onChange) }
                    item { ReplacementsCard(settings, target, facts, onReplace, onUseStock, onReextract) }
                }

                "boot-partitions", "boot_partitions" -> item {
                    BootPartitionsCard(settings, target, errors, dirtyPaths, onChange)
                }

                "slot" -> item { SlotCard(settings, target, dirtyPaths, onChange) }

                "filesystem-handling", "filesystem_handling" -> item {
                    FilesystemCard(settings, target, dirtyPaths, onChange)
                }

                else -> {
                    item { ArchiveLayoutCard(errors) }
                    item { ArchiveContentsCard(facts) }
                    item { ArchiveBrowserCard(facts.browse, archiveActions) }
                }
            }
            item { Diagnostics(errors) }
        }
    }
}

@Composable
private fun ArchiveLayoutCard(errors: List<ValidationError>) {
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
            text = "How the firmware archive is unpacked: its first bytes say what it is, and that picks the tool.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.BodySmall,
            fontFamily = ideFontFamily(),
        )
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
        val checksum = facts.checksumResult?.let { c ->
            if (c.ok) "checksum OK" else "checksum mismatch: expected ${c.expected ?: "none"}, actual ${c.actual}"
        }
        listOfNotNull(facts.detectedFormat?.let { "Format ${it.name}" }, checksum).takeIf { it.isNotEmpty() }
            ?.let { Hint(it.joinToString(" · ")) }
        val archive = facts.archive
        when {
            !facts.loaded -> Hint("Reading the workspace…")

            archive == null -> Hint("No firmware archive yet. Acquire or import one first.")

            facts.members.isEmpty() -> Hint("$archive could not be read.")

            // The members themselves are in the archive browser below.
            else -> Hint("$archive · ${facts.members.size} members")
        }
        ExtractedImages(facts)
    }
}

/** The images the last Extract produced (with filesystem and size), or what to run first. */
@Composable
private fun ExtractedImages(facts: ExtractFacts) {
    Text(
        text = "Extracted images",
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = FontSize.BodySmall,
        fontWeight = FontWeight.Medium,
        fontFamily = ideFontFamily(),
    )
    if (facts.partitionImages.isEmpty() && facts.extracted.isEmpty()) {
        Hint(if (facts.loaded) "None yet. Run the build through Extract to unpack the archive." else "")
    } else if (facts.partitionImages.isNotEmpty()) {
        facts.partitionImages.forEach { image ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val flags = listOfNotNull(
                    if (image.sparse) "sparse" else null,
                    image.fsType.name.lowercase().takeIf { it != "unknown" },
                ).joinToString()
                val label = if (flags.isNotBlank()) "${image.name} ($flags)" else image.name
                Mono(label)
                Mono(formatBytes(image.sizeBytes))
            }
        }
    } else {
        facts.extracted.forEach { image ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Mono(image.path)
                Mono(formatBytes(image.sizeBytes))
            }
        }
    }
}

/** Partitions whose stock image is replaced by the user's own EROFS image (a prebuilt product, a ported system). */
@Composable
private fun ReplacementsCard(
    settings: ExtractionSettings,
    target: TargetDevice,
    facts: ExtractFacts,
    onReplace: (String) -> Unit,
    onUseStock: (String) -> Unit,
    onReextract: (String) -> Unit,
) {
    var confirm by remember { mutableStateOf<String?>(null) }
    confirm?.let { p ->
        GlassDialog(
            onDismissRequest = { confirm = null },
            title = "Re-extract $p?",
            confirmButton = {
                GlassButton(onClick = {
                    confirm = null
                    onReextract(p)
                }) { Text("Re-extract", fontSize = FontSize.BodySmall) }
            },
            dismissButton = {
                GlassButton(onClick = { confirm = null }) { Text("Cancel", fontSize = FontSize.BodySmall) }
            },
        ) {
            Hint(
                "firmware/extracted/$p and its permission lists are made again from $p.img" +
                    (if (settings.replacementImages[p] != null) " (your own image)" else "") +
                    ". Anything changed there by hand is lost; work/ is assembled from it at the next build.",
            )
        }
    }
    ExtractCard("Your own partition images") {
        Hint(
            "Extract uses an image you pick instead of the firmware's: EROFS, ext4 or F2FS (a sparse one is " +
                "expanded; Build & Sign rebuilds it as the filesystem you choose there). The build stops if the " +
                "file changes outside the Studio. Re-extract unpacks one partition again without a whole Extract run.",
        )
        facts.replaceRefusal?.let { RefusalView(refusal = it) }
        facts.reextractMessage?.let { Hint(it) }
        val partitions = settings.dynamicPartitions.ifEmpty { target.dynamicPartitions }
        val idle = facts.replacing == null && facts.reextracting == null
        partitions.forEach { p ->
            val replaced = settings.replacementImages[p]
            Row(
                modifier = Modifier.fillMaxWidth().testTag("Replacement_$p"),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Mono(p)
                Box(Modifier.weight(1f)) {
                    Hint(replaced?.let { "Your image (sha256 ${it.take(SHA_SHOWN)}…)" } ?: "Stock image")
                }
                GlassButton(onClick = { onReplace(p) }, enabled = idle) {
                    Text(if (facts.replacing == p) "Importing…" else "Use my image…", fontSize = FontSize.BodySmall)
                }
                if (replaced != null) {
                    GlassButton(onClick = { onUseStock(p) }, enabled = idle) {
                        Text("Use the stock image", fontSize = FontSize.BodySmall)
                    }
                }
                GlassButton(
                    onClick = { confirm = p },
                    enabled = idle && facts.extracted.any { it.path == "$p.img" },
                    modifier = Modifier.testTag("Reextract_$p"),
                ) {
                    Text(if (facts.reextracting == p) "Extracting…" else "Re-extract", fontSize = FontSize.BodySmall)
                }
            }
        }
    }
}

private const val SHA_SHOWN = 12

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
