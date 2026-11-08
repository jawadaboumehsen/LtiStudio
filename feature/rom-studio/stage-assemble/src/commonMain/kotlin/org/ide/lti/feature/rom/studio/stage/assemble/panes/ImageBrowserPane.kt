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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
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
import org.ide.lti.core.designsystem.component.display.IdeTable
import org.ide.lti.core.designsystem.component.display.IdeTableHeader
import org.ide.lti.core.designsystem.component.display.IdeTableHeaderCell
import org.ide.lti.core.designsystem.component.display.IdeTableRow
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.TextFieldLeadingSlot
import org.ide.lti.core.designsystem.theme.ComponentSize
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
import org.ide.lti.core.model.workspace.TreeEdit
import org.ide.lti.core.ui.toolkit.RefusalView
import org.ide.lti.feature.rom.studio.api.formatBytes
import org.ide.lti.feature.rom.studio.stage.assemble.ImagePaneActions
import org.ide.lti.feature.rom.studio.stage.assemble.ImagePaneModel
import org.ide.lti.feature.rom.studio.stage.assemble.ImagePaneState

/**
 * A partition's files with owner, mode and SELinux label (FR-008): the project's trees, or any image of the
 * workspace read straight from its file (read-only). A row's details say what the build records for it; rename
 * and delete are kept as changes the build makes every time. Plus sparse/raw conversion (FR-010) and the super
 * layout (FR-011).
 */
@Composable
public fun ImageBrowserPane(
    target: TargetDevice,
    state: ImagePaneState,
    actions: ImagePaneActions,
    modifier: Modifier = Modifier,
    /** The renames and deletions kept for every build (Assemble settings). */
    edits: List<TreeEdit> = emptyList(),
) {
    Column(
        modifier = modifier.fillMaxSize().testTag("ImageBrowserPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        // Capped: messages, kept changes and the super's partitions scroll here, the file table keeps its height.
        ImagePanel(Modifier.heightIn(max = GlassDimens.BrowserControlsMaxHeight), scroll = true) {
            ImageTitle("Image browser")
            SourcePicker(target, state, actions)
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
                    ImageHint(
                        "${it.tree.entries.size} entries from $where. Click a row for its details; tick rows to " +
                            "copy several.",
                    )
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
            val info = state.imageInfo?.takeIf { state.image == null && (it.stock != null || it.built != null) }
            info?.let { SuperblockPanel(it) }
            ImageFeedback(state, actions)
            if (edits.isNotEmpty()) KeptChanges(edits, actions)
        }

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            EntryTable(state, actions, edits, Modifier.weight(1f))
            state.focused?.let { entry ->
                ItemDetails(entry, state, actions, Modifier.width(ComponentSize.PluginInspectorWidth))
            }
        }
    }
    EditDialogs(state, actions)
}

