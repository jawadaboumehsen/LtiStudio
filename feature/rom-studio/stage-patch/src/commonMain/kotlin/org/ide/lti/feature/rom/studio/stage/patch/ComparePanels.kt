/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.patch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.IdeTable
import org.ide.lti.core.designsystem.component.display.IdeTableHeader
import org.ide.lti.core.designsystem.component.display.IdeTableHeaderCell
import org.ide.lti.core.designsystem.component.display.IdeTableRow
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenu
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenuHeader
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenuItem
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.pipeline.stages.FirmwareExtractionStage
import org.ide.lti.core.domain.pipeline.toolkit.SuperblockRow
import org.ide.lti.core.domain.pipeline.toolkit.TreeChange
import org.ide.lti.core.domain.pipeline.toolkit.TreeChangeKind
import org.ide.lti.core.domain.workbench.CompareSide
import org.ide.lti.core.domain.workbench.ImageKind
import org.ide.lti.core.domain.workbench.SideComparison
import org.ide.lti.core.domain.workbench.imageOrigin
import org.ide.lti.core.domain.workbench.partition

/**
 * Two trees compared path by path: each side a partition tree or an image of any workspace, so stock against the
 * build, one release against the next, or the same partition of two phones; the filesystems need not match.
 */
@Composable
internal fun TreeCompareCard(state: ContentToolsState, viewModel: ContentToolsViewModel) {
    val t = state.trees
    LaunchedEffect(Unit) { viewModel.trees.loadCompare() }
    CompareCard("Compare two trees") {
        CompareHint(
            "Each side is a partition tree (stock firmware or the build) or an image read straight from its file, " +
                "from any workspace. Every path is marked identical, added, removed, content, metadata (owner, mode, " +
                "capabilities, SELinux label, link target) or kind. Click a changed file for its line diff, its " +
                "archive entries, or a JAR's or APK's dex files and methods.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            SidePicker("A", t.a, true, state, viewModel, Modifier.weight(1f))
            SidePicker("B", t.b, false, state, viewModel, Modifier.weight(1f))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassToggle(checked = t.trustTimes, onCheckedChange = viewModel.trees::trustTimes)
            CompareHint("Trust the same size and time (faster; otherwise every file of the same size is hashed)")
            Box(Modifier.weight(1f))
            GlassButton(
                onClick = viewModel.trees::compareTrees,
                enabled = state.busy == null && ready(t.a, t) && ready(t.b, t),
                variant = GlassButtonVariant.Primary,
                modifier = Modifier.testTag("CompareTreesButton"),
            ) { Text("Compare", fontSize = FontSize.BodySmall) }
        }
        if (t.sources[state.trees.a?.workspaceId ?: ""]?.trees?.isEmpty() == true) {
            CompareHint("This workspace has no trees yet: run the build through Extract, or pick an image.")
        }
    }
    t.result?.let { result -> ResultCard(result, state, viewModel) }
}

/** Whether [side] names something to read: a tree, a filesystem image, or a partition picked inside super. */
private fun ready(side: CompareSide?, t: TreeCompareState): Boolean = when (side) {
    null -> false

    // A workspace was chosen, nothing in it yet.
    is CompareSide.Tree -> side.folder.isNotEmpty()

    is CompareSide.Image -> {
        val kind = t.sources[side.workspaceId]?.images?.firstOrNull { it.path == side.image }?.kind
        val members = t.members["${side.workspaceId}|${side.image}"]
        // A super image needs a partition picked; a sparse one too, unless it turned out to hold none.
        side.member != null || kind == ImageKind.Erofs || kind == ImageKind.Ext4 || kind == ImageKind.F2fs ||
            (kind == ImageKind.Sparse && members?.isEmpty() == true)
    }
}

/** One side: its workspace, then a tree or an image of it, then (for a super image) a partition inside. */
@Composable
private fun SidePicker(
    name: String,
    side: CompareSide?,
    isA: Boolean,
    state: ContentToolsState,
    viewModel: ContentToolsViewModel,
    modifier: Modifier = Modifier,
) {
    val workspace = side?.workspaceId ?: viewModel.workspaceId
    val model = viewModel.trees
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Text(
            name,
            fontSize = FontSize.TitleSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        WorkspaceMenu(name, workspace, state) { id ->
            model.loadSources(id)
            // A workspace chosen, nothing in it picked yet.
            if (id != workspace) model.pick(isA, CompareSide.Tree(id, ""))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            TreeMenu(name, workspace, side, state, Modifier.weight(1f)) { model.pick(isA, it) }
            ImageMenu(name, workspace, side, state, Modifier.weight(1f)) { model.pick(isA, it) }
        }
        val image = side as? CompareSide.Image
        val members = image?.let { state.trees.members["${it.workspaceId}|${it.image}"] }
        if (image != null && members != null) MemberChips(image, members) { model.pick(isA, it) }
    }
}

