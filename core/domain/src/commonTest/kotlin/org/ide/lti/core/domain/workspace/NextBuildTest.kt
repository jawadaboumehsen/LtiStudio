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

import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.pipeline.PipelineFixture
import org.ide.lti.core.domain.pipeline.StagePreview
import org.ide.lti.core.domain.repository.run.InMemoryRunRepository
import org.ide.lti.core.domain.repository.snapshot.InMemorySnapshotRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.run.Artifact
import org.ide.lti.core.model.run.ArtifactKind
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageOutcome
import org.ide.lti.core.model.run.StageState
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextBuildTest {
    private val fixture = PipelineFixture()
    private val built = fixture.snapshotGlobalOta
    private val current = built.copy(
        id = "now",
        build = built.build.copy(partitionCompression = mapOf("system" to "lzma")),
    )
    private val zip = Artifact(ArtifactKind.FLASHABLE_ZIP, "/ws/out/rom.zip", sizeBytes = 1, sha256 = "a")
    private val preview = listOf(
        StagePreview(StageId.WORK_TREE_ASSEMBLY, null),
        StagePreview(StageId.BUILD_FLASHABLE_ZIP, "build.partitionCompression"),
    )

    private fun run(id: String, state: RunState, artifacts: List<Artifact>, startedAt: Long) = BuildRun(
        id = id,
        workspaceId = built.workspaceId,
        snapshotId = built.id,
        state = state,
        startedAt = kotlinx.datetime.Instant.fromEpochSeconds(startedAt),
        artifacts = artifacts,
        stages = listOf(StageOutcome(StageId.BUILD_FLASHABLE_ZIP, StageState.EXECUTED, durationMs = 60_000)),
    )

    private suspend fun next(vararg history: BuildRun): NextBuildFacts {
        val snapshots = InMemorySnapshotRepository().apply {
            saveSnapshot(built)
            saveSnapshot(current)
        }
        val runs = InMemoryRunRepository().apply { history.forEach { upsert(it) } }
        val workspaces = Proxy.newProxyInstance(
            WorkspaceRepository::class.java.classLoader,
            arrayOf(WorkspaceRepository::class.java),
        ) { _, method, _ ->
            check(method.name == "getWorkspace")
            fixture.workspace.copy(effectiveSnapshotId = current.id)
        } as WorkspaceRepository
        return NextBuild(workspaces, snapshots, runs, preview = { Result.success(preview) }).of(built.workspaceId)
    }

    @Test
    fun theNextBuildIsMeasuredAgainstTheNewestRunThatMadeAPackage() = runTest {
        val good = run("good", RunState.SUCCEEDED, listOf(zip), startedAt = 100)
        // Newer, but they made nothing to install: never "the last build that worked".
        val throughAssemble = run("assemble", RunState.SUCCEEDED, emptyList(), startedAt = 200)
        val failed = run("failed", RunState.FAILED, listOf(zip), startedAt = 300)

        val facts = next(good, throughAssemble, failed)

        assertEquals("good", facts.lastGood?.id)
        assertEquals(listOf("build.partitionCompression.system: (none) → lzma"), facts.changes)
        assertEquals(preview, facts.stages)
        assertEquals(60_000, facts.estimate?.typicalMs)
    }

    @Test
    fun withoutABuildThatWorkedThereIsNothingToCompare() = runTest {
        val facts = next(run("failed", RunState.FAILED, listOf(zip), startedAt = 1))
        assertNull(facts.lastGood)
        assertNull(facts.changes)
    }
}
