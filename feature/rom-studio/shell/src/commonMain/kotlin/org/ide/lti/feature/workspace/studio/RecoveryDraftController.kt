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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.ide.lti.core.domain.ports.RecoveryDraftStorePort
import org.ide.lti.core.model.draft.RecoveryDraftRecord

/** Quiet crash-recovery persistence alongside the canonical StudioDraftController. */
public class RecoveryDraftController(
    draft: StateFlow<WorkspacePipelineDraft>,
    private val store: RecoveryDraftStorePort,
    private val scope: CoroutineScope,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) {
    private val _lastFailure = MutableStateFlow<Throwable?>(null)
    public val lastFailure: StateFlow<Throwable?> = _lastFailure.asStateFlow()
    private var discardGeneration = 0L
    private var pending: Job? = null
    private var latestDraft = draft.value

    init {
        pending = scope.launch {
            draft.collectLatest {
                latestDraft = it
                delay(debounceMillis)
                persistIfCurrent(it)
            }
        }
    }

    public suspend fun flush() {
        pending?.cancel()
        persistIfCurrent(latestDraft)
    }

    public suspend fun discard() {
        pending?.cancel()
        val persistedGeneration = store.get(latestDraft.workspaceId)?.record?.discardGeneration ?: 0L
        discardGeneration = maxOf(discardGeneration, persistedGeneration) + 1L
        val generation = discardGeneration
        val tombstone = store.advanceDiscardGeneration(latestDraft.workspaceId, generation)
        tombstone.onFailure(::recordFailure)
        if (tombstone.isSuccess) {
            store.delete(latestDraft.workspaceId).onFailure(::recordFailure)
        }
    }

    public suspend fun retireAfterSave(savedAsOfRevision: Long) {
        store.retireIfRevision(latestDraft.workspaceId, savedAsOfRevision).onFailure(::recordFailure)
    }

    private suspend fun persistIfCurrent(draft: WorkspacePipelineDraft) {
        if (draft.workspaceId != latestDraft.workspaceId) return
        val record = draft.toRecoveryRecord(discardGeneration)
        store.persist(record).onFailure(::recordFailure)
    }

    private fun recordFailure(failure: Throwable) {
        _lastFailure.value = failure
    }

    private fun WorkspacePipelineDraft.toRecoveryRecord(generation: Long) = RecoveryDraftRecord(
        workspaceId = workspaceId,
        baseSnapshotId = baseSnapshotId,
        baseSnapshotDigest = baseSnapshotDigest,
        draftRevision = draftRevision,
        discardGeneration = generation,
        acquisition = acquisition,
        extraction = extraction,
        assembly = assembly,
        debloat = debloat,
        customization = customization,
        build = build,
        release = release,
        publish = publish,
        dirtyPaths = dirtyPaths,
        lastPersistedAt = Clock.System.now(),
    )

    public companion object {
        /** 500 ms coalesces typing bursts while keeping crash recovery reasonably fresh. */
        public const val DEFAULT_DEBOUNCE_MILLIS: Long = 500L
    }
}
