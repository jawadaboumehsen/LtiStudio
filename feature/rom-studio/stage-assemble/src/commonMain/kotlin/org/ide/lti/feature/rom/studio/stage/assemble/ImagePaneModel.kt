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
import org.ide.lti.core.domain.pipeline.toolkit.ImageTreeEntry
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.pipeline.toolkit.SuperLayout
import org.ide.lti.core.domain.ports.AppEnvironmentPort
import org.ide.lti.core.domain.workbench.ImageWorkbench
import org.ide.lti.core.domain.workbench.PartitionTree

/** The image browser as the pane shows it (FR-008, FR-010, FR-011). */
public data class ImagePaneState(
    val partition: String = "system",
    val tree: PartitionTree? = null,
    val filter: String = "",
    val superLayout: SuperLayout? = null,
    val busy: String? = null,
    val message: String? = null,
    val refusal: Refusal? = null,
) {
    val entries: List<ImageTreeEntry>
        get() = tree?.tree?.entries.orEmpty().let { all ->
            if (filter.isBlank()) all else all.filter { it.path.contains(filter.trim(), ignoreCase = true) }
        }
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
)

/** The image browser's state and jobs; only the latest job runs (a new one cancels the last). */
public class ImagePaneModel(
    private val scope: CoroutineScope,
    private val workspaceId: String,
    private val images: ImageWorkbench?,
    private val environment: AppEnvironmentPort?,
    /** The device's dynamic partitions, for a search across all of them. */
    private val partitions: () -> List<String> = { emptyList() },
) {
    public companion object {
        /** [ImagePaneState.partition] while the tree shows a search across every partition. */
        public const val ALL: String = "*"
    }

    private val mutableState = MutableStateFlow(ImagePaneState())
    public val state: StateFlow<ImagePaneState> = mutableState.asStateFlow()

    private var job: Job? = null

    public val actions: ImagePaneActions = ImagePaneActions(
        selectPartition = { partition ->
            mutableState.update { it.copy(partition = partition, tree = null, refusal = null, message = null) }
            task("Reading $partition") { images ->
                val tree = images.tree(workspaceId, partition)
                mutableState.update { it.copy(tree = tree) }
            }
        },
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
    )

    /** Every partition's entries named like the filter, in one list (`<partition>/<path>`). */
    private fun searchAll() {
        val query = state.value.filter
        task("Searching all partitions for '$query'") { images ->
            val hits = images.search(workspaceId, partitions(), query)
            mutableState.update { it.copy(partition = ALL, tree = PartitionTree(hits, "work", null)) }
        }
    }

    /** Copies [entry] out of the tree to `out/toolkit/` and opens the folder it landed in. */
    private fun extract(entry: ImageTreeEntry) = task("Extracting ${entry.path}") { images ->
        // A search hit names its partition first.
        val (partition, path) = if (state.value.partition == ALL) {
            entry.path.substringBefore('/') to entry.path.substringAfter('/')
        } else {
            state.value.partition to entry.path
        }
        val dest = images.extract(workspaceId, partition, path)
        mutableState.update { it.copy(message = "Copied to $dest") }
        environment?.openFolder(dest.substringBeforeLast('\\').substringBeforeLast('/'))
    }

    /** The whole tree is already unpacked by Extract: this opens it in Explorer. */
    private fun openTree() {
        val folder = state.value.tree?.hostFolder ?: return
        val opened = environment?.openFolder(folder) == true
        if (!opened) mutableState.update { it.copy(message = "Open $folder in Explorer") }
    }

    private fun task(name: String, block: suspend (ImageWorkbench) -> Unit) {
        val workbench = images ?: return
        job?.cancel()
        mutableState.update { it.copy(busy = name, message = null, refusal = null) }
        job = scope.launch {
            try {
                block(workbench)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RefusalException) {
                mutableState.update { it.copy(refusal = e.refusal) }
            } catch (e: IllegalArgumentException) {
                val refusal = Refusal(e.message ?: "Invalid input.", name, "Correct it and try again.")
                mutableState.update { it.copy(refusal = refusal) }
            } finally {
                mutableState.update { it.copy(busy = null) }
            }
        }
    }
}
