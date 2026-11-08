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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenu
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenuHeader
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenuItem
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.pipeline.toolkit.CoverageFix
import org.ide.lti.core.domain.pipeline.toolkit.ImageTreeEntry
import org.ide.lti.core.domain.workbench.ImageKind
import org.ide.lti.core.domain.workbench.WorkspaceImage
import org.ide.lti.core.domain.workbench.imageOrigin
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.TreeEdit
import org.ide.lti.feature.rom.studio.api.formatBytes
import org.ide.lti.feature.rom.studio.stage.assemble.ImagePaneActions
import org.ide.lti.feature.rom.studio.stage.assemble.ImagePaneState

/** What to read: a partition's tree in the project, or an image of the workspace (and a partition of super). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SourcePicker(target: TargetDevice, state: ImagePaneState, actions: ImagePaneActions) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        target.dynamicPartitions.forEach { partition ->
            GlassChip(
                label = partition,
                selected = state.image == null && partition == state.partition && state.tree != null,
                onClick = { actions.selectPartition(partition) },
                modifier = Modifier.testTag("PartitionChip_$partition"),
            )
        }
        ImageMenu(state, actions)
    }
    state.members?.let { layout ->
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            ImageHint("Inside ${state.image?.substringAfterLast('/')}:")
            layout.partitions.filter { it.size > 0 }.forEach { p ->
                GlassChip(
                    label = "${p.name} · ${formatBytes(p.size)}",
                    selected = p.name == state.member,
                    onClick = { actions.browseMember(p.name) },
                    modifier = Modifier.testTag("SuperMemberChip_${p.name}"),
                )
            }
        }
    }
}

/**
 * "Browse an image…": the workspace's images by where they come from, read straight from the file. The list is
 * read again each time it opens, so a new build's images show up.
 */
@Composable
private fun ImageMenu(state: ImagePaneState, actions: ImagePaneActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        GlassButton(
            onClick = {
                actions.listImages()
                open = true
            },
            enabled = state.busy == null,
            variant = if (state.image != null) GlassButtonVariant.Primary else GlassButtonVariant.Secondary,
            modifier = Modifier.testTag("BrowseImageButton"),
        ) {
            Text(
                state.image?.let {
                    "${it.substringAfterLast(
                        '/',
                    )} · ${imageOrigin(it)} (read-only) ▾"
                } ?: "Browse an image… ▾",
                fontSize = FontSize.BodySmall,
            )
        }
        val images = state.images
        GlassDropdownMenu(expanded = open && images != null, onDismissRequest = { open = false }) {
            if (images.isNullOrEmpty()) {
                GlassDropdownMenuHeader("No EROFS, ext4, F2FS or super image yet: run the build through Extract.")
            }
            images.orEmpty().groupBy { imageOrigin(it.path) }.forEach { (where, group) ->
                GlassDropdownMenuHeader(where)
                group.forEach { image ->
                    GlassDropdownMenuItem(
                        text = image.path.substringAfterLast('/'),
                        subtitle = "${kindOf(image)}, ${formatBytes(image.sizeBytes)} · ${image.path}",
                        isSelected = image.path == state.image,
                        onClick = {
                            open = false
                            actions.browseImage(image)
                        },
                        modifier = Modifier.testTag("ImageItem_${image.path}"),
                    )
                }
            }
        }
    }
}

private fun kindOf(image: WorkspaceImage): String = when (image.kind) {
    ImageKind.Erofs -> "EROFS"
    ImageKind.Ext4 -> "ext4"
    ImageKind.F2fs -> "F2FS"
    ImageKind.Super -> "super"
    ImageKind.Sparse -> "sparse"
}

/** Everything known about the focused row, what the build records for it, and what can be done with it. */
@Composable
internal fun ItemDetails(
    entry: ImageTreeEntry,
    state: ImagePaneState,
    actions: ImagePaneActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium)
            .verticalScroll(rememberScrollState())
            .testTag("ImageItemDetails"),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        ItemFacts(entry, state)
        if (state.image != null) ReadFromImage(entry) else RecordedForBuild(state)
        ItemOperations(entry, state, actions)
    }
}

