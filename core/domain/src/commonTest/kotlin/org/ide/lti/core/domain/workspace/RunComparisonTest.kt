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

import org.ide.lti.core.domain.debloat.DebloatDecision
import org.ide.lti.core.domain.debloat.DebloatResolvedRemoval
import org.ide.lti.core.domain.debloat.RemovalReason
import org.ide.lti.core.domain.pipeline.PipelineFixture
import org.ide.lti.core.model.run.Artifact
import org.ide.lti.core.model.run.ArtifactKind
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageOutcome
import org.ide.lti.core.model.run.StageState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunComparisonTest {
    private val fixture = PipelineFixture()
    private val snapshot = fixture.snapshotGlobalOta

    private fun run(id: String, sha: String, tools: Map<String, String>, buildState: StageState) = BuildRun(
        id = id,
        workspaceId = snapshot.workspaceId,
        snapshotId = snapshot.id,
        startedAt = snapshot.createdAt,
        artifacts = listOf(
            Artifact(ArtifactKind.FLASHABLE_ZIP, "/ws/out/rom.zip", sizeBytes = 3L shl 20, sha256 = sha),
        ),
        toolVersions = tools,
        stages = listOf(
            StageOutcome(StageId.WORK_TREE_ASSEMBLY, StageState.SKIPPED),
            StageOutcome(StageId.BUILD_FLASHABLE_ZIP, buildState, durationMs = 40_000, whyRan = "build.compression"),
        ),
    )

    private fun removed(vararg packages: String) = RunRecord.EMPTY.copy(
        present = true,
        debloat = DebloatDecision(
            removals = packages.map {
                DebloatResolvedRemoval(
                    "product",
                    "app/$it",
                    1L shl 20,
                    it,
                    RemovalReason.EXPLICIT_SELECTOR,
                    emptyList(),
                )
            },
            kept = emptyList(),
            logicalBytesFreed = packages.size.toLong() shl 20,
        ),
        certificate = "AA",
    )

    @Test
    fun eachDifferenceIsNamedWithBothSides() {
        val changed = snapshot.copy(build = snapshot.build.copy(partitionCompression = mapOf("system" to "lzma")))
        val before = RunComparison.Side(
            run("r1", "aaa", mapOf("erofs-utils" to "1.8"), StageState.EXECUTED),
            snapshot,
            removed("com.a"),
        )
        val after = RunComparison.Side(
            run("r2", "bbb", mapOf("erofs-utils" to "1.9"), StageState.EXECUTED),
            changed,
            removed("com.a", "com.b").copy(certificate = "BB"),
        )
        val result = RunComparison.of(before, after)
        val text = result.markdown
        assertTrue("build.partitionCompression.system: (none) → lzma" in text, text)
        assertTrue("erofs-utils: 1.8 → 1.9" in text, text)
        assertTrue("com.b: now removed" in text && "1 MiB freed → 2 MiB" in text, text)
        assertTrue("platform certificate AA → BB" in text, text)
        assertTrue("rom.zip: different content" in text, text)
        assertTrue("work_tree_assembly: from cache → from cache" in text, text)
        assertTrue("build_flashable_zip: ran in 40 s → ran in 40 s: build.compression" in text, text)
        assertEquals(6, result.differences)
    }

    @Test
    fun theSameRunTwiceHasNoDifferencesAndARunWithoutRecordIsSaid() {
        val side = RunComparison.Side(run("r1", "aaa", emptyMap(), StageState.EXECUTED), snapshot, RunRecord.EMPTY)
        val result = RunComparison.of(side, side.copy(run = side.run.copy(id = "r2")))
        assertEquals(0, result.differences)
        assertTrue("used the same settings" in result.markdown)
        assertTrue("Run r1 has no record" in result.markdown)
    }
}