@Composable
private fun WorkspaceMenu(name: String, workspace: String, state: ContentToolsState, onPick: (String) -> Unit) {
    val all = state.trees.workspaces
    val label = all.firstOrNull { it.first == workspace }?.second ?: workspace
    Menu(label, "CompareWorkspace$name", state.busy == null, picked = false) { close ->
        GlassDropdownMenuHeader("Workspace")
        all.forEach { (id, title) ->
            GlassDropdownMenuItem(
                text = title,
                isSelected = id == workspace,
                onClick = {
                    close()
                    onPick(id)
                },
            )
        }
    }
}

/** The workspace's partition trees: stock, then the build's. */
@Composable
private fun TreeMenu(
    name: String,
    workspace: String,
    side: CompareSide?,
    state: ContentToolsState,
    modifier: Modifier = Modifier,
    onPick: (CompareSide) -> Unit,
) {
    val trees = state.trees.sources[workspace]?.trees
    val picked = (side as? CompareSide.Tree)?.takeIf { it.workspaceId == workspace && it.folder.isNotEmpty() }
    val label = picked?.let(::describe) ?: if (trees?.isEmpty() == true) "No trees yet" else "Tree…"
    Menu(label, "CompareTree$name", state.busy == null && !trees.isNullOrEmpty(), picked != null, modifier) { close ->
        trees.orEmpty().forEach { folder ->
            val pick = CompareSide.Tree(workspace, folder)
            val choose = {
                close()
                onPick(pick)
            }
            GlassDropdownMenuItem(describe(pick), choose, subtitle = folder, isSelected = side == pick)
        }
    }
}

/** The workspace's images by where they come from, read straight from the file. */
@Composable
private fun ImageMenu(
    name: String,
    workspace: String,
    side: CompareSide?,
    state: ContentToolsState,
    modifier: Modifier = Modifier,
    onPick: (CompareSide) -> Unit,
) {
    val images = state.trees.sources[workspace]?.images
    val picked = (side as? CompareSide.Image)?.takeIf { it.workspaceId == workspace }
    val label = picked?.let(::describe) ?: if (images?.isEmpty() == true) "No images yet" else "Image…"
    val enabled = state.busy == null && !images.isNullOrEmpty()
    Menu(label, "CompareImage$name", enabled, picked != null, modifier) { close ->
        images.orEmpty().groupBy { imageOrigin(it.path) }.forEach { (origin, group) ->
            GlassDropdownMenuHeader(origin)
            group.forEach { image ->
                val choose = {
                    close()
                    onPick(CompareSide.Image(workspace, image.path))
                }
                GlassDropdownMenuItem(
                    text = image.path.substringAfterLast('/'),
                    onClick = choose,
                    subtitle = "${image.kind.name.lowercase()} · ${image.path}",
                    isSelected = picked?.image == image.path,
                )
            }
        }
    }
}

/** The partitions inside a super image, to pick the one to compare. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberChips(image: CompareSide.Image, members: List<String>, onPick: (CompareSide) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        members.forEach { m ->
            GlassChip(label = m, selected = image.member == m, onClick = { onPick(image.copy(member = m)) })
        }
    }
}

/** A side in words: `system · stock firmware`, `system · the build (work/)`, `odm.img · Stock firmware`. */
private fun describe(side: CompareSide): String = when (side) {
    is CompareSide.Tree -> when {
        side.folder.isEmpty() -> ""
        side.folder.startsWith(FirmwareExtractionStage.EXTRACTED) -> "${side.partition} · stock firmware"
        else -> "${side.partition} · the build (work/)"
    }

    is CompareSide.Image -> side.image.substringAfterLast('/') + (side.member?.let { " › $it" } ?: "") +
        " · ${imageOrigin(side.image)}"
}

