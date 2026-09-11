/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.repository.run

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryRunRepositoryTest {

    private fun createRun(
        id: String,
        workspaceId: String = "ws-1",
        state: RunState = RunState.QUEUED,
        startedAtEpochMs: Long = 1000L,
    ): BuildRun {
        return BuildRun(
            id = id,
            workspaceId = workspaceId,
            snapshotId = "snap-1",
            state = state,
            startedAt = Instant.fromEpochMilliseconds(startedAtEpochMs),
        )
    }

    @Test
    fun testObserveRunsAndUpsert() = runTest {
        val repo = InMemoryRunRepository()
        val run1 = createRun("r1", "ws-1")
        val run2 = createRun("r2", "ws-2")

        repo.upsert(run1)
        repo.upsert(run2)

        val ws1Runs = repo.observeRuns("ws-1").first()
        assertEquals(1, ws1Runs.size)
        assertEquals("r1", ws1Runs.first().id)

        // Update run1
        val updatedRun1 = run1.copy(state = RunState.RUNNING)
        repo.upsert(updatedRun1)
        val ws1Updated = repo.observeRuns("ws-1").first()
        assertEquals(RunState.RUNNING, ws1Updated.first().state)
    }

    @Test
    fun testAppendAndCachedEvents() = runTest {
        val repo = InMemoryRunRepository()
        repo.appendEvents("r1", listOf("ev0", "ev1", "ev2"))
        repo.appendEvents("r1", listOf("ev3"))

        val allEvents = repo.cachedEvents("r1", fromSeq = 0L)
        assertEquals(listOf("ev0", "ev1", "ev2", "ev3"), allEvents)

        val partial = repo.cachedEvents("r1", fromSeq = 2L)
        assertEquals(listOf("ev2", "ev3"), partial)

        val empty = repo.cachedEvents("unknown_run")
        assertTrue(empty.isEmpty())
    }

    @Test
    fun testLatestActive() = runTest {
        val repo = InMemoryRunRepository()
        val r1 = createRun("r1", "ws-1", state = RunState.SUCCEEDED, startedAtEpochMs = 1000L)
        val r2 = createRun("r2", "ws-1", state = RunState.RUNNING, startedAtEpochMs = 2000L)
        val r3 = createRun("r3", "ws-1", state = RunState.CANCELLING, startedAtEpochMs = 3000L)

        repo.upsert(r1)
        val active1 = repo.latestActive("ws-1")
        assertNull(active1)

        repo.upsert(r2)
        val active2 = repo.latestActive("ws-1")
        assertNotNull(active2)
        assertEquals("r2", active2.id)

        repo.upsert(r3)
        val active3 = repo.latestActive("ws-1")
        assertNotNull(active3)
        assertEquals("r3", active3.id)
    }
}
