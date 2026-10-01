/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.session

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PipelineDraftSessionTest {

    @Test
    fun initialSessionIsNotDirty() = runTest {
        val repo = PipelineDraftSessionRepository()
        val session = repo.observeSession("ws-1").first()
        assertFalse(session.isDirty)
        assertEquals(session.persistedRevision, session.workingRevision)
    }

    @Test
    fun updatingWorkingSnapshotMarksSessionDirty() = runTest {
        val repo = PipelineDraftSessionRepository()
        val updateAcquire = UpdateAcquisitionConfigUseCase(repo)
        val observeAcquire = ObserveAcquisitionConfigUseCase(repo)

        val initialAcq = observeAcquire("ws-1").first()
        updateAcquire("ws-1", initialAcq.copy(retries = 5))

        val session = repo.observeSession("ws-1").first()
        assertTrue(session.isDirty)
        assertEquals(5, session.workingSnapshot.acquisition.retries)
        assertEquals(session.persistedRevision + 1, session.workingRevision)
    }

    @Test
    fun savingWithCorrectRevisionCommitsAndClearsDirty() = runTest {
        val repo = PipelineDraftSessionRepository()
        val updateAcquire = UpdateAcquisitionConfigUseCase(repo)
        val save = SavePipelineDraftUseCase(repo)

        val initialAcq = repo.observeSession("ws-1").first().workingSnapshot.acquisition
        updateAcquire("ws-1", initialAcq.copy(retries = 4))

        val sessionBeforeSave = repo.observeSession("ws-1").first()
        assertTrue(sessionBeforeSave.isDirty)

        val committed = save("ws-1", sessionBeforeSave.workingRevision)
        assertEquals(4, committed.acquisition.retries)

        val sessionAfterSave = repo.observeSession("ws-1").first()
        assertFalse(sessionAfterSave.isDirty)
    }

    @Test
    fun savingWithStaleRevisionThrowsOptimisticConflict() = runTest {
        val repo = PipelineDraftSessionRepository()
        val updateAcquire = UpdateAcquisitionConfigUseCase(repo)
        val save = SavePipelineDraftUseCase(repo)

        val initialSession = repo.observeSession("ws-1").first()
        updateAcquire("ws-1", initialSession.workingSnapshot.acquisition.copy(retries = 2))

        assertFailsWith<OptimisticRevisionConflictException> {
            save("ws-1", initialSession.workingRevision) // Stale revision!
        }
    }

    @Test
    fun discardingChangesRevertsToPersistedSnapshot() = runTest {
        val repo = PipelineDraftSessionRepository()
        val updateAcquire = UpdateAcquisitionConfigUseCase(repo)
        val discard = DiscardPipelineDraftChangesUseCase(repo)

        val initialAcq = repo.observeSession("ws-1").first().workingSnapshot.acquisition
        updateAcquire("ws-1", initialAcq.copy(retries = 1))
        assertTrue(repo.observeSession("ws-1").first().isDirty)

        val sessionAfterDiscard = discard("ws-1")
        assertFalse(sessionAfterDiscard.isDirty)
        assertEquals(initialAcq.retries, sessionAfterDiscard.workingSnapshot.acquisition.retries)
    }
}
