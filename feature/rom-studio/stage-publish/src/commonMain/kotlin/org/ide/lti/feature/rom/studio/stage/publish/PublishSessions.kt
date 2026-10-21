/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.publish

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.usecase.publish.PublishReleaseUseCase
import org.ide.lti.core.domain.usecase.publish.PublishTransfer
import org.ide.lti.core.model.workspace.PublishSettings
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The publishes of this app session, one per workspace. A publish runs here, not in its editor: it goes on when the
 * editor closes or the person leaves the workspace, and the editor shows it again when it comes back.
 */
public class PublishSessions(
    private val publisher: PublishReleaseUseCase?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val states = mutableMapOf<String, MutableStateFlow<PublishUiState>>()
    private val jobs = mutableMapOf<String, Job>()

    /** Where each running transfer's speed is measured from. */
    private val rateFrom = mutableMapOf<String, Pair<TimeMark, Long>>()

    public fun state(workspaceId: String): StateFlow<PublishUiState> = flow(workspaceId).asStateFlow()

    private fun flow(workspaceId: String) =
        synchronized(states) { states.getOrPut(workspaceId) { MutableStateFlow(PublishUiState()) } }

    /** Reads what Publish would do with [settings] (the editor's, maybe unsaved) and the last attempt's journal. */
    public fun refresh(workspaceId: String, settings: PublishSettings) {
        val use = publisher ?: return
        scope.launch {
            val preflight = use.preflight(workspaceId, settings)
            flow(workspaceId).update {
                // While publishing, the live journal is the running one; the file is only as new as its last step.
                it.copy(preflight = preflight, journal = if (it.publishing) it.journal else preflight.journal)
            }
        }
    }

    /** Publishes (or resumes the last attempt). Only called after the person confirms it. */
    public fun publish(workspaceId: String, settings: PublishSettings) {
        val use = publisher ?: return
        val state = flow(workspaceId)
        if (state.value.publishing) return
        state.update { it.copy(publishing = true, transfer = null, bytesPerSecond = null) }
        jobs[workspaceId] = scope.launch {
            try {
                use.publish(
                    workspaceId = workspaceId,
                    edited = settings,
                    onProgress = { journal -> state.update { it.copy(journal = journal) } },
                    onTransfer = { onTransfer(workspaceId, it) },
                )
            } finally {
                state.update { it.copy(publishing = false, cancelling = false, transfer = null) }
                refresh(workspaceId, settings)
            }
        }
    }

    /** Stops the running publish; the journal keeps what is done, so publishing again resumes. */
    public fun cancel(workspaceId: String) {
        val state = flow(workspaceId)
        if (!state.value.publishing) return
        state.update { it.copy(cancelling = true) }
        jobs[workspaceId]?.cancel()
    }

    private fun onTransfer(workspaceId: String, transfer: PublishTransfer) {
        val state = flow(workspaceId)
        val previous = state.value.transfer
        val done = transfer.done
        if (previous?.stepId != transfer.stepId || done == null) rateFrom.remove(workspaceId)
        val rate = done?.let {
            val from = rateFrom.getOrPut(workspaceId) { TimeSource.Monotonic.markNow() to it }
            val seconds = from.first.elapsedNow().inWholeMilliseconds / MILLIS
            if (seconds >= MIN_RATE_SECONDS) (it - from.second) / seconds else null
        }
        val sameStep = previous?.stepId == transfer.stepId && done != null
        state.update { it.copy(transfer = transfer, bytesPerSecond = rate ?: it.bytesPerSecond.takeIf { sameStep }) }
    }

    private companion object {
        const val MILLIS = 1000.0
        const val MIN_RATE_SECONDS = 3.0
    }
}