@Composable
private fun EntryTable(
    state: ImagePaneState,
    actions: ImagePaneActions,
    edits: List<TreeEdit>,
    modifier: Modifier = Modifier,
) {
    // A row's kept change, by its tree path: "-> NewName" or "deleted".
    val marks = edits.filter { it.partition == state.partition && state.editable }
        .associate { (it.path.trim('/')) to (it.renameTo?.let { name -> "renamed to $name" } ?: "deleted") }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        IdeTable(modifier = Modifier.fillMaxWidth()) {
            IdeTableHeader {
                IdeTableHeaderCell(text = "", modifier = Modifier.width(IconSize.ExtraLarge))
                IdeTableHeaderCell(text = "Path", modifier = Modifier.weight(PATH_WEIGHT))
                IdeTableHeaderCell(text = "Size", modifier = Modifier.weight(SIZE_WEIGHT))
                IdeTableHeaderCell(text = "Mode", modifier = Modifier.weight(1f))
                IdeTableHeaderCell(text = "Owner", modifier = Modifier.weight(1f))
                IdeTableHeaderCell(text = "SELinux label", modifier = Modifier.weight(LABEL_WEIGHT))
            }
        }
        val entries = state.entries
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(Spacing.Large).testTag("ImageTreeEmptyView"), Alignment.Center) {
                ImageHint(
                    when {
                        state.tree == null && state.busy != null -> "${state.busy}…"
                        state.tree == null && state.members != null -> "Pick a partition of the super image above."
                        state.tree == null -> "Pick a partition, or Browse an image, to read its files."
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
                    EntryRow(
                        entry = entry,
                        ticked = entry.path in state.selected,
                        focused = entry == state.focused,
                        mark = marks[entry.path.trimEnd('/')],
                        actions = actions,
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: ImageTreeEntry,
    ticked: Boolean,
    focused: Boolean,
    mark: String?,
    actions: ImagePaneActions,
) {
    val isDirectory = entry.path.endsWith("/")
    IdeTableRow(
        isSelected = focused,
        onClick = { actions.focus(entry) },
        modifier = Modifier.testTag("ImageTreeRow_${entry.path}"),
    ) {
        Box(Modifier.width(IconSize.ExtraLarge), Alignment.Center) {
            Checkbox(checked = ticked, onCheckedChange = { actions.toggleSelected(entry) })
        }
        Row(
            modifier = Modifier.weight(PATH_WEIGHT),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                imageVector = when {
                    entry.target != null -> Icons.Default.Link
                    isDirectory -> Icons.Default.Folder
                    else -> Icons.Default.Description
                },
                contentDescription = null,
                tint = with(MaterialTheme.colorScheme) { if (isDirectory) primary else onSurfaceVariant },
                modifier = Modifier.size(IconSize.Small),
            )
            ImageCell(
                (entry.target?.let { "${entry.path} -> $it" } ?: entry.path) +
                    (mark?.let { "  ($it in every build)" } ?: ""),
            )
        }
        ImageCell(if (isDirectory) "" else formatBytes(entry.size), Modifier.weight(SIZE_WEIGHT))
        ImageCell(
            if (entry.hasOwner) entry.mode.toString(OCTAL).padStart(MODE_DIGITS, '0') else "",
            Modifier.weight(1f),
        )
        val caps = if (entry.capabilities != 0L) " caps 0x${entry.capabilities.toString(HEX)}" else ""
        ImageCell(if (entry.hasOwner) "${entry.uid}:${entry.gid}$caps" else "no fs_config line", Modifier.weight(1f))
        ImageCell(entry.label, Modifier.weight(LABEL_WEIGHT))
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
            ImageHint("Superblock: stock ${describe(info.stock)} · built ${describe(info.built)}")
            GlassButton(onClick = { open = !open }, variant = GlassButtonVariant.Secondary) {
                Text(if (open) "Hide fields" else "Show fields", fontSize = FontSize.Micro)
            }
        }
        if (!open) return@Column
        val keys = (info.stock?.fields.orEmpty() + info.built?.fields.orEmpty()).map { it.first }.distinct()
        Row(Modifier.fillMaxWidth()) {
            ImageCell("", Modifier.weight(SB_KEY_WEIGHT))
            ImageCell("Stock (firmware/extracted)", Modifier.weight(1f))
            ImageCell("Built (out/images)", Modifier.weight(1f))
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
        ImageCell(key, Modifier.weight(SB_KEY_WEIGHT))
        ImageCell(stock, Modifier.weight(1f))
        ImageCell(built, Modifier.weight(1f))
    }
}

/** Sparse/raw conversion, splitting (FR-010) and the super layout (FR-011) of images in the workspace. */
@Composable
public fun ImageToolsPane(state: ImagePaneState, actions: ImagePaneActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("ImageToolsPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        ImagePanel { ImageFeedback(state, actions) }
        ToolsPanel(state, actions)
    }
}

@Composable
private fun ToolsPanel(state: ImagePaneState, actions: ImagePaneActions) {
    var image by remember { mutableStateOf("firmware/raw/super.img") }
    var megabytes by remember { mutableStateOf("1024") }
    val idle = state.busy == null
    ImagePanel {
        ImageTitle("Images")
        ImageHint(
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
                // What the buttons leave: a field takes the whole width otherwise, and they get none.
                modifier = Modifier.weight(1f),
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
            ImageCell(
                "super ${formatBytes(
                    layout.superSize,
                )}, free ${formatBytes(layout.freeBytes)}" + (layout.reason?.let { " - $it" } ?: ""),
            )
            layout.groups.forEach { g ->
                ImageCell("group ${g.name}: max ${formatBytes(g.maxSize)}")
                layout.partitions.filter { it.group == g.name }.forEach { p ->
                    ImageCell(
                        "  ${p.name}: ${formatBytes(p.size)}",
                    )
                }
            }
        }
    }
}

@Composable
internal fun ImageFeedback(state: ImagePaneState, actions: ImagePaneActions) {
    state.busy?.let { busy ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            ImageHint("$busy…")
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
internal fun ImagePanel(modifier: Modifier = Modifier, scroll: Boolean = false, content: @Composable () -> Unit) {
    val inner = Modifier.padding(Spacing.Medium)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .then(if (scroll) inner.verticalScroll(rememberScrollState()) else inner),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        content()
    }
}

@Composable
internal fun ImageTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = FontSize.TitleSmall,
        fontWeight = FontWeight.SemiBold,
        fontFamily = ideFontFamily(),
    )
}

@Composable
internal fun ImageHint(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = FontSize.Micro,
        fontFamily = ideFontFamily(),
    )
}

@Composable
internal fun ImageCell(text: String, modifier: Modifier = Modifier) {
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
