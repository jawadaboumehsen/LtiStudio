/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.assemble

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.pipeline.stages.TreeEdits
import org.ide.lti.core.domain.pipeline.text.EntryKind
import org.ide.lti.core.domain.pipeline.toolkit.ImageInfo
import org.ide.lti.core.domain.pipeline.toolkit.ImageTreeEntry
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.pipeline.toolkit.SuperLayout
import org.ide.lti.core.domain.ports.AppEnvironmentPort
import org.ide.lti.core.domain.workbench.EntryDetails
import org.ide.lti.core.domain.workbench.ImageKind
import org.ide.lti.core.domain.workbench.ImageWorkbench
import org.ide.lti.core.domain.workbench.OpenedImage
import org.ide.lti.core.domain.workbench.PartitionTree
import org.ide.lti.core.domain.workbench.WorkspaceImage
import org.ide.lti.core.model.workspace.TreeEdit

/** The image browser as the pane shows it (FR-008, FR-010, FR-011). */
public data class ImagePaneState(
    val partition: String = "system",
    val tree: PartitionTree? = null,
    val filter: String = "",
    val superLayout: SuperLayout? = null,
    /** Paths picked for "Copy out selected" (as the tree lists them). */
    val selected: Set<String> = emptySet(),
    /** The partition's stock and built superblocks. */
    val imageInfo: ImageInfo? = null,
    val busy: String? = null,
    val message: String? = null,
    val refusal: Refusal? = null,
    /** The image read straight from its file (with [member], that partition of a super image); null for a tree. */
    val image: String? = null,
    val member: String? = null,
    /** The workspace's images, once the picker asked for them. */
    val images: List<WorkspaceImage>? = null,
    /** The partitions of [image] when it is a super image. */
    val members: SuperLayout? = null,
    /** The row whose details show, and those details once read. */
    val focused: ImageTreeEntry? = null,
    val details: EntryDetails? = null,
    /** The row being renamed or deleted (a dialog asks first). */
    val renaming: ImageTreeEntry? = null,
    val deleting: ImageTreeEntry? = null,
) {
    val entries: List<ImageTreeEntry>
        get() = tree?.tree?.entries.orEmpty().let { all ->
            if (filter.isBlank()) all else all.filter { it.path.contains(filter.trim(), ignoreCase = true) }
        }

    /** An image file or a search across partitions is read-only; only a partition's own tree takes changes. */
    val editable: Boolean get() = tree != null && image == null && partition != ImagePaneModel.ALL
}

/** What the image browser can ask for (see [ImagePaneModel]). */
public class ImagePaneActions(
    public val selectPartition: (String) -> Unit = {},
    public val setFilter: (String) -> Unit = {},
    public val extract: (ImageTreeEntry) -> Unit = {},
    public val openTree: () -> Unit = {},
    public val convert: (image: String, toSparse: Boolean) -> Unit = { _, _ -> },
    public val split: (image: String, megabytes: Int) -> Unit = { _, _ -> },
    public val readSuper: () -> Unit = {},
    public val cancel: () -> Unit = {},
    /** Searches every partition for the current filter text (names only). */
    public val searchAll: () -> Unit = {},
    public val toggleSelected: (ImageTreeEntry) -> Unit = {},
    public val clearSelection: () -> Unit = {},
    /** Copies every selected entry out at once. */
    public val extractSelected: () -> Unit = {},
    /** Lists the workspace's images for the picker. */
    public val listImages: () -> Unit = {},
    /** Reads an image (or, for super, lists its partitions) straight from its file. */
    public val browseImage: (WorkspaceImage) -> Unit = {},
    /** Reads one partition of the open super image. */
    public val browseMember: (String) -> Unit = {},
    /** Shows a row's details: what the build records for it. */
    public val focus: (ImageTreeEntry) -> Unit = {},
    /** Opens the folder holding a row in Explorer. */
    public val reveal: (ImageTreeEntry) -> Unit = {},
    public val startRename: (ImageTreeEntry) -> Unit = {},
    public val rename: (newName: String) -> Unit = {},
    public val startDelete: (ImageTreeEntry) -> Unit = {},
    public val confirmDelete: () -> Unit = {},
    /** Closes the rename or delete dialog without a change. */
    public val dismissEdit: () -> Unit = {},
    /** Takes a kept change back (the next build leaves the item as it is). */
    public val undoEdit: (TreeEdit) -> Unit = {},
)

