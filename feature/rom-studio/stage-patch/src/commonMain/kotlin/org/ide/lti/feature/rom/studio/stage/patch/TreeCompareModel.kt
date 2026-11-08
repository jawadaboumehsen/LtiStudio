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

import kotlinx.coroutines.flow.first
import org.ide.lti.core.domain.pipeline.stages.FirmwareExtractionStage
import org.ide.lti.core.domain.pipeline.toolkit.TreeChange
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.workbench.CompareSide
import org.ide.lti.core.domain.workbench.CompareSources
import org.ide.lti.core.domain.workbench.CompareWorkbench
import org.ide.lti.core.domain.workbench.EntryComparison
import org.ide.lti.core.domain.workbench.ImageKind
import org.ide.lti.core.domain.workbench.SideComparison
import org.ide.lti.core.domain.workbench.partition

/** Two trees compared (Patches > Compare): the sides picked, what each workspace offers, the result, a row opened. */
public data class TreeCompareState(
    /** The workspaces a side can come from (id, name). */
    val workspaces: List<Pair<String, String>> = emptyList(),
    val sources: Map<String, CompareSources> = emptyMap(),
    /** The partitions inside a super image, by `<workspace>|<image>`. */
    val members: Map<String, List<String>> = emptyMap(),
    val a: CompareSide? = null,
    val b: CompareSide? = null,
    val trustTimes: Boolean = false,
    val result: SideComparison? = null,
    val opened: TreeChange? = null,
    val entry: EntryComparison? = null,
)

/**
 * The actions of Patches > Compare's two-tree comparison. Its state lives in [ContentToolsState.trees]; every read runs
 * as one of the content tools' tasks (one at a time, cancellable, a refusal shown like any other).
 */
public class TreeCompareModel internal constructor(
    private val workspaceId: String,
    private val workspaces: WorkspaceRepository?,
    private val current: () -> TreeCompareState,
    private val update: ((TreeCompareState) -> TreeCompareState) -> Unit,
    private val task: (String, suspend (CompareWorkbench) -> Unit) -> Unit,
) {
    /** The workspaces and this one's sources; the first time, stock against the build of the same partition. */
    public fun loadCompare() {
        task("Looking for trees and images") { tools ->
            val all = workspaces?.getWorkspaces()?.first().orEmpty().filter { it.linuxPath != null }
            val own = tools.sources(workspaceId)
            update { t ->
                val stock = own.trees.firstOrNull { it == "${FirmwareExtractionStage.EXTRACTED}/system" }
                    ?: own.trees.firstOrNull { it.startsWith(FirmwareExtractionStage.EXTRACTED) }
                val built = stock?.let { "work/${it.substringAfterLast('/')}" }?.takeIf { it in own.trees }
                t.copy(
                    workspaces = all.map { it.id to it.name },
                    sources = t.sources + (workspaceId to own),
                    a = t.a ?: stock?.let { CompareSide.Tree(workspaceId, it) },
                    b = t.b ?: built?.let { CompareSide.Tree(workspaceId, it) },
                )
            }
        }
    }

    /** What [workspace] offers to compare, read once. */
    public fun loadSources(workspace: String) {
        if (workspace in current().sources) return
        task("Reading the trees and images of $workspace") { tools ->
            val found = tools.sources(workspace)
            update { it.copy(sources = it.sources + (workspace to found)) }
        }
    }

    /** Sets side A or B; a super image first lists its partitions to pick one. */
    public fun pick(sideA: Boolean, side: CompareSide) {
        update { if (sideA) it.copy(a = side, result = null) else it.copy(b = side, result = null) }
        val image = side as? CompareSide.Image ?: return
        val kind = current().sources[side.workspaceId]?.images?.firstOrNull { it.path == image.image }?.kind
        val key = "${side.workspaceId}|${image.image}"
        // A sparse image may be a super image too: expanded once, it says.
        val mayHoldPartitions = kind == ImageKind.Super || kind == ImageKind.Sparse
        if (image.member == null && mayHoldPartitions && key !in current().members) {
            task("Reading the partitions of ${image.image.substringAfterLast('/')}") { tools ->
                val names = tools.superPartitions(side.workspaceId, image.image)
                update { it.copy(members = it.members + (key to names)) }
            }
        }
    }

    public fun trustTimes(on: Boolean) {
        update { it.copy(trustTimes = on) }
    }

    /** Compares side A with side B. */
    public fun compareTrees() {
        val t = current()
        val a = t.a ?: return
        val b = t.b ?: return
        task("Comparing ${a.partition} with ${b.partition}") { tools ->
            val result = tools.compareSides(a, b, t.trustTimes)
            update { it.copy(result = result, opened = null, entry = null) }
        }
    }

    /** Opens a row of the comparison: its line diff, entries, dex files and methods, or sizes and hashes. */
    public fun open(change: TreeChange) {
        val result = current().result ?: return
        update { it.copy(opened = change, entry = null) }
        task("Comparing ${change.path.substringAfterLast('/')}") { tools ->
            val entry = tools.compareEntry(result.a, result.b, change)
            update { if (it.opened == change) it.copy(entry = entry) else it }
        }
    }
}
