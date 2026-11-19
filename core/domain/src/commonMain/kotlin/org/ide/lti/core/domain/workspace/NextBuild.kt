/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workspace

import kotlinx.coroutines.flow.first
import org.ide.lti.core.domain.pipeline.RunEstimate
import org.ide.lti.core.domain.pipeline.StagePreview
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState

/** What building now would do, next to the last build that worked. */
public data class NextBuildFacts(
    /** The newest succeeded run that made a package; null when none has here. */
    val lastGood: BuildRun?,
    /** Settings fields and mod versions changed since [lastGood]; null when its snapshot is unavailable. */
    val changes: List<String>?,
    /** Each stage of the next run: reused from cache, or why it runs. */
    val stages: List<StagePreview>,
    /** How long and how much disk the stages that run took before; null when none runs or none was measured. */
    val estimate: RunEstimate?,
)

/**
 * The expected effect of the next build, read without building: the settings and mods changed since the last build
 * that made a package (its snapshot against the current one), which stages run and why ([preview], the same code
 * as a run), and the time and disk earlier runs took for them. Writes nothing.
 */
public class NextBuild(
    private val workspaces: WorkspaceRepository,
    private val snapshots: SnapshotRepository,
    private val runs: RunRepository,
    private val preview: suspend (workspaceId: String) -> Result<List<StagePreview>>,
) {
    public suspend fun of(workspaceId: String): NextBuildFacts {
        // The preview saves pending settings first, so the current snapshot below is the one it judged.
        val stages = preview(workspaceId).getOrThrow()
        val history = runs.observeRuns(workspaceId).first()
        val lastGood = history.filter { it.state == RunState.SUCCEEDED && it.artifacts.isNotEmpty() }
            .maxByOrNull { it.startedAt }
        val then = lastGood?.let { snapshots.getSnapshot(it.snapshotId) }
        val now = workspaces.getWorkspace(workspaceId)?.effectiveSnapshotId?.let { snapshots.getSnapshot(it) }
        val changes = if (then != null && now != null) {
            RunComparison.settingsChanges(then, now) + RunComparison.modChanges(then, now)
        } else {
            null
        }
        return NextBuildFacts(lastGood, changes, stages, RunEstimate.of(stages, history))
    }
}