/** The image browser's state and jobs; only the latest job runs (a new one cancels the last). */
public class ImagePaneModel(
    private val scope: CoroutineScope,
    private val workspaceId: String,
    private val images: ImageWorkbench?,
    private val environment: AppEnvironmentPort?,
    /** The device's dynamic partitions, for a search across all of them. */
    private val partitions: () -> List<String> = { emptyList() },
    /** The changes kept for every build (Assemble settings) and how to save a new list. */
    private val edits: () -> List<TreeEdit> = { emptyList() },
    private val setEdits: (List<TreeEdit>) -> Unit = {},
    /** The user's coverage rules (Build & Sign), tried first for an item with no line. */
    private val coverageRules: suspend () -> String = { "" },
) {
    public companion object {
        /** [ImagePaneState.partition] while the tree shows a search across every partition. */
        public const val ALL: String = "*"
    }

    private val mutableState = MutableStateFlow(ImagePaneState())
    public val state: StateFlow<ImagePaneState> = mutableState.asStateFlow()

    private var job: Job? = null

    public val actions: ImagePaneActions = ImagePaneActions(
        selectPartition = ::selectPartition,
        setFilter = { query -> mutableState.update { it.copy(filter = query) } },
        extract = ::extract,
        openTree = ::openTree,
        convert = { image, toSparse ->
            task("Converting ${image.substringAfterLast('/')}") { images ->
                val out = images.convert(workspaceId, image, toSparse)
                mutableState.update { it.copy(message = "Wrote $out") }
            }
        },
        split = { image, megabytes ->
            task("Splitting ${image.substringAfterLast('/')}") { images ->
                val parts = images.split(workspaceId, image, megabytes)
                mutableState.update { it.copy(message = "Wrote ${parts.size} part(s): ${parts.joinToString()}") }
            }
        },
        readSuper = {
            task("Reading super") { images ->
                val layout = images.superLayout(workspaceId)
                mutableState.update { it.copy(superLayout = layout) }
            }
        },
        cancel = { job?.cancel() },
        searchAll = ::searchAll,
        toggleSelected = { entry ->
            mutableState.update {
                it.copy(
                    selected = if (entry.path in it.selected) it.selected - entry.path else it.selected + entry.path,
                )
            }
        },
        clearSelection = { mutableState.update { it.copy(selected = emptySet()) } },
        extractSelected = ::extractSelected,
        listImages = {
            task("Looking for images") { images ->
                val found = images.images(workspaceId)
                mutableState.update { it.copy(images = found) }
            }
        },
        browseImage = ::browseImage,
        browseMember = { member -> state.value.image?.let { read(it, member) } },
        focus = ::focus,
        reveal = ::reveal,
        startRename = { entry -> mutableState.update { it.copy(renaming = entry, deleting = null, refusal = null) } },
        rename = ::rename,
        startDelete = { entry -> mutableState.update { it.copy(deleting = entry, renaming = null) } },
        confirmDelete = ::confirmDelete,
        dismissEdit = { mutableState.update { it.copy(renaming = null, deleting = null) } },
        undoEdit = { edit ->
            setEdits(edits() - edit)
            mutableState.update {
                it.copy(
                    message = "${edit.partition}/${edit.path} stays as it is in the next build.",
                )
            }
        },
    )

    private fun cleared(s: ImagePaneState) = s.copy(
        tree = null,
        selected = emptySet(),
        imageInfo = null,
        refusal = null,
        message = null,
        focused = null,
        details = null,
    )

    private fun selectPartition(partition: String) {
        mutableState.update { cleared(it).copy(partition = partition, image = null, member = null, members = null) }
        task("Reading $partition") { images ->
            mutableState.update { it.copy(imageInfo = images.imageInfo(workspaceId, partition)) }
            val tree = images.tree(workspaceId, partition)
            mutableState.update { it.copy(tree = tree) }
        }
    }

    /** A filesystem image is read at once; a super image (also a sparse one) first lists its partitions. */
    private fun browseImage(image: WorkspaceImage) {
        mutableState.update { cleared(it).copy(image = image.path, member = null, members = null) }
        val name = image.path.substringAfterLast('/')
        val doing = if (image.kind == ImageKind.Sparse) {
            "Expanding $name once (sparse), then reading it"
        } else {
            "Reading $name"
        }
        task(doing) { images ->
            when (val opened = images.open(workspaceId, image.path)) {
                is OpenedImage.Super -> mutableState.update { it.copy(members = opened.layout) }
                is OpenedImage.Files -> mutableState.update { it.copy(tree = opened.tree) }
            }
        }
    }

    private fun read(image: String, member: String?) {
        mutableState.update { cleared(it).copy(image = image, member = member) }
        task("Reading ${member ?: image.substringAfterLast('/')} (read-only)") { images ->
            val tree = images.browse(workspaceId, image, member)
            mutableState.update { it.copy(tree = tree, partition = member ?: it.partition) }
        }
    }

    private fun focus(entry: ImageTreeEntry) {
        mutableState.update { it.copy(focused = entry, details = null) }
        val s = state.value
        val tree = s.tree ?: return
        // An image read off its file has its own lines in the table; details of the project's lines need a tree.
        if (s.image != null) return
        val (partition, path) = partitionAndPath(entry.path)
        val source = if (s.partition == ALL) "work/$partition" else tree.source
        val kind = when {
            entry.target != null -> EntryKind.Link
            entry.path.endsWith("/") -> EntryKind.Directory
            else -> EntryKind.File
        }
        quietly { images ->
            val details = images.details(workspaceId, partition, path, kind, source, coverageRules())
            mutableState.update { if (it.focused == entry) it.copy(details = details) else it }
        }
    }

    /** Opens the folder holding [entry] in Explorer (a tree of the project only: an image has no folder). */
    private fun reveal(entry: ImageTreeEntry) {
        val folder = state.value.tree?.hostFolder ?: return
        val inside = entry.path.trimEnd('/').substringBeforeLast('/', "")
        val path = if (inside.isEmpty()) folder else folder + "\\" + inside.replace('/', '\\')
        val opened = environment?.openFolder(path) == true
        if (!opened) mutableState.update { it.copy(message = "Open $path in Explorer") }
    }

    private fun rename(newName: String) {
        val entry = state.value.renaming ?: return
        val (partition, path) = partitionAndPath(entry.path)
        keep(TreeEdit(partition, path.trimEnd('/'), newName.trim()))
    }

    private fun confirmDelete() {
        val entry = state.value.deleting ?: return
        val (partition, path) = partitionAndPath(entry.path)
        keep(TreeEdit(partition, path.trimEnd('/')))
    }

    /** Saves [edit] for every build, replacing an earlier change of the same item; refuses one that cannot work. */
    private fun keep(edit: TreeEdit) {
        TreeEdits.problem(edit, partitions().ifEmpty { null })?.let { problem ->
            mutableState.update {
                it.copy(refusal = Refusal(problem, "${edit.partition}/${edit.path}", "Pick another name."))
            }
            return
        }
        val others = edits().filterNot { it.partition == edit.partition && it.path.trim('/') == edit.path }
        setEdits(others + edit)
        val said = if (edit.renameTo == null) "deleted" else "renamed to ${edit.renameTo}"
        mutableState.update {
            it.copy(
                renaming = null,
                deleting = null,
                refusal = null,
                message = "${edit.partition}/${edit.path} will be $said in every build from the next one.",
            )
        }
    }

    /** Copies the selected entries out, per partition (a search hit names its partition first). */
    private fun extractSelected() {
        val picked = state.value.selected.toList()
        task("Extracting ${picked.size} item(s)") { images ->
            val s = state.value
            val dests = if (s.image != null) {
                listOf(images.extractFromImage(workspaceId, s.image, s.member, picked))
            } else {
                val byPartition = picked.groupBy({ partitionAndPath(it).first }, { partitionAndPath(it).second })
                byPartition.map { (partition, paths) -> images.extractMany(workspaceId, partition, paths) }
            }
            mutableState.update { it.copy(message = "Copied ${picked.size} item(s) to ${dests.joinToString()}") }
            dests.singleOrNull()?.let { environment?.openFolder(it) }
        }
    }

    private fun partitionAndPath(path: String): Pair<String, String> = if (state.value.partition == ALL) {
        path.substringBefore('/') to path.substringAfter('/')
    } else {
        state.value.partition to path
    }

    /** Every partition's entries named like the filter, in one list (`<partition>/<path>`). */
    private fun searchAll() {
        val query = state.value.filter
        task("Searching all partitions for '$query'") { images ->
            val hits = images.search(workspaceId, partitions(), query)
            mutableState.update {
                cleared(it).copy(partition = ALL, tree = PartitionTree(hits, "work", null), image = null, member = null)
            }
        }
    }

    /** Copies [entry] out to `out/toolkit/` and opens the folder it landed in. */
    private fun extract(entry: ImageTreeEntry) = task("Extracting ${entry.path}") { images ->
        val s = state.value
        val dest = if (s.image != null) {
            images.extractFromImage(workspaceId, s.image, s.member, listOf(entry.path))
        } else {
            val (partition, path) = partitionAndPath(entry.path)
            images.extract(workspaceId, partition, path)
        }
        mutableState.update { it.copy(message = "Copied to $dest") }
        environment?.openFolder(if (s.image != null) dest else dest.substringBeforeLast('\\').substringBeforeLast('/'))
    }

    /** The whole tree is already unpacked by Extract: this opens it in Explorer. */
    private fun openTree() {
        val folder = state.value.tree?.hostFolder ?: return
        val opened = environment?.openFolder(folder) == true
        if (!opened) mutableState.update { it.copy(message = "Open $folder in Explorer") }
    }

    /** A read that must not cancel the running job (details while browsing); its failure shows as a refusal. */
    private fun quietly(block: suspend (ImageWorkbench) -> Unit) {
        val workbench = images ?: return
        scope.launch {
            try {
                block(workbench)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RefusalException) {
                mutableState.update { it.copy(refusal = e.refusal) }
            } catch (e: Exception) {
                mutableState.update { it.copy(refusal = unexpected(e, "Reading the item's details")) }
            }
        }
    }

    private fun task(name: String, block: suspend (ImageWorkbench) -> Unit) {
        val workbench = images ?: return
        val previous = job
        previous?.cancel()
        val request = ++taskRequest
        mutableState.update { it.copy(busy = name, message = null, refusal = null) }
        job = scope.launch {
            try {
                // The cancelled task first lets go of the job lock, or this one would be refused.
                previous?.join()
                block(workbench)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RefusalException) {
                mutableState.update { it.copy(refusal = e.refusal) }
            } catch (e: IllegalArgumentException) {
                val refusal = Refusal(e.message ?: "Invalid input.", name, "Correct it and try again.")
                mutableState.update { it.copy(refusal = refusal) }
            } catch (e: Exception) {
                mutableState.update { it.copy(refusal = unexpected(e, name)) }
            } finally {
                // A task replaced by a newer one leaves the newer one's indicator alone.
                if (request == taskRequest) mutableState.update { it.copy(busy = null) }
            }
        }
    }

    private var taskRequest = 0L
}

/** A failure no tool reported (the service gone, a read that broke), said in the pane instead of lost. */
internal fun unexpected(e: Exception, task: String): Refusal =
    Refusal(e.message ?: e::class.simpleName ?: "Unknown error", task, "Check the build service and try again.")
