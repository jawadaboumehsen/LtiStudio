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

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.model.run.Artifact
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.Presence

/**
 * Port interface for managing build runs, stages, steps, events, and artifacts.
 */
interface RunRepository {
    /**
     * Observes all runs belonging to a specific workspace.
     */
    fun observeRuns(workspaceId: String): Flow<List<BuildRun>>

    /**
     * Upserts a run record (creates or updates).
     */
    suspend fun upsert(run: BuildRun)

    /**
     * Appends serialized stream events for a run.
     */
    suspend fun appendEvents(runId: String, events: List<String>)

    /**
     * Retrieves cached stream events for a run starting from sequence [fromSeq].
     */
    suspend fun cachedEvents(runId: String, fromSeq: Long = 0L): List<String>

    /**
     * Returns the latest active run for a workspace, or null if none is active.
     */
    suspend fun latestActive(workspaceId: String): BuildRun?

    /**
     * Retrieves a run by its ID.
     */
    suspend fun getRun(runId: String): BuildRun? = null

    /**
     * Deletes a run record and all its associated events and cache files.
     */
    suspend fun deleteRun(runId: String) {}

    /**
     * Refreshes the artifact presence status for runs in [workspaceId]
     * using the given [checker] function.
     */
    suspend fun refreshPresence(workspaceId: String, checker: suspend (Artifact) -> Presence) {}

    /**
     * Prunes missing artifacts from the specified run.
     */
    suspend fun pruneArtifacts(runId: String) {}

    /**
     * Attempts to claim the driver lease for a run.
     * Returns true if claimed, or false when another live driver (heartbeat younger than 3 * 15 s) holds it.
     */
    suspend fun claimDriver(runId: String, instanceId: String, now: Long = System.currentTimeMillis()): Boolean

    /**
     * Updates the driver heartbeat timestamp for a run.
     */
    suspend fun heartbeatDriver(runId: String, instanceId: String, now: Long = System.currentTimeMillis())
}
