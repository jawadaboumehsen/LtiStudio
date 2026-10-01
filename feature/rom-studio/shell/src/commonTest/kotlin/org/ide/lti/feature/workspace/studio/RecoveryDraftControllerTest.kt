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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.ports.RecoveryDraftStorePort
import org.ide.lti.core.model.draft.RecoveryDraftReadResult
import org.ide.lti.core.model.draft.RecoveryDraftRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryDraftControllerTest {
    @Test
    fun rapidEditsProduceOnePersist() = runTest {
        val draft = MutableStateFlow(WorkspacePipelineDraft.create("workspace", null))
        val fake = FakeStore()
        RecoveryDraftController(draft, fake, backgroundScope, debounceMillis = 500L)
        runCurrent()
        draft.value = draft.value.copy(draftRevision = 1L)
        draft.value = draft.value.copy(draftRevision = 2L)
        advanceTimeBy(501L)
        runCurrent()
        assertEquals(1, fake.persisted.size)
        assertEquals(2L, fake.persisted.single().draftRevision)
    }

    @Test
    fun discardTombstonesBeforeDeleting() = runTest {
        val draft = MutableStateFlow(WorkspacePipelineDraft.create("workspace", null))
        val fake = FakeStore()
        val controller = RecoveryDraftController(draft, fake, backgroundScope)
        controller.discard()
        assertEquals(listOf("tombstone", "delete"), fake.calls)
    }

    @Test
    fun pendingOlderGenerationCannotResurrectDraft() = runTest {
        val draft = MutableStateFlow(WorkspacePipelineDraft.create("workspace", null))
        val fake = FakeStore()
        val controller = RecoveryDraftController(draft, fake, backgroundScope, debounceMillis = 500L)
        draft.value = draft.value.copy(draftRevision = 1L)
        controller.discard()
        advanceTimeBy(501L)
        runCurrent()
        assertTrue(fake.persisted.isEmpty())
    }

    private class FakeStore : RecoveryDraftStorePort {
        val persisted = mutableListOf<RecoveryDraftRecord>()
        val calls = mutableListOf<String>()
        override suspend fun persist(record: RecoveryDraftRecord): Result<RecoveryDraftRecord> {
            persisted += record
            return Result.success(record)
        }
        override suspend fun get(
            workspaceId: String,
            expectedBaseSnapshotId: String?,
            expectedBaseSnapshotDigest: String?,
        ): RecoveryDraftReadResult? = null
        override suspend fun advanceDiscardGeneration(workspaceId: String, generation: Long): Result<Unit> {
            calls += "tombstone"
            return Result.success(Unit)
        }
        override suspend fun delete(workspaceId: String): Result<Unit> {
            calls += "delete"
            return Result.success(Unit)
        }
        override suspend fun retireIfRevision(workspaceId: String, savedRevision: Long) = Result.success(false)
    }
}