@Composable
private fun ItemFacts(entry: ImageTreeEntry, state: ImagePaneState) {
    val isDirectory = entry.path.endsWith("/")
    ImageTitle(entry.path.trimEnd('/').substringAfterLast('/').ifEmpty { "/" })
    val kind = when {
        entry.target != null -> "symbolic link"
        isDirectory -> "folder"
        else -> "file"
    }
    Detail("Kind", kind)
    Detail("In", state.tree?.source.orEmpty())
    Detail("Path", entry.path.trimEnd('/'))
    state.details?.let { Detail("Device path", it.devicePath) }
    if (!isDirectory) Detail("Size", "${formatBytes(entry.size)} (${entry.size} bytes)")
    entry.target?.let { Detail("Target", it) }
    state.details?.resolves?.let { Detail("Resolves", it) }
}

@Composable
private fun ReadFromImage(entry: ImageTreeEntry) {
    Section("Read from the image")
    Detail("Owner", "${entry.uid}:${entry.gid}")
    Detail("Mode", entry.mode.toString(OCTAL_RADIX).padStart(MODE_WIDTH, '0'))
    Detail("SELinux", entry.label)
    if (entry.capabilities != 0L) Detail("Capabilities", "0x${entry.capabilities.toString(HEX_RADIX)}")
}

/** The item's fs_config and SELinux lines, or what the build adds and why. */
@Composable
private fun RecordedForBuild(state: ImagePaneState) {
    Section("Recorded for the build")
    val details = state.details
    if (details == null) {
        ImageHint("Reading…")
        return
    }
    val preview = details.preview
    Detail(
        "fs_config",
        details.fsConfigLine?.substringAfter(' ')
            ?: "none yet: the build gives ${preview?.owner} (${preview?.ownerFrom?.said})",
    )
    Detail("SELinux", details.label ?: "none yet: the build gives ${preview?.label} (${preview?.labelFrom?.said})")
    if (preview?.labelFrom == CoverageFix.Source.Folder && details.label == null) {
        ImageHint("No rule of the ROM names this path, so it takes its folder's label.")
    }
}

@Composable
private fun ItemOperations(entry: ImageTreeEntry, state: ImagePaneState, actions: ImagePaneActions) {
    Section("Operations")
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        GlassButton(
            onClick = { actions.extract(entry) },
            enabled = state.busy == null,
            modifier = Modifier.testTag("CopyOutButton"),
        ) { Text("Copy out", fontSize = FontSize.Micro) }
        if (state.tree?.hostFolder != null) {
            GlassButton(onClick = { actions.reveal(entry) }, variant = GlassButtonVariant.Secondary) {
                Text("Reveal in Explorer", fontSize = FontSize.Micro)
            }
        }
    }
    if (!state.editable) {
        ImageHint(
            if (state.image != null) {
                "Read-only view inside the image. Copy out saves a file or folder to out/toolkit/."
            } else {
                "Search results are read-only: open the partition to rename or delete."
            },
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        GlassButton(
            onClick = { actions.startRename(entry) },
            enabled = entry.target == null,
            variant = GlassButtonVariant.Secondary,
            modifier = Modifier.testTag("RenameEntryButton"),
        ) { Text("Rename…", fontSize = FontSize.Micro) }
        GlassButton(
            onClick = { actions.startDelete(entry) },
            variant = GlassButtonVariant.Secondary,
            modifier = Modifier.testTag("DeleteEntryButton"),
        ) { Text("Delete…", fontSize = FontSize.Micro) }
    }
    if (entry.target != null) {
        ImageHint(
            "A link is not renamed here: other files reach things through its name, and nothing in the tree " +
                "would show what broke.",
        )
    }
    ImageHint("Renames and deletions apply to the work tree in every build, after the mods and scripts.")
}