@Composable
private fun Menu(
    label: String,
    tag: String,
    enabled: Boolean,
    picked: Boolean = true,
    modifier: Modifier = Modifier,
    items: @Composable (close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        GlassButton(
            onClick = { open = true },
            enabled = enabled,
            // The menu holding the side's pick stands out; the other offers the alternative.
            variant = if (picked) GlassButtonVariant.Primary else GlassButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth().testTag(tag),
        ) { Text("$label ▾", fontSize = FontSize.BodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        GlassDropdownMenu(expanded = open, onDismissRequest = { open = false }) { items { open = false } }
    }
}

/** The changed paths with counts to filter by, a search, the superblocks beside them, and the opened row below. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(result: SideComparison, state: ContentToolsState, viewModel: ContentToolsViewModel) {
    var shown by remember(result) { mutableStateOf<TreeChangeKind?>(null) }
    var query by remember(result) { mutableStateOf("") }
    val diff = result.diff
    CompareCard("${describe(result.a)}  →  ${describe(result.b)}") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            GlassChip(label = "identical ${diff.unchanged}", selected = false)
            TreeChangeKind.entries.forEach { k ->
                GlassChip(
                    label = "${KIND_LABEL.getValue(k)} ${diff.count(k)}",
                    selected = shown == k,
                    onClick = { shown = if (shown == k) null else k },
                    modifier = Modifier.testTag("CompareKind_$k"),
                )
            }
        }
        GlassTextField(value = query, onValueChange = { query = it }, placeholder = "Filter paths…")
        CompareHint("${result.hashedFiles} file(s) of the same size hashed on both sides.")
        val rows = diff.changes.filter { (shown == null || it.kind == shown) && query.trim() in it.path }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            ChangeTable(rows, diff.changes.isEmpty(), state.trees.opened, viewModel, Modifier.weight(LIST_WEIGHT))
            Superblocks(result.superblocks, Modifier.weight(1f))
        }
    }
    state.trees.opened?.let { opened -> EntryCard(opened, state.trees.entry, state.busy != null) }
}

@Composable
private fun ChangeTable(
    rows: List<TreeChange>,
    same: Boolean,
    opened: TreeChange?,
    viewModel: ContentToolsViewModel,
    modifier: Modifier = Modifier,
) {
    IdeTable(modifier) {
        IdeTableHeader {
            IdeTableHeaderCell("Change", Modifier.weight(KIND_WEIGHT))
            IdeTableHeaderCell("Path", Modifier.weight(1f))
        }
        if (rows.isEmpty()) {
            IdeTableRow {
                CompareHint(if (same) "No differences: every path is identical on both sides." else "No path matches.")
            }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = ComponentSize.MaxPanelHeight)) {
            items(rows, key = { it.path }) { change ->
                IdeTableRow(
                    isSelected = change == opened,
                    onClick = { viewModel.trees.open(change) },
                    modifier = Modifier.testTag("CompareRow_${change.path}"),
                ) {
                    Text(
                        KIND_LABEL.getValue(change.kind),
                        color = kindColor(change.kind),
                        fontSize = FontSize.BodySmall,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = ideFontFamily(),
                        modifier = Modifier.weight(KIND_WEIGHT),
                    )
                    Column(Modifier.weight(1f)) {
                        CompareMono(change.path)
                        if (change.details.isNotEmpty()) CompareHint(change.details.joinToString(" · "))
                    }
                }
            }
        }
    }
}

/** Both superblocks as a table, one field a row; a field that differs is marked. */
@Composable
private fun Superblocks(rows: List<SuperblockRow>, modifier: Modifier = Modifier) {
    IdeTable(modifier) {
        IdeTableHeader {
            IdeTableHeaderCell("Superblock", Modifier.weight(FIELD_WEIGHT))
            IdeTableHeaderCell("A", Modifier.weight(1f))
            IdeTableHeaderCell("B", Modifier.weight(1f))
        }
        if (rows.isEmpty()) CompareHint("Neither side has an image to read one from (Build makes the build's).")
        rows.forEach { row ->
            val valueColor = if (row.differs) {
                GlassTheme.diagnosticColors.warning
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            IdeTableRow(modifier = Modifier.testTag("Superblock_${row.field}")) {
                Box(Modifier.weight(FIELD_WEIGHT)) { CompareHint(row.field) }
                listOf(row.a, row.b).forEach { value ->
                    Text(
                        value ?: "—",
                        color = valueColor,
                        fontSize = FontSize.BodySmall,
                        fontWeight = if (row.differs) FontWeight.SemiBold else FontWeight.Normal,
                        fontFamily = codeFontFamily(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun kindColor(kind: TreeChangeKind): Color = with(GlassTheme.diagnosticColors) {
    when (kind) {
        TreeChangeKind.ADDED -> gitAdded
        TreeChangeKind.REMOVED -> gitDeleted
        TreeChangeKind.CONTENT -> gitModified
        TreeChangeKind.META -> info
        TreeChangeKind.TYPE -> warning
    }
}

private val KIND_LABEL = mapOf(
    TreeChangeKind.ADDED to "added",
    TreeChangeKind.REMOVED to "removed",
    TreeChangeKind.CONTENT to "content",
    TreeChangeKind.META to "metadata",
    TreeChangeKind.TYPE to "kind",
)
private const val LIST_WEIGHT = 1.6f
private const val KIND_WEIGHT = 0.18f
private const val FIELD_WEIGHT = 0.8f

@Composable
internal fun CompareCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
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
internal fun CompareHint(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = FontSize.BodySmall,
        fontFamily = ideFontFamily(),
    )
}

@Composable
internal fun CompareMono(text: String, color: Color = Color.Unspecified) {
    Text(
        text = text,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
        fontSize = FontSize.BodySmall,
        fontFamily = codeFontFamily(),
    )
}
