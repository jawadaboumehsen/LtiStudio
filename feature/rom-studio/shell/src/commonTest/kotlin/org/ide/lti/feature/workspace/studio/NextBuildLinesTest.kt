/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio

import kotlinx.datetime.Instant
import org.ide.lti.core.domain.pipeline.StagePreview
import org.ide.lti.core.domain.workspace.NextBuildFacts
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.StageId
import org.ide.lti.feature.workspace.studio.components.nextBuildLines
import kotlin.test.Test
import kotlin.test.assertEquals

class NextBuildLinesTest {
    private val good = BuildRun("r", "w", "s", startedAt = Instant.parse("2026-11-13T23:08:30Z"))

    @Test
    fun changesSinceTheLastGoodBuildAndTheFirstStageThatRunsAreSaid() {
        val stages = listOf(
            StagePreview(StageId.WORK_TREE_ASSEMBLY, null),
            StagePreview(StageId.BUILD_FLASHABLE_ZIP, "build.brotliLevel"),
            StagePreview(StageId.GENERATE_OTA_MANIFEST, "an earlier stage runs first"),
        )
        val changes = (1..10).map { "setting $it" }
        val lines = nextBuildLines(NextBuildFacts(good, changes, stages, estimate = null))
        assertEquals("Changed since the last build that worked (2026-11-13 23:08):", lines.first())
        assertEquals("• setting 8", lines[8])
        assertEquals("…and 2 more.", lines[9])
        assertEquals("2 of 3 stages run, from Build & sign: build.brotliLevel", lines.last())
    }

    @Test
    fun aFirstBuildAndAFullyCachedOneAreSaidPlainly() {
        val cached = listOf(StagePreview(StageId.WORK_TREE_ASSEMBLY, null))
        assertEquals(
            listOf("No build has made a package here yet.", "Every stage comes from cache."),
            nextBuildLines(NextBuildFacts(null, null, cached, estimate = null)),
        )
    }
}