/** The renames and deletions every build makes, each with an Undo; folded away until asked for. */
@Composable
internal fun KeptChanges(edits: List<TreeEdit>, actions: ImagePaneActions) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall), modifier = Modifier.testTag("KeptChanges")) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ImageHint("${edits.size} change(s) kept for every build")
            GlassButton(onClick = { open = !open }, variant = GlassButtonVariant.Secondary) {
                Text(if (open) "Hide" else "Show", fontSize = FontSize.Micro)
            }
        }
        if (!open) return@Column
        edits.forEach { edit ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ImageCell(
                    if (edit.renameTo == null) {
                        "delete ${edit.partition}/${edit.path}"
                    } else {
                        "rename ${edit.partition}/${edit.path} to ${edit.renameTo}"
                    },
                    Modifier.weight(1f),
                )
                GlassButton(onClick = { actions.undoEdit(edit) }, variant = GlassButtonVariant.Secondary) {
                    Text("Undo", fontSize = FontSize.Micro)
                }
            }
        }
    }
}

/** Asks before a rename or deletion is kept. */
@Composable
internal fun EditDialogs(state: ImagePaneState, actions: ImagePaneActions) {
    state.renaming?.let { entry ->
        val old = entry.path.trimEnd('/').substringAfterLast('/')
        var name by remember(entry) { mutableStateOf(old) }
        GlassDialog(
            onDismissRequest = actions.dismissEdit,
            title = "Rename \"$old\"",
            confirmButton = {
                GlassButton(
                    onClick = { actions.rename(name) },
                    enabled = name.isNotBlank() && name.trim() != old,
                    variant = GlassButtonVariant.Primary,
                    modifier = Modifier.testTag("ConfirmRenameButton"),
                ) { Text("Rename in every build") }
            },
            dismissButton = {
                GlassButton(onClick = actions.dismissEdit, variant = GlassButtonVariant.Standard) { Text("Cancel") }
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                DialogText("A new name in the same folder. It keeps its owner, mode and SELinux label.")
                state.refusal?.let {
                    Text(
                        text = it.cause,
                        fontSize = FontSize.BodySmall,
                        fontFamily = ideFontFamily(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("RenameError"),
                    )
                }
                GlassTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "New name",
                    modifier = Modifier.fillMaxWidth().testTag("RenameField"),
                )
            }
        }
    }
    state.deleting?.let { entry ->
        val isFolder = entry.path.endsWith("/")
        GlassDialog(
            onDismissRequest = actions.dismissEdit,
            title = "Delete \"${entry.path.trimEnd('/').substringAfterLast('/')}\"?",
            confirmButton = {
                GlassButton(
                    onClick = actions.confirmDelete,
                    variant = GlassButtonVariant.Primary,
                    modifier = Modifier.testTag("ConfirmDeleteButton"),
                ) { Text("Delete in every build") }
            },
            dismissButton = {
                GlassButton(onClick = actions.dismissEdit, variant = GlassButtonVariant.Standard) { Text("Cancel") }
            },
        ) {
            DialogText(
                (if (isFolder) "This folder and everything in it" else "This item") +
                    " is removed from the work tree in every build, with its permission and SELinux lines. " +
                    "Undo it under Changes kept for every build. An app is better removed in Debloat, which checks " +
                    "what still needs it.",
            )
        }
    }
}

@Composable
private fun DialogText(text: String) {
    Text(
        text = text,
        fontSize = FontSize.BodySmall,
        fontFamily = ideFontFamily(),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun Section(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = FontSize.BodySmall,
        fontWeight = FontWeight.SemiBold,
        fontFamily = ideFontFamily(),
        modifier = Modifier.padding(top = Spacing.Small),
    )
}

@Composable
private fun Detail(key: String, value: String) {
    // One line per fact, key beside value: the panel shares the height with the file table.
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = key,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            modifier = Modifier.weight(KEY_WEIGHT),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.Micro,
            fontFamily = codeFontFamily(),
            modifier = Modifier.weight(VALUE_WEIGHT),
        )
    }
}

private const val OCTAL_RADIX = 8
private const val HEX_RADIX = 16
private const val MODE_WIDTH = 4
private const val KEY_WEIGHT = 1f
private const val VALUE_WEIGHT = 2.6f
