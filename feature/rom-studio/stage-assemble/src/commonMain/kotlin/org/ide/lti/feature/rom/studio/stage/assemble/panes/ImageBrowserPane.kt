/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.assemble.panes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.IdeTable
import org.ide.lti.core.designsystem.component.display.IdeTableHeader
import org.ide.lti.core.designsystem.component.display.IdeTableHeaderCell
import org.ide.lti.core.designsystem.component.display.IdeTableRow
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.TextFieldLeadingSlot
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.pipeline.toolkit.ImageInfo
import org.ide.lti.core.domain.pipeline.toolkit.ImageTreeEntry
import org.ide.lti.core.domain.pipeline.toolkit.Superblock
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.ui.toolkit.RefusalView
import org.ide.lti.feature.rom.studio.api.formatBytes
import org.ide.lti.feature.rom.studio.stage.assemble.ImagePaneActions
import org.ide.lti.feature.rom.studio.stage.assemble.ImagePaneModel
import org.ide.lti.feature.rom.studio.stage.assemble.ImagePaneState

/**
 * A partition's files with owner, mode and SELinux label as Extract read them off the image (FR-008), plus
 * sparse/raw conversion (FR-010) and the super layout (FR-011).
 */
@Composable
public fun ImageBrowserPane(
    target: TargetDevice,
    state: ImagePaneState,
    actions: ImagePaneActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().testTag("ImageBrowserPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Panel {
            Title("Image browser")
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                target.dynamicPartitions.forEach { partition ->
                    GlassChip(
                        label = partition,
                        selected = partition == state.partition && state.tree != null,
                        onClick = { actions.selectPartition(partition) },
                        modifier = Modifier.testTag("PartitionChip_$partition"),
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassTextField(
                    value = state.filter,
                    onValueChange = actions.setFilter,
                    placeholder = "Filter by path (bin, priv-app, ...)",
                    leading = TextFieldLeadingSlot.Custom {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                    },
                    modifier = Modifier.weight(1f).testTag("ImageSearchFilter"),
                )
                GlassButton(
                    onClick = actions.searchAll,
                    enabled = state.busy == null && state.filter.trim().length >= 2,
                    modifier = Modifier.testTag("SearchAllPartitionsButton"),
                ) {
                    Text("Search all partitions", fontSize = FontSize.BodySmall)
                }
                GlassButton(onClick = actions.openTree, enabled = state.tree?.hostFolder != null) {
                    Text("Open the tree in Explorer", fontSize = FontSize.BodySmall)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.tree?.let {
                    val where = if (state.partition == ImagePaneModel.ALL) "every partition" else it.source
                    Hint("${it.tree.entries.size} entries from $where. Click rows to select several.")
                }
                if (state.selected.isNotEmpty()) {
                    GlassButton(
                        onClick = actions.extractSelected,
                        enabled = state.busy == null,
                        modifier = Modifier.testTag("ExtractSelectedButton"),
                    ) { Text("Copy out ${state.selected.size} selected", fontSize = FontSize.BodySmall) }
                    GlassButton(onClick = actions.clearSelection, variant = GlassButtonVariant.Secondary) {
                        Text("Clear selection", fontSize = FontSize.BodySmall)
                    }
                }
            }
            state.imageInfo?.takeIf { it.stock != null || it.built != null }?.let { SuperblockPanel(it) }
            Feedback(state, actions)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(GlassShapes.ShellCard)
                .ideCardSurface(shape = GlassShapes.ShellCard)
                .padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            IdeTable(modifier = Modifier.fillMaxWidth()) {
                IdeTableHeader {
                    IdeTableHeaderCell(text = "Path", modifier = Modifier.weight(PATH_WEIGHT))
                    IdeTableHeaderCell(text = "Size", modifier = Modifier.weight(SIZE_WEIGHT))
                    IdeTableHeaderCell(text = "Mode", modifier = Modifier.weight(1f))
                    IdeTableHeaderCell(text = "Owner", modifier = Modifier.weight(1f))
                    IdeTableHeaderCell(text = "SELinux label", modifier = Modifier.weight(LABEL_WEIGHT))
                    IdeTableHeaderCell(text = "", modifier = Modifier.width(GlassDimens.AcquireTableActionColWidth))
                }
            }
            val entries = state.entries
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(Spacing.Large).testTag("ImageTreeEmptyView"), Alignment.Center) {
                    Hint(
                        when {
                            state.tree == null -> "Pick a partition to read its tree."
                            state.filter.isNotBlank() -> "No entries match '${state.filter}'."
                            else -> "The ${state.partition} tree is empty."
                        },
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("ImageTreeLazyList"),
                    contentPadding = PaddingValues(vertical = Spacing.ExtraSmall),
                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    items(items = entries, key = { it.path }) { entry ->
                        EntryRow(entry, entry.path in state.selected, actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: ImageTreeEntry, selected: Boolean, actions: ImagePaneActions) {
    val isDirectory = entry.path.endsWith("/")
    IdeTableRow(
        isSelected = selected,
        onClick = { actions.toggleSelected(entry) },
        modifier = Modifier.testTag("ImageTreeRow_${entry.path}"),
    ) {
        Row(
            modifier = Modifier.weight(PATH_WEIGHT),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                imageVector = if (isDirectory) Icons.Default.Folder else Icons.Default.Description,
                contentDescription = null,
                tint = with(MaterialTheme.colorScheme) { if (isDirectory) primary else onSurfaceVariant },
                modifier = Modifier.size(IconSize.Small),
            )
            Cell(entry.target?.let { "${entry.path} -> $it" } ?: entry.path)
        }
        Cell(if (isDirectory) "" else formatBytes(entry.size), Modifier.weight(SIZE_WEIGHT))
        Cell(entry.mode.toString(OCTAL).padStart(MODE_DIGITS, '0'), Modifier.weight(1f))
        val caps = if (entry.capabilities != 0L) " caps 0x${entry.capabilities.toString(HEX)}" else ""
        Cell(if (entry.hasOwner) "${entry.uid}:${entry.gid}$caps" else "no fs_config line", Modifier.weight(1f))
        Cell(entry.label, Modifier.weight(LABEL_WEIGHT))
        Box(modifier = Modifier.width(GlassDimens.AcquireTableActionColWidth), contentAlignment = Alignment.CenterEnd) {
            GlassButton(
                variant = GlassButtonVariant.Secondary,
                onClick = { actions.extract(entry) },
                modifier = Modifier.testTag("ExtractEntryButton_${entry.path}"),
            ) {
                Text(text = "Copy out", fontSize = FontSize.Micro)
            }
        }
    }
}

/** The partition's stock image and last built one side by side, as their own tools read the superblock. */
@Composable
private fun SuperblockPanel(info: ImageInfo) {
    // Collapsed by default: the file table below needs the height.
    var open by remember { mutableStateOf(false) }
    fun describe(sb: Superblock?) = sb?.let { "${it.fs.name.lowercase()}, ${formatBytes(it.sizeBytes)}" } ?: "none"
    // Inside the browser's top panel, not a card of its own: the file table below needs the height.
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("SuperblockPanel"),
        ) {
            Hint("Superblock: stock ${describe(info.stock)} · built ${describe(info.built)}")
            GlassButton(onClick = { open = !open }, variant = GlassButtonVariant.Secondary) {
                Text(if (open) "Hide fields" else "Show fields", fontSize = FontSize.Micro)
            }
        }
        if (!open) return@Column
        val keys = (info.stock?.fields.orEmpty() + info.built?.fields.orEmpty()).map { it.first }.distinct()
        Row(Modifier.fillMaxWidth()) {
            Cell("", Modifier.weight(SB_KEY_WEIGHT))
            Cell("Stock (firmware/extracted)", Modifier.weight(1f))
            Cell("Built (out/images)", Modifier.weight(1f))
        }
        keys.forEach { key ->
            SuperblockRow(
                key,
                info.stock?.fields?.firstOrNull { it.first == key }?.second ?: "",
                info.built?.fields?.firstOrNull { it.first == key }?.second ?: "",
            )
        }
    }
}

@Composable
private fun SuperblockRow(key: String, stock: String, built: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Cell(key, Modifier.weight(SB_KEY_WEIGHT))
        Cell(stock, Modifier.weight(1f))
        Cell(built, Modifier.weight(1f))
    }
}

/** Sparse/raw conversion, splitting (FR-010) and the super layout (FR-011) of images in the workspace. */
@Composable
public fun ImageToolsPane(state: ImagePaneState, actions: ImagePaneActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("ImageToolsPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Panel { Feedback(state, actions) }
        ToolsPanel(state, actions)
    }
}

@Composable
private fun ToolsPanel(state: ImagePaneState, actions: ImagePaneActions) {
    var image by remember { mutableStateOf("firmware/raw/super.img") }
    var megabytes by remember { mutableStateOf("1024") }
    val idle = state.busy == null
    Panel {
        Title("Images")
        Hint(
            "A path in the workspace, for example firmware/raw/super.img or out/images/system.img. " +
                "Results go to out/toolkit/.",
        )
        GlassTextField(value = image, onValueChange = { image = it }, placeholder = "Image path")
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassButton(
                onClick = { actions.convert(image, false) },
                enabled = idle,
            ) { Text("Sparse to raw", fontSize = FontSize.BodySmall) }
            GlassButton(
                onClick = { actions.convert(image, true) },
                enabled = idle,
            ) { Text("Raw to sparse", fontSize = FontSize.BodySmall) }
            GlassTextField(
                value = megabytes,
                onValueChange = { megabytes = it.filter(Char::isDigit) },
                placeholder = "MB",
            )
            GlassButton(
                onClick = { megabytes.toIntOrNull()?.let { actions.split(image, it) } },
                enabled = idle && (megabytes.toIntOrNull() ?: 0) > 0,
            ) {
                Text("Split into parts (MB)", fontSize = FontSize.BodySmall)
            }
            GlassButton(
                onClick = actions.readSuper,
                enabled = idle,
            ) { Text("Read super layout", fontSize = FontSize.BodySmall) }
        }
        state.superLayout?.let { layout ->
            Cell(
                "super ${formatBytes(
                    layout.superSize,
                )}, free ${formatBytes(layout.freeBytes)}" + (layout.reason?.let { " - $it" } ?: ""),
            )
            layout.groups.forEach { g ->
                Cell("group ${g.name}: max ${formatBytes(g.maxSize)}")
                layout.partitions.filter { it.group == g.name }.forEach { p ->
                    Cell(
                        "  ${p.name}: ${formatBytes(p.size)}",
                    )
                }
            }
        }
    }
}

@Composable
private fun Feedback(state: ImagePaneState, actions: ImagePaneActions) {
    state.busy?.let { busy ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Hint("$busy…")
            GlassButton(
                onClick = actions.cancel,
                variant = GlassButtonVariant.Secondary,
            ) { Text("Cancel", fontSize = FontSize.BodySmall) }
        }
    }
    state.refusal?.let { RefusalView(refusal = it) }
    state.message?.let { GlassBanner(severity = GlassBannerSeverity.INFO, title = it) }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        content()
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = FontSize.TitleSmall,
        fontWeight = FontWeight.SemiBold,
        fontFamily = ideFontFamily(),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = FontSize.Micro,
        fontFamily = ideFontFamily(),
    )
}

@Composable
private fun Cell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = FontSize.Micro,
        fontFamily = codeFontFamily(),
        maxLines = 1,
        modifier = modifier,
    )
}

private const val SB_KEY_WEIGHT = 1.2f
private const val PATH_WEIGHT = 3.5f
private const val SIZE_WEIGHT = 1.2f
private const val LABEL_WEIGHT = 2.5f
private const val OCTAL = 8
private const val HEX = 16
private const val MODE_DIGITS = 4
