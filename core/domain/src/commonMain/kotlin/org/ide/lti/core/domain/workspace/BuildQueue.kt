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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.common.utils.runCatchingCancellable
import org.ide.lti.core.domain.pipeline.stages.Preflight
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState

/** One profile of a [BuildQueue] and how far it got. */
public data class QueuedBuild(val profile: String, val status: String = WAITING, val runId: String? = null) {
    public companion object {
        public const val WAITING: String = "waiting"
    }
}

/** The queue now: its workspace, its builds in order, and whether it is going. */
public data class QueueState(
    val workspaceId: String? = null,
    val builds: List<QueuedBuild> = emptyList(),
    val running: Boolean = false,
)

/**
 * Builds a workspace's profiles one after another: each profile is made the current settings, checked by the
 * Preflight first (a blocking finding stops the queue there, with why, instead of starting a build that fails), then
 * built in full; the next starts once that run has ended. A failed build stops the queue. Stopping lets the running
 * build finish and starts no other. The queue lives while the app does; a build it started goes on as any build.
 */
public class BuildQueue(
    private val profiles: BuildProfiles,
    private val start: suspend (workspaceId: String) -> Result<BuildRun>,
    private val runs: RunRepository,
    private val scope: CoroutineScope,
    private val preflight: Preflight? = null,
) {
    private val _state = MutableStateFlow(QueueState())
    public val state: StateFlow<QueueState> = _state.asStateFlow()
    private var job: Job? = null

    /** Builds [names] of [workspaceId]'s profiles in that order; refused while a queue is going. */
    public fun start(workspaceId: String, names: List<String>) {
        if (_state.value.running || names.isEmpty()) return
        _state.value = QueueState(workspaceId, names.map { QueuedBuild(it) }, running = true)
        job = scope.launch {
            try {
                for (index in names.indices) {
                    if (!_state.value.running || !buildOne(workspaceId, index)) break
                }
            } finally {
                _state.update { it.copy(running = false) }
            }
        }
    }

    /** No build starts after the one running (which goes on to its end). */
    public fun stop() {
        _state.update { s ->
            val builds = s.builds.map { if (it.status == QueuedBuild.WAITING) it.copy(status = "not started") else it }
            s.copy(running = false, builds = builds)
        }
    }

    /** Applies, checks, starts and waits for the build at [index]; false stops the queue. */
    private suspend fun buildOne(workspaceId: String, index: Int): Boolean {
        val name = _state.value.builds[index].profile
        val profile = profiles.list(workspaceId).firstOrNull { it.name == name }
        val stop = when {
            profile == null -> "the profile is gone"
            profiles.apply(workspaceId, profile).isFailure -> "its settings could not be applied"
            else -> blocker(workspaceId)
        }
        if (stop != null) {
            mark(index, "stopped: $stop", null)
            return false
        }
        mark(index, "checked, starting", null)
        val run = start(workspaceId)
        run.exceptionOrNull()?.let { mark(index, "stopped: could not start (${it.message})", null) }
        return run.getOrNull()?.let { waitFor(workspaceId, index, it) } ?: false
    }

    /** Waits for [run] to end; true when it built. */
    private suspend fun waitFor(workspaceId: String, index: Int, run: BuildRun): Boolean {
        mark(index, "building", run.id)
        val ended = runs.observeRuns(workspaceId)
            .mapNotNull { list -> list.firstOrNull { it.id == run.id && it.state.isTerminal } }
            .first()
        val ok = ended.state == RunState.SUCCEEDED
        mark(index, if (ok) "built" else "stopped: the build ended ${ended.state.name.lowercase()}", run.id)
        return ok
    }

    /** The Preflight's first blocking finding for the settings now current; null when nothing would stop it. */
    private suspend fun blocker(workspaceId: String): String? {
        val checker = preflight ?: return null
        return runCatchingCancellable { checker.check(workspaceId) }.fold(
            onSuccess = { findings ->
                findings.firstOrNull { it.blocks }?.let { "Will it build? says ${it.code}: ${it.message}" }
            },
            onFailure = { "the check could not run (${it.message})" },
        )
    }

    private fun mark(index: Int, status: String, runId: String?) {
        _state.update { s ->
            val builds = s.builds.mapIndexed { i, b ->
                if (i == index) b.copy(status = status, runId = runId ?: b.runId) else b
            }
            s.copy(builds = builds)
        }
    }
}
